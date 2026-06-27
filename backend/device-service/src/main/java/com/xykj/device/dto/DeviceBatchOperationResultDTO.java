package com.xykj.device.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备批量操作结果
 */
@Data
public class DeviceBatchOperationResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer totalCount = 0;
    private Integer successCount = 0;
    private Integer failCount = 0;
    private Integer skippedCount = 0;
    private List<Long> successIds = new ArrayList<>();
    private List<DeviceBatchItemResultDTO> failedItems = new ArrayList<>();
    private List<DeviceBatchItemResultDTO> skippedItems = new ArrayList<>();
}
