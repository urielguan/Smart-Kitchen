package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 消息通知统计 VO（Header 使用）
 */
@Data
public class NotificationStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 未读总数 */
    private Integer totalUnread;

    /** 按分类的未读数 */
    private Map<String, Integer> categoryCounts;
}
