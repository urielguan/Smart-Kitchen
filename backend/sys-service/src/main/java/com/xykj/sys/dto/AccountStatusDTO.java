package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 账号状态修改DTO
 */
@Data
public class AccountStatusDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 账号状态：active=启用，inactive=禁用
     */
    @NotBlank(message = "账号状态不能为空")
    private String accountStatus;
}