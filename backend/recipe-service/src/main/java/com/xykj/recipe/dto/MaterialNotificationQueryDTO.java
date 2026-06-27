package com.xykj.recipe.dto;

import lombok.Data;

import java.util.List;

/**
 * 物料通知查询DTO
 */
@Data
public class MaterialNotificationQueryDTO {

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 20;

    /**
     * 通知类型: expiring=临期, expired=已过期, low_stock=库存不足
     */
    private String notificationType;

    /**
     * 状态: unread=未读, read=已读, handled=已处理, dismissed=已忽略
     */
    private String status;

    /**
     * 优先级: high=高, medium=中, low=低
     */
    private String priority;

    /**
     * 组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 关键字搜索（物料名称）
     */
    private String keyword;
}
