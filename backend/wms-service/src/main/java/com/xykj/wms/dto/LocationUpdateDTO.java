package com.xykj.wms.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LocationUpdateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Size(max = 50)
    private String locationCode;

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

    @Size(max = 200)
    private String materialTypes;

    private Long sensorDeviceId;

    private String status;

    @Size(max = 200)
    private String remark;

    private Long version;
}
