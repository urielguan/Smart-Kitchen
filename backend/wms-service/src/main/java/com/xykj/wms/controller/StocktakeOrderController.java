package com.xykj.wms.controller;

import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.StocktakeOrderApproveDTO;
import com.xykj.wms.dto.StocktakeOrderCreateDTO;
import com.xykj.wms.dto.StocktakeOrderQueryDTO;
import com.xykj.wms.dto.StocktakeOrderRejectDTO;
import com.xykj.wms.dto.StocktakeOrderUpdateDTO;
import com.xykj.wms.dto.StocktakeOrderVoidDTO;
import com.xykj.wms.dto.StocktakeSnapshotPreviewDTO;
import com.xykj.wms.service.StocktakeOrderService;
import com.xykj.wms.vo.StocktakeOrderDetailVO;
import com.xykj.wms.vo.StocktakeOrderListVO;
import com.xykj.wms.vo.StocktakeSnapshotPreviewVO;
import com.xykj.wms.vo.StocktakeStatisticsVO;
import com.xykj.wms.vo.StocktakeVersionDetailVO;
import com.xykj.wms.vo.StocktakeVersionSummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/wms/stocktake-orders")
@RequiredArgsConstructor
public class StocktakeOrderController {

    private final StocktakeOrderService stocktakeOrderService;

    @GetMapping
    public R<PageResult<StocktakeOrderListVO>> list(@Valid StocktakeOrderQueryDTO query) {
        return R.ok(stocktakeOrderService.list(query));
    }

    @GetMapping("/statistics")
    public R<StocktakeStatisticsVO> statistics(@Valid StocktakeOrderQueryDTO query) {
        return R.ok(stocktakeOrderService.getStatistics(query));
    }

    @GetMapping("/export")
    public void export(@Valid StocktakeOrderQueryDTO query, HttpServletResponse response) {
        stocktakeOrderService.export(query, response);
    }

    @GetMapping("/{id:\\d+}")
    public R<StocktakeOrderDetailVO> detail(@PathVariable Long id) {
        return R.ok(stocktakeOrderService.getDetail(id));
    }

    @GetMapping("/{id:\\d+}/versions")
    public R<List<StocktakeVersionSummaryVO>> versions(@PathVariable Long id) {
        return R.ok(stocktakeOrderService.getVersions(id));
    }

    @GetMapping("/{id:\\d+}/versions/{versionNo:\\d+}")
    public R<StocktakeVersionDetailVO> versionDetail(@PathVariable Long id, @PathVariable Integer versionNo) {
        return R.ok(stocktakeOrderService.getVersionDetail(id, versionNo));
    }

    @GetMapping("/snapshot-preview")
    public R<List<StocktakeSnapshotPreviewVO>> snapshotPreview(@RequestParam(required = false) Long warehouseId,
                                                               @RequestParam(required = false) Long locationId,
                                                               @RequestParam(required = false) List<Long> warehouseIds,
                                                               @RequestParam(required = false) List<Long> locationIds) {
        if (warehouseId == null && (warehouseIds == null || warehouseIds.isEmpty())) {
            throw BizException.validationFailed("盘点仓库不能为空");
        }
        StocktakeSnapshotPreviewDTO dto = new StocktakeSnapshotPreviewDTO();
        dto.setWarehouseId(warehouseId);
        dto.setLocationId(locationId);
        dto.setWarehouseIds(warehouseIds);
        dto.setLocationIds(locationIds);
        return R.ok(stocktakeOrderService.previewSnapshot(dto));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody StocktakeOrderCreateDTO dto) {
        return R.ok(stocktakeOrderService.create(dto));
    }

    @PutMapping("/{id:\\d+}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody StocktakeOrderUpdateDTO dto) {
        stocktakeOrderService.update(id, dto);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/submit")
    public R<Void> submit(@PathVariable Long id) {
        stocktakeOrderService.submit(id);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/approve")
    public R<Void> approve(@PathVariable Long id, @Valid @RequestBody(required = false) StocktakeOrderApproveDTO dto) {
        stocktakeOrderService.approve(id, dto);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/reject")
    public R<Void> reject(@PathVariable Long id, @Valid @RequestBody StocktakeOrderRejectDTO dto) {
        stocktakeOrderService.reject(id, dto);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/void")
    public R<Void> voidOrder(@PathVariable Long id, @Valid @RequestBody StocktakeOrderVoidDTO dto) {
        stocktakeOrderService.voidOrder(id, dto);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/refresh-snapshot")
    public R<Void> refreshSnapshot(@PathVariable Long id) {
        stocktakeOrderService.refreshSnapshot(id);
        return R.ok();
    }

    @PostMapping("/{id:\\d+}/attachments")
    public R<Void> uploadAttachments(@PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        stocktakeOrderService.uploadAttachments(id, files);
        return R.ok();
    }

    @GetMapping("/{id:\\d+}/attachments/download")
    public void downloadAttachment(@PathVariable Long id,
                                   @RequestParam String url,
                                   HttpServletResponse response) {
        stocktakeOrderService.downloadAttachment(id, url, response);
    }
}
