package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 组织新增DTO
 */
@Data
public class OrganizationCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织编码（唯一）
     */
    @NotBlank(message = "组织编码不能为空")
    @Size(max = 50, message = "组织编码长度不能超过50个字符")
    private String orgCode;

    /**
     * 组织名称
     */
    @NotBlank(message = "组织名称不能为空")
    @Size(max = 100, message = "组织名称长度不能超过100个字符")
    private String orgName;

    /**
     * 组织类型：group/company/canteen/dept
     */
    @NotBlank(message = "组织类型不能为空")
    private String orgType;

    /**
     * 父组织ID（不传则为顶级组织）
     */
    private Long parentId;

    /**
     * 负责人姓名
     */
    private String leaderName;

    /**
     * 联系电话
     */
    @Size(max = 20, message = "联系电话长度不能超过20个字符")
    private String contactPhone;

    /**
     * 地址
     */
    @Size(max = 200, message = "地址长度不能超过200个字符")
    private String address;

    /**
     * 状态：active/inactive
     */
    private String status;

    /**
     * 排序序号
     */
    private Integer sortOrder;
}
