package com.xykj.sys.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.SupervisionDashboardQueryDTO;
import com.xykj.sys.dto.SupervisionTraceQueryDTO;
import com.xykj.sys.dto.SupervisionViolationQueryDTO;
import com.xykj.sys.service.SupervisionService;
import com.xykj.sys.vo.SupervisionDashboardVO;
import com.xykj.sys.vo.SupervisionTraceVO;
import com.xykj.sys.vo.SupervisionViolationDetailVO;
import com.xykj.sys.vo.SupervisionViolationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据监管控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sys/supervision")
@RequiredArgsConstructor
public class SupervisionController {

    private final SupervisionService supervisionService;

    /**
     * 数据监管看板首页
     */
    @GetMapping("/dashboard")
    public R<SupervisionDashboardVO> getDashboard(SupervisionDashboardQueryDTO query) {
        return R.ok(supervisionService.getDashboard(query));
    }

    /**
     * 违规记录列表
     */
    @GetMapping("/violations")
    public R<PageResult<SupervisionViolationVO>> listViolations(SupervisionViolationQueryDTO query) {
        return R.ok(supervisionService.listViolations(query));
    }

    /**
     * 违规记录详情
     */
    @GetMapping("/violations/{id}")
    public R<SupervisionViolationDetailVO> getViolationDetail(@PathVariable Long id) {
        return R.ok(supervisionService.getViolationDetail(id));
    }

    /**
     * 溯源响应记录列表
     */
    @GetMapping("/traces")
    public R<PageResult<SupervisionTraceVO>> listTraces(SupervisionTraceQueryDTO query) {
        return R.ok(supervisionService.listTraces(query));
    }
}
