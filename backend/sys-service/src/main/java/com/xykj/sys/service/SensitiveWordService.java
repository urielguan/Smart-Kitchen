package com.xykj.sys.service;

/**
 * 敏感词校验服务
 */
public interface SensitiveWordService {

    /**
     * 校验文本是否包含敏感词
     * @param text 待校验文本
     * @return true 表示包含敏感词
     */
    boolean containsSensitiveWord(String text);

    /**
     * 刷新敏感词库（从数据库重新加载）
     */
    void refreshWordMap();
}
