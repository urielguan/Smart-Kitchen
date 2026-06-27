package com.xykj.device.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据采集日志VO
 */
@Data
public class DataLogVO {

    private Long id;

    private Long deviceId;

    private String deviceCode;

    /** 数据类型编码 */
    private String dataType;

    /** 数据类型名称 */
    private String dataTypeName;

    private BigDecimal dataValue;

    private String dataUnit;

    private LocalDateTime collectedAt;

    private LocalDateTime createdAt;
}
