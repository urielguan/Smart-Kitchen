package com.xykj.sys.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;

/**
 * 员工导入DTO
 */
@Data
@HeadRowHeight(40)
@ColumnWidth(15)
public class EmployeeImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 员工编号（留空自动生成，填写且已存在则覆盖更新）
     */
    @ExcelProperty(index = 0)
    @ColumnWidth(22)
    private String employeeNo;

    /**
     * 姓名（必填）
     */
    @ExcelProperty(index = 1)
    @ColumnWidth(12)
    private String realName;

    /**
     * 性别：male/female
     */
    @ExcelProperty(index = 2)
    @ColumnWidth(15)
    private String gender;

    /**
     * 手机号（必填）
     */
    @ExcelProperty(index = 3)
    @ColumnWidth(15)
    private String phone;

    /**
     * 邮箱
     */
    @ExcelProperty(index = 4)
    @ColumnWidth(25)
    private String email;

    /**
     * 身份证号
     */
    @ExcelProperty(index = 5)
    @ColumnWidth(20)
    private String idCard;

    /**
     * 所属组织编码（必填，导入时解析为orgId）
     */
    @ExcelProperty(index = 6)
    @ColumnWidth(20)
    private String orgCode;

    /**
     * 职位（见说明行中的有效值）
     */
    @ExcelProperty(index = 7)
    @ColumnWidth(15)
    private String position;

    /**
     * 入职日期（YYYY-MM-DD）
     */
    @ExcelProperty(index = 8)
    @ColumnWidth(15)
    private String hireDate;

    /**
     * 员工状态：active=在职，left=离职
     */
    @ExcelProperty(index = 9)
    @ColumnWidth(15)
    private String status;

    /**
     * 账号状态：active=启用，inactive=禁用
     */
    @ExcelProperty(index = 10)
    @ColumnWidth(15)
    private String accountStatus;

    /**
     * 备注
     */
    @ExcelProperty(index = 11)
    @ColumnWidth(30)
    private String remark;

    /**
     * 导入失败原因（仅用于错误文件导出）
     */
    @ExcelProperty(index = 12)
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
