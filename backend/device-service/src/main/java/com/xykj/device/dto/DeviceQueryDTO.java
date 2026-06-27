package com.xykj.device.dto;

import lombok.Data;

import java.util.List;

/**
 * 设备列表查询参数
 */
@Data
public class DeviceQueryDTO {

    private Long pageNum = 1L;
    private Long pageSize = 20L;

    /** 设备类型筛选 */
    private String deviceType;

    /** 在线状态筛选：online/offline/fault */
    private String onlineStatus;

    /** 状态筛选：active/inactive/maintenance */
    private String status;

    /** 设备名称（模糊搜索，同时匹配名称和编码） */
    private String deviceName;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;
}
