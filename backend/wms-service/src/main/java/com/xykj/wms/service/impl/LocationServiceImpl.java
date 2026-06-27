package com.xykj.wms.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.LocationCreateDTO;
import com.xykj.wms.dto.LocationImportDTO;
import com.xykj.wms.dto.LocationImportErrorDTO;
import com.xykj.wms.dto.LocationImportResultDTO;
import com.xykj.wms.dto.LocationQueryDTO;
import com.xykj.wms.dto.LocationUpdateDTO;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.WarehouseMapper;
import com.xykj.wms.service.LocationService;
import com.xykj.wms.service.support.LocationDeleteValidator;
import com.xykj.wms.service.support.WarehouseStatusRefreshService;
import com.xykj.wms.vo.LocationVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceImpl implements LocationService {

    private static final int MAX_IMPORT_ROWS = 5000;
    private static final long MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024L;
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final String[] IMPORT_TEMPLATE_HEADERS = {
            "组织编码", "所属仓库编码", "仓位编码", "仓位名称", "仓位类型", "区域编码", "货架编码", "货位编码", "状态", "仓位最大容量"
    };
    private static final int[] IMPORT_TEMPLATE_WIDTHS = {18, 18, 18, 22, 14, 16, 16, 16, 12, 14};
    private static final String[] IMPORT_TEMPLATE_SAMPLE = {"ORG001", "WH001", "KW-A-02-01", "a区2货架1号", "存储仓位", "A", "2", "1", "可用", "100"};
    private static final String[] EXPORT_HEADERS = {
            "仓库编码", "仓位编码", "仓位名称", "仓位类型", "区域编码", "货架编码", "货位编码", "状态", "仓位最大容量", "已用容量", "创建时间", "更新时间"
    };
    private static final int[] EXPORT_WIDTHS = {18, 18, 22, 14, 16, 16, 16, 12, 14, 14, 22, 22};
    private static final String IMPORT_ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/location-import-errors/";
    private static final DateTimeFormatter EXPORT_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final LocationMapper  locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final LocationDeleteValidator locationDeleteValidator;
    private final WarehouseStatusRefreshService warehouseStatusRefreshService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<LocationVO> list(LocationQueryDTO query) {
        Page<LocationVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        locationMapper.selectLocationPage(page, query);
        return PageResult.of(page);
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("仓位导入模板");
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sampleStyle = createSampleStyle(workbook);

            int rowNum = 0;
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(34);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】首行红色数据为示例占位，导入时会自动跳过。仅支持 .xlsx，单次最多 5000 行。组织编码必须能匹配系统组织；所属仓库编码必须存在且属于对应组织。仓位编码与层级编码在同仓库内唯一。状态支持可用、占用、维护中（也兼容 available、occupied、maintenance），留空默认可用。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }

            String[] sample = IMPORT_TEMPLATE_SAMPLE;
            Row sampleRow = sheet.createRow(rowNum);
            for (int i = 0; i < sample.length; i++) {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(sample[i]);
                cell.setCellStyle(sampleStyle);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓位导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("下载仓位导入模板失败", e);
            throw BizException.badRequest("下载仓位导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LocationImportResultDTO importLocations(MultipartFile file) {
        validateImportFile(file);
        try {
            List<LocationImportDTO> rawList = EasyExcel.read(file.getInputStream())
                    .head(LocationImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();
            if (rawList == null || rawList.isEmpty()) {
                throw BizException.badRequest("导入文件为空");
            }
            if (rawList.size() > MAX_IMPORT_ROWS) {
                throw BizException.badRequest("单次导入最多支持5000行");
            }

            List<LocationImportDTO> importRows = new ArrayList<>();
            for (int i = 0; i < rawList.size(); i++) {
                LocationImportDTO row = rawList.get(i);
                row.setRowNumber(i + 3);
                if (isSampleRow(row) || isEmptyRow(row)) {
                    continue;
                }
                importRows.add(row);
            }
            if (importRows.isEmpty()) {
                return new LocationImportResultDTO(0, 0, 0, false, List.of(), null);
            }
            if (importRows.size() > MAX_IMPORT_ROWS) {
                throw BizException.badRequest("单次导入最多支持5000行");
            }

            Set<String> seenLocationCodes = new LinkedHashSet<>();
            Set<String> seenHierarchyCodes = new LinkedHashSet<>();
            List<LocationImportErrorDTO> errors = new ArrayList<>();
            List<LocationImportDTO> errorRows = new ArrayList<>();
            int successCount = 0;

            for (LocationImportDTO row : importRows) {
                ValidationError validationError = validateImportRow(row, seenLocationCodes, seenHierarchyCodes);
                if (validationError != null) {
                    appendImportError(row, validationError, errors, errorRows);
                    continue;
                }

                Warehouse warehouse = resolveWarehouse(row.getOrgCode(), row.getWarehouseCode());
                if (warehouse == null) {
                    ValidationError warehouseError = resolveWarehouseImportError(row.getOrgCode(), row.getWarehouseCode());
                    appendImportError(row, warehouseError, errors, errorRows);
                    continue;
                }

                try {
                    checkImportLocationUniqueness(row, warehouse.getId());
                    Location location = new Location();
                    location.setWarehouseId(warehouse.getId());
                    location.setLocationCode(row.getLocationCode());
                    location.setLocationName(row.getLocationName());
                    location.setLocationType(row.getLocationType());
                    location.setRegionCode(row.getRegionCode());
                    location.setShelfCode(row.getShelfCode());
                    location.setSlotCode(row.getSlotCode());
                    location.setStatus(StrUtil.blankToDefault(row.getStatus(), "available"));
                    location.setCapacity(row.getCapacity());
                    location.setUsedCapacity(BigDecimal.ZERO);
                    locationMapper.insert(location);
                    successCount++;
                } catch (BizException ex) {
                    log.warn("导入仓位业务校验失败，行号：{}，错误：{}", row.getRowNumber(), ex.getMessage());
                    appendImportError(row, mapBizExceptionToImportError(ex), errors, errorRows);
                } catch (Exception ex) {
                    log.error("导入仓位失败，行号：{}，错误：{}", row.getRowNumber(), ex.getMessage(), ex);
                    appendImportError(row, new ValidationError("row", resolveImportExceptionMessage(ex)), errors, errorRows);
                }
            }

            String errorFileName = errorRows.isEmpty() ? null : generateImportErrorFile(errorRows);
            return new LocationImportResultDTO(
                    successCount,
                    errorRows.size(),
                    importRows.size(),
                    !errorRows.isEmpty() && successCount > 0,
                    errors,
                    errorFileName
            );
        } catch (IOException e) {
            log.error("读取仓位导入文件失败", e);
            throw BizException.badRequest("读取仓位导入文件失败");
        }
    }

    @Override
    @DataScope
    public void exportLocations(LocationQueryDTO query, HttpServletResponse response) {
        if (query.getWarehouseId() == null) {
            throw BizException.badRequest("请选择导出仓库");
        }
        validateAccessibleWarehouse(query.getWarehouseId(), "导出");
        List<LocationVO> locations = locationMapper.selectLocationExportList(query);
        if (locations.size() > MAX_EXPORT_ROWS) {
            throw BizException.badRequest("当前筛选结果超过5000条，请缩小范围后重试");
        }
        String format = normalizeExportFormat(query.getFormat());
        if ("csv".equals(format)) {
            exportCsv(locations, response);
            return;
        }
        exportXlsx(locations, response);
    }

    @Override
    public void downloadImportErrorFile(String fileName, HttpServletResponse response) {
        try {
            File file = resolveImportErrorFile(fileName);
            if (!file.exists()) {
                throw BizException.notFound("错误文件不存在或已过期");
            }
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Content-Disposition", "attachment;filename=" + file.getName());
            try (FileInputStream inputStream = new FileInputStream(file)) {
                inputStream.transferTo(response.getOutputStream());
            }
        } catch (IOException e) {
            log.error("下载仓位导入错误文件失败", e);
            throw BizException.badRequest("下载仓位导入错误文件失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(LocationCreateDTO dto) {
        Warehouse warehouse = validateAccessibleWarehouse(dto.getWarehouseId(), "创建");
        checkLocationCodeUnique(dto.getLocationCode(), warehouse.getId(), null);
        checkHierarchyUnique(warehouse.getId(), dto.getRegionCode(), dto.getShelfCode(), dto.getSlotCode(), null);

        Location loc = new Location();
        BeanUtil.copyProperties(dto, loc);
        loc.setWarehouseId(warehouse.getId());
        if (StrUtil.isBlank(loc.getStatus())) loc.setStatus("available");
        if (loc.getUsedCapacity() == null) loc.setUsedCapacity(BigDecimal.ZERO);

        locationMapper.insert(loc);
        warehouseStatusRefreshService.refreshWarehouse(loc.getWarehouseId());
        log.info("新增仓位: id={}, code={}", loc.getId(), loc.getLocationCode());
        return loc.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, LocationUpdateDTO dto) {
        Location loc = getLocationById(id);
        Warehouse warehouse = validateAccessibleWarehouse(loc.getWarehouseId(), "更新");
        if (StrUtil.isNotBlank(dto.getLocationCode()) &&
                !dto.getLocationCode().equals(loc.getLocationCode())) {
            checkLocationCodeUnique(dto.getLocationCode(), warehouse.getId(), id);
        }
        checkHierarchyUnique(
                warehouse.getId(),
                StrUtil.blankToDefault(dto.getRegionCode(), loc.getRegionCode()),
                StrUtil.blankToDefault(dto.getShelfCode(), loc.getShelfCode()),
                StrUtil.blankToDefault(dto.getSlotCode(), loc.getSlotCode()),
                id
        );
        validateStatusChange(loc, dto);
        Long currentVersion = loc.getVersion();
        BeanUtil.copyProperties(dto, loc, true);
        int updated = locationMapper.updateWithVersion(loc, currentVersion);
        if (updated == 0) {
            throw BizException.conflict("数据已被他人修改，请刷新后重试");
        }
        warehouseStatusRefreshService.refreshWarehouse(loc.getWarehouseId());
        log.info("更新仓位: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Location location = getLocationById(id);
        locationDeleteValidator.validateDelete(id);
        locationMapper.deleteById(id);
        warehouseStatusRefreshService.refreshWarehouse(location.getWarehouseId());
        log.info("删除仓位: id={}", id);
    }

    private Location getLocationById(Long id) {
        Location loc = locationMapper.selectById(id);
        if (loc == null) throw BizException.notFound("仓位不存在");
        return loc;
    }

    private void validateStatusChange(Location currentLocation, LocationUpdateDTO dto) {
        if (StrUtil.isBlank(dto.getStatus()) || dto.getStatus().equals(currentLocation.getStatus())) {
            return;
        }

        if (Set.of("inactive", "archived").contains(dto.getStatus())) {
            BigDecimal inventoryQuantity = locationMapper.selectLocationInventoryQuantity(currentLocation.getId());
            if (inventoryQuantity != null && inventoryQuantity.compareTo(BigDecimal.ZERO) > 0) {
                throw BizException.validationFailed("该仓位仍有库存，无法停用或归档");
            }
            return;
        }

        boolean occupied = warehouseStatusRefreshService.hasCurrentLocationOccupancy(currentLocation.getId());
        if (occupied && "available".equals(dto.getStatus())) {
            throw BizException.validationFailed("该仓位存在待处理出库/入库/盘点业务或库存占用，无法改为可用");
        }
        if (occupied && "maintenance".equals(dto.getStatus())) {
            throw BizException.validationFailed("该仓位存在待处理出库/入库/盘点业务或库存占用，请先解除占用后再改为维护中");
        }
    }

    private void checkImportLocationUniqueness(LocationImportDTO row, Long warehouseId) {
        if (locationMapper.existsLocationCodeInWarehouse(warehouseId, row.getLocationCode())) {
            throw BizException.conflict("同仓库下仓位编码已存在");
        }
        if (locationMapper.existsHierarchyInWarehouse(warehouseId, row.getRegionCode(), row.getShelfCode(), row.getSlotCode())) {
            throw BizException.conflict("同仓库下仓位层级已存在");
        }
    }

    private void checkHierarchyUnique(Long warehouseId, String regionCode, String shelfCode, String slotCode, Long excludeId) {
        if (StrUtil.hasBlank(regionCode, shelfCode, slotCode)) {
            return;
        }
        Long count = locationMapper.selectCount(new LambdaQueryWrapper<Location>()
                .eq(Location::getDeleted, 0)
                .eq(Location::getWarehouseId, warehouseId)
                .eq(Location::getRegionCode, regionCode)
                .eq(Location::getShelfCode, shelfCode)
                .eq(Location::getSlotCode, slotCode)
                .ne(excludeId != null, Location::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("同仓库下仓位层级已存在");
        }
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("导入文件不能为空");
        }
        if (file.getSize() > MAX_IMPORT_FILE_SIZE) {
            throw BizException.badRequest("导入文件大小不能超过10MB");
        }
        String fileName = file.getOriginalFilename();
        String extension = StrUtil.subAfter(StrUtil.blankToDefault(fileName, ""), ".", true);
        if (!"xlsx".equalsIgnoreCase(extension)) {
            throw BizException.badRequest("仅支持 .xlsx 格式的导入文件");
        }
    }

    private boolean isSampleRow(LocationImportDTO row) {
        return isPlaceholderSample(row.getOrgCode())
                || isPlaceholderSample(row.getWarehouseCode())
                || isPlaceholderSample(row.getLocationCode())
                || isPlaceholderSample(row.getLocationName())
                || isPlaceholderSample(row.getRegionCode())
                || isPlaceholderSample(row.getShelfCode())
                || isPlaceholderSample(row.getSlotCode())
                || isPlaceholderSample(row.getStatus())
                || matchesTemplateSampleRow(row);
    }

    private boolean matchesTemplateSampleRow(LocationImportDTO row) {
        return StrUtil.equals(normalize(row.getOrgCode()), IMPORT_TEMPLATE_SAMPLE[0])
                && StrUtil.equals(normalize(row.getWarehouseCode()), IMPORT_TEMPLATE_SAMPLE[1])
                && StrUtil.equals(normalize(row.getLocationCode()), IMPORT_TEMPLATE_SAMPLE[2])
                && StrUtil.equals(normalize(row.getLocationName()), IMPORT_TEMPLATE_SAMPLE[3])
                && StrUtil.equals(normalize(row.getLocationType()), IMPORT_TEMPLATE_SAMPLE[4])
                && StrUtil.equals(normalize(row.getRegionCode()), IMPORT_TEMPLATE_SAMPLE[5])
                && StrUtil.equals(normalize(row.getShelfCode()), IMPORT_TEMPLATE_SAMPLE[6])
                && StrUtil.equals(normalize(row.getSlotCode()), IMPORT_TEMPLATE_SAMPLE[7])
                && StrUtil.equals(normalizeStatus(row.getStatus()), normalizeStatus(IMPORT_TEMPLATE_SAMPLE[8]))
                && row.getCapacity() != null
                && row.getCapacity().compareTo(new BigDecimal(IMPORT_TEMPLATE_SAMPLE[9])) == 0;
    }

    private boolean isEmptyRow(LocationImportDTO row) {
        return StrUtil.isAllBlank(
                normalize(row.getOrgCode()),
                normalize(row.getWarehouseCode()),
                normalize(row.getLocationCode()),
                normalize(row.getLocationName())
        );
    }

    private ValidationError validateImportRow(LocationImportDTO row, Set<String> seenLocationCodes, Set<String> seenHierarchyCodes) {
        row.setOrgCode(normalize(row.getOrgCode()));
        row.setWarehouseCode(normalize(row.getWarehouseCode()));
        row.setLocationCode(normalize(row.getLocationCode()));
        row.setLocationName(normalize(row.getLocationName()));
        row.setLocationType(normalize(row.getLocationType()));
        row.setRegionCode(normalize(row.getRegionCode()));
        row.setShelfCode(normalize(row.getShelfCode()));
        row.setSlotCode(normalize(row.getSlotCode()));
        row.setStatus(normalizeStatus(row.getStatus()));

        if (StrUtil.isBlank(row.getOrgCode())) return new ValidationError("orgCode", "组织编码不能为空");
        if (StrUtil.isBlank(row.getWarehouseCode())) return new ValidationError("warehouseCode", "所属仓库编码不能为空");
        if (StrUtil.isBlank(row.getLocationCode())) return new ValidationError("locationCode", "仓位编码不能为空");
        if (StrUtil.isBlank(row.getLocationName())) return new ValidationError("locationName", "仓位名称不能为空");
        if (StrUtil.isBlank(row.getRegionCode())) return new ValidationError("regionCode", "区域编码不能为空");
        if (StrUtil.isBlank(row.getShelfCode())) return new ValidationError("shelfCode", "货架编码不能为空");
        if (StrUtil.isBlank(row.getSlotCode())) return new ValidationError("slotCode", "货位编码不能为空");
        if (row.getLocationCode().length() > 50) return new ValidationError("locationCode", "仓位编码长度不能超过50个字符");
        if (row.getLocationName().length() > 50) return new ValidationError("locationName", "仓位名称长度不能超过50个字符");
        if (StrUtil.isNotBlank(row.getLocationType()) && row.getLocationType().length() > 50) return new ValidationError("locationType", "仓位类型长度不能超过50个字符");
        if (row.getRegionCode().length() > 50) return new ValidationError("regionCode", "区域编码长度不能超过50个字符");
        if (row.getShelfCode().length() > 50) return new ValidationError("shelfCode", "货架编码长度不能超过50个字符");
        if (row.getSlotCode().length() > 50) return new ValidationError("slotCode", "货位编码长度不能超过50个字符");
        if (StrUtil.isNotBlank(row.getStatus()) && !Set.of("available", "occupied", "maintenance").contains(row.getStatus())) {
            return new ValidationError("status", "状态仅支持 可用、占用、维护中（或 available、occupied、maintenance）");
        }
        String duplicateLocationKey = row.getOrgCode() + "::" + row.getWarehouseCode() + "::" + row.getLocationCode();
        if (!seenLocationCodes.add(duplicateLocationKey)) {
            return new ValidationError("locationCode", "同一导入文件中同仓库仓位编码重复：" + row.getLocationCode());
        }
        String hierarchyKey = row.getOrgCode() + "::" + row.getWarehouseCode() + "::" + row.getRegionCode() + "::" + row.getShelfCode() + "::" + row.getSlotCode();
        if (!seenHierarchyCodes.add(hierarchyKey)) {
            return new ValidationError("slotCode", "同一导入文件中同仓库仓位层级重复：" + row.getRegionCode() + "-" + row.getShelfCode() + "-" + row.getSlotCode());
        }
        return null;
    }

    private void appendImportError(LocationImportDTO row, ValidationError validationError, List<LocationImportErrorDTO> errors, List<LocationImportDTO> errorRows) {
        row.setErrorField(validationError.field());
        row.setErrorReason(validationError.reason());
        errors.add(new LocationImportErrorDTO(row.getRowNumber(), validationError.field(), validationError.reason()));
        errorRows.add(row);
    }

    private ValidationError resolveWarehouseImportError(String orgCode, String warehouseCode) {
        Long orgId = resolveOrgIdByCode(orgCode);
        if (orgId == null) {
            return new ValidationError("orgCode", "组织编码无效，未匹配到系统组织");
        }
        Warehouse warehouse = locationMapper.selectWarehouseByCode(orgId, warehouseCode);
        if (warehouse == null) {
            return new ValidationError("warehouseCode", "所属仓库不存在");
        }
        if (isWarehouseForbiddenStatus(warehouse.getStatus())) {
            return new ValidationError("warehouseCode", "所属仓库当前状态不允许新增或修改仓位");
        }
        return null;
    }

    private Warehouse resolveWarehouse(String orgCode, String warehouseCode) {
        Long orgId = resolveOrgIdByCode(orgCode);
        if (orgId == null) {
            return null;
        }
        Warehouse warehouse = locationMapper.selectWarehouseByCode(orgId, warehouseCode);
        if (warehouse == null || isWarehouseForbiddenStatus(warehouse.getStatus())) {
            return null;
        }
        return warehouse;
    }

    private Long resolveOrgIdByCode(String orgCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM sys_organization WHERE org_code = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                orgCode
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private File resolveImportErrorFile(String fileName) {
        if (StrUtil.isBlank(fileName) || !fileName.matches("location_import_errors_\\d+\\.xlsx")) {
            throw BizException.badRequest("错误文件名非法");
        }
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR).getCanonicalFile();
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建仓位导入错误文件目录失败: {}", dir.getAbsolutePath());
            }
            File file = new File(dir, fileName).getCanonicalFile();
            String dirPath = dir.getPath() + File.separator;
            if (!file.getPath().startsWith(dirPath)) {
                throw BizException.badRequest("错误文件名非法");
            }
            return file;
        } catch (IOException ex) {
            throw BizException.badRequest("错误文件名非法");
        }
    }

    private boolean isPlaceholderSample(String value) {
        String normalized = normalize(value);
        return normalized != null && normalized.startsWith("#{") && normalized.endsWith("}");
    }

    private String resolveImportExceptionMessage(Exception ex) {
        if (ex instanceof BizException bizException) {
            return bizException.getMessage();
        }
        Throwable cause = ex.getCause();
        return cause != null && StrUtil.isNotBlank(cause.getMessage()) ? cause.getMessage() : "导入失败";
    }

    private ValidationError mapBizExceptionToImportError(BizException ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("仓位编码")) {
            return new ValidationError("locationCode", message);
        }
        if (message != null && message.contains("仓位层级")) {
            return new ValidationError("slotCode", message);
        }
        return new ValidationError("row", message == null ? "导入失败" : message);
    }

    private String generateImportErrorFile(List<LocationImportDTO> errorRows) {
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建仓位导入错误文件目录失败: {}", dir.getAbsolutePath());
            }
            String fileName = "location_import_errors_" + System.currentTimeMillis() + ".xlsx";
            try (Workbook workbook = new XSSFWorkbook(); FileOutputStream outputStream = new FileOutputStream(IMPORT_ERROR_FILE_DIR + fileName)) {
                Sheet sheet = workbook.createSheet("导入失败数据");
                CellStyle tipStyle = createTipStyle(workbook);
                CellStyle headerStyle = createHeaderStyle(workbook);
                CellStyle errorStyle = createSampleStyle(workbook);

                int rowNum = 0;
                Row tipRow = sheet.createRow(rowNum++);
                Cell tipCell = tipRow.createCell(0);
                tipCell.setCellValue("【说明】以下数据导入失败，请根据失败原因修正后重新导入。错误明细已包含行号、字段、原因。 ");
                tipCell.setCellStyle(tipStyle);
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length + 2));

                Row headerRow = sheet.createRow(rowNum++);
                Cell rowNumberCell = headerRow.createCell(0);
                rowNumberCell.setCellValue("行号");
                rowNumberCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(0, 10 * 256);
                for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i + 1);
                    cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i + 1, IMPORT_TEMPLATE_WIDTHS[i] * 256);
                }
                Cell fieldCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 1);
                fieldCell.setCellValue("错误字段");
                fieldCell.setCellStyle(headerStyle);
                Cell reasonCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 2);
                reasonCell.setCellValue("失败原因");
                reasonCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 1, 18 * 256);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 2, 36 * 256);

                for (LocationImportDTO row : errorRows) {
                    Row dataRow = sheet.createRow(rowNum++);
                    String[] values = {
                            String.valueOf(row.getRowNumber()),
                            blankToEmpty(row.getOrgCode()),
                            blankToEmpty(row.getWarehouseCode()),
                            blankToEmpty(row.getLocationCode()),
                            blankToEmpty(row.getLocationName()),
                            blankToEmpty(row.getLocationType()),
                            blankToEmpty(row.getRegionCode()),
                            blankToEmpty(row.getShelfCode()),
                            blankToEmpty(row.getSlotCode()),
                            blankToEmpty(row.getStatus()),
                            row.getCapacity() == null ? "" : row.getCapacity().stripTrailingZeros().toPlainString(),
                            blankToEmpty(row.getErrorField()),
                            blankToEmpty(row.getErrorReason())
                    };
                    for (int i = 0; i < values.length; i++) {
                        Cell cell = dataRow.createCell(i);
                        cell.setCellValue(values[i]);
                        cell.setCellStyle(errorStyle);
                    }
                }
                workbook.write(outputStream);
            }
            return fileName;
        } catch (Exception ex) {
            log.error("生成仓位导入错误文件失败", ex);
            return null;
        }
    }

    private void exportXlsx(List<LocationVO> locations, HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("仓位信息");
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int rowNum = 0;
            Row tipRow = sheet.createRow(rowNum++);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】当前导出结果已按页面仓库筛选条件及仓位筛选条件处理。 ");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, EXPORT_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EXPORT_WIDTHS[i] * 256);
            }

            for (LocationVO location : locations) {
                Row row = sheet.createRow(rowNum++);
                String[] values = toExportValues(location);
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓位信息_" + LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("导出仓位失败", e);
            throw BizException.badRequest("导出仓位失败");
        }
    }

    private void exportCsv(List<LocationVO> locations, HttpServletResponse response) {
        try {
            response.setContentType("text/csv");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓位信息_" + LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".csv");
            StringBuilder builder = new StringBuilder();
            builder.append('﻿');
            builder.append(String.join(",", EXPORT_HEADERS)).append("\n");
            for (LocationVO location : locations) {
                String[] values = toExportValues(location);
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) {
                        builder.append(',');
                    }
                    builder.append(escapeCsv(values[i]));
                }
                builder.append("\n");
            }
            response.getOutputStream().write(builder.toString().getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        } catch (IOException e) {
            log.error("导出仓位CSV失败", e);
            throw BizException.badRequest("导出仓位失败");
        }
    }

    private String[] toExportValues(LocationVO location) {
        return new String[]{
                blankToEmpty(resolveWarehouseCode(location.getWarehouseId())),
                blankToEmpty(location.getLocationCode()),
                blankToEmpty(location.getLocationName()),
                blankToEmpty(location.getLocationType()),
                blankToEmpty(location.getRegionCode()),
                blankToEmpty(location.getShelfCode()),
                blankToEmpty(location.getSlotCode()),
                blankToEmpty(location.getStatus()),
                decimalToString(location.getCapacity()),
                decimalToString(location.getUsedCapacity()),
                location.getCreatedAt() == null ? "" : location.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                location.getUpdatedAt() == null ? "" : location.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        };
    }

    private String resolveWarehouseCode(Long warehouseId) {
        if (warehouseId == null) {
            return "";
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        return warehouse == null ? "" : warehouse.getWarehouseCode();
    }

    private Warehouse validateAccessibleWarehouse(Long warehouseId, String action) {
        if (warehouseId == null) {
            throw BizException.badRequest("请选择所属仓库");
        }
        Warehouse warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null) {
            throw BizException.notFound("所属仓库不存在");
        }
        Long currentOrgId = UserContext.getOrgId();
        if (currentOrgId != null && warehouse.getOrgId() != null && !currentOrgId.equals(warehouse.getOrgId())) {
            throw BizException.validationFailed("所属仓库不属于当前组织");
        }
        if (isWarehouseForbiddenStatus(warehouse.getStatus())) {
            throw BizException.validationFailed("所属仓库当前状态不允许新增或修改仓位");
        }
        return warehouse;
    }

    private boolean isWarehouseForbiddenStatus(String status) {
        return "inactive".equals(status) || "archived".equals(status);
    }

    private String normalize(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return null;
        }
        return switch (normalized) {
            case "可用" -> "available";
            case "占用" -> "occupied";
            case "维护中" -> "maintenance";
            default -> normalized.toLowerCase(Locale.ROOT);
        };
    }

    private String normalizeExportFormat(String format) {
        String normalized = normalize(format);
        if (normalized == null) {
            return "xlsx";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (!"xlsx".equals(lower) && !"csv".equals(lower)) {
            throw BizException.badRequest("导出格式仅支持 xlsx 或 csv");
        }
        return lower;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String escapeCsv(String value) {
        String safe = blankToEmpty(value).replace("\"", "\"\"");
        return "\"" + safe + "\"";
    }

    private CellStyle createTipStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.DARK_RED.getIndex());
        font.setFontName("微软雅黑");
        style.setFont(font);
        return style;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontName("微软雅黑");
        style.setFont(font);
        return style;
    }

    private CellStyle createSampleStyle(Workbook workbook) {
        CellStyle style = createDataStyle(workbook);
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setFontName("微软雅黑");
        style.setFont(font);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        style.setFont(font);
        return style;
    }

    private record ValidationError(String field, String reason) {
    }

    private void checkLocationCodeUnique(String code, Long warehouseId, Long excludeId) {
        Long count = locationMapper.selectCount(
                new LambdaQueryWrapper<Location>()
                        .eq(Location::getLocationCode, code)
                        .eq(Location::getWarehouseId, warehouseId)
                        .ne(excludeId != null, Location::getId, excludeId));
        if (count > 0) throw BizException.conflict("仓位编码已存在");
    }
}
