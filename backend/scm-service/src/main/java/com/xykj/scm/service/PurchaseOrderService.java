package com.xykj.scm.service;

import com.xykj.common.result.PageResult;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 采购订单服务
 */
public interface PurchaseOrderService {

    PageResult<PurchaseOrderVO> list(PurchaseOrderQueryDTO query);

    PurchaseOrderStatisticsVO getStatistics(Long orgId);

    PurchaseOrderVO getDetail(Long id);

    Long create(PurchaseOrderCreateDTO dto, MultipartFile file);

    void update(Long id, PurchaseOrderUpdateDTO dto, MultipartFile file);

    void audit(Long id, PurchaseOrderAuditDTO dto);

    PurchaseOrderReverseAuditResultVO reverseAudit(Long id, PurchaseOrderReverseAuditDTO dto);

    void applyVoid(Long id, PurchaseOrderVoidApplyDTO dto);

    void auditVoid(Long id, PurchaseOrderVoidAuditDTO dto);

    void updateLogistics(Long id, PurchaseOrderLogisticsUpdateDTO dto, MultipartFile file);

    void deleteLogisticsAttachment(Long id);

    void updateInspection(Long id, PurchaseOrderInspectionUpdateDTO dto, MultipartFile file);

    void deleteInspectionAttachment(Long id);

    void updateTraceability(Long id, PurchaseOrderTraceabilityUpdateDTO dto, MultipartFile file);

    void deleteTraceabilityAttachment(Long id);

    PurchaseOrderSceneIntegrationMetaVO getSceneIntegrationMeta(Long id, String scene);

    PurchaseOrderSceneIntegrationTriggerResultVO triggerSceneIntegrationSync(Long id, String scene, PurchaseOrderSceneIntegrationSyncDTO dto);

    PurchaseOrderSceneIntegrationLogsVO getSceneIntegrationLogs(Long id, String scene);

    void delete(Long id);

    List<PurchaseOrderItemVO> getItems(Long id);

    List<PurchaseOrderLinkedInboundRecordVO> listLinkedInboundRecords(Long id);

    List<PurchaseOrderSupplierOptionVO> listSupplierOptions(Long orgId);

    List<PurchaseOrderMaterialOptionVO> listMaterialOptions(Long orgId);

    List<SelectablePurchasePlanVO> listSelectablePlans(Long orgId, String keyword, Long excludeOrderId);

    List<PurchaseOrderPlanItemOptionVO> listPlanItems(Long orgId, List<Long> planIds, Long excludeOrderId);
}
