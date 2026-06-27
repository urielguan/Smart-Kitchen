package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 组织查询DTO
 */
@Data
public class OrganizationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码（默认1）
     */
    private Integer pageNum = 1;

    /**
     * 每页条数（默认20，最大100）
     */
    private Integer pageSize = 20;

    /**
     * 搜索关键字（同时匹配组织名称和编码）
     */
    private String keyword;

    /**
     * 组织名称（模糊搜索）
     */
    private String orgName;

    /**
     * 组织编码
     */
    private String orgCode;

    /**
     * 组织类型：group/company/canteen/dept
     */
    private String orgType;

    /**
     * 状态：active/inactive
     */
    private String status;

    /**
     * 父组织ID（获取子组织列表）
     */
    private Long parentId;

    /**
     * 是否返回树形结构（默认false）
     */
    private Boolean treeMode = false;

    /**
     * 搜索关键字时是否包含匹配组织的下级组织
     */
    private Boolean includeChildren = false;
}
