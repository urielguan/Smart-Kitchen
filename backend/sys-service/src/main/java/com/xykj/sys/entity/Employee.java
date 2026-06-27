package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 员工实体类
 * 对应数据库表: sys_employee
 */
@Data
@TableName("sys_employee")
public class Employee implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 员工ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 员工工号
     */
    private String employeeNo;

    /**
     * 关联系统用户ID
     */
    private Long userId;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 性别：0=未知，1=男，2=女
     */
    private Integer gender;

    /**
     * 身份证号（加密存储）
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
     * 照片URL
     */
    private String avatarUrl;

    /**
     * 所属部门
     */
    private String department;

    /**
     * 岗位
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String position;

    /**
     * 入职日期
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private LocalDate hireDate;

    /**
     * 离职日期
     */
    private LocalDate leaveDate;

    /**
     * 状态：active=在职，left=离职
     */
    private String status;

    /**
     * 健康证状态（冗余）
     */
    private String healthCertStatus;

    /**
     * 人脸是否已录入：0=未录入，1=已录入
     */
    private Integer faceEnrolled;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0=未删除，1=已删除
     */
    @TableLogic
    private Integer deleted;
}