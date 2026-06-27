package com.xykj.device.vo;

import lombok.Data;

/**
 * AI人员行为统计VO
 */
@Data
public class BehaviorStatisticsVO {

    /** 总员工数 */
    private Long totalEmployees;

    /** 平均效率评分 */
    private Double averageEfficiency;

    /** 平均合规评分 */
    private Double averageCompliance;

    /** 平均卫生评分 */
    private Double averageHygiene;

    /** 需改进人数 */
    private Long needImprovementCount;

    /** 标杆人数（优秀员工） */
    private Long benchmarkCount;

    /** 今日分析次数 */
    private Long todayAnalysisCount;

    /** 发现问题数 */
    private Long issueCount;

    /** 各评分区间人数分布 */
    private ScoreDistribution efficiencyDistribution;
    private ScoreDistribution complianceDistribution;
    private ScoreDistribution hygieneDistribution;

    /** 评分分布 */
    @Data
    public static class ScoreDistribution {
        /** 优秀(90-100) */
        private Long excellent;
        /** 良好(80-89) */
        private Long good;
        /** 一般(70-79) */
        private Long average;
        /** 较差(60-69) */
        private Long poor;
        /** 不及格(<60) */
        private Long fail;
    }
}
