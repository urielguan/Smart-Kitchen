package com.xykj.wms.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料导入DTO
 */
@Data
@HeadRowHeight(40)
@ColumnWidth(15)
public class MaterialImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ExcelProperty(index = 0)
    @ColumnWidth(20)
    private String materialCode;

    @ExcelProperty(index = 1)
    @ColumnWidth(20)
    private String materialName;

    @ExcelProperty(index = 2)
    @ColumnWidth(15)
    private String materialSpec;

    @ExcelProperty(index = 3)
    @ColumnWidth(10)
    private String unit;

    @ExcelProperty(index = 4)
    @ColumnWidth(12)
    private String categoryName;

    @ExcelProperty(index = 5)
    @ColumnWidth(14)
    private String shelfLifeDays;

    @ExcelProperty(index = 6)
    @ColumnWidth(14)
    private String nearExpiryDays;

    @ExcelProperty(index = 7)
    @ColumnWidth(12)
    private String warningDays;

    @ExcelProperty(index = 8)
    @ColumnWidth(12)
    private String minStock;

    @ExcelProperty(index = 9)
    @ColumnWidth(12)
    private String maxStock;

    @ExcelProperty(index = 10)
    @ColumnWidth(20)
    private String storageConditions;

    @ExcelProperty(index = 11)
    @ColumnWidth(25)
    private String remark;

    @ExcelProperty(index = 12)
    @ColumnWidth(15)
    private String status;

    /** 失败原因（仅错误文件使用） */
    @ExcelProperty(index = 13)
    @ColumnWidth(40)
    private String errorMessage;

    /** 行号（运行时） */
    private Integer rowNum;

    /** 是否成功（运行时） */
    private Boolean success;
}
