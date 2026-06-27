package com.xykj.device.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 云监控平台指标数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CloudMetric {

    /** 数据采集时间（毫秒时间戳） */
    private long timestamp;

    /** 指标名称（如 C00, C01） */
    private String name;

    /** 指标值 */
    private double value;
}
