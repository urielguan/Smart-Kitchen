package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 消息通知查询DTO
 */
@Data
public class NotificationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    /** 关键字（标题模糊） */
    private String keyword;

    /** 消息大类 */
    private String category;

    /** 消息子类 */
    private String subCategory;

    /** 阅读状态: unread/read */
    private String readStatus;

    /** 处理状态 */
    private String processStatus;

    /** 风险等级 */
    private String riskLevel;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;
}
