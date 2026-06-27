package com.xykj.wms.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.wms.dto.InboundOrderActionDTO;
import com.xykj.wms.dto.InboundAreaValidationPreviewDTO;
import com.xykj.wms.dto.InboundImportResultDTO;
import com.xykj.wms.dto.InboundOrderCreateDTO;
import com.xykj.wms.dto.InboundOrderQueryDTO;
import com.xykj.wms.dto.InboundOrderUpdateDTO;
import com.xykj.wms.dto.InboundValidationErrorDTO;
import com.xykj.wms.exception.InboundOrderValidationException;
import com.xykj.wms.service.InboundOrderService;
import com.xykj.wms.vo.InboundAreaValidationPreviewVO;
import com.xykj.wms.vo.InboundOrderStatisticsVO;
import com.xykj.wms.vo.InboundOrderVO;
import com.xykj.wms.vo.InboundOrderWriteResultVO;
import com.xykj.wms.vo.InboundSourceOrderOptionVO;
import com.xykj.wms.vo.PurchaseOrderItemForInboundVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/wms/inbound-orders")
@RequiredArgsConstructor
public class InboundOrderController {

    private final InboundOrderService inboundOrderService;

    @ExceptionHandler(InboundOrderValidationException.class)
    public R<InboundValidationErrorDTO> handleInboundOrderValidationException(InboundOrderValidationException exception) {
        InboundValidationErrorDTO error = exception.getError();
        String message = error != null ? error.getGlobalMessage() : exception.getMessage();
        return R.<InboundValidationErrorDTO>validationFailed(message).data(error);
    }

    /** GET /api/v1/wms/inbound-orders */
    @GetMapping
    public R<PageResult<InboundOrderVO>> list(@Valid InboundOrderQueryDTO query) {
        return R.ok(inboundOrderService.list(query));
    }

    /** GET /api/v1/wms/inbound-orders/statistics */
    @GetMapping("/statistics")
    public R<InboundOrderStatisticsVO> statistics(@Valid InboundOrderQueryDTO query) {
        return R.ok(inboundOrderService.getStatistics(query));
    }

    /** POST /api/v1/wms/inbound-orders/sync-approved-inventory */
    @PostMapping("/sync-approved-inventory")
    public R<Map<String, Integer>> syncApprovedInventory() {
        int syncedCount = inboundOrderService.syncApprovedInventory();
        return R.ok(Map.of("syncedCount", syncedCount));
    }

    /** GET /api/v1/wms/inbound-orders/{id} */
    @GetMapping("/{id:\\d+}")
    public R<InboundOrderVO> detail(@PathVariable Long id) {
        return R.ok(inboundOrderService.getDetail(id));
    }

    /** GET /api/v1/wms/inbound-orders/source-order-options */
    @GetMapping("/source-order-options")
    public R<List<InboundSourceOrderOptionVO>> sourceOrderOptions(@RequestParam String sourceType,
                                                                  @RequestParam(required = false) Long excludeInboundOrderId) {
        return R.ok(inboundOrderService.listSourceOrderOptions(sourceType, excludeInboundOrderId));
    }

    /** GET /api/v1/wms/inbound-orders/source-order-items?purchaseOrderId={id} */
    @GetMapping("/source-order-items")
    public R<List<PurchaseOrderItemForInboundVO>> sourceOrderItems(@RequestParam Long purchaseOrderId,
                                                                   @RequestParam(required = false) Long excludeInboundOrderId) {
        return R.ok(inboundOrderService.listPurchaseOrderItemsForInbound(purchaseOrderId, excludeInboundOrderId));
    }

    @PostMapping("/area-validation/preview")
    public R<InboundAreaValidationPreviewVO> previewAreaValidation(@Valid @RequestBody InboundAreaValidationPreviewDTO dto) {
        return R.ok(inboundOrderService.previewAreaValidation(dto));
    }

    @GetMapping("/import/template")
    public void downloadImportTemplate(HttpServletResponse response) {
        inboundOrderService.downloadImportTemplate(response);
    }

    @PostMapping("/import")
    public R<InboundImportResultDTO> importOrders(@RequestParam("file") MultipartFile file) {
        return R.ok(inboundOrderService.importOrders(file));
    }

    @GetMapping("/import/errors/{fileName}")
    public void downloadImportErrorFile(@PathVariable("fileName") String fileName, HttpServletResponse response) {
        inboundOrderService.downloadImportErrorFile(fileName, response);
    }

    /** POST /api/v1/wms/inbound-orders */
    @PostMapping
    public R<Long> create(@Valid @RequestBody InboundOrderCreateDTO dto) {
        return R.ok(inboundOrderService.create(dto));
    }

    @PostMapping("/{id:\\d+}/attachments")
    public R<Void> uploadAttachments(@PathVariable Long id, @RequestParam("files") MultipartFile[] files) {
        inboundOrderService.uploadAttachments(id, files);
        return R.ok();
    }

    @GetMapping("/{id:\\d+}/attachments/download")
    public void downloadAttachment(@PathVariable Long id,
                                   @RequestParam String url,
                                   HttpServletResponse response) {
        inboundOrderService.downloadAttachment(id, url, response);
    }

    @GetMapping("/{id:\\d+}/attachments/preview")
    public void previewAttachment(@PathVariable Long id,
                                  @RequestParam String url,
                                  HttpServletResponse response) {
        inboundOrderService.previewAttachment(id, url, response);
    }

    /** PUT /api/v1/wms/inbound-orders/{id} */
    @PutMapping("/{id:\\d+}")
    public R<InboundOrderWriteResultVO> update(@PathVariable Long id, @RequestBody InboundOrderUpdateDTO dto) {
        return R.ok(inboundOrderService.update(id, dto));
    }

    /** DELETE /api/v1/wms/inbound-orders/{id} */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        inboundOrderService.delete(id);
        return R.ok();
    }

    /** POST /api/v1/wms/inbound-orders/{id}/submit */
    @PostMapping("/{id:\\d+}/submit")
    public R<InboundOrderWriteResultVO> submit(@PathVariable Long id,
                                               @Valid @RequestBody InboundOrderActionDTO dto) {
        return R.ok(inboundOrderService.submit(id, dto));
    }

    /** POST /api/v1/wms/inbound-orders/{id}/approve */
    @PostMapping("/{id:\\d+}/approve")
    public R<InboundOrderWriteResultVO> approve(@PathVariable Long id,
                                                @Valid @RequestBody InboundOrderActionDTO dto) {
        return R.ok(inboundOrderService.approve(id, dto));
    }

    /** POST /api/v1/wms/inbound-orders/{id}/post */
    @PostMapping("/{id:\\d+}/post")
    public R<InboundOrderWriteResultVO> postApproved(@PathVariable Long id,
                                                     @Valid @RequestBody InboundOrderActionDTO dto) {
        return R.ok(inboundOrderService.postApproved(id, dto));
    }

    /** POST /api/v1/wms/inbound-orders/{id}/unapprove */
    @PostMapping("/{id:\\d+}/unapprove")
    public R<InboundOrderWriteResultVO> unapprove(@PathVariable Long id,
                                                  @Valid @RequestBody InboundOrderActionDTO dto) {
        return R.ok(inboundOrderService.unapprove(id, dto));
    }

    /** POST /api/v1/wms/inbound-orders/{id}/retry-post */
    @PostMapping("/{id:\\d+}/retry-post")
    public R<InboundOrderWriteResultVO> retryPost(@PathVariable Long id,
                                                  @Valid @RequestBody InboundOrderActionDTO dto) {
        return R.ok(inboundOrderService.retryPost(id, dto));
    }

    /** POST /api/v1/wms/inbound-orders/{id}/reject */
    @PostMapping("/{id:\\d+}/reject")
    public R<Void> reject(@PathVariable Long id,
                          @RequestBody Map<String, String> body) {
        inboundOrderService.reject(id, body.get("approveRemark"));
        return R.ok();
    }

    /** POST /api/v1/wms/inbound-orders/{id}/cancel */
    @PostMapping("/{id:\\d+}/cancel")
    public R<Void> cancel(@PathVariable Long id) {
        inboundOrderService.cancel(id);
        return R.ok();
    }
}
