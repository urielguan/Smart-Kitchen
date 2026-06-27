package com.xykj.cook.dto;

import lombok.Data;

/**
 * 温度记录上报DTO
 */
@Data
public class TemperatureRecordDTO {

    /**
     * 任务ID
     */
    private Long taskId;

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
}
