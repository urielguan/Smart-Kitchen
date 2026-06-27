package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.InventoryMovementQueryDTO;
import com.xykj.wms.dto.InventoryOverviewQueryDTO;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.service.InventoryService;
import com.xykj.wms.vo.InventoryDistributionVO;
import com.xykj.wms.vo.InventoryMovementVO;
import com.xykj.wms.vo.InventoryOverviewVO;
import com.xykj.wms.vo.InventoryShelfLifeSummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存查询控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/wms/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryMapper inventoryMapper;
    private final InventoryService inventoryService;

    /**
     * 查询可用的批次号列表
     * 根据物料ID、规格、仓库ID查询有库存的批次号
     *
     * @param materialId 物料ID
     * @param spec 规格（可选）
     * @param warehouseId 仓库ID
     * @return 批次号选项列表
     */
    @GetMapping("/batch-nos")
    public R<List<Map<String, Object>>> getAvailableBatchNos(
            @RequestParam Long materialId,
            @RequestParam(required = false) String spec,
            @RequestParam Long warehouseId,
            @RequestParam(required = false) Long locationId) {
        log.info("查询批次号: materialId={}, spec={}, warehouseId={}, locationId={}", materialId, spec, warehouseId, locationId);
        List<Map<String, Object>> batchNos = inventoryMapper.selectAvailableBatchNos(materialId, spec, warehouseId, locationId);
        return R.ok(batchNos);
    }

    @GetMapping("/overview")
    public R<PageResult<InventoryOverviewVO>> getOverview(@Valid InventoryOverviewQueryDTO query) {
        return R.ok(inventoryService.getOverview(query));
    }

    @GetMapping("/{materialId}/distribution")
    public R<List<InventoryDistributionVO>> getDistribution(@PathVariable Long materialId) {
        return R.ok(inventoryService.getDistribution(materialId));
    }

    @GetMapping("/{materialId}/shelf-life-summary")
    public R<InventoryShelfLifeSummaryVO> getShelfLifeSummary(@PathVariable Long materialId) {
        return R.ok(inventoryService.getShelfLifeSummary(materialId));
    }

    @GetMapping("/{materialId}/movements")
    public R<PageResult<InventoryMovementVO>> getMovements(@PathVariable Long materialId, @Valid InventoryMovementQueryDTO query) {
        return R.ok(inventoryService.getMovements(materialId, query));
    }

    @GetMapping("/export")
    public void exportOverview(@Valid InventoryOverviewQueryDTO query, HttpServletResponse response) {
        inventoryService.exportOverview(query, response);
    }

    @GetMapping("/{materialId}/movements/export")
    public void exportMovements(@PathVariable Long materialId,
                                @Valid InventoryMovementQueryDTO query,
                                HttpServletResponse response) {
        inventoryService.exportMovements(materialId, query, response);
    }
}
