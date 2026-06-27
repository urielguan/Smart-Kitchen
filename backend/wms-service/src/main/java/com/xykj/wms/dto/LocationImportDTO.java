package com.xykj.wms.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class LocationImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty("组织编码")
    private String orgCode;

    @ExcelProperty("所属仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓位编码")
    private String locationCode;

    @ExcelProperty("仓位名称")
    private String locationName;

    @ExcelProperty("仓位类型")
    private String locationType;

    @ExcelProperty("区域编码")
    private String regionCode;

    @ExcelProperty("货架编码")
    private String shelfCode;

    @ExcelProperty("货位编码")
    private String slotCode;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("仓位最大容量")
    private BigDecimal capacity;

    private Integer rowNumber;
    private String errorField;
    private String errorReason;
}
