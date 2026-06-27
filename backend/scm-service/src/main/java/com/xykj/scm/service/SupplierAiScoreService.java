package com.xykj.scm.service;

import com.xykj.scm.vo.SupplierVO;

import java.util.Collection;
import java.util.List;

/**
 * 供应商 AI 综合评分服务
 */
public interface SupplierAiScoreService {

    /**
     * 批量刷新全部供应商 AI 评分
     */
    void refreshAllSupplierScores();

    /**
     * 按供应商ID刷新 AI 评分
     */
    void refreshSupplierScores(Collection<Long> supplierIds);

    /**
     * 为详情 VO 补充 AI 评分元信息
     */
    void enrichScoreMeta(SupplierVO supplierVO);

    /**
     * 为列表/详情 VO 批量补充 AI 评分元信息
     */
    void enrichScoreMeta(List<SupplierVO> supplierVOList);
}
