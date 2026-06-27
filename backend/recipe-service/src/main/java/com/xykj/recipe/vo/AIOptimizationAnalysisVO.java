package com.xykj.recipe.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI智能菜谱优化分析VO
 */
@Data
public class AIOptimizationAnalysisVO {

    /**
     * 菜谱ID
     */
    private Long recipeId;

    /**
     * 菜谱名称
     */
    private String recipeName;

    /**
     * 综合看板
     */
    private ComprehensiveDashboard comprehensiveDashboard;

    /**
     * 成本分析
     */
    private CostAnalysis costAnalysis;

    /**
     * 投诉反馈分析
     */
    private ComplaintAnalysis complaintAnalysis;

    /**
     * 优化建议列表
     */
    private List<OptimizationSuggestion> optimizationSuggestions;

    @Data
    public static class ComprehensiveDashboard {
        private BigDecimal costPercentVsAvg;
        private Integer nutritionScore;
        private BigDecimal reviewScore;
        private Integer complaintCount;
    }

    @Data
    public static class CostAnalysis {
        private List<RecentPurchase> recentPurchases;
        private List<HighCostAlert> highCostAlerts;
    }

    @Data
    public static class RecentPurchase {
        private String materialName;
        private BigDecimal unitPrice;
        private LocalDateTime purchaseDate;
    }

    @Data
    public static class HighCostAlert {
        private String materialName;
        private String reason;
        private BigDecimal currentPrice;
        private BigDecimal avgPrice;
        private String aiSuggestion;
    }

    @Data
    public static class ComplaintAnalysis {
        private Integer tasteIssues;
        private Integer qualityIssues;
        private Integer portionIssues;
        private Integer otherIssues;
        private String complaintSuggestions;
        private List<RecentReview> recentReviews;
    }

    @Data
    public static class RecentReview {
        private Integer score;
        private String content;
        private LocalDateTime reviewTime;
    }

    @Data
    public static class OptimizationSuggestion {
        private String suggestionName;
        private String source;
        private String priority;
        private String description;
        private String improvementTrend;
    }
}
