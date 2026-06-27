package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 新增投诉DTO
 */
@Data
public class ComplaintCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 投诉类型：food/service/hygiene/other
     */
    @NotBlank(message = "投诉类型不能为空")
    private String complaintType;

    /**
     * 来源：meal/supervision/manual
     */
    @NotBlank(message = "来源不能为空")
    private String source;

    /**
     * 投诉标题
     */
    @NotBlank(message = "投诉标题不能为空")
    private String title;

    /**
     * 投诉描述
     */
    private String description;

    /**
     * 投诉人ID
     */
    @NotNull(message = "投诉人ID不能为空")
    private Long submitterId;

    /**
     * 投诉人姓名
     */
    @NotBlank(message = "投诉人姓名不能为空")
    private String submitterName;

    /**
     * 投诉人电话
     */
    private String submitterPhone;

    /**
     * 关联菜品ID
     */
    private Long relatedMenuId;

    /**
     * 关联菜品名称
     */
    private String relatedMenuName;

    /**
     * 投诉图片
     */
    private List<String> images;

    /**
     * 优先级：high/medium/low
     */
    private String priority;

    /**
     * 所属组织ID
     */
    @NotNull(message = "组织ID不能为空")
    private Long orgId;
}
