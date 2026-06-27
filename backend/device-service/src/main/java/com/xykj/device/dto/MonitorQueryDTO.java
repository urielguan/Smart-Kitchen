package com.xykj.device.dto;

import lombok.Data;

import java.util.List;

/**
 * 监控查询DTO
 */
@Data
public class MonitorQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 12;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;

    /** 在线状态：online/offline */
    private String onlineStatus;

    /** 位置（模糊搜索） */
    private String location;
}
