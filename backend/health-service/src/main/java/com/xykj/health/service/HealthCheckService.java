package com.xykj.health.service;

import com.xykj.common.result.PageResult;
import com.xykj.health.dto.AiCheckDTO;
import com.xykj.health.dto.HealthCheckCreateDTO;
import com.xykj.health.dto.HealthCheckQueryDTO;
import com.xykj.health.dto.HealthCheckUpdateDTO;
import com.xykj.health.vo.AiCheckResultVO;
import com.xykj.health.vo.HealthCheckDetailVO;
import com.xykj.health.vo.HealthCheckLinkageVersionVO;
import com.xykj.health.vo.HealthCheckRecordVO;
import com.xykj.health.vo.HealthDashboardVO;

/**
 * 晨检记录服务接口
 */
public interface HealthCheckService {

    /** 状态常量 */
    String STATUS_PENDING_CHECK = "pending_check";
    String STATUS_CHECKING = "checking";
    String STATUS_COMPLETED_NORMAL = "completed_normal";
    String STATUS_COMPLETED_ABNORMAL = "completed_abnormal";
    String STATUS_ARCHIVED = "archived";

    /** 体温状态常量 */
    String TEMP_LOW = "low";
    String TEMP_NORMAL = "normal";
    String TEMP_HIGH = "high";

    /**
     * 获取晨检看板数据
     */
    HealthDashboardVO getDashboard(HealthCheckQueryDTO query);

    /**
     * 获取待晨检列表（今日未晨检）
     */
    PageResult<HealthCheckRecordVO> getPendingList(HealthCheckQueryDTO query);

    /**
     * 获取已完成/全部记录列表（分页）
     */
    PageResult<HealthCheckRecordVO> getRecordPage(HealthCheckQueryDTO query);

    /**
     * 执行晨检（创建记录）
     */
    HealthCheckDetailVO createRecord(HealthCheckCreateDTO dto);

    /**
     * 获取晨检记录详情
     */
    HealthCheckDetailVO getRecordDetail(Long id);

    /**
     * 更新晨检记录
     */
    HealthCheckDetailVO updateRecord(Long id, HealthCheckUpdateDTO dto);

    /**
     * 归档晨检记录
     */
    HealthCheckDetailVO archiveRecord(Long id);

    /**
     * AI一键晨检
     * 传入人脸照片，自动完成全部检查流程（人脸识别+体温+健康证+手部/着装）
     */
    AiCheckResultVO aiCheck(AiCheckDTO dto);

    /**
     * 获取晨检联动版本，用于前端即时刷新
     */
    HealthCheckLinkageVersionVO getLinkageVersion();
}
