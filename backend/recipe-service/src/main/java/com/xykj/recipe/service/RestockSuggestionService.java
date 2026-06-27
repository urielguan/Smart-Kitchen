package com.xykj.recipe.service;

import java.math.BigDecimal;

/**
 * 补货建议服务 - 根据物料规格/参考重量生成智能补货建议
 */
public interface RestockSuggestionService {

    /**
     * 生成补货建议
     *
     * @param materialId   物料ID
     * @param materialName 物料名称（用于模糊匹配参考表）
     * @param shortageQty  缺货数量
     * @param unit         缺货单位（g、kg等）
     * @return 建议文本如 "1000g（约7个土豆）"，无法生成时返回null
     */
    String generateSuggestion(Long materialId, String materialName,
                              BigDecimal shortageQty, String unit);
}
