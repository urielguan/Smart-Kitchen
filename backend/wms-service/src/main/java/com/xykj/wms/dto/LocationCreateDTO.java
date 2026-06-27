package com.xykj.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LocationCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotNull(message = "所属仓库不能为空")
    private Long warehouseId;

    @NotBlank(message = "仓位编码不能为空")
    @Size(max = 50)
    private String locationCode;

    @NotBlank(message = "仓位名称不能为空")
    @Size(max = 50)
    private String locationName;

    @Size(max = 50)
    private String locationType;

    @Size(max = 50)
    private String regionCode;

    @Size(max = 50)
    private String shelfCode;

    @Size(max = 50)
    private String slotCode;

    private BigDecimal capacity;
    private String capacityUnit;
    private BigDecimal temperatureMin;
    private BigDecimal temperatureMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;

    /** 绑定的传感器设备ID */
    private Long sensorDeviceId;

    @Size(max = 200)
    private String materialTypes;

    private String status;

    @Size(max = 200)
    private String remark;
}
