package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LocationVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String regionCode;
    private String shelfCode;
    private String slotCode;
    private Long warehouseId;
    private String warehouseName;
    private BigDecimal capacity;
    private String capacityUnit;
    private BigDecimal usedCapacity;
    private BigDecimal temperatureMin;
    private BigDecimal temperatureMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;
    private Long sensorDeviceId;
    private String materialTypes;
    private String status;
    private String remark;
    private Long version;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
