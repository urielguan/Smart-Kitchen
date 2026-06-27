package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 员工更新DTO
 */
@Data
public class EmployeeUpdateDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 账号状态：active=启用，inactive=禁用，locked=锁定
     * 修改此字段会同步更新关联的用户账号状态
     */
    private String accountStatus;

    /**
     * 性别：male/female（前端传入，后端转换为0/1/2）
     */
    private String gender;

    /**
     * 身份证号
     */
    private String idCard;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 所属部门
     */
    private String department;

    /**
     * 岗位
     */
    private String position;

    /**
     * 入职日期
     */
    private LocalDate hireDate;

    /**
     * 状态：active=在职，left=离职
     */
    private String status;

    /**
     * 角色ID列表
     */
    private List<Long> roleIds;

    /**
     * 备注
     */
    private String remark;
}