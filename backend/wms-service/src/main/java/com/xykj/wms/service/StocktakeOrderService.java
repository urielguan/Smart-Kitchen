package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.StocktakeOrderApproveDTO;
import com.xykj.wms.dto.StocktakeOrderCreateDTO;
import com.xykj.wms.dto.StocktakeOrderQueryDTO;
import com.xykj.wms.dto.StocktakeOrderRejectDTO;
import com.xykj.wms.dto.StocktakeOrderUpdateDTO;
import com.xykj.wms.dto.StocktakeOrderVoidDTO;
import com.xykj.wms.dto.StocktakeSnapshotPreviewDTO;
import com.xykj.wms.vo.StocktakeOrderDetailVO;
import com.xykj.wms.vo.StocktakeOrderListVO;
import com.xykj.wms.vo.StocktakeSnapshotPreviewVO;
import com.xykj.wms.vo.StocktakeStatisticsVO;
import com.xykj.wms.vo.StocktakeVersionDetailVO;
import com.xykj.wms.vo.StocktakeVersionSummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StocktakeOrderService {

    PageResult<StocktakeOrderListVO> list(StocktakeOrderQueryDTO query);

    StocktakeStatisticsVO getStatistics(StocktakeOrderQueryDTO query);

    StocktakeOrderDetailVO getDetail(Long id);

    List<StocktakeVersionSummaryVO> getVersions(Long id);

    StocktakeVersionDetailVO getVersionDetail(Long id, Integer versionNo);

    List<StocktakeSnapshotPreviewVO> previewSnapshot(StocktakeSnapshotPreviewDTO dto);

    Long create(StocktakeOrderCreateDTO dto);

    void update(Long id, StocktakeOrderUpdateDTO dto);

    void submit(Long id);

    void approve(Long id, StocktakeOrderApproveDTO dto);

    void reject(Long id, StocktakeOrderRejectDTO dto);

    void voidOrder(Long id, StocktakeOrderVoidDTO dto);

    void uploadAttachments(Long id, MultipartFile[] files);

    void refreshSnapshot(Long id);

    void downloadAttachment(Long id, String url, HttpServletResponse response);

    void export(StocktakeOrderQueryDTO query, HttpServletResponse response);
}
