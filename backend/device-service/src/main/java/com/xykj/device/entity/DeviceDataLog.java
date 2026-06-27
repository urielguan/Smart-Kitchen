package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据采集日志实体
 * 对应数据库表: device_data_log
 */
@Data
@TableName("device_data_log")
public class DeviceDataLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备ID */
    private Long deviceId;

    /** 设备编码（冗余） */
    private String deviceCode;

    /** 数据类型：temperature=温度，humidity=湿度，weight=重量，heartbeat=心跳 */
    private String dataType;

    /** 数据值 */
    private BigDecimal dataValue;

    /** 数据单位（℃/%/kg等） */
    private String dataUnit;

    /** 完整数据（JSON格式，存储复杂数据） */
    private String dataJson;

    /** 采集时间 */
    private LocalDateTime collectedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
