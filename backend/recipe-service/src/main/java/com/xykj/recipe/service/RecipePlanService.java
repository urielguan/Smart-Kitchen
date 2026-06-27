package com.xykj.recipe.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.recipe.dto.*;
import com.xykj.recipe.controller.RecipePlanController;
import com.xykj.recipe.vo.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 菜谱计划服务接口
 */
public interface RecipePlanService {

    /**
     * 分页查询计划列表
     */
    Page<RecipePlanVO> list(RecipePlanQueryDTO query);

    /**
     * 获取计划详情
     */
    RecipePlanDetailVO getDetail(Long id);

    /**
     * 创建计划
     */
    Long create(RecipePlanCreateDTO dto);

    /**
     * 更新计划
     */
    RecipePlanDetailVO update(Long id, RecipePlanCreateDTO dto);

    /**
     * 删除计划
     */
    void delete(Long id);

    /**
     * 提交审核
     * @return 提交后的状态
     */
    String submit(Long id);

    /**
     * 审核计划
     * @return 审核结果，包含是否生成烹饪任务
     */
    AuditResultVO audit(Long id, String status, String remark);

    /**
     * AI营养评估
     */
    Object getAiNutritionAssessment(Long id);

    /**
     * AI推荐菜谱
     */
    List<RecipeVO> getAiRecommendRecipes(RecipePlanQueryDTO query);

    /**
     * 调整申请
     * @param planId 计划ID
     * @param dto 调整申请DTO
     * @return 调整结果
     */
    AdjustmentResultVO createAdjustment(Long planId, RecipePlanController.AdjustmentDTO dto);

    /**
     * 审核调整申请
     * @return 审核结果
     */
    AdjustmentAuditResultVO auditAdjustment(Long adjustmentId, String status, String remark);

    /**
     * 分页查询调整申请列表
     */
    Page<RecipePlanAdjustmentVO> listAdjustments(RecipePlanQueryDTO query);

    /**
     * 获取调整申请详情
     */
    RecipePlanAdjustmentDetailVO getAdjustmentDetail(Long id);

    /**
     * AI推荐菜谱（增强版，支持预算和周计划）
     */
    AIRecommendResultVO getAiRecommendRecipesEnhanced(RecipePlanQueryDTO query);

    /**
     * 获取计划统计数据
     */
    RecipePlanStatisticsVO getStatistics();

    /**
     * 按 T-7 / T-3 / T-1 节奏自动复检库存风险
     * @return 本次复检的计划数量
     */
    int autoRecheckStockRiskByCadence();

    /**
     * 导出计划列表
     */
    void exportPlans(RecipePlanQueryDTO query, HttpServletResponse response);

    /**
     * 下载菜谱计划导入模板
     */
    void downloadImportTemplate(HttpServletResponse response);

    /**
     * 导入菜谱计划
     */
    RecipePlanImportResultDTO importPlans(MultipartFile file);

    /**
     * 下载导入错误文件
     */
    void downloadImportErrorFile(String fileName, HttpServletResponse response);

    /**
     * 驳回后重新提交菜谱计划
     */
    RecipePlanDetailVO resubmit(Long id, RecipePlanCreateDTO dto);

    /**
     * 获取计划审批历史
     */
    List<RecipePlanAuditLogVO> getAuditLog(Long planId);

    /**
     * 导出调整申请列表
     */
    void exportAdjustments(RecipePlanQueryDTO query, HttpServletResponse response);

    /**
     * 复制菜谱计划
     * @param sourceId 源计划ID
     * @return 复制结果（含异常检测）
     */
    CopyPlanResultVO copyPlan(Long sourceId);

    /**
     * 撤回已审核的菜谱计划
     * @param planId 计划ID
     * @param reason 撤回原因
     */
    void withdraw(Long planId, String reason);

    /**
     * 批量删除菜谱计划
     * @param planIds 计划ID列表
     * @return 批量操作结果
     */
    BatchOperationResultVO batchDelete(List<Long> planIds);

    /**
     * 批量审核菜谱计划
     * @param planIds 计划ID列表
     * @param status 审核状态
     * @param remark 备注
     * @return 批量操作结果
     */
    BatchOperationResultVO batchAudit(List<Long> planIds, String status, String remark);
}
