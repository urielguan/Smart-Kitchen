package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.OutboundImportResultDTO;
import com.xykj.wms.dto.OutboundImportTaskActionDTO;
import com.xykj.wms.dto.OutboundImportTaskDTO;
import com.xykj.wms.dto.OutboundOrderCreateDTO;
import com.xykj.wms.dto.OutboundOrderQueryDTO;
import com.xykj.wms.dto.OutboundOrderUpdateDTO;
import com.xykj.wms.dto.OutboundSuggestionPreviewDTO;
import com.xykj.wms.dto.OutboundSuggestionRevalidateDTO;
import com.xykj.wms.vo.OutboundOrderStatisticsVO;
import com.xykj.wms.vo.OutboundOrderVO;
import com.xykj.wms.vo.OutboundSourceOrderOptionVO;
import com.xykj.wms.vo.OutboundSuggestionPreviewVO;
import com.xykj.wms.vo.OutboundSuggestionRevalidateVO;
import com.xykj.wms.vo.OutboundTypeOptionVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface OutboundOrderService {

    void downloadImportTemplate(HttpServletResponse response);

    OutboundImportResultDTO importOrders(MultipartFile file);

    OutboundImportTaskDTO getImportTask(String taskNo);

    OutboundImportTaskDTO resumeImportTask(String taskNo, OutboundImportTaskActionDTO action);

    OutboundImportTaskDTO terminateImportTask(String taskNo, OutboundImportTaskActionDTO action);

    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    void exportList(OutboundOrderQueryDTO query, HttpServletResponse response);

    void exportDetails(OutboundOrderQueryDTO query, HttpServletResponse response);

    List<OutboundTypeOptionVO> typeOptions();

    PageResult<OutboundOrderVO> list(OutboundOrderQueryDTO query);

    OutboundOrderStatisticsVO getStatistics();

    List<OutboundSourceOrderOptionVO> listSourceOrderOptions(String outboundType);

    OutboundOrderVO getDetail(Long id);

    OutboundSuggestionPreviewVO previewSuggestions(OutboundSuggestionPreviewDTO dto);

    OutboundSuggestionRevalidateVO revalidateSuggestions(OutboundSuggestionRevalidateDTO dto);

    Long create(OutboundOrderCreateDTO dto);

    void update(Long id, OutboundOrderUpdateDTO dto);

    void delete(Long id);

    void submit(Long id);

    void approve(Long id, String approveRemark);

    void reject(Long id, String rejectReason);

    void withdraw(Long id);

    void execute(Long id);  // 执行出库，扣减库存

    void reverse(Long id);  // 反审核

    void uploadAttachments(Long id, MultipartFile[] files);

    void downloadAttachment(Long id, String url, HttpServletResponse response);
}
