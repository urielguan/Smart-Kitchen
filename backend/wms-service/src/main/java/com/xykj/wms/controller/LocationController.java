package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.LocationCreateDTO;
import com.xykj.wms.dto.LocationImportResultDTO;
import com.xykj.wms.dto.LocationQueryDTO;
import com.xykj.wms.dto.LocationUpdateDTO;
import com.xykj.wms.service.LocationService;
import com.xykj.wms.vo.LocationVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.Default;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/wms/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final JdbcTemplate jdbcTemplate;

    /** GET /api/v1/wms/locations?warehouseId=xxx */
    @GetMapping
    public R<PageResult<LocationVO>> list(@Valid LocationQueryDTO query) {
        return R.ok(locationService.list(query));
    }

    /** GET /api/v1/wms/locations/import/template */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        locationService.downloadImportTemplate(response);
    }

    /** POST /api/v1/wms/locations/import */
    @PostMapping("/import")
    public R<LocationImportResultDTO> importLocations(@RequestParam("file") MultipartFile file) {
        return R.ok(locationService.importLocations(file));
    }

    /** GET /api/v1/wms/locations/export */
    @GetMapping("/export")
    public void exportLocations(LocationQueryDTO query, HttpServletResponse response) {
        locationService.exportLocations(query, response);
    }

    /** GET /api/v1/wms/locations/import/errors/{fileName} */
    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        locationService.downloadImportErrorFile(fileName, response);
    }

    /** POST /api/v1/wms/locations */
    @PostMapping
    public R<Long> create(@Valid @RequestBody LocationCreateDTO dto) {
        return R.ok(locationService.create(dto));
    }

    /** PUT /api/v1/wms/locations/{id} */
    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @RequestBody LocationUpdateDTO dto) {
        locationService.update(id, dto);
        return R.ok();
    }

    /** DELETE /api/v1/wms/locations/{id} */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        locationService.delete(id);
        return R.ok();
    }

    // ==================== 传感器绑定（设备管理调用） ====================

    /** PUT /api/v1/wms/locations/sensor-binding — 绑定/解绑传感器到仓位 */
    @PutMapping("/sensor-binding")
    public R<Void> bindSensor(@Valid @RequestBody SensorBindingDTO dto) {
        // 1. 清除该传感器的旧绑定
        jdbcTemplate.update(
            "UPDATE wms_location SET sensor_device_id = NULL WHERE sensor_device_id = ? AND deleted = 0",
            dto.getSensorDeviceId());
        // 2. 设置新绑定（如果指定了仓位）
        if (dto.getLocationId() != null) {
            int rows = jdbcTemplate.update(
                "UPDATE wms_location SET sensor_device_id = ? WHERE id = ? AND deleted = 0",
                dto.getSensorDeviceId(), dto.getLocationId());
            if (rows == 0) {
                return R.fail("仓位不存在");
            }
        }
        log.info("传感器绑定: sensorDeviceId={}, locationId={}", dto.getSensorDeviceId(), dto.getLocationId());
        return R.ok();
    }

    /** GET /api/v1/wms/locations/all-for-binding — 所有仓位列表（设备管理绑定下拉用） */
    @GetMapping("/all-for-binding")
    public R<java.util.List<LocationVO>> listAllForBinding() {
        java.util.List<LocationVO> list = jdbcTemplate.query(
            "SELECT l.id, l.location_code, l.location_name, l.warehouse_id, w.warehouse_name " +
            "FROM wms_location l LEFT JOIN wms_warehouse w ON l.warehouse_id = w.id AND w.deleted = 0 " +
            "WHERE l.deleted = 0 ORDER BY l.warehouse_id, l.location_code",
            (rs, rowNum) -> {
                LocationVO v = new LocationVO();
                v.setId(rs.getLong("id"));
                v.setLocationCode(rs.getString("location_code"));
                v.setLocationName(rs.getString("location_name"));
                v.setWarehouseId(rs.getLong("warehouse_id"));
                v.setWarehouseName(rs.getString("warehouse_name"));
                return v;
            });
        return R.ok(list);
    }

    /** GET /api/v1/wms/locations/by-sensor/{sensorDeviceId} — 查询传感器绑定的仓位 */
    @GetMapping("/by-sensor/{sensorDeviceId:\\d+}")
    public R<LocationVO> getBySensor(@PathVariable Long sensorDeviceId) {
        LocationVO vo = jdbcTemplate.query(
            "SELECT l.id, l.location_code, l.location_name, l.warehouse_id, w.warehouse_name " +
            "FROM wms_location l LEFT JOIN wms_warehouse w ON l.warehouse_id = w.id AND w.deleted = 0 " +
            "WHERE l.sensor_device_id = ? AND l.deleted = 0 LIMIT 1",
            rs -> {
                if (rs.next()) {
                    LocationVO v = new LocationVO();
                    v.setId(rs.getLong("id"));
                    v.setLocationCode(rs.getString("location_code"));
                    v.setLocationName(rs.getString("location_name"));
                    v.setWarehouseId(rs.getLong("warehouse_id"));
                    return v;
                }
                return null;
            }, sensorDeviceId);
        return R.ok(vo);
    }

    @Data
    static class SensorBindingDTO {
        @NotNull(message = "传感器设备ID不能为空")
        private Long sensorDeviceId;
        /** 仓位ID，null 表示解绑 */
        private Long locationId;
    }
}
