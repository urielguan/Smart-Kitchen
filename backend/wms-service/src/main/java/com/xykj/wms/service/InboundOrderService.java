package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.InboundAreaValidationPreviewDTO;
import com.xykj.wms.dto.InboundImportResultDTO;
import com.xykj.wms.dto.InboundOrderActionDTO;
import com.xykj.wms.dto.InboundOrderCreateDTO;
import com.xykj.wms.dto.InboundOrderQueryDTO;
import com.xykj.wms.dto.InboundOrderUpdateDTO;
import com.xykj.wms.vo.InboundAreaValidationPreviewVO;
import com.xykj.wms.vo.InboundOrderStatisticsVO;
import com.xykj.wms.vo.InboundOrderVO;
import com.xykj.wms.vo.InboundOrderWriteResultVO;
import com.xykj.wms.vo.InboundSourceOrderOptionVO;
import com.xykj.wms.vo.PurchaseOrderItemForInboundVO;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;

public interface InboundOrderService {

    PageResult<InboundOrderVO> list(InboundOrderQueryDTO query);

    InboundOrderStatisticsVO getStatistics(InboundOrderQueryDTO query);

    InboundOrderVO getDetail(Long id);

    List<InboundSourceOrderOptionVO> listSourceOrderOptions(String sourceType, Long excludeInboundOrderId);

    InboundAreaValidationPreviewVO previewAreaValidation(InboundAreaValidationPreviewDTO dto);

    void downloadImportTemplate(HttpServletResponse response);

    InboundImportResultDTO importOrders(MultipartFile file);

    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    Long create(InboundOrderCreateDTO dto);

    void uploadAttachments(Long id, MultipartFile[] files);

    void downloadAttachment(Long id, String url, HttpServletResponse response);

    void previewAttachment(Long id, String url, HttpServletResponse response);

    InboundOrderWriteResultVO update(Long id, InboundOrderUpdateDTO dto);

    void delete(Long id);

    InboundOrderWriteResultVO submit(Long id, InboundOrderActionDTO dto);

    InboundOrderWriteResultVO approve(Long id, InboundOrderActionDTO dto);

    InboundOrderWriteResultVO postApproved(Long id, InboundOrderActionDTO dto);

    InboundOrderWriteResultVO unapprove(Long id, InboundOrderActionDTO dto);

    InboundOrderWriteResultVO retryPost(Long id, InboundOrderActionDTO dto);

    void reject(Long id, String approveRemark);

    int syncApprovedInventory();

    void cancel(Long id);

    /** 根据采购订单ID查询未完全入库的物料明细 */
    List<PurchaseOrderItemForInboundVO> listPurchaseOrderItemsForInbound(Long purchaseOrderId, Long excludeInboundOrderId);
}
