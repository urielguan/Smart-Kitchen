package com.xykj.sample.service;

import com.xykj.common.result.PageResult;
import com.xykj.sample.dto.SampleRecordCreateDTO;
import com.xykj.sample.dto.SampleRecordDisposeDTO;
import com.xykj.sample.dto.SampleHistoryTaskQueryDTO;
import com.xykj.sample.dto.SampleManualDisposalSupplementDTO;
import com.xykj.sample.dto.SampleOperationLockAcquireDTO;
import com.xykj.sample.dto.SampleOperationLockRefreshDTO;
import com.xykj.sample.dto.SampleOperationLockReleaseDTO;
import com.xykj.sample.dto.SampleRecordQueryDTO;
import com.xykj.sample.dto.SampleRecordHistoryCreateDTO;
import com.xykj.sample.dto.SampleRecordRegisterDTO;
import com.xykj.sample.dto.SampleRecordUpdateDTO;
import com.xykj.sample.dto.BatchVoidDTO;
import com.xykj.sample.vo.*;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 留样记录服务接口
 */
public interface SampleRecordService {

    /**
     * 获取留样看板数据
     */
    SampleDashboardVO getDashboard(SampleRecordQueryDTO query);

    /**
     * 分页查询留样记录
     */
    PageResult<SampleRecordVO> getRecordPage(SampleRecordQueryDTO query);

    /**
     * 获取手工新增可选烹饪任务
     */
    List<SampleAvailableCookTaskVO> getAvailableCookTasks();

    /**
     * 获取历史补录可选烹饪任务
     */
    List<SampleAvailableCookTaskVO> getHistoryAvailableCookTasks(SampleHistoryTaskQueryDTO query);

    /**
     * 新增留样记录
     */
    SampleRecordDetailVO createRecord(SampleRecordCreateDTO dto);

    /**
     * 历史补录留样记录
     */
    SampleRecordDetailVO createHistoricalRecord(SampleRecordHistoryCreateDTO dto);

    /**
     * 留样登记
     */
    SampleRecordDetailVO registerRecord(Long id, SampleRecordRegisterDTO dto);

    /**
     * 获取留样记录详情
     */
    SampleRecordDetailVO getRecordDetail(Long id);

    /**
     * 抢占留样操作锁
     */
    SampleOperationLockVO acquireOperationLock(Long id, SampleOperationLockAcquireDTO dto);

    /**
     * 续租留样操作锁
     */
    SampleOperationLockVO refreshOperationLock(Long id, SampleOperationLockRefreshDTO dto);

    /**
     * 释放留样操作锁
     */
    void releaseOperationLock(Long id, SampleOperationLockReleaseDTO dto);

    /**
     * 执行销样
     */
    SampleRecordDetailVO disposeRecord(Long id, SampleRecordDisposeDTO dto);

    /**
     * 销样手工补录
     */
    SampleRecordDetailVO manualSupplementDisposal(Long id, SampleManualDisposalSupplementDTO dto);

    /**
     * 获取销样提醒列表
     */
    PageResult<DisposalReminderVO> getDisposalReminders(SampleRecordQueryDTO query);

    /**
     * 获取销样详情
     */
    SampleRecordDetailVO getDisposalDetail(Long id);

    /**
     * AI智能评估（Mock实现）
     */
    AiEvaluateVO aiEvaluateRecord(Long id);

    /**
     * 编辑留样记录（仅sampled状态可编辑）
     */
    SampleRecordDetailVO updateRecord(Long id, SampleRecordUpdateDTO dto);

    /**
     * 作废留样记录
     */
    SampleRecordDetailVO voidRecord(Long id, String reason);

    /**
     * 禁止物理删除（仅支持作废或归档）
     */
    void deleteRecord(Long id);

    /**
     * 上传留样图片
     */
    String uploadImage(MultipartFile file);

    /**
     * 归档留样记录
     */
    SampleRecordDetailVO archiveRecord(Long id);

    /**
     * 导出留样记录到Excel
     */
    void exportRecords(SampleRecordQueryDTO query, HttpServletResponse response);

    /**
     * 获取操作日志
     */
    List<OperationLogVO> getOperationLogs(Long recordId);

    /**
     * 批量作废
     */
    void batchVoidRecords(List<Long> ids, String reason);

    /**
     * 批量归档
     */
    void batchArchiveRecords(List<Long> ids);

    /**
     * 监管锁定
     */
    SampleRecordDetailVO lockRecord(Long id, String lockStatus);

    /**
     * 解除锁定
     */
    SampleRecordDetailVO unlockRecord(Long id);
}
