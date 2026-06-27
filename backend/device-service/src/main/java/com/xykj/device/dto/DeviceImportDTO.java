package com.xykj.device.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.annotation.write.style.HeadRowHeight;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 设备导入DTO
 */
@Data
@HeadRowHeight(40)
@ColumnWidth(15)
public class DeviceImportDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 设备编码（唯一） */
    @ExcelProperty(index = 0)
    @ColumnWidth(20)
    private String deviceCode;

    /** 设备名称 */
    @ExcelProperty(index = 1)
    @ColumnWidth(25)
    private String deviceName;

    /** 设备类型 */
    @ExcelProperty(index = 2)
    @ColumnWidth(15)
    private String deviceType;

    /** 设备型号 */
    @ExcelProperty(index = 3)
    @ColumnWidth(15)
    private String deviceModel;

    /** 厂商 */
    @ExcelProperty(index = 4)
    @ColumnWidth(15)
    private String manufacturer;

    /** 序列号 */
    @ExcelProperty(index = 5)
    @ColumnWidth(20)
    private String sn;

    /** MAC地址 */
    @ExcelProperty(index = 6)
    @ColumnWidth(18)
    private String macAddress;

    /** IP地址 */
    @ExcelProperty(index = 7)
    @ColumnWidth(18)
    private String ipAddress;

    /** 安装位置 */
    @ExcelProperty(index = 8)
    @ColumnWidth(20)
    private String locationDesc;

    /** 负责人姓名 */
    @ExcelProperty(index = 9)
    @ColumnWidth(12)
    private String managerName;

    /** 负责人电话 */
    @ExcelProperty(index = 10)
    @ColumnWidth(15)
    private String managerPhone;

    /** 维保周期(天) */
    @ExcelProperty(index = 11)
    @ColumnWidth(12)
    private Integer maintenanceCycleDays;

    /** 安装日期 */
    @ExcelProperty(index = 12)
    @ColumnWidth(15)
    private String installDate;

    /** 保修到期 */
    @ExcelProperty(index = 13)
    @ColumnWidth(15)
    private String warrantyExpiresAt;

    /** 下次维保 */
    @ExcelProperty(index = 14)
    @ColumnWidth(15)
    private String nextMaintenanceAt;

    /** 备注 */
    @ExcelProperty(index = 15)
    @ColumnWidth(25)
    private String remark;

    /** 导入失败原因（仅用于错误文件导出） */
    @ExcelProperty(index = 16)
    @ColumnWidth(40)
    private String errorMessage;

    /** 行号（用于错误提示） */
    private Integer rowNum;

    /** 是否导入成功 */
    private Boolean success;
}
