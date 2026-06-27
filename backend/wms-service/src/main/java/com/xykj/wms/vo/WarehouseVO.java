package com.xykj.wms.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class WarehouseVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String warehouseTypeName;
    private BigDecimal capacity;
    private String capacityUnit;
    private String address;
    private String managerName;
    private String managerPhone;
    private String status;
    private String remark;
    private Long version;

    // 仓库级温湿度预警阈值
    private BigDecimal temperatureMin;
    private BigDecimal temperatureMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;

    // 仓库级实时温湿度（列表页展示）
    private BigDecimal currentTemperature;
    private BigDecimal currentHumidity;

    // 聚合字段（LEFT JOIN wms_location）
    private Integer positionTotal;
    private Integer positionUsed;
    private Integer positionIdle;

    // 温湿度状态（由 Service 层通过 JdbcTemplate 填充）
    /** 仓库整体温度状态: normal/warning/alarm */
    private String tempStatus;
    /** 仓库整体湿度状态: normal/warning/alarm */
    private String humidityStatus;
    /** 各仓位传感器实时数据 */
    private List<LocationSensorData> locationSensorData;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 仓位传感器实时数据
     */
    @Data
    public static class LocationSensorData implements Serializable {
        private Long locationId;
        private String locationName;
        private BigDecimal currentTemperature;
        private BigDecimal currentHumidity;
        /** normal/warning/alarm */
        private String tempStatus;
        /** normal/warning/alarm */
        private String humidityStatus;
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dataCollectedAt;
    }
}
