package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色成员VO
 */
@Data
public class RoleMemberVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 员工ID
     */
    private Long id;

    /**
     * 员工编号
     */
    private String employeeNo;

    /**
     * 员工姓名
     */
    private String realName;

    /**
     * 所属组织名称
     */
    private String orgName;

    /**
     * 职位
     */
    private String position;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 员工状态（在职/离职）
     */
    private String employeeStatus;

    /**
     * 账号状态（启用/禁用/锁定）
     */
    private String status;

    /**
     * 加入角色时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime joinedAt;
}
