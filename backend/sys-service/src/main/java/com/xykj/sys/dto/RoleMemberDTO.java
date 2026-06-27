package com.xykj.sys.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 角色成员DTO
 */
@Data
public class RoleMemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 员工ID列表
     */
    @NotEmpty(message = "员工ID列表不能为空")
    private List<Long> employeeIds;
}
