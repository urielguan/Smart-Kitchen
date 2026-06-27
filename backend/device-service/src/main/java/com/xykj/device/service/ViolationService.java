package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.ViolationHandleDTO;
import com.xykj.device.dto.ViolationQueryDTO;
import com.xykj.device.vo.ViolationStatisticsVO;
import com.xykj.device.vo.ViolationOperationLogVO;
import com.xykj.device.vo.ViolationVO;

/**
 * AI违规识别服务接口
 */
public interface ViolationService {

    /**
     * 获取违规事件列表
     * @param query 查询条件
     * @return 分页结果
     */
    Page<ViolationVO> getViolationList(ViolationQueryDTO query);

    /**
     * 获取违规事件详情
     * @param id 事件ID
     * @return 事件详情
     */
    ViolationVO getViolationDetail(Long id);

    /**
     * 处理违规事件
     * @param id 事件ID
     * @param dto 处理信息
     * @return 是否成功
     */
    boolean handleViolation(Long id, ViolationHandleDTO dto);

    /**
     * 获取违规统计数据
     * @param orgId 组织ID
     * @return 统计数据
     */
    ViolationStatisticsVO getViolationStatistics(Long orgId);

    /**
     * 批量处理违规事件
     * @param ids 事件ID列表
     * @param dto 处理信息
     * @return 是否成功
     */
    boolean batchHandleViolations(java.util.List<Long> ids, ViolationHandleDTO dto);

    /**
     * 获取违规事件操作日志
     * @param alertId 告警ID
     * @return 操作日志列表
     */
    java.util.List<ViolationOperationLogVO> getOperationLogs(Long alertId);
}
