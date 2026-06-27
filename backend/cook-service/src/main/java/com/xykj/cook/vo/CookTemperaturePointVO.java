package com.xykj.cook.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 温度曲线点VO
 */
@Data
public class CookTemperaturePointVO {

    private Long id;

    private LocalDateTime recordTime;
    private Integer temperature;
    private Boolean abnormal;
    private String remark;
}
