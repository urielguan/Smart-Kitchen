package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料类别面积系数 AI 建议请求
 */
@Data
public class DictCategoryAreaSuggestionDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请选择分类大类")
    private String categoryType;

    @NotBlank(message = "请输入分类项名称")
    @Size(max = 50, message = "分类项名称长度不能超过50个字符")
    private String dictName;
}
