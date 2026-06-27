package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色创建DTO
 */
@Data
public class RoleCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 角色编码
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50个字符")
    private String roleCode;

    /**
     * 角色名称
     */
    @NotBlank(message = "角色名称不能为空")
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
    @NotNull(message = "所属分组不能为空")
    private Long groupId;

    /**
     * 角色类型
     */
    private String roleType = "custom";

    /**
     * 状态
     */
    private String status = "active";

    /**
     * 排序序号
     */
    private Integer sortOrder = 0;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 数据权限范围
     */
    private String dataScope = "all";

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
