package com.xykj.health.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 晨检联动版本信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HealthCheckLinkageVersionVO {

    /**
     * 版本值，用于前端判断是否需要刷新
     */
    private String version;

    /**
     * 最近一次联动更新时间
     */
    private LocalDateTime updatedAt;
}
