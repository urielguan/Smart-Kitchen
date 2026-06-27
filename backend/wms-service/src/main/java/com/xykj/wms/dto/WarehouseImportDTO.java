package com.xykj.wms.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class WarehouseImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("组织编码")
    private String orgCode;

    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("仓库类型")
    private String warehouseType;

    @ExcelProperty("仓库位置")
    private String address;

    @ExcelProperty("负责人")
    private String managerName;

    @ExcelProperty("联系方式")
    private String managerPhone;

    @ExcelProperty("最大容量")
    private BigDecimal capacity;

    @ExcelProperty("状态")
    private String status;

    private Integer rowNumber;
    private String errorField;
    private String errorReason;
}
