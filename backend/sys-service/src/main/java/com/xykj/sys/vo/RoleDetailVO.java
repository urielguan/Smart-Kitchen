package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 角色详情VO
 */
@Data
public class RoleDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID
     */
    private Long id;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String roleDesc;

    /**
     * 所属分组ID
     */
    private Long groupId;

    /**
     * 所属分组名称
     */
    private String groupName;

    /**
     * 所属分组是否可见
     */
    private Boolean groupVisible;

    /**
     * 角色类型
     */
    private String roleType;

    /**
     * 状态
     */
    private String status;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 所属组织名称
     */
    private String orgName;

    /**
     * 数据权限范围
     */
    private String dataScope;

    /**
     * 数据权限组织ID列表
     */
    private List<Long> dataScopeOrgIds;

    /**
     * 功能权限列表
     */
    private List<String> funcPermissions;

    /**
     * 功能权限名称映射（permissionCode -> permissionName）
     */
    private Map<String, String> funcPermissionNameMap;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 成员数量
     */
    private Integer memberCount;
}
