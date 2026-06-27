package com.xykj.wms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.wms.dto.OutboundImportResultDTO;
import com.xykj.wms.dto.OutboundImportTaskActionDTO;
import com.xykj.wms.dto.OutboundImportTaskDTO;
import com.xykj.wms.dto.OutboundOrderCreateDTO;
import com.xykj.wms.dto.OutboundOrderQueryDTO;
import com.xykj.wms.dto.OutboundOrderUpdateDTO;
import com.xykj.wms.dto.OutboundSuggestionPreviewDTO;
import com.xykj.wms.dto.OutboundSuggestionRevalidateDTO;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.entity.Material;
import com.xykj.wms.entity.OutboundImportTask;
import com.xykj.wms.entity.OutboundImportTaskRow;
import com.xykj.wms.entity.OutboundOrder;
import com.xykj.wms.entity.OutboundOrderAllocation;
import com.xykj.wms.entity.OutboundOrderItem;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.wms.mapper.OutboundImportTaskMapper;
import com.xykj.wms.mapper.OutboundImportTaskRowMapper;
import com.xykj.wms.mapper.OutboundOrderAllocationMapper;
import com.xykj.wms.mapper.OutboundOrderItemMapper;
import com.xykj.wms.mapper.OutboundOrderMapper;
import com.xykj.wms.service.OutboundOrderService;
import com.xykj.wms.service.StocktakeLockService;
import com.xykj.wms.service.support.LocationAreaPostingService;
import com.xykj.wms.service.support.LocationAreaValidationService;
import com.xykj.wms.service.support.WarehouseStatusRefreshService;
import com.xykj.wms.vo.OutboundOrderAllocationVO;
import com.xykj.wms.vo.OutboundOrderItemVO;
import com.xykj.wms.vo.OutboundOrderStatisticsVO;
import com.xykj.wms.vo.OutboundOrderVO;
import com.xykj.wms.vo.OutboundSourceOrderOptionVO;
import com.xykj.wms.vo.OutboundSuggestionPreviewVO;
import com.xykj.wms.vo.OutboundSuggestionRevalidateVO;
import com.xykj.wms.vo.OutboundSuggestionStockCandidateVO;
import com.xykj.wms.vo.OutboundTypeOptionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@Slf4j
@Service
public class OutboundOrderServiceImpl implements OutboundOrderService {

    private final OutboundOrderMapper outboundOrderMapper;
    private final OutboundOrderItemMapper outboundOrderItemMapper;
    private final OutboundOrderAllocationMapper outboundOrderAllocationMapper;
    private final InventoryMapper inventoryMapper;
    private final OutboundImportTaskMapper outboundImportTaskMapper;
    private final OutboundImportTaskRowMapper outboundImportTaskRowMapper;
    private final MaterialMapper materialMapper;
    private final LocationMapper locationMapper;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;
    private final StocktakeLockService stocktakeLockService;
    private final WarehouseStatusRefreshService warehouseStatusRefreshService;
    private final FileStorageService fileStorageService;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
    private final LocationAreaValidationService locationAreaValidationService;
    private final LocationAreaPostingService locationAreaPostingService;
    private final com.xykj.wms.service.alert.MaterialAlertEngine materialAlertEngine;
    private final AuditLogService auditLogService;

    private static final long IMAGE_ATTACHMENT_MAX_SIZE = 20L * 1024 * 1024;
    private static final long VIDEO_ATTACHMENT_MAX_SIZE = 200L * 1024 * 1024;
    private static final long DOCUMENT_ATTACHMENT_MAX_SIZE = 50L * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/quicktime",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "text/plain"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp",
            ".mp4", ".mov",
            ".pdf", ".xlsx", ".xls", ".docx", ".doc", ".txt"
    );

    private static final String OUTBOUND_TYPE_DICT = "wms_outbound_type";
    private static final String DEFAULT_BATCH_NO = "DEFAULT";
    private static final String VERSION_CONFLICT_CODE = "VERSION_CONFLICT";
    private static final String OUTBOUND_IMPORT_TEMPLATE_SHEET = "出库单导入模板";
    private static final String OUTBOUND_IMPORT_ERROR_FILE_PREFIX = "outbound-import-";
    private static final String OUTBOUND_SUGGEST_RULE_VERSION = "OUTBOUND_SUGGEST_V1";
    private static final String SUGGEST_SOURCE_TYPE = "suggestion_apply";
    private static final int SUGGEST_SPLIT_WARNING_LIMIT = 5;
    private static final String[] OUTBOUND_IMPORT_TEMPLATE_HEADERS = {
            "出库单号（可选）", "出库类型", "出库日期", "申请组织", "仓库编码", "仓位编码", "物料编码", "批次号", "出库数量", "备注", "导入分组号",
            "调入组织", "原入库单号", "结算主体", "受赠主体", "报废原因", "盘点任务号"
    };
    private static final int[] OUTBOUND_IMPORT_TEMPLATE_WIDTHS = {22, 16, 16, 18, 18, 18, 18, 16, 14, 20, 16, 18, 18, 18, 18, 18, 18};
    private static final AtomicInteger sequence = new AtomicInteger(1);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public OutboundOrderServiceImpl(OutboundOrderMapper outboundOrderMapper,
                                    OutboundOrderItemMapper outboundOrderItemMapper,
                                    OutboundOrderAllocationMapper outboundOrderAllocationMapper,
                                    InventoryMapper inventoryMapper,
                                    DataScopeService dataScopeService,
                                    JdbcTemplate jdbcTemplate,
                                    StocktakeLockService stocktakeLockService,
                                    WarehouseStatusRefreshService warehouseStatusRefreshService,
                                    FileStorageService fileStorageService,
                                    MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
                                    LocationAreaValidationService locationAreaValidationService,
                                    LocationAreaPostingService locationAreaPostingService,
                                    AuditLogService auditLogService) {
        this(null, null, outboundOrderMapper, outboundOrderItemMapper, outboundOrderAllocationMapper, inventoryMapper, null, null, dataScopeService, jdbcTemplate,
                stocktakeLockService, warehouseStatusRefreshService, fileStorageService,
                materialCategoryCoefficientLockService, locationAreaValidationService, locationAreaPostingService, null, auditLogService);
    }

    public OutboundOrderServiceImpl(OutboundOrderMapper outboundOrderMapper,
                                    OutboundOrderItemMapper outboundOrderItemMapper,
                                    OutboundOrderAllocationMapper outboundOrderAllocationMapper,
                                    InventoryMapper inventoryMapper,
                                    MaterialMapper materialMapper,
                                    LocationMapper locationMapper,
                                    DataScopeService dataScopeService,
                                    JdbcTemplate jdbcTemplate,
                                    StocktakeLockService stocktakeLockService,
                                    WarehouseStatusRefreshService warehouseStatusRefreshService,
                                    FileStorageService fileStorageService,
                                    MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
                                    LocationAreaValidationService locationAreaValidationService,
                                    LocationAreaPostingService locationAreaPostingService,
                                    AuditLogService auditLogService) {
        this(null, null, outboundOrderMapper, outboundOrderItemMapper, outboundOrderAllocationMapper, inventoryMapper, materialMapper, locationMapper, dataScopeService, jdbcTemplate,
                stocktakeLockService, warehouseStatusRefreshService, fileStorageService,
                materialCategoryCoefficientLockService, locationAreaValidationService, locationAreaPostingService, null, auditLogService);
    }

    @Autowired
    public OutboundOrderServiceImpl(OutboundImportTaskMapper outboundImportTaskMapper,
                                    OutboundImportTaskRowMapper outboundImportTaskRowMapper,
                                    OutboundOrderMapper outboundOrderMapper,
                                    OutboundOrderItemMapper outboundOrderItemMapper,
                                    OutboundOrderAllocationMapper outboundOrderAllocationMapper,
                                    InventoryMapper inventoryMapper,
                                    MaterialMapper materialMapper,
                                    LocationMapper locationMapper,
                                    DataScopeService dataScopeService,
                                    JdbcTemplate jdbcTemplate,
                                    StocktakeLockService stocktakeLockService,
                                    WarehouseStatusRefreshService warehouseStatusRefreshService,
                                    FileStorageService fileStorageService,
                                    MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
                                    LocationAreaValidationService locationAreaValidationService,
                                    LocationAreaPostingService locationAreaPostingService,
                                    com.xykj.wms.service.alert.MaterialAlertEngine materialAlertEngine,
                                    AuditLogService auditLogService) {
        this.outboundImportTaskMapper = outboundImportTaskMapper;
        this.outboundImportTaskRowMapper = outboundImportTaskRowMapper;
        this.outboundOrderMapper = outboundOrderMapper;
        this.outboundOrderItemMapper = outboundOrderItemMapper;
        this.outboundOrderAllocationMapper = outboundOrderAllocationMapper;
        this.inventoryMapper = inventoryMapper;
        this.materialMapper = materialMapper;
        this.locationMapper = locationMapper;
        this.dataScopeService = dataScopeService;
        this.jdbcTemplate = jdbcTemplate;
        this.stocktakeLockService = stocktakeLockService;
        this.warehouseStatusRefreshService = warehouseStatusRefreshService;
        this.fileStorageService = fileStorageService;
        this.materialCategoryCoefficientLockService = materialCategoryCoefficientLockService;
        this.locationAreaValidationService = locationAreaValidationService;
        this.locationAreaPostingService = locationAreaPostingService;
        this.materialAlertEngine = materialAlertEngine;
        this.auditLogService = auditLogService;
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(OUTBOUND_IMPORT_TEMPLATE_SHEET);

            Row tipRow = sheet.createRow(0);
            tipRow.createCell(0).setCellValue("【说明】仅支持 .xlsx，请按出库类型填写对应字段；通用字段与明细字段均在同一表头中，不适用字段可留空。出库单号可不填，由系统生成。调拨出库请填写调入组织，退货出库请填写原入库单号，销售出库请填写结算主体，捐赠出库请填写受赠主体，报废出库请填写报废原因，盘亏/报损类业务请填写盘点任务号。导入解析将在后续任务实现，此模板当前仅用于下载结构。");            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, OUTBOUND_IMPORT_TEMPLATE_HEADERS.length - 1));

            Row headerRow = sheet.createRow(1);
            for (int i = 0; i < OUTBOUND_IMPORT_TEMPLATE_HEADERS.length; i++) {
                headerRow.createCell(i).setCellValue(OUTBOUND_IMPORT_TEMPLATE_HEADERS[i]);
                sheet.setColumnWidth(i, OUTBOUND_IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }

            Row sampleRow = sheet.createRow(2);
            sampleRow.createCell(0).setCellValue("CK20260609001");
            sampleRow.createCell(1).setCellValue("调拨出库");
            sampleRow.createCell(2).setCellValue("2026-06-09");
            sampleRow.createCell(3).setCellValue("总部食堂");
            sampleRow.createCell(4).setCellValue("WH001");
            sampleRow.createCell(5).setCellValue("A-01-01");
            sampleRow.createCell(6).setCellValue("MAT001");
            sampleRow.createCell(7).setCellValue("B-20260609-01");
            sampleRow.createCell(8).setCellValue("10");
            sampleRow.createCell(9).setCellValue("模板示例");
            sampleRow.createCell(10).setCellValue("GROUP-001");
            sampleRow.createCell(11).setCellValue("二号门店");

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("出库单导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException ex) {
            log.error("下载出库导入模板失败", ex);
            throw BizException.badRequest("下载出库导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OutboundImportResultDTO importOrders(MultipartFile file) {
        ensureImportPermission("无权导入出库单");
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("导入文件不能为空");
        }
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<OutboundImportError> errors = new ArrayList<>();
            Map<String, ImportGroup> groups = new LinkedHashMap<>();
            for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                ImportRow importRow = parseImportRow(row);
                if (importRow.error() != null) {
                    errors.add(importRow.error());
                    continue;
                }
                String groupKey = StrUtil.blankToDefault(importRow.groupNo(), "ROW-" + importRow.rowNumber());
                groups.computeIfAbsent(groupKey, key -> new ImportGroup(groupKey)).rows().add(importRow);
            }
            markConflictingOverwriteGroups(groups, errors);
            String taskNo = "TASK-" + System.currentTimeMillis();
            for (ImportGroup group : groups.values()) {
                if (group.failed()) {
                    continue;
                }
                persistImportGroup(group, errors, taskNo);
            }
            int total = groups.values().stream().mapToInt(group -> group.rows().size()).sum() + errors.stream().filter(error -> error.rowNumber() != null).map(OutboundImportError::rowNumber).distinct().toList().size() - groups.values().stream().filter(ImportGroup::failed).mapToInt(group -> group.rows().size()).sum();
            int failureCount = errors.size();
            int successCount = total - failureCount;
            String taskStatus = failureCount > 0 ? "FAILED" : "COMPLETED";
            String errorFileName = failureCount > 0 ? generateImportErrorFile(taskNo, errors) : null;
            OutboundImportTaskDTO taskDto = new OutboundImportTaskDTO(taskNo, taskStatus, total, successCount, failureCount, errorFileName, List.of());
            return new OutboundImportResultDTO(successCount, failureCount, total, failureCount > 0 && successCount > 0, errors.stream().map(OutboundImportError::toDto).toList(), errorFileName, taskNo, taskStatus, taskDto);
        } catch (BizException ex) {
            throw ex;
        } catch (IOException ex) {
            throw BizException.badRequest("导入文件解析失败");
        }
    }

    @Override
    public OutboundImportTaskDTO getImportTask(String taskNo) {
        ParsedTaskRequest request = parseTaskRequest(taskNo);
        OutboundImportTask task = loadOwnedTask(request.taskNo(), false);
        List<OutboundImportTaskRow> rows = loadTaskRows(task.getId());
        return toTaskDto(task, filterRows(rows, request.filters()));
    }

    @Override
    public OutboundImportTaskDTO resumeImportTask(String taskNo, OutboundImportTaskActionDTO action) {
        String sourceTaskNo = action == null ? null : StrUtil.trim(action.getSourceTaskNo());
        if (StrUtil.isBlank(sourceTaskNo)) {
            throw BizException.badRequest("sourceTaskNo不能为空");
        }
        OutboundImportTask task = loadOwnedTask(sourceTaskNo, true);
        List<OutboundImportTaskRow> rows = loadTaskRows(task.getId());
        for (OutboundImportTaskRow row : rows) {
            if (!isUnhandledRow(row)) {
                continue;
            }
            processTaskRow(task, row);
        }
        refreshTaskCounts(task, rows);
        return toTaskDto(task, rows);
    }

    @Override
    public OutboundImportTaskDTO terminateImportTask(String taskNo, OutboundImportTaskActionDTO action) {
        OutboundImportTask task = loadOwnedTask(taskNo, false);
        List<OutboundImportTaskRow> rows = loadTaskRows(task.getId());
        for (OutboundImportTaskRow row : rows) {
            if (!isUnhandledRow(row)) {
                continue;
            }
            row.setStatus("TERMINATED_UNEXECUTED");
            row.setErrorField("taskStatus");
            row.setErrorReason("已终止未执行");
            row.setExceptionType("TASK_TERMINATED");
            updateTaskRow(row);
        }
        task.setStatus("TERMINATED");
        refreshTaskCounts(task, rows);
        return toTaskDto(task, rows);
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
        try (java.io.OutputStream outputStream = response.getOutputStream()) {
            Files.copy(file.toPath(), outputStream);
            outputStream.flush();
        } catch (IOException ex) {
            log.error("下载出库导入错误文件失败", ex);
            throw BizException.badRequest("下载出库导入错误文件失败");
        }
    }

    @Override
    public void exportList(OutboundOrderQueryDTO query, HttpServletResponse response) {
        ensureExportPermission("无权导出出库单列表");
        List<OutboundOrderVO> orders = exportOrders(query);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("出库单列表");
            writeRow(sheet, 0, "出库单号", "出库类型", "出库组织", "用途", "来源单据编号", "出库总数量", "出库总金额", "状态", "创建时间", "更新时间");
            int rowIndex = 1;
            for (OutboundOrderVO order : orders) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(StrUtil.blankToDefault(order.getOutboundNo(), ""));
                row.createCell(1).setCellValue(resolveOutboundTypeLabel(order.getOutboundType()));
                row.createCell(2).setCellValue(StrUtil.blankToDefault(order.getOrgName(), ""));
                row.createCell(3).setCellValue(StrUtil.blankToDefault(order.getPurpose(), ""));
                row.createCell(4).setCellValue(StrUtil.blankToDefault(order.getSourceOrderNo(), ""));
                row.createCell(5).setCellValue(defaultAmount(order.getTotalQuantity()).stripTrailingZeros().toPlainString());
                row.createCell(6).setCellValue(defaultAmount(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP).toPlainString());
                row.createCell(7).setCellValue(resolveOutboundStatusLabel(order.getStatus()));
                row.createCell(8).setCellValue(order.getCreatedAt() == null ? "" : order.getCreatedAt().toString());
                row.createCell(9).setCellValue(order.getUpdatedAt() == null ? "" : order.getUpdatedAt().toString());
            }
            writeExportWorkbook(response, workbook, "outbound-orders.xlsx");
        } catch (IOException ex) {
            throw BizException.badRequest("导出出库单列表失败");
        }
    }

    @Override
    public void exportDetails(OutboundOrderQueryDTO query, HttpServletResponse response) {
        ensureExportPermission("无权导出出库单明细");
        List<OutboundOrderVO> orders = exportOrders(query);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("出库单明细");
            writeRow(sheet, 0, "出库单号", "物料名称", "单位", "出库数量", "AI建议状态", "是否应用AI建议");
            int rowIndex = 1;
            for (OutboundOrderVO order : orders) {
                List<OutboundOrderItemVO> items = outboundOrderItemMapper.selectByOutboundId(order.getId());
                if (items == null || items.isEmpty()) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(StrUtil.blankToDefault(order.getOutboundNo(), ""));
                    row.createCell(4).setCellValue("未生成");
                    row.createCell(5).setCellValue("否");
                    continue;
                }
                for (OutboundOrderItemVO item : items) {
                    Row row = sheet.createRow(rowIndex++);
                    row.createCell(0).setCellValue(StrUtil.blankToDefault(order.getOutboundNo(), ""));
                    row.createCell(1).setCellValue(StrUtil.blankToDefault(item.getMaterialName(), ""));
                    row.createCell(2).setCellValue(StrUtil.blankToDefault(item.getUnit(), ""));
                    row.createCell(3).setCellValue(defaultAmount(item.getQuantity()).stripTrailingZeros().toPlainString());
                    row.createCell(4).setCellValue("未生成");
                    row.createCell(5).setCellValue("否");
                }
            }
            writeExportWorkbook(response, workbook, "outbound-order-details.xlsx");
        } catch (IOException ex) {
            throw BizException.badRequest("导出出库单明细失败");
        }
    }

    @Override
    public List<OutboundTypeOptionVO> typeOptions() {
        List<OutboundTypeOptionVO> options = loadOutboundTypeOptions();
        return options.isEmpty() ? fallbackOutboundTypeOptions() : options;
    }

    @DataScope
    private List<OutboundOrderVO> exportOrders(OutboundOrderQueryDTO query) {
        OutboundOrderQueryDTO exportQuery = query == null ? new OutboundOrderQueryDTO() : query;
        if (exportQuery.getOrgId() == null && exportQuery.getOrgIds() != null && exportQuery.getOrgIds().isEmpty()) {
            return List.of();
        }
        List<OutboundOrderVO> rows = outboundOrderMapper.selectOutboundExportRows(exportQuery);
        return rows == null ? List.of() : rows;
    }

    private void writeRow(Sheet sheet, int rowIndex, String... values) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) {
            row.createCell(i).setCellValue(values[i]);
        }
    }

    private void writeExportWorkbook(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
        response.flushBuffer();
    }

    public PageResult<OutboundOrderVO> list(OutboundOrderQueryDTO query) {
        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            return PageResult.empty((long) query.getPageNum(), (long) query.getPageSize());
        }

        Page<OutboundOrderVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        outboundOrderMapper.selectOutboundPage(page, query);
        return PageResult.of(page);
    }

    @Override
    public OutboundOrderStatisticsVO getStatistics() {
        OutboundOrderStatisticsVO vo = new OutboundOrderStatisticsVO();
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        if (allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            vo.setTotalCount(0L);
            vo.setDraftCount(0L);
            vo.setPendingCount(0L);
            vo.setApprovedCount(0L);
            vo.setCompletedCount(0L);
            vo.setThisMonthAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return vo;
        }

        StringBuilder baseSql = new StringBuilder(" FROM wms_outbound_order WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (allowedOrgIds != null) {
            baseSql.append(" AND org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }

        vo.setTotalCount(queryCount("SELECT COUNT(*)" + baseSql, args));
        vo.setDraftCount(queryCount("SELECT COUNT(*)" + baseSql + " AND status = 'draft'", args));
        vo.setPendingCount(queryCount("SELECT COUNT(*)" + baseSql + " AND status = 'pending'", args));
        vo.setApprovedCount(queryCount("SELECT COUNT(*)" + baseSql + " AND status = 'approved'", args));
        vo.setCompletedCount(queryCount("SELECT COUNT(*)" + baseSql + " AND status = 'completed'", args));
        vo.setThisMonthAmount(defaultAmount(queryAmount(
                "SELECT SUM(COALESCE(total_amount, 0))" + baseSql + " AND status = 'completed' AND DATE_FORMAT(completed_at, '%Y-%m') = DATE_FORMAT(NOW(), '%Y-%m')",
                args
        )).setScale(2, RoundingMode.HALF_UP));
        return vo;
    }

    @Override
    public List<OutboundSourceOrderOptionVO> listSourceOrderOptions(String outboundType) {
        OutboundTypeOptionVO option = getOutboundTypeOption(outboundType);
        if (option == null || !isTypeActive(option)) {
            return List.of();
        }
        return switch (StrUtil.blankToDefault(outboundType, "")) {
            case "requisition" -> listRequisitionSourceOrderOptions();
            case "sales", "return", "transfer", "loss", "donation", "scrap", "other" -> List.of();
            default -> List.of();
        };
    }

    @Override
    public OutboundOrderVO getDetail(Long id) {
        OutboundOrder order = getOrderById(id, true);
        OutboundOrderVO vo = outboundOrderMapper.selectOutboundDetail(order.getId());
        if (vo == null) {
            throw BizException.notFound("出库单不存在");
        }
        List<OutboundOrderItemVO> items = outboundOrderItemMapper.selectByOutboundId(id);
        Map<Long, List<OutboundOrderAllocationVO>> allocationMap = outboundOrderAllocationMapper.selectByOutboundId(id)
                .stream()
                .collect(Collectors.groupingBy(OutboundOrderAllocationVO::getOutboundItemId, LinkedHashMap::new, Collectors.toList()));
        for (OutboundOrderItemVO item : items) {
            item.setAllocations(allocationMap.getOrDefault(item.getId(), List.of()));
        }
        vo.setItems(items);
        return vo;
    }

    @Override
    public OutboundSuggestionPreviewVO previewSuggestions(OutboundSuggestionPreviewDTO dto) {
        Long orgId = resolveSuggestionOrgId(dto.getOrderId());
        Long tenantId = resolveTenantId();
        validateSuggestionAccess(orgId);

        OutboundSuggestionPreviewVO result = buildSuggestionPreview(dto, orgId, tenantId);
        recordSuggestionPreviewAudit(dto, result, orgId, tenantId);
        return result;
    }

    @Override
    public OutboundSuggestionRevalidateVO revalidateSuggestions(OutboundSuggestionRevalidateDTO dto) {
        Long orgId = resolveSuggestionOrgId(dto.getOrderId());
        Long tenantId = resolveTenantId();
        validateSuggestionAccess(orgId);
        return buildSuggestionRevalidate(dto, orgId, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(OutboundOrderCreateDTO dto) {
        assertOutboundTypeEnabled(dto.getOutboundType());
        ensureWritePermission("无权创建出库单", "outbound:create");
        OutboundOrder order = new OutboundOrder();
        BeanUtil.copyProperties(dto, order, "items");
        order.setTargetOrgId(dto.getTargetOrgId());
        order.setSourceOrderId(dto.getSourceOrderId());
        order.setSourceOrderNo(dto.getSourceOrderNo());
        order.setOutboundNo(generateOutboundNo());
        order.setStatus("draft");
        order.setTotalAmount(BigDecimal.ZERO);
        Long currentOrgId = UserContext.getOrgId();
        if (currentOrgId == null) {
            throw BizException.badRequest("当前用户缺少组织信息");
        }
        ensureOrgAllowed(currentOrgId);
        order.setOrgId(currentOrgId);
        order.setTenantId(resolveTenantId());
        List<PersistedOrderItemInput> itemInputs = buildCreateItemInputs(dto.getItems());
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                itemInputs.stream().map(PersistedOrderItemInput::materialId).toList(),
                "保存出库单"
        );
        validatePersistedOrderItems(itemInputs, order.getWarehouseId(), currentOrgId, order.getTenantId());
        applyOrderHeaderWarehouse(order, itemInputs);
        outboundOrderMapper.insert(order);

        saveItems(order.getId(), itemInputs, currentOrgId, order.getTenantId());
        recalcTotals(order);
        recordOrderAudit(AuditOperationType.CREATE, order.getId(), order.getOutboundNo(), null, buildOutboundAuditSnapshot(order.getId()), "新增出库单");

        log.info("新增出库单: id={}, no={}", order.getId(), order.getOutboundNo());

        // 关联菜谱计划时，更新烹饪任务备料状态
        if ("requisition".equals(order.getOutboundType()) && order.getSourceOrderId() != null) {
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE cook_task SET material_prep_status = 'pending_prep', updated_at = NOW() " +
                        "WHERE plan_id = ? AND deleted = 0 AND material_prep_status IS NULL",
                    order.getSourceOrderId());
                if (updated > 0) {
                    log.info("关联计划[{}]，更新{}个任务备料状态为待备料", order.getSourceOrderId(), updated);
                }
            } catch (Exception e) {
                log.warn("更新备料状态失败, sourceOrderId={}: {}", order.getSourceOrderId(), e.getMessage());
            }
        }

        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, OutboundOrderUpdateDTO dto) {
        if (StrUtil.isNotBlank(dto.getOutboundType())) {
            assertOutboundTypeEnabled(dto.getOutboundType());
        }
        ensureWritePermission("无权编辑出库单", "outbound:edit");
        OutboundOrder order = getOrderById(id, true);
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw BizException.validationFailed("只有草稿或已驳回状态的出库单可以编辑");
        }
        String beforeSnapshot = buildOutboundAuditSnapshot(id);
        // 如果是驳回状态编辑，改回草稿
        if ("rejected".equals(order.getStatus())) {
            order.setStatus("draft");
        }
        BeanUtil.copyProperties(dto, order, true);
        // org_id 始终保持当前用户的组织ID，不接受前端传入
        Long userOrgId = UserContext.getOrgId();
        if (userOrgId != null) {
            order.setOrgId(userOrgId);
        }
        if (dto.getTargetOrgId() != null) {
            order.setTargetOrgId(dto.getTargetOrgId());
        }
        if (StrUtil.isNotBlank(dto.getPurpose())) {
            order.setPurpose(dto.getPurpose());
        }
        order.setSourceOrderId(dto.getSourceOrderId());
        order.setSourceOrderNo(StrUtil.isBlank(dto.getSourceOrderNo()) ? null : dto.getSourceOrderNo());

        if (dto.getItems() != null) {
            List<PersistedOrderItemInput> itemInputs = buildUpdateItemInputs(dto.getItems());
            materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                    itemInputs.stream().map(PersistedOrderItemInput::materialId).toList(),
                    "保存出库单"
            );
            validatePersistedOrderItems(itemInputs, order.getWarehouseId(), order.getOrgId(), order.getTenantId());
            applyOrderHeaderWarehouse(order, itemInputs);
            outboundOrderMapper.updateById(order);
            if (dto.getSourceOrderId() == null && StrUtil.isBlank(dto.getSourceOrderNo())) {
                outboundOrderMapper.update(null, new UpdateWrapper<OutboundOrder>()
                        .eq("id", id)
                        .set("source_order_id", null)
                        .set("source_order_no", null));
            }
            List<OutboundOrderItem> oldItems = outboundOrderItemMapper.selectList(
                    new LambdaQueryWrapper<OutboundOrderItem>()
                            .eq(OutboundOrderItem::getOutboundId, id));
            outboundOrderItemMapper.deleteByOutboundId(id);
            outboundOrderAllocationMapper.deleteByOutboundId(id);
            saveItems(id, itemInputs, order.getOrgId(), order.getTenantId());
            recalcTotals(order);
            refreshOrderStatusScope(order, oldItems);
        } else {
            outboundOrderMapper.updateById(order);
            if (dto.getSourceOrderId() == null && StrUtil.isBlank(dto.getSourceOrderNo())) {
                outboundOrderMapper.update(null, new UpdateWrapper<OutboundOrder>()
                        .eq("id", id)
                        .set("source_order_id", null)
                        .set("source_order_no", null));
            }
        }
        recordOrderAudit(AuditOperationType.UPDATE, id, order.getOutboundNo(), beforeSnapshot, buildOutboundAuditSnapshot(id), "编辑出库单");
        log.info("更新出库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        OutboundOrder order = getOrderById(id, true);
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw BizException.validationFailed("只有草稿或已驳回状态的出库单可以删除");
        }
        List<OutboundOrderItem> oldItems = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOutboundId, id));
        outboundOrderAllocationMapper.deleteByOutboundId(id);
        outboundOrderItemMapper.deleteByOutboundId(id);
        // 软删除前修改outbound_no，避免唯一索引冲突
        String delNo = order.getOutboundNo() + "-del-" + id;
        outboundOrderMapper.update(null, new LambdaUpdateWrapper<OutboundOrder>()
                .eq(OutboundOrder::getId, id)
                .set(OutboundOrder::getOutboundNo, delNo));
        outboundOrderMapper.deleteById(id);
        refreshOrderStatusScope(order, oldItems);
        log.info("删除出库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submit(Long id) {
        OutboundOrder order = getOrderById(id, true);
        if (!"draft".equals(order.getStatus()) && !"rejected".equals(order.getStatus())) {
            throw BizException.validationFailed("只有草稿或已驳回状态的出库单可以提交");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                outboundOrderItemMapper.selectList(
                        new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id)
                ).stream().map(OutboundOrderItem::getMaterialId).toList(),
                "提交出库单"
        );
        validateAggregatedBatchStock(order);
        validateStocktakeUnlocked(order, "出库");
        order.setStatus("pending");
        order.setSubmittedAt(LocalDateTime.now());
        order.setSubmittedBy(UserContext.getUserId());
        // 清除之前的驳回信息
        order.setApproveRemark(null);
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("提交出库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id, String approveRemark) {
        OutboundOrder order = getOrderById(id, true);
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审核状态的出库单可以审核");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                outboundOrderItemMapper.selectList(
                        new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id)
                ).stream().map(OutboundOrderItem::getMaterialId).toList(),
                "审核出库单"
        );
        validateAggregatedBatchStock(order);
        validateStocktakeUnlocked(order, "出库");
        order.setStatus("approved");
        order.setApprovedAt(LocalDateTime.now());
        order.setApprovedBy(UserContext.getUserId());
        order.setApproveRemark(approveRemark);
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("审核通过出库单: id={}", id);

        // 如果是领用出库单且有来源菜谱计划，更新关联烹饪任务的备料状态
        if ("requisition".equals(order.getOutboundType()) && order.getSourceOrderId() != null) {
            try {
                int updated = jdbcTemplate.update(
                        "UPDATE cook_task SET material_prep_status = 'prepared', updated_at = NOW() " +
                                "WHERE plan_id = ? AND deleted = 0 AND material_prep_status = 'pending_prep'",
                        order.getSourceOrderId());
                if (updated > 0) {
                    log.info("更新{}个烹饪任务备料状态为已备料, planId={}", updated, order.getSourceOrderId());
                }
            } catch (Exception e) {
                log.warn("更新烹饪任务备料状态失败, sourceOrderId={}: {}", order.getSourceOrderId(), e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String rejectReason) {
        OutboundOrder order = getOrderById(id, true);
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审核状态的出库单可以驳回");
        }
        if (StrUtil.isBlank(rejectReason)) {
            throw BizException.badRequest("驳回原因不能为空");
        }
        order.setStatus("rejected");
        order.setApproveRemark("驳回原因: " + rejectReason);
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("驳回出库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdraw(Long id) {
        OutboundOrder order = getOrderById(id, true);
        if (!"pending".equals(order.getStatus())) {
            throw BizException.validationFailed("只有待审核状态的出库单可以撤回");
        }
        order.setStatus("draft");
        order.setSubmittedAt(null);
        order.setSubmittedBy(null);
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("撤回出库单: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void execute(Long id) {
        OutboundOrder order = getOrderById(id, true);
        if (!"approved".equals(order.getStatus())) {
            throw BizException.validationFailed("只有已审核状态的出库单可以执行出库");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                outboundOrderItemMapper.selectList(
                        new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id)
                ).stream().map(OutboundOrderItem::getMaterialId).toList(),
                "执行出库库存变动"
        );
        validateAggregatedBatchStock(order);
        validateStocktakeUnlocked(order, "出库");

        // 扣减库存
        List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id));
        Map<Long, List<OutboundOrderAllocation>> allocationMap = loadAllocationEntityMap(id);
        for (OutboundOrderItem item : items) {
            List<OutboundOrderAllocation> allocations = allocationMap.getOrDefault(item.getId(), List.of());
            if (!allocations.isEmpty()) {
                deductInventoriesByAllocations(item, allocations);
                if (item.getInventoryId() == null && allocations.size() == 1) {
                    item.setInventoryId(allocations.getFirst().getSourceStockDetailId());
                    outboundOrderItemMapper.updateById(item);
                }
                continue;
            }
            deductInventoriesByItem(item);
        }
        locationAreaPostingService.postOutboundExecutedArea(order, items, locationAreaValidationService);

        order.setStatus("completed");
        order.setCompletedAt(LocalDateTime.now());
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("执行出库: id={}", id);
        // 库存变更后触发物料告警校验
        scheduleMaterialAlertCheck(items.stream().map(OutboundOrderItem::getMaterialId).distinct().toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverse(Long id) {
        OutboundOrder order = getOrderById(id, true);
        if (!"approved".equals(order.getStatus()) && !"completed".equals(order.getStatus())) {
            throw BizException.validationFailed("只有已审核或已出库状态的出库单可以反审核");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                outboundOrderItemMapper.selectList(
                        new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id)
                ).stream().map(OutboundOrderItem::getMaterialId).toList(),
                "反审核出库单"
        );

        if ("completed".equals(order.getStatus())) {
            List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                    new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id));
            Map<Long, List<OutboundOrderAllocation>> allocationMap = loadAllocationEntityMap(id);
            for (OutboundOrderItem item : items) {
                List<OutboundOrderAllocation> allocations = allocationMap.getOrDefault(item.getId(), List.of());
                if (!allocations.isEmpty()) {
                    restoreInventoriesByAllocations(item, allocations);
                    continue;
                }
                restoreInventoriesByItem(item);
            }
            locationAreaPostingService.reverseOutboundExecutedArea(order, items);
            order.setCompletedAt(null);
        }

        order.setStatus("draft");
        order.setApprovedAt(null);
        order.setApprovedBy(null);
        order.setApproveRemark(null);
        outboundOrderMapper.updateById(order);
        refreshOrderStatusScope(order);
        log.info("反审核出库单: id={}", id);
        // 库存恢复后触发物料告警校验
        List<Long> materialIds = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>().eq(OutboundOrderItem::getOutboundId, id))
                .stream().map(OutboundOrderItem::getMaterialId).distinct().toList();
        scheduleMaterialAlertCheck(materialIds);
    }

    // ==================== 私有方法 ====================

    /**
     * 事务提交后触发物料告警校验
     */
    private void scheduleMaterialAlertCheck(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) return;
        log.info("注册物料告警校验回调（出库）：materialIds={}", materialIds);
        try {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            log.info("事务已提交，开始执行物料告警校验（出库）：materialIds={}", materialIds);
                            try {
                                materialAlertEngine.checkMaterials(materialIds);
                            } catch (Exception e) {
                                log.error("事务提交后物料告警校验异常（出库）: materialIds={}, error={}", materialIds, e.getMessage(), e);
                            }
                        }
                    });
        } catch (Exception e) {
            log.warn("物料告警校验注册失败，直接调用（出库）: materialIds={}", materialIds);
            materialAlertEngine.checkMaterials(materialIds);
        }
    }

    private OutboundOrder getOrderById(Long id, boolean checkDataScope) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("出库单不存在");
        }
        if (checkDataScope) {
            ensureOrgAllowed(order.getOrgId());
        }
        return order;
    }

    private boolean isBlankRow(Row row) {
        for (int i = 0; i < 11; i++) {
            if (StrUtil.isNotBlank(readCell(row, i))) {
                return false;
            }
        }
        return true;
    }

    private ImportRow parseImportRow(Row row) {
        Integer rowNumber = row.getRowNum() + 1;
        String outboundNo = trimToNull(readCell(row, 0));
        String outboundTypeName = trimToNull(readCell(row, 1));
        String warehouseCode = trimToNull(readCell(row, 4));
        String materialCode = trimToNull(readCell(row, 6));
        String batchNo = trimToNull(readCell(row, 7));
        String quantityText = trimToNull(readCell(row, 8));
        String remark = trimToNull(readCell(row, 9));
        String importGroupNo = trimToNull(readCell(row, 10));

        if (StrUtil.isBlank(outboundTypeName)) {
            return ImportRow.error(rowNumber, "outboundType", "出库类型不能为空");
        }
        String outboundType = resolveOutboundTypeCode(outboundTypeName);
        if (outboundType == null) {
            return ImportRow.error(rowNumber, "outboundType", "当前出库类型暂不支持导入");
        }
        if (StrUtil.isBlank(warehouseCode)) {
            return ImportRow.error(rowNumber, "warehouseCode", "仓库编码不能为空");
        }
        if (StrUtil.isBlank(materialCode)) {
            return ImportRow.error(rowNumber, "materialCode", "物料编码不能为空");
        }
        if (StrUtil.isBlank(quantityText)) {
            return ImportRow.error(rowNumber, "quantity", "出库数量不能为空");
        }
        BigDecimal quantity;
        try {
            quantity = new BigDecimal(quantityText);
        } catch (NumberFormatException ex) {
            return ImportRow.error(rowNumber, "quantity", "出库数量格式不正确");
        }
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            return ImportRow.error(rowNumber, "quantity", "出库数量必须大于0");
        }

        Long orgId = UserContext.getOrgId();
        Warehouse warehouse = locationMapper.selectWarehouseByCode(orgId, warehouseCode);
        if (warehouse == null) {
            return ImportRow.error(rowNumber, "warehouseCode", "仓库编码不存在");
        }
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, materialCode)
                .eq(Material::getDeleted, 0)
                .last("LIMIT 1"));
        if (material == null || !"active".equalsIgnoreCase(StrUtil.blankToDefault(material.getStatus(), ""))) {
            return ImportRow.error(rowNumber, "materialCode", "物料编码不存在或未启用");
        }

        String effectiveBatchNo = batchNo;
        if (StrUtil.isBlank(effectiveBatchNo) && hasDefaultBatchInventory(warehouse.getId(), material.getId())) {
            effectiveBatchNo = DEFAULT_BATCH_NO;
        }
        if (StrUtil.isBlank(effectiveBatchNo)) {
            return ImportRow.error(rowNumber, "batchNo", "批次号不能为空");
        }

        OutboundOrder existing = null;
        if (StrUtil.isNotBlank(outboundNo)) {
            existing = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()
                    .eq(OutboundOrder::getOutboundNo, outboundNo)
                    .eq(OutboundOrder::getDeleted, 0)
                    .last("LIMIT 1"));
            if (existing != null && !"draft".equals(existing.getStatus())) {
                return ImportRow.error(rowNumber, "outboundNo", "仅支持覆盖草稿单据");
            }
        }

        return new ImportRow(rowNumber, importGroupNo, outboundNo, outboundType, warehouse, material, effectiveBatchNo, quantity, remark, existing, null);
    }

    private String resolveOutboundTypeCode(String typeName) {
        return fallbackOutboundTypeOptions().stream()
                .filter(this::isTypeActive)
                .filter(option -> typeName.equals(option.getTypeName()) || typeName.equals(option.getTypeCode()))
                .map(OutboundTypeOptionVO::getTypeCode)
                .findFirst()
                .orElseGet(() -> {
                    try {
                        return loadOutboundTypeOptions().stream()
                                .filter(this::isTypeActive)
                                .filter(option -> typeName.equals(option.getTypeName()) || typeName.equals(option.getTypeCode()))
                                .map(OutboundTypeOptionVO::getTypeCode)
                                .findFirst()
                                .orElse(null);
                    } catch (Exception ignore) {
                        return null;
                    }
                });
    }

    private boolean hasDefaultBatchInventory(Long warehouseId, Long materialId) {
        Inventory inventory = findInventory(warehouseId, null, materialId, null);
        return inventory != null;
    }

    private void markConflictingOverwriteGroups(Map<String, ImportGroup> groups, List<OutboundImportError> errors) {
        Map<Long, List<ImportGroup>> groupsByExistingOrder = new LinkedHashMap<>();
        for (ImportGroup group : groups.values()) {
            Set<Long> existingIds = group.rows().stream()
                    .map(ImportRow::existingOrder)
                    .filter(java.util.Objects::nonNull)
                    .map(OutboundOrder::getId)
                    .collect(java.util.stream.Collectors.toSet());
            if (existingIds.size() == 1) {
                Long existingId = existingIds.iterator().next();
                groupsByExistingOrder.computeIfAbsent(existingId, key -> new ArrayList<>()).add(group);
            }
        }
        for (List<ImportGroup> conflictingGroups : groupsByExistingOrder.values()) {
            if (conflictingGroups.size() <= 1) {
                continue;
            }
            for (ImportGroup group : conflictingGroups) {
                group.failed(true);
                for (ImportRow row : group.rows()) {
                    errors.add(new OutboundImportError(row.rowNumber(), "importGroupNo", "多个导入分组命中同一已有单据，整组导入失败", "GROUP_CONFLICT", null));
                }
            }
        }
    }

    private void persistImportGroup(ImportGroup group, List<OutboundImportError> errors, String taskNo) {
        if (!hasConsistentOutboundNo(group.rows())) {
            group.failed(true);
            for (ImportRow row : group.rows()) {
                errors.add(new OutboundImportError(row.rowNumber(), "outboundNo", "同一导入分组的出库单号必须一致", "GROUP_OUTBOUND_NO_MISMATCH", taskNo));
            }
            return;
        }
        ImportRow firstRow = group.rows().getFirst();
        if (firstRow.existingOrder() != null) {
            if (!overwriteExistingDraft(firstRow, group.rows(), errors, taskNo)) {
                group.failed(true);
            }
            return;
        }
        createImportedOrder(firstRow, group.rows());
    }

    private boolean hasConsistentOutboundNo(List<ImportRow> rows) {
        Set<String> outboundNos = rows.stream()
                .map(ImportRow::outboundNo)
                .map(this::trimToNull)
                .collect(java.util.stream.Collectors.toSet());
        return outboundNos.size() <= 1;
    }

    private void createImportedOrder(ImportRow firstRow, List<ImportRow> rows) {
        OutboundOrder order = new OutboundOrder();
        order.setOutboundNo(StrUtil.blankToDefault(firstRow.outboundNo(), generateOutboundNo()));
        order.setOutboundType(firstRow.outboundType());
        order.setWarehouseId(firstRow.warehouse().getId());
        order.setRemark(firstRow.remark());
        order.setStatus("draft");
        order.setTotalAmount(BigDecimal.ZERO);
        order.setOrgId(UserContext.getOrgId());
        order.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        outboundOrderMapper.insert(order);
        saveImportedItems(order.getId(), rows);
        recalcTotals(order);
    }

    private boolean overwriteExistingDraft(ImportRow firstRow, List<ImportRow> rows, List<OutboundImportError> errors, String taskNo) {
        OutboundOrder order = firstRow.existingOrder();
        order.setOutboundType(firstRow.outboundType());
        order.setWarehouseId(firstRow.warehouse().getId());
        order.setRemark(firstRow.remark());
        if (outboundOrderMapper.updateById(order) <= 0) {
            errors.add(new OutboundImportError(firstRow.rowNumber(), "outboundNo", "导入覆盖时单据已变更，请刷新后重试", VERSION_CONFLICT_CODE, taskNo));
            return false;
        }
        outboundOrderItemMapper.deleteByOutboundId(order.getId());
        saveImportedItems(order.getId(), rows);
        recalcTotals(order);
        return true;
    }

    private void saveImportedItems(Long outboundId, List<ImportRow> rows) {
        for (ImportRow row : rows) {
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundId(outboundId);
            item.setMaterialId(row.material().getId());
            item.setMaterialName(row.material().getMaterialName());
            item.setSpec(row.material().getSpec());
            item.setUnit(row.material().getUnit());
            item.setWarehouseId(row.warehouse().getId());
            item.setQuantity(row.quantity());
            item.setBatchNo(row.batchNo());
            Inventory inventory = findInventory(row.warehouse().getId(), null, row.material().getId(), DEFAULT_BATCH_NO.equals(row.batchNo()) ? null : row.batchNo());
            if (inventory != null) {
                item.setInventoryId(inventory.getId());
                item.setUnitCost(inventory.getUnitCost());
                if (inventory.getUnitCost() != null) {
                    item.setTotalCost(inventory.getUnitCost().multiply(row.quantity()));
                }
            }
            item.setRemark(row.remark());
            outboundOrderItemMapper.insert(item);
        }
    }

    private String readCell(Row row, int cellIndex) {
        if (row == null || row.getCell(cellIndex) == null) {
            return "";
        }
        row.getCell(cellIndex).setCellType(org.apache.poi.ss.usermodel.CellType.STRING);
        return row.getCell(cellIndex).getStringCellValue();
    }

    private String trimToNull(String value) {
        return StrUtil.blankToDefault(StrUtil.trim(value), null);
    }

    private ParsedTaskRequest parseTaskRequest(String taskNoWithQuery) {
        String raw = StrUtil.blankToDefault(taskNoWithQuery, "");
        String[] parts = raw.split("\\?", 2);
        return new ParsedTaskRequest(parts[0], parts.length > 1 ? parseQueryString(parts[1]) : Map.of());
    }

    private ParsedTaskRequest parseErrorFileTask(String fileName) {
        String taskNo = StrUtil.subBetween(fileName, "outbound-import-", "-errors.xlsx");
        if (StrUtil.isBlank(taskNo)) {
            throw BizException.badRequest("错误文件名非法");
        }
        return new ParsedTaskRequest(taskNo, Map.of());
    }

    private Map<String, String> parseQueryString(String query) {
        if (StrUtil.isBlank(query)) {
            return Map.of();
        }
        Map<String, String> filters = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && StrUtil.isNotBlank(kv[0]) && StrUtil.isNotBlank(kv[1])) {
                filters.put(kv[0], kv[1]);
            }
        }
        return filters;
    }

    private OutboundImportTask loadOwnedTask(String taskNo, boolean enforceRetryOwnerMessage) {
        if (outboundImportTaskMapper == null) {
            throw new UnsupportedOperationException("Import task mapper not configured");
        }
        OutboundImportTask task = outboundImportTaskMapper.selectByTaskNo(taskNo);
        if (task == null) {
            throw BizException.notFound("批量任务不存在");
        }
        Long currentUserId = UserContext.getUserId();
        if (task.getCreatedBy() != null && currentUserId != null && !task.getCreatedBy().equals(currentUserId)) {
            throw BizException.forbidden(enforceRetryOwnerMessage ? "仅原操作人可续导失败行" : "仅原操作人可操作该批量任务");
        }
        return task;
    }

    private List<OutboundImportTaskRow> loadTaskRows(Long taskId) {
        if (outboundImportTaskRowMapper == null) {
            return List.of();
        }
        List<OutboundImportTaskRow> rows = outboundImportTaskRowMapper.selectByTaskId(taskId);
        return rows == null ? new ArrayList<>() : rows;
    }

    private List<OutboundImportTaskRow> filterRows(List<OutboundImportTaskRow> rows, Map<String, String> filters) {
        if (filters.isEmpty()) {
            return rows;
        }
        return rows.stream().filter(row -> matchesFilter(row, filters)).toList();
    }

    private boolean matchesFilter(OutboundImportTaskRow row, Map<String, String> filters) {
        RowPayload payload = parseRowPayload(row.getRawPayload());
        return matchesValue(filters.get("exceptionType"), StrUtil.blankToDefault(row.getExceptionType(), payload.exceptionType()))
                && matchesValue(filters.get("outboundNo"), payload.outboundNo())
                && matchesValue(filters.get("materialCode"), payload.materialCode())
                && matchesValue(filters.get("warehouseCode"), payload.warehouseCode())
                && matchesValue(filters.get("outboundType"), payload.outboundType())
                && matchesValue(filters.get("taskStatus"), row.getStatus());
    }

    private boolean matchesValue(String expected, String actual) {
        return StrUtil.isBlank(expected) || StrUtil.equals(expected, StrUtil.blankToDefault(actual, ""));
    }

    private boolean isUnhandledRow(OutboundImportTaskRow row) {
        String status = StrUtil.blankToDefault(row.getStatus(), "");
        return "PENDING".equalsIgnoreCase(status);
    }

    private void processTaskRow(OutboundImportTask task, OutboundImportTaskRow row) {
        try {
            ImportRow importRow = rebuildImportRow(row);
            createImportedOrder(importRow, List.of(importRow));
            row.setStatus("SUCCESS");
            row.setErrorField(null);
            row.setErrorReason(null);
            row.setExceptionType(null);
        } catch (BizException ex) {
            row.setStatus("FAILED");
            row.setErrorField(StrUtil.blankToDefault(row.getErrorField(), "row"));
            row.setErrorReason(ex.getMessage());
            row.setExceptionType(StrUtil.blankToDefault(ex.getCode(), "BUSINESS_VALIDATION"));
        }
        updateTaskRow(row);
        task.setStatus("COMPLETED");
    }

    private ImportRow rebuildImportRow(OutboundImportTaskRow row) {
        RowPayload payload = parseRowPayload(row.getRawPayload());
        Long orgId = UserContext.getOrgId();
        Warehouse warehouse = locationMapper.selectWarehouseByCode(orgId, payload.warehouseCode());
        if (warehouse == null) {
            throw BizException.badRequest("仓库编码不存在");
        }
        Material material = materialMapper.selectOne(new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, payload.materialCode())
                .eq(Material::getDeleted, 0)
                .last("LIMIT 1"));
        if (material == null) {
            throw BizException.badRequest("物料编码不存在或未启用");
        }
        String effectiveBatchNo = trimToNull(payload.batchNo());
        Inventory inventory = findInventory(warehouse.getId(), null, material.getId(), DEFAULT_BATCH_NO.equals(effectiveBatchNo) ? null : effectiveBatchNo);
        if (inventory == null) {
            throw BizException.badRequest("批次号不能为空");
        }
        BigDecimal quantity = parseQuantity(payload.quantity());
        return new ImportRow(row.getRowNumber(), null, payload.outboundNo(), resolveOutboundTypeCode(payload.outboundType()), warehouse,
                material, effectiveBatchNo, quantity, payload.remark(), null, null);
    }

    private void updateTaskRow(OutboundImportTaskRow row) {
        if (outboundImportTaskRowMapper != null) {
            outboundImportTaskRowMapper.updateById(row);
        }
    }

    private void refreshTaskCounts(OutboundImportTask task, List<OutboundImportTaskRow> rows) {
        int successCount = (int) rows.stream().filter(row -> "SUCCESS".equalsIgnoreCase(StrUtil.blankToDefault(row.getStatus(), ""))).count();
        int failureCount = rows.size() - successCount;
        task.setSuccessCount(successCount);
        task.setFailureCount(failureCount);
        if (!"TERMINATED".equals(task.getStatus())) {
            task.setStatus(failureCount > 0 ? "COMPLETED" : "COMPLETED");
        }
        if (outboundImportTaskMapper != null) {
            outboundImportTaskMapper.updateById(task);
        }
    }

    private OutboundImportTaskDTO toTaskDto(OutboundImportTask task, List<OutboundImportTaskRow> rows) {
        List<OutboundImportTaskDTO.RowDTO> rowDtos = rows.stream().map(row -> {
            RowPayload payload = parseRowPayload(row.getRawPayload());
            return new OutboundImportTaskDTO.RowDTO(
                    row.getRowNumber(),
                    row.getStatus(),
                    row.getErrorField(),
                    row.getErrorReason(),
                    StrUtil.blankToDefault(row.getExceptionType(), payload.exceptionType()),
                    payload.outboundNo(),
                    payload.materialCode(),
                    payload.warehouseCode(),
                    payload.outboundType(),
                    task.getTaskNo()
            );
        }).toList();
        return new OutboundImportTaskDTO(task.getTaskNo(), task.getStatus(), task.getTotalCount(), task.getSuccessCount(), task.getFailureCount(), task.getErrorFileName(), rowDtos);
    }

    private RowPayload parseRowPayload(String rawPayload) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : StrUtil.blankToDefault(rawPayload, "").split(";")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                values.put(kv[0], kv[1]);
            }
        }
        return new RowPayload(
                values.get("outboundNo"),
                values.get("materialCode"),
                values.get("warehouseCode"),
                values.get("outboundType"),
                values.get("exceptionType"),
                values.get("batchNo"),
                values.get("quantity"),
                values.get("remark")
        );
    }

    private BigDecimal parseQuantity(String quantity) {
        try {
            return new BigDecimal(StrUtil.blankToDefault(quantity, "0"));
        } catch (NumberFormatException ex) {
            throw BizException.badRequest("出库数量格式不正确");
        }
    }

    private record ParsedTaskRequest(String taskNo, Map<String, String> filters) {
    }

    private record RowPayload(String outboundNo,
                              String materialCode,
                              String warehouseCode,
                              String outboundType,
                              String exceptionType,
                              String batchNo,
                              String quantity,
                              String remark) {
    }


    private List<Long> resolveAllowedOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return new ArrayList<>(scope.getOrgIds());
    }

    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private void ensureImportPermission(String errorMessage) {
        // Prefer a dedicated import permission when present, but keep the legacy create permission as a compatibility fallback.
        ensureAnyPermission(errorMessage, "outbound:import", "outbound:create");
    }

    private void ensureWritePermission(String errorMessage, String permissionCode) {
        ensureAnyPermission(errorMessage, permissionCode);
    }

    private void ensureExportPermission(String errorMessage) {
        // Prefer a dedicated export permission when present, but keep the legacy audit permission as a compatibility fallback.
        ensureAnyPermission(errorMessage, "outbound:export", "outbound:audit");
    }

    private void ensureAnyPermission(String errorMessage, String... permissionCodes) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden(errorMessage);
        }
        for (String permissionCode : permissionCodes) {
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
            if (count != null && count > 0L) {
                return;
            }
        }
        throw BizException.forbidden(errorMessage);
    }

    private List<OutboundSourceOrderOptionVO> listRequisitionSourceOrderOptions() {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        if (allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return List.of();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT rp.id, rp.plan_code AS orderNo, rp.org_id AS orgId, so.org_name AS orgName " +
                        "FROM recipe_plan rp " +
                        "JOIN cook_task ct ON ct.plan_id = rp.id AND ct.deleted = 0 " +
                        "LEFT JOIN sys_organization so ON so.id = rp.org_id AND so.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND rp.status IN ('approved', 'cooking', 'completed') " +
                        "AND NOT EXISTS (" +
                        "  SELECT 1 FROM wms_outbound_order o" +
                        "  WHERE o.source_order_id = rp.id AND o.outbound_type = 'requisition' AND o.deleted = 0" +
                        ")"
        );
        List<Object> args = new ArrayList<>();
        if (allowedOrgIds != null) {
            sql.append(" AND rp.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        sql.append(" GROUP BY rp.id, rp.plan_code, rp.org_id, so.org_name ORDER BY MAX(rp.updated_at) DESC");

        return jdbcTemplate.query(sql.toString(), args.toArray(), (rs, rowNum) -> {
            OutboundSourceOrderOptionVO vo = new OutboundSourceOrderOptionVO();
            vo.setId(rs.getLong("id"));
            vo.setOrderNo(rs.getString("orderNo"));
            vo.setOrgId(rs.getLong("orgId"));
            if (rs.wasNull()) {
                vo.setOrgId(null);
            }
            vo.setOrgName(rs.getString("orgName"));
            return vo;
        });
    }

    private List<OutboundTypeOptionVO> loadOutboundTypeOptions() {
        return jdbcTemplate.query(
                "SELECT id, dict_code, dict_name, dict_value, status, sort_order, parent_code, remark " +
                        "FROM sys_dict WHERE dict_type = ? AND deleted = 0 ORDER BY sort_order ASC, id ASC",
                (rs, rowNum) -> {
                    OutboundTypeOptionVO option = new OutboundTypeOptionVO();
                    option.setTypeId(rs.getLong("id"));
                    option.setTypeCode(rs.getString("dict_code"));
                    option.setTypeName(rs.getString("dict_name"));
                    option.setStatus(rs.getString("status"));
                    option.setSortOrder(rs.getObject("sort_order", Integer.class));
                    option.setTypeSource(StrUtil.blankToDefault(rs.getString("parent_code"), "dict"));
                    option.setSourceRequirementText(StrUtil.blankToDefault(rs.getString("remark"), ""));
                    applyRuleMetadata(option, rs.getString("dict_value"));
                    return option;
                },
                OUTBOUND_TYPE_DICT
        );
    }

    private List<OutboundTypeOptionVO> fallbackOutboundTypeOptions() {
        return List.of(
                fallbackTypeOption(1L, "requisition", "领用出库", 10),
                fallbackTypeOption(2L, "sales", "销售出库", 20),
                fallbackTypeOption(3L, "return", "退货出库", 30),
                fallbackTypeOption(4L, "transfer", "调拨出库", 40),
                fallbackTypeOption(5L, "loss", "报损出库", 50),
                fallbackTypeOption(6L, "donation", "捐赠出库", 60),
                fallbackTypeOption(7L, "scrap", "报废出库", 70),
                fallbackTypeOption(8L, "other", "其他出库", 80)
        );
    }

    private OutboundTypeOptionVO fallbackTypeOption(Long id, String code, String name, Integer sortOrder) {
        OutboundTypeOptionVO option = new OutboundTypeOptionVO();
        option.setTypeId(id);
        option.setTypeCode(code);
        option.setTypeName(name);
        option.setStatus("active");
        option.setSortOrder(sortOrder);
        option.setTypeSource("fallback");
        option.setSourceRequirementText("");
        option.setRequiresSourceBiz(false);
        option.setApprovalMode("direct");
        option.setSupportsAiSuggestion(false);
        return option;
    }

    private void applyRuleMetadata(OutboundTypeOptionVO option, String dictValue) {
        String[] parts = StrUtil.blankToDefault(dictValue, "optional|none|false|direct|false").split("\\|");
        option.setRequiresSourceBiz(parts.length > 2 && Boolean.parseBoolean(parts[2]));
        option.setApprovalMode(parts.length > 3 && StrUtil.isNotBlank(parts[3]) ? parts[3] : "direct");
        option.setSupportsAiSuggestion(parts.length > 4 && Boolean.parseBoolean(parts[4]));
    }

    private String resolveOutboundStatusLabel(String status) {
        if (StrUtil.isBlank(status)) {
            return "";
        }
        return switch (status) {
            case "draft" -> "草稿";
            case "pending" -> "待审核";
            case "approved" -> "已审核";
            case "completed" -> "已出库";
            case "rejected" -> "已驳回";
            default -> status;
        };
    }

    private String resolveOutboundTypeLabel(String outboundType) {
        OutboundTypeOptionVO option = getOutboundTypeOption(outboundType);
        if (option != null && StrUtil.isNotBlank(option.getTypeName())) {
            return option.getTypeName();
        }
        return StrUtil.blankToDefault(outboundType, "");
    }

    private OutboundTypeOptionVO getOutboundTypeOption(String outboundType) {
        if (StrUtil.isBlank(outboundType)) {
            return null;
        }
        return typeOptions().stream()
                .filter(option -> outboundType.equals(option.getTypeCode()))
                .findFirst()
                .orElse(null);
    }

    private boolean isTypeActive(OutboundTypeOptionVO option) {
        return option != null && "active".equalsIgnoreCase(StrUtil.blankToDefault(option.getStatus(), "active"));
    }

    private void assertOutboundTypeEnabled(String outboundType) {
        OutboundTypeOptionVO option = getOutboundTypeOption(outboundType);
        if (option == null) {
            throw BizException.validationFailed("不支持的出库类型: " + outboundType);
        }
        if (!isTypeActive(option)) {
            throw BizException.validationFailed("出库类型已禁用: " + outboundType);
        }
    }

    private Long queryCount(String sql, List<Object> args) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    private BigDecimal queryAmount(String sql, List<Object> args) {
        BigDecimal amount = jdbcTemplate.queryForObject(sql, BigDecimal.class, args.toArray());
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private void refreshOrderStatusScope(OutboundOrder order) {
        refreshOrderStatusScope(order, List.of());
    }

    private void refreshOrderStatusScope(OutboundOrder order, List<OutboundOrderItem> extraItems) {
        List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOutboundId, order.getId()));
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

    private void refreshStatusRanges(OutboundOrder order, List<OutboundOrderItem> items, Set<String> refreshedRanges) {
        Map<Long, List<OutboundOrderAllocation>> allocationMap = order.getId() == null
                ? Map.of()
                : loadAllocationEntityMap(order.getId());
        for (OutboundOrderItem item : items) {
            List<OutboundOrderAllocation> allocations = allocationMap.getOrDefault(item.getId(), List.of());
            if (!allocations.isEmpty()) {
                for (OutboundOrderAllocation allocation : allocations) {
                    String key = allocation.getWarehouseId() + "_" + allocation.getLocationId();
                    if (refreshedRanges.add(key)) {
                        warehouseStatusRefreshService.refreshLocationAndWarehouse(allocation.getWarehouseId(), allocation.getLocationId());
                    }
                }
                continue;
            }
            Long warehouseId = item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId();
            Long locationId = item.getLocationId();
            String key = warehouseId + "_" + locationId;
            if (refreshedRanges.add(key)) {
                warehouseStatusRefreshService.refreshLocationAndWarehouse(warehouseId, locationId);
            }
        }
    }

    private void saveItems(Long outboundId, List<PersistedOrderItemInput> itemInputs, Long orgId, Long tenantId) {
        for (PersistedOrderItemInput input : itemInputs) {
            OutboundOrderItem item = new OutboundOrderItem();
            item.setOutboundId(outboundId);
            item.setMaterialId(input.materialId());
            item.setMaterialName(input.materialName());
            item.setSpec(input.spec());
            item.setUnit(input.unit());
            item.setWarehouseId(input.effectiveWarehouseId());
            item.setLocationId(input.effectiveLocationId());
            item.setQuantity(input.quantity());
            item.setUnitCost(input.unitCost());
            item.setBatchNo(input.effectiveBatchNo());
            item.setInventoryId(input.representativeInventoryId());
            item.setExpiryDate(input.effectiveExpiryDate());
            item.setPurpose(input.purpose());
            item.setRemark(input.remark());
            if (item.getUnitCost() != null && item.getQuantity() != null) {
                item.setTotalCost(item.getUnitCost().multiply(item.getQuantity()));
            }
            outboundOrderItemMapper.insert(item);
            saveAllocations(outboundId, item.getId(), input.allocations(), orgId, tenantId);
        }
    }

    private void saveAllocations(Long outboundId,
                                 Long outboundItemId,
                                 List<PersistedAllocationInput> allocations,
                                 Long orgId,
                                 Long tenantId) {
        if (allocations == null || allocations.isEmpty()) {
            return;
        }
        for (PersistedAllocationInput allocationInput : allocations) {
            OutboundOrderAllocation allocation = new OutboundOrderAllocation();
            allocation.setOutboundId(outboundId);
            allocation.setOutboundItemId(outboundItemId);
            allocation.setSourceStockDetailId(allocationInput.sourceStockDetailId());
            allocation.setWarehouseId(allocationInput.warehouseId());
            allocation.setLocationId(allocationInput.locationId());
            allocation.setBatchNo(allocationInput.batchNo());
            allocation.setProductionDate(allocationInput.productionDate());
            allocation.setExpiryDate(allocationInput.expiryDate());
            allocation.setQuantity(allocationInput.quantity());
            allocation.setSourceType(StrUtil.blankToDefault(allocationInput.sourceType(), "manual"));
            allocation.setOrgId(orgId);
            allocation.setTenantId(tenantId);
            outboundOrderAllocationMapper.insert(allocation);
        }
    }

    private void recalcTotals(OutboundOrder order) {
        List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOutboundId, order.getId()));

        BigDecimal totalAmount = items.stream()
                .filter(i -> i.getTotalCost() != null)
                .map(OutboundOrderItem::getTotalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(totalAmount);
        outboundOrderMapper.updateById(order);
    }

    private void validateStocktakeUnlocked(OutboundOrder order, String actionName) {
        List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOutboundId, order.getId()));
        if (items.isEmpty()) {
            throw BizException.validationFailed("出库单明细不能为空");
        }
        Map<Long, List<OutboundOrderAllocation>> allocationMap = loadAllocationEntityMap(order.getId());
        Set<String> validatedRanges = new HashSet<>();
        for (OutboundOrderItem item : items) {
            List<OutboundOrderAllocation> allocations = allocationMap.getOrDefault(item.getId(), List.of());
            if (!allocations.isEmpty()) {
                for (OutboundOrderAllocation allocation : allocations) {
                    String rangeKey = allocation.getWarehouseId() + "_" + allocation.getLocationId();
                    if (validatedRanges.add(rangeKey)) {
                        stocktakeLockService.validateUnlocked(allocation.getWarehouseId(), allocation.getLocationId(), actionName);
                    }
                }
                continue;
            }
            Long warehouseId = item.getWarehouseId() != null ? item.getWarehouseId() : order.getWarehouseId();
            if (warehouseId == null) {
                throw BizException.validationFailed("出库单明细缺少仓库信息，无法校验盘点锁");
            }
            Long locationId = item.getLocationId();
            String rangeKey = warehouseId + "_" + locationId;
            if (validatedRanges.add(rangeKey)) {
                stocktakeLockService.validateUnlocked(warehouseId, locationId, actionName);
            }
        }
    }

    private void validateAggregatedBatchStock(OutboundOrder order) {
        List<OutboundOrderItem> items = outboundOrderItemMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderItem>()
                        .eq(OutboundOrderItem::getOutboundId, order.getId()));
        if (items.isEmpty()) {
            return;
        }
        Map<Long, List<OutboundOrderAllocation>> allocationMap = loadAllocationEntityMap(order.getId());
        List<BatchStockDemand> manualDemands = new ArrayList<>();
        Map<Long, InventoryDemandContext> allocationDemands = new LinkedHashMap<>();
        for (OutboundOrderItem item : items) {
            List<OutboundOrderAllocation> allocations = allocationMap.getOrDefault(item.getId(), List.of());
            if (allocations.isEmpty()) {
                manualDemands.add(new BatchStockDemand(
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getMaterialId(),
                        item.getBatchNo(),
                        item.getMaterialName(),
                        item.getQuantity()
                ));
                continue;
            }
            for (OutboundOrderAllocation allocation : allocations) {
                InventoryDemandContext context = allocationDemands.computeIfAbsent(
                        allocation.getSourceStockDetailId(),
                        key -> new InventoryDemandContext(
                                allocation.getSourceStockDetailId(),
                                item.getMaterialName(),
                                item.getMaterialId(),
                                item.getSpec(),
                                allocation.getWarehouseId(),
                                allocation.getLocationId(),
                                allocation.getBatchNo(),
                                BigDecimal.ZERO
                        )
                );
                context.addQuantity(allocation.getQuantity());
            }
        }
        if (!manualDemands.isEmpty()) {
            validateAggregatedBatchStock(manualDemands);
        }
        if (!allocationDemands.isEmpty()) {
            validateAllocationInventoryDemands(new ArrayList<>(allocationDemands.values()), order.getOrgId(), order.getTenantId());
        }
    }

    private void validatePersistedOrderItems(List<PersistedOrderItemInput> items,
                                             Long headerWarehouseId,
                                             Long orgId,
                                             Long tenantId) {
        List<BatchStockDemand> manualDemands = new ArrayList<>();
        Map<Long, InventoryDemandContext> allocationDemands = new LinkedHashMap<>();
        for (PersistedOrderItemInput item : items) {
            validatePersistedItemRequiredFields(item);
            if (item.allocations().isEmpty()) {
                if (item.effectiveWarehouseId() == null) {
                    throw BizException.validationFailed("请先选择仓库后再保存");
                }
                if (StrUtil.isBlank(item.batchNo())) {
                    throw BizException.validationFailed("请先选择批次号后再保存");
                }
                manualDemands.add(new BatchStockDemand(
                        item.effectiveWarehouseId() != null ? item.effectiveWarehouseId() : headerWarehouseId,
                        item.effectiveLocationId(),
                        item.materialId(),
                        item.batchNo(),
                        item.materialName(),
                        item.quantity()
                ));
                continue;
            }
            BigDecimal allocationTotal = item.allocations().stream()
                    .map(PersistedAllocationInput::quantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocationTotal.compareTo(item.quantity()) != 0) {
                throw BizException.validationFailed(item.materialName() + " 的分配总量与出库数量不一致，请重新生成建议");
            }
            for (PersistedAllocationInput allocation : item.allocations()) {
                allocationDemands.computeIfAbsent(
                        allocation.sourceStockDetailId(),
                        key -> new InventoryDemandContext(
                                allocation.sourceStockDetailId(),
                                item.materialName(),
                                item.materialId(),
                                item.spec(),
                                allocation.warehouseId(),
                                allocation.locationId(),
                                allocation.batchNo(),
                                BigDecimal.ZERO
                        )
                ).addQuantity(allocation.quantity());
            }
        }
        if (!manualDemands.isEmpty()) {
            validateAggregatedBatchStock(manualDemands);
        }
        if (!allocationDemands.isEmpty()) {
            validateAllocationInventoryDemands(new ArrayList<>(allocationDemands.values()), orgId, tenantId);
        }
    }

    private void validateAggregatedBatchStock(List<BatchStockDemand> items) {
        Map<BatchStockKey, BigDecimal> demandByKey = new LinkedHashMap<>();
        Map<BatchStockKey, String> materialNameByKey = new LinkedHashMap<>();
        for (BatchStockDemand item : items) {
            BatchStockKey key = new BatchStockKey(
                    item.warehouseId(),
                    item.locationId(),
                    item.materialId(),
                    item.batchNo()
            );
            demandByKey.merge(key, defaultAmount(item.quantity()), BigDecimal::add);
            materialNameByKey.putIfAbsent(key, item.materialName());
        }
        for (Map.Entry<BatchStockKey, BigDecimal> entry : demandByKey.entrySet()) {
            BatchStockKey key = entry.getKey();
            BigDecimal demand = entry.getValue();
            List<Inventory> inventories = findInventories(
                    key.warehouseId(),
                    key.locationId(),
                    key.materialId(),
                    key.batchNo(),
                    false
            );
            BigDecimal totalQuantity = inventories.stream()
                    .map(Inventory::getQuantity)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalQuantity.compareTo(demand) < 0) {
                throw BizException.validationFailed(
                        "库存不足: " + materialNameByKey.get(key)
                                + "，当前库存 " + totalQuantity.stripTrailingZeros().toPlainString()
                                + "，出库数量 " + demand.stripTrailingZeros().toPlainString()
                );
            }
        }
    }

    private void validateAllocationInventoryDemands(List<InventoryDemandContext> demands, Long orgId, Long tenantId) {
        Map<Long, Inventory> inventoryMap = loadInventoryMap(demands.stream()
                .map(InventoryDemandContext::inventoryId)
                .collect(Collectors.toSet()));
        for (InventoryDemandContext demand : demands) {
            Inventory inventory = inventoryMap.get(demand.inventoryId());
            if (inventory == null) {
                throw BizException.validationFailed(demand.materialName() + " 的建议库存已不存在，请重新生成建议");
            }
            if (!Objects.equals(inventory.getOrgId(), orgId) || !Objects.equals(inventory.getTenantId(), tenantId)) {
                throw BizException.forbidden("存在越权库存分配，无法保存");
            }
            if (!Objects.equals(inventory.getMaterialId(), demand.materialId())
                    || !StrUtil.equals(StrUtil.blankToDefault(inventory.getSpec(), ""), StrUtil.blankToDefault(demand.spec(), ""))) {
                throw BizException.validationFailed(demand.materialName() + " 的建议库存与当前物料/规格不一致，请重新生成建议");
            }
            if ("locked".equalsIgnoreCase(StrUtil.blankToDefault(inventory.getStatus(), ""))) {
                throw BizException.validationFailed(demand.materialName() + " 的建议库存已冻结，请重新生成建议");
            }
            if (isExpiredInventory(inventory)) {
                throw BizException.validationFailed(demand.materialName() + " 的建议库存已过期，请重新生成建议");
            }
            if (demand.warehouseId() != null && !Objects.equals(demand.warehouseId(), inventory.getWarehouseId())) {
                throw BizException.validationFailed(demand.materialName() + " 的建议仓库已变化，请重新生成建议");
            }
            if (demand.locationId() != null && !Objects.equals(demand.locationId(), inventory.getLocationId())) {
                throw BizException.validationFailed(demand.materialName() + " 的建议仓位已变化，请重新生成建议");
            }
            if (StrUtil.isNotBlank(demand.batchNo()) && !StrUtil.equals(demand.batchNo(), inventory.getBatchNo())) {
                throw BizException.validationFailed(demand.materialName() + " 的建议批次已变化，请重新生成建议");
            }
            BigDecimal available = defaultAmount(inventory.getQuantity());
            if (available.compareTo(demand.quantity()) < 0) {
                throw BizException.validationFailed(
                        demand.materialName() + " 的建议库存不足，当前库存 "
                                + available.stripTrailingZeros().toPlainString()
                                + "，建议数量 " + demand.quantity().stripTrailingZeros().toPlainString()
                );
            }
        }
    }

    private Map<Long, Inventory> loadInventoryMap(Set<Long> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return Map.of();
        }
        List<Inventory> inventories = inventoryMapper.selectBatchIds(inventoryIds);
        if (inventories == null || inventories.isEmpty()) {
            return Map.of();
        }
        return inventories.stream().collect(Collectors.toMap(Inventory::getId, inventory -> inventory));
    }

    private boolean isExpiredInventory(Inventory inventory) {
        if (inventory == null) {
            return false;
        }
        if ("expired".equalsIgnoreCase(StrUtil.blankToDefault(inventory.getStatus(), ""))) {
            return true;
        }
        return inventory.getExpiryDate() != null && inventory.getExpiryDate().isBefore(LocalDate.now());
    }

    private List<PersistedOrderItemInput> buildCreateItemInputs(List<OutboundOrderCreateDTO.OutboundOrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<PersistedOrderItemInput> result = new ArrayList<>();
        for (OutboundOrderCreateDTO.OutboundOrderItemDTO item : items) {
            result.add(buildPersistedOrderItemInput(
                    item.getMaterialId(),
                    item.getMaterialName(),
                    item.getSpec(),
                    item.getUnit(),
                    item.getWarehouseId(),
                    item.getLocationId(),
                    item.getBatchNo(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    item.getExpiryDate(),
                    item.getPurpose(),
                    item.getRemark(),
                    buildCreateAllocations(item.getAllocations())
            ));
        }
        return result;
    }

    private List<PersistedOrderItemInput> buildUpdateItemInputs(List<OutboundOrderUpdateDTO.OutboundOrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<PersistedOrderItemInput> result = new ArrayList<>();
        for (OutboundOrderUpdateDTO.OutboundOrderItemDTO item : items) {
            result.add(buildPersistedOrderItemInput(
                    item.getMaterialId(),
                    item.getMaterialName(),
                    item.getSpec(),
                    item.getUnit(),
                    item.getWarehouseId(),
                    item.getLocationId(),
                    item.getBatchNo(),
                    item.getQuantity(),
                    item.getUnitCost(),
                    item.getExpiryDate(),
                    item.getPurpose(),
                    StrUtil.blankToDefault(item.getRemark(), item.getLineRemark()),
                    buildUpdateAllocations(item.getAllocations())
            ));
        }
        return result;
    }

    private PersistedOrderItemInput buildPersistedOrderItemInput(Long materialId,
                                                                 String materialName,
                                                                 String spec,
                                                                 String unit,
                                                                 Long warehouseId,
                                                                 Long locationId,
                                                                 String batchNo,
                                                                 BigDecimal quantity,
                                                                 BigDecimal unitCost,
                                                                 java.time.LocalDate expiryDate,
                                                                 String purpose,
                                                                 String remark,
                                                                 List<PersistedAllocationInput> allocations) {
        List<PersistedAllocationInput> safeAllocations = allocations == null ? List.of() : allocations;
        Long effectiveWarehouseId = warehouseId;
        Long effectiveLocationId = locationId;
        String effectiveBatchNo = batchNo;
        Long representativeInventoryId = null;
        LocalDate effectiveExpiryDate = expiryDate;
        BigDecimal resolvedUnitCost = unitCost;
        if (!safeAllocations.isEmpty()) {
            PersistedAllocationInput first = safeAllocations.getFirst();
            effectiveWarehouseId = warehouseId != null ? warehouseId : first.warehouseId();
            effectiveLocationId = locationId != null ? locationId : first.locationId();
            representativeInventoryId = safeAllocations.size() == 1 ? first.sourceStockDetailId() : null;
            if (StrUtil.isBlank(effectiveBatchNo)) {
                effectiveBatchNo = safeAllocations.size() == 1 ? first.batchNo() : "多批次(" + safeAllocations.size() + ")";
            }
            if (effectiveExpiryDate == null) {
                effectiveExpiryDate = first.expiryDate();
            }
            if (resolvedUnitCost == null) {
                resolvedUnitCost = resolveUnitCostFromInventory(first.sourceStockDetailId());
            }
        }
        return new PersistedOrderItemInput(
                materialId,
                materialName,
                StrUtil.blankToDefault(spec, ""),
                unit,
                warehouseId,
                locationId,
                batchNo,
                quantity,
                resolvedUnitCost,
                purpose,
                remark,
                safeAllocations,
                effectiveWarehouseId,
                effectiveLocationId,
                effectiveBatchNo,
                representativeInventoryId,
                effectiveExpiryDate
        );
    }

    private BigDecimal resolveUnitCostFromInventory(Long inventoryId) {
        if (inventoryId == null) {
            return null;
        }
        Inventory inventory = inventoryMapper.selectById(inventoryId);
        return inventory != null ? inventory.getUnitCost() : null;
    }

    private List<PersistedAllocationInput> buildCreateAllocations(List<OutboundOrderCreateDTO.OutboundOrderAllocationDTO> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        return allocations.stream()
                .map(item -> new PersistedAllocationInput(
                        item.getSourceStockDetailId(),
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getBatchNo(),
                        item.getProductionDate(),
                        item.getExpiryDate(),
                        item.getQuantity(),
                        item.getSourceType()
                ))
                .toList();
    }

    private List<PersistedAllocationInput> buildUpdateAllocations(List<OutboundOrderUpdateDTO.OutboundOrderAllocationDTO> allocations) {
        if (allocations == null || allocations.isEmpty()) {
            return List.of();
        }
        return allocations.stream()
                .map(item -> new PersistedAllocationInput(
                        item.getSourceStockDetailId(),
                        item.getWarehouseId(),
                        item.getLocationId(),
                        item.getBatchNo(),
                        item.getProductionDate(),
                        item.getExpiryDate(),
                        item.getQuantity(),
                        item.getSourceType()
                ))
                .toList();
    }

    private void validatePersistedItemRequiredFields(PersistedOrderItemInput item) {
        if (item.materialId() == null || StrUtil.isBlank(item.materialName())) {
            throw BizException.validationFailed("请先选择物料后再保存");
        }
        if (StrUtil.isBlank(item.unit())) {
            throw BizException.validationFailed(item.materialName() + " 缺少单位，无法保存");
        }
        if (item.quantity() == null || item.quantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.validationFailed(item.materialName() + " 的出库数量必须大于0");
        }
        if (StrUtil.isBlank(item.purpose())) {
            throw BizException.validationFailed(item.materialName() + " 的用途不能为空");
        }
    }

    private void applyOrderHeaderWarehouse(OutboundOrder order, List<PersistedOrderItemInput> itemInputs) {
        if (itemInputs == null || itemInputs.isEmpty()) {
            return;
        }
        for (PersistedOrderItemInput item : itemInputs) {
            if (item.effectiveWarehouseId() != null) {
                order.setWarehouseId(item.effectiveWarehouseId());
                return;
            }
        }
        throw BizException.validationFailed("至少需要一个有效仓库才能保存出库单");
    }

    private Map<Long, List<OutboundOrderAllocation>> loadAllocationEntityMap(Long outboundId) {
        if (outboundId == null) {
            return Map.of();
        }
        List<OutboundOrderAllocation> allocations = outboundOrderAllocationMapper.selectList(
                new LambdaQueryWrapper<OutboundOrderAllocation>()
                        .eq(OutboundOrderAllocation::getOutboundId, outboundId)
                        .orderByAsc(OutboundOrderAllocation::getOutboundItemId)
                        .orderByAsc(OutboundOrderAllocation::getId));
        if (allocations == null || allocations.isEmpty()) {
            return Map.of();
        }
        return allocations.stream().collect(Collectors.groupingBy(OutboundOrderAllocation::getOutboundItemId, LinkedHashMap::new, Collectors.toList()));
    }

    private void deductInventoriesByItem(OutboundOrderItem item) {
        List<Inventory> inventories = resolveInventories(item, false);
        if (inventories.isEmpty()) {
            throw BizException.notFound("库存记录不存在: " + item.getMaterialName());
        }
        BigDecimal totalQuantity = inventories.stream()
                .map(Inventory::getQuantity)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalQuantity.compareTo(item.getQuantity()) < 0) {
            throw BizException.validationFailed("库存不足: " + item.getMaterialName() + "，当前库存 " + totalQuantity.stripTrailingZeros().toPlainString() + "，出库数量 " + item.getQuantity().stripTrailingZeros().toPlainString());
        }
        BigDecimal remaining = item.getQuantity();
        for (Inventory inventory : inventories) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = defaultAmount(inventory.getQuantity());
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal deducted = available.min(remaining);
            BigDecimal newQty = available.subtract(deducted);
            inventory.setQuantity(newQty);
            inventory.setTotalCost(newQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : inventory.getUnitCost().multiply(newQty));
            inventoryMapper.updateById(inventory);
            remaining = remaining.subtract(deducted);
            if (item.getInventoryId() == null) {
                item.setInventoryId(inventory.getId());
            }
        }
        if (item.getInventoryId() != null) {
            outboundOrderItemMapper.updateById(item);
        }
    }

    private void deductInventoriesByAllocations(OutboundOrderItem item, List<OutboundOrderAllocation> allocations) {
        Map<Long, BigDecimal> demandByInventoryId = allocations.stream()
                .collect(Collectors.groupingBy(
                        OutboundOrderAllocation::getSourceStockDetailId,
                        LinkedHashMap::new,
                        Collectors.mapping(OutboundOrderAllocation::getQuantity, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        Map<Long, Inventory> inventoryMap = loadInventoryMap(demandByInventoryId.keySet());
        for (Map.Entry<Long, BigDecimal> entry : demandByInventoryId.entrySet()) {
            Inventory inventory = inventoryMap.get(entry.getKey());
            if (inventory == null) {
                throw BizException.notFound("库存记录不存在: " + item.getMaterialName());
            }
            BigDecimal demand = entry.getValue();
            BigDecimal available = defaultAmount(inventory.getQuantity());
            if (available.compareTo(demand) < 0) {
                throw BizException.validationFailed(item.getMaterialName() + " 的建议库存不足，请重新生成建议");
            }
            BigDecimal newQty = available.subtract(demand);
            inventory.setQuantity(newQty);
            inventory.setTotalCost(newQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : inventory.getUnitCost().multiply(newQty));
            inventoryMapper.updateById(inventory);
        }
    }

    private void restoreInventoriesByItem(OutboundOrderItem item) {
        List<Inventory> inventories = resolveInventories(item, true);
        if (inventories.isEmpty()) {
            throw BizException.notFound("库存记录不存在: " + item.getMaterialName());
        }
        BigDecimal remaining = item.getQuantity();
        for (Inventory inventory : inventories) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal restored = remaining;
            BigDecimal restoredQty = defaultAmount(inventory.getQuantity()).add(restored);
            inventory.setQuantity(restoredQty);
            inventory.setTotalCost(restoredQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : inventory.getUnitCost().multiply(restoredQty));
            inventoryMapper.updateById(inventory);
            remaining = remaining.subtract(restored);
        }
    }

    private void restoreInventoriesByAllocations(OutboundOrderItem item, List<OutboundOrderAllocation> allocations) {
        Map<Long, BigDecimal> demandByInventoryId = allocations.stream()
                .collect(Collectors.groupingBy(
                        OutboundOrderAllocation::getSourceStockDetailId,
                        LinkedHashMap::new,
                        Collectors.mapping(OutboundOrderAllocation::getQuantity, Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))
                ));
        Map<Long, Inventory> inventoryMap = loadInventoryMap(demandByInventoryId.keySet());
        for (Map.Entry<Long, BigDecimal> entry : demandByInventoryId.entrySet()) {
            Inventory inventory = inventoryMap.get(entry.getKey());
            if (inventory == null) {
                throw BizException.notFound("库存记录不存在: " + item.getMaterialName());
            }
            BigDecimal restoredQty = defaultAmount(inventory.getQuantity()).add(entry.getValue());
            inventory.setQuantity(restoredQty);
            inventory.setTotalCost(restoredQty.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : inventory.getUnitCost().multiply(restoredQty));
            inventoryMapper.updateById(inventory);
        }
    }

    private BigDecimal defaultAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private List<Inventory> resolveInventories(OutboundOrderItem item, boolean includeZeroQuantity) {
        return findInventories(item.getWarehouseId(), item.getLocationId(), item.getMaterialId(), item.getBatchNo(), includeZeroQuantity);
    }

    private Inventory resolveInventory(OutboundOrderItem item) {
        return findInventory(item.getWarehouseId(), item.getLocationId(), item.getMaterialId(), item.getBatchNo());
    }

    private List<Inventory> findInventories(Long warehouseId, Long locationId, Long materialId, String batchNo, boolean includeZeroQuantity) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseId, warehouseId)
                .eq(Inventory::getMaterialId, materialId);
        if (locationId != null) {
            wrapper.eq(Inventory::getLocationId, locationId);
        }
        if (StrUtil.isNotBlank(batchNo)) {
            wrapper.eq(Inventory::getBatchNo, batchNo);
        }
        if (includeZeroQuantity) {
            wrapper.ge(Inventory::getQuantity, BigDecimal.ZERO);
        } else {
            wrapper.gt(Inventory::getQuantity, BigDecimal.ZERO);
        }
        wrapper.orderByAsc(Inventory::getExpiryDate).orderByAsc(Inventory::getId);
        List<Inventory> rows = inventoryMapper.selectList(wrapper);
        return rows == null ? List.of() : rows;
    }

    private Inventory findInventory(Long warehouseId, Long locationId, Long materialId, String batchNo) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getWarehouseId, warehouseId)
                .eq(Inventory::getMaterialId, materialId);
        if (locationId != null) {
            wrapper.eq(Inventory::getLocationId, locationId);
        }
        if (StrUtil.isNotBlank(batchNo)) {
            wrapper.eq(Inventory::getBatchNo, batchNo);
        }
        wrapper.gt(Inventory::getQuantity, BigDecimal.ZERO);
        wrapper.orderByAsc(Inventory::getExpiryDate);
        wrapper.last("LIMIT 1");
        return inventoryMapper.selectOne(wrapper);
    }

    private Long resolveSuggestionOrgId(Long orderId) {
        if (orderId != null) {
            return getOrderById(orderId, true).getOrgId();
        }
        Long orgId = UserContext.getOrgId();
        if (orgId == null) {
            throw BizException.badRequest("当前用户缺少组织信息");
        }
        return orgId;
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    private void validateSuggestionAccess(Long orgId) {
        ensureOrgAllowed(orgId);
        ensureAnyPermission("无权生成出库建议", "outbound:create", "outbound:edit");
    }

    private OutboundSuggestionPreviewVO buildSuggestionPreview(OutboundSuggestionPreviewDTO dto, Long orgId, Long tenantId) {
        OutboundSuggestionPreviewVO result = new OutboundSuggestionPreviewVO();
        result.setSuccess(true);
        result.setGenerateTime(LocalDateTime.now());
        result.setRuleVersion(OUTBOUND_SUGGEST_RULE_VERSION);
        result.getWarnings().add("建议结果未锁定库存，保存或提交时将再次校验库存");

        OutboundSuggestionPreviewVO.SummaryVO summary = new OutboundSuggestionPreviewVO.SummaryVO();
        summary.setDetailCount(dto.getDetails().size());
        summary.setFullMatchedCount(0);
        summary.setPartialMatchedCount(0);
        summary.setFailedCount(0);
        result.setSummary(summary);

        Map<Long, BigDecimal> workingAvailableQty = new HashMap<>();
        Set<String> duplicateKeys = resolveDuplicateSuggestionKeys(dto.getDetails());
        if (!duplicateKeys.isEmpty()) {
            result.getWarnings().add("当前单据中存在相同物料规格的多行明细，系统已按行顺序生成建议");
        }

        for (OutboundSuggestionPreviewDTO.DetailDTO detail : dto.getDetails()) {
            OutboundSuggestionPreviewVO.DetailVO detailVO = buildSuggestionDetail(
                    detail,
                    orgId,
                    tenantId,
                    workingAvailableQty,
                    duplicateKeys.contains(buildSuggestionDuplicateKey(detail))
            );
            result.getDetails().add(detailVO);
            switch (StrUtil.blankToDefault(detailVO.getSuggestStatus(), "")) {
                case "full_matched" -> summary.setFullMatchedCount(summary.getFullMatchedCount() + 1);
                case "partial_matched" -> summary.setPartialMatchedCount(summary.getPartialMatchedCount() + 1);
                default -> summary.setFailedCount(summary.getFailedCount() + 1);
            }
        }
        return result;
    }

    private OutboundSuggestionPreviewVO.DetailVO buildSuggestionDetail(OutboundSuggestionPreviewDTO.DetailDTO detail,
                                                                       Long orgId,
                                                                       Long tenantId,
                                                                       Map<Long, BigDecimal> workingAvailableQty,
                                                                       boolean duplicatedLine) {
        OutboundSuggestionPreviewVO.DetailVO detailVO = new OutboundSuggestionPreviewVO.DetailVO();
        detailVO.setDetailId(detail.getDetailId());
        detailVO.setLineNo(detail.getLineNo());
        detailVO.setMaterialId(detail.getMaterialId());
        detailVO.setMaterialName(detail.getMaterialName());
        detailVO.setSpecName(StrUtil.blankToDefault(detail.getSpecName(), ""));
        detailVO.setRequestQty(detail.getRequestQty());
        detailVO.setMatchedQty(BigDecimal.ZERO);
        detailVO.setUnmatchedQty(detail.getRequestQty());

        if (detail.getMaterialId() == null) {
            detailVO.setSuggestStatus("invalid");
            detailVO.setMessage("请先选择物料后再生成建议");
            return detailVO;
        }
        if (StrUtil.isBlank(detail.getSpecName())) {
            detailVO.setSuggestStatus("invalid");
            detailVO.setMessage("请先选择规格后再生成建议");
            return detailVO;
        }
        if (detail.getRequestQty() == null || detail.getRequestQty().compareTo(BigDecimal.ZERO) <= 0) {
            detailVO.setSuggestStatus("invalid");
            detailVO.setMessage("出库数量必须大于0");
            return detailVO;
        }

        Material material = materialMapper.selectById(detail.getMaterialId());
        if (material == null || material.getDeleted() != 0 || !"active".equalsIgnoreCase(StrUtil.blankToDefault(material.getStatus(), ""))) {
            detailVO.setSuggestStatus("invalid");
            detailVO.setMessage("所选物料不存在或已停用");
            return detailVO;
        }
        if (duplicatedLine) {
            detailVO.getWarnings().add("存在相同物料多行明细，系统已按行顺序生成建议");
        }

        List<OutboundSuggestionStockCandidateVO> rawCandidates = inventoryMapper.selectOutboundSuggestionCandidates(
                detail.getMaterialId(),
                normalizeSpec(detail.getSpecName()),
                detail.getFixedWarehouseId(),
                detail.getFixedLocationId(),
                orgId,
                tenantId
        );
        boolean shelfLifeManaged = material.getShelfLifeDays() != null && material.getShelfLifeDays() > 0;
        List<OutboundSuggestionStockCandidateVO> candidates = rawCandidates.stream()
                .filter(this::isUsableSuggestionCandidate)
                .sorted(buildSuggestionCandidateComparator(shelfLifeManaged))
                .toList();

        if (candidates.isEmpty()) {
            detailVO.setSuggestStatus("no_stock");
            detailVO.setMessage(resolveNoCandidateMessage(rawCandidates));
            return detailVO;
        }

        BigDecimal remaining = detail.getRequestQty();
        int suggestionIndex = 0;
        for (OutboundSuggestionStockCandidateVO candidate : candidates) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal currentAvailable = workingAvailableQty.getOrDefault(candidate.getInventoryId(), defaultAmount(candidate.getQuantity()));
            if (currentAvailable.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal allocated = currentAvailable.min(remaining);
            if (allocated.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            suggestionIndex++;
            OutboundSuggestionPreviewVO.SuggestionVO suggestionVO = new OutboundSuggestionPreviewVO.SuggestionVO();
            suggestionVO.setSourceStockDetailId(candidate.getInventoryId());
            suggestionVO.setWarehouseId(candidate.getWarehouseId());
            suggestionVO.setWarehouseName(candidate.getWarehouseName());
            suggestionVO.setLocationId(candidate.getLocationId());
            suggestionVO.setLocationName(candidate.getLocationName());
            suggestionVO.setBatchNo(candidate.getBatchNo());
            suggestionVO.setProductionDate(candidate.getProductionDate());
            suggestionVO.setExpiryDate(candidate.getExpiryDate());
            suggestionVO.setRemainingShelfLifeDays(candidate.getExpiryDate() == null ? null : java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), candidate.getExpiryDate()));
            suggestionVO.setAvailableQty(currentAvailable);
            suggestionVO.setSuggestQty(allocated);
            suggestionVO.setReason(buildSuggestionReason(shelfLifeManaged, suggestionIndex));
            detailVO.getSuggestions().add(suggestionVO);

            remaining = remaining.subtract(allocated);
            workingAvailableQty.put(candidate.getInventoryId(), currentAvailable.subtract(allocated));
        }

        detailVO.setMatchedQty(detail.getRequestQty().subtract(remaining));
        detailVO.setUnmatchedQty(remaining.max(BigDecimal.ZERO));
        if (remaining.compareTo(BigDecimal.ZERO) == 0) {
            detailVO.setSuggestStatus("full_matched");
            detailVO.setMessage("已按临期优先规则生成建议");
        } else if (detailVO.getMatchedQty().compareTo(BigDecimal.ZERO) > 0) {
            detailVO.setSuggestStatus("partial_matched");
            detailVO.setMessage("库存不足，系统仅生成部分建议");
        } else {
            detailVO.setSuggestStatus("no_stock");
            detailVO.setMessage("当前无可用库存，无法生成出库建议");
        }
        if (!shelfLifeManaged) {
            detailVO.getWarnings().add("该物料非保质期管理，已按批次/FIFO规则生成建议");
        }
        long missingExpiryCount = candidates.stream().filter(candidate -> candidate.getExpiryDate() == null).count();
        if (shelfLifeManaged && missingExpiryCount > 0) {
            detailVO.getWarnings().add("存在无到期日库存，已自动排到建议末尾");
        }
        if (detailVO.getSuggestions().size() > SUGGEST_SPLIT_WARNING_LIMIT) {
            detailVO.getWarnings().add("建议拆分批次数较多，请人工确认");
        }
        log.info("出库建议计算: lineNo={}, materialId={}, requestQty={}, matchedQty={}, status={}, candidateCount={}",
                detail.getLineNo(), detail.getMaterialId(), detail.getRequestQty(), detailVO.getMatchedQty(),
                detailVO.getSuggestStatus(), candidates.size());
        return detailVO;
    }

    private OutboundSuggestionRevalidateVO buildSuggestionRevalidate(OutboundSuggestionRevalidateDTO dto, Long orgId, Long tenantId) {
        OutboundSuggestionRevalidateVO result = new OutboundSuggestionRevalidateVO();
        result.setValid(true);
        result.setResult("valid");

        Map<Long, Inventory> inventoryMap = new HashMap<>();
        Map<Long, BigDecimal> demandByInventoryId = new LinkedHashMap<>();
        Map<Long, List<OutboundSuggestionRevalidateVO.DetailVO>> detailRefsByInventoryId = new LinkedHashMap<>();

        for (OutboundSuggestionRevalidateDTO.DetailDTO detail : dto.getDetails()) {
            OutboundSuggestionRevalidateVO.DetailVO detailVO = new OutboundSuggestionRevalidateVO.DetailVO();
            detailVO.setDetailId(detail.getDetailId());
            detailVO.setLineNo(detail.getLineNo());
            detailVO.setValid(true);
            detailVO.setResult("valid");
            detailVO.setMessage("建议仍然有效");

            BigDecimal allocationTotal = detail.getAllocations().stream()
                    .map(OutboundSuggestionRevalidateDTO.AllocationDTO::getSuggestQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (allocationTotal.compareTo(detail.getRequestQty()) != 0) {
                detailVO.setValid(false);
                detailVO.setResult("expired");
                detailVO.setMessage("分配总量与出库数量不一致，请重新生成建议");
            }

            for (OutboundSuggestionRevalidateDTO.AllocationDTO allocation : detail.getAllocations()) {
                demandByInventoryId.merge(allocation.getSourceStockDetailId(), allocation.getSuggestQty(), BigDecimal::add);
                detailRefsByInventoryId.computeIfAbsent(allocation.getSourceStockDetailId(), key -> new ArrayList<>()).add(detailVO);
            }
            result.getDetails().add(detailVO);
        }

        inventoryMap.putAll(loadInventoryMap(demandByInventoryId.keySet()));
        for (OutboundSuggestionRevalidateDTO.DetailDTO detail : dto.getDetails()) {
            OutboundSuggestionRevalidateVO.DetailVO detailVO = result.getDetails().stream()
                    .filter(item -> Objects.equals(item.getDetailId(), detail.getDetailId()) && Objects.equals(item.getLineNo(), detail.getLineNo()))
                    .findFirst()
                    .orElse(null);
            if (detailVO == null || !detailVO.isValid()) {
                continue;
            }
            for (OutboundSuggestionRevalidateDTO.AllocationDTO allocation : detail.getAllocations()) {
                Inventory inventory = inventoryMap.get(allocation.getSourceStockDetailId());
                if (inventory == null) {
                    invalidateSuggestionDetail(detailVO, "expired", "当前库存已变化，建议结果失效，请重新生成出库建议");
                    break;
                }
                if (!Objects.equals(inventory.getOrgId(), orgId) || !Objects.equals(inventory.getTenantId(), tenantId)) {
                    invalidateSuggestionDetail(detailVO, "expired", "存在越权库存分配，请重新生成建议");
                    break;
                }
                if (detail.getMaterialId() != null && !Objects.equals(inventory.getMaterialId(), detail.getMaterialId())) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议批次对应物料已变化，请重新生成建议");
                    break;
                }
                if (StrUtil.isNotBlank(detail.getSpecName()) && !StrUtil.equals(normalizeSpec(detail.getSpecName()), normalizeSpec(inventory.getSpec()))) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议批次对应规格已变化，请重新生成建议");
                    break;
                }
                if (allocation.getWarehouseId() != null && !Objects.equals(allocation.getWarehouseId(), inventory.getWarehouseId())) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议仓库已变化，请重新生成建议");
                    break;
                }
                if (allocation.getLocationId() != null && !Objects.equals(allocation.getLocationId(), inventory.getLocationId())) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议仓位已变化，请重新生成建议");
                    break;
                }
                if (StrUtil.isNotBlank(allocation.getBatchNo()) && !StrUtil.equals(allocation.getBatchNo(), inventory.getBatchNo())) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议批次已变化，请重新生成建议");
                    break;
                }
                if ("locked".equalsIgnoreCase(StrUtil.blankToDefault(inventory.getStatus(), "")) || isExpiredInventory(inventory)) {
                    invalidateSuggestionDetail(detailVO, "stock_status_changed", "建议批次状态已变化，请重新生成建议");
                    break;
                }
            }
        }

        for (Map.Entry<Long, BigDecimal> entry : demandByInventoryId.entrySet()) {
            Inventory inventory = inventoryMap.get(entry.getKey());
            if (inventory == null) {
                continue;
            }
            if (defaultAmount(inventory.getQuantity()).compareTo(entry.getValue()) < 0) {
                for (OutboundSuggestionRevalidateVO.DetailVO detailVO : detailRefsByInventoryId.getOrDefault(entry.getKey(), List.of())) {
                    invalidateSuggestionDetail(detailVO, "insufficient_stock", "当前库存已变化，建议结果失效，请重新生成出库建议");
                }
            }
        }

        OutboundSuggestionRevalidateVO.DetailVO failedDetail = result.getDetails().stream().filter(item -> !item.isValid()).findFirst().orElse(null);
        if (failedDetail != null) {
            result.setValid(false);
            result.setResult(failedDetail.getResult());
            result.getWarnings().add(failedDetail.getMessage());
        }
        return result;
    }

    private void invalidateSuggestionDetail(OutboundSuggestionRevalidateVO.DetailVO detailVO, String result, String message) {
        detailVO.setValid(false);
        detailVO.setResult(result);
        detailVO.setMessage(message);
    }

    private Set<String> resolveDuplicateSuggestionKeys(List<OutboundSuggestionPreviewDTO.DetailDTO> details) {
        Set<String> duplicates = new LinkedHashSet<>();
        Set<String> seen = new HashSet<>();
        for (OutboundSuggestionPreviewDTO.DetailDTO detail : details) {
            String key = buildSuggestionDuplicateKey(detail);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        return duplicates;
    }

    private String buildSuggestionDuplicateKey(OutboundSuggestionPreviewDTO.DetailDTO detail) {
        return detail.getMaterialId() + "::" + normalizeSpec(detail.getSpecName());
    }

    private String normalizeSpec(String spec) {
        return StrUtil.blankToDefault(spec, "");
    }

    private boolean isUsableSuggestionCandidate(OutboundSuggestionStockCandidateVO candidate) {
        if (candidate == null) {
            return false;
        }
        if ("locked".equalsIgnoreCase(StrUtil.blankToDefault(candidate.getInventoryStatus(), ""))) {
            return false;
        }
        return candidate.getExpiryDate() == null || !candidate.getExpiryDate().isBefore(LocalDate.now());
    }

    private Comparator<OutboundSuggestionStockCandidateVO> buildSuggestionCandidateComparator(boolean shelfLifeManaged) {
        Comparator<OutboundSuggestionStockCandidateVO> comparator;
        if (shelfLifeManaged) {
            comparator = Comparator.comparing(
                    OutboundSuggestionStockCandidateVO::getExpiryDate,
                    Comparator.nullsLast(Comparator.naturalOrder())
            );
        } else {
            comparator = Comparator.comparing(candidate -> 0);
        }
        return comparator
                .thenComparing(OutboundSuggestionStockCandidateVO::getInboundTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(candidate -> StrUtil.blankToDefault(candidate.getBatchNo(), ""))
                .thenComparing(candidate -> StrUtil.blankToDefault(candidate.getWarehouseName(), ""))
                .thenComparing(candidate -> StrUtil.blankToDefault(candidate.getLocationName(), ""))
                .thenComparing(OutboundSuggestionStockCandidateVO::getInventoryId);
    }

    private String resolveNoCandidateMessage(List<OutboundSuggestionStockCandidateVO> rawCandidates) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return "当前无可用库存，无法生成出库建议";
        }
        boolean allLocked = rawCandidates.stream()
                .allMatch(candidate -> "locked".equalsIgnoreCase(StrUtil.blankToDefault(candidate.getInventoryStatus(), "")));
        if (allLocked) {
            return "当前库存处于冻结状态，无法生成建议";
        }
        boolean allExpired = rawCandidates.stream()
                .allMatch(candidate -> candidate.getExpiryDate() != null && candidate.getExpiryDate().isBefore(LocalDate.now()));
        if (allExpired) {
            return "当前库存均已过期，无法生成建议";
        }
        return "当前无可用库存，无法生成出库建议";
    }

    private String buildSuggestionReason(boolean shelfLifeManaged, int suggestionIndex) {
        if (suggestionIndex == 1) {
            return shelfLifeManaged ? "剩余保质期更短，优先出库" : "按FIFO/批次优先规则建议";
        }
        return "上一批次不足，继续按批次优先补足";
    }

    private void recordSuggestionPreviewAudit(OutboundSuggestionPreviewDTO dto,
                                              OutboundSuggestionPreviewVO result,
                                              Long orgId,
                                              Long tenantId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("request", dto);
        snapshot.put("orgId", orgId);
        snapshot.put("tenantId", tenantId);
        snapshot.put("result", result);
        auditLogService.log(
                AuditModule.WMS_OUTBOUND_ORDER,
                AuditOperationType.VIEW,
                dto.getOrderId(),
                dto.getOrderId() == null ? null : String.valueOf(dto.getOrderId()),
                "生成出库建议预览",
                null,
                JSONUtil.toJsonStr(snapshot)
        );
    }

    private String buildOutboundAuditSnapshot(Long outboundId) {
        if (outboundId == null) {
            return null;
        }
        OutboundOrderVO detail = getDetail(outboundId);
        return JSONUtil.toJsonStr(detail);
    }

    private void recordOrderAudit(AuditOperationType operationType,
                                  Long targetId,
                                  String targetNo,
                                  String beforeData,
                                  String afterData,
                                  String desc) {
        auditLogService.log(
                AuditModule.WMS_OUTBOUND_ORDER,
                operationType,
                targetId,
                targetNo,
                desc,
                beforeData,
                afterData
        );
    }

    private String generateOutboundNo() {
        String dateStr = LocalDate.now().format(DATE_FMT);
        String prefix = "CK-" + dateStr;

        // 查询当天最大单号
        String maxNo = outboundOrderMapper.getMaxOutboundNo(prefix);
        int seq = 1;
        if (maxNo != null && maxNo.startsWith(prefix)) {
            try {
                seq = Integer.parseInt(maxNo.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                seq = 1;
            }
        }
        return String.format("%s%05d", prefix, seq);
    }

    private static final class ImportGroup {
        private final String groupNo;
        private final List<ImportRow> rows = new ArrayList<>();
        private boolean failed;

        private ImportGroup(String groupNo) {
            this.groupNo = groupNo;
        }

        private List<ImportRow> rows() {
            return rows;
        }

        private boolean failed() {
            return failed;
        }

        private void failed(boolean failed) {
            this.failed = failed;
        }
    }

    private record BatchStockKey(Long warehouseId, Long locationId, Long materialId, String batchNo) {
    }

    private record BatchStockDemand(Long warehouseId,
                                    Long locationId,
                                    Long materialId,
                                    String batchNo,
                                    String materialName,
                                    BigDecimal quantity) {
    }

    private record ImportRow(
            Integer rowNumber,
            String groupNo,
            String outboundNo,
            String outboundType,
            Warehouse warehouse,
            Material material,
            String batchNo,
            BigDecimal quantity,
            String remark,
            OutboundOrder existingOrder,
            OutboundImportError error
    ) {
        private static ImportRow error(Integer rowNumber, String field, String reason) {
            return new ImportRow(rowNumber, null, null, null, null, null, null, null, null, null, new OutboundImportError(rowNumber, field, reason, "BUSINESS_VALIDATION", null));
        }
    }

    private record OutboundImportError(Integer rowNumber, String field, String reason, String exceptionType, String taskNo) {
        private com.xykj.wms.dto.OutboundImportResultErrorDTO toDto() {
            com.xykj.wms.dto.OutboundImportResultErrorDTO dto = new com.xykj.wms.dto.OutboundImportResultErrorDTO();
            dto.setRowNumber(rowNumber);
            dto.setField(field);
            dto.setReason(reason);
            dto.setExceptionType(exceptionType);
            dto.setTaskNo(taskNo);
            return dto;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void uploadAttachments(Long id, MultipartFile[] files) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("出库单不存在");
        }
        if (files == null || files.length == 0) {
            throw BizException.badRequest("请选择要上传的附件");
        }

        List<String> attachments = new ArrayList<>(order.getAttachments() == null ? Collections.emptyList() : order.getAttachments());
        for (MultipartFile file : files) {
            validateAttachment(file);
            String url = fileStorageService.upload(file, "outbound");
            attachments.add(url);
        }

        order.setAttachments(attachments);
        outboundOrderMapper.updateById(order);
        log.info("上传出库单附件: id={}, count={}", id, files.length);
    }

    @Override
    public void downloadAttachment(Long id, String url, HttpServletResponse response) {
        OutboundOrder order = outboundOrderMapper.selectById(id);
        if (order == null) {
            throw BizException.notFound("出库单不存在");
        }
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
            log.error("下载出库附件失败: id={}, url={}", id, url, e);
            throw new BizException("附件下载失败，请稍后重试");
        }
    }

    private File resolveImportErrorFile(String fileName) {
        return new File(System.getProperty("java.io.tmpdir"), fileName);
    }

    private String generateImportErrorFile(String taskNo, List<OutboundImportError> errors) {
        String fileName = OUTBOUND_IMPORT_ERROR_FILE_PREFIX + taskNo + "-errors.xlsx";
        File file = resolveImportErrorFile(fileName);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("导入错误");
            Row header = sheet.createRow(0);
            String[] headers = {"行号", "错误字段", "错误原因", "异常类型", "批量任务单号"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int index = 1;
            for (OutboundImportError error : errors) {
                Row data = sheet.createRow(index++);
                data.createCell(0).setCellValue(error.rowNumber() == null ? 0 : error.rowNumber());
                data.createCell(1).setCellValue(StrUtil.blankToDefault(error.field(), ""));
                data.createCell(2).setCellValue(StrUtil.blankToDefault(error.reason(), ""));
                data.createCell(3).setCellValue(StrUtil.blankToDefault(error.exceptionType(), ""));
                data.createCell(4).setCellValue(StrUtil.blankToDefault(error.taskNo(), taskNo));
            }
            try (java.io.OutputStream outputStream = Files.newOutputStream(file.toPath())) {
                workbook.write(outputStream);
            }
            return fileName;
        } catch (IOException ex) {
            throw BizException.badRequest("生成出库导入错误文件失败");
        }
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

        AttachmentCategory category = resolveAttachmentCategory(normalizedSuffix, contentType);
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

    private AttachmentCategory resolveAttachmentCategory(String suffix, String contentType) {
        if (contentType.startsWith("image/") || Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp").contains(suffix)) {
            return AttachmentCategory.IMAGE;
        }
        if (contentType.startsWith("video/") || Set.of(".mp4", ".mov").contains(suffix)) {
            return AttachmentCategory.VIDEO;
        }
        return AttachmentCategory.DOCUMENT;
    }

    private record PersistedOrderItemInput(Long materialId,
                                           String materialName,
                                           String spec,
                                           String unit,
                                           Long warehouseId,
                                           Long locationId,
                                           String batchNo,
                                           BigDecimal quantity,
                                           BigDecimal unitCost,
                                           String purpose,
                                           String remark,
                                           List<PersistedAllocationInput> allocations,
                                           Long effectiveWarehouseId,
                                           Long effectiveLocationId,
                                           String effectiveBatchNo,
                                           Long representativeInventoryId,
                                           LocalDate effectiveExpiryDate) {
    }

    private record PersistedAllocationInput(Long sourceStockDetailId,
                                            Long warehouseId,
                                            Long locationId,
                                            String batchNo,
                                            LocalDate productionDate,
                                            LocalDate expiryDate,
                                            BigDecimal quantity,
                                            String sourceType) {
    }

    private static final class InventoryDemandContext {
        private final Long inventoryId;
        private final String materialName;
        private final Long materialId;
        private final String spec;
        private final Long warehouseId;
        private final Long locationId;
        private final String batchNo;
        private BigDecimal quantity;

        private InventoryDemandContext(Long inventoryId,
                                       String materialName,
                                       Long materialId,
                                       String spec,
                                       Long warehouseId,
                                       Long locationId,
                                       String batchNo,
                                       BigDecimal quantity) {
            this.inventoryId = inventoryId;
            this.materialName = materialName;
            this.materialId = materialId;
            this.spec = spec;
            this.warehouseId = warehouseId;
            this.locationId = locationId;
            this.batchNo = batchNo;
            this.quantity = quantity;
        }

        private Long inventoryId() {
            return inventoryId;
        }

        private String materialName() {
            return materialName;
        }

        private Long materialId() {
            return materialId;
        }

        private String spec() {
            return spec;
        }

        private Long warehouseId() {
            return warehouseId;
        }

        private Long locationId() {
            return locationId;
        }

        private String batchNo() {
            return batchNo;
        }

        private BigDecimal quantity() {
            return quantity;
        }

        private void addQuantity(BigDecimal delta) {
            this.quantity = this.quantity.add(defaultAmountStatic(delta));
        }
    }

    private static BigDecimal defaultAmountStatic(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private enum AttachmentCategory {
        IMAGE,
        VIDEO,
        DOCUMENT
    }

    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
    }
}
