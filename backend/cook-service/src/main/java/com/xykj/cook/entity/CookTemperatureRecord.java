package com.xykj.cook.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 烹饪温度记录实体
 * 对应数据库表: cook_temperature_record
 */
@Data
@TableName("cook_temperature_record")
public class CookTemperatureRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联烹饪任务ID
     */
    private Long taskId;

    /**
     * 记录时间
     */
    private LocalDateTime recordTime;

    /**
     * 温度值（摄氏度）
     */
    private Integer temperature;

    /**
     * 是否异常
     */
    private Boolean abnormal;

    /**
     * 备注
     */
    private String remark;

    /**
     * 所属组织ID
     */
    private Long orgId;

    /**
     * 租户ID
     */
    private Long tenantId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
