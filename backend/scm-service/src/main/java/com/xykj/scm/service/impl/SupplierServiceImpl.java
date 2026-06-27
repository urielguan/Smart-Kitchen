package com.xykj.scm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.scm.dto.SupplierAuditDTO;
import com.xykj.scm.dto.SupplierCancelDTO;
import com.xykj.scm.dto.SupplierCreateDTO;
import com.xykj.scm.dto.SupplierDisableDTO;
import com.xykj.scm.dto.SupplierImportDTO;
import com.xykj.scm.dto.SupplierImportFailureDTO;
import com.xykj.scm.dto.SupplierImportResultDTO;
import com.xykj.scm.dto.SupplierQualificationFileDTO;
import com.xykj.scm.dto.SupplierQueryDTO;
import com.xykj.scm.dto.SupplierUpdateDTO;
import com.xykj.scm.entity.Supplier;
import com.xykj.scm.mapper.SupplierMapper;
import com.xykj.scm.service.SupplierAiScoreService;
import com.xykj.scm.service.SupplierService;
import com.xykj.scm.support.SupplierQualificationStatusSupport;
import com.xykj.scm.vo.SupplierDuplicateCheckVO;
import com.xykj.scm.vo.SupplierImportValidationConflictVO;
import com.xykj.scm.vo.SupplierImportValidationVO;
import com.xykj.scm.vo.SupplierQualificationFileVO;
import com.xykj.scm.vo.SupplierStatisticsVO;
import com.xykj.scm.vo.SupplierVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 供应商服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private static final Long DEFAULT_ORG_ID = 1L;
    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_AUDIT_USER_ID = 1L;
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_DISABLED = "disabled";
    private static final String STATUS_CANCELLED = "cancelled";
    private static final String DELETE_BLOCK_MESSAGE = "该供应商已存在业务数据，为保证数据完整性与追溯性，不允许删除";
    private static final String CONCURRENT_RECHECK_BLOCK_MESSAGE = "当前供应商已被新增业务单据引用，暂不允许执行停用/注销/删除，请稍后重试或核查关联单据";
    private static final String QUALIFICATION_FILE_DIR = "scm/suppliers";
    private static final String IMPORT_ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/supplier-import-errors/";
    private static final long MAX_IMPORT_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_IMPORT_ROWS = 5000;
    private static final long MAX_QUALIFICATION_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> ALLOWED_QUALIFICATION_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx");
    private static final Set<String> ALLOWED_IMPORT_EXTENSIONS = Set.of("xls", "xlsx");
    private static final Set<String> ALLOWED_IMPORT_STATUSES = Set.of(STATUS_DRAFT, STATUS_PENDING);
    private static final String DUPLICATE_SUPPLIER_CODE_FIELD = "供应商编码";
    private static final String DUPLICATE_SUPPLIER_CODE_MESSAGE = "当前租户下已存在相同供应商编码，请修改后重新保存";
    private static final String DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE = "当前租户下已存在相同供应商编码，不可重复导入";
    private static final String DUPLICATE_SUPPLIER_NAME_FIELD = "供应商名称";
    private static final String DUPLICATE_SUPPLIER_NAME_MESSAGE = "当前租户下已存在相同供应商名称，请修改后重新保存";
    private static final String DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE = "当前租户下已存在相同供应商名称，不可重复导入";
    private static final String DUPLICATE_UNIFIED_CREDIT_CODE_FIELD = "统一社会信用代码";
    private static final String DUPLICATE_UNIFIED_CREDIT_CODE_FIELD_MESSAGE = "该统一社会信用代码已存在，请勿重复录入";
    private static final String DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE = "该统一社会信用代码已存在，不可重复保存";
    private static final String DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE = "该统一社会信用代码已存在，不可重复导入";
    private static final String DUPLICATE_LICENSE_NO_FIELD = "营业执照编号";
    private static final String DUPLICATE_LICENSE_NO_FIELD_MESSAGE = "当前租户下存在相同营业执照编号的有效供应商，请修改后重试";
    private static final String DUPLICATE_LICENSE_NO_SAVE_MESSAGE = "当前租户下存在相同营业执照编号的有效供应商，请修改后重试";
    private static final String DUPLICATE_LICENSE_NO_IMPORT_MESSAGE = "当前租户下存在相同营业执照编号的有效供应商，不可重复导入";
    private static final String DUPLICATE_FOOD_LICENSE_NO_FIELD = "食品许可证号";
    private static final String DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE = "当前租户下存在相同食品许可证号的有效供应商，请修改后重试";
    private static final String DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE = "当前租户下存在相同食品许可证号的有效供应商，请修改后重试";
    private static final String DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE = "当前租户下存在相同食品许可证号的有效供应商，不可重复导入";
    private static final String IMPORT_REVERSE_CREDIT_CODE_BLOCK_MESSAGE = "统一社会信用代码已绑定其他供应商，不可通过信用代码反写更新供应商编码";
    private static final String IMPORT_CREATE_ORG_PERMISSION_BLOCK_MESSAGE = "该行供应商填写的所属组织不在你的数据权限范围内，无法新增";
    private static final String IMPORT_UPDATE_ORG_PERMISSION_BLOCK_MESSAGE = "该供应商编码归属组织不在你的数据权限范围内，不允许更新";
    private static final String IMPORT_ORG_CHANGE_BLOCK_MESSAGE = "不允许通过导入修改供应商所属组织";
    private static final String IMPORT_CODE_CREDIT_CONFLICT_BLOCK_MESSAGE = "供应商编码与统一社会信用代码匹配到不同供应商，不允许导入更新";
    private static final String IMPORT_FAILED_FIELD_CODE_AND_CREDIT = "统一社会信用代码,供应商编码";
    private static final String IMPORT_FAILED_FIELD_ORG = "所属组织编码";
    private static final String IMPORT_FAILED_FIELD_SUPPLIER_CODE_AND_CREDIT = "供应商编码,统一社会信用代码";
    private static final String IMPORT_PERMISSION_BLOCK_MESSAGE = "当前账号无供应商导入权限，请联系管理员后重试";
    private static final String IMPORT_OWNERSHIP_BLOCK_MESSAGE = "导入模板仅允许填写所属组织编码列，请勿新增租户ID、所属组织ID或归属组织列";
    private static final String IMPORT_FILE_SIZE_LIMIT_MESSAGE = "上传文件不能超过10MB";
    private static final String IMPORT_ROW_LIMIT_MESSAGE = "单次导入最多支持5000条供应商数据";
    private static final String IMPORT_BATCH_UPDATE_DUPLICATE_MESSAGE = "同一批导入中不允许重复更新同一供应商";
    private static final List<String> IMPORT_IGNORED_UPDATE_FIELDS = List.of("供应商编码", "所属组织编码", "创建人", "创建时间", "状态");
    private static final String DOCUMENT_TYPE_LICENSE = "营业执照";
    private static final String DOCUMENT_TYPE_FOOD_LICENSE = "食品许可证";
    private static final String IMPORT_SUPPLIER_TYPE_FIELD = "供应商类型";
    private static final String IMPORT_SUPPLIER_TYPE_INVALID_MESSAGE = "供应商类型不存在或未启用";
    private static final String DICT_TYPE_SUPPLIER_TYPE = "supplier_type";
    private static final String DICT_STATUS_ACTIVE = "active";
    private static final Set<String> FORBIDDEN_IMPORT_OWNERSHIP_HEADERS = Set.of(
            "tenantid",
            "租户id",
            "orgid",
            "组织id",
            "所属组织id",
            "归属组织"
    );
    private static final Pattern MOBILE_PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.\\w+$");
    private static final String[] IMPORT_TEMPLATE_HEADERS = {
            "供应商编码\n(必填，唯一)",
            "供应商名称\n(必填)",
            "联系人\n(必填)",
            "联系电话\n(必填，11位手机号)",
            "联系邮箱",
            "地址",
            "供应商类型",
            "社会信用代码\n(必填)",
            "银行账号\n(必填)",
            "开户行\n(必填)",
            "营业执照编号\n(必填)",
            "执照到期日\n(必填，yyyy-MM-dd)",
            "食品许可证号",
            "食品许可证到期日\n(yyyy-MM-dd)",
            "状态\n(可选：draft/pending)",
            "所属组织编码\n(必填)"
    };
    private static final int[] IMPORT_TEMPLATE_WIDTHS = {20, 24, 14, 18, 24, 32, 16, 22, 22, 24, 20, 18, 20, 18, 18, 16};
    private static final String[] EXPORT_HEADERS = {
            "供应商名称",
            "供应商编码",
            "状态",
            "供应商类型",
            "联系人",
            "联系电话",
            "联系邮箱",
            "地址",
            "社会信用代码",
            "开户行",
            "银行账号",
            "营业执照编号",
            "营业执照到期",
            "食品许可证号",
            "食品许可证到期",
            "资质文件",
            "信用评分",
            "资质完整性",
            "历史供货质量",
            "价格稳定性",
            "履约准时率",
            "禁用原因",
            "注销原因",
            "创建时间",
            "创建人",
            "更新时间",
            "修改人",
            "审核时间",
            "审核人",
            "审核备注"
    };
    private static final int[] EXPORT_COLUMN_WIDTHS = {22, 18, 12, 16, 14, 18, 24, 32, 22, 20, 22, 20, 18, 20, 18, 28, 12, 12, 12, 12, 12, 24, 24, 20, 14, 20, 14, 20, 14, 24};

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter EXPORT_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final SupplierMapper supplierMapper;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final FileStorageService fileStorageService;
    private final SupplierAiScoreService supplierAiScoreService;

    @Override
    @DataScope
    public PageResult<SupplierVO> list(SupplierQueryDTO query) {
        LambdaQueryWrapper<Supplier> wrapper = buildListWrapper(query);
        IPage<Supplier> page = supplierMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        List<SupplierVO> voList = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page, voList);
    }

    private LambdaQueryWrapper<Supplier> buildListWrapper(SupplierQueryDTO query) {
        String normalizedStatus = normalizeQueryStatus(query.getStatus());
        String normalizedKeyword = normalizeKeyword(query.getKeyword());
        String normalizedSupplierName = normalizeKeyword(query.getSupplierName());
        String normalizedSupplierCode = normalizeKeyword(query.getSupplierCode());

        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StrUtil.isNotBlank(normalizedKeyword), nested -> nested
                        .like(Supplier::getSupplierName, normalizedKeyword)
                        .or()
                        .like(Supplier::getSupplierCode, normalizedKeyword)
                        .or()
                        .like(Supplier::getUnifiedCreditCode, normalizedKeyword))
                .like(StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedSupplierName), Supplier::getSupplierName, normalizedSupplierName)
                .like(StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedSupplierCode), Supplier::getSupplierCode, normalizedSupplierCode)
                .eq(StrUtil.isNotBlank(normalizedStatus), Supplier::getStatus, normalizedStatus)
                .eq(query.getOrgId() != null, Supplier::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Supplier::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Supplier::getId)
                .orderByDesc(Supplier::getCreatedAt);
        return wrapper;
    }

    private String normalizeKeyword(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    @Override
    public SupplierVO getDetail(Long id) {
        Supplier supplier = getSupplierById(id);
        SupplierVO vo = toVO(supplier);
        supplierAiScoreService.enrichScoreMeta(vo);
        vo.setCreatedByName(resolveOperatorName(supplier.getCreatedBy()));
        vo.setUpdatedByName(resolveOperatorName(supplier.getUpdatedBy()));
        vo.setAuditByName(resolveOperatorName(supplier.getAuditBy()));
        return vo;
    }

    @Override
    public SupplierQualificationFileVO uploadQualificationFile(MultipartFile file) {
        validateQualificationFile(file);

        SupplierQualificationFileVO vo = new SupplierQualificationFileVO();
        vo.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        vo.setName(resolveAttachmentName(file));
        vo.setSize(formatFileSize(file.getSize()));
        vo.setUrl(fileStorageService.upload(file, QUALIFICATION_FILE_DIR));

        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.UPDATE,
                null,
                vo.getName(),
                "上传供应商资质文件：" + vo.getName(),
                null,
                toJson(vo)
        );
        return vo;
    }

    @Override
    public void deleteQualificationFile(String fileUrl, String fileName) {
        String normalizedUrl = normalizeRequiredReason(fileUrl, "文件地址不能为空");
        String normalizedFileName = StrUtil.blankToDefault(normalizeOptionalReason(fileName), normalizedUrl);

        fileStorageService.delete(normalizedUrl);

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("fileName", normalizedFileName);
        beforeData.put("fileUrl", normalizedUrl);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.DELETE,
                null,
                normalizedFileName,
                "删除供应商资质文件：" + normalizedFileName,
                toJson(beforeData),
                null
        );
    }

    @Override
    public void downloadQualificationFile(String fileUrl, String fileName, HttpServletResponse response) {
        String normalizedUrl = normalizeRequiredReason(fileUrl, "文件地址不能为空");
        String normalizedFileName = StrUtil.blankToDefault(normalizeOptionalReason(fileName), "attachment");

        writeAttachment(normalizedFileName, normalizedUrl, response);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("fileName", normalizedFileName);
        afterData.put("fileUrl", normalizedUrl);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.EXPORT,
                null,
                normalizedFileName,
                "下载供应商资质文件：" + normalizedFileName,
                null,
                toJson(afterData)
        );
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("供应商导入模板");

            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sampleStyle = createSampleStyle(workbook);

            int rowNum = 0;

            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(34);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】前两行红色数据为示例，导入时会自动跳过。系统按“供应商编码优先、统一社会信用代码二次校验”执行导入；编码命中仅更新白名单业务字段，不会变更供应商编码、所属组织编码、创建信息和状态。新增供应商时将按模板中的所属组织编码匹配组织并创建，且仅允许导入当前账号数据权限范围内的组织；若编码已存在，则所属组织编码仅用于权限校验，不允许通过导入修改供应商所属组织。供应商类型支持填写名称或编码，且必须为当前租户已启用的字典项。状态仅支持 draft 或 pending，留空时新增默认 pending。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(46);
            for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }

            String[][] sampleData = {
                    {"#SUP-001", "示例蔬菜供应商", "张三", "13800138000", "supplier@example.com", "北京市海淀区示例路1号", "蔬菜", "91110108MA00000001", "6222020202020202020", "中国银行北京支行", "LIC-001", "2027-12-31", "FOOD-001", "2027-12-31", "pending", "ORG001"},
                    {"#SUP-002", "示例粮油供应商", "李四", "13900139000", "", "上海市浦东新区示例路8号", "粮油", "91310000MA00000002", "6222020202020202021", "农业银行上海支行", "LIC-002", "2028-06-30", "", "", "draft", "ORG002"}
            };
            for (String[] rowValues : sampleData) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < rowValues.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(rowValues[i]);
                    cell.setCellStyle(sampleStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("供应商导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("下载供应商导入模板失败", e);
            throw BizException.badRequest("下载供应商导入模板失败");
        }
    }

    @Override
    public SupplierImportValidationVO validateImportFile(MultipartFile file) {
        DataScopeService.DataScopeResult importOrgScope = ensureImportOrgScope();
        List<SupplierImportDTO> importList = prepareImportRows(file);
        int effectiveRowCount = importList.size();
        boolean rowLimitExceeded = checkImportRowLimit(file, effectiveRowCount, false);
        List<SupplierImportValidationConflictVO> duplicateConflicts = rowLimitExceeded
                ? Collections.emptyList()
                : collectImportValidationConflicts(importList, importOrgScope);
        if (!rowLimitExceeded) {
            log.info(
                    "供应商导入预校验完成: fileName={}, fileSize={}, effectiveRowCount={}, duplicateConflictCount={}",
                    resolveAttachmentName(file),
                    formatFileSize(file.getSize()),
                    effectiveRowCount,
                    duplicateConflicts.size()
            );
        }
        return new SupplierImportValidationVO(effectiveRowCount, rowLimitExceeded, duplicateConflicts);
    }

    @Override
    public SupplierDuplicateCheckVO checkDuplicate(
            Long excludeId,
            String supplierCode,
            String supplierName,
            String licenseNo,
            String foodLicenseNo
    ) {
        String normalizedSupplierCode = normalizeOptionalReason(supplierCode);
        String normalizedSupplierName = normalizeOptionalReason(supplierName);
        String normalizedLicenseNo = normalizeCertificateNo(licenseNo);
        String normalizedFoodLicenseNo = normalizeCertificateNo(foodLicenseNo);
        Long tenantId = resolveCurrentTenantId();

        boolean supplierCodeDuplicate = hasDuplicateSupplierCode(normalizedSupplierCode, excludeId, tenantId);
        boolean supplierNameDuplicate = hasDuplicateSupplierName(normalizedSupplierName, excludeId, tenantId);
        boolean licenseNoDuplicate = hasDuplicateLicenseNo(normalizedLicenseNo, excludeId, tenantId);
        boolean foodLicenseNoDuplicate = hasDuplicateFoodLicenseNo(normalizedFoodLicenseNo, excludeId, tenantId);
        return new SupplierDuplicateCheckVO(
                supplierCodeDuplicate,
                supplierCodeDuplicate ? DUPLICATE_SUPPLIER_CODE_MESSAGE : null,
                supplierNameDuplicate,
                supplierNameDuplicate ? DUPLICATE_SUPPLIER_NAME_MESSAGE : null,
                licenseNoDuplicate,
                licenseNoDuplicate ? DUPLICATE_LICENSE_NO_SAVE_MESSAGE : null,
                foodLicenseNoDuplicate,
                foodLicenseNoDuplicate ? DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE : null
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SupplierImportResultDTO importSuppliers(MultipartFile file) {
        DataScopeService.DataScopeResult importOrgScope = ensureImportOrgScope();
        List<SupplierImportDTO> importList = prepareImportRows(file);
        checkImportRowLimit(file, importList.size(), true);
        SupplierImportResultDTO duplicateBlockedResult = buildDuplicateBlockedImportResult(importList);
        if (duplicateBlockedResult != null) {
            return duplicateBlockedResult;
        }

        ImportMatchContext matchContext = buildImportMatchContext(importList);
        Map<String, Long> orgCodeToIdMap = buildImportOrgCodeToIdMap(importList);
        Set<String> seenSupplierCodes = new LinkedHashSet<>();
        Set<String> seenSupplierNames = new LinkedHashSet<>();
        Set<String> seenUnifiedCreditCodes = new LinkedHashSet<>();
        Set<String> seenLicenseNos = new LinkedHashSet<>();
        Set<String> seenFoodLicenseNos = new LinkedHashSet<>();
        Set<Long> processedSupplierIds = new LinkedHashSet<>();
        List<SupplierImportDTO> errorRows = new ArrayList<>();
        List<SupplierImportFailureDTO> failures = new ArrayList<>();
        int successCount = 0;
        int failedRowCount = 0;

        for (SupplierImportDTO row : importList) {
            String basicErrorMessage = validateImportRowBasic(row, orgCodeToIdMap);
            if (basicErrorMessage != null) {
                appendImportFailure(row, errorRows, failures, basicErrorMessage);
                failedRowCount++;
                continue;
            }

            ImportMatchResult matchResult = resolveImportTarget(row, matchContext, importOrgScope);
            if (matchResult.getErrorMessage() != null) {
                appendImportFailure(row, errorRows, failures, matchResult.getErrorMessage());
                failedRowCount++;
                continue;
            }

            if (matchResult.getTarget() != null && processedSupplierIds.contains(matchResult.getTarget().getId())) {
                appendImportFailure(row, errorRows, failures, IMPORT_BATCH_UPDATE_DUPLICATE_MESSAGE);
                failedRowCount++;
                continue;
            }

            List<String> duplicateMessages = collectImportDuplicateMessages(
                    row,
                    matchResult,
                    seenSupplierCodes,
                    seenSupplierNames,
                    seenUnifiedCreditCodes,
                    seenLicenseNos,
                    seenFoodLicenseNos
            );
            if (!duplicateMessages.isEmpty()) {
                duplicateMessages.forEach(message -> appendImportFailure(row, errorRows, failures, message));
                failedRowCount++;
                continue;
            }

            String supplierTypeErrorMessage = validateAndNormalizeImportSupplierType(row);
            if (supplierTypeErrorMessage != null) {
                appendImportFailure(row, errorRows, failures, supplierTypeErrorMessage);
                failedRowCount++;
                continue;
            }

            try {
                if (matchResult == null || matchResult.getTarget() == null) {
                    createImportSupplier(row);
                } else {
                    importUpdateSupplier(matchResult.getTarget(), row);
                    processedSupplierIds.add(matchResult.getTarget().getId());
                }
                row.setSuccess(true);
                successCount++;
            } catch (Exception ex) {
                log.error("导入供应商失败，行号：{}，错误：{}", row.getRowNum(), ex.getMessage(), ex);
                appendImportFailure(row, errorRows, failures, resolveImportExceptionMessage(ex));
                failedRowCount++;
            }
        }

        String errorFileUrl = errorRows.isEmpty() ? null : generateImportErrorFile(errorRows);
        SupplierImportResultDTO result = new SupplierImportResultDTO(
                importList.size(),
                successCount,
                failedRowCount,
                failedRowCount > 0,
                errorFileUrl,
                failures
        );

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("total", result.getTotal());
        afterData.put("successCount", result.getSuccessCount());
        afterData.put("failCount", result.getFailCount());
        afterData.put("failures", failures);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.IMPORT,
                null,
                null,
                "导入供应商：共" + result.getTotal() + "条，成功" + result.getSuccessCount() + "条，失败" + result.getFailCount() + "条",
                null,
                toJson(afterData)
        );
        return result;
    }

    @Override
    @DataScope
    public void exportSuppliers(SupplierQueryDTO query, HttpServletResponse response) {
        String operationResult = "success";
        String errorMessage = null;
        int exportCount = 0;

        try (Workbook workbook = new XSSFWorkbook()) {
            LambdaQueryWrapper<Supplier> wrapper = buildListWrapper(query);
            List<Supplier> suppliers = supplierMapper.selectList(wrapper);
            exportCount = suppliers.size();

            Sheet sheet = workbook.createSheet("供应商信息");
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            Map<Long, String> operatorNameCache = new HashMap<>();

            int rowNum = 0;
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(28);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】当前导出结果已按页面筛选条件和数据权限范围处理。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, EXPORT_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(24);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EXPORT_COLUMN_WIDTHS[i] * 256);
            }

            for (Supplier supplier : suppliers) {
                SupplierVO vo = toVO(supplier);
                Row row = sheet.createRow(rowNum++);
                String[] values = {
                        blankToEmpty(vo.getSupplierName()),
                        blankToEmpty(vo.getSupplierCode()),
                        resolveSupplierStatusLabel(vo.getStatus()),
                        blankToEmpty(vo.getSupplierType()),
                        blankToEmpty(vo.getContactName()),
                        blankToEmpty(vo.getContactPhone()),
                        blankToEmpty(vo.getContactEmail()),
                        blankToEmpty(vo.getAddress()),
                        blankToEmpty(vo.getUnifiedCreditCode()),
                        blankToEmpty(vo.getBankName()),
                        blankToEmpty(vo.getBankAccount()),
                        blankToEmpty(vo.getLicenseNo()),
                        blankToEmpty(vo.getLicenseExpiresAt()),
                        blankToEmpty(vo.getFoodLicenseNo()),
                        blankToEmpty(vo.getFoodLicenseExpiresAt()),
                        joinQualificationFileNames(vo.getQualificationFiles()),
                        vo.getCreditScore() == null ? "" : vo.getCreditScore().stripTrailingZeros().toPlainString(),
                        vo.getScoreQualification() == null ? "" : vo.getScoreQualification().stripTrailingZeros().toPlainString(),
                        vo.getScoreQuality() == null ? "" : vo.getScoreQuality().stripTrailingZeros().toPlainString(),
                        vo.getScorePrice() == null ? "" : vo.getScorePrice().stripTrailingZeros().toPlainString(),
                        vo.getScoreDelivery() == null ? "" : vo.getScoreDelivery().stripTrailingZeros().toPlainString(),
                        blankToEmpty(vo.getDisableReason()),
                        blankToEmpty(vo.getCancelReason()),
                        blankToEmpty(vo.getCreatedAt()),
                        blankToEmpty(resolveOperatorName(supplier.getCreatedBy(), operatorNameCache)),
                        blankToEmpty(vo.getUpdatedAt()),
                        blankToEmpty(resolveOperatorName(supplier.getUpdatedBy(), operatorNameCache)),
                        blankToEmpty(vo.getAuditAt()),
                        blankToEmpty(resolveOperatorName(supplier.getAuditBy(), operatorNameCache)),
                        blankToEmpty(vo.getAuditRemark())
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = "供应商信息_" + LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER);
            response.setHeader(
                    "Content-Disposition",
                    "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20") + ".xlsx"
            );
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception ex) {
            operationResult = "failure";
            errorMessage = ex.getMessage();
            log.error("导出供应商失败", ex);
            throw BizException.badRequest("导出供应商失败");
        } finally {
            try {
                String desc = "success".equals(operationResult)
                        ? "导出供应商数据（" + exportCount + "条）"
                        : "导出供应商数据失败";
                auditLogService.log(
                        AuditModule.SCM_SUPPLIER,
                        AuditOperationType.EXPORT,
                        null,
                        null,
                        desc,
                        null,
                        null,
                        operationResult,
                        errorMessage
                );
            } catch (Exception logEx) {
                log.error("记录供应商导出审计日志失败", logEx);
            }
        }
    }

    @Override
    public void downloadImportErrorFile(String fileName, HttpServletResponse response) {
        try {
            File file = new File(IMPORT_ERROR_FILE_DIR + fileName);
            if (!file.exists()) {
                throw BizException.notFound("错误文件不存在或已过期");
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            try (FileInputStream fis = new FileInputStream(file)) {
                fis.transferTo(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("下载供应商导入错误文件失败", e);
            throw BizException.badRequest("下载供应商导入错误文件失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SupplierCreateDTO dto) {
        return createSupplier(dto, resolveCurrentOrgId());
    }

    private Long createSupplier(SupplierCreateDTO dto, Long orgId) {
        normalizeCreateIdentityFields(dto);
        dto.setLicenseNo(normalizeRequiredReason(dto.getLicenseNo(), "营业执照编号不能为空"));
        dto.setFoodLicenseNo(normalizeOptionalReason(dto.getFoodLicenseNo()));
        dto.setUnifiedCreditCode(normalizeUnifiedCreditCode(dto.getUnifiedCreditCode()));
        checkSupplierCodeUnique(dto.getSupplierCode(), null);
        checkSupplierNameUnique(dto.getSupplierName(), null);
        checkUnifiedCreditCodeUnique(dto.getUnifiedCreditCode(), null, DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE);
        checkLicenseNoUnique(dto.getLicenseNo(), resolveCurrentTenantId(), null, DUPLICATE_LICENSE_NO_SAVE_MESSAGE);
        checkFoodLicenseNoUnique(dto.getFoodLicenseNo(), resolveCurrentTenantId(), null, DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE);

        Supplier supplier = new Supplier();
        copyCreateFields(dto, supplier);
        if (StrUtil.isBlank(supplier.getStatus())) {
            supplier.setStatus(STATUS_DRAFT);
        }
        if (supplier.getCreditScore() == null) {
            supplier.setCreditScore(BigDecimal.valueOf(100));
        }
        String normalizedStatus = normalizeCreateStatus(supplier.getStatus(), dto.getDisableReason());
        supplier.setStatus(normalizedStatus);
        supplier.setDisableReason(STATUS_DISABLED.equals(normalizedStatus) ? normalizeOptionalReason(dto.getDisableReason()) : null);
        supplier.setOrgId(orgId);
        supplier.setTenantId(resolveCurrentTenantId());

        supplierMapper.insert(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(supplier.getId()));
        supplier = getSupplierById(supplier.getId());
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.CREATE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "新增供应商：" + supplier.getSupplierName(),
                null,
                toAuditJson(supplier)
        );
        log.info("新增供应商成功: id={}, code={}", supplier.getId(), supplier.getSupplierCode());
        return supplier.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SupplierUpdateDTO dto) {
        Supplier supplier = getSupplierById(id);
        ensureEditableSupplier(supplier);
        normalizeEditableIdentityFields(dto);
        normalizeEditableUniqueFields(dto);
        validateEditableUniqueFields(dto, supplier, id);
        String beforeData = toAuditJson(supplier);
        String oldQualificationFiles = supplier.getQualificationFiles();
        String normalizedStatus = normalizeUpdateStatus(supplier.getStatus(), dto.getStatus());

        applyEditableFields(dto, supplier, id);
        if (normalizedStatus != null) {
            supplier.setStatus(normalizedStatus);
            if (isResetAuditStatus(normalizedStatus)) {
                clearAuditFields(supplier);
            }
        }

        persistEditedSupplier(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(id));
        supplier = getSupplierById(id);
        deleteRemovedQualificationFiles(oldQualificationFiles, supplier.getQualificationFiles());
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.UPDATE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "编辑供应商：" + supplier.getSupplierName(),
                beforeData,
                toAuditJson(supplier)
        );
        log.info("编辑供应商成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, SupplierAuditDTO dto) {
        if (!STATUS_ACTIVE.equals(dto.getStatus()) && !STATUS_REJECTED.equals(dto.getStatus())) {
            throw BizException.badRequest("审核状态仅支持 active 或 rejected");
        }

        Supplier supplier = getSupplierById(id);
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("已注销供应商不可审核");
        }
        if (!STATUS_PENDING.equals(supplier.getStatus())) {
            throw BizException.badRequest("仅待审核状态供应商可审核");
        }
        String beforeData = toAuditJson(supplier);
        supplier.setStatus(dto.getStatus());
        supplier.setDisableReason(null);
        supplier.setAuditBy(resolveCurrentUserId());
        supplier.setAuditAt(LocalDateTime.now());
        supplier.setAuditRemark(StrUtil.isNotBlank(dto.getRemark())
                ? dto.getRemark().trim()
                : (STATUS_ACTIVE.equals(dto.getStatus()) ? "审核通过" : "审核驳回"));

        supplierMapper.updateById(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(id));
        supplier = getSupplierById(id);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.STATUS_CHANGE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "供应商审核状态变更为：" + dto.getStatus(),
                beforeData,
                toAuditJson(supplier)
        );
        log.info("审核供应商成功: id={}, status={}", id, dto.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id, SupplierDisableDTO dto) {
        Supplier supplier = getSupplierById(id);
        validateDisablePreconditions(supplier, id, false);
        normalizeEditableUniqueFields(dto);
        validateEditableUniqueFields(dto, supplier, id);

        String beforeData = toAuditJson(supplier);
        String oldQualificationFiles = supplier.getQualificationFiles();
        applyEditableFields(dto, supplier, id);
        performConcurrentRecheck(
                id,
                "停用",
                AuditOperationType.STATUS_CHANGE,
                latestSupplier -> validateDisablePreconditions(latestSupplier, id, true)
        );
        supplier.setStatus(STATUS_DISABLED);
        supplier.setDisableReason(normalizeRequiredReason(dto.getReason(), "禁用原因不能为空"));
        clearAuditFields(supplier);
        supplierMapper.updateById(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(id));
        supplier = getSupplierById(id);
        deleteRemovedQualificationFiles(oldQualificationFiles, supplier.getQualificationFiles());
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.STATUS_CHANGE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "供应商已禁用",
                beforeData,
                toAuditJson(supplier)
        );
        log.info("禁用供应商成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void enable(Long id) {
        Supplier supplier = getSupplierById(id);
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("已注销供应商不可启用");
        }
        if (!STATUS_DISABLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("仅禁用状态供应商可启用");
        }

        String beforeData = toAuditJson(supplier);
        supplier.setStatus(STATUS_ACTIVE);
        supplier.setDisableReason(null);
        supplier.setAuditBy(resolveCurrentUserId());
        supplier.setAuditAt(LocalDateTime.now());
        supplier.setAuditRemark("供应商重新启用");
        supplierMapper.updateById(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(id));
        supplier = getSupplierById(id);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.STATUS_CHANGE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "供应商已启用",
                beforeData,
                toAuditJson(supplier)
        );
        log.info("启用供应商成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, SupplierCancelDTO dto) {
        Supplier supplier = getSupplierById(id);
        validateCancelPreconditions(supplier, id, false);

        String beforeData = toAuditJson(supplier);
        performConcurrentRecheck(
                id,
                "注销",
                AuditOperationType.STATUS_CHANGE,
                latestSupplier -> validateCancelPreconditions(latestSupplier, id, true)
        );
        supplier.setStatus(STATUS_CANCELLED);
        supplier.setCancelReason(normalizeRequiredReason(dto.getReason(), "注销原因不能为空"));
        clearAuditFields(supplier);
        supplierMapper.updateById(supplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(id));
        supplier = getSupplierById(id);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.STATUS_CHANGE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "供应商已注销",
                beforeData,
                toAuditJson(supplier)
        );
        log.info("注销供应商成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier supplier = getSupplierById(id);
        validateDeletePreconditions(supplier, id, false);

        String beforeData = toAuditJson(supplier);
        List<String> qualificationFileUrls = extractQualificationFileUrls(supplier.getQualificationFiles());
        performConcurrentRecheck(
                id,
                "删除",
                AuditOperationType.DELETE,
                latestSupplier -> validateDeletePreconditions(latestSupplier, id, true)
        );
        supplierMapper.deleteById(id);
        deleteQualificationFiles(qualificationFileUrls);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.DELETE,
                supplier.getId(),
                supplier.getSupplierCode(),
                "删除供应商：" + supplier.getSupplierName(),
                beforeData,
                null
        );
        log.info("删除供应商成功: id={}", id);
    }

    @Override
    @DataScope
    public SupplierStatisticsVO getStatistics(SupplierQueryDTO query) {
        Set<Long> scopedOrgIds = resolveScopedOrgIds(query != null ? query.getOrgId() : null, query != null ? query.getOrgIds() : null);
        if (scopedOrgIds != null && scopedOrgIds.isEmpty()) {
            SupplierStatisticsVO empty = new SupplierStatisticsVO();
            empty.setTotal(0L);
            empty.setActiveCount(0L);
            empty.setPendingCount(0L);
            empty.setNearExpireCount(0L);
            return empty;
        }

        List<Long> orgIdList = scopedOrgIds == null ? null : List.copyOf(scopedOrgIds);
        SupplierStatisticsVO vo = new SupplierStatisticsVO();
        vo.setTotal(nullToZero(supplierMapper.countTotalByOrgIds(orgIdList)));
        vo.setActiveCount(nullToZero(supplierMapper.countByStatusAndOrgIds("active", orgIdList)));
        vo.setPendingCount(nullToZero(supplierMapper.countByStatusAndOrgIds("pending", orgIdList)));
        vo.setNearExpireCount(nullToZero(supplierMapper.countNearExpireByOrgIds(orgIdList)));
        return vo;
    }

    private CellStyle createTipStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createSampleStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private void validateImportUploadFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("导入文件不能为空");
        }
        String fileName = resolveAttachmentName(file);
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            log.warn("供应商导入文件大小超限: fileName={}, fileSize={}", fileName, formatFileSize(file.getSize()));
            throw BizException.badRequest(IMPORT_FILE_SIZE_LIMIT_MESSAGE);
        }
        String extension = StrUtil.subAfter(fileName, ".", true);
        if (StrUtil.isBlank(extension) || !ALLOWED_IMPORT_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw BizException.badRequest("仅支持 .xls 或 .xlsx 格式的 Excel 文件");
        }
    }

    private List<SupplierImportDTO> prepareImportRows(MultipartFile file) {
        validateImportUploadFile(file);
        ensureNoForbiddenImportOwnershipHeaders(file);
        List<SupplierImportDTO> rawList = readSupplierImportRows(file);
        if (rawList.isEmpty()) {
            throw BizException.badRequest("导入文件为空");
        }

        List<SupplierImportDTO> importList = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            SupplierImportDTO dto = rawList.get(i);
            dto.setRowNum(i + 3);
            dto.setRawSupplierCode(dto.getSupplierCode());
            dto.setRawUnifiedCreditCode(dto.getUnifiedCreditCode());
            dto.setRawLicenseNo(dto.getLicenseNo());
            dto.setRawSupplierType(dto.getSupplierType());
            dto.setRawFoodLicenseNo(dto.getFoodLicenseNo());
            dto.setRawOrgCode(dto.getOrgCode());
            if (isSampleImportRow(dto) || isEmptyImportRow(dto)) {
                continue;
            }
            importList.add(dto);
        }

        if (importList.isEmpty()) {
            throw BizException.badRequest("导入文件为空");
        }
        return importList;
    }

    private boolean checkImportRowLimit(MultipartFile file, int effectiveRowCount, boolean throwWhenExceeded) {
        if (effectiveRowCount <= MAX_IMPORT_ROWS) {
            return false;
        }
        log.warn(
                "供应商导入行数超限: fileName={}, fileSize={}, effectiveRowCount={}, maxRowCount={}",
                resolveAttachmentName(file),
                formatFileSize(file.getSize()),
                effectiveRowCount,
                MAX_IMPORT_ROWS
        );
        if (throwWhenExceeded) {
            throw BizException.badRequest(IMPORT_ROW_LIMIT_MESSAGE);
        }
        return true;
    }

    private DataScopeService.DataScopeResult ensureImportOrgScope() {
        if (UserContext.getTenantId() == null || UserContext.getUserId() == null) {
            throw BizException.badRequest(IMPORT_PERMISSION_BLOCK_MESSAGE);
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllAccess() && scope.getOrgIds().isEmpty()) {
            throw BizException.badRequest(IMPORT_PERMISSION_BLOCK_MESSAGE);
        }
        return scope;
    }

    private void ensureNoForbiddenImportOwnershipHeaders(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            if (workbook.getNumberOfSheets() <= 0) {
                return;
            }
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return;
            }
            Row headerRow = sheet.getRow(1);
            if (headerRow == null) {
                return;
            }

            DataFormatter formatter = new DataFormatter();
            short lastCellNum = headerRow.getLastCellNum();
            if (lastCellNum <= 0) {
                return;
            }

            for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
                Cell cell = headerRow.getCell(cellIndex);
                String headerKey = normalizeImportLookupKey(formatter.formatCellValue(cell));
                if (StrUtil.isBlank(headerKey)) {
                    continue;
                }
                boolean hasForbiddenOwnershipHeader = FORBIDDEN_IMPORT_OWNERSHIP_HEADERS.stream()
                        .anyMatch(headerKey::contains);
                if (hasForbiddenOwnershipHeader) {
                    throw BizException.badRequest(IMPORT_OWNERSHIP_BLOCK_MESSAGE);
                }
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("解析供应商导入表头失败", ex);
            throw BizException.badRequest("读取供应商导入文件失败");
        }
    }

    private List<SupplierImportDTO> readSupplierImportRows(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return EasyExcel.read(inputStream)
                    .autoCloseStream(Boolean.TRUE)
                    .head(SupplierImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();
        } catch (Exception e) {
            log.error("读取供应商导入文件失败: fileName={}", resolveAttachmentName(file), e);
            throw BizException.badRequest("读取供应商导入文件失败");
        }
    }

    private boolean isSampleImportRow(SupplierImportDTO dto) {
        return StrUtil.startWith(normalizeImportText(dto.getSupplierCode()), "#");
    }

    private boolean isEmptyImportRow(SupplierImportDTO dto) {
        return StrUtil.isAllBlank(
                normalizeImportText(dto.getSupplierCode()),
                normalizeImportText(dto.getSupplierName()),
                normalizeImportText(dto.getContactName()),
                normalizeImportText(dto.getContactPhone()),
                normalizeImportText(dto.getUnifiedCreditCode())
        );
    }

    private ImportMatchContext buildImportMatchContext(List<SupplierImportDTO> importList) {
        Set<String> supplierCodes = importList.stream()
                .map(SupplierImportDTO::getSupplierCode)
                .map(this::normalizeImportText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> unifiedCreditCodes = importList.stream()
                .map(SupplierImportDTO::getUnifiedCreditCode)
                .map(this::normalizeUnifiedCreditCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Supplier> candidates = findImportCandidates(supplierCodes, unifiedCreditCodes);
        Map<String, List<Supplier>> suppliersByCode = new HashMap<>();
        Map<String, List<Supplier>> suppliersByUnifiedCreditCode = new HashMap<>();
        for (Supplier supplier : candidates) {
            String supplierCode = normalizeImportText(supplier.getSupplierCode());
            if (StrUtil.isNotBlank(supplierCode)) {
                suppliersByCode.computeIfAbsent(supplierCode, key -> new ArrayList<>()).add(supplier);
            }
            String unifiedCreditCode = normalizeUnifiedCreditCode(supplier.getUnifiedCreditCode());
            if (StrUtil.isNotBlank(unifiedCreditCode)) {
                suppliersByUnifiedCreditCode.computeIfAbsent(unifiedCreditCode, key -> new ArrayList<>()).add(supplier);
            }
        }

        return new ImportMatchContext(suppliersByCode, suppliersByUnifiedCreditCode);
    }

    private List<Supplier> findImportCandidates(Set<String> supplierCodes, Set<String> unifiedCreditCodes) {
        if ((supplierCodes == null || supplierCodes.isEmpty()) && (unifiedCreditCodes == null || unifiedCreditCodes.isEmpty())) {
            return Collections.emptyList();
        }
        Map<Long, Supplier> candidates = new LinkedHashMap<>();
        if (supplierCodes != null && !supplierCodes.isEmpty()) {
            LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Supplier::getTenantId, resolveCurrentTenantId())
                    .in(Supplier::getSupplierCode, supplierCodes);
            supplierMapper.selectList(wrapper).forEach(supplier -> candidates.put(supplier.getId(), supplier));
        }
        if (unifiedCreditCodes != null && !unifiedCreditCodes.isEmpty()) {
            findSuppliersByNormalizedUnifiedCreditCodes(unifiedCreditCodes)
                    .forEach(supplier -> candidates.put(supplier.getId(), supplier));
        }
        return new ArrayList<>(candidates.values());
    }

    private Map<String, Long> buildImportOrgCodeToIdMap(List<SupplierImportDTO> importList) {
        if (importList == null || importList.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> orgCodes = importList.stream()
                .map(SupplierImportDTO::getOrgCode)
                .map(this::normalizeImportText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (orgCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> args = new ArrayList<>();
        args.add(resolveCurrentTenantId());
        args.addAll(orgCodes);
        String placeholders = String.join(",", Collections.nCopies(orgCodes.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, org_code FROM sys_organization " +
                        "WHERE tenant_id = ? AND deleted = 0 AND org_code IN (" + placeholders + ")",
                args.toArray()
        );

        Map<String, Long> orgCodeToIdMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String orgCode = normalizeImportText(Objects.toString(row.get("org_code"), null));
            Object orgIdValue = row.get("id");
            Long orgId = null;
            if (orgIdValue instanceof Number number) {
                orgId = number.longValue();
            } else if (orgIdValue != null) {
                orgId = Long.parseLong(String.valueOf(orgIdValue));
            }
            if (StrUtil.isNotBlank(orgCode) && orgId != null) {
                orgCodeToIdMap.put(orgCode, orgId);
            }
        }
        return orgCodeToIdMap;
    }

    private String validateImportRowBasic(SupplierImportDTO row, Map<String, Long> orgCodeToIdMap) {
        String supplierCode = normalizeImportText(row.getSupplierCode());
        String supplierName = normalizeImportText(row.getSupplierName());
        String contactName = normalizeImportText(row.getContactName());
        String contactPhone = normalizeImportText(row.getContactPhone());
        String contactEmail = normalizeImportText(row.getContactEmail());
        String address = normalizeImportText(row.getAddress());
        String supplierType = normalizeImportText(row.getSupplierType());
        String unifiedCreditCode = normalizeUnifiedCreditCode(row.getUnifiedCreditCode());
        String bankAccount = normalizeImportText(row.getBankAccount());
        String bankName = normalizeImportText(row.getBankName());
        String licenseNo = normalizeImportText(row.getLicenseNo());
        String licenseExpiresAt = normalizeImportText(row.getLicenseExpiresAt());
        String foodLicenseNo = normalizeImportText(row.getFoodLicenseNo());
        String foodLicenseExpiresAt = normalizeImportText(row.getFoodLicenseExpiresAt());
        String status = normalizeImportStatus(row.getStatus());
        String orgCode = normalizeImportText(row.getOrgCode());

        if (StrUtil.isBlank(supplierCode)) return "供应商编码不能为空";
        if (StrUtil.isBlank(supplierName)) return "供应商名称不能为空";
        if (StrUtil.isBlank(contactName)) return "联系人不能为空";
        if (StrUtil.isBlank(contactPhone)) return "联系电话不能为空";
        if (StrUtil.isBlank(unifiedCreditCode)) return "社会信用代码不能为空";
        if (StrUtil.isBlank(bankAccount)) return "银行账号不能为空";
        if (StrUtil.isBlank(bankName)) return "开户行不能为空";
        if (StrUtil.isBlank(licenseNo)) return "营业执照编号不能为空";
        if (StrUtil.isBlank(licenseExpiresAt)) return "执照到期日不能为空";
        if (StrUtil.isBlank(orgCode)) return "所属组织编码不能为空";

        if (supplierCode.length() > 50) return "供应商编码长度不能超过50个字符";
        if (supplierName.length() > 100) return "供应商名称长度不能超过100个字符";
        if (contactName.length() > 50) return "联系人长度不能超过50个字符";
        if (contactPhone.length() > 20) return "联系电话长度不能超过20个字符";
        if (StrUtil.isNotBlank(contactEmail) && contactEmail.length() > 100) return "联系邮箱长度不能超过100个字符";
        if (StrUtil.isNotBlank(address) && address.length() > 200) return "地址长度不能超过200个字符";
        if (StrUtil.isNotBlank(supplierType) && supplierType.length() > 50) return "供应商类型长度不能超过50个字符";
        if (unifiedCreditCode.length() > 50) return "社会信用代码长度不能超过50个字符";
        if (bankAccount.length() > 50) return "银行账号长度不能超过50个字符";
        if (bankName.length() > 100) return "开户行长度不能超过100个字符";
        if (licenseNo.length() > 100) return "营业执照编号长度不能超过100个字符";
        if (StrUtil.isNotBlank(foodLicenseNo) && foodLicenseNo.length() > 100) return "食品许可证号长度不能超过100个字符";
        if (orgCode.length() > 50) return "所属组织编码长度不能超过50个字符";

        if (!MOBILE_PHONE_PATTERN.matcher(contactPhone).matches()) {
            return "联系电话格式不正确，应为11位手机号";
        }
        if (StrUtil.isNotBlank(contactEmail) && !EMAIL_PATTERN.matcher(contactEmail).matches()) {
            return "联系邮箱格式不正确";
        }

        Long parsedOrgId;
        try {
            parseDateTime(licenseExpiresAt, "执照到期日");
            if (StrUtil.isNotBlank(foodLicenseExpiresAt)) {
                parseDateTime(foodLicenseExpiresAt, "食品许可证到期日");
            }
            parsedOrgId = resolveImportOrgIdByCode(orgCode, orgCodeToIdMap);
        } catch (BizException ex) {
            return ex.getMessage();
        }
        if (StrUtil.isNotBlank(status) && !ALLOWED_IMPORT_STATUSES.contains(status)) {
            return "状态仅支持 draft 或 pending";
        }

        row.setSupplierCode(supplierCode);
        row.setSupplierName(supplierName);
        row.setContactName(contactName);
        row.setContactPhone(contactPhone);
        row.setContactEmail(contactEmail);
        row.setAddress(address);
        row.setSupplierType(supplierType);
        row.setUnifiedCreditCode(unifiedCreditCode);
        row.setBankAccount(bankAccount);
        row.setBankName(bankName);
        row.setLicenseNo(licenseNo);
        row.setLicenseExpiresAt(licenseExpiresAt);
        row.setFoodLicenseNo(foodLicenseNo);
        row.setFoodLicenseExpiresAt(foodLicenseExpiresAt);
        row.setStatus(status);
        row.setOrgCode(orgCode);
        row.setParsedOrgId(parsedOrgId);
        return null;
    }

    private String validateAndNormalizeImportSupplierType(SupplierImportDTO row) {
        String supplierType = normalizeImportText(row.getSupplierType());
        row.setSupplierType(supplierType);
        if (StrUtil.isBlank(supplierType)) {
            return null;
        }

        String lookupKey = normalizeImportLookupKey(supplierType);
        Map<Long, ImportSupplierTypeOption> matchedOptions = new LinkedHashMap<>();
        for (ImportSupplierTypeOption option : loadActiveSupplierTypeOptions()) {
            if (Objects.equals(lookupKey, normalizeImportLookupKey(option.getDictCode()))
                    || Objects.equals(lookupKey, normalizeImportLookupKey(option.getDictName()))) {
                matchedOptions.put(option.getId(), option);
            }
        }

        if (matchedOptions.size() != 1) {
            return IMPORT_SUPPLIER_TYPE_INVALID_MESSAGE;
        }

        ImportSupplierTypeOption matchedOption = matchedOptions.values().iterator().next();
        row.setSupplierType(matchedOption.getStoredValue());
        return null;
    }

    private List<ImportSupplierTypeOption> loadActiveSupplierTypeOptions() {
        return jdbcTemplate.query(
                "SELECT id, dict_code, dict_name, dict_value " +
                        "FROM sys_dict " +
                        "WHERE tenant_id = ? AND dict_type = ? AND status = ? AND deleted = 0 " +
                        "ORDER BY sort_order ASC, updated_at DESC, id ASC",
                (rs, rowNum) -> new ImportSupplierTypeOption(
                        rs.getLong("id"),
                        rs.getString("dict_code"),
                        rs.getString("dict_name"),
                        rs.getString("dict_value")
                ),
                resolveCurrentTenantId(),
                DICT_TYPE_SUPPLIER_TYPE,
                DICT_STATUS_ACTIVE
        );
    }

    private List<String> collectImportDuplicateMessages(
            SupplierImportDTO row,
            ImportMatchResult matchResult,
            Set<String> seenSupplierCodes,
            Set<String> seenSupplierNames,
            Set<String> seenUnifiedCreditCodes,
            Set<String> seenLicenseNos,
            Set<String> seenFoodLicenseNos
    ) {
        Set<String> errorMessages = new LinkedHashSet<>();
        String supplierCode = row.getSupplierCode();
        String supplierName = row.getSupplierName();
        String unifiedCreditCode = row.getUnifiedCreditCode();
        String licenseNo = row.getLicenseNo();
        String foodLicenseNo = row.getFoodLicenseNo();

        if (!seenSupplierCodes.add(supplierCode)) {
            errorMessages.add("同一导入文件中供应商编码重复：" + resolveImportFailureSupplierCode(row));
        }
        if (!seenSupplierNames.add(supplierName)) {
            errorMessages.add("同一导入文件中供应商名称重复：" + resolveImportFailureSupplierName(row));
        }
        if (!seenUnifiedCreditCodes.add(unifiedCreditCode)) {
            errorMessages.add(DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE);
        }
        if (!seenLicenseNos.add(licenseNo)) {
            errorMessages.add(DUPLICATE_LICENSE_NO_IMPORT_MESSAGE);
        }
        if (StrUtil.isNotBlank(foodLicenseNo) && !seenFoodLicenseNos.add(foodLicenseNo)) {
            errorMessages.add(DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE);
        }

        Supplier target = matchResult.getTarget();
        Long targetId = target != null ? target.getId() : null;
        Long tenantId = target != null && target.getTenantId() != null ? target.getTenantId() : resolveCurrentTenantId();

        if (hasDuplicateSupplierCode(supplierCode, targetId, tenantId)) {
            errorMessages.add(DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE);
        }
        if (hasDuplicateSupplierName(supplierName, targetId, tenantId)) {
            errorMessages.add(DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE);
        }
        if (hasDuplicateUnifiedCreditCode(unifiedCreditCode, targetId, tenantId)) {
            errorMessages.add(DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE);
        }
        if (hasDuplicateLicenseNo(licenseNo, targetId, tenantId)) {
            errorMessages.add(DUPLICATE_LICENSE_NO_IMPORT_MESSAGE);
        }
        if (hasDuplicateFoodLicenseNo(foodLicenseNo, targetId, tenantId)) {
            errorMessages.add(DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE);
        }

        return new ArrayList<>(errorMessages);
    }

    private List<SupplierImportValidationConflictVO> collectImportValidationConflicts(
            List<SupplierImportDTO> importList,
            DataScopeService.DataScopeResult importOrgScope
    ) {
        if (importList == null || importList.isEmpty()) {
            return Collections.emptyList();
        }

        ImportMatchContext matchContext = buildImportMatchContext(importList);
        Map<String, Long> orgCodeToIdMap = buildImportOrgCodeToIdMap(importList);
        Set<String> seenSupplierCodes = new LinkedHashSet<>();
        Set<String> seenSupplierNames = new LinkedHashSet<>();
        Set<String> seenUnifiedCreditCodes = new LinkedHashSet<>();
        Set<String> seenLicenseNos = new LinkedHashSet<>();
        Set<String> seenFoodLicenseNos = new LinkedHashSet<>();
        Set<Long> processedSupplierIds = new LinkedHashSet<>();
        List<SupplierImportValidationConflictVO> conflicts = new ArrayList<>();

        for (SupplierImportDTO row : importList) {
            String basicErrorMessage = validateImportRowBasic(row, orgCodeToIdMap);
            if (basicErrorMessage != null) {
                conflicts.add(buildImportValidationConflict(row, basicErrorMessage));
                continue;
            }

            ImportMatchResult matchResult = resolveImportTarget(row, matchContext, importOrgScope);
            if (matchResult.getErrorMessage() != null) {
                conflicts.add(buildImportValidationConflict(row, matchResult.getErrorMessage()));
                continue;
            }

            Supplier target = matchResult.getTarget();
            if (target != null && !processedSupplierIds.add(target.getId())) {
                conflicts.add(buildImportValidationConflict(row, IMPORT_BATCH_UPDATE_DUPLICATE_MESSAGE));
                continue;
            }

            String supplierTypeErrorMessage = validateAndNormalizeImportSupplierType(row);
            if (supplierTypeErrorMessage != null) {
                conflicts.add(buildImportValidationConflict(row, supplierTypeErrorMessage));
                continue;
            }

            Long targetId = target != null ? target.getId() : null;
            Long tenantId = target != null && target.getTenantId() != null ? target.getTenantId() : resolveCurrentTenantId();

            if (!seenSupplierCodes.add(row.getSupplierCode())) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_SUPPLIER_CODE_FIELD,
                        resolveImportFailureSupplierCode(row),
                        "同一导入文件中供应商编码重复：" + resolveImportFailureSupplierCode(row)
                ));
            } else if (hasDuplicateSupplierCode(row.getSupplierCode(), targetId, tenantId)) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_SUPPLIER_CODE_FIELD,
                        resolveImportFailureSupplierCode(row),
                        DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE
                ));
            }

            if (!seenSupplierNames.add(row.getSupplierName())) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_SUPPLIER_NAME_FIELD,
                        resolveImportFailureSupplierName(row),
                        "同一导入文件中供应商名称重复：" + resolveImportFailureSupplierName(row)
                ));
            } else if (hasDuplicateSupplierName(row.getSupplierName(), targetId, tenantId)) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_SUPPLIER_NAME_FIELD,
                        resolveImportFailureSupplierName(row),
                        DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE
                ));
            }

            if (!seenUnifiedCreditCodes.add(row.getUnifiedCreditCode())) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_UNIFIED_CREDIT_CODE_FIELD,
                        resolveImportFailureUnifiedCreditCode(row),
                        DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE
                ));
            } else if (hasDuplicateUnifiedCreditCode(row.getUnifiedCreditCode(), targetId, tenantId)) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_UNIFIED_CREDIT_CODE_FIELD,
                        resolveImportFailureUnifiedCreditCode(row),
                        DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE
                ));
            }

            if (!seenLicenseNos.add(row.getLicenseNo())) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_LICENSE_NO_FIELD,
                        row.getLicenseNo(),
                        "同一导入文件中营业执照编号重复：" + row.getLicenseNo()
                ));
            } else if (hasDuplicateLicenseNo(row.getLicenseNo(), targetId, tenantId)) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_LICENSE_NO_FIELD,
                        row.getLicenseNo(),
                        DUPLICATE_LICENSE_NO_IMPORT_MESSAGE
                ));
            }

            if (StrUtil.isNotBlank(row.getFoodLicenseNo()) && !seenFoodLicenseNos.add(row.getFoodLicenseNo())) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_FOOD_LICENSE_NO_FIELD,
                        row.getFoodLicenseNo(),
                        "同一导入文件中食品许可证号重复：" + row.getFoodLicenseNo()
                ));
            } else if (hasDuplicateFoodLicenseNo(row.getFoodLicenseNo(), targetId, tenantId)) {
                conflicts.add(new SupplierImportValidationConflictVO(
                        row.getRowNum(),
                        DUPLICATE_FOOD_LICENSE_NO_FIELD,
                        row.getFoodLicenseNo(),
                        DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE
                ));
            }
        }

        return conflicts;
    }

    private SupplierImportValidationConflictVO buildImportValidationConflict(SupplierImportDTO row, String errorMessage) {
        return new SupplierImportValidationConflictVO(
                row.getRowNum(),
                resolveImportFailedField(errorMessage),
                resolveImportConflictValue(row, errorMessage),
                errorMessage
        );
    }

    private SupplierImportResultDTO buildDuplicateBlockedImportResult(List<SupplierImportDTO> importList) {
        DataScopeService.DataScopeResult importOrgScope = ensureImportOrgScope();
        List<SupplierImportValidationConflictVO> conflicts = collectImportValidationConflicts(importList, importOrgScope).stream()
                .filter(this::isDuplicateBlockedValidationConflict)
                .toList();
        if (conflicts.isEmpty()) {
            return null;
        }

        Map<Integer, SupplierImportDTO> rowMap = importList.stream()
                .filter(row -> row.getRowNum() != null)
                .collect(Collectors.toMap(SupplierImportDTO::getRowNum, row -> row, (left, right) -> left, LinkedHashMap::new));
        List<SupplierImportDTO> errorRows = new ArrayList<>();
        List<SupplierImportFailureDTO> failures = new ArrayList<>();

        for (SupplierImportValidationConflictVO conflict : conflicts) {
            SupplierImportDTO row = rowMap.get(conflict.getRowNum());
            if (row != null) {
                appendImportFailure(row, errorRows, failures, conflict.getMessage());
            }
        }

        String errorFileUrl = errorRows.isEmpty() ? null : generateImportErrorFile(errorRows);
        int failedRowCount = (int) conflicts.stream()
                .map(SupplierImportValidationConflictVO::getRowNum)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        SupplierImportResultDTO result = new SupplierImportResultDTO(
                importList.size(),
                0,
                failedRowCount,
                true,
                errorFileUrl,
                failures
        );

        Map<String, Object> afterData = new HashMap<>();
        afterData.put("total", result.getTotal());
        afterData.put("successCount", result.getSuccessCount());
        afterData.put("failCount", result.getFailCount());
        afterData.put("failures", failures);
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.IMPORT,
                null,
                null,
                "导入供应商失败：检测到唯一字段冲突，已阻断导入",
                null,
                toJson(afterData)
        );
        return result;
    }

    private boolean isDuplicateBlockedValidationConflict(SupplierImportValidationConflictVO conflict) {
        String message = conflict == null ? null : conflict.getMessage();
        if (StrUtil.isBlank(message)) {
            return false;
        }
        return DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE.equals(message)
                || DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE.equals(message)
                || DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE.equals(message)
                || DUPLICATE_LICENSE_NO_IMPORT_MESSAGE.equals(message)
                || DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE.equals(message)
                || message.startsWith("同一导入文件中供应商编码重复")
                || message.startsWith("同一导入文件中供应商名称重复")
                || message.startsWith("同一导入文件中营业执照编号重复")
                || message.startsWith("同一导入文件中食品许可证号重复");
    }

    private ImportMatchResult resolveImportTarget(
            SupplierImportDTO row,
            ImportMatchContext matchContext,
            DataScopeService.DataScopeResult importOrgScope
    ) {
        List<Supplier> codeMatches = matchContext.getSuppliersByCode()
                .getOrDefault(normalizeImportText(row.getSupplierCode()), Collections.emptyList());
        if (codeMatches.size() > 1) {
            return ImportMatchResult.error("供应商编码匹配到多条数据，请检查系统数据：" + resolveImportFailureSupplierCode(row));
        }

        List<Supplier> unifiedCreditCodeMatches = matchContext.getSuppliersByUnifiedCreditCode()
                .getOrDefault(normalizeUnifiedCreditCode(row.getUnifiedCreditCode()), Collections.emptyList());
        if (unifiedCreditCodeMatches.size() > 1) {
            return ImportMatchResult.error("统一社会信用代码匹配到多条数据，请检查系统数据：" + resolveImportFailureUnifiedCreditCode(row));
        }

        Supplier targetByCode = codeMatches.isEmpty() ? null : codeMatches.get(0);
        Supplier targetByUnifiedCreditCode = unifiedCreditCodeMatches.isEmpty() ? null : unifiedCreditCodeMatches.get(0);
        Long importOrgId = row.getParsedOrgId();

        if (targetByCode != null) {
            if (!importOrgScope.isAllowed(targetByCode.getOrgId())) {
                return ImportMatchResult.error(IMPORT_UPDATE_ORG_PERMISSION_BLOCK_MESSAGE);
            }
            if (!Objects.equals(importOrgId, targetByCode.getOrgId())) {
                return ImportMatchResult.error(IMPORT_ORG_CHANGE_BLOCK_MESSAGE);
            }
            if (targetByUnifiedCreditCode != null && !Objects.equals(targetByCode.getId(), targetByUnifiedCreditCode.getId())) {
                return ImportMatchResult.error(IMPORT_CODE_CREDIT_CONFLICT_BLOCK_MESSAGE);
            }
            return ImportMatchResult.success(targetByCode);
        }

        if (targetByUnifiedCreditCode != null) {
            return ImportMatchResult.error(IMPORT_REVERSE_CREDIT_CODE_BLOCK_MESSAGE);
        }

        if (!importOrgScope.isAllowed(importOrgId)) {
            return ImportMatchResult.error(IMPORT_CREATE_ORG_PERMISSION_BLOCK_MESSAGE);
        }
        return ImportMatchResult.success(null);
    }

    private SupplierCreateDTO toCreateImportDTO(SupplierImportDTO row) {
        SupplierCreateDTO dto = new SupplierCreateDTO();
        dto.setSupplierCode(row.getSupplierCode());
        dto.setSupplierName(row.getSupplierName());
        dto.setContactName(row.getContactName());
        dto.setContactPhone(row.getContactPhone());
        dto.setContactEmail(row.getContactEmail());
        dto.setAddress(row.getAddress());
        dto.setSupplierType(row.getSupplierType());
        dto.setUnifiedCreditCode(row.getUnifiedCreditCode());
        dto.setBankAccount(row.getBankAccount());
        dto.setBankName(row.getBankName());
        dto.setLicenseNo(row.getLicenseNo());
        dto.setLicenseExpiresAt(row.getLicenseExpiresAt());
        dto.setFoodLicenseNo(row.getFoodLicenseNo());
        dto.setFoodLicenseExpiresAt(row.getFoodLicenseExpiresAt());
        dto.setStatus(StrUtil.blankToDefault(row.getStatus(), STATUS_PENDING));
        return dto;
    }

    private SupplierUpdateDTO toUpdateImportDTO(SupplierImportDTO row) {
        SupplierUpdateDTO dto = new SupplierUpdateDTO();
        dto.setSupplierName(row.getSupplierName());
        dto.setContactName(row.getContactName());
        dto.setContactPhone(row.getContactPhone());
        dto.setContactEmail(row.getContactEmail());
        dto.setAddress(row.getAddress());
        dto.setSupplierType(row.getSupplierType());
        dto.setUnifiedCreditCode(row.getUnifiedCreditCode());
        dto.setBankAccount(row.getBankAccount());
        dto.setBankName(row.getBankName());
        dto.setLicenseNo(row.getLicenseNo());
        dto.setLicenseExpiresAt(row.getLicenseExpiresAt());
        dto.setFoodLicenseNo(row.getFoodLicenseNo());
        dto.setFoodLicenseExpiresAt(row.getFoodLicenseExpiresAt());
        return dto;
    }

    private void createImportSupplier(SupplierImportDTO row) {
        createSupplier(toCreateImportDTO(row), row.getParsedOrgId());
    }

    private void importUpdateSupplier(Supplier targetSupplier, SupplierImportDTO row) {
        ensureEditableSupplier(targetSupplier);

        SupplierUpdateDTO dto = toUpdateImportDTO(row);
        normalizeEditableUniqueFields(dto);
        validateEditableUniqueFields(dto, targetSupplier, targetSupplier.getId());

        String beforeData = toAuditJson(targetSupplier);
        boolean qualificationFieldsChanged = hasImportQualificationFieldChanges(dto, targetSupplier);
        applyEditableFields(dto, targetSupplier, targetSupplier.getId());
        applyImportReauditRule(targetSupplier, qualificationFieldsChanged);
        persistEditedSupplier(targetSupplier);
        supplierAiScoreService.refreshSupplierScores(Collections.singletonList(targetSupplier.getId()));

        Supplier latestSupplier = getSupplierById(targetSupplier.getId());
        auditLogService.log(
                AuditModule.SCM_SUPPLIER,
                AuditOperationType.UPDATE,
                latestSupplier.getId(),
                latestSupplier.getSupplierCode(),
                "导入更新供应商：" + latestSupplier.getSupplierName(),
                beforeData,
                buildImportUpdateAuditData(latestSupplier, qualificationFieldsChanged)
        );
    }

    private void appendImportFailure(
            SupplierImportDTO row,
            List<SupplierImportDTO> errorRows,
            List<SupplierImportFailureDTO> failures,
            String errorMessage
    ) {
        SupplierImportDTO errorRow = copyImportRow(row);
        errorRow.setDocumentType(resolveImportDocumentType(errorMessage));
        errorRow.setDocumentNo(resolveImportDocumentNo(row, errorMessage));
        errorRow.setErrorMessage(errorMessage);
        errorRow.setFailedField(resolveImportFailedField(errorMessage));
        errorRow.setSuccess(false);
        errorRows.add(errorRow);
        failures.add(new SupplierImportFailureDTO(
                row.getRowNum(),
                resolveImportFailureSupplierCode(row),
                row.getSupplierName(),
                resolveImportFailureSupplierType(row),
                resolveImportFailureUnifiedCreditCode(row),
                errorRow.getDocumentType(),
                errorRow.getDocumentNo(),
                errorRow.getFailedField(),
                errorMessage
        ));
    }

    private String resolveImportExceptionMessage(Exception ex) {
        if (StrUtil.isNotBlank(ex.getMessage())) {
            if (DUPLICATE_SUPPLIER_CODE_MESSAGE.equals(ex.getMessage())) {
                return DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE;
            }
            if (DUPLICATE_SUPPLIER_NAME_MESSAGE.equals(ex.getMessage())) {
                return DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE;
            }
            if (DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE.equals(ex.getMessage())
                    || DUPLICATE_UNIFIED_CREDIT_CODE_FIELD_MESSAGE.equals(ex.getMessage())) {
                return DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE;
            }
            if (DUPLICATE_LICENSE_NO_SAVE_MESSAGE.equals(ex.getMessage())
                    || DUPLICATE_LICENSE_NO_FIELD_MESSAGE.equals(ex.getMessage())) {
                return DUPLICATE_LICENSE_NO_IMPORT_MESSAGE;
            }
            if (DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE.equals(ex.getMessage())
                    || DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE.equals(ex.getMessage())) {
                return DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE;
            }
            return ex.getMessage();
        }
        Throwable cause = ex.getCause();
        if (cause != null && StrUtil.isNotBlank(cause.getMessage())) {
            if (DUPLICATE_SUPPLIER_CODE_MESSAGE.equals(cause.getMessage())) {
                return DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE;
            }
            if (DUPLICATE_SUPPLIER_NAME_MESSAGE.equals(cause.getMessage())) {
                return DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE;
            }
            if (DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE.equals(cause.getMessage())
                    || DUPLICATE_UNIFIED_CREDIT_CODE_FIELD_MESSAGE.equals(cause.getMessage())) {
                return DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE;
            }
            if (DUPLICATE_LICENSE_NO_SAVE_MESSAGE.equals(cause.getMessage())
                    || DUPLICATE_LICENSE_NO_FIELD_MESSAGE.equals(cause.getMessage())) {
                return DUPLICATE_LICENSE_NO_IMPORT_MESSAGE;
            }
            if (DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE.equals(cause.getMessage())
                    || DUPLICATE_FOOD_LICENSE_NO_FIELD_MESSAGE.equals(cause.getMessage())) {
                return DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE;
            }
            return cause.getMessage();
        }
        return "导入失败";
    }

    private SupplierImportDTO copyImportRow(SupplierImportDTO row) {
        SupplierImportDTO copy = new SupplierImportDTO();
        copy.setSupplierCode(row.getSupplierCode());
        copy.setSupplierName(row.getSupplierName());
        copy.setContactName(row.getContactName());
        copy.setContactPhone(row.getContactPhone());
        copy.setContactEmail(row.getContactEmail());
        copy.setAddress(row.getAddress());
        copy.setSupplierType(row.getSupplierType());
        copy.setUnifiedCreditCode(row.getUnifiedCreditCode());
        copy.setBankAccount(row.getBankAccount());
        copy.setBankName(row.getBankName());
        copy.setLicenseNo(row.getLicenseNo());
        copy.setLicenseExpiresAt(row.getLicenseExpiresAt());
        copy.setFoodLicenseNo(row.getFoodLicenseNo());
        copy.setFoodLicenseExpiresAt(row.getFoodLicenseExpiresAt());
        copy.setStatus(row.getStatus());
        copy.setOrgCode(row.getOrgCode());
        copy.setRawSupplierCode(row.getRawSupplierCode());
        copy.setRawUnifiedCreditCode(row.getRawUnifiedCreditCode());
        copy.setRawLicenseNo(row.getRawLicenseNo());
        copy.setRawSupplierType(row.getRawSupplierType());
        copy.setRawFoodLicenseNo(row.getRawFoodLicenseNo());
        copy.setRawOrgCode(row.getRawOrgCode());
        copy.setParsedOrgId(row.getParsedOrgId());
        copy.setRowNum(row.getRowNum());
        copy.setSuccess(row.getSuccess());
        return copy;
    }

    private String generateImportErrorFile(List<SupplierImportDTO> errorRows) {
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建供应商导入错误文件目录失败: {}", dir.getAbsolutePath());
            }

            String fileName = "supplier_import_errors_" + System.currentTimeMillis() + ".xlsx";
            String filePath = IMPORT_ERROR_FILE_DIR + fileName;

            try (Workbook workbook = new XSSFWorkbook(); FileOutputStream outputStream = new FileOutputStream(filePath)) {
                Sheet sheet = workbook.createSheet("导入失败数据");
                CellStyle tipStyle = createTipStyle(workbook);
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle errorStyle = createSampleStyle(workbook);

                int rowNum = 0;
                Row tipRow = sheet.createRow(rowNum++);
                tipRow.setHeightInPoints(30);
                Cell tipCell = tipRow.createCell(0);
                tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修正后重新导入。");
                tipCell.setCellStyle(tipStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length + 4));

                Row headerRow = sheet.createRow(rowNum++);
                headerRow.setHeightInPoints(46);
                Cell rowNumHeaderCell = headerRow.createCell(0);
                rowNumHeaderCell.setCellValue("行号");
                rowNumHeaderCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(0, 12 * 256);
                for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i + 1);
                    cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i + 1, IMPORT_TEMPLATE_WIDTHS[i] * 256);
                }
                Cell documentTypeHeaderCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 1);
                documentTypeHeaderCell.setCellValue("异常证照类型");
                documentTypeHeaderCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 1, 18 * 256);
                Cell documentNoHeaderCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 2);
                documentNoHeaderCell.setCellValue("证照编号原值");
                documentNoHeaderCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 2, 22 * 256);
                Cell failedFieldHeaderCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 3);
                failedFieldHeaderCell.setCellValue("失败字段");
                failedFieldHeaderCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 3, 22 * 256);
                Cell errorHeaderCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 4);
                errorHeaderCell.setCellValue("失败原因");
                errorHeaderCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 4, 42 * 256);

                for (SupplierImportDTO row : errorRows) {
                    Row dataRow = sheet.createRow(rowNum++);
                    String[] values = {
                            row.getRowNum() == null ? "" : String.valueOf(row.getRowNum()),
                            blankToEmpty(resolveImportFailureSupplierCode(row)),
                            blankToEmpty(row.getSupplierName()),
                            blankToEmpty(row.getContactName()),
                            blankToEmpty(row.getContactPhone()),
                            blankToEmpty(row.getContactEmail()),
                            blankToEmpty(row.getAddress()),
                            blankToEmpty(resolveImportFailureSupplierType(row)),
                            blankToEmpty(resolveImportFailureUnifiedCreditCode(row)),
                            blankToEmpty(row.getBankAccount()),
                            blankToEmpty(row.getBankName()),
                            blankToEmpty(resolveImportFailureLicenseNo(row)),
                            blankToEmpty(row.getLicenseExpiresAt()),
                            blankToEmpty(resolveImportFailureFoodLicenseNo(row)),
                            blankToEmpty(row.getFoodLicenseExpiresAt()),
                            blankToEmpty(row.getStatus()),
                            blankToEmpty(resolveImportFailureOrgCode(row)),
                            blankToEmpty(row.getDocumentType()),
                            blankToEmpty(row.getDocumentNo()),
                            blankToEmpty(row.getFailedField()),
                            blankToEmpty(row.getErrorMessage())
                    };
                    for (int i = 0; i < values.length; i++) {
                        Cell cell = dataRow.createCell(i);
                        cell.setCellValue(values[i]);
                        cell.setCellStyle(errorStyle);
                    }
                }

                workbook.write(outputStream);
            }
            return "/api/v1/scm/suppliers/import/errors/" + fileName;
        } catch (Exception ex) {
            log.error("生成供应商导入错误文件失败", ex);
            return null;
        }
    }

    private String normalizeImportText(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private String normalizeCertificateNo(String value) {
        return normalizeImportText(value);
    }

    private String normalizeUnifiedCreditCode(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeImportStatus(String status) {
        String normalized = normalizeImportText(status);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private Long resolveImportOrgIdByCode(String orgCodeText, Map<String, Long> orgCodeToIdMap) {
        String normalized = normalizeImportText(orgCodeText);
        if (normalized == null) {
            return null;
        }
        Long orgId = orgCodeToIdMap == null ? null : orgCodeToIdMap.get(normalized);
        if (orgId == null) {
            throw BizException.badRequest("所属组织编码不存在：" + normalized);
        }
        return orgId;
    }

    private String normalizeImportLookupKey(String value) {
        String normalized = StrUtil.trim(value);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        return normalized
                .replace("\n", "")
                .replace("\r", "")
                .replace("\t", "")
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "")
                .replace("（", "")
                .replace("）", "")
                .replace("(", "")
                .replace(")", "")
                .replace("：", "")
                .replace(":", "")
                .toLowerCase(Locale.ROOT);
    }

    private String resolveImportFailureSupplierCode(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawSupplierCode() != null ? row.getRawSupplierCode() : row.getSupplierCode();
    }

    private String resolveImportFailureSupplierName(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getSupplierName();
    }

    private String resolveImportFailureSupplierType(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawSupplierType() != null ? row.getRawSupplierType() : row.getSupplierType();
    }

    private String resolveSupplierStatusLabel(String status) {
        return switch (normalizeQueryStatus(status)) {
            case STATUS_DRAFT -> "暂存";
            case STATUS_PENDING -> "待审核";
            case STATUS_ACTIVE -> "已审核";
            case STATUS_REJECTED -> "已驳回";
            case STATUS_DISABLED -> "禁用";
            case STATUS_CANCELLED -> "已注销";
            default -> blankToEmpty(status);
        };
    }

    private String joinQualificationFileNames(List<SupplierQualificationFileVO> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        return files.stream()
                .map(SupplierQualificationFileVO::getName)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("；"));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Supplier getSupplierById(Long id) {
        Supplier supplier = supplierMapper.selectById(id);
        if (supplier == null) {
            throw BizException.notFound("供应商不存在");
        }
        return supplier;
    }

    private void checkSupplierCodeUnique(String supplierCode, Long excludeId) {
        Long count = supplierMapper.countByTenantAndSupplierCode(
                resolveCurrentTenantId(),
                supplierCode,
                excludeId
        );
        if (count != null && count > 0) {
            throw BizException.conflict(DUPLICATE_SUPPLIER_CODE_MESSAGE);
        }
    }

    private boolean hasDuplicateSupplierCode(String supplierCode, Long excludeId, Long tenantId) {
        String normalizedSupplierCode = normalizeOptionalReason(supplierCode);
        if (StrUtil.isBlank(normalizedSupplierCode) || tenantId == null) {
            return false;
        }
        Long count = supplierMapper.countByTenantAndSupplierCode(tenantId, normalizedSupplierCode, excludeId);
        return count != null && count > 0;
    }

    private void checkSupplierNameUnique(String supplierName, Long excludeId) {
        Long count = supplierMapper.countByTenantAndSupplierName(
                resolveCurrentTenantId(),
                supplierName,
                excludeId
        );
        if (count != null && count > 0) {
            throw BizException.conflict(DUPLICATE_SUPPLIER_NAME_MESSAGE);
        }
    }

    private boolean hasDuplicateSupplierName(String supplierName, Long excludeId, Long tenantId) {
        String normalizedSupplierName = normalizeOptionalReason(supplierName);
        if (StrUtil.isBlank(normalizedSupplierName) || tenantId == null) {
            return false;
        }
        Long count = supplierMapper.countByTenantAndSupplierName(tenantId, normalizedSupplierName, excludeId);
        return count != null && count > 0;
    }

    private void checkUnifiedCreditCodeUnique(String unifiedCreditCode, Long excludeId, String errorMessage) {
        String normalizedUnifiedCreditCode = normalizeUnifiedCreditCode(unifiedCreditCode);
        if (StrUtil.isBlank(normalizedUnifiedCreditCode)) {
            return;
        }
        Long count = supplierMapper.countByTenantAndNormalizedUnifiedCreditCode(
                resolveCurrentTenantId(),
                normalizedUnifiedCreditCode,
                excludeId
        );
        if (count != null && count > 0) {
            throw BizException.conflict(errorMessage);
        }
    }

    private boolean hasDuplicateUnifiedCreditCode(String unifiedCreditCode, Long excludeId, Long tenantId) {
        String normalizedUnifiedCreditCode = normalizeUnifiedCreditCode(unifiedCreditCode);
        if (StrUtil.isBlank(normalizedUnifiedCreditCode)) {
            return false;
        }
        Long count = supplierMapper.countByTenantAndNormalizedUnifiedCreditCode(
                tenantId,
                normalizedUnifiedCreditCode,
                excludeId
        );
        return count != null && count > 0;
    }

    private void checkLicenseNoUnique(String licenseNo, Long tenantId, Long excludeId, String errorMessage) {
        String normalizedLicenseNo = normalizeCertificateNo(licenseNo);
        if (StrUtil.isBlank(normalizedLicenseNo)) {
            return;
        }
        Long count = supplierMapper.countByTenantAndNormalizedLicenseNo(
                tenantId,
                normalizedLicenseNo,
                excludeId
        );
        if (count != null && count > 0) {
            throw BizException.conflict(errorMessage);
        }
    }

    private boolean hasDuplicateLicenseNo(String licenseNo, Long excludeId, Long tenantId) {
        String normalizedLicenseNo = normalizeCertificateNo(licenseNo);
        if (StrUtil.isBlank(normalizedLicenseNo) || tenantId == null) {
            return false;
        }
        Long count = supplierMapper.countByTenantAndNormalizedLicenseNo(
                tenantId,
                normalizedLicenseNo,
                excludeId
        );
        return count != null && count > 0;
    }

    private void checkFoodLicenseNoUnique(String foodLicenseNo, Long tenantId, Long excludeId, String errorMessage) {
        String normalizedFoodLicenseNo = normalizeCertificateNo(foodLicenseNo);
        if (StrUtil.isBlank(normalizedFoodLicenseNo)) {
            return;
        }
        Long count = supplierMapper.countByTenantAndNormalizedFoodLicenseNo(
                tenantId,
                normalizedFoodLicenseNo,
                excludeId
        );
        if (count != null && count > 0) {
            throw BizException.conflict(errorMessage);
        }
    }

    private boolean hasDuplicateFoodLicenseNo(String foodLicenseNo, Long excludeId, Long tenantId) {
        String normalizedFoodLicenseNo = normalizeCertificateNo(foodLicenseNo);
        if (StrUtil.isBlank(normalizedFoodLicenseNo) || tenantId == null) {
            return false;
        }
        Long count = supplierMapper.countByTenantAndNormalizedFoodLicenseNo(
                tenantId,
                normalizedFoodLicenseNo,
                excludeId
        );
        return count != null && count > 0;
    }

    private void normalizeCreateIdentityFields(SupplierCreateDTO dto) {
        dto.setSupplierCode(normalizeRequiredReason(dto.getSupplierCode(), "供应商编码不能为空"));
        dto.setSupplierName(normalizeRequiredReason(dto.getSupplierName(), "供应商名称不能为空"));
    }

    private void normalizeEditableIdentityFields(SupplierUpdateDTO dto) {
        if (dto.getSupplierCode() != null) {
            dto.setSupplierCode(normalizeRequiredReason(dto.getSupplierCode(), "供应商编码不能为空"));
        }
        if (dto.getSupplierName() != null) {
            dto.setSupplierName(normalizeRequiredReason(dto.getSupplierName(), "供应商名称不能为空"));
        }
    }

    private void normalizeEditableUniqueFields(SupplierUpdateDTO dto) {
        dto.setUnifiedCreditCode(normalizeUnifiedCreditCode(dto.getUnifiedCreditCode()));
        dto.setLicenseNo(normalizeRequiredReason(dto.getLicenseNo(), "营业执照编号不能为空"));
        dto.setFoodLicenseNo(normalizeOptionalReason(dto.getFoodLicenseNo()));
    }

    private boolean hasImportQualificationFieldChanges(SupplierUpdateDTO dto, Supplier supplier) {
        return !Objects.equals(dto.getLicenseNo(), normalizeCertificateNo(supplier.getLicenseNo()))
                || !Objects.equals(parseDateTime(dto.getLicenseExpiresAt(), "营业执照到期日"), supplier.getLicenseExpiresAt())
                || !Objects.equals(dto.getFoodLicenseNo(), normalizeCertificateNo(supplier.getFoodLicenseNo()))
                || !Objects.equals(parseDateTime(dto.getFoodLicenseExpiresAt(), "食品许可证到期日"), supplier.getFoodLicenseExpiresAt());
    }

    private void applyImportReauditRule(Supplier supplier, boolean qualificationFieldsChanged) {
        if (!qualificationFieldsChanged) {
            return;
        }
        if (STATUS_ACTIVE.equals(supplier.getStatus()) || STATUS_REJECTED.equals(supplier.getStatus())) {
            supplier.setStatus(STATUS_PENDING);
            clearAuditFields(supplier);
        }
    }

    private String buildImportUpdateAuditData(Supplier supplier, boolean qualificationFieldsChanged) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("supplier", toVO(supplier));
        payload.put("ignoredUpdateFields", IMPORT_IGNORED_UPDATE_FIELDS);
        payload.put("qualificationFieldsChanged", qualificationFieldsChanged);
        payload.put("matchRule", "supplierCodeFirst");
        return toJson(payload);
    }

    private void validateEditableUniqueFields(SupplierUpdateDTO dto, Supplier supplier, Long supplierId) {
        String currentSupplierCode = normalizeOptionalReason(supplier.getSupplierCode());
        String currentSupplierName = normalizeOptionalReason(supplier.getSupplierName());
        String currentUnifiedCreditCode = normalizeUnifiedCreditCode(supplier.getUnifiedCreditCode());
        String currentLicenseNo = normalizeCertificateNo(supplier.getLicenseNo());
        String currentFoodLicenseNo = normalizeCertificateNo(supplier.getFoodLicenseNo());
        Long tenantId = supplier.getTenantId() != null ? supplier.getTenantId() : resolveCurrentTenantId();

        if (dto.getSupplierCode() != null && !Objects.equals(dto.getSupplierCode(), currentSupplierCode)) {
            checkSupplierCodeUnique(dto.getSupplierCode(), supplierId);
        }
        if (dto.getSupplierName() != null && !Objects.equals(dto.getSupplierName(), currentSupplierName)) {
            checkSupplierNameUnique(dto.getSupplierName(), supplierId);
        }
        if (!Objects.equals(dto.getUnifiedCreditCode(), currentUnifiedCreditCode)) {
            checkUnifiedCreditCodeUnique(dto.getUnifiedCreditCode(), supplierId, DUPLICATE_UNIFIED_CREDIT_CODE_SAVE_MESSAGE);
        }
        if (!Objects.equals(dto.getLicenseNo(), currentLicenseNo)) {
            checkLicenseNoUnique(dto.getLicenseNo(), tenantId, supplierId, DUPLICATE_LICENSE_NO_SAVE_MESSAGE);
        }
        if (!Objects.equals(dto.getFoodLicenseNo(), currentFoodLicenseNo)) {
            checkFoodLicenseNoUnique(dto.getFoodLicenseNo(), tenantId, supplierId, DUPLICATE_FOOD_LICENSE_NO_SAVE_MESSAGE);
        }
    }

    private List<Supplier> findSuppliersByNormalizedUnifiedCreditCodes(Set<String> unifiedCreditCodes) {
        Set<String> normalizedUnifiedCreditCodes = unifiedCreditCodes == null
                ? Collections.emptySet()
                : unifiedCreditCodes.stream()
                .map(this::normalizeUnifiedCreditCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalizedUnifiedCreditCodes.isEmpty()) {
            return Collections.emptyList();
        }
        return supplierMapper.selectByTenantAndNormalizedUnifiedCreditCodes(
                resolveCurrentTenantId(),
                new ArrayList<>(normalizedUnifiedCreditCodes)
        );
    }

    private String resolveImportFailureUnifiedCreditCode(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawUnifiedCreditCode() != null ? row.getRawUnifiedCreditCode() : row.getUnifiedCreditCode();
    }

    private String resolveImportFailureLicenseNo(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawLicenseNo() != null ? row.getRawLicenseNo() : row.getLicenseNo();
    }

    private String resolveImportFailureFoodLicenseNo(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawFoodLicenseNo() != null ? row.getRawFoodLicenseNo() : row.getFoodLicenseNo();
    }

    private String resolveImportFailureOrgCode(SupplierImportDTO row) {
        if (row == null) {
            return null;
        }
        return row.getRawOrgCode() != null ? row.getRawOrgCode() : row.getOrgCode();
    }

    private String resolveImportConflictValue(SupplierImportDTO row, String errorMessage) {
        if (row == null || StrUtil.isBlank(errorMessage)) {
            return null;
        }
        if (DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE.equals(errorMessage)
                || IMPORT_BATCH_UPDATE_DUPLICATE_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("同一导入文件中供应商编码重复")
                || errorMessage.startsWith("供应商编码匹配到多条数据")) {
            return resolveImportFailureSupplierCode(row);
        }
        if (DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("同一导入文件中供应商名称重复")) {
            return resolveImportFailureSupplierName(row);
        }
        if (DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE.equals(errorMessage)
                || IMPORT_REVERSE_CREDIT_CODE_BLOCK_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("统一社会信用代码匹配到多条数据")) {
            return resolveImportFailureUnifiedCreditCode(row);
        }
        if (DUPLICATE_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("同一导入文件中营业执照编号重复")) {
            return resolveImportFailureLicenseNo(row);
        }
        if (DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("同一导入文件中食品许可证号重复")) {
            return resolveImportFailureFoodLicenseNo(row);
        }
        if (IMPORT_CREATE_ORG_PERMISSION_BLOCK_MESSAGE.equals(errorMessage)
                || IMPORT_UPDATE_ORG_PERMISSION_BLOCK_MESSAGE.equals(errorMessage)
                || IMPORT_ORG_CHANGE_BLOCK_MESSAGE.equals(errorMessage)
                || errorMessage.startsWith("所属组织编码")) {
            return resolveImportFailureOrgCode(row);
        }
        if (IMPORT_SUPPLIER_TYPE_INVALID_MESSAGE.equals(errorMessage)) {
            return resolveImportFailureSupplierType(row);
        }
        return null;
    }

    private String resolveImportFailedField(String errorMessage) {
        if (DUPLICATE_SUPPLIER_CODE_IMPORT_MESSAGE.equals(errorMessage)
                || IMPORT_BATCH_UPDATE_DUPLICATE_MESSAGE.equals(errorMessage)
                || (errorMessage != null && errorMessage.startsWith("同一导入文件中供应商编码重复"))) {
            return DUPLICATE_SUPPLIER_CODE_FIELD;
        }
        if (DUPLICATE_SUPPLIER_NAME_IMPORT_MESSAGE.equals(errorMessage)
                || (errorMessage != null && errorMessage.startsWith("同一导入文件中供应商名称重复"))) {
            return DUPLICATE_SUPPLIER_NAME_FIELD;
        }
        if (IMPORT_REVERSE_CREDIT_CODE_BLOCK_MESSAGE.equals(errorMessage)) {
            return IMPORT_FAILED_FIELD_CODE_AND_CREDIT;
        }
        if (IMPORT_CREATE_ORG_PERMISSION_BLOCK_MESSAGE.equals(errorMessage)
                || IMPORT_UPDATE_ORG_PERMISSION_BLOCK_MESSAGE.equals(errorMessage)
                || IMPORT_ORG_CHANGE_BLOCK_MESSAGE.equals(errorMessage)
                || (errorMessage != null && errorMessage.startsWith("所属组织编码"))) {
            return IMPORT_FAILED_FIELD_ORG;
        }
        if (IMPORT_CODE_CREDIT_CONFLICT_BLOCK_MESSAGE.equals(errorMessage)) {
            return IMPORT_FAILED_FIELD_SUPPLIER_CODE_AND_CREDIT;
        }
        if (DUPLICATE_UNIFIED_CREDIT_CODE_IMPORT_MESSAGE.equals(errorMessage)) {
            return DUPLICATE_UNIFIED_CREDIT_CODE_FIELD;
        }
        if (DUPLICATE_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return DUPLICATE_LICENSE_NO_FIELD;
        }
        if (DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return DUPLICATE_FOOD_LICENSE_NO_FIELD;
        }
        if (IMPORT_SUPPLIER_TYPE_INVALID_MESSAGE.equals(errorMessage)) {
            return IMPORT_SUPPLIER_TYPE_FIELD;
        }
        return null;
    }

    private String resolveImportDocumentType(String errorMessage) {
        if (DUPLICATE_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return DOCUMENT_TYPE_LICENSE;
        }
        if (DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return DOCUMENT_TYPE_FOOD_LICENSE;
        }
        return null;
    }

    private String resolveImportDocumentNo(SupplierImportDTO row, String errorMessage) {
        if (DUPLICATE_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return resolveImportFailureLicenseNo(row);
        }
        if (DUPLICATE_FOOD_LICENSE_NO_IMPORT_MESSAGE.equals(errorMessage)) {
            return resolveImportFailureFoodLicenseNo(row);
        }
        return null;
    }

    private void copyCreateFields(SupplierCreateDTO dto, Supplier supplier) {
        supplier.setSupplierCode(dto.getSupplierCode());
        supplier.setSupplierName(dto.getSupplierName());
        supplier.setContactName(dto.getContactName());
        supplier.setContactPhone(dto.getContactPhone());
        supplier.setContactEmail(dto.getContactEmail());
        supplier.setAddress(dto.getAddress());
        supplier.setSupplierType(dto.getSupplierType());
        supplier.setUnifiedCreditCode(normalizeUnifiedCreditCode(dto.getUnifiedCreditCode()));
        supplier.setBankAccount(dto.getBankAccount());
        supplier.setBankName(dto.getBankName());
        supplier.setLicenseNo(normalizeRequiredReason(dto.getLicenseNo(), "营业执照编号不能为空"));
        supplier.setLicenseExpiresAt(parseDateTime(dto.getLicenseExpiresAt(), "营业执照到期日"));
        supplier.setFoodLicenseNo(normalizeOptionalReason(dto.getFoodLicenseNo()));
        supplier.setFoodLicenseExpiresAt(parseDateTime(dto.getFoodLicenseExpiresAt(), "食品许可证到期日"));
        supplier.setQualificationFiles(toQualificationFilesJson(dto.getQualificationFiles()));
        supplier.setStatus(dto.getStatus());
        supplier.setDisableReason(normalizeOptionalReason(dto.getDisableReason()));
    }

    private String normalizeQueryStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return status;
        }
        if ("approved".equalsIgnoreCase(status.trim())) {
            return "active";
        }
        return status.trim();
    }

    private SupplierVO toVO(Supplier supplier) {
        LocalDate today = LocalDate.now();
        SupplierVO vo = new SupplierVO();
        vo.setId(supplier.getId());
        vo.setSupplierCode(supplier.getSupplierCode());
        vo.setSupplierName(supplier.getSupplierName());
        vo.setContactName(supplier.getContactName());
        vo.setContactPhone(supplier.getContactPhone());
        vo.setContactEmail(supplier.getContactEmail());
        vo.setAddress(supplier.getAddress());
        vo.setSupplierType(supplier.getSupplierType());
        vo.setUnifiedCreditCode(supplier.getUnifiedCreditCode());
        vo.setBankAccount(supplier.getBankAccount());
        vo.setBankName(supplier.getBankName());
        vo.setLicenseNo(supplier.getLicenseNo());
        vo.setLicenseExpiresAt(formatDate(supplier.getLicenseExpiresAt()));
        vo.setLicenseExpiryStatus(SupplierQualificationStatusSupport.resolveStatus(supplier.getLicenseExpiresAt(), today));
        vo.setLicenseRemainingDays(SupplierQualificationStatusSupport.resolveRemainingDays(supplier.getLicenseExpiresAt(), today));
        vo.setFoodLicenseNo(supplier.getFoodLicenseNo());
        vo.setFoodLicenseExpiresAt(formatDate(supplier.getFoodLicenseExpiresAt()));
        vo.setFoodLicenseExpiryStatus(SupplierQualificationStatusSupport.resolveStatus(supplier.getFoodLicenseExpiresAt(), today));
        vo.setFoodLicenseRemainingDays(SupplierQualificationStatusSupport.resolveRemainingDays(supplier.getFoodLicenseExpiresAt(), today));
        vo.setCreditScore(supplier.getCreditScore() == null ? BigDecimal.ZERO : supplier.getCreditScore());
        vo.setScoreQualification(supplier.getScoreQualification());
        vo.setScoreQuality(supplier.getScoreQuality());
        vo.setScorePrice(supplier.getScorePrice());
        vo.setScoreDelivery(supplier.getScoreDelivery());
        vo.setStatus(supplier.getStatus());
        vo.setDisableReason(supplier.getDisableReason());
        vo.setCancelReason(supplier.getCancelReason());
        vo.setQualificationFiles(parseQualificationFiles(supplier.getQualificationFiles()));
        vo.setAuditAt(formatDateTime(supplier.getAuditAt()));
        vo.setAuditRemark(supplier.getAuditRemark());
        vo.setTenantId(supplier.getTenantId());
        vo.setCreatedAt(formatDateTime(supplier.getCreatedAt()));
        vo.setUpdatedAt(formatDateTime(supplier.getUpdatedAt()));
        return vo;
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        String text = value.trim();
        try {
            if (text.length() == 10) {
                return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
            }
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest(fieldName + "格式错误，正确格式应为 yyyy-MM-dd");
        }
    }

    private String formatDate(LocalDateTime value) {
        return value == null ? null : value.toLocalDate().format(DATE_FORMATTER);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private boolean isResetAuditStatus(String status) {
        return STATUS_DRAFT.equals(status)
                || STATUS_PENDING.equals(status)
                || STATUS_DISABLED.equals(status)
                || STATUS_CANCELLED.equals(status);
    }

    private String toQualificationFilesJson(List<SupplierQualificationFileDTO> files) {
        if (files == null || files.isEmpty()) {
            return null;
        }
        List<SupplierQualificationFileDTO> normalizedFiles = normalizeQualificationFiles(files);
        if (normalizedFiles.isEmpty()) {
            return null;
        }
        String json = toJson(normalizedFiles);
        if (json == null) {
            throw BizException.badRequest("资质文件格式不正确");
        }
        return json;
    }

    private List<SupplierQualificationFileVO> parseQualificationFiles(String json) {
        if (StrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SupplierQualificationFileVO>>() {
            });
        } catch (Exception e) {
            log.warn("解析资质文件失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private Long nullToZero(Long value) {
        return value == null ? 0L : value;
    }

    private Set<Long> resolveScopedOrgIds(Long orgId, List<Long> orgIds) {
        if (orgId != null) {
            return Set.of(orgId);
        }
        if (orgIds == null) {
            return null;
        }
        return Set.copyOf(orgIds);
    }

    private String normalizeCreateStatus(String status, String disableReason) {
        String normalizedStatus = StrUtil.blankToDefault(StrUtil.trim(status), STATUS_DRAFT);
        if (!STATUS_DRAFT.equals(normalizedStatus)
                && !STATUS_PENDING.equals(normalizedStatus)
                && !STATUS_DISABLED.equals(normalizedStatus)) {
            throw BizException.badRequest("新增供应商仅支持暂存、提交审核或禁用");
        }
        if (STATUS_DISABLED.equals(normalizedStatus)) {
            normalizeRequiredReason(disableReason, "禁用原因不能为空");
        }
        return normalizedStatus;
    }

    private void ensureEditableSupplier(Supplier supplier) {
        if (STATUS_DISABLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("禁用供应商不可编辑，请先启用");
        }
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("已注销供应商不可编辑");
        }
    }

    private String normalizeUpdateStatus(String currentStatus, String requestedStatus) {
        if (requestedStatus == null) {
            return null;
        }
        String normalized = StrUtil.trim(requestedStatus);
        if (StrUtil.isBlank(normalized)) {
            return null;
        }
        if (STATUS_DISABLED.equals(normalized)) {
            throw BizException.badRequest("禁用操作请使用专用接口");
        }
        if (STATUS_CANCELLED.equals(normalized)) {
            throw BizException.badRequest("注销操作请使用专用接口");
        }
        if ((STATUS_ACTIVE.equals(currentStatus)
                || STATUS_PENDING.equals(currentStatus)
                || STATUS_REJECTED.equals(currentStatus)
                || STATUS_DISABLED.equals(currentStatus))
                && STATUS_DRAFT.equals(normalized)) {
            throw BizException.badRequest("当前状态供应商不允许保存为暂存，请使用提交");
        }
        if (!STATUS_DRAFT.equals(normalized)
                && !STATUS_PENDING.equals(normalized)
                && !STATUS_ACTIVE.equals(normalized)
                && !STATUS_REJECTED.equals(normalized)) {
            throw BizException.badRequest("供应商状态不合法");
        }
        return normalized;
    }

    private void applyEditableFields(SupplierUpdateDTO dto, Supplier supplier, Long supplierId) {
        if (StrUtil.isNotBlank(dto.getSupplierCode()) && !dto.getSupplierCode().equals(supplier.getSupplierCode())) {
            checkSupplierCodeUnique(dto.getSupplierCode(), supplierId);
            supplier.setSupplierCode(dto.getSupplierCode());
        }

        if (dto.getSupplierName() != null) supplier.setSupplierName(dto.getSupplierName());
        if (dto.getContactName() != null) supplier.setContactName(dto.getContactName());
        if (dto.getContactPhone() != null) supplier.setContactPhone(dto.getContactPhone());
        if (dto.getContactEmail() != null) supplier.setContactEmail(dto.getContactEmail());
        if (dto.getAddress() != null) supplier.setAddress(dto.getAddress());
        supplier.setSupplierType(normalizeOptionalReason(dto.getSupplierType()));
        if (dto.getUnifiedCreditCode() != null) supplier.setUnifiedCreditCode(normalizeUnifiedCreditCode(dto.getUnifiedCreditCode()));
        if (dto.getBankAccount() != null) supplier.setBankAccount(dto.getBankAccount());
        if (dto.getBankName() != null) supplier.setBankName(dto.getBankName());
        if (dto.getLicenseNo() != null) supplier.setLicenseNo(normalizeRequiredReason(dto.getLicenseNo(), "营业执照编号不能为空"));
        if (dto.getLicenseExpiresAt() != null) {
            supplier.setLicenseExpiresAt(parseDateTime(dto.getLicenseExpiresAt(), "营业执照到期日"));
        }
        supplier.setFoodLicenseNo(normalizeOptionalReason(dto.getFoodLicenseNo()));
        supplier.setFoodLicenseExpiresAt(parseDateTime(dto.getFoodLicenseExpiresAt(), "食品许可证到期日"));
        if (dto.getQualificationFiles() != null) {
            supplier.setQualificationFiles(toQualificationFilesJson(dto.getQualificationFiles()));
        }
    }

    private void persistEditedSupplier(Supplier supplier) {
        Supplier updateEntity = new Supplier();
        updateEntity.setUpdatedAt(LocalDateTime.now());
        updateEntity.setUpdatedBy(resolveCurrentUserId());

        LambdaUpdateWrapper<Supplier> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Supplier::getId, supplier.getId())
                .set(Supplier::getSupplierCode, supplier.getSupplierCode())
                .set(Supplier::getSupplierName, supplier.getSupplierName())
                .set(Supplier::getContactName, supplier.getContactName())
                .set(Supplier::getContactPhone, supplier.getContactPhone())
                .set(Supplier::getContactEmail, supplier.getContactEmail())
                .set(Supplier::getAddress, supplier.getAddress())
                .set(Supplier::getSupplierType, supplier.getSupplierType())
                .set(Supplier::getUnifiedCreditCode, supplier.getUnifiedCreditCode())
                .set(Supplier::getBankAccount, supplier.getBankAccount())
                .set(Supplier::getBankName, supplier.getBankName())
                .set(Supplier::getLicenseNo, supplier.getLicenseNo())
                .set(Supplier::getLicenseExpiresAt, supplier.getLicenseExpiresAt())
                .set(Supplier::getFoodLicenseNo, supplier.getFoodLicenseNo())
                .set(Supplier::getFoodLicenseExpiresAt, supplier.getFoodLicenseExpiresAt())
                .set(Supplier::getQualificationFiles, supplier.getQualificationFiles())
                .set(Supplier::getStatus, supplier.getStatus())
                .set(Supplier::getDisableReason, supplier.getDisableReason())
                .set(Supplier::getAuditBy, supplier.getAuditBy())
                .set(Supplier::getAuditAt, supplier.getAuditAt())
                .set(Supplier::getAuditRemark, supplier.getAuditRemark());

        int updatedRows = supplierMapper.update(updateEntity, updateWrapper);
        if (updatedRows != 1) {
            throw BizException.notFound("供应商不存在");
        }
    }

    private List<SupplierQualificationFileDTO> normalizeQualificationFiles(List<SupplierQualificationFileDTO> files) {
        List<SupplierQualificationFileDTO> normalizedFiles = new ArrayList<>();
        for (SupplierQualificationFileDTO file : files) {
            if (file == null) {
                continue;
            }
            SupplierQualificationFileDTO normalizedFile = new SupplierQualificationFileDTO();
            normalizedFile.setId(file.getId());
            normalizedFile.setName(normalizeRequiredReason(file.getName(), "资质文件名不能为空"));
            normalizedFile.setSize(normalizeRequiredReason(file.getSize(), "资质文件大小不能为空"));
            normalizedFile.setUrl(normalizeOptionalReason(file.getUrl()));
            normalizedFiles.add(normalizedFile);
        }
        return normalizedFiles;
    }

    private List<String> extractQualificationFileUrls(String qualificationFilesJson) {
        if (StrUtil.isBlank(qualificationFilesJson)) {
            return Collections.emptyList();
        }
        return parseQualificationFiles(qualificationFilesJson).stream()
                .map(SupplierQualificationFileVO::getUrl)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private void deleteRemovedQualificationFiles(String beforeQualificationFiles, String afterQualificationFiles) {
        Set<String> currentUrls = new LinkedHashSet<>(extractQualificationFileUrls(afterQualificationFiles));
        List<String> removedUrls = extractQualificationFileUrls(beforeQualificationFiles).stream()
                .filter(url -> !currentUrls.contains(url))
                .toList();
        deleteQualificationFiles(removedUrls);
    }

    private void deleteQualificationFiles(List<String> fileUrls) {
        if (fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        fileUrls.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .forEach(fileStorageService::delete);
    }

    private void clearAuditFields(Supplier supplier) {
        supplier.setAuditAt(null);
        supplier.setAuditRemark(null);
        supplier.setAuditBy(null);
    }

    private void validateDisablePreconditions(Supplier supplier, Long supplierId, boolean concurrentRecheck) {
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("已注销供应商不可禁用");
        }
        if (STATUS_DISABLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("当前供应商已禁用");
        }
        if (hasPendingApprovePurchaseOrders(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, "该供应商存在待审批采购订单，不允许禁用");
        }
        if (hasPendingOrPartialInboundPurchaseOrders(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, "该供应商存在未入库或部分入库的采购订单，不允许禁用");
        }
        if (hasPendingInboundOrders(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, "该供应商存在待审批的入库单，不允许禁用");
        }
    }

    private void validateCancelPreconditions(Supplier supplier, Long supplierId, boolean concurrentRecheck) {
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("当前供应商已注销");
        }
        if (hasUnfinishedPurchaseOrders(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, "该供应商存在未完成采购订单，不允许注销");
        }
        if (hasUnfinishedInboundOrders(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, "该供应商存在未完成或未入库的入库单，不允许注销");
        }
    }

    private void validateDeletePreconditions(Supplier supplier, Long supplierId, boolean concurrentRecheck) {
        if (STATUS_CANCELLED.equals(supplier.getStatus())) {
            throw BizException.badRequest("已注销供应商不可删除");
        }
        if (hasAnyBusinessData(supplierId)) {
            throwConcurrentOrSpecific(concurrentRecheck, DELETE_BLOCK_MESSAGE);
        }
    }

    private void throwConcurrentOrSpecific(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? CONCURRENT_RECHECK_BLOCK_MESSAGE : defaultMessage);
    }

    private void performConcurrentRecheck(
            Long supplierId,
            String actionLabel,
            AuditOperationType operationType,
            java.util.function.Consumer<Supplier> validator
    ) {
        Supplier latestSupplier = getSupplierById(supplierId);
        String beforeData = toAuditJson(latestSupplier);
        Map<String, Object> resultPayload = buildConcurrentRecheckPayload(actionLabel, "passed", null);
        try {
            validator.accept(latestSupplier);
            auditLogService.log(
                    AuditModule.SCM_SUPPLIER,
                    operationType,
                    latestSupplier.getId(),
                    latestSupplier.getSupplierCode(),
                    "供应商" + actionLabel + "并发二次重校验通过",
                    beforeData,
                    toJson(resultPayload)
            );
        } catch (BizException ex) {
            Map<String, Object> failurePayload = buildConcurrentRecheckPayload(actionLabel, "blocked", ex.getMessage());
            auditLogService.log(
                    AuditModule.SCM_SUPPLIER,
                    operationType,
                    latestSupplier.getId(),
                    latestSupplier.getSupplierCode(),
                    "供应商" + actionLabel + "并发二次重校验拦截",
                    beforeData,
                    toJson(failurePayload),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private Map<String, Object> buildConcurrentRecheckPayload(String actionLabel, String result, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", actionLabel);
        payload.put("stage", "concurrent_final_recheck");
        payload.put("result", result);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", resolveCurrentUserId());
        payload.put("operatorName", UserContext.getUsername());
        payload.put("reason", reason);
        return payload;
    }

    private String normalizeOptionalReason(String reason) {
        String normalized = StrUtil.trim(reason);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private String normalizeRequiredReason(String reason, String message) {
        String normalized = normalizeOptionalReason(reason);
        if (normalized == null) {
            throw BizException.badRequest(message);
        }
        return normalized;
    }

    private void validateQualificationFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_QUALIFICATION_FILE_SIZE) {
            throw BizException.badRequest("文件大小不能超过10MB");
        }

        String fileName = resolveAttachmentName(file);
        String extension = StrUtil.subAfter(fileName, ".", true);
        if (StrUtil.isBlank(extension) || !ALLOWED_QUALIFICATION_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT))) {
            throw BizException.badRequest("仅支持 PDF、JPG、PNG、DOC、DOCX、XLS、XLSX 格式的文件");
        }
    }

    private String resolveAttachmentName(MultipartFile file) {
        String originalFilename = file == null ? null : file.getOriginalFilename();
        if (StrUtil.isBlank(originalFilename)) {
            return "supplier-file";
        }
        return originalFilename.trim();
    }

    private String formatFileSize(long fileSize) {
        if (fileSize >= 1024L * 1024) {
            return String.format(Locale.ROOT, "%.1f MB", fileSize / 1024D / 1024D);
        }
        return Math.max(1, fileSize / 1024) + " KB";
    }

    private boolean hasPendingApprovePurchaseOrders(Long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order " +
                        "WHERE deleted = 0 AND supplier_id = ? AND status = 'pending_approve'",
                Integer.class,
                supplierId
        );
        return count != null && count > 0;
    }

    private boolean hasPendingOrPartialInboundPurchaseOrders(Long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order " +
                        "WHERE deleted = 0 AND supplier_id = ? " +
                        "AND status IN ('approved', 'delivering', 'pending_receipt', 'pending_void_approve')",
                Integer.class,
                supplierId
        );
        return count != null && count > 0;
    }

    private boolean hasUnfinishedPurchaseOrders(Long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order " +
                        "WHERE deleted = 0 AND supplier_id = ? AND status NOT IN ('voided', 'closed', 'cancelled')",
                Integer.class,
                supplierId
        );
        return count != null && count > 0;
    }

    private boolean hasPendingInboundOrders(Long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inbound_order " +
                        "WHERE deleted = 0 AND supplier_id = ? AND status = 'pending'",
                Integer.class,
                supplierId
        );
        return count != null && count > 0;
    }

    private boolean hasUnfinishedInboundOrders(Long supplierId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inbound_order " +
                        "WHERE deleted = 0 AND supplier_id = ? AND status NOT IN ('approved', 'cancelled')",
                Integer.class,
                supplierId
        );
        return count != null && count > 0;
    }

    private boolean hasAnyBusinessData(Long supplierId) {
        Integer purchaseCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order WHERE deleted = 0 AND supplier_id = ?",
                Integer.class,
                supplierId
        );
        if (purchaseCount != null && purchaseCount > 0) {
            return true;
        }

        Integer inboundCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inbound_order WHERE deleted = 0 AND supplier_id = ?",
                Integer.class,
                supplierId
        );
        return inboundCount != null && inboundCount > 0;
    }

    private Long resolveCurrentOrgId() {
        return UserContext.getOrgId() != null ? UserContext.getOrgId() : DEFAULT_ORG_ID;
    }

    private Long resolveCurrentTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
    }

    private Long resolveCurrentUserId() {
        return UserContext.getUserId() != null ? UserContext.getUserId() : DEFAULT_AUDIT_USER_ID;
    }

    private String toAuditJson(Supplier supplier) {
        if (supplier == null) {
            return null;
        }
        return toJson(toVO(supplier));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("序列化数据失败: {}", e.getMessage());
            return null;
        }
    }

    private void writeAttachment(String attachmentName, String attachmentUrl, HttpServletResponse response) {
        FileStorageService.StoredFile storedFile = fileStorageService.download(attachmentUrl);
        String fileName = StrUtil.blankToDefault(attachmentName, "attachment");
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
            response.setHeader(
                    "Content-Disposition",
                    "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20")
            );
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            throw new RuntimeException("附件下载失败", e);
        }
    }

    private String resolveOperatorName(Long userId) {
        if (userId == null) {
            return null;
        }

        List<String> userNames = jdbcTemplate.query(
                "SELECT COALESCE(NULLIF(TRIM(e.real_name), ''), NULLIF(TRIM(u.real_name), ''), NULLIF(TRIM(u.username), '')) AS operator_name " +
                        "FROM auth_user u " +
                        "LEFT JOIN sys_employee e ON e.user_id = u.id AND e.deleted = 0 " +
                        "WHERE u.id = ? AND u.deleted = 0 " +
                        "LIMIT 1",
                (rs, rowNum) -> rs.getString("operator_name"),
                userId
        );
        if (!userNames.isEmpty() && StrUtil.isNotBlank(userNames.get(0))) {
            return userNames.get(0).trim();
        }

        List<String> employeeNames = jdbcTemplate.query(
                "SELECT NULLIF(TRIM(real_name), '') AS operator_name " +
                        "FROM sys_employee " +
                        "WHERE id = ? AND deleted = 0 " +
                        "LIMIT 1",
                (rs, rowNum) -> rs.getString("operator_name"),
                userId
        );
        if (!employeeNames.isEmpty() && StrUtil.isNotBlank(employeeNames.get(0))) {
            return employeeNames.get(0).trim();
        }

        return null;
    }

    private String resolveOperatorName(Long userId, Map<Long, String> cache) {
        if (userId == null) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        String operatorName = resolveOperatorName(userId);
        cache.put(userId, operatorName);
        return operatorName;
    }

    @lombok.Getter
    @RequiredArgsConstructor
    private static final class ImportMatchContext {

        private final Map<String, List<Supplier>> suppliersByCode;

        private final Map<String, List<Supplier>> suppliersByUnifiedCreditCode;
    }

    @lombok.Getter
    @RequiredArgsConstructor
    private static final class ImportMatchResult {

        private final Supplier target;

        private final String errorMessage;

        private static ImportMatchResult success(Supplier target) {
            return new ImportMatchResult(target, null);
        }

        private static ImportMatchResult error(String errorMessage) {
            return new ImportMatchResult(null, errorMessage);
        }
    }

    @lombok.Getter
    @RequiredArgsConstructor
    private static final class ImportSupplierTypeOption {

        private final Long id;

        private final String dictCode;

        private final String dictName;

        private final String dictValue;

        private String getStoredValue() {
            return StrUtil.blankToDefault(dictValue, dictName);
        }
    }
}
