package com.xykj.sys.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;

/**
 * 组织导入导出DTO
 */
@Data
@HeadRowHeight(40)
@ColumnWidth(15)
public class OrganizationImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 组织编码（唯一）
     */
    @ExcelProperty(index = 0)
    @ColumnWidth(20)
    private String orgCode;

    /**
     * 组织名称
     */
    @ExcelProperty(index = 1)
    @ColumnWidth(25)
    private String orgName;

    /**
     * 组织类型：group/company/canteen/dept
     */
    @ExcelProperty(index = 2)
    @ColumnWidth(15)
    private String orgType;

    /**
     * 父组织编码（用于导入时关联父组织）
     */
    @ExcelProperty(index = 3)
    @ColumnWidth(20)
    private String parentOrgCode;

    /**
     * 负责人姓名
     */
    @ExcelProperty(index = 4)
    @ColumnWidth(12)
    private String leaderName;

    /**
     * 联系电话
     */
    @ExcelProperty(index = 5)
    @ColumnWidth(15)
    private String contactPhone;

    /**
     * 地址
     */
    @ExcelProperty(index = 6)
    @ColumnWidth(30)
    private String address;

    /**
     * 排序序号
     */
    @ExcelProperty(index = 7)
    @ColumnWidth(10)
    private String sortOrder;

    /**
     * 状态：active/inactive
     */
    @ExcelProperty(index = 8)
    @ColumnWidth(15)
    private String status;

    /**
     * 导入失败原因（仅用于错误文件导出）
     */
    @ExcelProperty(index = 9)
    @ColumnWidth(40)
    private String errorMessage;

    /**
     * 行号（用于错误提示）
     */
    private Integer rowNum;

    /**
     * 是否导入成功
     */
    private Boolean success;
}
