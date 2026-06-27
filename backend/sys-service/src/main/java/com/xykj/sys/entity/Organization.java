package com.xykj.sys.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 组织实体类
 * 对应数据库表: sys_organization
 */
@Data
@TableName("sys_organization")
public class Organization implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 组织编码（唯一）
     */
    private String orgCode;

    /**
     * 组织名称
     */
    private String orgName;

    /**
     * 组织类型：group=集团，company=分公司，canteen=食堂，dept=部门
     */
    private String orgType;

    /**
     * 父组织ID（0=顶级）
     */
    private Long parentId;

    /**
     * 组织层级（1=顶级）
     */
    private Integer level;

    /**
     * 组织路径（如：/智慧食安集团总部/华东区分公司/）
     */
    private String path;

    /**
     * 负责人姓名
     */
    private String leaderName;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 地址
     */
    private String address;

    /**
     * 状态：active=启用，inactive=停用
     */
    private String status;

    /**
     * 排序序号
     */
    private Integer sortOrder;

    /**
     * 租户ID
     */
    private Long tenantId;

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
