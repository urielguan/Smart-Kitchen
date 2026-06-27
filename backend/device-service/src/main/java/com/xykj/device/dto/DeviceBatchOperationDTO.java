package com.xykj.device.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 设备批量操作请求
 */
@Data
public class DeviceBatchOperationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotEmpty(message = "设备ID列表不能为空")
    private List<Long> ids;
}
