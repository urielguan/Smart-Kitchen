package com.xykj.wms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.wms.dto.StocktakeOrderApproveDTO;
import com.xykj.wms.dto.StocktakeOrderCreateDTO;
import com.xykj.wms.dto.StocktakeOrderQueryDTO;
import com.xykj.wms.dto.StocktakeOrderRejectDTO;
import com.xykj.wms.dto.StocktakeOrderUpdateDTO;
import com.xykj.wms.dto.StocktakeOrderVoidDTO;
import com.xykj.wms.dto.StocktakeSnapshotPreviewDTO;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.entity.StocktakeOperationLog;
import com.xykj.wms.entity.StocktakeOrder;
import com.xykj.wms.entity.StocktakeOrderItem;
import com.xykj.wms.entity.StocktakeOrderItemVersion;
import com.xykj.wms.entity.StocktakeOrderVersion;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.StocktakeOperationLogMapper;
import com.xykj.wms.mapper.StocktakeOrderItemMapper;
import com.xykj.wms.mapper.StocktakeOrderItemVersionMapper;
import com.xykj.wms.mapper.StocktakeOrderMapper;
import com.xykj.wms.mapper.StocktakeOrderVersionMapper;
import com.xykj.wms.service.StocktakeOrderService;
import com.xykj.wms.service.support.LocationAreaPostingService;
import com.xykj.wms.service.support.LocationAreaValidationService;
import com.xykj.wms.service.support.WarehouseStatusRefreshService;
import com.xykj.wms.vo.StocktakeOrderDetailVO;
import com.xykj.wms.vo.StocktakeOrderItemVO;
import com.xykj.wms.vo.StocktakeOrderListVO;
import com.xykj.wms.vo.StocktakeSnapshotPreviewVO;
import com.xykj.wms.vo.StocktakeStatisticsVO;
import com.xykj.wms.vo.StocktakeVersionDetailVO;
import com.xykj.wms.vo.StocktakeVersionSummaryVO;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class StocktakeOrderServiceImpl implements StocktakeOrderService {

    private static final Long DEFAULT_USER_ID = 1L;
    private static final String DEFAULT_USER_NAME = "系统";
    private static final BigDecimal DIFF_REASON_THRESHOLD = new BigDecimal("0.05");
    private static final long IMAGE_ATTACHMENT_MAX_SIZE = 20L * 1024 * 1024;
    private static final long VIDEO_ATTACHMENT_MAX_SIZE = 200L * 1024 * 1024;
    private static final long DOCUMENT_ATTACHMENT_MAX_SIZE = 50L * 1024 * 1024;
    private static final List<String> ALLOWED_ATTACHMENT_TYPES = Arrays.asList(
            "image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4", "video/quicktime", "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );
    private static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS = Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "pdf", "xlsx", "xls", "doc", "docx", "txt"
    );

    private final StocktakeOrderMapper stocktakeOrderMapper;
    private final StocktakeOrderItemMapper stocktakeOrderItemMapper;
    private final StocktakeOperationLogMapper stocktakeOperationLogMapper;
    private final StocktakeOrderVersionMapper stocktakeOrderVersionMapper;
    private final StocktakeOrderItemVersionMapper stocktakeOrderItemVersionMapper;
    private final InventoryMapper inventoryMapper;
    private final DataScopeService dataScopeService;
    private final WarehouseStatusRefreshService warehouseStatusRefreshService;
    private final FileStorageService fileStorageService;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
    private final LocationAreaValidationService locationAreaValidationService;
    private final LocationAreaPostingService locationAreaPostingService;
    private final com.xykj.wms.service.alert.MaterialAlertEngine materialAlertEngine;

    @Override
    @DataScope
    public PageResult<StocktakeOrderListVO> list(StocktakeOrderQueryDTO query) {
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            return PageResult.empty((long) query.getPageNum(), (long) query.getPageSize());
        }
        query.setTenantId(resolveTenantId());
        Page<StocktakeOrderListVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        stocktakeOrderMapper.selectStocktakePage(page, query);
        return PageResult.of(page);
    }

    @Override
    @DataScope
    public StocktakeStatisticsVO getStatistics(StocktakeOrderQueryDTO query) {
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            return new StocktakeStatisticsVO();
        }
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonthStart = monthStart.plusMonths(1);
        query.setTenantId(resolveTenantId());
        StocktakeStatisticsVO statistics = stocktakeOrderMapper.selectStatistics(monthStart, nextMonthStart, query);
        return statistics != null ? statistics : new StocktakeStatisticsVO();
    }

    @Override
    public StocktakeOrderDetailVO getDetail(Long id) {
        StocktakeOrder order = getOrder(id);
        StocktakeOrderDetailVO detail = stocktakeOrderMapper.selectStocktakeDetail(id, order.getOrgId(), order.getTenantId());
        if (detail == null) {
            throw BizException.notFound("盘点单不存在");
        }
        detail.setItems(stocktakeOrderItemMapper.selectByStocktakeId(id, order.getOrgId(), order.getTenantId()));
        detail.setVersions(stocktakeOrderMapper.selectVersionSummaryList(id, order.getOrgId(), order.getTenantId()));
        detail.setOperationLogs(stocktakeOperationLogMapper.selectByStocktakeId(id, order.getOrgId(), order.getTenantId()));
        return detail;
    }

    @Override
    public List<StocktakeVersionSummaryVO> getVersions(Long id) {
        StocktakeOrder order = getOrder(id);
        return stocktakeOrderMapper.selectVersionSummaryList(id, order.getOrgId(), order.getTenantId());
    }

    @Override
    public StocktakeVersionDetailVO getVersionDetail(Long id, Integer versionNo) {
        StocktakeOrder order = getOrder(id);
        StocktakeVersionDetailVO detail = stocktakeOrderMapper.selectVersionDetail(id, versionNo, order.getOrgId(), order.getTenantId());
        if (detail == null) {
            throw BizException.notFound("盘点版本不存在");
        }
        detail.setItems(stocktakeOrderItemMapper.selectVersionItems(id, versionNo, order.getOrgId(), order.getTenantId()));
        return detail;
    }

    @Override
    public List<StocktakeSnapshotPreviewVO> previewSnapshot(StocktakeSnapshotPreviewDTO dto) {
        List<Long> warehouseIds = normalizeIds(dto.getWarehouseIds(), dto.getWarehouseId());
        List<Long> locationIds = normalizeIds(dto.getLocationIds(), dto.getLocationId());
        List<Long> allowedOrgIds = resolveAllowedOrgIdsForInventory();
        Long tenantId = resolveTenantId();
        return inventoryMapper.selectStocktakeSnapshotCandidates(
                dto.getWarehouseId(),
                dto.getLocationId(),
                warehouseIds,
                locationIds,
                allowedOrgIds,
                tenantId
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(StocktakeOrderCreateDTO dto) {
        Long orgId = resolveCurrentOrgId();
        ensureOrgAllowed(orgId);
        Long tenantId = resolveTenantId();
        Long currentUserId = resolveCurrentUserId();

        List<Long> warehouseIds = normalizeIds(dto.getWarehouseIds(), dto.getWarehouseId());
        List<Long> locationIds = normalizeIds(dto.getLocationIds(), dto.getLocationId());
        validateActiveRanges(warehouseIds, locationIds, orgId, tenantId);

        List<StocktakeSnapshotPreviewVO> snapshotItems = previewSnapshot(buildPreviewDTO(warehouseIds, locationIds));
        if (snapshotItems.isEmpty()) {
            throw BizException.validationFailed("当前范围暂无可盘点物料");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                snapshotItems.stream().map(StocktakeSnapshotPreviewVO::getMaterialId).toList(),
                "创建盘点单"
        );

        StocktakeOrder order = new StocktakeOrder();
        applyCreateHeader(dto, order);
        order.setStocktakeNo(generateStocktakeNo());
        order.setStatus("draft");
        order.setOrgId(orgId);
        order.setTenantId(tenantId);
        order.setCreatedBy(currentUserId);
        order.setUpdatedBy(currentUserId);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setStartAt(LocalDateTime.now());
        initializeOrderSummary(order);
        stocktakeOrderMapper.insert(order);

        List<StocktakeOrderItem> items = buildSnapshotItems(order, snapshotItems);
        saveItems(order.getId(), items);
        refreshOrderSummary(order);
        stocktakeOrderMapper.updateById(order);

        insertOperationLog(order.getId(), "snapshot_generated", "生成快照", "根据仓库范围生成盘点快照明细");
        insertOperationLog(order.getId(), "create", "创建", "创建盘点单");
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, StocktakeOrderUpdateDTO dto) {
        StocktakeOrder order = getOrder(id);
        validateEditableStatus(order);
        if (dto.getItems() != null) {
            materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                    dto.getItems().stream().map(StocktakeOrderCreateDTO.StocktakeOrderItemDTO::getMaterialId).toList(),
                    "保存盘点单"
            );
        }

        applyUpdateHeader(dto, order);
        if ("rejected".equals(order.getStatus())) {
            order.setStatus("draft");
            order.setRejectRemark(null);
        }

        if (dto.getItems() != null) {
            applyEditableFields(id, dto.getItems());
        }

        refreshOrderSummary(order);
        Long currentUserId = resolveCurrentUserId();
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "save", "保存", "保存盘点单");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        StocktakeOrder order = getOrder(id);
        Long tenantId = resolveTenantId();
        Long currentUserId = resolveCurrentUserId();
        validateEditableStatus(order);

        List<StocktakeOrderItem> items = getOrderItems(id);
        if (items.isEmpty()) {
            throw BizException.validationFailed("盘点明细不能为空");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(StocktakeOrderItem::getMaterialId).toList(),
                "提交盘点单"
        );

        for (StocktakeOrderItem item : items) {
            if (item.getActualQty() == null) {
                throw BizException.validationFailed("提交前请完善所有明细的实盘数量");
            }
            if (item.getActualQty().compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.validationFailed("实盘数量不能小于0");
            }
            fillDiffFields(item);
            if (requiresDiffReason(item) && StrUtil.isBlank(item.getDiffReason())) {
                throw BizException.validationFailed("存在超阈值差异明细未填写差异原因");
            }
            stocktakeOrderItemMapper.updateById(item);
        }

        refreshOrderSummary(order);
        int nextVersionNo = (order.getVersionNo() == null ? 0 : order.getVersionNo()) + 1;
        order.setVersionNo(nextVersionNo);
        order.setStatus("pending");
        order.setRejectRemark(null);
        order.setApprovedBy(null);
        order.setApprovedAt(null);
        order.setApproveRemark(null);
        order.setSubmittedBy(currentUserId);
        order.setSubmittedAt(LocalDateTime.now());
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);

        persistVersion(order, items, nextVersionNo);
        insertOperationLog(id, "submit", "提交审核", "提交盘点单审核");
        refreshOrderStatusScope(order, items);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, StocktakeOrderApproveDTO dto) {
        StocktakeOrder order = getOrder(id);
        Long tenantId = resolveTenantId();
        Long currentUserId = resolveCurrentUserId();
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审核状态的盘点单可以审核通过");
        }

        List<StocktakeOrderItem> items = getOrderItems(id);
        if (items.isEmpty()) {
            throw BizException.validationFailed("盘点明细不能为空");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(StocktakeOrderItem::getMaterialId).toList(),
                "执行盘点库存变动"
        );

        for (StocktakeOrderItem item : items) {
            Map<String, Object> inventoryRow = inventoryMapper.selectStocktakeInventoryCheckRow(
                    item.getInventoryId()
            );
            if (inventoryRow == null) {
                throw BizException.conflict("库存记录已不存在，请刷新快照后重新提交");
            }
            Long warehouseId = toLong(inventoryRow.get("warehouse_id"));
            Long locationId = toLong(inventoryRow.get("location_id"));
            String batchNo = Objects.toString(inventoryRow.get("batch_no"), null);
            BigDecimal currentQuantity = toBigDecimal(inventoryRow.get("quantity"));
            LocalDateTime inventoryUpdatedAt = toLocalDateTime(inventoryRow.get("updated_at"));
            boolean batchChanged = !Objects.equals(StrUtil.blankToDefault(batchNo, null), StrUtil.blankToDefault(item.getBatchNo(), null));
            boolean locationChanged = !Objects.equals(locationId, item.getLocationId()) || !Objects.equals(warehouseId, item.getWarehouseId());
            boolean quantityChanged = currentQuantity.compareTo(defaultZero(item.getSystemQty())) != 0;
            boolean updatedAfterSubmit = order.getSubmittedAt() != null
                    && inventoryUpdatedAt != null
                    && inventoryUpdatedAt.isAfter(order.getSubmittedAt());
            if (batchChanged || locationChanged || quantityChanged || updatedAfterSubmit) {
                throw BizException.conflict("库存快照已失效，请刷新快照后重新提交");
            }

            fillDiffFields(item);
            Inventory inventory = inventoryMapper.selectById(item.getInventoryId());
            if (inventory == null) {
                throw BizException.conflict("库存记录已不存在，请刷新快照后重新提交");
            }
            BigDecimal diffQty = defaultZero(item.getDiffQty());
            BigDecimal newQuantity = defaultZero(inventory.getQuantity()).add(diffQty);
            if (newQuantity.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.validationFailed("盘亏后库存不能为负数");
            }
            if (diffQty.compareTo(BigDecimal.ZERO) != 0) {
                inventory.setQuantity(newQuantity);
                inventory.setTotalCost(defaultZero(inventory.getUnitCost()).multiply(newQuantity));
                inventoryMapper.updateById(inventory);
            }
            stocktakeOrderItemMapper.updateById(item);
        }
        locationAreaPostingService.postStocktakeApprovedArea(order, items, locationAreaValidationService);

        refreshOrderSummary(order);
        order.setStatus("completed");
        order.setApprovedBy(currentUserId);
        order.setApprovedAt(LocalDateTime.now());
        order.setEndAt(LocalDateTime.now());
        order.setApproveRemark(dto == null ? null : dto.getApproveRemark());
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "approve", "审核通过", "审核通过盘点单");
        refreshOrderStatusScope(order, items);
        // 盘点导致库存变更后触发物料告警校验
        List<Long> changedMaterialIds = items.stream()
                .filter(i -> defaultZero(i.getDiffQty()).compareTo(BigDecimal.ZERO) != 0)
                .map(StocktakeOrderItem::getMaterialId).distinct().toList();
        if (!changedMaterialIds.isEmpty()) {
            scheduleMaterialAlertCheck(changedMaterialIds);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, StocktakeOrderRejectDTO dto) {
        StocktakeOrder order = getOrder(id);
        Long currentUserId = resolveCurrentUserId();
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审核状态的盘点单可以驳回");
        }
        order.setStatus("rejected");
        order.setRejectRemark(dto.getRejectRemark());
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "reject", "驳回", dto.getRejectRemark());
        refreshOrderStatusScope(order, getOrderItems(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void voidOrder(Long id, StocktakeOrderVoidDTO dto) {
        StocktakeOrder order = getOrder(id);
        Long currentUserId = resolveCurrentUserId();
        validateEditableStatus(order);
        order.setStatus("voided");
        order.setVoidReason(dto.getVoidReason());
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "void", "作废", dto.getVoidReason());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refreshSnapshot(Long id) {
        StocktakeOrder order = getOrder(id);
        validateEditableStatus(order);

        List<Long> warehouseIds = normalizeIds(List.of(order.getWarehouseId()), order.getWarehouseId());
        List<Long> locationIds = normalizeIds(order.getLocationId() != null ? List.of(order.getLocationId()) : null, order.getLocationId());

        List<StocktakeSnapshotPreviewVO> snapshotItems = previewSnapshot(buildPreviewDTO(warehouseIds, locationIds));
        if (snapshotItems.isEmpty()) {
            throw BizException.validationFailed("当前范围暂无可盘点物料");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                snapshotItems.stream().map(StocktakeSnapshotPreviewVO::getMaterialId).toList(),
                "刷新盘点快照"
        );

        stocktakeOrderItemMapper.deleteByStocktakeId(id);
        List<StocktakeOrderItem> items = buildSnapshotItems(order, snapshotItems);
        saveItems(id, items);
        refreshOrderSummary(order);
        Long currentUserId = resolveCurrentUserId();
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "refresh_snapshot", "刷新快照", "重新生成盘点快照明细");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadAttachments(Long id, MultipartFile[] files) {
        StocktakeOrder order = getOrder(id);
        Long currentUserId = resolveCurrentUserId();
        if (files == null || files.length == 0) {
            throw BizException.badRequest("请选择要上传的附件");
        }

        List<String> attachments = new ArrayList<>(order.getAttachments() == null ? Collections.emptyList() : order.getAttachments());
        for (MultipartFile file : files) {
            validateAttachment(file);
            String url = fileStorageService.upload(file, "stocktake");
            attachments.add(url);
        }

        order.setAttachments(attachments);
        order.setUpdatedBy(currentUserId);
        order.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderMapper.updateById(order);
        insertOperationLog(id, "upload_attachment", "上传附件", "上传盘点单附件");
    }

    @Override
    public void downloadAttachment(Long id, String url, HttpServletResponse response) {
        StocktakeOrder order = getOrder(id);
        if (StrUtil.isBlank(url)) {
            throw BizException.badRequest("附件地址不能为空");
        }
        List<String> attachments = order.getAttachments() == null ? Collections.emptyList() : order.getAttachments();
        if (!attachments.contains(url)) {
            throw BizException.notFound("附件不存在");
        }

        FileStorageService.StoredFile storedFile = fileStorageService.download(url);
        String fileName = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : url;
        try (InputStream inputStream = storedFile.inputStream()) {
            response.setContentType(
                    StrUtil.isBlank(storedFile.contentType())
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : storedFile.contentType()
            );
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (storedFile.size() != null) {
                response.setContentLengthLong(storedFile.size());
            }
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + encodeFileName(fileName));
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("下载盘点附件失败: id={}, url={}", id, url, e);
            throw new BizException("附件下载失败，请稍后重试");
        }
    }

    @Override
    @DataScope
    public void export(StocktakeOrderQueryDTO query, HttpServletResponse response) {
        query.setPageNum(1);
        query.setPageSize(10000);
        query.setTenantId(resolveTenantId());
        Page<StocktakeOrderListVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        stocktakeOrderMapper.selectStocktakePage(page, query);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("盘点历史");
            writeHeader(sheet);
            int rowIndex = 1;
            for (StocktakeOrderListVO record : page.getRecords()) {
                Row row = sheet.createRow(rowIndex++);
                writeCell(row, 0, record.getStocktakeNo());
                writeCell(row, 1, record.getStocktakeDate() == null ? "" : record.getStocktakeDate().toString());
                writeCell(row, 2, record.getCheckerName());
                writeCell(row, 3, record.getWarehouseName());
                writeCell(row, 4, StrUtil.blankToDefault(record.getLocationName(), "全部仓位"));
                writeCell(row, 5, record.getItemCount());
                writeCell(row, 6, formatRate(record.getDiffRate()));
                writeCell(row, 7, translateStatus(record.getStatus()));
                writeCell(row, 8, record.getCreatedAt() == null ? "" : record.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            }
            for (int i = 0; i < 9; i++) {
                sheet.autoSizeColumn(i);
            }

            String fileName = URLEncoder.encode("盘点历史.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);
            try (ServletOutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                outputStream.flush();
            }
        } catch (IOException e) {
            log.error("导出盘点历史失败", e);
            throw new BizException("导出盘点历史失败，请稍后重试");
        }
    }

    private void refreshOrderStatusScope(StocktakeOrder order, List<StocktakeOrderItem> items) {
        if (items == null || items.isEmpty()) {
            warehouseStatusRefreshService.refreshLocationAndWarehouse(order.getWarehouseId(), order.getLocationId());
            return;
        }
        List<String> refreshedRanges = new ArrayList<>();
        for (StocktakeOrderItem item : items) {
            Long warehouseId = item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId();
            Long locationId = item.getLocationId();
            String key = warehouseId + "_" + locationId;
            if (!refreshedRanges.contains(key)) {
                refreshedRanges.add(key);
                warehouseStatusRefreshService.refreshLocationAndWarehouse(warehouseId, locationId);
            }
        }
    }

    private void applyCreateHeader(StocktakeOrderCreateDTO dto, StocktakeOrder order) {
        List<Long> warehouseIds = normalizeIds(dto.getWarehouseIds(), dto.getWarehouseId());
        List<Long> locationIds = normalizeIds(dto.getLocationIds(), dto.getLocationId());
        order.setWarehouseId(warehouseIds.get(0));
        order.setLocationId(locationIds.isEmpty() ? null : locationIds.get(0));
        order.setWarehouseIds(warehouseIds);
        order.setLocationIds(locationIds);
        order.setStocktakeType(StrUtil.blankToDefault(dto.getStocktakeType(), "regular"));
        order.setStocktakeDate(dto.getStocktakeDate());
        order.setStartAt(dto.getStartAt());
        order.setEndAt(dto.getEndAt());
        order.setCheckerId(dto.getCheckerId() == null ? resolveCurrentUserId() : dto.getCheckerId());
        order.setCheckerName(dto.getCheckerId() == null ? resolveCurrentUserName() : StrUtil.blankToDefault(dto.getCheckerName(), resolveCurrentUserName()));
        order.setRemark(dto.getRemark());
        order.setAttachments(dto.getAttachments() == null ? new ArrayList<>() : new ArrayList<>(dto.getAttachments()));
    }

    private void applyUpdateHeader(StocktakeOrderUpdateDTO dto, StocktakeOrder order) {
        List<Long> warehouseIds = normalizeIds(dto.getWarehouseIds(), dto.getWarehouseId());
        List<Long> locationIds = normalizeIds(dto.getLocationIds(), dto.getLocationId());
        order.setWarehouseId(warehouseIds.get(0));
        order.setLocationId(locationIds.isEmpty() ? null : locationIds.get(0));
        order.setWarehouseIds(warehouseIds);
        order.setLocationIds(locationIds);
        order.setStocktakeDate(dto.getStocktakeDate());
        order.setStartAt(dto.getStartAt());
        order.setEndAt(dto.getEndAt());
        order.setCheckerId(dto.getCheckerId() == null ? resolveCurrentUserId() : dto.getCheckerId());
        order.setCheckerName(dto.getCheckerId() == null ? resolveCurrentUserName() : StrUtil.blankToDefault(dto.getCheckerName(), resolveCurrentUserName()));
        order.setRemark(dto.getRemark());
        order.setAttachments(dto.getAttachments() == null ? new ArrayList<>() : new ArrayList<>(dto.getAttachments()));
        if (StrUtil.isNotBlank(dto.getStocktakeType())) {
            order.setStocktakeType(dto.getStocktakeType());
        }
    }

    private void initializeOrderSummary(StocktakeOrder order) {
        order.setItemCount(0);
        order.setDiffQtyTotal(BigDecimal.ZERO);
        order.setProfitAmountTotal(BigDecimal.ZERO);
        order.setLossAmountTotal(BigDecimal.ZERO);
        order.setDiffRate(BigDecimal.ZERO);
        order.setSurplusQty(BigDecimal.ZERO);
        order.setDeficitQty(BigDecimal.ZERO);
        order.setSurplusAmount(BigDecimal.ZERO);
        order.setDeficitAmount(BigDecimal.ZERO);
    }

    private StocktakeSnapshotPreviewDTO buildPreviewDTO(List<Long> warehouseIds, List<Long> locationIds) {
        StocktakeSnapshotPreviewDTO previewDTO = new StocktakeSnapshotPreviewDTO();
        previewDTO.setWarehouseId(warehouseIds.isEmpty() ? null : warehouseIds.get(0));
        previewDTO.setLocationId(locationIds.isEmpty() ? null : locationIds.get(0));
        previewDTO.setWarehouseIds(warehouseIds);
        previewDTO.setLocationIds(locationIds);
        return previewDTO;
    }

    private void validateActiveRanges(List<Long> warehouseIds, List<Long> locationIds, Long orgId, Long tenantId) {
        for (Long warehouseId : warehouseIds) {
            if (locationIds.isEmpty()) {
                ensureRangeUnlocked(warehouseId, null, orgId, tenantId);
                continue;
            }
            for (Long locationId : locationIds) {
                ensureRangeUnlocked(warehouseId, locationId, orgId, tenantId);
            }
        }
    }

    private void ensureRangeUnlocked(Long warehouseId, Long locationId, Long orgId, Long tenantId) {
        Long activeCount = stocktakeOrderMapper.countActiveRange(
                warehouseId, locationId, null, orgId, tenantId
        );
        if (activeCount != null && activeCount > 0) {
            throw BizException.conflict("当前仓库/仓位范围已有进行中的盘点单");
        }
    }

    private List<Long> normalizeIds(List<Long> values, Long fallback) {
        List<Long> result = new ArrayList<>();
        if (values != null) {
            for (Long value : values) {
                if (value != null && !result.contains(value)) {
                    result.add(value);
                }
            }
        }
        if (fallback != null && !result.contains(fallback)) {
            result.add(fallback);
        }
        return result;
    }

    private List<StocktakeOrderItem> buildSnapshotItems(StocktakeOrder order, List<StocktakeSnapshotPreviewVO> snapshotItems) {
        List<StocktakeOrderItem> items = new ArrayList<>();
        for (StocktakeSnapshotPreviewVO snapshot : snapshotItems) {
            StocktakeOrderItem item = new StocktakeOrderItem();
            item.setStocktakeId(order.getId());
            item.setMaterialId(snapshot.getMaterialId());
            item.setMaterialName(snapshot.getMaterialName());
            item.setSpec(snapshot.getSpec());
            item.setUnit(snapshot.getUnit());
            item.setWarehouseId(snapshot.getWarehouseId());
            item.setLocationId(snapshot.getLocationId());
            item.setBatchNo(snapshot.getBatchNo());
            item.setInventoryId(snapshot.getInventoryId());
            item.setExpiryDate(snapshot.getExpiryDate());
            item.setSystemQty(defaultZero(snapshot.getQuantity()));
            item.setActualQty(null);
            item.setUnitCost(defaultZero(snapshot.getUnitCost()));
            item.setRecognitionSource("snapshot");
            item.setRemark(null);
            item.setLineRemark(null);
            fillDiffFields(item);
            items.add(item);
        }
        return items;
    }

    private void applyEditableFields(Long stocktakeId, List<StocktakeOrderCreateDTO.StocktakeOrderItemDTO> itemDTOs) {
        List<StocktakeOrderItem> existingItems = getOrderItems(stocktakeId);
        Map<Long, StocktakeOrderItem> existingItemMap = existingItems.stream()
                .filter(item -> item.getId() != null)
                .collect(java.util.stream.Collectors.toMap(StocktakeOrderItem::getId, item -> item));
        for (StocktakeOrderCreateDTO.StocktakeOrderItemDTO dto : itemDTOs) {
            if (dto.getId() == null) {
                throw BizException.validationFailed("保存盘点单时必须传入已存在的明细ID");
            }
            StocktakeOrderItem existing = existingItemMap.get(dto.getId());
            if (existing == null) {
                throw BizException.validationFailed("存在不属于当前盘点单的明细，无法保存");
            }
            existing.setActualQty(dto.getActualQty());
            existing.setDiffReason(dto.getDiffReason());
            existing.setRecognitionSource(dto.getRecognitionSource());
            existing.setAiConfidence(dto.getAiConfidence());
            existing.setRemark(dto.getRemark());
            existing.setLineRemark(dto.getLineRemark());
            fillDiffFields(existing);
            stocktakeOrderItemMapper.updateById(existing);
        }
    }

    private void saveItems(Long stocktakeId, List<StocktakeOrderItem> items) {
        for (StocktakeOrderItem item : items) {
            item.setId(null);
            item.setStocktakeId(stocktakeId);
            stocktakeOrderItemMapper.insert(item);
        }
    }

    private void refreshOrderSummary(StocktakeOrder order) {
        List<StocktakeOrderItem> items = getOrderItems(order.getId());
        BigDecimal diffQtyTotal = BigDecimal.ZERO;
        BigDecimal profitAmountTotal = BigDecimal.ZERO;
        BigDecimal lossAmountTotal = BigDecimal.ZERO;
        BigDecimal surplusQty = BigDecimal.ZERO;
        BigDecimal deficitQty = BigDecimal.ZERO;
        BigDecimal surplusAmount = BigDecimal.ZERO;
        BigDecimal deficitAmount = BigDecimal.ZERO;
        BigDecimal systemQtyTotal = BigDecimal.ZERO;

        for (StocktakeOrderItem item : items) {
            fillDiffFields(item);
            BigDecimal absDiffQty = defaultZero(item.getDiffQty()).abs();
            diffQtyTotal = diffQtyTotal.add(absDiffQty);
            systemQtyTotal = systemQtyTotal.add(defaultZero(item.getSystemQty()));
            if ("surplus".equals(item.getDiffDirection())) {
                surplusQty = surplusQty.add(absDiffQty);
                surplusAmount = surplusAmount.add(defaultZero(item.getDiffAmount()));
                profitAmountTotal = profitAmountTotal.add(defaultZero(item.getDiffAmount()));
            } else if ("deficit".equals(item.getDiffDirection())) {
                BigDecimal absQty = defaultZero(item.getDiffQty()).abs();
                BigDecimal absAmount = defaultZero(item.getDiffAmount()).abs();
                deficitQty = deficitQty.add(absQty);
                deficitAmount = deficitAmount.add(absAmount);
                lossAmountTotal = lossAmountTotal.add(absAmount);
            }
        }

        order.setItemCount(items.size());
        order.setDiffQtyTotal(diffQtyTotal);
        order.setProfitAmountTotal(profitAmountTotal);
        order.setLossAmountTotal(lossAmountTotal);
        order.setSurplusQty(surplusQty);
        order.setDeficitQty(deficitQty);
        order.setSurplusAmount(surplusAmount);
        order.setDeficitAmount(deficitAmount);
        if (systemQtyTotal.compareTo(BigDecimal.ZERO) == 0) {
            order.setDiffRate(diffQtyTotal.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.ONE);
        } else {
            order.setDiffRate(diffQtyTotal.abs().divide(systemQtyTotal, 4, RoundingMode.HALF_UP));
        }
    }

    private void persistVersion(StocktakeOrder order, List<StocktakeOrderItem> items, Integer versionNo) {
        StocktakeOrderVersion version = new StocktakeOrderVersion();
        BeanUtil.copyProperties(order, version);
        version.setId(null);
        version.setStocktakeId(order.getId());
        version.setVersionNo(versionNo);
        Long currentUserId = resolveCurrentUserId();
        version.setCreatedBy(currentUserId);
        version.setCreatedAt(LocalDateTime.now());
        version.setUpdatedBy(currentUserId);
        version.setUpdatedAt(LocalDateTime.now());
        stocktakeOrderVersionMapper.insert(version);

        for (StocktakeOrderItem item : items) {
            StocktakeOrderItemVersion itemVersion = new StocktakeOrderItemVersion();
            BeanUtil.copyProperties(item, itemVersion);
            itemVersion.setId(null);
            itemVersion.setStocktakeVersionId(version.getId());
            itemVersion.setStocktakeId(order.getId());
            itemVersion.setStocktakeItemId(item.getId());
            itemVersion.setVersionNo(versionNo);
            stocktakeOrderItemVersionMapper.insert(itemVersion);
        }
    }

    private void validateEditableStatus(StocktakeOrder order) {
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw BizException.validationFailed("只有草稿或已驳回状态的盘点单可以操作");
        }
    }

    private void fillDiffFields(StocktakeOrderItem item) {
        BigDecimal systemQty = defaultZero(item.getSystemQty());
        BigDecimal actualQty = item.getActualQty() == null ? null : defaultZero(item.getActualQty());
        BigDecimal diffQty = actualQty == null ? BigDecimal.ZERO : actualQty.subtract(systemQty);
        BigDecimal unitCost = defaultZero(item.getUnitCost());
        item.setSystemQty(systemQty);
        item.setActualQty(actualQty);
        item.setDiffQty(diffQty);
        item.setDiffAmount(diffQty.multiply(unitCost));
        if (diffQty.compareTo(BigDecimal.ZERO) > 0) {
            item.setDiffType("surplus");
            item.setDiffDirection("surplus");
        } else if (diffQty.compareTo(BigDecimal.ZERO) < 0) {
            item.setDiffType("deficit");
            item.setDiffDirection("deficit");
        } else {
            item.setDiffType("normal");
            item.setDiffDirection("normal");
        }
    }

    private boolean requiresDiffReason(StocktakeOrderItem item) {
        if (item.getActualQty() == null) {
            return false;
        }
        BigDecimal systemQty = defaultZero(item.getSystemQty());
        BigDecimal actualQty = defaultZero(item.getActualQty());
        if (systemQty.compareTo(BigDecimal.ZERO) == 0) {
            return actualQty.compareTo(BigDecimal.ZERO) > 0;
        }
        return defaultZero(item.getDiffQty()).abs().divide(systemQty, 4, RoundingMode.HALF_UP)
                .compareTo(DIFF_REASON_THRESHOLD) > 0;
    }

    private void insertOperationLog(Long stocktakeId, String action, String actionName, String content) {
        StocktakeOperationLog logEntity = new StocktakeOperationLog();
        logEntity.setStocktakeId(stocktakeId);
        logEntity.setAction(action);
        logEntity.setActionName(actionName);
        Long currentUserId = resolveCurrentUserId();
        logEntity.setOperatorId(currentUserId);
        logEntity.setOperatorName(resolveCurrentUserName());
        logEntity.setContent(content);
        logEntity.setCreatedAt(LocalDateTime.now());
        stocktakeOperationLogMapper.insert(logEntity);
    }

    private List<StocktakeOrderItem> getOrderItems(Long stocktakeId) {
        return stocktakeOrderItemMapper.selectList(
                new LambdaQueryWrapper<StocktakeOrderItem>().eq(StocktakeOrderItem::getStocktakeId, stocktakeId)
        );
    }

    /**
     * 事务提交后触发物料告警校验
     */
    private void scheduleMaterialAlertCheck(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return;
        log.info("注册物料告警校验回调（盘点）：materialIds={}", materialIds);
        try {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info("事务已提交，开始执行物料告警校验（盘点）：materialIds={}", materialIds);
                            try {
                                materialAlertEngine.checkMaterials(materialIds);
                            } catch (Exception e) {
                                log.error("事务提交后物料告警校验异常（盘点）: materialIds={}, error={}", materialIds, e.getMessage(), e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.warn("物料告警校验注册失败，直接调用（盘点）: materialIds={}", materialIds);
            materialAlertEngine.checkMaterials(materialIds);
        }
    }

    private StocktakeOrder getOrder(Long id) {
        Long tenantId = resolveTenantId();
        StocktakeOrder order = stocktakeOrderMapper.selectOne(
                new LambdaQueryWrapper<StocktakeOrder>()
                        .eq(StocktakeOrder::getId, id)
                        .eq(StocktakeOrder::getTenantId, tenantId)
        );
        if (order == null) {
            throw BizException.notFound("盘点单不存在");
        }
        ensureOrgAllowed(order.getOrgId());
        return order;
    }

    private String generateStocktakeNo() {
        return "PD-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + String.format("%05d", System.currentTimeMillis() % 100000);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Long resolveCurrentUserId() {
        Long userId = UserContext.getUserId();
        return userId != null ? userId : DEFAULT_USER_ID;
    }

    private String resolveCurrentUserName() {
        String realName = UserContext.getRealName();
        if (StrUtil.isNotBlank(realName)) {
            return realName;
        }
        String username = UserContext.getUsername();
        return StrUtil.isNotBlank(username) ? username : DEFAULT_USER_NAME;
    }

    private Long resolveCurrentOrgId() {
        Long orgId = UserContext.getOrgId();
        if (orgId == null) {
            throw BizException.badRequest("当前用户缺少组织信息");
        }
        return orgId;
    }

    private Long resolveTenantId() {
        Long tenantId = UserContext.getTenantId();
        return tenantId != null ? tenantId : 1L;
    }

    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private List<Long> resolveAllowedOrgIdsForInventory() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return new ArrayList<>(scope.getOrgIds());
    }

    private void validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的附件");
        }
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        String normalizedSuffix = StrUtil.blankToDefault(suffix, "").toLowerCase();
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase();
        boolean allowedType = StrUtil.isNotBlank(contentType) && ALLOWED_ATTACHMENT_TYPES.contains(contentType);
        boolean allowedSuffix = StrUtil.isNotBlank(normalizedSuffix) && ALLOWED_ATTACHMENT_EXTENSIONS.contains(normalizedSuffix);
        if (!allowedType && !allowedSuffix) {
            throw BizException.badRequest("不支持的附件格式，仅支持图片、视频、PDF、Excel、Word、TXT");
        }

        StocktakeAttachmentCategory category = resolveAttachmentCategory(normalizedSuffix, contentType);
        long maxSize = switch (category) {
            case IMAGE -> IMAGE_ATTACHMENT_MAX_SIZE;
            case VIDEO -> VIDEO_ATTACHMENT_MAX_SIZE;
            case DOCUMENT -> DOCUMENT_ATTACHMENT_MAX_SIZE;
        };
        if (file.getSize() > maxSize) {
            String message = switch (category) {
                case IMAGE -> "图片附件不能超过20MB";
                case VIDEO -> "视频附件不能超过200MB";
                case DOCUMENT -> "文档附件不能超过50MB";
            };
            throw BizException.badRequest(message);
        }
    }

    private StocktakeAttachmentCategory resolveAttachmentCategory(String suffix, String contentType) {
        if (contentType.startsWith("image/") || java.util.Set.of("jpg", "jpeg", "png", "gif", "webp").contains(suffix)) {
            return StocktakeAttachmentCategory.IMAGE;
        }
        if (contentType.startsWith("video/") || java.util.Set.of("mp4", "mov").contains(suffix)) {
            return StocktakeAttachmentCategory.VIDEO;
        }
        return StocktakeAttachmentCategory.DOCUMENT;
    }

    private enum StocktakeAttachmentCategory {
        IMAGE,
        VIDEO,
        DOCUMENT
    }

    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(" ", "T"));
    }

    private void writeHeader(XSSFSheet sheet) {
        Row header = sheet.createRow(0);
        writeCell(header, 0, "盘点单号");
        writeCell(header, 1, "盘点日期");
        writeCell(header, 2, "盘点人");
        writeCell(header, 3, "仓库");
        writeCell(header, 4, "仓位");
        writeCell(header, 5, "物料数");
        writeCell(header, 6, "差异率");
        writeCell(header, 7, "状态");
        writeCell(header, 8, "创建时间");
    }

    private void writeCell(Row row, int index, Object value) {
        Cell cell = row.createCell(index);
        cell.setCellValue(value == null ? "" : String.valueOf(value));
    }

    private String translateStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "draft" -> "草稿";
            case "pending" -> "待审核";
            case "rejected" -> "已驳回";
            case "completed" -> "已完成";
            case "voided" -> "已作废";
            default -> status;
        };
    }

    private String formatRate(BigDecimal rate) {
        if (rate == null) {
            return "";
        }
        return rate.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) + "%";
    }
}
