package com.xykj.recipe.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 温度曲线点VO
 */
@Data
public class CookTemperaturePointVO {

    private LocalDateTime recordTime;
    private Integer temperature;
    private Boolean abnormal;
    private String remark;
}
