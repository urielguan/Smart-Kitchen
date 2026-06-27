package com.xykj.sys.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 新增字典项
 */
@Data
public class DictCategoryCreateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请选择分类大类")
    private String categoryType;

    @NotBlank(message = "请输入分类项编码")
    @Size(max = 50, message = "分类项编码长度不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "分类项编码只能包含字母、数字、下划线和短横线")
    private String dictCode;

    @NotBlank(message = "请输入分类项名称")
    @Size(max = 50, message = "分类项名称长度不能超过50个字符")
    private String dictName;

    @Max(value = 99999, message = "排序号不能超过99999")
    private Integer sortOrder = 0;

    @Pattern(regexp = "^(active|inactive)?$", message = "状态值只能是active或inactive")
    private String status = "active";

    @Digits(integer = 14, fraction = 4, message = "物料类别统一面积系数最多支持14位整数和4位小数")
    @DecimalMin(value = "0.0001", message = "物料类别统一面积系数必须大于0")
    private BigDecimal areaCoefficient;

    @Pattern(regexp = "^(system|manual|ai)?$", message = "面积系数来源值不合法")
    private String areaCoefficientSource;

    @Digits(integer = 14, fraction = 4, message = "AI建议面积系数最多支持14位整数和4位小数")
    @DecimalMin(value = "0.0001", message = "AI建议面积系数必须大于0")
    private BigDecimal aiSuggestedAreaCoefficient;

    @Size(max = 300, message = "AI建议依据摘要长度不能超过300个字符")
    private String aiSuggestionReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime aiSuggestionGeneratedAt;

    @Size(max = 200, message = "备注长度不能超过200个字符")
    private String remark;
}
