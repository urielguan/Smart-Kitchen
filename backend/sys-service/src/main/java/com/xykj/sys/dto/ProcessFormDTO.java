package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 处理工单表单DTO
 */
@Data
public class ProcessFormDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 操作类型：process=标记处理中，complete=完成，cancel=取消
     */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /**
     * 处理内容
     */
    @NotBlank(message = "处理内容不能为空")
    private String content;

    /**
     * 处理图片
     */
    private List<String> images;
}
