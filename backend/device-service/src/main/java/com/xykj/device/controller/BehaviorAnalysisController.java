package com.xykj.device.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.device.dto.BehaviorAnalysisQueryDTO;
import com.xykj.device.service.BehaviorAnalysisService;
import com.xykj.device.vo.BehaviorAnalysisVO;
import com.xykj.device.vo.BehaviorStatisticsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI人员行为分析控制器
 * API路径: /api/v1/device/behavior-analysis
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/device/behavior-analysis")
@RequiredArgsConstructor
public class BehaviorAnalysisController {

    private final BehaviorAnalysisService behaviorAnalysisService;

    /**
     * 获取人员行为分析列表
     * GET /api/v1/device/behavior-analysis
     */
    @GetMapping
    public R<PageResult<BehaviorAnalysisVO>> getBehaviorList(BehaviorAnalysisQueryDTO query) {
        Page<BehaviorAnalysisVO> page = behaviorAnalysisService.getBehaviorList(query);
        return R.ok(PageResult.of(page));
    }

    /**
     * 获取行为分析详情（按分析记录ID）
     * GET /api/v1/device/behavior-analysis/{id}
     */
    @GetMapping("/{id}")
    public R<BehaviorAnalysisVO> getBehaviorDetail(@PathVariable Long id) {
        BehaviorAnalysisVO vo = behaviorAnalysisService.getBehaviorDetail(id);
        return R.ok(vo);
    }

    /**
     * 获取员工行为分析详情（按员工ID）
     * GET /api/v1/device/behavior-analysis/employee/{employeeId}
     */
    @GetMapping("/employee/{employeeId}")
    public R<BehaviorAnalysisVO> getEmployeeBehaviorDetail(@PathVariable Long employeeId) {
        BehaviorAnalysisVO vo = behaviorAnalysisService.getEmployeeBehaviorDetail(employeeId);
        return R.ok(vo);
    }

    /**
     * 获取人员行为统计数据
     * GET /api/v1/device/behavior-analysis/statistics
     */
    @GetMapping("/statistics")
    public R<BehaviorStatisticsVO> getStatistics(@RequestParam(required = false) Long orgId) {
        BehaviorStatisticsVO vo = behaviorAnalysisService.getBehaviorStatistics(orgId);
        return R.ok(vo);
    }
}
