package com.xykj.sys.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色分组更新DTO
 */
@Data
public class RoleGroupUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 分组ID（由URL路径参数设置）
     */
    private Long id;

    /**
     * 分组名称
     */
    @Size(max = 50, message = "分组名称长度不能超过50个字符")
    private String groupName;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 备注
     */
    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;
}
