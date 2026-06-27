package com.xykj.scm.controller;

import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.scm.dto.PurchaseDemandForecastGenerateDTO;
import com.xykj.scm.dto.PurchaseDemandForecastPrefillDTO;
import com.xykj.scm.dto.PurchaseDemandForecastQueryDTO;
import com.xykj.scm.service.PurchaseDemandForecastService;
import com.xykj.scm.vo.PurchaseDemandForecastDashboardVO;
import com.xykj.scm.vo.PurchaseDemandForecastLinkedPlanVO;
import com.xykj.scm.vo.PurchaseDemandForecastMaterialLinkageVO;
import com.xykj.scm.vo.PurchaseDemandForecastPlanPrefillVO;
import com.xykj.scm.vo.PurchaseDemandForecastVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 采购需求预测接口
 */
@RestController
@RequestMapping("/api/v1/scm/purchase-demand-forecasts")
@RequiredArgsConstructor
public class PurchaseDemandForecastController {

    private final PurchaseDemandForecastService purchaseDemandForecastService;

    @PostMapping("/generate")
    public R<PurchaseDemandForecastVO> generate(@Valid @RequestBody PurchaseDemandForecastGenerateDTO dto) {
        return R.ok(purchaseDemandForecastService.generate(dto));
    }

    @GetMapping
    public R<PageResult<PurchaseDemandForecastVO>> list(@Valid PurchaseDemandForecastQueryDTO query) {
        return R.ok(purchaseDemandForecastService.list(query));
    }

    @GetMapping("/{id:\\d+}")
    public R<PurchaseDemandForecastVO> detail(@PathVariable Long id) {
        return R.ok(purchaseDemandForecastService.getDetail(id));
    }

    @GetMapping("/dashboard")
    public R<PurchaseDemandForecastDashboardVO> dashboard(@RequestParam(required = false) Long orgId) {
        return R.ok(purchaseDemandForecastService.getDashboard(orgId));
    }

    @PostMapping("/refresh-analytics")
    public R<PurchaseDemandForecastDashboardVO> refreshAnalytics(@RequestParam(required = false) Long orgId) {
        return R.ok(purchaseDemandForecastService.refreshAnalytics(orgId));
    }

    @GetMapping("/{id:\\d+}/linked-plans")
    public R<List<PurchaseDemandForecastLinkedPlanVO>> linkedPlans(@PathVariable Long id) {
        return R.ok(purchaseDemandForecastService.getLinkedPlans(id));
    }

    @GetMapping("/material-linkage")
    public R<PurchaseDemandForecastMaterialLinkageVO> materialLinkage(
            @RequestParam String forecastNo,
            @RequestParam(required = false) Long excludePlanId
    ) {
        return R.ok(purchaseDemandForecastService.getMaterialLinkage(forecastNo, excludePlanId));
    }

    @PostMapping("/purchase-plan-prefill")
    public R<PurchaseDemandForecastPlanPrefillVO> purchasePlanPrefill(
            @Valid @RequestBody PurchaseDemandForecastPrefillDTO dto
    ) {
        return R.ok(purchaseDemandForecastService.buildPurchasePlanPrefill(dto));
    }
}
