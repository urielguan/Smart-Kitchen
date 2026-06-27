package com.xykj.sys.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色更新DTO
 */
@Data
public class RoleUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色ID（由URL路径参数设置）
     */
    private Long id;

    /**
     * 角色编码
     */
    @Size(max = 50, message = "角色编码长度不能超过50个字符")
    private String roleCode;

    /**
     * 角色名称
     */
    @Size(max = 50, message = "角色名称长度不能超过50个字符")
    private String roleName;

    /**
     * 角色描述
     */
    @Size(max = 200, message = "角色描述长度不能超过200个字符")
    private String roleDesc;

    /**
     * 所属分组ID
     */
    private Long groupId;

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
     * 备注
     */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;
}
