package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.ScreenshotQueryDTO;
import com.xykj.device.dto.ScreenshotUploadDTO;
import com.xykj.device.vo.DeviceVisionAnalysisTaskVO;
import com.xykj.device.vo.ScreenshotVO;
import org.springframework.web.multipart.MultipartFile;

public interface ScreenshotService {

    ScreenshotVO uploadScreenshot(MultipartFile file, ScreenshotUploadDTO dto);

    Page<ScreenshotVO> getScreenshotList(ScreenshotQueryDTO query);

    ScreenshotVO getScreenshotDetail(Long id);

    void deleteScreenshot(Long id);

    DeviceVisionAnalysisTaskVO analyzeScreenshot(Long id);

    DeviceVisionAnalysisTaskVO getLatestAnalysis(Long screenshotId);
}
