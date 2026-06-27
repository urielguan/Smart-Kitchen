package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 组织状态更新DTO
 */
@Data
public class OrganizationStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标状态：active(启用)/inactive(禁用)
     */
    @NotBlank(message = "状态不能为空")
    private String status;
}
