package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色分组创建DTO
 */
@Data
public class RoleGroupCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分组名称
     */
    @NotBlank(message = "分组名称不能为空")
    @Size(max = 50, message = "分组名称长度不能超过50个字符")
    private String groupName;

    /**
     * 所属组织ID（NULL=全局分组）
     */
    private Long orgId;

    /**
     * 排序序号
     */
    private Integer sortOrder = 0;

    /**
     * 备注
     */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;
}
