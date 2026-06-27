package com.xykj.device.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.BehaviorAnalysisQueryDTO;
import com.xykj.device.vo.BehaviorAnalysisVO;
import com.xykj.device.vo.BehaviorStatisticsVO;

/**
 * AI人员行为分析服务接口
 */
public interface BehaviorAnalysisService {

    /**
     * 获取人员行为分析列表
     * @param query 查询条件
     * @return 分页结果
     */
    Page<BehaviorAnalysisVO> getBehaviorList(BehaviorAnalysisQueryDTO query);

    /**
     * 获取行为分析详情（按分析记录ID）
     * @param id 分析记录ID
     * @return 详情
     */
    BehaviorAnalysisVO getBehaviorDetail(Long id);

    /**
     * 获取员工行为分析详情（按员工ID）
     * @param employeeId 员工ID
     * @return 详情
     */
    BehaviorAnalysisVO getEmployeeBehaviorDetail(Long employeeId);

    /**
     * 获取人员行为统计数据
     * @param orgId 组织ID（可选）
     * @return 统计数据
     */
    BehaviorStatisticsVO getBehaviorStatistics(Long orgId);
}
