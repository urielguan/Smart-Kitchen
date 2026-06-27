package com.xykj.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.controller.RecipePlanController;
import com.xykj.recipe.dto.RecipePlanQueryDTO;
import com.xykj.recipe.vo.AdjustmentAuditResultVO;
import com.xykj.recipe.vo.AdjustmentResultVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentDetailVO;
import com.xykj.recipe.vo.RecipePlanAdjustmentVO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 菜谱计划调整申请服务接口
 */
public interface RecipePlanAdjustmentService {

    /**
     * 创建调整申请
     */
    AdjustmentResultVO createAdjustment(Long planId, RecipePlanController.AdjustmentDTO dto);

    /**
     * 审核调整申请
     */
    AdjustmentAuditResultVO auditAdjustment(Long adjustmentId, String status, String remark);

    /**
     * 分页查询调整申请列表
     */
    Page<RecipePlanAdjustmentVO> list(RecipePlanQueryDTO query);

    /**
     * 获取调整申请详情
     */
    RecipePlanAdjustmentDetailVO getDetail(Long id);

    /**
     * 导出调整申请列表
     */
    void exportAdjustments(RecipePlanQueryDTO query, HttpServletResponse response);
}
