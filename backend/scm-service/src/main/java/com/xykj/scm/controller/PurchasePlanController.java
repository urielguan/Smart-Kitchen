package com.xykj.scm.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.scm.dto.PurchasePlanAuditDTO;
import com.xykj.scm.dto.PurchasePlanCreateDTO;
import com.xykj.scm.dto.PurchasePlanGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanMergeGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanQueryDTO;
import com.xykj.scm.dto.PurchasePlanReverseAuditDTO;
import com.xykj.scm.dto.PurchasePlanUpdateDTO;
import com.xykj.scm.dto.PurchasePlanVoidApplyDTO;
import com.xykj.scm.dto.PurchasePlanVoidAuditDTO;
import com.xykj.scm.service.PurchasePlanService;
import com.xykj.scm.vo.PurchaseOrderGenerateResultVO;
import com.xykj.scm.vo.PurchasePlanAttachmentVO;
import com.xykj.scm.vo.PurchasePlanLinkedOrderRecordVO;
import com.xykj.scm.vo.PurchasePlanMaterialOptionVO;
import com.xykj.scm.vo.PurchasePlanRecipeMaterialLinkageVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentItemPrefillVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentOptionVO;
import com.xykj.scm.vo.PurchasePlanReverseAuditResultVO;
import com.xykj.scm.vo.PurchasePlanStatisticsVO;
import com.xykj.scm.vo.PurchasePlanVO;
import com.xykj.scm.vo.SelectablePurchasePlanVO;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

import java.util.List;

/**
 * 采购计划接口
 */
@RestController
@RequestMapping("/api/v1/scm/purchase-plans")
@RequiredArgsConstructor
public class PurchasePlanController {

    private final PurchasePlanService purchasePlanService;

    @GetMapping
    public R<PageResult<PurchasePlanVO>> list(@Valid PurchasePlanQueryDTO query) {
        return R.ok(purchasePlanService.list(query));
    }

    @GetMapping("/statistics")
    public R<PurchasePlanStatisticsVO> statistics(@RequestParam(required = false) Long orgId) {
        return R.ok(purchasePlanService.getStatistics(orgId));
    }

    @GetMapping("/{id:\\d+}")
    public R<PurchasePlanVO> detail(@PathVariable Long id) {
        return R.ok(purchasePlanService.getDetail(id));
    }

    @GetMapping("/{id:\\d+}/linked-purchase-orders")
    public R<List<PurchasePlanLinkedOrderRecordVO>> linkedPurchaseOrders(@PathVariable Long id) {
        return R.ok(purchasePlanService.listLinkedPurchaseOrders(id));
    }

    @GetMapping("/{id:\\d+}/attachment/download")
    public void downloadAttachment(@PathVariable Long id, HttpServletResponse response) {
        PurchasePlanVO detail = purchasePlanService.getDetail(id);
        purchasePlanService.downloadAttachment(detail.getAttachmentUrl(), detail.getAttachmentName(), response);
    }

    @PostMapping("/files/upload")
    public R<PurchasePlanAttachmentVO> uploadAttachment(@RequestParam("file") MultipartFile file) {
        return R.ok(purchasePlanService.uploadAttachment(file));
    }

    @DeleteMapping("/files")
    public R<Void> deleteAttachment(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName
    ) {
        purchasePlanService.deleteAttachment(fileUrl, fileName);
        return R.ok();
    }

    @GetMapping("/files/download")
    public void downloadAttachment(
            @RequestParam("fileUrl") String fileUrl,
            @RequestParam(value = "fileName", required = false) String fileName,
            HttpServletResponse response
    ) {
        purchasePlanService.downloadAttachment(fileUrl, fileName, response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Long> create(
            @Valid @RequestPart("data") PurchasePlanCreateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return R.ok(purchasePlanService.create(dto, file));
    }

    @PutMapping(value = "/{id:\\d+}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<Void> update(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchasePlanUpdateDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        purchasePlanService.update(id, dto, file);
        return R.ok();
    }

    @PutMapping("/{id:\\d+}/audit")
    public R<Void> audit(@PathVariable Long id, @Valid @RequestBody PurchasePlanAuditDTO dto) {
        purchasePlanService.audit(id, dto);
        return R.ok();
    }

    @PutMapping("/{id:\\d+}/reverse-audit")
    public R<PurchasePlanReverseAuditResultVO> reverseAudit(
            @PathVariable Long id,
            @Valid @RequestBody PurchasePlanReverseAuditDTO dto
    ) {
        return R.ok(purchasePlanService.reverseAudit(id, dto));
    }

    @PutMapping("/{id:\\d+}/void")
    public R<Void> applyVoid(@PathVariable Long id, @Valid @RequestBody PurchasePlanVoidApplyDTO dto) {
        purchasePlanService.applyVoid(id, dto);
        return R.ok();
    }

    @PutMapping("/{id:\\d+}/void-audit")
    public R<Void> auditVoid(@PathVariable Long id, @Valid @RequestBody PurchasePlanVoidAuditDTO dto) {
        purchasePlanService.auditVoid(id, dto);
        return R.ok();
    }

    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        purchasePlanService.delete(id);
        return R.ok();
    }

    @GetMapping("/material-options")
    public R<List<PurchasePlanMaterialOptionVO>> materialOptions(@RequestParam(required = false) Long orgId) {
        return R.ok(purchasePlanService.listMaterialOptions(orgId));
    }

    @GetMapping("/related-documents")
    public R<List<PurchasePlanRelatedDocumentOptionVO>> relatedDocuments(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String keyword
    ) {
        return R.ok(purchasePlanService.listRelatedDocuments(orgId, keyword));
    }

    @GetMapping("/related-documents/{documentType}/{documentId:\\d+}/items")
    public R<List<PurchasePlanRelatedDocumentItemPrefillVO>> relatedDocumentItems(
            @PathVariable String documentType,
            @PathVariable Long documentId
    ) {
        return R.ok(purchasePlanService.listRelatedDocumentItems(documentType, documentId));
    }

    @GetMapping("/related-documents/recipePlan/{documentId:\\d+}/material-linkage")
    public R<PurchasePlanRecipeMaterialLinkageVO> recipePlanMaterialLinkage(
            @PathVariable Long documentId,
            @RequestParam(required = false) Long excludePlanId
    ) {
        return R.ok(purchasePlanService.getRecipePlanMaterialLinkage(documentId, excludePlanId));
    }

    @PostMapping(value = "/{id:\\d+}/generate-orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    public R<List<PurchaseOrderGenerateResultVO>> generateOrders(
            @PathVariable Long id,
            @Valid @RequestBody PurchasePlanGenerateOrderDTO dto
    ) {
        return R.ok(purchasePlanService.generateOrders(id, dto, null));
    }

    @PostMapping(value = "/{id:\\d+}/generate-orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<List<PurchaseOrderGenerateResultVO>> generateOrdersWithAttachment(
            @PathVariable Long id,
            @Valid @RequestPart("data") PurchasePlanGenerateOrderDTO dto,
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        return R.ok(purchasePlanService.generateOrders(id, dto, file));
    }

    @PostMapping("/merge-generate-order")
    public R<PurchaseOrderGenerateResultVO> mergeGenerateOrder(
            @Valid @RequestBody PurchasePlanMergeGenerateOrderDTO dto
    ) {
        return R.ok(purchasePlanService.mergeGenerateOrder(dto));
    }

    @GetMapping("/selectable-for-orders")
    public R<List<SelectablePurchasePlanVO>> selectableForOrders(
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String keyword
    ) {
        return R.ok(purchasePlanService.listSelectablePlansForOrders(orgId, keyword));
    }
}
