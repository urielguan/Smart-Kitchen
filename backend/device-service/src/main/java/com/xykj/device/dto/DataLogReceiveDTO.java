package com.xykj.device.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 设备数据接收DTO
 */
@Data
public class DataLogReceiveDTO {

    /** 设备ID */
    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    /** 数据类型：temperature/humidity/weight/heartbeat */
    @NotBlank(message = "数据类型不能为空")
    private String dataType;

    /** 数据值 */
    private BigDecimal dataValue;

    /** 数据单位 */
    private String dataUnit;

    /** 完整数据JSON */
    private String dataJson;

    /** 采集时间（不传则使用服务端时间） */
    private LocalDateTime collectedAt;
}
