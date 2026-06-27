package com.xykj.scm.service;

import com.xykj.common.result.PageResult;
import com.xykj.scm.dto.PurchasePlanAuditDTO;
import com.xykj.scm.dto.PurchasePlanCreateDTO;
import com.xykj.scm.dto.PurchasePlanGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanMergeGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanQueryDTO;
import com.xykj.scm.dto.PurchasePlanReverseAuditDTO;
import com.xykj.scm.dto.PurchasePlanUpdateDTO;
import com.xykj.scm.dto.PurchasePlanVoidApplyDTO;
import com.xykj.scm.dto.PurchasePlanVoidAuditDTO;
import com.xykj.scm.vo.PurchaseOrderGenerateResultVO;
import com.xykj.scm.vo.PurchasePlanAttachmentVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentItemPrefillVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentOptionVO;
import com.xykj.scm.vo.PurchasePlanRecipeMaterialLinkageVO;
import com.xykj.scm.vo.PurchasePlanLinkedOrderRecordVO;
import com.xykj.scm.vo.PurchasePlanMaterialOptionVO;
import com.xykj.scm.vo.PurchasePlanReverseAuditResultVO;
import com.xykj.scm.vo.PurchasePlanStatisticsVO;
import com.xykj.scm.vo.PurchasePlanVO;
import com.xykj.scm.vo.SelectablePurchasePlanVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 采购计划服务
 */
public interface PurchasePlanService {

    PageResult<PurchasePlanVO> list(PurchasePlanQueryDTO query);

    PurchasePlanStatisticsVO getStatistics(Long orgId);

    PurchasePlanVO getDetail(Long id);

    List<PurchasePlanLinkedOrderRecordVO> listLinkedPurchaseOrders(Long id);

    PurchasePlanAttachmentVO uploadAttachment(MultipartFile file);

    void deleteAttachment(String fileUrl, String fileName);

    void downloadAttachment(String fileUrl, String fileName, HttpServletResponse response);

    Long create(PurchasePlanCreateDTO dto, MultipartFile file);

    void update(Long id, PurchasePlanUpdateDTO dto, MultipartFile file);

    void audit(Long id, PurchasePlanAuditDTO dto);

    PurchasePlanReverseAuditResultVO reverseAudit(Long id, PurchasePlanReverseAuditDTO dto);

    void applyVoid(Long id, PurchasePlanVoidApplyDTO dto);

    void auditVoid(Long id, PurchasePlanVoidAuditDTO dto);

    void delete(Long id);

    List<PurchasePlanMaterialOptionVO> listMaterialOptions(Long orgId);

    List<PurchasePlanRelatedDocumentOptionVO> listRelatedDocuments(Long orgId, String keyword);

    List<PurchasePlanRelatedDocumentItemPrefillVO> listRelatedDocumentItems(String documentType, Long documentId);

    PurchasePlanRecipeMaterialLinkageVO getRecipePlanMaterialLinkage(Long documentId, Long excludePlanId);

    List<PurchaseOrderGenerateResultVO> generateOrders(Long id, PurchasePlanGenerateOrderDTO dto, MultipartFile file);

    PurchaseOrderGenerateResultVO mergeGenerateOrder(PurchasePlanMergeGenerateOrderDTO dto);

    List<SelectablePurchasePlanVO> listSelectablePlansForOrders(Long orgId, String keyword);
}
