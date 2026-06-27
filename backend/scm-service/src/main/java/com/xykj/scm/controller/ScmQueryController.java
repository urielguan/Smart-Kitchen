package com.xykj.scm.controller;

import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.common.service.FileStorageService;
import com.xykj.scm.dto.PurchaseOrderAuditDTO;
import com.xykj.scm.dto.PurchaseOrderCreateDTO;
import com.xykj.scm.dto.PurchaseOrderInspectionUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderLogisticsUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderQueryDTO;
import com.xykj.scm.dto.PurchaseOrderReverseAuditDTO;
import com.xykj.scm.dto.PurchaseOrderSceneIntegrationSyncDTO;
import com.xykj.scm.dto.PurchaseOrderTraceabilityUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderVoidApplyDTO;
import com.xykj.scm.dto.PurchaseOrderVoidAuditDTO;
import com.xykj.scm.service.PurchaseOrderService;
import com.xykj.scm.vo.PurchaseOrderAttachmentVO;
import com.xykj.scm.vo.PurchaseOrderItemVO;
import com.xykj.scm.vo.PurchaseOrderLinkedInboundRecordVO;
import com.xykj.scm.vo.PurchaseOrderMaterialOptionVO;
import com.xykj.scm.vo.PurchaseOrderPlanItemOptionVO;
import com.xykj.scm.vo.PurchaseOrderReverseAuditResultVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationLogsVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationMetaVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationTriggerResultVO;
import com.xykj.scm.vo.PurchaseOrderStatisticsVO;
import com.xykj.scm.vo.PurchaseOrderSupplierOptionVO;
import com.xykj.scm.vo.PurchaseOrderVO;
import com.xykj.scm.vo.SelectablePurchasePlanVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 采购订单接口
 */
@RestController
@RequestMapping("/api/v1/scm")
@RequiredArgsConstructor
public class ScmQueryController {

    private static final long MAX_ATTACHMENT_FILE_SIZE = 10L * 1024 * 1024;

    private final PurchaseOrderService purchaseOrderService;
    private final FileStorageService fileStorageService;

    /**
     * 采购订单列表
     */
    @GetMapping("/purchase-orders")
    public R<PageResult<PurchaseOrderVO>> listPurchaseOrders(@Valid PurchaseOrderQueryDTO query) {
        return R.ok(purchaseOrderService.list(query));
    }

    /**
     * 采购订单统计
     */
    @GetMapping("/purchase-orders/statistics")
    public R<PurchaseOrderStatisticsVO> getPurchaseOrderStatistics(@RequestParam(required = false) Long orgId) {
        return R.ok(purchaseOrderService.getStatistics(orgId));
    }

    /**
     * 采购订单详情
     */
    @GetMapping("/purchase-orders/{id:\\d+}")
    public R<PurchaseOrderVO> getPurchaseOrderDetail(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getDetail(id));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/attachment/download")
    public void downloadPurchaseOrderAttachment(@PathVariable Long id, HttpServletResponse response) {
        PurchaseOrderVO detail = purchaseOrderService.getDetail(id);
        writeAttachment(detail.getAttachmentName(), detail.getAttachmentUrl(), response);
    }

    @GetMapping("/purchase-orders/{id:\\d+}/logistics/attachment/download")
    public void downloadPurchaseOrderLogisticsAttachment(@PathVariable Long id, HttpServletResponse response) {
        PurchaseOrderVO detail = purchaseOrderService.getDetail(id);
        writeAttachment(detail.getLogisticsAttachmentName(), detail.getLogisticsAttachmentUrl(), response);
    }

    @GetMapping("/purchase-orders/{id:\\d+}/inspection/attachment/download")
    public void downloadPurchaseOrderInspectionAttachment(@PathVariable Long id, HttpServletResponse response) {
        PurchaseOrderVO detail = purchaseOrderService.getDetail(id);
        writeAttachment(detail.getInspectionAttachmentName(), detail.getInspectionAttachmentUrl(), response);
    }

    @GetMapping("/purchase-orders/{id:\\d+}/traceability/attachment/download")
    public void downloadPurchaseOrderTraceabilityAttachment(@PathVariable Long id, HttpServletResponse response) {
        PurchaseOrderVO detail = purchaseOrderService.getDetail(id);
        writeAttachment(detail.getTraceAttachmentName(), detail.getTraceAttachmentUrl(), response);
    }

    @PostMapping(value = "/purchase-orders/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<PurchaseOrderAttachmentVO> uploadPurchaseOrderAttachment(@RequestParam("file") MultipartFile file) {
        validateAttachmentFile(file);
        PurchaseOrderAttachmentVO vo = new PurchaseOrderAttachmentVO();
        vo.setName(resolveAttachmentName(file));
        vo.setSize(formatFileSize(file.getSize()));
        vo.setUrl(fileStorageService.upload(file, "scm/purchase-orders/maintenance"));
        return R.ok(vo);
    }

    @DeleteMapping("/purchase-orders/files")
    public R<Void> deletePurchaseOrderAttachment(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName
    ) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw BizException.badRequest("文件地址不能为空");
        }
        fileStorageService.delete(fileUrl.trim());
        return R.ok();
    }

    @GetMapping("/purchase-orders/files/download")
    public void downloadPurchaseOrderAttachmentByUrl(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName,
            HttpServletResponse response
    ) {
        writeAttachment(fileName, fileUrl, response);
    }

    /**
     * 新增采购订单
     */
    @PostMapping(value = "/purchase-orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> createPurchaseOrder(
            @Valid @RequestPart("data") PurchaseOrderCreateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return R.ok(purchaseOrderService.create(dto, file));
    }

    /**
     * 编辑采购订单
     */
    @PutMapping(value = "/purchase-orders/{id:\\d+}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> updatePurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchaseOrderUpdateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        purchaseOrderService.update(id, dto, file);
        return R.ok();
    }

    /**
     * 审核采购订单
     */
    @PutMapping("/purchase-orders/{id:\\d+}/audit")
    public R<Void> auditPurchaseOrder(@PathVariable Long id, @Valid @RequestBody PurchaseOrderAuditDTO dto) {
        purchaseOrderService.audit(id, dto);
        return R.ok();
    }

    /**
     * 反审核采购订单
     */
    @PutMapping("/purchase-orders/{id:\\d+}/reverse-audit")
    public R<PurchaseOrderReverseAuditResultVO> reverseAuditPurchaseOrder(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderReverseAuditDTO dto
    ) {
        return R.ok(purchaseOrderService.reverseAudit(id, dto));
    }

    /**
     * 发起采购订单作废申请
     */
    @PutMapping("/purchase-orders/{id:\\d+}/void")
    public R<Void> applyPurchaseOrderVoid(@PathVariable Long id, @Valid @RequestBody PurchaseOrderVoidApplyDTO dto) {
        purchaseOrderService.applyVoid(id, dto);
        return R.ok();
    }

    /**
     * 审核采购订单作废申请
     */
    @PutMapping("/purchase-orders/{id:\\d+}/void-audit")
    public R<Void> auditPurchaseOrderVoid(@PathVariable Long id, @Valid @RequestBody PurchaseOrderVoidAuditDTO dto) {
        purchaseOrderService.auditVoid(id, dto);
        return R.ok();
    }

    /**
     * 维护采购订单物流信息
     */
    @PutMapping(value = "/purchase-orders/{id:\\d+}/logistics", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> updatePurchaseOrderLogistics(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchaseOrderLogisticsUpdateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        purchaseOrderService.updateLogistics(id, dto, file);
        return R.ok();
    }

    /**
     * 删除采购订单物流附件
     */
    @DeleteMapping("/purchase-orders/{id:\\d+}/logistics/attachment")
    public R<Void> deletePurchaseOrderLogisticsAttachment(@PathVariable Long id) {
        purchaseOrderService.deleteLogisticsAttachment(id);
        return R.ok();
    }

    /**
     * 维护采购订单检测报告
     */
    @PutMapping(value = "/purchase-orders/{id:\\d+}/inspection", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> updatePurchaseOrderInspection(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchaseOrderInspectionUpdateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        purchaseOrderService.updateInspection(id, dto, file);
        return R.ok();
    }

    /**
     * 删除采购订单检测报告附件
     */
    @DeleteMapping("/purchase-orders/{id:\\d+}/inspection/attachment")
    public R<Void> deletePurchaseOrderInspectionAttachment(@PathVariable Long id) {
        purchaseOrderService.deleteInspectionAttachment(id);
        return R.ok();
    }

    /**
     * 维护采购订单溯源信息
     */
    @PutMapping(value = "/purchase-orders/{id:\\d+}/traceability", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> updatePurchaseOrderTraceability(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchaseOrderTraceabilityUpdateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        purchaseOrderService.updateTraceability(id, dto, file);
        return R.ok();
    }

    /**
     * 删除采购订单溯源附件
     */
    @DeleteMapping("/purchase-orders/{id:\\d+}/traceability/attachment")
    public R<Void> deletePurchaseOrderTraceabilityAttachment(@PathVariable Long id) {
        purchaseOrderService.deleteTraceabilityAttachment(id);
        return R.ok();
    }

    @GetMapping("/purchase-orders/{id:\\d+}/logistics/integration-meta")
    public R<PurchaseOrderSceneIntegrationMetaVO> getPurchaseOrderLogisticsIntegrationMeta(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationMeta(id, "logistics"));
    }

    @PostMapping("/purchase-orders/{id:\\d+}/logistics/integration-sync")
    public R<PurchaseOrderSceneIntegrationTriggerResultVO> triggerPurchaseOrderLogisticsIntegrationSync(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderSceneIntegrationSyncDTO dto
    ) {
        return R.ok(purchaseOrderService.triggerSceneIntegrationSync(id, "logistics", dto));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/logistics/integration-logs")
    public R<PurchaseOrderSceneIntegrationLogsVO> getPurchaseOrderLogisticsIntegrationLogs(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationLogs(id, "logistics"));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/inspection/integration-meta")
    public R<PurchaseOrderSceneIntegrationMetaVO> getPurchaseOrderInspectionIntegrationMeta(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationMeta(id, "inspection"));
    }

    @PostMapping("/purchase-orders/{id:\\d+}/inspection/integration-sync")
    public R<PurchaseOrderSceneIntegrationTriggerResultVO> triggerPurchaseOrderInspectionIntegrationSync(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderSceneIntegrationSyncDTO dto
    ) {
        return R.ok(purchaseOrderService.triggerSceneIntegrationSync(id, "inspection", dto));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/inspection/integration-logs")
    public R<PurchaseOrderSceneIntegrationLogsVO> getPurchaseOrderInspectionIntegrationLogs(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationLogs(id, "inspection"));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/traceability/integration-meta")
    public R<PurchaseOrderSceneIntegrationMetaVO> getPurchaseOrderTraceabilityIntegrationMeta(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationMeta(id, "traceability"));
    }

    @PostMapping("/purchase-orders/{id:\\d+}/traceability/integration-sync")
    public R<PurchaseOrderSceneIntegrationTriggerResultVO> triggerPurchaseOrderTraceabilityIntegrationSync(
            @PathVariable Long id,
            @Valid @RequestBody PurchaseOrderSceneIntegrationSyncDTO dto
    ) {
        return R.ok(purchaseOrderService.triggerSceneIntegrationSync(id, "traceability", dto));
    }

    @GetMapping("/purchase-orders/{id:\\d+}/traceability/integration-logs")
    public R<PurchaseOrderSceneIntegrationLogsVO> getPurchaseOrderTraceabilityIntegrationLogs(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getSceneIntegrationLogs(id, "traceability"));
    }

    /**
     * 删除采购订单
     */
    @DeleteMapping("/purchase-orders/{id:\\d+}")
    public R<Void> deletePurchaseOrder(@PathVariable Long id) {
        purchaseOrderService.delete(id);
        return R.ok();
    }

    /**
     * 采购订单明细（供入库模块带出物料）
     */
    @GetMapping("/purchase-orders/{id:\\d+}/items")
    public R<List<PurchaseOrderItemVO>> getPurchaseOrderItems(@PathVariable Long id) {
        return R.ok(purchaseOrderService.getItems(id));
    }

    /**
     * 采购订单关联入库单记录
     */
    @GetMapping("/purchase-orders/{id:\\d+}/linked-inbound-records")
    public R<List<PurchaseOrderLinkedInboundRecordVO>> getPurchaseOrderLinkedInboundRecords(@PathVariable Long id) {
        return R.ok(purchaseOrderService.listLinkedInboundRecords(id));
    }

    /**
     * 供应商选项
     */
    @GetMapping("/purchase-orders/supplier-options")
    public R<List<PurchaseOrderSupplierOptionVO>> getSupplierOptions(@RequestParam(required = false) Long orgId) {
        return R.ok(purchaseOrderService.listSupplierOptions(orgId));
    }

    /**
     * 物料选项
     */
    @GetMapping("/purchase-orders/material-options")
    public R<List<PurchaseOrderMaterialOptionVO>> getMaterialOptions(@RequestParam(required = false) Long orgId) {
        return R.ok(purchaseOrderService.listMaterialOptions(orgId));
    }

    /**
     * 可关联采购计划单
     */
    @GetMapping("/purchase-orders/selectable-plans")
    public R<List<SelectablePurchasePlanVO>> getSelectablePlans(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long excludeOrderId
    ) {
        return R.ok(purchaseOrderService.listSelectablePlans(orgId, keyword, excludeOrderId));
    }

    /**
     * 可关联采购计划明细
     */
    @GetMapping("/purchase-orders/plan-items")
    public R<List<PurchaseOrderPlanItemOptionVO>> getPlanItems(
            @RequestParam(required = false) Long orgId,
            @RequestParam String planIds,
            @RequestParam(required = false) Long excludeOrderId
    ) {
        return R.ok(purchaseOrderService.listPlanItems(orgId, parsePlanIds(planIds), excludeOrderId));
    }

    private List<Long> parsePlanIds(String rawPlanIds) {
        if (rawPlanIds == null || rawPlanIds.isBlank()) {
            return Collections.emptyList();
        }

        List<Long> planIds = new ArrayList<>();
        for (String value : rawPlanIds.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                planIds.add(Long.parseLong(trimmed));
            }
        }
        return planIds;
    }

    private void writeAttachment(String attachmentName, String attachmentUrl, HttpServletResponse response) {
        if (attachmentUrl == null || attachmentUrl.isBlank()) {
            throw BizException.notFound("附件不存在");
        }

        FileStorageService.StoredFile storedFile = fileStorageService.download(attachmentUrl);
        String fileName = attachmentName == null || attachmentName.isBlank() ? "attachment" : attachmentName;
        try (InputStream inputStream = storedFile.inputStream()) {
            response.setContentType(
                    storedFile.contentType() == null || storedFile.contentType().isBlank()
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

    private void validateAttachmentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_FILE_SIZE) {
            throw BizException.badRequest("文件大小不能超过10MB");
        }
    }

    private String resolveAttachmentName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return originalFilename == null || originalFilename.isBlank() ? "attachment" : originalFilename.trim();
    }

    private String formatFileSize(long fileSize) {
        if (fileSize >= 1024L * 1024) {
            return String.format("%.2f MB", fileSize / 1024D / 1024D);
        }
        if (fileSize >= 1024L) {
            return String.format("%.2f KB", fileSize / 1024D);
        }
        return fileSize + " B";
    }
}
