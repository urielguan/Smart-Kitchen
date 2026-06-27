package com.xykj.recipe.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.controller.RecipePlanController;
import com.xykj.recipe.dto.RecipePlanQueryDTO;
import com.xykj.recipe.service.RecipePlanAdjustmentService;
import com.xykj.recipe.service.RecipePlanService;
import com.xykj.recipe.vo.AdjustmentAuditResultVO;
import com.xykj.recipe.vo.AdjustmentResultVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentDetailVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 菜谱计划调整申请服务实现
 *
 * 说明：复用既有 RecipePlanService 中已经稳定运行的调整申请业务逻辑，
 * 仅将调整申请能力收口到独立 service，避免改动原有规则和状态流转。
 */
@Service
@RequiredArgsConstructor
public class RecipePlanAdjustmentServiceImpl implements RecipePlanAdjustmentService {

    private final RecipePlanService planService;

    @Override
    public AdjustmentResultVO createAdjustment(Long planId, RecipePlanController.AdjustmentDTO dto) {
        return planService.createAdjustment(planId, dto);
    }

    @Override
    public AdjustmentAuditResultVO auditAdjustment(Long adjustmentId, String status, String remark) {
        return planService.auditAdjustment(adjustmentId, status, remark);
    }

    @Override
    public Page<RecipePlanAdjustmentVO> list(RecipePlanQueryDTO query) {
        return planService.listAdjustments(query);
    }

    @Override
    public RecipePlanAdjustmentDetailVO getDetail(Long id) {
        return planService.getAdjustmentDetail(id);
    }

    @Override
    public void exportAdjustments(RecipePlanQueryDTO query, HttpServletResponse response) {
        planService.exportAdjustments(query, response);
    }
}
