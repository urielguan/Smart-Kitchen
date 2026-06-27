package com.xykj.sample.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI智能评估结果VO
 */
@Data
public class AiEvaluateVO {

    private Long id;

    /** 综合得分（0-100） */
    private BigDecimal finalScore;

    /** 星级（1-5） */
    private Integer starLevel;

    /** 维度评分 */
    private DimensionScores dimensionScores;

    /** 维度分析文本 */
    private DimensionAnalysis dimensionAnalysis;

    /** 优化建议列表 */
    private List<String> suggestions;

    /** 风险等级：low/medium/high */
    private String riskLevel;

    @Data
    public static class DimensionScores {
        /** 色泽评分 */
        private BigDecimal colorScore;
        /** 形态评分 */
        private BigDecimal shapeScore;
        /** 熟度评分 */
        private BigDecimal donenessScore;
    }

    @Data
    public static class DimensionAnalysis {
        /** 色泽分析 */
        private String colorAnalysis;
        /** 形态分析 */
        private String shapeAnalysis;
        /** 熟度分析 */
        private String donenessAnalysis;
    }
}
