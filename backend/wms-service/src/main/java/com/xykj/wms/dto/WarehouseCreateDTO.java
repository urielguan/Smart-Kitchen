package com.xykj.wms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WarehouseCreateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "仓库编码不能为空")
    @Size(max = 50)
    private String warehouseCode;

    @NotBlank(message = "仓库名称不能为空")
    @Size(max = 100)
    private String warehouseName;

    @NotBlank(message = "仓库类型不能为空")
    private String warehouseType;

    private BigDecimal capacity;
    private String capacityUnit;

    @Size(max = 200)
    private String address;

    @Size(max = 50)
    private String managerName;

    @Size(max = 20)
    private String managerPhone;

    private String status;

    @Size(max = 300)
    private String remark;

    // 仓库级温湿度预警阈值（可选）
    private BigDecimal temperatureMin;
    private BigDecimal temperatureMax;
    private BigDecimal humidityMin;
    private BigDecimal humidityMax;
}
