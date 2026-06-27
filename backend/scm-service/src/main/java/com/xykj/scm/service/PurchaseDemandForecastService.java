package com.xykj.scm.service;

import com.xykj.common.result.PageResult;
import com.xykj.scm.dto.PurchaseDemandForecastGenerateDTO;
import com.xykj.scm.dto.PurchaseDemandForecastPrefillDTO;
import com.xykj.scm.dto.PurchaseDemandForecastQueryDTO;
import com.xykj.scm.vo.PurchaseDemandForecastLinkedPlanVO;
import com.xykj.scm.vo.PurchaseDemandForecastDashboardVO;
import com.xykj.scm.vo.PurchaseDemandForecastMaterialLinkageVO;
import com.xykj.scm.vo.PurchaseDemandForecastPlanPrefillVO;
import com.xykj.scm.vo.PurchaseDemandForecastVO;

import java.util.List;

/**
 * 采购需求预测服务
 */
public interface PurchaseDemandForecastService {

    PurchaseDemandForecastVO generate(PurchaseDemandForecastGenerateDTO dto);

    PageResult<PurchaseDemandForecastVO> list(PurchaseDemandForecastQueryDTO query);

    PurchaseDemandForecastVO getDetail(Long id);

    List<PurchaseDemandForecastLinkedPlanVO> getLinkedPlans(Long id);

    PurchaseDemandForecastDashboardVO getDashboard(Long orgId);

    PurchaseDemandForecastDashboardVO refreshAnalytics(Long orgId);

    PurchaseDemandForecastPlanPrefillVO buildPurchasePlanPrefill(PurchaseDemandForecastPrefillDTO dto);

    PurchaseDemandForecastMaterialLinkageVO getMaterialLinkage(String forecastNo, Long excludePlanId);
}
