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
import com.xykj.wms.dto.WarehouseCreateDTO;
import com.xykj.wms.dto.WarehouseImportDTO;
import com.xykj.wms.dto.WarehouseImportErrorDTO;
import com.xykj.wms.dto.WarehouseImportResultDTO;
import com.xykj.wms.dto.WarehouseQueryDTO;
import com.xykj.wms.dto.WarehouseUpdateDTO;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.mapper.LocationMapper;
import com.xykj.wms.mapper.WarehouseMapper;
import com.xykj.wms.service.WarehouseService;
import com.xykj.wms.service.support.WarehouseDeleteValidator;
import com.xykj.wms.vo.WarehouseStatisticsVO;
import com.xykj.wms.vo.WarehouseVO;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseServiceImpl implements WarehouseService {

    private static final int MAX_IMPORT_ROWS = 5000;
    private static final long MAX_IMPORT_FILE_SIZE = 10 * 1024 * 1024L;
    private static final int MAX_EXPORT_ROWS = 5000;
    private static final String[] IMPORT_TEMPLATE_HEADERS = {
            "组织编码", "仓库编码", "仓库名称", "仓库类型", "仓库位置", "负责人", "联系方式", "最大容量", "状态"
    };
    private static final int[] IMPORT_TEMPLATE_WIDTHS = {18, 18, 22, 14, 28, 14, 16, 12, 12};
    private static final String[] EXPORT_HEADERS = {
            "仓库编码", "仓库名称", "仓库类型", "容量", "容量单位", "地址", "负责人", "联系电话", "状态", "备注", "仓位总数", "已占用", "空闲", "创建时间", "更新时间"
    };
    private static final int[] EXPORT_WIDTHS = {18, 22, 14, 12, 12, 28, 14, 16, 12, 28, 12, 12, 12, 22, 22};
    private static final String IMPORT_ERROR_FILE_DIR = System.getProperty("java.io.tmpdir") + "/warehouse-import-errors/";
    private static final DateTimeFormatter EXPORT_FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final WarehouseDeleteValidator warehouseDeleteValidator;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @DataScope
    public PageResult<WarehouseVO> list(WarehouseQueryDTO query) {
        Page<WarehouseVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        warehouseMapper.selectWarehousePage(page, query);
        List<WarehouseVO> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return PageResult.of(page);
        }

        // 批量查询所有仓库的仓位中绑定的传感器
        List<Long> warehouseIds = records.stream().map(WarehouseVO::getId).toList();
        List<Location> allLocations = locationMapper.selectList(
                new LambdaQueryWrapper<Location>()
                        .in(Location::getWarehouseId, warehouseIds)
                        .eq(Location::getDeleted, 0)
                        .isNotNull(Location::getSensorDeviceId));

        if (allLocations.isEmpty()) {
            return PageResult.of(page);
        }

        // 按仓库分组
        Map<Long, List<Location>> locationsByWarehouse = allLocations.stream()
                .collect(Collectors.groupingBy(Location::getWarehouseId));

        // 收集所有传感器 ID
        List<Long> allSensorIds = allLocations.stream()
                .map(Location::getSensorDeviceId)
                .distinct()
                .toList();

        // 批量查询最新温湿度
        Map<Long, LatestReading> latestTemp = queryLatestData(allSensorIds, "temperature");
        Map<Long, LatestReading> latestHumidity = queryLatestData(allSensorIds, "humidity");

        // 按 warehouseId 构建 sensorDeviceId → Location 映射
        for (WarehouseVO vo : records) {
            List<Location> locs = locationsByWarehouse.get(vo.getId());
            if (locs == null || locs.isEmpty()) {
                continue;
            }

            // 取该仓库所有仓位的最新温湿度（取最新的那条）
            BigDecimal latestTempValue = null;
            BigDecimal latestHumidityValue = null;

            for (Location loc : locs) {
                LatestReading tempReading = latestTemp.get(loc.getSensorDeviceId());
                LatestReading humidityReading = latestHumidity.get(loc.getSensorDeviceId());

                if (tempReading != null && (latestTempValue == null
                        || tempReading.value().compareTo(latestTempValue) != 0
                        && tempReading.collectedAt() != null)) {
                    latestTempValue = tempReading.value();
                }
                if (humidityReading != null && (latestHumidityValue == null
                        || humidityReading.value().compareTo(latestHumidityValue) != 0
                        && humidityReading.collectedAt() != null)) {
                    latestHumidityValue = humidityReading.value();
                }
            }

            vo.setCurrentTemperature(latestTempValue);
            vo.setCurrentHumidity(latestHumidityValue);
        }

        return PageResult.of(page);
    }

    @Override
    @DataScope
    public WarehouseStatisticsVO getStatistics(WarehouseQueryDTO query) {
        LambdaQueryWrapper<Warehouse> wrapper = new LambdaQueryWrapper<>();
        applyWarehouseScope(wrapper, query);

        Long warehouseTotal = warehouseMapper.selectCount(wrapper);
        Long activeCount = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, "active")
                .eq(Warehouse::getDeleted, 0)
                .eq(query != null && query.getOrgId() != null, Warehouse::getOrgId, query.getOrgId())
                .in(query != null && query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Warehouse::getOrgId, query.getOrgIds())
                .isNull(query != null && query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Warehouse::getId));
        Long maintenanceCount = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getStatus, "maintenance")
                .eq(Warehouse::getDeleted, 0)
                .eq(query != null && query.getOrgId() != null, Warehouse::getOrgId, query.getOrgId())
                .in(query != null && query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Warehouse::getOrgId, query.getOrgIds())
                .isNull(query != null && query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Warehouse::getId));

        List<Long> warehouseIds = warehouseMapper.selectList(wrapper).stream()
                .map(Warehouse::getId)
                .toList();

        Long positionTotal = 0L;
        if (!warehouseIds.isEmpty()) {
            positionTotal = locationMapper.selectCount(new LambdaQueryWrapper<Location>()
                    .in(Location::getWarehouseId, warehouseIds)
                    .eq(Location::getDeleted, 0));
        }

        WarehouseStatisticsVO vo = new WarehouseStatisticsVO();
        vo.setWarehouseTotal(warehouseTotal == null ? 0L : warehouseTotal);
        vo.setActiveCount(activeCount == null ? 0L : activeCount);
        vo.setMaintenanceCount(maintenanceCount == null ? 0L : maintenanceCount);
        vo.setPositionTotal(positionTotal == null ? 0L : positionTotal);
        return vo;
    }

    @Override
    public void downloadImportTemplate(HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("仓库导入模板");
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle sampleStyle = createSampleStyle(workbook);

            int rowNum = 0;
            Row tipRow = sheet.createRow(rowNum++);
            tipRow.setHeightInPoints(34);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】首行红色数据为示例占位，导入时会自动跳过。仅支持 .xlsx，单次最多 5000 行。组织编码必须能匹配系统组织；仓库编码按同组织内唯一校验。状态支持启用、停用、维护中（也兼容 active、inactive、maintenance），留空默认启用。");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, IMPORT_TEMPLATE_WIDTHS[i] * 256);
            }

            String[] sample = {"ORG001", "WH001", "一号常温仓", "常温仓", "一层东侧收货区", "张三", "13800138000", "500", "启用"};
            Row sampleRow = sheet.createRow(rowNum);
            for (int i = 0; i < sample.length; i++) {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(sample[i]);
                cell.setCellStyle(sampleStyle);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓库导入模板", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("下载仓库导入模板失败", e);
            throw BizException.badRequest("下载仓库导入模板失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WarehouseImportResultDTO importWarehouses(MultipartFile file) {
        validateImportFile(file);
        try {
            List<WarehouseImportDTO> rawList = EasyExcel.read(file.getInputStream())
                    .head(WarehouseImportDTO.class)
                    .sheet()
                    .headRowNumber(2)
                    .doReadSync();
            if (rawList == null || rawList.isEmpty()) {
                throw BizException.badRequest("导入文件为空");
            }
            if (rawList.size() > MAX_IMPORT_ROWS) {
                throw BizException.badRequest("单次导入最多支持5000行");
            }

            List<WarehouseImportDTO> importRows = new ArrayList<>();
            for (int i = 0; i < rawList.size(); i++) {
                WarehouseImportDTO row = rawList.get(i);
                row.setRowNumber(i + 3);
                if (isSampleRow(row) || isEmptyRow(row)) {
                    continue;
                }
                importRows.add(row);
            }
            if (importRows.isEmpty()) {
                return new WarehouseImportResultDTO(0, 0, 0, false, List.of(), null);
            }
            if (importRows.size() > MAX_IMPORT_ROWS) {
                throw BizException.badRequest("单次导入最多支持5000行");
            }

            Set<String> seenOrgWarehouseCodes = new LinkedHashSet<>();
            List<WarehouseImportErrorDTO> errors = new ArrayList<>();
            List<WarehouseImportDTO> errorRows = new ArrayList<>();
            int successCount = 0;
            Long tenantId = resolveCurrentTenantId();

            for (WarehouseImportDTO row : importRows) {
                ValidationError validationError = validateImportRow(row, seenOrgWarehouseCodes);
                if (validationError != null) {
                    appendImportError(row, validationError, errors, errorRows);
                    continue;
                }

                Long orgId = resolveOrgIdByCode(row.getOrgCode());
                if (orgId == null) {
                    appendImportError(row, new ValidationError("orgCode", "组织编码无效，未匹配到组织"), errors, errorRows);
                    continue;
                }

                try {
                    checkCodeUnique(row.getWarehouseCode(), orgId, null);
                    Warehouse warehouse = new Warehouse();
                    warehouse.setOrgId(orgId);
                    warehouse.setTenantId(tenantId);
                    warehouse.setWarehouseCode(row.getWarehouseCode());
                    warehouse.setWarehouseName(row.getWarehouseName());
                    warehouse.setWarehouseType(row.getWarehouseType());
                    warehouse.setCapacity(row.getCapacity());
                    warehouse.setAddress(row.getAddress());
                    warehouse.setManagerName(row.getManagerName());
                    warehouse.setManagerPhone(row.getManagerPhone());
                    warehouse.setStatus(StrUtil.blankToDefault(row.getStatus(), "active"));
                    warehouseMapper.insert(warehouse);
                    successCount++;
                } catch (Exception ex) {
                    log.error("导入仓库失败，行号：{}，错误：{}", row.getRowNumber(), ex.getMessage(), ex);
                    appendImportError(row, new ValidationError("row", resolveImportExceptionMessage(ex)), errors, errorRows);
                }
            }

            String errorFileName = errorRows.isEmpty() ? null : generateImportErrorFile(errorRows);
            return new WarehouseImportResultDTO(
                    successCount,
                    errorRows.size(),
                    importRows.size(),
                    !errorRows.isEmpty() && successCount > 0,
                    errors,
                    errorFileName
            );
        } catch (IOException e) {
            log.error("读取仓库导入文件失败", e);
            throw BizException.badRequest("读取仓库导入文件失败");
        }
    }

    @Override
    @DataScope
    public void exportWarehouses(WarehouseQueryDTO query, HttpServletResponse response) {
        validateExportScope(query);
        List<WarehouseVO> warehouses = warehouseMapper.selectWarehouseExportList(query);
        if (warehouses.size() > MAX_EXPORT_ROWS) {
            throw BizException.badRequest("当前筛选结果超过5000条，请缩小范围后重试");
        }
        String format = normalizeExportFormat(query.getFormat());
        if ("csv".equals(format)) {
            exportCsv(warehouses, response);
            return;
        }
        exportXlsx(warehouses, response);
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
            log.error("下载仓库导入错误文件失败", e);
            throw BizException.badRequest("下载仓库导入错误文件失败");
        }
    }

    @Override
    public WarehouseVO getDetail(Long id) {
        Warehouse w = getWarehouseById(id);
        WarehouseVO vo = new WarehouseVO();
        BeanUtil.copyProperties(w, vo);

        // 查询该仓库下所有仓位（含 sensorDeviceId）
        List<Location> locations = locationMapper.selectList(
                new LambdaQueryWrapper<Location>()
                        .eq(Location::getWarehouseId, id)
                        .eq(Location::getDeleted, 0));

        if (locations.isEmpty()) {
            return vo;
        }

        // 收集绑定了传感器的仓位（一个传感器可能绑定多个仓位）
        Map<Long, List<Location>> sensorLocationMap = locations.stream()
                .filter(loc -> loc.getSensorDeviceId() != null)
                .collect(Collectors.groupingBy(Location::getSensorDeviceId));

        if (sensorLocationMap.isEmpty()) {
            return vo;
        }

        List<Long> sensorIds = new ArrayList<>(sensorLocationMap.keySet());

        // 批量查询所有传感器的最新温度和湿度（含采集时间）
        Map<Long, LatestReading> latestTemp = queryLatestData(sensorIds, "temperature");
        Map<Long, LatestReading> latestHumidity = queryLatestData(sensorIds, "humidity");

        // 构建仓位传感器数据列表并计算状态
        String worstTempStatus = "normal";
        String worstHumidityStatus = "normal";
        List<WarehouseVO.LocationSensorData> sensorDataList = new ArrayList<>();

        for (Map.Entry<Long, List<Location>> entry : sensorLocationMap.entrySet()) {
            Long sensorId = entry.getKey();
            List<Location> boundLocations = entry.getValue();

            LatestReading tempReading = latestTemp.get(sensorId);
            LatestReading humidityReading = latestHumidity.get(sensorId);

            BigDecimal temp = tempReading != null ? tempReading.value() : null;
            BigDecimal humidity = humidityReading != null ? humidityReading.value() : null;

            LocalDateTime dataTime = latestOf(
                    tempReading != null ? tempReading.collectedAt() : null,
                    humidityReading != null ? humidityReading.collectedAt() : null);

            // 同一个传感器绑定的多个仓位，各自生成独立的监测数据行
            for (Location loc : boundLocations) {
                String tempStatus = calcStatus(temp,
                        loc.getTemperatureMin() != null ? loc.getTemperatureMin() : w.getTemperatureMin(),
                        loc.getTemperatureMax() != null ? loc.getTemperatureMax() : w.getTemperatureMax());
                String humidityStatus = calcStatus(humidity,
                        loc.getHumidityMin() != null ? loc.getHumidityMin() : w.getHumidityMin(),
                        loc.getHumidityMax() != null ? loc.getHumidityMax() : w.getHumidityMax());

                worstTempStatus = worstStatus(worstTempStatus, tempStatus);
                worstHumidityStatus = worstStatus(worstHumidityStatus, humidityStatus);

                WarehouseVO.LocationSensorData data = new WarehouseVO.LocationSensorData();
                data.setLocationId(loc.getId());
                data.setLocationName(loc.getLocationName());
                data.setCurrentTemperature(temp);
                data.setCurrentHumidity(humidity);
                data.setTempStatus(tempStatus);
                data.setHumidityStatus(humidityStatus);
                data.setDataCollectedAt(dataTime);
                sensorDataList.add(data);
            }
        }

        vo.setTempStatus(worstTempStatus);
        vo.setHumidityStatus(worstHumidityStatus);
        vo.setLocationSensorData(sensorDataList);
        return vo;
    }

    /** 传感器最新读数（数据值 + 采集时间） */
    private record LatestReading(BigDecimal value, LocalDateTime collectedAt) {}

    /**
     * 批量查询多个设备的最新数据值（含采集时间）
     */
    private Map<Long, LatestReading> queryLatestData(List<Long> deviceIds, String dataType) {
        if (deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = deviceIds.stream().map(id -> "?").collect(Collectors.joining(","));
        String sql = "SELECT ddl.device_id, ddl.data_value, ddl.collected_at FROM device_data_log ddl " +
                "INNER JOIN (" +
                "  SELECT device_id, MAX(collected_at) as max_time FROM device_data_log " +
                "  WHERE device_id IN (" + placeholders + ") AND data_type = ? " +
                "  GROUP BY device_id" +
                ") latest ON ddl.device_id = latest.device_id AND ddl.collected_at = latest.max_time " +
                "WHERE ddl.data_type = ?";

        List<Object> params = new ArrayList<>(deviceIds);
        params.add(dataType);
        params.add(dataType);

        Map<Long, LatestReading> result = new HashMap<>();
        jdbcTemplate.query(sql, params.toArray(), rs -> {
            result.put(rs.getLong("device_id"),
                    new LatestReading(rs.getBigDecimal("data_value"),
                            rs.getTimestamp("collected_at").toLocalDateTime()));
        });
        return result;
    }

    /**
     * 计算状态：alarm(超阈值) / warning(接近阈值) / normal
     */
    private String calcStatus(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null) {
            return "normal";
        }
        if (min != null && value.compareTo(min) < 0) {
            return "alarm";
        }
        if (max != null && value.compareTo(max) > 0) {
            return "alarm";
        }
        // 预警区间：距上界 10% 以内
        if (max != null && min != null) {
            BigDecimal range = max.subtract(min);
            BigDecimal warningLine = max.subtract(range.multiply(new BigDecimal("0.1")));
            if (value.compareTo(warningLine) >= 0) {
                return "warning";
            }
        }
        return "normal";
    }

    private String worstStatus(String current, String candidate) {
        if ("alarm".equals(candidate)) return "alarm";
        if ("warning".equals(candidate) && !"alarm".equals(current)) return "warning";
        return current;
    }

    /** 取两个时间戳中较新的那个 */
    private LocalDateTime latestOf(LocalDateTime a, LocalDateTime b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.isAfter(b) ? a : b;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WarehouseCreateDTO dto) {
        Long orgId = resolveCurrentOrgId();
        String warehouseCode = normalize(dto.getWarehouseCode());
        String warehouseName = normalize(dto.getWarehouseName());
        validateWarehouseCode(warehouseCode);
        validateWarehouseName(warehouseName);
        checkCodeUnique(warehouseCode, orgId, null);
        checkNameUnique(warehouseName, orgId, null);
        Warehouse w = new Warehouse();
        BeanUtil.copyProperties(dto, w);
        w.setWarehouseCode(warehouseCode);
        w.setWarehouseName(warehouseName);
        if (StrUtil.isBlank(w.getStatus())) {
            w.setStatus("active");
        }
        w.setOrgId(orgId);
        w.setTenantId(resolveCurrentTenantId());
        warehouseMapper.insert(w);
        log.info("新增仓库: id={}, code={}", w.getId(), w.getWarehouseCode());
        return w.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, WarehouseUpdateDTO dto) {
        Warehouse w = getWarehouseById(id);
        String nextWarehouseCode = dto.getWarehouseCode() != null ? normalize(dto.getWarehouseCode()) : w.getWarehouseCode();
        String nextWarehouseName = dto.getWarehouseName() != null ? normalize(dto.getWarehouseName()) : w.getWarehouseName();
        validateWarehouseCode(nextWarehouseCode);
        validateWarehouseName(nextWarehouseName);
        if (dto.getWarehouseCode() != null && !nextWarehouseCode.equals(w.getWarehouseCode())) {
            checkCodeUnique(nextWarehouseCode, w.getOrgId(), id);
        }
        if (dto.getWarehouseName() != null && !nextWarehouseName.equals(w.getWarehouseName())) {
            checkNameUnique(nextWarehouseName, w.getOrgId(), id);
        }
        BeanUtil.copyProperties(dto, w, true);
        w.setWarehouseCode(nextWarehouseCode);
        w.setWarehouseName(nextWarehouseName);
        int updated = warehouseMapper.updateWithVersion(w, dto.getVersion());
        if (updated == 0) {
            throw BizException.conflict("数据已被他人修改，请刷新后重试");
        }
        log.info("更新仓库: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        getWarehouseById(id);
        warehouseDeleteValidator.validateDelete(id);
        warehouseMapper.deleteById(id);
        log.info("删除仓库: id={}", id);
    }

    private Warehouse getWarehouseById(Long id) {
        Warehouse w = warehouseMapper.selectById(id);
        if (w == null) {
            throw BizException.notFound("仓库不存在");
        }
        return w;
    }

    private void applyWarehouseScope(LambdaQueryWrapper<Warehouse> wrapper, WarehouseQueryDTO query) {
        if (query == null) {
            return;
        }
        wrapper.eq(Warehouse::getDeleted, 0)
                .eq(query.getOrgId() != null, Warehouse::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Warehouse::getOrgId, query.getOrgIds())
                .isNull(query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty(), Warehouse::getId);
    }

    private void checkCodeUnique(String code, Long orgId, Long excludeId) {
        Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getDeleted, 0)
                .eq(Warehouse::getWarehouseCode, code)
                .eq(orgId != null, Warehouse::getOrgId, orgId)
                .ne(excludeId != null, Warehouse::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("同一组织下仓库编码已存在");
        }
    }

    private void checkNameUnique(String name, Long orgId, Long excludeId) {
        Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<Warehouse>()
                .eq(Warehouse::getDeleted, 0)
                .eq(Warehouse::getWarehouseName, name)
                .eq(orgId != null, Warehouse::getOrgId, orgId)
                .ne(excludeId != null, Warehouse::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("同一组织下仓库名称已存在");
        }
    }

    private void validateWarehouseCode(String code) {
        String normalizedCode = normalize(code);
        if (normalizedCode == null) {
            throw BizException.badRequest("仓库编码不能为空");
        }
        if (normalizedCode.length() > 50) {
            throw BizException.badRequest("仓库编码长度不能超过50个字符");
        }
    }

    private void validateWarehouseName(String name) {
        String normalizedName = normalize(name);
        if (normalizedName == null) {
            throw BizException.badRequest("仓库名称不能为空");
        }
        if (normalizedName.length() > 100) {
            throw BizException.badRequest("仓库名称长度不能超过100个字符");
        }
    }

    private void validateExportScope(WarehouseQueryDTO query) {
        if (query == null || query.getOrgId() == null || query.getOrgIds() == null) {
            return;
        }
        if (!query.getOrgIds().contains(query.getOrgId())) {
            throw BizException.badRequest("超出可导出组织范围");
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

    private boolean isSampleRow(WarehouseImportDTO row) {
        return isPlaceholderSample(row.getOrgCode())
                || isPlaceholderSample(row.getWarehouseCode())
                || isPlaceholderSample(row.getWarehouseName())
                || isPlaceholderSample(row.getWarehouseType())
                || isPlaceholderSample(row.getAddress())
                || isPlaceholderSample(row.getManagerName())
                || isPlaceholderSample(row.getManagerPhone())
                || isPlaceholderSample(row.getStatus());
    }

    private boolean isEmptyRow(WarehouseImportDTO row) {
        return StrUtil.isAllBlank(
                normalize(row.getOrgCode()),
                normalize(row.getWarehouseCode()),
                normalize(row.getWarehouseName()),
                normalize(row.getWarehouseType())
        );
    }

    private ValidationError validateImportRow(WarehouseImportDTO row, Set<String> seenOrgWarehouseCodes) {
        row.setOrgCode(normalize(row.getOrgCode()));
        row.setWarehouseCode(normalize(row.getWarehouseCode()));
        row.setWarehouseName(normalize(row.getWarehouseName()));
        row.setWarehouseType(normalize(row.getWarehouseType()));
        row.setAddress(normalize(row.getAddress()));
        row.setManagerName(normalize(row.getManagerName()));
        row.setManagerPhone(normalize(row.getManagerPhone()));
        row.setStatus(normalizeStatus(row.getStatus()));

        if (StrUtil.isBlank(row.getOrgCode())) return new ValidationError("orgCode", "组织编码不能为空");
        if (StrUtil.isBlank(row.getWarehouseCode())) return new ValidationError("warehouseCode", "仓库编码不能为空");
        if (StrUtil.isBlank(row.getWarehouseName())) return new ValidationError("warehouseName", "仓库名称不能为空");
        if (StrUtil.isBlank(row.getWarehouseType())) return new ValidationError("warehouseType", "仓库类型不能为空");
        if (row.getWarehouseCode().length() > 50) return new ValidationError("warehouseCode", "仓库编码长度不能超过50个字符");
        if (row.getWarehouseName().length() > 100) return new ValidationError("warehouseName", "仓库名称长度不能超过100个字符");
        if (StrUtil.isNotBlank(row.getAddress()) && row.getAddress().length() > 200) return new ValidationError("address", "仓库位置长度不能超过200个字符");
        if (StrUtil.isNotBlank(row.getManagerName()) && row.getManagerName().length() > 50) return new ValidationError("managerName", "负责人长度不能超过50个字符");
        if (StrUtil.isNotBlank(row.getManagerPhone()) && row.getManagerPhone().length() > 20) return new ValidationError("managerPhone", "联系方式长度不能超过20个字符");
        if (StrUtil.isNotBlank(row.getStatus()) && !Set.of("active", "inactive", "maintenance").contains(row.getStatus())) {
            return new ValidationError("status", "状态仅支持 启用、停用、维护中（或 active、inactive、maintenance）");
        }
        String duplicateKey = row.getOrgCode() + "::" + row.getWarehouseCode();
        if (!seenOrgWarehouseCodes.add(duplicateKey)) {
            return new ValidationError("warehouseCode", "同一导入文件中同组织仓库编码重复：" + row.getWarehouseCode());
        }
        return null;
    }

    private void appendImportError(WarehouseImportDTO row, ValidationError validationError, List<WarehouseImportErrorDTO> errors, List<WarehouseImportDTO> errorRows) {
        row.setErrorField(validationError.field());
        row.setErrorReason(validationError.reason());
        errors.add(new WarehouseImportErrorDTO(row.getRowNumber(), validationError.field(), validationError.reason()));
        errorRows.add(row);
    }

    private Long resolveOrgIdByCode(String orgCode) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM sys_organization WHERE org_code = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getLong(1),
                orgCode
        );
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Long resolveCurrentOrgId() {
        if (UserContext.getOrgId() == null) {
            throw BizException.badRequest("当前账号未绑定组织，请联系管理员处理");
        }
        return UserContext.getOrgId();
    }

    private Long resolveCurrentTenantId() {
        return UserContext.getTenantId() == null ? 1L : UserContext.getTenantId();
    }

    private File resolveImportErrorFile(String fileName) {
        if (StrUtil.isBlank(fileName) || !fileName.matches("warehouse_import_errors_\\d+\\.xlsx")) {
            throw BizException.badRequest("错误文件名非法");
        }
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR).getCanonicalFile();
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建仓库导入错误文件目录失败: {}", dir.getAbsolutePath());
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

    private String generateImportErrorFile(List<WarehouseImportDTO> errorRows) {
        try {
            File dir = new File(IMPORT_ERROR_FILE_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("创建仓库导入错误文件目录失败: {}", dir.getAbsolutePath());
            }
            String fileName = "warehouse_import_errors_" + System.currentTimeMillis() + ".xlsx";
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
                sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, IMPORT_TEMPLATE_HEADERS.length + 1));

                Row headerRow = sheet.createRow(rowNum++);
                for (int i = 0; i < IMPORT_TEMPLATE_HEADERS.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(IMPORT_TEMPLATE_HEADERS[i]);
                    cell.setCellStyle(headerStyle);
                    sheet.setColumnWidth(i, IMPORT_TEMPLATE_WIDTHS[i] * 256);
                }
                Cell fieldCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length);
                fieldCell.setCellValue("错误字段");
                fieldCell.setCellStyle(headerStyle);
                Cell reasonCell = headerRow.createCell(IMPORT_TEMPLATE_HEADERS.length + 1);
                reasonCell.setCellValue("失败原因");
                reasonCell.setCellStyle(headerStyle);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length, 18 * 256);
                sheet.setColumnWidth(IMPORT_TEMPLATE_HEADERS.length + 1, 36 * 256);

                for (WarehouseImportDTO row : errorRows) {
                    Row dataRow = sheet.createRow(rowNum++);
                    String[] values = {
                            blankToEmpty(row.getOrgCode()),
                            blankToEmpty(row.getWarehouseCode()),
                            blankToEmpty(row.getWarehouseName()),
                            blankToEmpty(row.getWarehouseType()),
                            blankToEmpty(row.getAddress()),
                            blankToEmpty(row.getManagerName()),
                            blankToEmpty(row.getManagerPhone()),
                            row.getCapacity() == null ? "" : row.getCapacity().stripTrailingZeros().toPlainString(),
                            blankToEmpty(row.getStatus()),
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
            log.error("生成仓库导入错误文件失败", ex);
            return null;
        }
    }

    private void exportXlsx(List<WarehouseVO> warehouses, HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("仓库信息");
            CellStyle tipStyle = createTipStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int rowNum = 0;
            Row tipRow = sheet.createRow(rowNum++);
            Cell tipCell = tipRow.createCell(0);
            tipCell.setCellValue("【说明】当前导出结果已按页面筛选条件和数据权限范围处理。 ");
            tipCell.setCellStyle(tipStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, EXPORT_HEADERS.length - 1));

            Row headerRow = sheet.createRow(rowNum++);
            for (int i = 0; i < EXPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(EXPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, EXPORT_WIDTHS[i] * 256);
            }

            for (WarehouseVO warehouse : warehouses) {
                Row row = sheet.createRow(rowNum++);
                String[] values = toExportValues(warehouse);
                for (int i = 0; i < values.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓库信息_" + LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
            workbook.write(response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            log.error("导出仓库失败", e);
            throw BizException.badRequest("导出仓库失败");
        }
    }

    private void exportCsv(List<WarehouseVO> warehouses, HttpServletResponse response) {
        try {
            response.setContentType("text/csv");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String fileName = URLEncoder.encode("仓库信息_" + LocalDateTime.now().format(EXPORT_FILE_TIME_FORMATTER), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".csv");
            StringBuilder builder = new StringBuilder();
            builder.append('﻿');
            builder.append(String.join(",", EXPORT_HEADERS)).append("\n");
            for (WarehouseVO warehouse : warehouses) {
                String[] values = toExportValues(warehouse);
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
            log.error("导出仓库CSV失败", e);
            throw BizException.badRequest("导出仓库失败");
        }
    }

    private String[] toExportValues(WarehouseVO warehouse) {
        return new String[]{
                blankToEmpty(warehouse.getWarehouseCode()),
                blankToEmpty(warehouse.getWarehouseName()),
                blankToEmpty(warehouse.getWarehouseType()),
                decimalToString(warehouse.getCapacity()),
                blankToEmpty(warehouse.getCapacityUnit()),
                blankToEmpty(warehouse.getAddress()),
                blankToEmpty(warehouse.getManagerName()),
                blankToEmpty(warehouse.getManagerPhone()),
                blankToEmpty(warehouse.getStatus()),
                blankToEmpty(warehouse.getRemark()),
                numberToString(warehouse.getPositionTotal()),
                numberToString(warehouse.getPositionUsed()),
                numberToString(warehouse.getPositionIdle()),
                warehouse.getCreatedAt() == null ? "" : warehouse.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                warehouse.getUpdatedAt() == null ? "" : warehouse.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        };
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
            case "启用" -> "active";
            case "停用" -> "inactive";
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

    private String numberToString(Number value) {
        return value == null ? "" : String.valueOf(value);
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
}
