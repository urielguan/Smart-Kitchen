package com.xykj.wms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.wms.dto.InboundAreaValidationPreviewDTO;
import com.xykj.wms.dto.InboundImportResultDTO;
import com.xykj.wms.dto.InboundImportResultErrorDTO;
import com.xykj.wms.dto.InboundOrderActionDTO;
import com.xykj.wms.dto.InboundOrderCreateDTO;
import com.xykj.wms.dto.InboundOrderQueryDTO;
import com.xykj.wms.dto.InboundOrderUpdateDTO;
import com.xykj.wms.entity.InboundOrder;
import com.xykj.wms.entity.InboundOrderActionIdempotency;
import com.xykj.wms.entity.InboundOrderItem;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.Material;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.exception.InboundOrderValidationException;
import com.xykj.wms.mapper.InboundOrderActionIdempotencyMapper;
import com.xykj.wms.mapper.InboundOrderItemMapper;
import com.xykj.wms.mapper.InboundOrderMapper;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.LocationAreaLedgerMapper;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.wms.mapper.OutboundOrderItemMapper;
import com.xykj.wms.mapper.WarehouseMapper;
import com.xykj.wms.service.InboundOrderService;
import com.xykj.wms.service.StocktakeLockService;
import com.xykj.wms.service.support.InboundOrderPostingService;
import com.xykj.wms.service.support.InboundOrderValidationService;
import com.xykj.wms.service.support.LocationAreaPostingService;
import com.xykj.wms.service.support.LocationAreaValidationService;
import com.xykj.wms.service.support.WarehouseStatusRefreshService;
import com.xykj.wms.vo.InboundAreaValidationPreviewVO;
import com.xykj.wms.vo.InboundOrderStatisticsVO;
import com.xykj.wms.vo.InboundOrderVO;
import com.xykj.wms.vo.InboundOrderWriteResultVO;
import com.xykj.wms.vo.InboundSourceOrderOptionVO;
import com.xykj.wms.vo.PurchaseOrderItemForInboundVO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class InboundOrderServiceImpl implements InboundOrderService {

    private static final int POST_ERROR_MESSAGE_MAX_LENGTH = 500;
    private static final int INBOUND_IMPORT_MAX_ROWS = 5000;
    private static final String INBOUND_IMPORT_TEMPLATE_SHEET = "入库单导入模板";
    private static final String INBOUND_IMPORT_ERROR_FILE_PREFIX = "inbound_import_errors_";
    private static final String[] INBOUND_IMPORT_TEMPLATE_HEADERS = {"入库类型", "来源单号", "仓库编码", "仓位编码", "物料编码", "入库数量"};
    private static final int[] INBOUND_IMPORT_TEMPLATE_WIDTHS = {20, 24, 18, 18, 18, 14};

    private static final String DEFAULT_INVENTORY_STATUS = "normal";
    private static final String DEFAULT_BATCH_NO = "默认批次";
    private static final String PURCHASE_OCCUPYING_STATUS_SQL = "'draft','pending','approved'";
    private static final long MAX_ATTACHMENT_SIZE = 20L * 1024 * 1024;
    private static final long IMAGE_ATTACHMENT_MAX_SIZE = 20L * 1024 * 1024;
    private static final long VIDEO_ATTACHMENT_MAX_SIZE = 200L * 1024 * 1024;
    private static final long DOCUMENT_ATTACHMENT_MAX_SIZE = 50L * 1024 * 1024;
    private static final List<String> ALLOWED_ATTACHMENT_TYPES = List.of(
            "image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4", "video/quicktime", "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );
    private static final List<String> ALLOWED_ATTACHMENT_EXTENSIONS = List.of(
            "jpg", "jpeg", "png", "gif", "webp", "mp4", "mov", "pdf", "xlsx", "xls", "doc", "docx", "txt"
    );

    private final InboundOrderMapper     inboundOrderMapper;
    private final InboundOrderItemMapper inboundOrderItemMapper;
    private final DataScopeService dataScopeService;
    private final InventoryMapper        inventoryMapper;
    private final StocktakeLockService   stocktakeLockService;
    private final WarehouseStatusRefreshService warehouseStatusRefreshService;
    private final JdbcTemplate jdbcTemplate;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
    private final WarehouseMapper warehouseMapper;
    private final MaterialMapper materialMapper;
    private final LocationMapper locationMapper;
    private final AuditLogService auditLogService;
    @Autowired
    private OutboundOrderItemMapper outboundOrderItemMapper;
    private final LocationAreaLedgerMapper locationAreaLedgerMapper;
    private final InboundOrderValidationService inboundOrderValidationService;
    private final LocationAreaValidationService locationAreaValidationService;
    private final LocationAreaPostingService locationAreaPostingService;
    private final InboundOrderActionIdempotencyMapper inboundOrderActionIdempotencyMapper;

    @Autowired
    private InboundOrderPostingService inboundOrderPostingService;

    @Autowired
    private com.xykj.wms.service.alert.MaterialAlertEngine materialAlertEngine;

    public InboundOrderServiceImpl(InboundOrderMapper inboundOrderMapper,
                                   InboundOrderItemMapper inboundOrderItemMapper,
                                   DataScopeService dataScopeService,
                                   InventoryMapper inventoryMapper,
                                   StocktakeLockService stocktakeLockService,
                                   WarehouseStatusRefreshService warehouseStatusRefreshService,
                                   JdbcTemplate jdbcTemplate,
                                   MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
                                   WarehouseMapper warehouseMapper,
                                   MaterialMapper materialMapper,
                                   LocationMapper locationMapper,
                                   AuditLogService auditLogService,
                                   LocationAreaLedgerMapper locationAreaLedgerMapper,
                                   InboundOrderValidationService inboundOrderValidationService,
                                   LocationAreaValidationService locationAreaValidationService,
                                   LocationAreaPostingService locationAreaPostingService) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderItemMapper = inboundOrderItemMapper;
        this.dataScopeService = dataScopeService;
        this.inventoryMapper = inventoryMapper;
        this.stocktakeLockService = stocktakeLockService;
        this.warehouseStatusRefreshService = warehouseStatusRefreshService;
        this.jdbcTemplate = jdbcTemplate;
        this.materialCategoryCoefficientLockService = materialCategoryCoefficientLockService;
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.auditLogService = auditLogService;
        this.locationAreaLedgerMapper = locationAreaLedgerMapper;
        this.inboundOrderValidationService = inboundOrderValidationService;
        this.locationAreaValidationService = locationAreaValidationService;
        this.locationAreaPostingService = locationAreaPostingService;
        this.inboundOrderActionIdempotencyMapper = null;
    }

    @Autowired
    public InboundOrderServiceImpl(InboundOrderMapper inboundOrderMapper,
                                   InboundOrderItemMapper inboundOrderItemMapper,
                                   DataScopeService dataScopeService,
                                   InventoryMapper inventoryMapper,
                                   StocktakeLockService stocktakeLockService,
                                   WarehouseStatusRefreshService warehouseStatusRefreshService,
                                   JdbcTemplate jdbcTemplate,
                                   MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
                                   WarehouseMapper warehouseMapper,
                                   MaterialMapper materialMapper,
                                   LocationMapper locationMapper,
                                   AuditLogService auditLogService,
                                   LocationAreaLedgerMapper locationAreaLedgerMapper,
                                   InboundOrderValidationService inboundOrderValidationService,
                                   LocationAreaValidationService locationAreaValidationService,
                                   LocationAreaPostingService locationAreaPostingService,
                                   InboundOrderActionIdempotencyMapper inboundOrderActionIdempotencyMapper) {
        this.inboundOrderMapper = inboundOrderMapper;
        this.inboundOrderItemMapper = inboundOrderItemMapper;
        this.dataScopeService = dataScopeService;
        this.inventoryMapper = inventoryMapper;
        this.stocktakeLockService = stocktakeLockService;
        this.warehouseStatusRefreshService = warehouseStatusRefreshService;
        this.jdbcTemplate = jdbcTemplate;
        this.materialCategoryCoefficientLockService = materialCategoryCoefficientLockService;
        this.warehouseMapper = warehouseMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.auditLogService = auditLogService;
        this.locationAreaLedgerMapper = locationAreaLedgerMapper;
        this.inboundOrderValidationService = inboundOrderValidationService;
        this.locationAreaValidationService = locationAreaValidationService;
        this.locationAreaPostingService = locationAreaPostingService;
        this.inboundOrderActionIdempotencyMapper = inboundOrderActionIdempotencyMapper;
    }

    @Value("${file.upload.path:./upload}")
    private String uploadBasePath;

    /**
     * 启动时确保 scm_purchase_order_item 表存在 inbound_qty 和 remaining_inbound_qty 字段，
     * 避免入库审核回写采购订单时报 SQL 字段不存在的错误。
     */
    @PostConstruct
    public void ensurePurchaseOrderItemInboundColumns() {
        ensureInboundSubmitIdempotencyTable();
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scm_purchase_order_item' AND COLUMN_NAME = 'inbound_qty'",
                    Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE scm_purchase_order_item " +
                                "ADD COLUMN inbound_qty DECIMAL(12,2) DEFAULT 0.00 COMMENT '已入库数量', " +
                                "ADD COLUMN remaining_inbound_qty DECIMAL(12,2) DEFAULT 0.00 COMMENT '剩余待入库数量'"
                );
                jdbcTemplate.execute(
                        "UPDATE scm_purchase_order_item SET remaining_inbound_qty = order_qty WHERE remaining_inbound_qty = 0 OR remaining_inbound_qty IS NULL"
                );
                log.info("wms-service: 采购订单明细表入库数量字段迁移完成，已添加 inbound_qty 和 remaining_inbound_qty 列");
            } else {
                log.info("wms-service: 采购订单明细表入库数量字段已存在，跳过迁移");
            }
        } catch (Exception e) {
            log.warn("wms-service: 采购订单明细表入库数量字段迁移异常: {}", e.getMessage());
        }
    }

    @Override
    @DataScope
    public PageResult<InboundOrderVO> list(InboundOrderQueryDTO query) {
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            return PageResult.empty((long) query.getPageNum(), (long) query.getPageSize());
        }
        Page<InboundOrderVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        inboundOrderMapper.selectInboundPage(page, query);
        return PageResult.of(page);
    }

    @Override
    @DataScope
    public InboundOrderStatisticsVO getStatistics(InboundOrderQueryDTO query) {
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            InboundOrderStatisticsVO vo = new InboundOrderStatisticsVO();
            vo.setThisMonthTotalCount(0L);
            vo.setThisMonthPendingCount(0L);
            vo.setThisMonthApprovedCount(0L);
            vo.setThisMonthInboundAmount(BigDecimal.ZERO);
            return vo;
        }
        return inboundOrderMapper.selectStatistics(query);
    }

    @Override
    public InboundOrderVO getDetail(Long id) {
        InboundOrder order = getOrderById(id);
        InboundOrderVO vo = inboundOrderMapper.selectInboundDetail(order.getId());
        if (vo == null) throw BizException.notFound("入库单不存在");
        vo.setItems(inboundOrderItemMapper.selectItemsByInboundId(id));
        return vo;
    }

    @Override
    public List<InboundSourceOrderOptionVO> listSourceOrderOptions(String sourceType, Long excludeInboundOrderId) {
        return switch (StrUtil.blankToDefault(sourceType, "")) {
            case "purchase" -> listPurchaseSourceOrderOptions(excludeInboundOrderId);
            case "transfer" -> listTransferSourceOrderOptions();
            case "material_return" -> listMaterialReturnSourceOrderOptions();
            case "surplus" -> listSurplusSourceOrderOptions();
            case "return" -> List.of();
            case "donation", "other" -> List.of();
            default -> List.of();
        };
    }

    @Override
    public InboundAreaValidationPreviewVO previewAreaValidation(InboundAreaValidationPreviewDTO dto) {
        return locationAreaValidationService.previewInbound(dto);
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(INBOUND_IMPORT_TEMPLATE_SHEET);

            Row tipRow = sheet.createRow(0);
            tipRow.createCell(0).setCellValue("【说明】首行红色数据为示例占位，导入时会自动跳过。仅支持 .xlsx，单次最多 5000 行。请填写启用状态的入库类型中文名称，不要填写系统编码；当前导入支持“采购入库”“调拨入库”“退料入库”“盘盈入库”“销售退货入库”“捐赠入库”“其他入库”。采购入库的来源单号需填写有效采购单号，调拨入库的来源单号需填写已审核或已完成的调拨出库单号，退料入库的来源单号需填写已审核或已完成的领料出库单号，盘盈入库的来源单号需填写已审核盘点单号，销售退货入库的来源单号需填写已审核或已完成的销售退货出库单号；捐赠入库、其他入库无需填写来源单号。缺字段、格式错误、数量非法、来源单号不存在的行会整行失败并返回错误明细。");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, INBOUND_IMPORT_TEMPLATE_HEADERS.length - 1));

            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < INBOUND_IMPORT_TEMPLATE_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(INBOUND_IMPORT_TEMPLATE_HEADERS[i]);
                sheet.setColumnWidth(i, INBOUND_IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }

            Row sampleRow = sheet.createRow(2);
            sampleRow.createCell(0).setCellValue("采购入库");
            sampleRow.createCell(1).setCellValue("PO001");
            sampleRow.createCell(2).setCellValue("WH001");
            sampleRow.createCell(3).setCellValue("A-01-01");
            sampleRow.createCell(4).setCellValue("MAT001");
            sampleRow.createCell(5).setCellValue("10");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("入库单导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException ex) {
            log.error("下载入库导入模板失败", ex);
            throw BizException.badRequest("下载入库导入模板失败");
        }
    }

    @Override
    public InboundImportResultDTO importOrders(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择导入文件");
        }
        try (var workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<InboundImportResultErrorDTO> errors = new ArrayList<>();
            List<InboundImportErrorRow> errorRows = new ArrayList<>();
            int successCount = 0;
            int totalCount = 0;
            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum - 1 > INBOUND_IMPORT_MAX_ROWS) {
                throw BizException.badRequest("单次导入不能超过5000行");
            }
            for (int rowIndex = 2; rowIndex <= lastRowNum; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isInboundImportRowBlank(row)) {
                    continue;
                }
                totalCount++;
                InboundImportRow importRow = new InboundImportRow(
                        rowIndex + 1,
                        readCellAsString(row, 0),
                        readCellAsString(row, 1),
                        readCellAsString(row, 2),
                        readCellAsString(row, 3),
                        readCellAsString(row, 4),
                        readCellAsString(row, 5)
                );

                InboundImportRowValidation validation = validateInboundImportRow(importRow);
                if (validation.error() != null) {
                    appendImportError(importRow, validation.error(), errors, errorRows);
                    continue;
                }

                try {
                    persistImportedPurchaseRow(validation.resolvedRow());
                    successCount++;
                } catch (BizException ex) {
                    appendImportError(
                            importRow,
                            new InboundImportResultErrorDTO(importRow.rowNumber(), "row", ex.getMessage()),
                            errors,
                            errorRows
                    );
                } catch (Exception ex) {
                    log.error("导入入库单失败，行号：{}，错误：{}", importRow.rowNumber(), ex.getMessage(), ex);
                    appendImportError(
                            importRow,
                            new InboundImportResultErrorDTO(importRow.rowNumber(), "row", "导入失败"),
                            errors,
                            errorRows
                    );
                }
            }
            String errorFileName = errorRows.isEmpty() ? null : generateImportErrorFile(errorRows);
            return new InboundImportResultDTO(
                    successCount,
                    errorRows.size(),
                    totalCount,
                    !errorRows.isEmpty() && successCount > 0,
                    errors,
                    errorFileName
            );
        } catch (BizException ex) {
            throw ex;
        } catch (IOException ex) {
            throw BizException.badRequest("导入文件解析失败");
        }
    }

    @Override
    public void downloadImportErrorFile(String fileName, HttpServletResponse response) {
        File file = resolveImportErrorFile(fileName);
        if (!file.exists()) {
            throw BizException.notFound("错误文件不存在或已过期");
        }
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment;filename=" + file.getName());
        try (OutputStream outputStream = response.getOutputStream()) {
            Files.copy(file.toPath(), outputStream);
            outputStream.flush();
        } catch (IOException ex) {
            log.error("下载入库导入错误文件失败", ex);
            throw BizException.badRequest("下载入库导入错误文件失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(InboundOrderCreateDTO dto) {
        InboundOrder order = new InboundOrder();
        BeanUtil.copyProperties(dto, order, "items");
        // 过滤掉数量为0或null的物料（数量为0表示本次不入库）
        List<InboundOrderCreateDTO.InboundOrderItemDTO> validItems = dto.getItems().stream()
                .filter(i -> i.getQuantity() != null && i.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .collect(java.util.stream.Collectors.toList());
        if (validItems.isEmpty()) {
            throw BizException.validationFailed("入库明细中所有物料数量均为0，请至少填写一条数量大于0的物料");
        }
        validateRequiredItemFields(validItems, null);
        validateProductionDates(validItems, null);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                validItems.stream().map(InboundOrderCreateDTO.InboundOrderItemDTO::getMaterialId).toList(),
                "保存入库单"
        );
        // 采购入库：校验物料来源和防超入
        if ("purchase".equals(dto.getSourceType()) && dto.getSourceOrderId() != null) {
            validatePurchaseInboundItems(dto.getSourceOrderId(), toPurchaseValidationItems(validItems), null, null);
            // 用采购订单物料的单价覆盖，确保价格统一不可篡改
            overrideUnitCostFromPurchaseOrder(dto.getSourceOrderId(), validItems);
        }
        Long userOrgId = UserContext.getOrgId();
        if (userOrgId == null) {
            throw BizException.badRequest("当前用户缺少组织信息");
        }
        Long headerWarehouseId = validItems.stream()
                .map(InboundOrderCreateDTO.InboundOrderItemDTO::getWarehouseId)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        validateInboundWarehouseAssignable(headerWarehouseId, userOrgId, null, validItems);
        order.setWarehouseId(headerWarehouseId);
        order.setInboundNo(generateInboundNo());
        order.setStatus("draft");
        ensureOrgAllowed(userOrgId);
        String resolvedSupplierName = dto.getSupplierId() == null
                ? dto.getSupplierName()
                : resolveActiveSupplierName(dto.getSupplierId(), userOrgId);
        order.setOrgId(userOrgId);
        order.setReceivingOrgId(dto.getReceivingOrgId());
        order.setSourceOrderId(dto.getSourceOrderId());
        order.setSourceOrderNo(dto.getSourceOrderNo());
        order.setSupplierId(dto.getSupplierId());
        order.setSupplierName(resolvedSupplierName);
        order.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        inboundOrderMapper.insert(order);

        saveItems(order.getId(), validItems);
        recalcTotalAmount(order);

        log.info("新增入库单: id={}, no={}", order.getId(), order.getInboundNo());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadAttachments(Long id, MultipartFile[] files) {
        InboundOrder order = getOrderById(id);
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw BizException.validationFailed("只有草稿或已驳回的入库单可以上传附件");
        }
        if (files == null || files.length == 0) {
            throw BizException.badRequest("请选择要上传的附件");
        }

        List<String> attachments = new ArrayList<>(order.getAttachments() == null ? List.of() : order.getAttachments());
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        for (MultipartFile file : files) {
            validateAttachment(file);
            String suffix = FileUtil.getSuffix(file.getOriginalFilename());
            String fileName = IdUtil.fastSimpleUUID() + (StrUtil.isNotBlank(suffix) ? "." + suffix : "");
            String relativePath = "/inbound/attachments/" + datePath + "/" + fileName;
            saveUploadFile(file, relativePath);
            attachments.add("/upload" + relativePath);
        }

        order.setAttachments(attachments);
        order.setUpdatedAt(LocalDateTime.now());
        inboundOrderMapper.updateById(order);
    }

    @Override
    public void downloadAttachment(Long id, String url, HttpServletResponse response) {
        streamAttachment(id, url, response, false);
    }

    @Override
    public void previewAttachment(Long id, String url, HttpServletResponse response) {
        streamAttachment(id, url, response, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderWriteResultVO update(Long id, InboundOrderUpdateDTO dto) {
        InboundOrder order = getOrderById(id);
        inboundOrderValidationService.validateEditable(order);
        assertVersionMatches(dto.getVersion(), order);
        Long originalOrgId = order.getOrgId();
        BeanUtil.copyProperties(dto, order, CopyOptions.create().ignoreNullValue());
        // org_id 始终保持当前用户的组织ID，BeanUtil 可能会用 DTO 的 null 覆盖
        Long userOrgId = UserContext.getOrgId();
        order.setOrgId(userOrgId != null ? userOrgId : originalOrgId);
        Long effectiveOrgId = order.getOrgId();
        if (effectiveOrgId == null) {
            throw BizException.badRequest("当前入库单缺少组织信息");
        }
        ensureOrgAllowed(effectiveOrgId);
        String resolvedSupplierName = dto.getSupplierId() == null
                ? dto.getSupplierName()
                : resolveActiveSupplierName(dto.getSupplierId(), effectiveOrgId);
        order.setSourceOrderId(dto.getSourceOrderId());
        order.setSourceOrderNo(dto.getSourceOrderNo());
        order.setSupplierId(dto.getSupplierId());
        order.setSupplierName(resolvedSupplierName);
        if (dto.getReceivingOrgId() != null) {
            order.setReceivingOrgId(dto.getReceivingOrgId());
        }
        order.setAttachments(dto.getAttachments() != null ? new ArrayList<>(dto.getAttachments()) : order.getAttachments());
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            Long headerWarehouseId = dto.getItems().stream()
                    .map(InboundOrderCreateDTO.InboundOrderItemDTO::getWarehouseId)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            validateInboundWarehouseAssignable(headerWarehouseId, effectiveOrgId, order.getVersion(), dto.getItems());
            order.setWarehouseId(headerWarehouseId);
        }
        inboundOrderMapper.updateById(order);

        if (dto.getItems() != null) {
            // 过滤掉数量为0或null的物料（数量为0表示本次不入库）
            List<InboundOrderCreateDTO.InboundOrderItemDTO> validItems = dto.getItems().stream()
                    .filter(i -> i.getQuantity() != null && i.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .collect(java.util.stream.Collectors.toList());
            if (validItems.isEmpty()) {
                throw BizException.validationFailed("入库明细中所有物料数量均为0，请至少填写一条数量大于0的物料");
            }
            validateRequiredItemFields(validItems, order.getVersion());
            validateProductionDates(validItems, order.getVersion());
            materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                    validItems.stream().map(InboundOrderCreateDTO.InboundOrderItemDTO::getMaterialId).toList(),
                    "保存入库单"
            );
            // 采购入库：校验物料来源和防超入
            String effectiveSourceType = dto.getSourceType() != null ? dto.getSourceType() : order.getSourceType();
            Long effectiveSourceOrderId = dto.getSourceOrderId() != null ? dto.getSourceOrderId() : order.getSourceOrderId();
            if ("purchase".equals(effectiveSourceType) && effectiveSourceOrderId != null) {
                validatePurchaseInboundItems(effectiveSourceOrderId, toPurchaseValidationItems(validItems), order.getVersion(), id);
                // 用采购订单物料的单价覆盖，确保价格统一不可篡改
                overrideUnitCostFromPurchaseOrder(effectiveSourceOrderId, validItems);
            }
            List<InboundOrderItem> oldItems = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
            // 删除旧明细，重新插入
            inboundOrderItemMapper.delete(
                    new LambdaQueryWrapper<InboundOrderItem>()
                            .eq(InboundOrderItem::getInboundId, id));
            saveItems(id, validItems);
            recalcTotalAmount(order);
            refreshOrderStatusScope(order, oldItems);
        }
        log.info("更新入库单: id={}", id);
        order.setVersion(nextVersion(order.getVersion()));
        inboundOrderMapper.updateById(order);
        return toWriteResult(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        InboundOrder order = getOrderById(id);
        inboundOrderValidationService.validateDeletable(order);
        List<InboundOrderItem> oldItems = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        inboundOrderItemMapper.delete(
                new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getInboundId, id));
        inboundOrderMapper.deleteById(id);
        refreshOrderStatusScope(order, oldItems);
        log.info("删除入库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderWriteResultVO submit(Long id, InboundOrderActionDTO dto) {
        InboundOrder order = getOrderById(id);
        if (isSubmitAlreadyAccepted(order, dto.getIdempotencyKey())) {
            inboundOrderValidationService.rejectDuplicateSubmit(inboundOrderMapper.selectVersionById(id));
        }
        assertVersionMatches(dto.getVersion(), order);
        inboundOrderValidationService.validateSubmittable(order);
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        validateSubmitItemReferences(items, order.getVersion());
        if ("purchase".equals(order.getSourceType()) && order.getSourceOrderId() != null) {
            validatePurchaseInboundItems(order.getSourceOrderId(), toPurchaseValidationItemsFromEntity(items), order.getVersion(), id);
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(InboundOrderItem::getMaterialId).toList(),
                "提交入库单"
        );
        validateStocktakeUnlocked(order, "入库");
        LocationAreaValidationService.AreaValidationResult areaValidation = locationAreaValidationService.validateInboundOrderArea(order.getWarehouseId(), items);
        if (areaValidation.hasExceeded()) {
            inboundOrderValidationService.rejectSubmitAreaExceeded(order.getVersion(), areaValidation.globalMessage());
        }
        order.setStatus("pending");
        order.setSubmittedAt(LocalDateTime.now());
        // TODO: 从用户上下文获取当前用户
        order.setSubmittedBy(1L);
        order.setSubmitterName("系统管理员");
        order.setVersion(nextVersion(order.getVersion()));
        inboundOrderMapper.updateById(order);
        recordSubmitIdempotency(order, dto.getIdempotencyKey(), dto.getVersion());
        refreshOrderStatusScope(order);
        log.info("提交入库单: id={}", id);
        return toWriteResult(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = InboundOrderValidationException.class)
    public InboundOrderWriteResultVO approve(Long id, InboundOrderActionDTO dto) {
        InboundOrder order = getOrderById(id);
        ensureInboundPermission(order, "wms:inbound:approve", "无权审核入库单", "入库单审核权限校验失败");
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审批状态的入库单可以审批");
        }
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(InboundOrderItem::getMaterialId).toList(),
                "执行入库库存变动"
        );
        validateStocktakeUnlocked(order, "入库");
        LocationAreaValidationService.AreaValidationResult validation = locationAreaValidationService.validateInboundOrderArea(order.getWarehouseId(), items);
        validation.throwIfExceeded();

        order.setStatus("approved");
        order.setPostStatus("unposted");
        order.setPostErrorMessage(null);
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(dto != null ? dto.getApproveRemark() : null);
        order.setVersion(nextVersion(order.getVersion()));
        inboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("审批通过入库单: id={}", id);
        return toWriteResult(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = InboundOrderValidationException.class)
    public InboundOrderWriteResultVO postApproved(Long id, InboundOrderActionDTO dto) {
        InboundOrder order = getOrderById(id);
        assertVersionMatches(dto.getVersion(), order);
        if (!"approved".equals(order.getStatus()) || !isUnpostedStatus(order.getPostStatus())) {
            inboundOrderValidationService.rejectPostApprovedUnavailable(order.getVersion());
        }
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(InboundOrderItem::getMaterialId).toList(),
                "执行入库过账"
        );
        validateStocktakeUnlocked(order, "入库");
        LocationAreaValidationService.AreaValidationResult validation = locationAreaValidationService.validateInboundOrderArea(order.getWarehouseId(), items);
        validation.throwIfExceeded();

        order.setPostStatus("posting");
        order.setPostErrorMessage(null);
        inboundOrderMapper.updateById(order);

        try {
            inboundOrderPostingService.executeApprovedPosting(order, items, validation);
            order.setStatus("completed");
            order.setPostStatus("posted");
            order.setPostErrorMessage(null);
            inboundOrderMapper.updateById(order);
            refreshOrderStatusScope(order);
            log.info("执行已审核未过账入库单过账成功: id={}", id);
            // 库存变更后触发物料告警校验
            scheduleMaterialAlertCheck(items.stream().map(InboundOrderItem::getMaterialId).distinct().toList());
            return toWriteResult(order);
        } catch (RuntimeException ex) {
            order.setPostStatus("post_failed");
            order.setPostErrorMessage(resolvePostErrorMessage(ex));
            inboundOrderMapper.updateById(order);
            inboundOrderValidationService.rejectPostFailed(order.getVersion(), order.getPostErrorMessage());
            return toWriteResult(order);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InboundOrderWriteResultVO unapprove(Long id, InboundOrderActionDTO dto) {
        InboundOrder order = getOrderById(id);
        ensureInboundPermission(order, "wms:inbound:unapprove", "无权反审核入库单", "入库单反审核权限校验失败");
        assertVersionMatches(dto.getVersion(), order);
        if (!canUnapprove(order)) {
            inboundOrderValidationService.rejectUnapproveUnavailable(order.getVersion());
        }
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        if (isCompletedOrder(order)) {
            assertReversibleInboundAreaLedgers(order, items);
            reverseInboundInventory(order, items);
            locationAreaPostingService.reverseInboundApprovedArea(order, items);
            rollbackPurchaseOrderCompletedQty(order, items);
        }
        order.setStatus("draft");
        order.setPostStatus("unposted");
        order.setPostErrorMessage(null);
        order.setApprovedAt(null);
        order.setApproveRemark(null);
        order.setVersion(nextVersion(order.getVersion()));
        inboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        // 库存冲销后触发物料告警校验（自动关闭已消除的告警，或触发新告警）
        scheduleMaterialAlertCheck(items.stream().map(InboundOrderItem::getMaterialId).distinct().toList());
        return toWriteResult(order);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = InboundOrderValidationException.class)
    public InboundOrderWriteResultVO retryPost(Long id, InboundOrderActionDTO dto) {
        InboundOrder order = getOrderById(id);
        assertVersionMatches(dto.getVersion(), order);
        if (!"approved".equals(order.getStatus()) || !"post_failed".equals(order.getPostStatus())) {
            inboundOrderValidationService.rejectRetryPostUnavailable(order.getVersion());
        }
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(id);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(InboundOrderItem::getMaterialId).toList(),
                "重试入库过账"
        );
        validateStocktakeUnlocked(order, "入库");
        LocationAreaValidationService.AreaValidationResult validation = locationAreaValidationService.validateInboundOrderArea(order.getWarehouseId(), items);
        validation.throwIfExceeded();

        order.setPostStatus("posting");
        order.setPostErrorMessage(null);
        inboundOrderMapper.updateById(order);

        try {
            inboundOrderPostingService.executeApprovedPosting(order, items, validation);
            order.setStatus("completed");
            order.setPostStatus("posted");
            order.setPostErrorMessage(null);
            inboundOrderMapper.updateById(order);
            refreshOrderStatusScope(order);
            log.info("重试入库过账成功: id={}", id);
            // 库存变更后触发物料告警校验
            scheduleMaterialAlertCheck(items.stream().map(InboundOrderItem::getMaterialId).distinct().toList());
            return toWriteResult(order);
        } catch (RuntimeException ex) {
            order.setPostStatus("post_failed");
            order.setPostErrorMessage(resolvePostErrorMessage(ex));
            inboundOrderMapper.updateById(order);
            inboundOrderValidationService.rejectPostFailed(order.getVersion(), order.getPostErrorMessage());
            return toWriteResult(order);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String approveRemark) {
        InboundOrder order = getOrderById(id);
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审批状态的入库单可以驳回");
        }
        if (StrUtil.isBlank(approveRemark)) {
            throw BizException.badRequest("驳回原因不能为空");
        }
        order.setStatus("rejected");
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(approveRemark);
        inboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("驳回入库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int syncApprovedInventory() {
        List<InboundOrder> approvedOrders = inboundOrderMapper.selectList(
                new LambdaQueryWrapper<InboundOrder>()
                        .eq(InboundOrder::getStatus, "approved")
                        .eq(InboundOrder::getDeleted, 0)
                        .orderByAsc(InboundOrder::getId));
        int syncedCount = 0;
        for (InboundOrder order : approvedOrders) {
            if (syncInventory(order)) {
                syncedCount++;
            }
        }
        log.info("补齐已审批入库单库存完成: syncedCount={}", syncedCount);
        return syncedCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        InboundOrder order = getOrderById(id);
        if ("approved".equals(order.getStatus())) {
            throw BizException.validationFailed("已审批的入库单不能取消");
        }
        order.setStatus("cancelled");
        inboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("取消入库单: id={}", id);
    }

    // ==================== 私有方法 ====================

    private InboundOrder getOrderById(Long id) {
        InboundOrder order = inboundOrderMapper.selectById(id);
        if (order == null) throw BizException.notFound("入库单不存在");
        ensureOrgAllowed(order.getOrgId());
        return order;
    }

    private void assertVersionMatches(Long requestVersion, InboundOrder order) {
        if (!Objects.equals(requestVersion, order.getVersion())) {
            inboundOrderValidationService.rejectVersionConflict(order.getVersion());
        }
    }

    private void ensureInboundPermission(InboundOrder order,
                                         String permissionCode,
                                         String errorMessage,
                                         String auditDesc) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            logInboundPermissionDenied(order, auditDesc, errorMessage);
            throw BizException.forbidden(errorMessage);
        }
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? " +
                        "  AND r.deleted = 0 " +
                        "  AND r.status = 'active' " +
                        "  AND p.status = 'active' " +
                        "  AND p.permission_code = ?",
                Long.class,
                userId,
                permissionCode
        );
        if (count == null || count <= 0L) {
            logInboundPermissionDenied(order, auditDesc, errorMessage);
            throw BizException.forbidden(errorMessage);
        }
    }

    private void logInboundPermissionDenied(InboundOrder order, String auditDesc, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        auditLogService.log(
                AuditModule.WMS_INBOUND_ORDER,
                AuditOperationType.STATUS_CHANGE,
                order.getId(),
                order.getInboundNo(),
                auditDesc,
                null,
                null,
                "failed",
                errorMessage
        );
    }

    private void ensureInboundSubmitIdempotencyTable() {
        try {
            Integer tableCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wms_inbound_order_idempotency'",
                    Integer.class
            );
            if (tableCount != null && tableCount == 0) {
                jdbcTemplate.execute(
                        "CREATE TABLE IF NOT EXISTS `wms_inbound_order_idempotency` (" +
                                "`id` BIGINT NOT NULL AUTO_INCREMENT," +
                                "`tenant_id` BIGINT NOT NULL COMMENT '租户ID'," +
                                "`inbound_order_id` BIGINT NOT NULL COMMENT '入库单ID'," +
                                "`action` VARCHAR(32) NOT NULL COMMENT '写操作类型'," +
                                "`idempotency_key` VARCHAR(64) NOT NULL COMMENT '幂等键'," +
                                "`request_version` BIGINT NULL COMMENT '请求版本号'," +
                                "`response_version` BIGINT NULL COMMENT '响应版本号'," +
                                "`created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                                "`updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                                "PRIMARY KEY (`id`)," +
                                "UNIQUE KEY `uk_inbound_order_action_idempotency` (`tenant_id`, `inbound_order_id`, `action`, `idempotency_key`)" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='入库单写操作幂等记录'"
                );
                log.info("wms-service: 入库单幂等表缺失，已自动创建 wms_inbound_order_idempotency");
            }
        } catch (Exception e) {
            log.warn("wms-service: 入库单幂等表检查异常: {}", e.getMessage());
        }
    }

    private boolean isSubmitAlreadyAccepted(InboundOrder order, String idempotencyKey) {
        if (inboundOrderActionIdempotencyMapper == null) {
            return false;
        }
        Long tenantId = defaultLong(order.getTenantId(), UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        return inboundOrderActionIdempotencyMapper.selectCount(
                new LambdaQueryWrapper<InboundOrderActionIdempotency>()
                        .eq(InboundOrderActionIdempotency::getTenantId, tenantId)
                        .eq(InboundOrderActionIdempotency::getInboundOrderId, order.getId())
                        .eq(InboundOrderActionIdempotency::getAction, "submit")
                        .eq(InboundOrderActionIdempotency::getIdempotencyKey, idempotencyKey)
        ) > 0;
    }

    private void recordSubmitIdempotency(InboundOrder order, String idempotencyKey, Long requestVersion) {
        if (inboundOrderActionIdempotencyMapper == null) {
            return;
        }
        InboundOrderActionIdempotency record = new InboundOrderActionIdempotency();
        record.setTenantId(defaultLong(order.getTenantId(), UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L));
        record.setInboundOrderId(order.getId());
        record.setAction("submit");
        record.setIdempotencyKey(idempotencyKey);
        record.setRequestVersion(requestVersion);
        record.setResponseVersion(order.getVersion());
        try {
            inboundOrderActionIdempotencyMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            inboundOrderValidationService.rejectDuplicateSubmit(inboundOrderMapper.selectVersionById(order.getId()));
        }
    }

    private void assertReversibleInboundAreaLedgers(InboundOrder order, List<InboundOrderItem> items) {
        if (items == null || items.isEmpty()) {
            inboundOrderValidationService.rejectUnapprovePreconditionFailed(order.getVersion(), "当前入库单缺少可冲销的仓位面积台账，无法反审核");
        }
        for (InboundOrderItem item : items) {
            var ledger = locationAreaLedgerMapper == null ? null : locationAreaLedgerMapper.selectLatestByBizItemForUpdate(
                    "inbound",
                    "approve",
                    order.getId(),
                    item.getId()
            );
            if (ledger == null || ledger.getReversedLedgerId() != null) {
                inboundOrderValidationService.rejectUnapprovePreconditionFailed(order.getVersion(), "当前入库单缺少可冲销的仓位面积台账，无法反审核");
            }
        }
    }

    private void reverseInboundInventory(InboundOrder order, List<InboundOrderItem> items) {
        for (InboundOrderItem item : items) {
            Inventory inventory = resolveInboundInventory(order, item);
            if (inventory == null) {
                inboundOrderValidationService.rejectUnapprovePreconditionFailed(order.getVersion(), "当前入库单缺少可冲销的库存记录，无法反审核");
            }
            if (hasDownstreamOutboundUsage(inventory.getId())) {
                inboundOrderValidationService.rejectUnapprovePreconditionFailed(order.getVersion(), "当前入库生成库存已被下游出库占用，无法反审核");
            }
            if (inventory.getQuantity() == null || item.getQuantity() == null || inventory.getQuantity().compareTo(item.getQuantity()) < 0) {
                inboundOrderValidationService.rejectUnapprovePreconditionFailed(order.getVersion(), "当前入库生成库存数量不足以冲销，无法反审核");
            }
            inventory.setQuantity(BigDecimal.ZERO);
            inventory.setTotalCost(BigDecimal.ZERO);
            inventoryMapper.updateById(inventory);
        }
    }

    private boolean hasDownstreamOutboundUsage(Long inventoryId) {
        if (inventoryId == null || outboundOrderItemMapper == null) {
            return false;
        }
        return outboundOrderItemMapper.selectCount(new LambdaQueryWrapper<com.xykj.wms.entity.OutboundOrderItem>()
                .eq(com.xykj.wms.entity.OutboundOrderItem::getInventoryId, inventoryId)) > 0;
    }

    private Inventory resolveInboundInventory(InboundOrder order, InboundOrderItem item) {
        String sourceType = StrUtil.blankToDefault(order.getSourceType(), "purchase");
        String spec = blankToNull(item.getSpec());
        String batchNo = StrUtil.isBlank(item.getBatchNo()) ? DEFAULT_BATCH_NO : item.getBatchNo();
        String traceBatchId = blankToNull(item.getTraceBatchId());
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSourceType, sourceType)
                .eq(Inventory::getSourceId, order.getId())
                .eq(Inventory::getWarehouseId, item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId())
                .eq(item.getLocationId() != null, Inventory::getLocationId, item.getLocationId())
                .isNull(item.getLocationId() == null, Inventory::getLocationId)
                .eq(Inventory::getMaterialId, item.getMaterialId())
                .eq(Inventory::getOrgId, defaultLong(order.getOrgId(), 1L))
                .eq(Inventory::getTenantId, defaultLong(order.getTenantId(), 1L));
        applyNullableStringMatch(wrapper, Inventory::getSpec, spec);
        applyNullableStringMatch(wrapper, Inventory::getBatchNo, batchNo);
        applyNullableStringMatch(wrapper, Inventory::getTraceBatchId, traceBatchId);
        return inventoryMapper.selectOne(wrapper);
    }

    /**
     * 审批通过后回写采购订单明细的已入库数量，并自动判断是否全部入库完成
     */
    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private void validateInboundWarehouseAssignable(Long warehouseId,
                                                    Long expectedOrgId,
                                                    Long latestVersion,
                                                    List<? extends InboundOrderCreateDTO.InboundOrderItemDTO> items) {
        String lineKey = resolveWarehouseLineKey(items);
        if (warehouseId == null) {
            inboundOrderValidationService.rejectWarehouseValidation(lineKey, latestVersion, "入库明细缺少仓库信息");
        }
        var warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            inboundOrderValidationService.rejectWarehouseValidation(lineKey, latestVersion, "所属仓库不存在");
        }
        if (expectedOrgId != null && warehouse.getOrgId() != null && !Objects.equals(expectedOrgId, warehouse.getOrgId())) {
            inboundOrderValidationService.rejectWarehouseValidation(lineKey, latestVersion, "所属仓库不属于当前组织");
        }
        if (isInboundForbiddenWarehouseStatus(warehouse.getStatus())) {
            inboundOrderValidationService.rejectWarehouseValidation(lineKey, latestVersion, "所属仓库当前状态不允许分配入库");
        }
    }

    private String resolveWarehouseLineKey(List<? extends InboundOrderCreateDTO.InboundOrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream()
                .map(InboundOrderCreateDTO.InboundOrderItemDTO::getLineKey)
                .filter(StrUtil::isNotBlank)
                .findFirst()
                .orElse(null);
    }

    private boolean isInboundForbiddenWarehouseStatus(String status) {
        return "inactive".equals(status) || "archived".equals(status);
    }

    private void validateRequiredItemFields(List<? extends InboundOrderCreateDTO.InboundOrderItemDTO> items,
                                            Long latestVersion) {
        if (items == null) {
            return;
        }
        for (InboundOrderCreateDTO.InboundOrderItemDTO item : items) {
            String lineKey = StrUtil.blankToDefault(item.getLineKey(), null);
            if (item.getWarehouseId() == null) {
                inboundOrderValidationService.rejectRequiredItemField(lineKey, "warehouseId", latestVersion, "入库仓库不能为空");
            }
            if (item.getLocationId() == null) {
                inboundOrderValidationService.rejectRequiredItemField(lineKey, "locationId", latestVersion, "仓位不能为空");
            }
            if (item.getMaterialId() == null) {
                inboundOrderValidationService.rejectRequiredItemField(lineKey, "materialId", latestVersion, "物料不能为空");
            }
            if (StrUtil.isBlank(item.getSpec())) {
                inboundOrderValidationService.rejectRequiredItemField(lineKey, "spec", latestVersion, "规格不能为空");
            }
            if (StrUtil.isBlank(item.getUnit())) {
                inboundOrderValidationService.rejectRequiredItemField(lineKey, "unit", latestVersion, "单位不能为空");
            }
        }
    }

    private void validateProductionDates(List<? extends InboundOrderCreateDTO.InboundOrderItemDTO> items,
                                         Long latestVersion) {
        if (items == null) {
            return;
        }
        LocalDate today = LocalDate.now();
        for (InboundOrderCreateDTO.InboundOrderItemDTO item : items) {
            if (item.getProductionDate() == null) {
                continue;
            }
            if (item.getProductionDate().isAfter(today)) {
                String lineKey = StrUtil.blankToDefault(item.getLineKey(), null);
                inboundOrderValidationService.rejectProductionDateValidation(lineKey, latestVersion, "生产日期不能晚于今天");
            }
        }
    }

    private void validateSubmitItemReferences(List<InboundOrderItem> items, Long latestVersion) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            InboundOrderItem item = items.get(index);
            String lineKey = String.valueOf(index);
            Material material = materialMapper != null && item.getMaterialId() != null ? materialMapper.selectById(item.getMaterialId()) : null;
            if (material == null) {
                inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "materialId", latestVersion, "物料不存在，请重新选择物料");
            }
            if (!"active".equals(material.getStatus())) {
                inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "materialId", latestVersion, "物料已停用，请重新选择物料");
            }
            if (StrUtil.isNotBlank(item.getSpec()) && !StrUtil.equals(StrUtil.trim(item.getSpec()), StrUtil.trim(material.getSpec()))) {
                inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "spec", latestVersion, "所选规格不属于当前物料，请重新选择规格");
            }
            Location location = locationMapper != null && item.getLocationId() != null ? locationMapper.selectById(item.getLocationId()) : null;
            if (location == null) {
                inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "locationId", latestVersion, "仓位不存在，请重新选择仓位");
            }
            if (item.getWarehouseId() != null && !Objects.equals(item.getWarehouseId(), location.getWarehouseId())) {
                inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "locationId", latestVersion, "仓位不属于所选仓库，请重新选择仓位");
            }
            if (location.getWarehouseId() != null) {
                var warehouse = warehouseMapper.selectById(location.getWarehouseId());
                if (warehouse == null || warehouse.getOrgId() == null || !Objects.equals(UserContext.getOrgId(), warehouse.getOrgId())) {
                    inboundOrderValidationService.rejectItemReferenceValidation(lineKey, "locationId", latestVersion, "当前仓库或仓位无权限使用，请重新选择");
                }
            }
        }
    }

    private String resolveActiveSupplierName(Long supplierId, Long orgId) {
        List<String> supplierNames = jdbcTemplate.query(
                "SELECT supplier_name FROM scm_supplier " +
                        "WHERE id = ? AND org_id = ? AND deleted = 0 AND status = 'active' LIMIT 1",
                (rs, rowNum) -> rs.getString("supplier_name"),
                supplierId, orgId
        );
        if (supplierNames.isEmpty()) {
            throw BizException.validationFailed("所选供应商不存在或当前状态不可用于新增入库业务，请选择已审核状态的供应商");
        }
        return supplierNames.get(0);
    }

    private void refreshOrderStatusScope(InboundOrder order) {
        refreshOrderStatusScope(order, List.of());
    }

    private String resolvePostErrorMessage(RuntimeException ex) {
        String message = StrUtil.blankToDefault(ex.getMessage(), "库存过账失败，请稍后重试");
        if (message.length() <= POST_ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, POST_ERROR_MESSAGE_MAX_LENGTH);
    }

    private boolean canUnapprove(InboundOrder order) {
        if (order == null) {
            return false;
        }
        if (isCompletedOrder(order)) {
            return true;
        }
        return "approved".equals(order.getStatus())
                && (isUnpostedStatus(order.getPostStatus()) || "post_failed".equals(order.getPostStatus()));
    }

    private boolean isCompletedOrder(InboundOrder order) {
        if (order == null) {
            return false;
        }
        if ("completed".equals(order.getStatus())) {
            return true;
        }
        return "approved".equals(order.getStatus()) && "posted".equals(order.getPostStatus());
    }

    private boolean isUnpostedStatus(String postStatus) {
        String normalizedStatus = StrUtil.blankToDefault(StrUtil.trim(postStatus), "unposted");
        return "unposted".equals(normalizedStatus) || "none".equals(normalizedStatus);
    }

    private void refreshOrderStatusScope(InboundOrder order, List<InboundOrderItem> extraItems) {
        List<InboundOrderItem> items = inboundOrderItemMapper.selectEntityItemsByInboundId(order.getId());
        if (items.isEmpty() && (extraItems == null || extraItems.isEmpty())) {
            warehouseStatusRefreshService.refreshWarehouse(order.getWarehouseId());
            return;
        }
        Set<String> refreshedRanges = new HashSet<>();
        refreshStatusRanges(order, items, refreshedRanges);
        if (extraItems != null && !extraItems.isEmpty()) {
            refreshStatusRanges(order, extraItems, refreshedRanges);
        }
    }

    private void refreshStatusRanges(InboundOrder order, List<InboundOrderItem> items, Set<String> refreshedRanges) {
        for (InboundOrderItem item : items) {
            Long warehouseId = item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId();
            Long locationId = item.getLocationId();
            String key = warehouseId + "_" + locationId;
            if (refreshedRanges.add(key)) {
                warehouseStatusRefreshService.refreshLocationAndWarehouse(warehouseId, locationId);
            }
        }
    }

    private void saveItems(Long inboundId, List<InboundOrderCreateDTO.InboundOrderItemDTO> itemDTOs) {
        for (InboundOrderCreateDTO.InboundOrderItemDTO dto : itemDTOs) {
            InboundOrderItem item = new InboundOrderItem();
            BeanUtil.copyProperties(dto, item);
            item.setInboundId(inboundId);
            if (StrUtil.isBlank(item.getBatchNo())) {
                item.setBatchNo(DEFAULT_BATCH_NO);
            }
            if (item.getUnitCost() != null && item.getQuantity() != null) {
                item.setTotalCost(item.getUnitCost().multiply(item.getQuantity()));
            }
            inboundOrderItemMapper.insert(item);
        }
    }

    private List<InboundSourceOrderOptionVO> listPurchaseSourceOrderOptions(Long excludeInboundOrderId) {
        String sql = "SELECT po.id, po.order_no AS orderNo, po.supplier_id AS supplierId, po.supplier_name AS supplierName, " +
                "po.org_id AS orgId, NULL AS orgName, " +
                "COALESCE(SUM(itemAgg.orderQty), 0) AS availableQuantity, " +
                "COALESCE(SUM(itemAgg.completedQty + COALESCE(active.activeQty, 0)), 0) AS linkedQuantity " +
                "FROM scm_purchase_order po " +
                "LEFT JOIN ( " +
                "  SELECT order_id AS orderId, material_id AS materialId, " +
                "         COALESCE(SUM(order_qty), 0) AS orderQty, " +
                "         COALESCE(SUM(inbound_qty), 0) AS completedQty " +
                "  FROM scm_purchase_order_item " +
                "  GROUP BY order_id, material_id " +
                ") itemAgg ON itemAgg.orderId = po.id " +
                "LEFT JOIN ( " +
                "  SELECT io.source_order_id AS orderId, ii.material_id AS materialId, COALESCE(SUM(ii.quantity), 0) AS activeQty " +
                "  FROM wms_inbound_order io " +
                "  JOIN wms_inbound_order_item ii ON ii.inbound_id = io.id " +
                "  WHERE io.deleted = 0 AND io.source_type = 'purchase' " +
                "    AND io.status IN (" + PURCHASE_OCCUPYING_STATUS_SQL + ") " +
                "    AND (? IS NULL OR io.id <> ?) " +
                "  GROUP BY io.source_order_id, ii.material_id " +
                ") active ON active.orderId = po.id AND active.materialId = itemAgg.materialId " +
                "WHERE po.deleted = 0 AND po.status = 'pending_receipt' " +
                "GROUP BY po.id, po.order_no, po.supplier_id, po.supplier_name, po.org_id " +
                "HAVING COALESCE(SUM(itemAgg.orderQty), 0) > COALESCE(SUM(itemAgg.completedQty + COALESCE(active.activeQty, 0)), 0) " +
                "ORDER BY po.updated_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSourceOrderOption(rs.getLong("id"), rs.getString("orderNo"),
                getNullableLong(rs, "supplierId"), rs.getString("supplierName"), getNullableLong(rs, "orgId"),
                rs.getString("orgName"), rs.getBigDecimal("availableQuantity"), rs.getBigDecimal("linkedQuantity")),
                excludeInboundOrderId, excludeInboundOrderId);
    }

    private List<InboundSourceOrderOptionVO> listTransferSourceOrderOptions() {
        String sql = "SELECT o.id, o.outbound_no AS orderNo, NULL AS supplierId, NULL AS supplierName, o.org_id AS orgId, so.org_name AS orgName, " +
                "COALESCE(SUM(oi.quantity), 0) AS availableQuantity, COALESCE(linked.linked_qty, 0) AS linkedQuantity " +
                "FROM wms_outbound_order o " +
                "LEFT JOIN wms_outbound_order_item oi ON oi.outbound_id = o.id " +
                "LEFT JOIN sys_organization so ON so.id = o.org_id AND so.deleted = 0 " +
                "LEFT JOIN ( " +
                "  SELECT source_order_id, COALESCE(SUM(quantity), 0) AS linked_qty " +
                "  FROM wms_inbound_order io " +
                "  JOIN wms_inbound_order_item ii ON ii.inbound_id = io.id " +
                "  WHERE io.deleted = 0 AND io.source_type = 'transfer' AND io.status <> 'cancelled' " +
                "  GROUP BY source_order_id " +
                ") linked ON linked.source_order_id = o.id " +
                "WHERE o.deleted = 0 AND o.outbound_type = 'transfer' AND o.status IN ('approved', 'completed') " +
                "GROUP BY o.id, o.outbound_no, o.org_id, so.org_name, linked.linked_qty " +
                "HAVING COALESCE(SUM(oi.quantity), 0) > COALESCE(linked.linked_qty, 0) " +
                "ORDER BY o.updated_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSourceOrderOption(rs.getLong("id"), rs.getString("orderNo"),
                getNullableLong(rs, "supplierId"), rs.getString("supplierName"), getNullableLong(rs, "orgId"),
                rs.getString("orgName"), rs.getBigDecimal("availableQuantity"), rs.getBigDecimal("linkedQuantity")));
    }

    private List<InboundSourceOrderOptionVO> listMaterialReturnSourceOrderOptions() {
        String sql = "SELECT o.id, o.outbound_no AS orderNo, NULL AS supplierId, NULL AS supplierName, o.org_id AS orgId, so.org_name AS orgName, " +
                "COALESCE(SUM(oi.quantity), 0) AS availableQuantity, COALESCE(linked.linked_qty, 0) AS linkedQuantity " +
                "FROM wms_outbound_order o " +
                "LEFT JOIN wms_outbound_order_item oi ON oi.outbound_id = o.id " +
                "LEFT JOIN sys_organization so ON so.id = o.org_id AND so.deleted = 0 " +
                "LEFT JOIN ( " +
                "  SELECT source_order_id, COALESCE(SUM(quantity), 0) AS linked_qty " +
                "  FROM wms_inbound_order io " +
                "  JOIN wms_inbound_order_item ii ON ii.inbound_id = io.id " +
                "  WHERE io.deleted = 0 AND io.source_type = 'material_return' AND io.status <> 'cancelled' " +
                "  GROUP BY source_order_id " +
                ") linked ON linked.source_order_id = o.id " +
                "WHERE o.deleted = 0 AND o.outbound_type = 'requisition' AND o.status IN ('approved', 'completed') " +
                "GROUP BY o.id, o.outbound_no, o.org_id, so.org_name, linked.linked_qty " +
                "HAVING COALESCE(SUM(oi.quantity), 0) > COALESCE(linked.linked_qty, 0) " +
                "ORDER BY o.updated_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSourceOrderOption(rs.getLong("id"), rs.getString("orderNo"),
                getNullableLong(rs, "supplierId"), rs.getString("supplierName"), getNullableLong(rs, "orgId"),
                rs.getString("orgName"), rs.getBigDecimal("availableQuantity"), rs.getBigDecimal("linkedQuantity")));
    }

    private List<InboundSourceOrderOptionVO> listSurplusSourceOrderOptions() {
        String sql = "SELECT o.id, o.stocktake_no AS orderNo, NULL AS supplierId, NULL AS supplierName, o.org_id AS orgId, NULL AS orgName, " +
                "COALESCE(SUM(CASE WHEN i.diff_qty > 0 THEN i.diff_qty ELSE 0 END), 0) AS availableQuantity, COALESCE(linked.linked_qty, 0) AS linkedQuantity " +
                "FROM wms_stocktake_order o " +
                "LEFT JOIN wms_stocktake_order_item i ON i.stocktake_id = o.id " +
                "LEFT JOIN ( " +
                "  SELECT source_order_id, COALESCE(SUM(quantity), 0) AS linked_qty " +
                "  FROM wms_inbound_order io " +
                "  JOIN wms_inbound_order_item ii ON ii.inbound_id = io.id " +
                "  WHERE io.deleted = 0 AND io.source_type = 'surplus' AND io.status <> 'cancelled' " +
                "  GROUP BY source_order_id " +
                ") linked ON linked.source_order_id = o.id " +
                "WHERE o.deleted = 0 AND o.status = 'approved' " +
                "GROUP BY o.id, o.stocktake_no, o.org_id, linked.linked_qty " +
                "HAVING COALESCE(SUM(CASE WHEN i.diff_qty > 0 THEN i.diff_qty ELSE 0 END), 0) > COALESCE(linked.linked_qty, 0) " +
                "ORDER BY o.updated_at DESC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapSourceOrderOption(rs.getLong("id"), rs.getString("orderNo"),
                getNullableLong(rs, "supplierId"), rs.getString("supplierName"), getNullableLong(rs, "orgId"),
                rs.getString("orgName"), rs.getBigDecimal("availableQuantity"), rs.getBigDecimal("linkedQuantity")));
    }

    private InboundSourceOrderOptionVO mapSourceOrderOption(Long id,
                                                            String orderNo,
                                                            Long supplierId,
                                                            String supplierName,
                                                            Long orgId,
                                                            String orgName,
                                                            BigDecimal availableQuantity,
                                                            BigDecimal linkedQuantity) {
        InboundSourceOrderOptionVO option = new InboundSourceOrderOptionVO();
        option.setId(id);
        option.setOrderNo(orderNo);
        option.setSupplierId(supplierId);
        option.setSupplierName(supplierName);
        option.setOrgId(orgId);
        option.setOrgName(orgName);
        option.setAvailableQuantity(defaultZero(availableQuantity));
        option.setLinkedQuantity(defaultZero(linkedQuantity));
        return option;
    }

    private Long getNullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private void recalcTotalAmount(InboundOrder order) {
        List<InboundOrderItem> items = inboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getInboundId, order.getId()));
        BigDecimal total = items.stream()
                .filter(i -> i.getTotalCost() != null)
                .map(InboundOrderItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(total);
        inboundOrderMapper.updateById(order);
    }

    private boolean syncInventory(InboundOrder order) {
        return syncInventory(order, inboundOrderItemMapper.selectEntityItemsByInboundId(order.getId()));
    }

    private boolean syncInventory(InboundOrder order, List<InboundOrderItem> items) {
        if (items.isEmpty()) {
            throw BizException.validationFailed("入库单明细不能为空");
        }
        Map<Long, Material> materialMap = loadMaterialsByInboundItems(items);
        boolean inserted = false;
        for (InboundOrderItem item : items) {
            if (hasInventoryRecord(order, item)) {
                continue;
            }
            Inventory inventory = new Inventory();
            inventory.setWarehouseId(item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId());
            inventory.setLocationId(item.getLocationId());
            inventory.setMaterialId(item.getMaterialId());
            inventory.setMaterialName(item.getMaterialName());
            inventory.setSpec(blankToNull(item.getSpec()));
            inventory.setBatchNo(StrUtil.isBlank(item.getBatchNo()) ? DEFAULT_BATCH_NO : item.getBatchNo());
            inventory.setTraceBatchId(blankToNull(item.getTraceBatchId()));
            inventory.setQuantity(defaultZero(item.getQuantity()));
            inventory.setUnit(item.getUnit());
            inventory.setUnitCost(item.getUnitCost());
            inventory.setTotalCost(calcTotalCost(item.getQuantity(), item.getUnitCost()));
            inventory.setProductionDate(item.getProductionDate());
            inventory.setExpiryDate(resolveInventoryExpiryDate(item, materialMap));
            inventory.setStatus(DEFAULT_INVENTORY_STATUS);
            inventory.setSourceType(StrUtil.blankToDefault(order.getSourceType(), "purchase"));
            inventory.setSourceId(order.getId());
            inventory.setOrgId(defaultLong(order.getOrgId(), 1L));
            inventory.setTenantId(defaultLong(order.getTenantId(), 1L));
            inventory.setCreatedBy(order.getApprovedBy() != null ? order.getApprovedBy() : order.getSubmittedBy());
            inventoryMapper.insert(inventory);
            inserted = true;
        }
        return inserted;
    }

    private Map<Long, Material> loadMaterialsByInboundItems(List<InboundOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Map.of();
        }
        List<Long> materialIds = items.stream()
                .map(InboundOrderItem::getMaterialId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (materialIds.isEmpty()) {
            return Map.of();
        }
        return materialMapper.selectBatchIds(materialIds).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Material::getId, material -> material, (left, right) -> left));
    }

    private LocalDate resolveInventoryExpiryDate(InboundOrderItem item, Map<Long, Material> materialMap) {
        if (item == null) {
            return null;
        }
        if (item.getExpiryDate() != null) {
            return item.getExpiryDate();
        }
        if (item.getProductionDate() == null || item.getMaterialId() == null || materialMap == null || materialMap.isEmpty()) {
            return null;
        }
        Material material = materialMap.get(item.getMaterialId());
        Integer shelfLifeDays = material != null ? material.getShelfLifeDays() : null;
        if (shelfLifeDays == null || shelfLifeDays <= 0) {
            return null;
        }
        return item.getProductionDate().plusDays(shelfLifeDays.longValue());
    }

    private boolean hasInventoryRecord(InboundOrder order, InboundOrderItem item) {
        String spec = blankToNull(item.getSpec());
        String batchNo = StrUtil.isBlank(item.getBatchNo()) ? DEFAULT_BATCH_NO : item.getBatchNo();
        String traceBatchId = blankToNull(item.getTraceBatchId());
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getSourceType, StrUtil.blankToDefault(order.getSourceType(), "purchase"))
                .eq(Inventory::getSourceId, order.getId())
                .eq(Inventory::getWarehouseId, item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId())
                .eq(item.getLocationId() != null, Inventory::getLocationId, item.getLocationId())
                .isNull(item.getLocationId() == null, Inventory::getLocationId)
                .eq(Inventory::getMaterialId, item.getMaterialId())
                .eq(Inventory::getOrgId, defaultLong(order.getOrgId(), 1L))
                .eq(Inventory::getTenantId, defaultLong(order.getTenantId(), 1L));
        applyNullableStringMatch(wrapper, Inventory::getSpec, spec);
        applyNullableStringMatch(wrapper, Inventory::getBatchNo, batchNo);
        applyNullableStringMatch(wrapper, Inventory::getTraceBatchId, traceBatchId);
        return inventoryMapper.selectCount(wrapper) > 0;
    }

    private <T> void applyNullableStringMatch(LambdaQueryWrapper<Inventory> wrapper,
                                              SFunction<Inventory, T> column,
                                              String value) {
        if (value == null) {
            wrapper.and(q -> q.isNull(column).or().eq(column, ""));
            return;
        }
        wrapper.eq(column, value);
    }

    private BigDecimal calcTotalCost(BigDecimal quantity, BigDecimal unitCost) {
        return defaultZero(quantity).multiply(defaultZero(unitCost));
    }

    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value;
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Long defaultLong(Long value, Long fallback) {
        return value != null ? value : fallback;
    }

    private void validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择要上传的附件");
        }
        String suffix = FileUtil.getSuffix(file.getOriginalFilename());
        String normalizedSuffix = StrUtil.blankToDefault(suffix, "").toLowerCase();
        String contentType = StrUtil.blankToDefault(file.getContentType(), "").toLowerCase();
        boolean allowedType = StrUtil.isNotBlank(contentType) && ALLOWED_ATTACHMENT_TYPES.contains(contentType);
        boolean allowedSuffix = StrUtil.isNotBlank(normalizedSuffix) && ALLOWED_ATTACHMENT_EXTENSIONS.contains(normalizedSuffix.startsWith(".") ? normalizedSuffix : "." + normalizedSuffix);
        if (!allowedType && !allowedSuffix) {
            throw BizException.badRequest("不支持的附件格式，仅支持图片、视频、PDF、Excel、Word、TXT");
        }

        InboundAttachmentCategory category = resolveAttachmentCategory(normalizedSuffix, contentType);
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

    private InboundAttachmentCategory resolveAttachmentCategory(String suffix, String contentType) {
        if (contentType.startsWith("image/") || List.of("jpg", "jpeg", "png", "gif", "webp").contains(suffix)) {
            return InboundAttachmentCategory.IMAGE;
        }
        if (contentType.startsWith("video/") || List.of("mp4", "mov").contains(suffix)) {
            return InboundAttachmentCategory.VIDEO;
        }
        return InboundAttachmentCategory.DOCUMENT;
    }

    private enum InboundAttachmentCategory {
        IMAGE,
        VIDEO,
        DOCUMENT
    }

    private void saveUploadFile(MultipartFile file, String relativePath) {
        try {
            Path fullPath = resolveUploadPath(relativePath);
            Files.createDirectories(fullPath.getParent());
            file.transferTo(fullPath.toFile());
        } catch (IOException e) {
            log.error("上传入库附件失败", e);
            throw new BizException("附件上传失败，请稍后重试");
        }
    }

    private Path resolveUploadPath(String relativePath) {
        Path basePath = Paths.get(uploadBasePath).toAbsolutePath().normalize();
        Path fullPath = basePath.resolve(relativePath.startsWith("/") ? relativePath.substring(1) : relativePath).normalize();
        if (!fullPath.startsWith(basePath)) {
            throw BizException.badRequest("附件地址不合法");
        }
        return fullPath;
    }

    private String detectContentType(Path fullPath) {
        try {
            String contentType = Files.probeContentType(fullPath);
            return StrUtil.blankToDefault(contentType, "application/octet-stream");
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }

    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }

    private void streamAttachment(Long id, String url, HttpServletResponse response, boolean inline) {
        InboundOrder order = getOrderById(id);
        if (StrUtil.isBlank(url)) {
            throw BizException.badRequest("附件地址不能为空");
        }
        List<String> attachments = order.getAttachments() == null ? List.of() : order.getAttachments();
        if (!attachments.contains(url)) {
            throw BizException.notFound("附件不存在");
        }
        if (!url.startsWith("/upload/")) {
            throw BizException.badRequest("历史附件仅保存了文件名，请重新上传后再查看或下载");
        }

        String relativePath = url.substring("/upload".length());
        Path fullPath = resolveUploadPath(relativePath);
        if (!Files.exists(fullPath) || !Files.isRegularFile(fullPath)) {
            throw BizException.notFound("附件不存在或已被删除");
        }

        String fileName = fullPath.getFileName().toString();
        response.setContentType(detectContentType(fullPath));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String disposition = inline ? "inline" : "attachment";
        response.setHeader("Content-Disposition", disposition + "; filename*=UTF-8''" + encodeFileName(fileName));
        response.setContentLengthLong(fullPath.toFile().length());

        try (OutputStream outputStream = response.getOutputStream()) {
            Files.copy(fullPath, outputStream);
            outputStream.flush();
        } catch (IOException e) {
            log.error("输出入库附件失败: id={}, url={}", id, url, e);
            throw new BizException(inline ? "附件预览失败，请稍后重试" : "附件下载失败，请稍后重试");
        }
    }

    private void validateStocktakeUnlocked(InboundOrder order, String actionName) {
        List<InboundOrderItem> items = inboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<InboundOrderItem>()
                        .eq(InboundOrderItem::getInboundId, order.getId()));
        if (items.isEmpty()) {
            throw BizException.validationFailed("入库单明细不能为空");
        }
        Set<String> validatedRanges = new HashSet<>();
        for (InboundOrderItem item : items) {
            Long warehouseId = item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId();
            if (warehouseId == null) {
                throw BizException.validationFailed("入库单明细缺少仓库信息，无法校验盘点锁");
            }
            Long locationId = item.getLocationId();
            String rangeKey = warehouseId + "_" + locationId;
            if (validatedRanges.add(rangeKey)) {
                stocktakeLockService.validateUnlocked(warehouseId, locationId, actionName);
            }
        }
    }

    /**
     * 采购入库模式校验：
     * 1. 来源采购订单仍处于可入库状态
     * 2. 仅允许来源采购订单中的物料
     * 3. 按物料汇总数量不超过剩余待入库总量
     */
    private void validatePurchaseInboundItems(Long purchaseOrderId,
                                              List<PurchaseValidationItem> items,
                                              Long latestVersion,
                                              Long excludeInboundOrderId) {
        PurchaseOrderInboundSnapshot snapshot = loadPurchaseOrderInboundSnapshot(purchaseOrderId, excludeInboundOrderId, true);
        if (!"pending_receipt".equals(snapshot.status())) {
            inboundOrderValidationService.rejectPurchaseSourceUnavailable(latestVersion, "来源采购订单状态已变更，请刷新来源后重试");
        }

        Map<Long, BigDecimal> remainMap = new java.util.HashMap<>();
        Set<Long> allowedMaterialIds = new HashSet<>();
        snapshot.balances().forEach(balance -> {
            allowedMaterialIds.add(balance.materialId());
            remainMap.put(balance.materialId(), balance.remainingQty());
        });

        if (allowedMaterialIds.isEmpty()) {
            inboundOrderValidationService.rejectPurchaseSourceUnavailable(latestVersion, "关联的采购订单无物料明细");
        }

        boolean hasRemainingQuantity = remainMap.values().stream()
                .filter(Objects::nonNull)
                .anyMatch(remaining -> remaining.compareTo(BigDecimal.ZERO) > 0);
        if (!hasRemainingQuantity) {
            inboundOrderValidationService.rejectPurchaseSourceUnavailable(latestVersion, "来源采购订单已无剩余可入库数量，请刷新来源后重试");
        }

        Map<Long, BigDecimal> sumMap = new java.util.HashMap<>();
        Map<Long, String> lineKeyMap = new java.util.HashMap<>();
        for (PurchaseValidationItem item : items) {
            if (item.materialId() == null) continue;
            BigDecimal current = sumMap.getOrDefault(item.materialId(), BigDecimal.ZERO);
            sumMap.put(item.materialId(), current.add(item.quantity()));
            if (StrUtil.isNotBlank(item.lineKey())) {
                lineKeyMap.putIfAbsent(item.materialId(), item.lineKey());
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : sumMap.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal totalQty = entry.getValue();
            String lineKey = StrUtil.blankToDefault(lineKeyMap.get(materialId), "material-" + materialId);

            if (!allowedMaterialIds.contains(materialId)) {
                inboundOrderValidationService.rejectPurchaseSourceMaterialMismatch(
                        lineKey,
                        latestVersion,
                        "入库明细中包含来源采购订单以外的物料，采购入库仅允许来源订单物料"
                );
            }
            BigDecimal remaining = remainMap.get(materialId);
            if (remaining != null && totalQty.compareTo(remaining) > 0) {
                inboundOrderValidationService.rejectQuantityExceeded(
                        lineKey,
                        latestVersion,
                        "物料累计入库数量 " + totalQty + " 超出当前可入库数量 " + remaining + "，请调整"
                );
            }
        }
        log.info("采购入库物料校验通过: purchaseOrderId={}, 物料数={}", purchaseOrderId, sumMap.size());
    }

    private PurchaseOrderInboundSnapshot loadPurchaseOrderInboundSnapshot(Long purchaseOrderId,
                                                                          Long excludeInboundOrderId,
                                                                          boolean lockRows) {
        String statusSql = "SELECT status FROM scm_purchase_order WHERE id = ? AND deleted = 0"
                + (lockRows ? " FOR UPDATE" : "");
        List<String> statuses = jdbcTemplate.query(
                statusSql,
                (rs, rowNum) -> rs.getString("status"),
                purchaseOrderId
        );
        if (statuses.isEmpty()) {
            throw BizException.notFound("来源采购订单不存在");
        }
        if (lockRows) {
            jdbcTemplate.query(
                    "SELECT id FROM scm_purchase_order_item WHERE order_id = ? FOR UPDATE",
                    (rs, rowNum) -> rs.getLong("id"),
                    purchaseOrderId
            );
        }
        return new PurchaseOrderInboundSnapshot(
                statuses.get(0),
                queryPurchaseInboundBalances(purchaseOrderId, excludeInboundOrderId)
        );
    }

    private List<PurchaseInboundBalance> queryPurchaseInboundBalances(Long purchaseOrderId, Long excludeInboundOrderId) {
        String sql = "SELECT itemAgg.materialId, itemAgg.materialName, itemAgg.spec, itemAgg.unit, itemAgg.unitPrice, " +
                "itemAgg.orderQty, itemAgg.completedQty + COALESCE(active.activeQty, 0) AS occupiedQty, " +
                "itemAgg.orderQty - itemAgg.completedQty - COALESCE(active.activeQty, 0) AS remainingQty " +
                "FROM ( " +
                "  SELECT material_id AS materialId, MAX(material_name) AS materialName, MAX(material_spec) AS spec, " +
                "         MAX(material_unit) AS unit, MAX(unit_price) AS unitPrice, " +
                "         COALESCE(SUM(order_qty), 0) AS orderQty, COALESCE(SUM(inbound_qty), 0) AS completedQty " +
                "  FROM scm_purchase_order_item " +
                "  WHERE order_id = ? " +
                "  GROUP BY material_id " +
                ") itemAgg " +
                "LEFT JOIN ( " +
                "  SELECT ii.material_id AS materialId, COALESCE(SUM(ii.quantity), 0) AS activeQty " +
                "  FROM wms_inbound_order io " +
                "  JOIN wms_inbound_order_item ii ON ii.inbound_id = io.id " +
                "  WHERE io.deleted = 0 AND io.source_type = 'purchase' AND io.source_order_id = ? " +
                "    AND io.status IN (" + PURCHASE_OCCUPYING_STATUS_SQL + ") " +
                "    AND (? IS NULL OR io.id <> ?) " +
                "  GROUP BY ii.material_id " +
                ") active ON active.materialId = itemAgg.materialId " +
                "ORDER BY itemAgg.materialId ASC";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            BigDecimal orderQty = defaultZero(rs.getBigDecimal("orderQty"));
            BigDecimal occupiedQty = defaultZero(rs.getBigDecimal("occupiedQty"));
            BigDecimal remainingQty = defaultZero(rs.getBigDecimal("remainingQty"));
            if (remainingQty.compareTo(BigDecimal.ZERO) < 0) {
                remainingQty = BigDecimal.ZERO;
            }
            return new PurchaseInboundBalance(
                    rs.getLong("materialId"),
                    rs.getString("materialName"),
                    rs.getString("spec"),
                    rs.getString("unit"),
                    rs.getBigDecimal("unitPrice"),
                    orderQty,
                    occupiedQty,
                    remainingQty
            );
        }, purchaseOrderId, purchaseOrderId, excludeInboundOrderId, excludeInboundOrderId);
    }

    private void rollbackPurchaseOrderCompletedQty(InboundOrder order, List<InboundOrderItem> items) {
        if (!"purchase".equals(order.getSourceType()) || order.getSourceOrderId() == null || items == null || items.isEmpty()) {
            return;
        }
        Long purchaseOrderId = order.getSourceOrderId();
        jdbcTemplate.query(
                "SELECT id FROM scm_purchase_order_item WHERE order_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong("id"),
                purchaseOrderId
        );
        for (InboundOrderItem item : items) {
            if (item.getMaterialId() == null || item.getQuantity() == null || item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal quantity = item.getQuantity();
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order_item " +
                            "SET inbound_qty = GREATEST(COALESCE(inbound_qty, 0) - ?, 0), " +
                            "remaining_inbound_qty = GREATEST(order_qty - GREATEST(COALESCE(inbound_qty, 0) - ?, 0), 0) " +
                            "WHERE order_id = ? AND material_id = ?",
                    quantity, quantity, purchaseOrderId, item.getMaterialId()
            );
        }

        Integer remainingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order_item " +
                        "WHERE order_id = ? AND COALESCE(inbound_qty, 0) < COALESCE(order_qty, 0)",
                Integer.class,
                purchaseOrderId
        );
        if (remainingCount != null && remainingCount > 0) {
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order SET status = 'pending_receipt' " +
                            "WHERE id = ? AND deleted = 0 AND status = 'completed'",
                    purchaseOrderId
            );
        }
    }

    /**
     * 采购入库：用采购订单物料的单价覆盖前端传入的单价，确保价格统一、台账合规
     */
    private void overrideUnitCostFromPurchaseOrder(Long purchaseOrderId, List<InboundOrderCreateDTO.InboundOrderItemDTO> items) {
        String sql = "SELECT material_id, unit_price FROM scm_purchase_order_item WHERE order_id = ?";
        Map<Long, BigDecimal> priceMap = new java.util.HashMap<>();
        jdbcTemplate.query(sql, (rs, rowNum) -> {
            priceMap.put(rs.getLong("material_id"), rs.getBigDecimal("unit_price"));
            return null;
        }, purchaseOrderId);
        for (InboundOrderCreateDTO.InboundOrderItemDTO item : items) {
            if (item.getMaterialId() != null) {
                BigDecimal poPrice = priceMap.get(item.getMaterialId());
                if (poPrice != null) {
                    item.setUnitCost(poPrice);
                }
            }
        }
    }

    private List<PurchaseValidationItem> toPurchaseValidationItems(List<? extends InboundOrderCreateDTO.InboundOrderItemDTO> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new PurchaseValidationItem(
                        item.getLineKey(),
                        item.getMaterialId(),
                        defaultZero(item.getQuantity())
                ))
                .toList();
    }

    private List<PurchaseValidationItem> toPurchaseValidationItemsFromEntity(List<InboundOrderItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(item -> new PurchaseValidationItem(
                        null,
                        item.getMaterialId(),
                        defaultZero(item.getQuantity())
                ))
                .toList();
    }

    private InboundImportRowValidation validateInboundImportRow(InboundImportRow row) {
        int rowNumber = row.rowNumber();
        String sourceTypeName = row.sourceTypeName();
        String sourceOrderNo = row.sourceOrderNo();
        String warehouseCode = row.warehouseCode();
        String locationCode = row.locationCode();
        String materialCode = row.materialCode();
        String quantityText = row.quantityText();
        if (StrUtil.isBlank(sourceTypeName)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "sourceTypeName", "入库类型不能为空"));
        }
        if (!List.of("采购入库", "调拨入库", "退料入库", "盘盈入库", "销售退货入库", "捐赠入库", "其他入库").contains(StrUtil.trim(sourceTypeName))) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "sourceTypeName", "当前入库类型暂不支持导入"));
        }
        boolean sourceOrderOptional = isSourceOrderOptionalInboundType(sourceTypeName);
        if (!sourceOrderOptional && StrUtil.isBlank(sourceOrderNo)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "sourceOrderNo", "来源单号不能为空"));
        }
        if (StrUtil.isBlank(warehouseCode)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "warehouseCode", "仓库编码不能为空"));
        }
        if (StrUtil.isBlank(locationCode)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "locationCode", "仓位编码不能为空"));
        }
        if (StrUtil.isBlank(materialCode)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "materialCode", "物料编码不能为空"));
        }
        if (StrUtil.isBlank(quantityText)) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "quantity", "入库数量不能为空"));
        }

        BigDecimal quantity;
        try {
            quantity = new BigDecimal(quantityText.trim());
        } catch (NumberFormatException ex) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "quantity", "入库数量格式不正确"));
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "quantity", "入库数量必须大于0"));
        }

        Long orgId = UserContext.getOrgId();
        Warehouse warehouse = locationMapper.selectWarehouseByCode(orgId, warehouseCode);
        if (warehouse == null) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "warehouseCode", "仓库编码不存在"));
        }

        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, materialCode)
                .eq(Material::getDeleted, 0)
                .last("LIMIT 1"));
        if (material == null) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "materialCode", "物料编码不存在"));
        }

        Long sourceOrderId = findSourceOrderIdBySourceType(sourceTypeName, sourceOrderNo);
        if (!sourceOrderOptional && sourceOrderId == null) {
            return InboundImportRowValidation.error(new InboundImportResultErrorDTO(rowNumber, "sourceOrderNo", "来源单号不存在"));
        }

        return InboundImportRowValidation.success(new ResolvedInboundImportRow(
                rowNumber,
                sourceTypeName.trim(),
                sourceOrderNo.trim(),
                warehouseCode.trim(),
                locationCode.trim(),
                materialCode.trim(),
                quantity,
                sourceOrderId,
                warehouse,
                material
        ));
    }

    private void persistImportedPurchaseRow(ResolvedInboundImportRow row) {
        InboundOrder order = new InboundOrder();
        order.setInboundNo(generateInboundNo());
        order.setSourceType(resolveInboundSourceType(row.sourceTypeName()));
        order.setSourceOrderId(row.sourceOrderId());
        order.setSourceOrderNo(row.sourceOrderNo());
        order.setWarehouseId(row.warehouse().getId());
        order.setStatus("draft");
        order.setPostStatus("unposted");
        order.setOrgId(UserContext.getOrgId());
        order.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        inboundOrderMapper.insert(order);

        InboundOrderItem item = new InboundOrderItem();
        item.setInboundId(order.getId());
        item.setWarehouseId(row.warehouse().getId());
        item.setLocationId(resolveLocationId(row.warehouse().getId(), row.locationCode()));
        item.setMaterialId(row.material().getId());
        item.setMaterialName(row.material().getMaterialName());
        item.setSpec(row.material().getSpec());
        item.setUnit(row.material().getUnit());
        item.setQuantity(row.quantity());
        item.setBatchNo(DEFAULT_BATCH_NO);
        inboundOrderItemMapper.insert(item);

        recalcTotalAmount(order);
    }

    private Long resolveLocationId(Long warehouseId, String locationCode) {
        if (warehouseId == null || StrUtil.isBlank(locationCode)) {
            return null;
        }
        Location location = locationMapper.selectOne(new LambdaQueryWrapper<Location>()
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getLocationCode, locationCode)
                .eq(Location::getDeleted, 0)
                .last("LIMIT 1"));
        return location != null ? location.getId() : null;
    }

    private void appendImportError(InboundImportRow row,
                                   InboundImportResultErrorDTO error,
                                   List<InboundImportResultErrorDTO> errors,
                                   List<InboundImportErrorRow> errorRows) {
        errors.add(error);
        errorRows.add(new InboundImportErrorRow(
                row.rowNumber(),
                row.sourceTypeName(),
                row.sourceOrderNo(),
                row.warehouseCode(),
                row.locationCode(),
                row.materialCode(),
                row.quantityText(),
                error.getField(),
                error.getReason()
        ));
    }

    private File resolveImportErrorFile(String fileName) {
        if (StrUtil.isBlank(fileName) || !fileName.matches(INBOUND_IMPORT_ERROR_FILE_PREFIX + "\\d+\\.xlsx")) {
            throw BizException.badRequest("错误文件名非法");
        }
        try {
            Path dir = Paths.get(uploadBasePath, "inbound-import-errors").toAbsolutePath().normalize();
            Files.createDirectories(dir);
            Path filePath = dir.resolve(fileName).normalize();
            if (!filePath.startsWith(dir)) {
                throw BizException.badRequest("错误文件名非法");
            }
            return filePath.toFile();
        } catch (IOException ex) {
            throw BizException.badRequest("错误文件名非法");
        }
    }

    private String generateImportErrorFile(List<InboundImportErrorRow> errorRows) {
        File file = resolveImportErrorFile(INBOUND_IMPORT_ERROR_FILE_PREFIX + System.currentTimeMillis() + ".xlsx");
        try (Workbook workbook = new XSSFWorkbook(); OutputStream outputStream = Files.newOutputStream(file.toPath())) {
            Sheet sheet = workbook.createSheet("导入失败数据");

            Row tipRow = sheet.createRow(0);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修正后重新导入。");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, INBOUND_IMPORT_TEMPLATE_HEADERS.length + 1));

            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < INBOUND_IMPORT_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(INBOUND_IMPORT_TEMPLATE_HEADERS[i]);
                sheet.setColumnWidth(i, INBOUND_IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }
            headerRow.createCell(INBOUND_IMPORT_TEMPLATE_HEADERS.length).setCellValue("错误字段");
            headerRow.createCell(INBOUND_IMPORT_TEMPLATE_HEADERS.length + 1).setCellValue("失败原因");
            sheet.setColumnWidth(INBOUND_IMPORT_TEMPLATE_HEADERS.length, 18 * 256);
            sheet.setColumnWidth(INBOUND_IMPORT_TEMPLATE_HEADERS.length + 1, 36 * 256);

            int rowNum = 2;
            for (InboundImportErrorRow row : errorRows) {
                Row dataRow = sheet.createRow(rowNum++);
                dataRow.createCell(0).setCellValue(StrUtil.blankToDefault(row.sourceTypeName(), ""));
                dataRow.createCell(1).setCellValue(StrUtil.blankToDefault(row.sourceOrderNo(), ""));
                dataRow.createCell(2).setCellValue(StrUtil.blankToDefault(row.warehouseCode(), ""));
                dataRow.createCell(3).setCellValue(StrUtil.blankToDefault(row.locationCode(), ""));
                dataRow.createCell(4).setCellValue(StrUtil.blankToDefault(row.materialCode(), ""));
                dataRow.createCell(5).setCellValue(StrUtil.blankToDefault(row.quantityText(), ""));
                dataRow.createCell(6).setCellValue(StrUtil.blankToDefault(row.errorField(), ""));
                dataRow.createCell(7).setCellValue(StrUtil.blankToDefault(row.errorReason(), ""));
            }
            workbook.write(outputStream);
            outputStream.flush();
            return file.getName();
        } catch (Exception ex) {
            log.error("生成入库导入错误文件失败", ex);
            return null;
        }
    }

    private String resolveInboundSourceType(String sourceTypeName) {
        return switch (StrUtil.blankToDefault(StrUtil.trim(sourceTypeName), "")) {
            case "采购入库" -> "purchase";
            case "调拨入库" -> "transfer";
            case "退料入库" -> "material_return";
            case "盘盈入库" -> "surplus";
            case "销售退货入库" -> "return";
            case "捐赠入库" -> "donation";
            case "其他入库" -> "other";
            default -> throw BizException.validationFailed("当前入库类型暂不支持导入");
        };
    }

    private Long findSourceOrderIdBySourceType(String sourceTypeName, String sourceOrderNo) {
        return switch (StrUtil.blankToDefault(StrUtil.trim(sourceTypeName), "")) {
            case "采购入库" -> findPurchaseOrderIdBySourceOrderNo(sourceOrderNo);
            case "调拨入库" -> findTransferOrderIdBySourceOrderNo(sourceOrderNo);
            case "退料入库" -> findMaterialReturnOrderIdBySourceOrderNo(sourceOrderNo);
            case "盘盈入库" -> findSurplusOrderIdBySourceOrderNo(sourceOrderNo);
            case "销售退货入库" -> findReturnOrderIdBySourceOrderNo(sourceOrderNo);
            case "捐赠入库", "其他入库" -> null;
            default -> null;
        };
    }

    private boolean isSourceOrderOptionalInboundType(String sourceTypeName) {
        String normalizedType = StrUtil.blankToDefault(StrUtil.trim(sourceTypeName), "");
        return "捐赠入库".equals(normalizedType) || "其他入库".equals(normalizedType);
    }

    private Long findPurchaseOrderIdBySourceOrderNo(String sourceOrderNo) {
        if (StrUtil.isBlank(sourceOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM scm_purchase_order WHERE order_no = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                sourceOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findTransferOrderIdBySourceOrderNo(String sourceOrderNo) {
        if (StrUtil.isBlank(sourceOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM wms_outbound_order WHERE outbound_no = ? AND deleted = 0 AND outbound_type = 'transfer' AND status IN ('approved', 'completed') LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                sourceOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findMaterialReturnOrderIdBySourceOrderNo(String sourceOrderNo) {
        if (StrUtil.isBlank(sourceOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM wms_outbound_order WHERE outbound_no = ? AND deleted = 0 AND outbound_type = 'requisition' AND status IN ('approved', 'completed') LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                sourceOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findSurplusOrderIdBySourceOrderNo(String sourceOrderNo) {
        if (StrUtil.isBlank(sourceOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM wms_stocktake_order WHERE stocktake_no = ? AND deleted = 0 AND status = 'approved' LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                sourceOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long findReturnOrderIdBySourceOrderNo(String sourceOrderNo) {
        if (StrUtil.isBlank(sourceOrderNo)) {
            return null;
        }
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM wms_outbound_order WHERE outbound_no = ? AND deleted = 0 AND outbound_type = 'return' AND status IN ('approved', 'completed') LIMIT 1",
                (rs, rowNum) -> rs.getLong("id"),
                sourceOrderNo.trim()
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private boolean isInboundImportRowBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (int i = 0; i < 6; i++) {
            if (StrUtil.isNotBlank(readCellAsString(row, i))) {
                return false;
            }
        }
        return true;
    }

    private String readCellAsString(Row row, int cellIndex) {
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(cellIndex);
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> StrUtil.trim(cell.getStringCellValue());
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> StrUtil.trim(cell.getCellFormula());
            default -> "";
        };
    }

    private record PurchaseValidationItem(String lineKey, Long materialId, BigDecimal quantity) {
    }

    private record PurchaseInboundBalance(Long materialId,
                                          String materialName,
                                          String spec,
                                          String unit,
                                          BigDecimal unitPrice,
                                          BigDecimal orderQty,
                                          BigDecimal occupiedQty,
                                          BigDecimal remainingQty) {
    }

    private record PurchaseOrderInboundSnapshot(String status, List<PurchaseInboundBalance> balances) {
    }

    private record InboundImportRow(int rowNumber,
                                    String sourceTypeName,
                                    String sourceOrderNo,
                                    String warehouseCode,
                                    String locationCode,
                                    String materialCode,
                                    String quantityText) {
    }

    private record InboundImportErrorRow(int rowNumber,
                                         String sourceTypeName,
                                         String sourceOrderNo,
                                         String warehouseCode,
                                         String locationCode,
                                         String materialCode,
                                         String quantityText,
                                         String errorField,
                                         String errorReason) {
    }

    private record ResolvedInboundImportRow(int rowNumber,
                                            String sourceTypeName,
                                            String sourceOrderNo,
                                            String warehouseCode,
                                            String locationCode,
                                            String materialCode,
                                            BigDecimal quantity,
                                            Long sourceOrderId,
                                            Warehouse warehouse,
                                            Material material) {
    }

    private record InboundImportRowValidation(ResolvedInboundImportRow resolvedRow,
                                              InboundImportResultErrorDTO error) {
        private static InboundImportRowValidation success(ResolvedInboundImportRow row) {
            return new InboundImportRowValidation(row, null);
        }

        private static InboundImportRowValidation error(InboundImportResultErrorDTO error) {
            return new InboundImportRowValidation(null, error);
        }
    }

    private String generateInboundNo() {
        return "IN" + System.currentTimeMillis();
    }

    private long nextVersion(Long version) {
        return version == null ? 1L : version + 1L;
    }

    /**
     * 事务提交后触发物料告警校验
     */
    private void scheduleMaterialAlertCheck(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            log.debug("物料告警校验跳过：materialIds为空");
            return;
        }
        log.info("注册物料告警校验回调：materialIds={}", materialIds);
        try {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info("事务已提交，开始执行物料告警校验：materialIds={}", materialIds);
                            try {
                                materialAlertEngine.checkMaterials(materialIds);
                            } catch (Exception e) {
                                log.error("事务提交后物料告警校验异常: materialIds={}, error={}", materialIds, e.getMessage(), e);
                            }
                        }
                    });
        } catch (Exception e) {
            // 非事务上下文中直接调用
            log.warn("物料告警校验注册失败，直接调用: materialIds={}", materialIds);
            materialAlertEngine.checkMaterials(materialIds);
        }
    }

    private InboundOrderWriteResultVO toWriteResult(InboundOrder order) {
        return new InboundOrderWriteResultVO(order.getId(), order.getVersion());
    }

    @Override
    public List<PurchaseOrderItemForInboundVO> listPurchaseOrderItemsForInbound(Long purchaseOrderId, Long excludeInboundOrderId) {
        return queryPurchaseInboundBalances(purchaseOrderId, excludeInboundOrderId).stream()
                .filter(balance -> balance.remainingQty().compareTo(BigDecimal.ZERO) > 0)
                .map(balance -> {
                    PurchaseOrderItemForInboundVO vo = new PurchaseOrderItemForInboundVO();
                    vo.setMaterialId(balance.materialId());
                    vo.setMaterialName(balance.materialName());
                    vo.setSpec(balance.spec());
                    vo.setUnit(balance.unit());
                    vo.setUnitPrice(balance.unitPrice());
                    vo.setOrderQty(balance.orderQty());
                    vo.setInboundQty(balance.occupiedQty());
                    vo.setRemainingInboundQty(balance.remainingQty());
                    return vo;
                })
                .toList();
    }
}
