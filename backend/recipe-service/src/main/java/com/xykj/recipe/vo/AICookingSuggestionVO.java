package com.xykj.recipe.vo;

import lombok.Data;

/**
 * AI烹饪参数建议VO
 */
@Data
public class AICookingSuggestionVO {

    /**
     * 建议烹饪时长（分钟）
     */
    private Integer suggestedTime;

    /**
     * 建议最低温度（℃）
     */
    private Integer suggestedTempMin;

    /**
     * 建议最高温度（℃）
     */
    private Integer suggestedTempMax;

    /**
     * 建议原因
     */
    private String reason;

    /**
     * 食品安全标准说明
     */
    private String foodSafetyStandard;
}
