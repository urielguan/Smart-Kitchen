package com.xykj.sample.vo;

import lombok.Data;

/**
 * 追溯链VO
 * 串联：菜谱计划 -> 烹饪任务 -> 留样记录 -> 销样
 */
@Data
public class TraceChainVO {
    /** 菜谱计划编号 */
    private String planCode;
    /** 烹饪任务编号 */
    private String taskNo;
    /** 厨师 */
    private String chefName;
    /** 留样编号 */
    private String sampleNo;
    /** 留样人 */
    private String sampledByName;
    /** 销样人 */
    private String disposalByName;
}
