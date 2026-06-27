package com.xykj.device.dto;

import lombok.Data;

import java.util.List;

/**
 * 告警派单工单查询参数
 */
@Data
public class AlertDispatchQueryDTO {

    private Integer pageNum = 1;
    private Integer pageSize = 20;

    /** 数据权限 - 组织ID */
    private Long orgId;

    /** 数据权限 - 组织ID列表 */
    private List<Long> orgIds;

    /** 状态筛选 */
    private String status;

    /** 派单方式筛选 */
    private String dispatchType;

    /** 处理人姓名模糊搜索 */
    private String handlerName;

    /** 开始时间 */
    private String startTime;

    /** 结束时间 */
    private String endTime;

    /** 当前登录用户对应的员工ID（用于匹配 handler_id） */
    private Long handlerEmployeeId;
}
