package com.xykj.sys.controller;

import com.xykj.common.result.R;
import com.xykj.sys.dto.RegulatoryDashboardQueryDTO;
import com.xykj.sys.service.RegulatoryDashboardService;
import com.xykj.sys.vo.RegulatoryDashboardDataVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 监管看板首页控制器
 */
@RestController
@RequestMapping("/api/v1/dashboard/regulatory")
@RequiredArgsConstructor
public class RegulatoryDashboardController {

    private final RegulatoryDashboardService regulatoryDashboardService;

    /**
     * 首页快照
     */
    @GetMapping("/home")
    public R<RegulatoryDashboardDataVO> getHomeSnapshot(RegulatoryDashboardQueryDTO query) {
        return R.ok(regulatoryDashboardService.getHomeSnapshot(query));
    }
}
