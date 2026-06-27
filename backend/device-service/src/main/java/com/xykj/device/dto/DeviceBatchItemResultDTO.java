package com.xykj.device.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 设备批量操作单项结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceBatchItemResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String deviceCode;
    private String deviceName;
    private Boolean success;
    private String reason;
    private String errorCode;
}
