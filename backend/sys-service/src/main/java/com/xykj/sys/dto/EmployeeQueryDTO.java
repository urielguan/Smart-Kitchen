package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 员工查询DTO
 */
@Data
public class EmployeeQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码（默认1）
     */
    private Integer pageNum = 1;

    /**
     * 每页条数（默认10，最大100）
     */
    private Integer pageSize = 10;

    /**
     * 搜索关键字（匹配员工编号、姓名、手机号）
     */
    private String keyword;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;

    /**
     * 账号状态：active/inactive
     */
    private String accountStatus;

    /**
     * 员工状态：active(在职)/left(离职)
     */
    private String status;
}