package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 字典项状态变更
 */
@Data
public class DictCategoryStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "请选择状态")
    @Pattern(regexp = "^(active|inactive)$", message = "状态值只能是active或inactive")
    private String status;
}
