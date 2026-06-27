package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 组织树节点VO
 */
@Data
public class OrganizationTreeVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织ID
     */
    private Long id;

    /**
     * 组织编码
     */
    private String orgCode;

    /**
     * 组织名称
     */
    private String orgName;

    /**
     * 组织类型：group/company/canteen/dept
     */
    private String orgType;

    /**
     * 父组织ID
     */
    private Long parentId;

    /**
     * 父组织名称
     */
    private String parentName;

    /**
     * 组织层级
     */
    private Integer level;

    /**
     * 状态：active/inactive
     */
    private String status;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 负责人姓名
     */
    private String leaderName;

    /**
     * 成员数量（员工模块未实现，暂时返回0）
     */
    private Integer memberCount;

    /**
     * 子组织列表
     */
    private List<OrganizationTreeVO> children;
}
