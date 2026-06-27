package com.xykj.wms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.AuditLog;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.util.FileValidationUtil;
import com.xykj.wms.dto.MaterialCreateDTO;
import com.xykj.wms.dto.MaterialImportDTO;
import com.xykj.wms.dto.MaterialImportResultDTO;
import com.xykj.wms.dto.MaterialQueryDTO;
import com.xykj.wms.dto.MaterialUpdateDTO;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.entity.Material;
import com.xykj.wms.entity.MaterialFoodMapping;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.MaterialFoodMappingMapper;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.wms.service.MaterialService;
import com.xykj.wms.vo.MaterialStatisticsVO;
import com.xykj.wms.vo.MaterialVO;
import com.xykj.wms.vo.NutritionSyncResultVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物料服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final MaterialMapper materialMapper;
    private final InventoryMapper inventoryMapper;
    private final MaterialFoodMappingMapper materialFoodMappingMapper;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;
    private final com.xykj.wms.service.alert.MaterialAlertEngine materialAlertEngine;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    @Override
    @DataScope
    public PageResult<MaterialVO> list(MaterialQueryDTO query) {
        LambdaQueryWrapper<Material> wrapper = buildListWrapper(query);

        // 不按库存状态筛选时，走数据库分页。
        if (StrUtil.isBlank(query.getStockStatus())) {
            IPage<Material> page = materialMapper.selectPage(
                    new Page<>(query.getPageNum(), query.getPageSize()),
                    wrapper
            );
            List<MaterialVO> voList = page.getRecords().stream()
                    .map(this::convertToVO)
                    .toList();
            return PageResult.of(page, voList);
        }

        // 按库存状态筛选时，先查出范围内全部物料，再按计算后的库存状态过滤并手动分页。
        List<Material> scopedMaterials = materialMapper.selectList(wrapper);
        if (scopedMaterials.isEmpty()) {
            return PageResult.empty(query.getPageNum().longValue(), query.getPageSize().longValue());
        }

        List<MaterialVO> filtered = buildAndFilterByStockStatus(scopedMaterials, query.getStockStatus());
        long total = filtered.size();
        int fromIndex = Math.max((query.getPageNum() - 1) * query.getPageSize(), 0);
        int toIndex = Math.min(fromIndex + query.getPageSize(), filtered.size());
        List<MaterialVO> pageList = fromIndex >= filtered.size() ? List.of() : filtered.subList(fromIndex, toIndex);
        return PageResult.of(pageList, query.getPageNum().longValue(), query.getPageSize().longValue(), total);
    }

    @Override
    public MaterialVO getDetail(Long id) {
        Material material = getMaterialById(id);
        return convertToVO(material);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.WMS_MATERIAL,
        operationType = AuditOperationType.CREATE,
        targetId = "#result",
        targetNo = "#dto.materialCode",
        desc = "'新增物料：' + #dto.materialName + '（' + #dto.materialCode + '）'",
        mapper = MaterialMapper.class
    )
    public Long create(MaterialCreateDTO dto) {
        Long targetTenantId = UserContext.getTenantId();

        // 校验物料编码唯一性
        checkMaterialCodeUnique(dto.getMaterialCode(), null);
        // 校验物料名称+规格唯一性（同租户）
        checkMaterialNameSpecUnique(dto.getMaterialName(), dto.getMaterialSpec(), targetTenantId, null);

        // 校验库存范围
        validateStockRange(dto.getMinStock(), dto.getMaxStock());

        Integer targetNearExpiryDays = dto.getNearExpiryDays() != null ? dto.getNearExpiryDays() : 7;
        Integer targetWarningDays = dto.getWarningDays() != null ? dto.getWarningDays() : 30;
        validateDayRules(targetNearExpiryDays, targetWarningDays, dto.getShelfLifeDays());

        // 创建物料实体
        Material material = new Material();
        material.setMaterialCode(dto.getMaterialCode());
        material.setMaterialName(dto.getMaterialName());
        material.setSpec(dto.getMaterialSpec());
        material.setUnit(dto.getUnit());
        material.setMaterialCategory(dto.getCategoryName());
        material.setStorageConditions(dto.getStorageRequire());
        material.setShelfLifeDays(dto.getShelfLifeDays());
        material.setNearExpiryDays(dto.getNearExpiryDays() != null ? dto.getNearExpiryDays() : 7);
        material.setWarningDays(dto.getWarningDays() != null ? dto.getWarningDays() : 30);
        material.setMinStock(dto.getMinStock());
        material.setMaxStock(dto.getMaxStock());
        material.setImageUrl(dto.getImageUrl());
        material.setRemark(dto.getRemark());
        material.setStatus("active");
        Long orgId = UserContext.getOrgId();
        material.setOrgId(orgId != null ? orgId : 0L);
        material.setTenantId(targetTenantId);

        materialMapper.insert(material);
        log.info("创建物料成功: id={}, code={}", material.getId(), material.getMaterialCode());

        return material.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.WMS_MATERIAL,
        operationType = AuditOperationType.UPDATE,
        targetId = "#id",
        targetNo = "#entity.materialCode",
        desc = "'编辑物料：' + #entity.materialName + '（' + #entity.materialCode + '）'",
        mapper = MaterialMapper.class
    )
    public void update(Long id, MaterialUpdateDTO dto) {
        Material material = getMaterialById(id);

        // 校验库存范围
        BigDecimal minStock = dto.getMinStock() != null ? dto.getMinStock() : material.getMinStock();
        BigDecimal maxStock = dto.getMaxStock() != null ? dto.getMaxStock() : material.getMaxStock();
        validateStockRange(minStock, maxStock);

        Integer targetNearExpiryDays = dto.getNearExpiryDays() != null ? dto.getNearExpiryDays() : material.getNearExpiryDays();
        Integer targetWarningDays = dto.getWarningDays() != null ? dto.getWarningDays() : material.getWarningDays();
        Integer targetShelfLifeDays = dto.getShelfLifeDays() != null ? dto.getShelfLifeDays() : material.getShelfLifeDays();
        validateDayRules(targetNearExpiryDays, targetWarningDays, targetShelfLifeDays);

        String targetMaterialName = StrUtil.isNotBlank(dto.getMaterialName()) ? dto.getMaterialName() : material.getMaterialName();
        String targetMaterialSpec = StrUtil.isNotBlank(dto.getMaterialSpec()) ? dto.getMaterialSpec() : material.getSpec();
        checkMaterialNameSpecUnique(targetMaterialName, targetMaterialSpec, material.getTenantId(), id);

        // 如果要禁用物料，检查库存是否为0
        if ("inactive".equals(dto.getStatus()) && "active".equals(material.getStatus())) {
            BigDecimal currentStock = materialMapper.getCurrentStock(id);
            if (currentStock.compareTo(BigDecimal.ZERO) > 0) {
                throw BizException.validationFailed("物料库存数不为0，无法禁用");
            }
        }

        // 更新字段
        if (StrUtil.isNotBlank(dto.getMaterialName())) {
            material.setMaterialName(dto.getMaterialName());
        }
        if (StrUtil.isNotBlank(dto.getMaterialSpec())) {
            material.setSpec(dto.getMaterialSpec());
        }
        if (StrUtil.isNotBlank(dto.getUnit())) {
            material.setUnit(dto.getUnit());
        }
        if (StrUtil.isNotBlank(dto.getCategoryName())) {
            material.setMaterialCategory(dto.getCategoryName());
        }
        if (dto.getStorageRequire() != null) {
            material.setStorageConditions(dto.getStorageRequire());
        }
        if (dto.getShelfLifeDays() != null) {
            Integer oldShelfLifeDays = material.getShelfLifeDays();
            material.setShelfLifeDays(dto.getShelfLifeDays());
            // 保质期变更时同步更新库存和入库单明细的到期日期
            if (!dto.getShelfLifeDays().equals(oldShelfLifeDays)) {
                syncExpiryDate(id, dto.getShelfLifeDays());
            }
        }
        if (dto.getNearExpiryDays() != null) {
            material.setNearExpiryDays(dto.getNearExpiryDays());
        }
        if (dto.getWarningDays() != null) {
            material.setWarningDays(dto.getWarningDays());
        }
        if (dto.getMinStock() != null) {
            material.setMinStock(dto.getMinStock());
        }
        if (dto.getMaxStock() != null) {
            material.setMaxStock(dto.getMaxStock());
        }
        if (dto.getImageUrl() != null) {
            material.setImageUrl(dto.getImageUrl());
        }
        if (dto.getStatus() != null) {
            material.setStatus(dto.getStatus());
        }
        if (dto.getRemark() != null) {
            material.setRemark(dto.getRemark());
        }

        materialMapper.updateById(material);
        log.info("更新物料成功: id={}", id);
        // 物料配置变更后触发告警校验
        materialAlertEngine.checkMaterial(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.WMS_MATERIAL,
        operationType = AuditOperationType.DELETE,
        targetId = "#id",
        targetNo = "#entity.materialCode",
        desc = "'删除物料：' + #entity.materialName + '（' + #entity.materialCode + '）'",
        mapper = MaterialMapper.class
    )
    public void delete(Long id) {
        Material material = getMaterialById(id);

        // 检查库存是否为0
        BigDecimal currentStock = materialMapper.getCurrentStock(id);
        if (currentStock.compareTo(BigDecimal.ZERO) > 0) {
            throw BizException.validationFailed("物料库存数不为0，无法删除");
        }

        // 检查是否关联菜谱
        Integer recipeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recipe_ingredient WHERE material_id = ? AND deleted = 0",
                Integer.class, id);
        if (recipeCount != null && recipeCount > 0) {
            throw BizException.validationFailed("该物料已关联菜谱，无法删除");
        }

        // 检查是否关联业务单据（采购计划、采购订单、入库单、出库单、盘点单，不限状态）
        String businessErrMsg = "该物料已存在业务单据数据，为保证食材溯源完整、库存准确及台账可追溯，不允许删除";

        Integer purchasePlanCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_plan_item i " +
                        "JOIN scm_purchase_plan o ON i.plan_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ?",
                Integer.class, id);
        if (purchasePlanCount != null && purchasePlanCount > 0) {
            throw BizException.validationFailed(businessErrMsg);
        }

        Integer inboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inbound_order_item i " +
                        "JOIN wms_inbound_order o ON i.inbound_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ?",
                Integer.class, id);
        if (inboundCount != null && inboundCount > 0) {
            throw BizException.validationFailed(businessErrMsg);
        }

        Integer outboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_outbound_order_item i " +
                        "JOIN wms_outbound_order o ON i.outbound_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ?",
                Integer.class, id);
        if (outboundCount != null && outboundCount > 0) {
            throw BizException.validationFailed(businessErrMsg);
        }

        Integer purchaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order_item i " +
                        "JOIN scm_purchase_order o ON i.order_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ?",
                Integer.class, id);
        if (purchaseCount != null && purchaseCount > 0) {
            throw BizException.validationFailed(businessErrMsg);
        }

        Integer stocktakeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_stocktake_order_item i " +
                        "JOIN wms_stocktake_order o ON i.stocktake_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ?",
                Integer.class, id);
        if (stocktakeCount != null && stocktakeCount > 0) {
            throw BizException.validationFailed(businessErrMsg);
        }

        materialMapper.deleteById(id);
        log.info("删除物料成功: id={}", id);
        // 删除物料后从告警规则中解绑
        materialAlertEngine.removeMaterialFromRules(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @AuditLog(
        module = AuditModule.WMS_MATERIAL,
        operationType = AuditOperationType.STATUS_CHANGE,
        targetId = "#id",
        targetNo = "#entity.materialCode",
        desc = "(#status == 'active' ? '启用' : '停用') + '物料：' + #entity.materialName + '（' + #entity.materialCode + '）'",
        mapper = MaterialMapper.class
    )
    public void updateStatus(Long id, String status) {
        if (!"active".equals(status) && !"inactive".equals(status)) {
            throw BizException.validationFailed("无效的物料状态");
        }
        Material material = getMaterialById(id);
        if (status.equals(material.getStatus())) {
            return;
        }
        // 停用时检查
        if ("inactive".equals(status)) {
            String error = validateDeactivate(id);
            if (error != null) {
                throw BizException.validationFailed(error);
            }
        }
        material.setStatus(status);
        materialMapper.updateById(material);
        log.info("物料状态变更: id={}, status={}", id, status);
    }

    @Override
    @DataScope
    public MaterialStatisticsVO getStatistics(MaterialQueryDTO query) {
        LambdaQueryWrapper<Material> materialWrapper = new LambdaQueryWrapper<>();
        materialWrapper.eq(Material::getDeleted, 0)
                .eq(query != null && query.getOrgId() != null, Material::getOrgId, query.getOrgId())
                .in(query != null && query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Material::getOrgId, query.getOrgIds())
                .isNull(query != null && query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Material::getId);

        List<Material> scopedMaterials = materialMapper.selectList(materialWrapper);

        long activeCount = scopedMaterials.stream().filter(m -> "active".equals(m.getStatus())).count();
        long inactiveCount = scopedMaterials.stream().filter(m -> "inactive".equals(m.getStatus())).count();

        // 待完善资料：必填字段（物料名称、编码、规格、单位、分类、保质期天数、临期提醒天数、最低库存、最高库存）任一缺失
        long incompleteCount = scopedMaterials.stream().filter(m -> {
            if (StrUtil.isBlank(m.getMaterialName())) return true;
            if (StrUtil.isBlank(m.getMaterialCode())) return true;
            if (StrUtil.isBlank(m.getSpec())) return true;
            if (StrUtil.isBlank(m.getUnit())) return true;
            if (StrUtil.isBlank(m.getMaterialCategory())) return true;
            if (m.getShelfLifeDays() == null) return true;
            if (m.getNearExpiryDays() == null) return true;
            if (m.getMinStock() == null) return true;
            if (m.getMaxStock() == null) return true;
            return false;
        }).count();

        MaterialStatisticsVO vo = new MaterialStatisticsVO();
        vo.setTotal((long) scopedMaterials.size());
        vo.setActiveCount(activeCount);
        vo.setInactiveCount(inactiveCount);
        vo.setIncompleteCount(incompleteCount);
        return vo;
    }

    @Override
    public String uploadImage(MultipartFile file) {
        FileValidationUtil.validateImageFile(file, MAX_FILE_SIZE);
        return fileStorageService.upload(file, "materials");
    }

    /**
     * 构建列表查询条件（不包含库存状态，库存状态需基于计算值过滤）。
     */
    private LambdaQueryWrapper<Material> buildListWrapper(MaterialQueryDTO query) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StrUtil.isNotBlank(query.getMaterialName()), Material::getMaterialName, query.getMaterialName())
                .eq(StrUtil.isNotBlank(query.getMaterialCode()), Material::getMaterialCode, query.getMaterialCode())
                .eq(StrUtil.isNotBlank(query.getCategoryName()), Material::getMaterialCategory, query.getCategoryName())
                .eq(StrUtil.isNotBlank(query.getStatus()), Material::getStatus, query.getStatus())
                .eq(query.getOrgId() != null, Material::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Material::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Material::getId)
                .orderByDesc(Material::getCreatedAt);
        return wrapper;
    }

    private List<MaterialVO> buildAndFilterByStockStatus(List<Material> materials, String stockStatus) {
        List<Long> materialIds = materials.stream().map(Material::getId).toList();

        Map<Long, BigDecimal> stockTotalMap = new HashMap<>();
        Set<Long> expiredMaterialIds = new HashSet<>();
        if (!materialIds.isEmpty()) {
            List<Inventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().in(Inventory::getMaterialId, materialIds)
            );
            for (Inventory inventory : inventories) {
                BigDecimal quantity = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
                stockTotalMap.merge(inventory.getMaterialId(), quantity, BigDecimal::add);
                if (inventory.getExpiryDate() != null && !inventory.getExpiryDate().isAfter(LocalDate.now())
                        && inventory.getQuantity() != null && inventory.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    expiredMaterialIds.add(inventory.getMaterialId());
                }
            }
        }

        return materials.stream()
                .map(material -> convertToVO(material, stockTotalMap, expiredMaterialIds))
                .filter(vo -> stockStatus.equals(vo.getStockStatus()))
                .toList();
    }

    /** 按库存状态过滤物料实体（用于导出） */
    private List<Material> filterByStockStatus(List<Material> materials, String stockStatus) {
        List<Long> materialIds = materials.stream().map(Material::getId).toList();

        Map<Long, BigDecimal> stockTotalMap = new HashMap<>();
        Set<Long> expiredMaterialIds = new HashSet<>();
        if (!materialIds.isEmpty()) {
            List<Inventory> inventories = inventoryMapper.selectList(
                    new LambdaQueryWrapper<Inventory>().in(Inventory::getMaterialId, materialIds)
            );
            for (Inventory inventory : inventories) {
                BigDecimal quantity = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
                stockTotalMap.merge(inventory.getMaterialId(), quantity, BigDecimal::add);
                if (inventory.getExpiryDate() != null && !inventory.getExpiryDate().isAfter(LocalDate.now())
                        && quantity.compareTo(BigDecimal.ZERO) > 0) {
                    expiredMaterialIds.add(inventory.getMaterialId());
                }
            }
        }

        return materials.stream()
                .filter(m -> {
                    boolean expired = expiredMaterialIds.contains(m.getId());
                    String status;
                    if (expired) {
                        status = "expired";
                    } else {
                        BigDecimal currentStock = stockTotalMap.getOrDefault(m.getId(), BigDecimal.ZERO);
                        status = calculateStockStatus(currentStock, m.getMinStock(), m.getMaxStock());
                    }
                    return stockStatus.equals(status);
                })
                .toList();
    }

    /**
     * 根据ID获取物料
     */
    private Material getMaterialById(Long id) {
        Material material = materialMapper.selectById(id);
        if (material == null) {
            throw BizException.notFound("物料不存在");
        }
        return material;
    }

    /**
     * 校验物料编码唯一性
     */
    private void checkMaterialCodeUnique(String materialCode, Long excludeId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Material::getMaterialCode, materialCode)
                .ne(excludeId != null, Material::getId, excludeId);
        Long count = materialMapper.selectCount(wrapper);
        if (count > 0) {
            throw BizException.conflict("物料编码已存在");
        }
    }

    /**
     * 校验物料名称+规格唯一性（租户内）
     */
    private void checkMaterialNameSpecUnique(String materialName, String materialSpec, Long tenantId, Long excludeId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Material::getMaterialName, materialName)
                .eq(Material::getSpec, materialSpec)
                .eq(tenantId != null, Material::getTenantId, tenantId)
                .isNull(tenantId == null, Material::getTenantId)
                .ne(excludeId != null, Material::getId, excludeId);
        Long count = materialMapper.selectCount(wrapper);
        if (count > 0) {
            throw BizException.conflict("同一租户下物料名称和规格组合已存在");
        }
    }

    /**
     * 校验库存范围
     */
    private void validateStockRange(BigDecimal minStock, BigDecimal maxStock) {
        if (minStock != null && maxStock != null && maxStock.compareTo(minStock) < 0) {
            throw BizException.badRequest("最高库存不能小于最低库存");
        }
    }

    /**
     * 校验物料是否允许停用，返回错误信息；允许停用时返回 null。
     */
    private String validateDeactivate(Long materialId) {
        BigDecimal currentStock = materialMapper.getCurrentStock(materialId);
        if (currentStock != null && currentStock.compareTo(BigDecimal.ZERO) > 0) {
            return "物料库存数不为0，无法停用";
        }
        Integer recipeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recipe_ingredient WHERE material_id = ? AND deleted = 0",
                Integer.class, materialId);
        if (recipeCount != null && recipeCount > 0) {
            return "该物料已关联菜谱，无法停用";
        }
        String inProgressMsg = "该物料存在未办结在途业务/用料计划引用，请先办结、作废相关单据与任务后，再执行禁用操作";
        Integer inboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inbound_order_item i " +
                        "JOIN wms_inbound_order o ON i.inbound_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ? AND o.status NOT IN ('cancelled', 'completed')",
                Integer.class, materialId);
        if (inboundCount != null && inboundCount > 0) {
            return inProgressMsg;
        }
        Integer outboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_outbound_order_item i " +
                        "JOIN wms_outbound_order o ON i.outbound_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ? AND o.status NOT IN ('completed')",
                Integer.class, materialId);
        if (outboundCount != null && outboundCount > 0) {
            return inProgressMsg;
        }
        Integer purchaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order_item i " +
                        "JOIN scm_purchase_order o ON i.order_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ? AND o.status NOT IN ('voided', 'cancelled', 'closed', 'completed')",
                Integer.class, materialId);
        if (purchaseCount != null && purchaseCount > 0) {
            return inProgressMsg;
        }
        Integer stocktakeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_stocktake_order_item i " +
                        "JOIN wms_stocktake_order o ON i.stocktake_id = o.id AND o.deleted = 0 " +
                        "WHERE i.material_id = ? AND o.status NOT IN ('completed', 'voided')",
                Integer.class, materialId);
        if (stocktakeCount != null && stocktakeCount > 0) {
            return inProgressMsg;
        }
        return null;
    }

    /**
     * 物料保质期变更时，同步更新库存和入库单明细的到期日期。
     * expiry_date = production_date + shelf_life_days
     */
    private void syncExpiryDate(Long materialId, Integer newShelfLifeDays) {
        // 同步库存到期日期
        jdbcTemplate.update(
                "UPDATE wms_inventory SET expiry_date = DATE_ADD(production_date, INTERVAL ? DAY) " +
                        "WHERE material_id = ? AND production_date IS NOT NULL",
                newShelfLifeDays, materialId);
        // 同步入库单明细到期日期（排除已删除的入库单）
        jdbcTemplate.update(
                "UPDATE wms_inbound_order_item i JOIN wms_inbound_order o ON i.inbound_id = o.id " +
                        "SET i.expiry_date = DATE_ADD(i.production_date, INTERVAL ? DAY) " +
                        "WHERE i.material_id = ? AND i.production_date IS NOT NULL AND o.deleted = 0",
                newShelfLifeDays, materialId);
        log.info("同步物料保质期变更: materialId={}, newShelfLifeDays={}", materialId, newShelfLifeDays);
    }

    /**
     * 校验天数规则：临期提醒天数 <= 预警天数 <= 保质期。
     */
    private void validateDayRules(Integer nearExpiryDays, Integer warningDays, Integer shelfLifeDays) {
        if (nearExpiryDays == null || warningDays == null || shelfLifeDays == null) {
            return;
        }
        if (nearExpiryDays > warningDays || warningDays > shelfLifeDays) {
            throw BizException.validationFailed("临期提醒天数需小于等于预警天数，且预警天数需小于等于保质期");
        }
    }

    /**
     * 转换为VO
     */
    private MaterialVO convertToVO(Material material) {
        return convertToVO(material, null, null);
    }

    private MaterialVO convertToVO(Material material,
                                   Map<Long, BigDecimal> stockTotalMap,
                                   Set<Long> expiredMaterialIds) {
        MaterialVO vo = new MaterialVO();
        BeanUtil.copyProperties(material, vo);

        // 手动映射字段名不一致的属性
        vo.setMaterialSpec(material.getSpec());
        vo.setCategoryName(material.getMaterialCategory());
        vo.setStorageRequire(material.getStorageConditions());
        vo.setNearExpiryDays(material.getNearExpiryDays());
        vo.setWarningDays(material.getWarningDays());

        // 获取当前库存
        BigDecimal currentStock;
        if (stockTotalMap != null) {
            currentStock = stockTotalMap.getOrDefault(material.getId(), BigDecimal.ZERO);
        } else {
            currentStock = materialMapper.getCurrentStock(material.getId());
        }
        vo.setCurrentStock(currentStock != null ? currentStock : BigDecimal.ZERO);

        // 计算库存状态（优先判断过期）
        boolean expired;
        if (expiredMaterialIds != null) {
            expired = expiredMaterialIds.contains(material.getId());
        } else {
            expired = materialMapper.countExpiredStock(material.getId()) > 0;
        }

        if (expired) {
            vo.setStockStatus("expired");
        } else {
            vo.setStockStatus(calculateStockStatus(currentStock, material.getMinStock(), material.getMaxStock()));
        }

        if (material.getFoodItemId() != null) {
            Map<String, Object> foodItem = getFoodItemSummary(material.getFoodItemId());
            if (foodItem != null) {
                vo.setFoodCode((String) foodItem.get("food_code"));
                vo.setFoodName((String) foodItem.get("food_name"));
            }
        }

        return vo;
    }

    private Map<String, Object> getFoodItemSummary(Long foodItemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, food_code, food_name FROM food_item WHERE id = ? AND deleted = 0",
                foodItemId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    /**
     * 计算库存状态
     * 库存不足：当前库存 <= 最低库存
     * 库存积压：当前库存 >= 最高库存
     * 正常：其余情况
     * 库存为0也参与判断
     */
    private String calculateStockStatus(BigDecimal currentStock, BigDecimal minStock, BigDecimal maxStock) {
        if (currentStock == null) {
            currentStock = BigDecimal.ZERO;
        }

        if (minStock != null && currentStock.compareTo(minStock) <= 0) {
            return "low";
        }

        if (maxStock != null && currentStock.compareTo(maxStock) >= 0) {
            return "high";
        }

        return "normal";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NutritionSyncResultVO syncNutrition(Long id) {
        Material material = getMaterialById(id);
        MaterialFoodMapping mapping = materialFoodMappingMapper.selectOne(new LambdaQueryWrapper<MaterialFoodMapping>()
                .eq(MaterialFoodMapping::getMaterialId, id)
                .eq(MaterialFoodMapping::getDeleted, 0));
        if (mapping == null || mapping.getFoodItemId() == null) {
            throw BizException.validationFailed("当前物料未关联标准食品，无法同步营养");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, food_name, energy_kcal, protein, carbohydrate, fat, sodium, dietary_fiber,
                       vitamin_a, vitamin_b1, vitamin_b2, vitamin_c, vitamin_e, calcium, iron, zinc
                  FROM food_item
                 WHERE id = ? AND deleted = 0
                """, mapping.getFoodItemId());
        if (rows.isEmpty()) {
            throw BizException.notFound("关联的标准食品不存在");
        }

        Map<String, Object> food = rows.getFirst();
        material.setFoodItemId(mapping.getFoodItemId());
        material.setCalories(decimalValue(food.get("energy_kcal")));
        material.setProtein(decimalValue(food.get("protein")));
        material.setCarbohydrate(decimalValue(food.get("carbohydrate")));
        material.setFat(decimalValue(food.get("fat")));
        material.setSodium(decimalValue(food.get("sodium")));
        material.setFiber(decimalValue(food.get("dietary_fiber")));
        material.setVitaminA(decimalValue(food.get("vitamin_a")));
        material.setVitaminB1(decimalValue(food.get("vitamin_b1")));
        material.setVitaminB2(decimalValue(food.get("vitamin_b2")));
        material.setVitaminC(decimalValue(food.get("vitamin_c")));
        material.setVitaminE(decimalValue(food.get("vitamin_e")));
        material.setCalcium(decimalValue(food.get("calcium")));
        material.setIron(decimalValue(food.get("iron")));
        material.setZinc(decimalValue(food.get("zinc")));
        material.setNutritionSourceType("food_item");
        material.setNutritionSourceRefId(mapping.getFoodItemId());
        material.setNutritionSyncedAt(LocalDateTime.now());
        materialMapper.updateById(material);

        NutritionSyncResultVO vo = new NutritionSyncResultVO();
        vo.setMaterialId(material.getId());
        vo.setFoodItemId(mapping.getFoodItemId());
        vo.setMaterialName(material.getMaterialName());
        vo.setFoodName((String) food.get("food_name"));
        vo.setNutritionSourceType("food_item");
        return vo;
    }

    private BigDecimal decimalValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(raw));
    }

    // ==================== 导入导出相关方法 ====================

    /** 错误文件存储目录 */
    private static final String ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/material-import-errors/";

    /** 模板表头 */
    private static final String[] TEMPLATE_HEADERS = {
            "物料编码\n(自动生成，可填)",
            "物料名称\n(必填)",
            "规格\n(必填)",
            "单位\n(必填)",
            "物料分类\n(必填，见说明行)",
            "保质期(天)\n(必填，数字)",
            "临期提醒(天)\n(默认7)",
            "预警天数\n(默认15)",
            "最低库存\n(必填，数字)",
            "最高库存\n(必填，数字)",
            "存储条件",
            "备注",
            "状态\n(active/inactive)"
    };

    /** 列宽 */
    private static final int[] COLUMN_WIDTHS = {20, 20, 15, 10, 12, 14, 14, 12, 12, 12, 20, 25, 15};

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("物料导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("物料数据");

            // 创建样式
            // 说明行样式（浅蓝色背景）
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 示例数据样式（红色文字）
            CellStyle sampleStyle = workbook.createCellStyle();
            sampleStyle.setAlignment(HorizontalAlignment.LEFT);
            sampleStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            sampleStyle.setBorderTop(BorderStyle.THIN);
            sampleStyle.setBorderBottom(BorderStyle.THIN);
            sampleStyle.setBorderLeft(BorderStyle.THIN);
            sampleStyle.setBorderRight(BorderStyle.THIN);
            Font sampleFont = workbook.createFont();
            sampleFont.setFontName("微软雅黑");
            sampleFont.setFontHeightInPoints((short) 10);
            sampleFont.setColor(IndexedColors.RED.getIndex());
            sampleStyle.setFont(sampleFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】前两行为示例数据（红色文字，编码以#开头），导入时自动跳过。物料编码留空则自动生成（格式：MAT+6位数字），填写已存在编码则覆盖更新。物料名称+规格在同租户内需唯一。状态：active(启用)、inactive(停用)。\n物料分类：" + getCategoryDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }

            // 第3-4行：示例数据（红色文字，编码以#开头）
            String[][] sampleData = {
                    {"#MAT-001", "金龙鱼大豆油（示例）", "5L/桶", "桶", "粮油", "365", "30", "90", "10", "100", "常温干燥处保存", "示例物料，请删除", "active"},
                    {"#MAT-002", "东北大米（示例）", "25kg/袋", "袋", "粮油", "180", "15", "60", "20", "200", "阴凉干燥处", "示例物料，请删除", "active"}
            };

            for (String[] data : sampleData) {
                Row dataRow = sheet.createRow(rowNum++);
                for (int i = 0; i < data.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(data[i]);
                    cell.setCellStyle(sampleStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();

        } catch (IOException e) {
            log.error("下载物料导入模板失败", e);
            throw new BizException("下载导入模板失败");
        }
    }

    @Override
    public MaterialImportResultDTO importMaterials(MultipartFile file) {
        try {
            List<MaterialImportDTO> importList = EasyExcel.read(file.getInputStream())
                    .head(MaterialImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();

            if (importList.isEmpty()) {
                throw new BizException("导入文件为空");
            }

            if (importList.size() > 5000) {
                throw new BizException("单次导入不能超过5000行，当前" + importList.size() + "行");
            }

            int total = 0;
            int successCount = 0;
            int failCount = 0;
            List<MaterialImportDTO> errorList = new ArrayList<>();

            Long tenantId = UserContext.getTenantId();
            Long orgId = UserContext.getOrgId();
            Set<Long> manageableOrgIds = resolveManageableOrgIds();

            // 获取有效的物料分类名称
            Set<String> validCategoryNames = getValidCategoryNames();

            // 构建现有物料编码→ID的映射（同租户内）用于覆盖更新
            Map<String, Long> existingCodeMap = buildMaterialCodeMap(tenantId);
            // 构建现有物料名称+规格的集合（同租户内）用于唯一性校验
            Map<String, Long> existingNameSpecMap = buildMaterialNameSpecMap(tenantId);
            // 跟踪本次导入新创建的物料名称+规格
            Map<String, Long> newNameSpecMap = new HashMap<>();
            // 跟踪本次导入使用的物料编码
            Set<String> usedCodes = new HashSet<>();

            for (int i = 0; i < importList.size(); i++) {
                MaterialImportDTO dto = importList.get(i);
                dto.setRowNum(i + 3);

                // 跳过示例数据（物料编码以#开头的是示例数据）
                if (dto.getMaterialCode() != null && dto.getMaterialCode().startsWith("#")) {
                    continue;
                }

                total++;

                try {
                    // 校验数据
                    String error = validateImportData(dto, existingNameSpecMap, newNameSpecMap, existingCodeMap, usedCodes, validCategoryNames, manageableOrgIds);
                    if (error != null) {
                        dto.setErrorMessage(error);
                        dto.setSuccess(false);
                        errorList.add(dto);
                        failCount++;
                        continue;
                    }

                    // 构建名称+规格唯一键
                    String nameSpecKey = buildNameSpecKey(dto.getMaterialName(), dto.getMaterialSpec());

                    // 判断是覆盖更新（编码重复）还是新增
                    String importCode = StrUtil.isNotBlank(dto.getMaterialCode()) ? dto.getMaterialCode() : null;
                    Long existingId = importCode != null ? existingCodeMap.get(importCode) : null;
                    if (existingId != null) {
                        // 编码重复 → 覆盖更新已有物料
                        Material material = materialMapper.selectById(existingId);
                        if (material != null) {
                            // 如果要停用，与页面停用做相同校验
                            if ("inactive".equals(dto.getStatus()) && "active".equals(material.getStatus())) {
                                String deactivatError = validateDeactivate(existingId);
                                if (deactivatError != null) {
                                    dto.setErrorMessage(deactivatError);
                                    dto.setSuccess(false);
                                    errorList.add(dto);
                                    failCount++;
                                    continue;
                                }
                            }

                            material.setMaterialName(dto.getMaterialName());
                            material.setSpec(dto.getMaterialSpec());
                            material.setUnit(dto.getUnit());
                            material.setMaterialCategory(dto.getCategoryName());
                            Integer oldShelfLifeDays = material.getShelfLifeDays();
                            Integer newShelfLifeDays = parseInteger(dto.getShelfLifeDays());
                            material.setShelfLifeDays(newShelfLifeDays);
                            material.setNearExpiryDays(dto.getNearExpiryDays() != null && StrUtil.isNotBlank(dto.getNearExpiryDays())
                                    ? parseInteger(dto.getNearExpiryDays()) : 7);
                            material.setWarningDays(dto.getWarningDays() != null && StrUtil.isNotBlank(dto.getWarningDays())
                                    ? parseInteger(dto.getWarningDays()) : 30);
                            material.setMinStock(parseStockValue(dto.getMinStock()));
                            material.setMaxStock(parseStockValue(dto.getMaxStock()));
                            material.setStorageConditions(dto.getStorageConditions());
                            material.setRemark(dto.getRemark());
                            if (StrUtil.isNotBlank(dto.getStatus())) {
                                material.setStatus(dto.getStatus());
                            }
                            materialMapper.updateById(material);
                            // 保质期变更时同步更新库存和入库单明细的到期日期
                            if (newShelfLifeDays != null && !newShelfLifeDays.equals(oldShelfLifeDays)) {
                                syncExpiryDate(existingId, newShelfLifeDays);
                            }
                        }
                        // 跟踪编码使用（避免文件内编码重复）
                        if (importCode != null) {
                            usedCodes.add(importCode);
                        }
                    } else {
                        // 新增物料
                        Material material = new Material();
                        material.setMaterialCode(StrUtil.isNotBlank(dto.getMaterialCode())
                                ? dto.getMaterialCode() : generateMaterialCode());
                        material.setMaterialName(dto.getMaterialName());
                        material.setSpec(dto.getMaterialSpec());
                        material.setUnit(dto.getUnit());
                        material.setMaterialCategory(dto.getCategoryName());
                        material.setShelfLifeDays(parseInteger(dto.getShelfLifeDays()));
                        material.setNearExpiryDays(dto.getNearExpiryDays() != null && StrUtil.isNotBlank(dto.getNearExpiryDays())
                                ? parseInteger(dto.getNearExpiryDays()) : 7);
                        material.setWarningDays(dto.getWarningDays() != null && StrUtil.isNotBlank(dto.getWarningDays())
                                ? parseInteger(dto.getWarningDays()) : 30);
                        material.setMinStock(parseStockValue(dto.getMinStock()));
                        material.setMaxStock(parseStockValue(dto.getMaxStock()));
                        material.setStorageConditions(dto.getStorageConditions());
                        material.setRemark(dto.getRemark());
                        material.setStatus(StrUtil.isNotBlank(dto.getStatus()) ? dto.getStatus() : "active");
                        material.setOrgId(orgId != null ? orgId : 0L);
                        material.setTenantId(tenantId);
                        materialMapper.insert(material);
                        newNameSpecMap.put(nameSpecKey, material.getId());
                        // 跟踪编码使用
                        if (material.getMaterialCode() != null) {
                            usedCodes.add(material.getMaterialCode());
                            existingCodeMap.put(material.getMaterialCode(), material.getId());
                        }
                    }

                    dto.setSuccess(true);
                    successCount++;

                } catch (Exception e) {
                    log.error("导入物料失败，行号：{}，错误：{}", dto.getRowNum(), e.getMessage());
                    dto.setErrorMessage("导入失败：" + e.getMessage());
                    dto.setSuccess(false);
                    errorList.add(dto);
                    failCount++;
                }
            }

            // 生成错误文件
            String errorFileUrl = null;
            if (!errorList.isEmpty()) {
                errorFileUrl = generateErrorFile(errorList);
            }

            MaterialImportResultDTO result = new MaterialImportResultDTO(
                    total,
                    successCount,
                    failCount,
                    !errorList.isEmpty(),
                    errorFileUrl
            );

            // 审计日志
            Map<String, Object> afterDataMap = new HashMap<>();
            afterDataMap.put("total", result.getTotal());
            afterDataMap.put("successCount", result.getSuccessCount());
            afterDataMap.put("failCount", result.getFailCount());
            if (!errorList.isEmpty()) {
                List<Map<String, Object>> failureDetails = errorList.stream().map(err -> {
                    Map<String, Object> f = new HashMap<>();
                    f.put("row", err.getRowNum());
                    f.put("materialName", err.getMaterialName());
                    f.put("materialSpec", err.getMaterialSpec());
                    f.put("error", err.getErrorMessage());
                    return f;
                }).collect(Collectors.toList());
                afterDataMap.put("failures", failureDetails);
            }

            auditLogService.log(AuditModule.WMS_MATERIAL, AuditOperationType.IMPORT, null, null,
                    "导入物料：共" + result.getTotal() + "条，成功" + result.getSuccessCount() + "条，失败" + result.getFailCount() + "条",
                    null, JSONUtil.toJsonStr(afterDataMap));

            return result;

        } catch (IOException e) {
            log.error("读取导入文件失败", e);
            throw new BizException("读取导入文件失败，请检查文件是否为正确的 Excel 格式");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导入物料失败，文件格式或内容异常", e);
            throw new BizException("导入失败，请确认使用正确的导入模板");
        }
    }

    @Override
    @DataScope
    public void exportMaterials(MaterialQueryDTO query, HttpServletResponse response) {
        String operationResult = "success";
        String errorMsg = null;
        int exportCount = 0;

        try {
            // 查询符合条件的物料
            LambdaQueryWrapper<Material> wrapper = buildListWrapper(query);
            List<Material> materials = materialMapper.selectList(wrapper);

            // 按库存状态筛选（库存状态是计算值，需内存过滤）
            if (StrUtil.isNotBlank(query.getStockStatus())) {
                materials = filterByStockStatus(materials, query.getStockStatus());
            }

            exportCount = materials.size();

            // 使用 POI 生成导出文件，格式与模板一致
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("物料数据");

            // 创建样式
            // 说明行样式
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            // 表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // 数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            Font dataFont = workbook.createFont();
            dataFont.setFontName("微软雅黑");
            dataFont.setFontHeightInPoints((short) 10);
            dataStyle.setFont(dataFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】物料编码留空则自动生成（格式：MAT+6位数字），填写已存在编码则覆盖更新。物料名称+规格在同租户内需唯一。状态：active(启用)、inactive(停用)。\n物料分类：" + getCategoryDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 12));

            // 第2行：表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, COLUMN_WIDTHS[i] * 256);
            }

            // 数据行
            for (Material material : materials) {
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        material.getMaterialCode() != null ? material.getMaterialCode() : "",
                        material.getMaterialName() != null ? material.getMaterialName() : "",
                        material.getSpec() != null ? material.getSpec() : "",
                        material.getUnit() != null ? material.getUnit() : "",
                        material.getMaterialCategory() != null ? material.getMaterialCategory() : "",
                        material.getShelfLifeDays() != null ? String.valueOf(material.getShelfLifeDays()) : "",
                        material.getNearExpiryDays() != null ? String.valueOf(material.getNearExpiryDays()) : "",
                        material.getWarningDays() != null ? String.valueOf(material.getWarningDays()) : "",
                        material.getMinStock() != null ? material.getMinStock().toBigInteger().toString() : "",
                        material.getMaxStock() != null ? material.getMaxStock().toBigInteger().toString() : "",
                        material.getStorageConditions() != null ? material.getStorageConditions() : "",
                        material.getRemark() != null ? material.getRemark() : "",
                        material.getStatus() != null ? material.getStatus() : ""
                };

                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String exportFileName = URLEncoder.encode("物料数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + exportFileName + ".xlsx");

            workbook.write(response.getOutputStream());
            workbook.close();

        } catch (Exception e) {
            operationResult = "failure";
            errorMsg = e.getMessage();
            log.error("导出物料失败", e);
            throw new BizException("导出物料失败");
        } finally {
            try {
                String desc = "success".equals(operationResult)
                        ? "导出物料数据（" + exportCount + "条）"
                        : "导出物料数据失败";
                log.info("导出物料审计日志：result={}, desc={}, userId={}", operationResult, desc, UserContext.getUserId());
                auditLogService.log(AuditModule.WMS_MATERIAL, AuditOperationType.EXPORT,
                        null, null, desc, null, null, operationResult, errorMsg);
            } catch (Exception logEx) {
                log.error("导出审计日志记录失败: {}", logEx.getMessage(), logEx);
            }
        }
    }

    @Override
    public void downloadErrorFile(String fileName, HttpServletResponse response) {
        try {
            File file = new File(ERROR_FILE_DIR + fileName);
            if (!file.exists()) {
                throw new BizException("错误文件不存在或已过期");
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);

            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(response.getOutputStream());
            }

        } catch (IOException e) {
            log.error("下载错误文件失败", e);
            throw new BizException("下载错误文件失败");
        }
    }

    // ==================== 导入导出辅助方法 ====================

    /**
     * 生成错误文件
     */
    private String generateErrorFile(List<MaterialImportDTO> errorList) {
        try {
            File dir = new File(ERROR_FILE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String fileName = "material_import_errors_" + System.currentTimeMillis() + ".xlsx";
            String filePath = ERROR_FILE_DIR + fileName;

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("导入失败数据");

            // 创建样式
            CellStyle tipStyle = workbook.createCellStyle();
            tipStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            tipStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tipStyle.setAlignment(HorizontalAlignment.LEFT);
            tipStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            tipStyle.setWrapText(true);
            Font tipFont = workbook.createFont();
            tipFont.setFontName("微软雅黑");
            tipFont.setFontHeightInPoints((short) 10);
            tipStyle.setFont(tipFont);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setWrapText(true);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);
            Font headerFont = workbook.createFont();
            headerFont.setFontName("微软雅黑");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            CellStyle errorStyle = workbook.createCellStyle();
            errorStyle.setAlignment(HorizontalAlignment.LEFT);
            errorStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            errorStyle.setBorderTop(BorderStyle.THIN);
            errorStyle.setBorderBottom(BorderStyle.THIN);
            errorStyle.setBorderLeft(BorderStyle.THIN);
            errorStyle.setBorderRight(BorderStyle.THIN);
            Font errorFont = workbook.createFont();
            errorFont.setFontName("微软雅黑");
            errorFont.setFontHeightInPoints((short) 10);
            errorFont.setColor(IndexedColors.RED.getIndex());
            errorStyle.setFont(errorFont);

            int rowNum = 0;

            // 第1行：说明行
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(30);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修改后重新导入。\n物料分类：" + getCategoryDesc() + "。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 13));

            // 第2行：表头（增加失败原因列）
            String[] errorHeaders = new String[TEMPLATE_HEADERS.length + 1];
            System.arraycopy(TEMPLATE_HEADERS, 0, errorHeaders, 0, TEMPLATE_HEADERS.length);
            errorHeaders[TEMPLATE_HEADERS.length] = "失败原因";

            int[] errorWidths = new int[COLUMN_WIDTHS.length + 1];
            System.arraycopy(COLUMN_WIDTHS, 0, errorWidths, 0, COLUMN_WIDTHS.length);
            errorWidths[COLUMN_WIDTHS.length] = 40;

            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(45);
            for (int i = 0; i < errorHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(errorHeaders[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, errorWidths[i] * 256);
            }

            // 数据行（红色文字）
            for (MaterialImportDTO dto : errorList) {
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        dto.getMaterialCode() != null ? dto.getMaterialCode() : "",
                        dto.getMaterialName() != null ? dto.getMaterialName() : "",
                        dto.getMaterialSpec() != null ? dto.getMaterialSpec() : "",
                        dto.getUnit() != null ? dto.getUnit() : "",
                        dto.getCategoryName() != null ? dto.getCategoryName() : "",
                        dto.getShelfLifeDays() != null ? dto.getShelfLifeDays() : "",
                        dto.getNearExpiryDays() != null ? dto.getNearExpiryDays() : "",
                        dto.getWarningDays() != null ? dto.getWarningDays() : "",
                        dto.getMinStock() != null ? dto.getMinStock() : "",
                        dto.getMaxStock() != null ? dto.getMaxStock() : "",
                        dto.getStorageConditions() != null ? dto.getStorageConditions() : "",
                        dto.getRemark() != null ? dto.getRemark() : "",
                        dto.getStatus() != null ? dto.getStatus() : "",
                        dto.getErrorMessage() != null ? dto.getErrorMessage() : ""
                };

                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(errorStyle);
                }
            }

            try (FileOutputStream fos = new FileOutputStream(filePath)) {
                workbook.write(fos);
            }
            workbook.close();

            return "/api/v1/wms/materials/import/errors/" + fileName;

        } catch (Exception e) {
            log.error("生成错误文件失败", e);
            return null;
        }
    }

    /**
     * 获取物料分类说明文案
     */
    private String getCategoryDesc() {
        List<String> names = getValidCategoryNamesList();
        if (names.isEmpty()) {
            return "请在字典分类维护中配置物料分类";
        }
        return String.join("、", names);
    }

    /**
     * 获取有效的物料分类名称集合
     */
    private Set<String> getValidCategoryNames() {
        return new HashSet<>(getValidCategoryNamesList());
    }

    /**
     * 获取有效的物料分类名称列表
     */
    private List<String> getValidCategoryNamesList() {
        return jdbcTemplate.query(
                "SELECT dict_name FROM sys_dict WHERE dict_type = 'material_category' AND status = 'active' AND deleted = 0 ORDER BY sort_order",
                (rs, rowNum) -> rs.getString("dict_name")
        );
    }

    /**
     * 校验导入数据
     */
    private String validateImportData(MaterialImportDTO dto,
                                       Map<String, Long> existingNameSpecMap,
                                       Map<String, Long> newNameSpecMap,
                                       Map<String, Long> existingCodeMap,
                                       Set<String> usedCodes,
                                       Set<String> validCategoryNames,
                                       Set<Long> manageableOrgIds) {
        // 校验必填字段
        if (StrUtil.isBlank(dto.getMaterialName())) {
            return "物料名称不能为空";
        }
        if (StrUtil.isBlank(dto.getMaterialSpec())) {
            return "规格不能为空";
        }
        if (StrUtil.isBlank(dto.getUnit())) {
            return "单位不能为空";
        }
        if (StrUtil.isBlank(dto.getCategoryName())) {
            return "物料分类不能为空";
        }
        if (StrUtil.isBlank(dto.getShelfLifeDays())) {
            return "保质期天数不能为空";
        }
        if (StrUtil.isBlank(dto.getMinStock())) {
            return "最低库存不能为空";
        }
        if (StrUtil.isBlank(dto.getMaxStock())) {
            return "最高库存不能为空";
        }

        // 校验物料分类
        if (!validCategoryNames.contains(dto.getCategoryName())) {
            return "物料分类无效，有效值：" + String.join("、", validCategoryNames);
        }

        // 校验数字字段
        Integer shelfLifeDays = parseInteger(dto.getShelfLifeDays());
        if (shelfLifeDays == null || shelfLifeDays <= 0) {
            if (StrUtil.isNotBlank(dto.getShelfLifeDays())) {
                try {
                    double val = Double.parseDouble(dto.getShelfLifeDays().trim());
                    if (val != (int) val || (int) val <= 0) {
                        return "保质期天数必须为正整数";
                    }
                } catch (NumberFormatException ignored) {}
            }
            return "保质期天数必须为正整数";
        }

        BigDecimal minStock = parseBigDecimal(dto.getMinStock());
        if (minStock == null || minStock.compareTo(BigDecimal.ZERO) < 0) {
            return "最低库存必须为非负整数";
        }
        minStock = minStock.setScale(0, java.math.RoundingMode.DOWN);

        BigDecimal maxStock = parseBigDecimal(dto.getMaxStock());
        if (maxStock == null || maxStock.compareTo(BigDecimal.ZERO) < 0) {
            return "最高库存必须为非负整数";
        }
        maxStock = maxStock.setScale(0, java.math.RoundingMode.DOWN);

        // 校验库存范围
        if (maxStock.compareTo(minStock) < 0) {
            return "最高库存不能小于最低库存";
        }

        // 校验临期提醒天数
        if (StrUtil.isNotBlank(dto.getNearExpiryDays())) {
            try {
                double nearVal = Double.parseDouble(dto.getNearExpiryDays().trim());
                if (nearVal != (int) nearVal || (int) nearVal <= 0) {
                    return "临期提醒天数必须为正整数";
                }
            } catch (NumberFormatException e) {
                return "临期提醒天数必须为正整数";
            }
        }

        // 校验预警天数
        if (StrUtil.isNotBlank(dto.getWarningDays())) {
            try {
                double warnVal = Double.parseDouble(dto.getWarningDays().trim());
                if (warnVal != (int) warnVal || (int) warnVal <= 0) {
                    return "预警天数必须为正整数";
                }
            } catch (NumberFormatException e) {
                return "预警天数必须为正整数";
            }
        }

        Integer nearExpiryDays = StrUtil.isNotBlank(dto.getNearExpiryDays()) ? parseInteger(dto.getNearExpiryDays()) : 7;
        Integer warningDays = StrUtil.isNotBlank(dto.getWarningDays()) ? parseInteger(dto.getWarningDays()) : 30;
        if (nearExpiryDays == null) nearExpiryDays = 7;
        if (warningDays == null) warningDays = 30;

        if (nearExpiryDays > warningDays || warningDays > shelfLifeDays) {
            return "临期提醒天数需小于等于预警天数，且预警天数需小于等于保质期";
        }

        // 校验状态
        if (StrUtil.isNotBlank(dto.getStatus()) && !"active".equals(dto.getStatus()) && !"inactive".equals(dto.getStatus())) {
            return "状态无效，必须为：active 或 inactive";
        }

        // 校验名称+规格唯一性（文件内 + 数据库）
        String nameSpecKey = buildNameSpecKey(dto.getMaterialName(), dto.getMaterialSpec());
        if (newNameSpecMap.containsKey(nameSpecKey)) {
            return "文件内物料名称和规格组合重复";
        }
        if (existingNameSpecMap.containsKey(nameSpecKey)) {
            // 检查是否是同一编码（编码重复的覆盖更新允许name+spec重复）
            String importCode = StrUtil.isNotBlank(dto.getMaterialCode()) ? dto.getMaterialCode() : null;
            Long codeMatchId = importCode != null ? existingCodeMap.get(importCode) : null;
            Long nameSpecMatchId = existingNameSpecMap.get(nameSpecKey);
            if (!Objects.equals(codeMatchId, nameSpecMatchId)) {
                return "物料名称和规格组合已存在";
            }
        }

        // 校验编码唯一性（文件内）
        String importCode = StrUtil.isNotBlank(dto.getMaterialCode()) ? dto.getMaterialCode() : null;
        if (importCode != null && usedCodes.contains(importCode)) {
            return "文件内物料编码重复";
        }

        // 数据权限校验：覆盖更新时检查已有物料的orgId是否在权限范围内
        if (importCode != null) {
            Long existingId = existingCodeMap.get(importCode);
            if (existingId != null && manageableOrgIds != null) {
                Material existing = materialMapper.selectById(existingId);
                if (existing != null && !manageableOrgIds.contains(existing.getOrgId())) {
                    return "无权限修改该物料：" + dto.getMaterialName();
                }
            }
        }

        return null;
    }

    /**
     * 自动生成物料编码
     */
    private String generateMaterialCode() {
        return "MAT-" + String.valueOf(System.currentTimeMillis()).substring(7);
    }

    /**
     * 构建名称+规格唯一键
     */
    private String buildNameSpecKey(String materialName, String materialSpec) {
        return materialName + "||" + materialSpec;
    }

    /**
     * 构建现有物料名称+规格到ID的映射（同租户内）
     */
    private Map<String, Long> buildMaterialNameSpecMap(Long tenantId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, Material::getTenantId, tenantId)
                .isNull(tenantId == null, Material::getTenantId);
        List<Material> all = materialMapper.selectList(wrapper);
        return all.stream().collect(Collectors.toMap(
                m -> buildNameSpecKey(m.getMaterialName(), m.getSpec()),
                Material::getId,
                (a, b) -> a
        ));
    }

    /**
     * 构建现有物料编码到ID的映射（同租户内）
     */
    private Map<String, Long> buildMaterialCodeMap(Long tenantId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, Material::getTenantId, tenantId)
                .isNull(tenantId == null, Material::getTenantId);
        List<Material> all = materialMapper.selectList(wrapper);
        return all.stream()
                .filter(m -> StrUtil.isNotBlank(m.getMaterialCode()))
                .collect(Collectors.toMap(
                        Material::getMaterialCode,
                        Material::getId,
                        (a, b) -> a
                ));
    }

    /**
     * 解析当前用户可管理的组织ID集合（null 表示全部权限）
     */
    private Set<Long> resolveManageableOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return scope.getOrgIds();
    }

    /**
     * 解析整数值
     */
    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析BigDecimal值
     */
    private BigDecimal parseBigDecimal(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析库存值（取整数） */
    private BigDecimal parseStockValue(String value) {
        BigDecimal result = parseBigDecimal(value);
        if (result != null) {
            result = result.setScale(0, java.math.RoundingMode.DOWN);
        }
        return result;
    }
}
