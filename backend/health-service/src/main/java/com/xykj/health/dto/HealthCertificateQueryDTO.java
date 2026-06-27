package com.xykj.health.dto;

import lombok.Data;

import java.util.List;

/**
 * 健康证查询DTO
 */
@Data
public class HealthCertificateQueryDTO {

    /** 页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 状态 */
    private String status;

    /** 搜索关键字（员工姓名/编号/健康证编号） */
    private String keyword;

    /** 组织ID */
    private Long orgId;

    /** 数据权限注入组织ID列表 */
    private List<Long> orgIds;

    /** 员工姓名 */
    private String employeeName;

    /** 即将过期天数 */
    private Integer withinDays = 30;

    /** 是否显示离职员工，默认否 */
    private Boolean showLeftEmployees = false;
}
