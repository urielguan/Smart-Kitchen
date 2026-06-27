package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.WarehouseCreateDTO;
import com.xykj.wms.dto.WarehouseImportResultDTO;
import com.xykj.wms.dto.WarehouseQueryDTO;
import com.xykj.wms.dto.WarehouseUpdateDTO;
import com.xykj.wms.service.WarehouseService;
import com.xykj.wms.vo.WarehouseStatisticsVO;
import com.xykj.wms.vo.WarehouseVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/v1/wms/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    /** GET /api/v1/wms/warehouses */
    @GetMapping
    public R<PageResult<WarehouseVO>> list(@Valid WarehouseQueryDTO query) {
        return R.ok(warehouseService.list(query));
    }

    /** GET /api/v1/wms/warehouses/statistics */
    @GetMapping("/statistics")
    public R<WarehouseStatisticsVO> statistics(@Valid WarehouseQueryDTO query) {
        return R.ok(warehouseService.getStatistics(query));
    }

    /** GET /api/v1/wms/warehouses/import/template */
    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        warehouseService.downloadImportTemplate(response);
    }

    /** POST /api/v1/wms/warehouses/import */
    @PostMapping("/import")
    public R<WarehouseImportResultDTO> importWarehouses(@RequestParam("file") MultipartFile file) {
        return R.ok(warehouseService.importWarehouses(file));
    }

    /** GET /api/v1/wms/warehouses/export */
    @GetMapping("/export")
    public void exportWarehouses(@Valid WarehouseQueryDTO query, HttpServletResponse response) {
        warehouseService.exportWarehouses(query, response);
    }

    /** GET /api/v1/wms/warehouses/import/errors/{fileName} */
    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        warehouseService.downloadImportErrorFile(fileName, response);
    }

    /** GET /api/v1/wms/warehouses/{id} */
    @GetMapping("/{id:\\d+}")
    public R<WarehouseVO> detail(@PathVariable Long id) {
        return R.ok(warehouseService.getDetail(id));
    }

    /** POST /api/v1/wms/warehouses */
    @PostMapping
    public R<Long> create(@Valid @RequestBody WarehouseCreateDTO dto) {
        return R.ok(warehouseService.create(dto));
    }

    /** PUT /api/v1/wms/warehouses/{id} */
    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @RequestBody WarehouseUpdateDTO dto) {
        warehouseService.update(id, dto);
        return R.ok();
    }

    /** DELETE /api/v1/wms/warehouses/{id} */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        warehouseService.delete(id);
        return R.ok();
    }
}
