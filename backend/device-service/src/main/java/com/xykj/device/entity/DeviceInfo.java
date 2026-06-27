package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 设备信息实体
 * 对应数据库表: device_info
 */
@Data
@TableName("device_info")
public class DeviceInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备编码 */
    private String deviceCode;

    /** 设备名称 */
    private String deviceName;

    /**
     * 设备类型：
     * camera=监控摄像头，sensor=温湿度传感器，scale=智能秤，
     * gas_detector=气体监测设备，sample_terminal=智能留样设备，
     * health_terminal=智能晨检设备
     */
    private String deviceType;

    /** 设备型号 */
    private String deviceModel;

    /** 生产厂商 */
    private String manufacturer;

    /** 设备序列号 */
    private String sn;

    /** MAC地址 */
    private String macAddress;

    /** IP地址 */
    private String ipAddress;

    /** 位置描述 */
    private String locationDesc;

    /** 3D坐标X */
    private BigDecimal positionX;

    /** 3D坐标Y */
    private BigDecimal positionY;

    /** 3D坐标Z */
    private BigDecimal positionZ;

    /** X轴旋转角度 */
    private BigDecimal rotationX;

    /** Y轴旋转角度 */
    private BigDecimal rotationY;

    /** Z轴旋转角度 */
    private BigDecimal rotationZ;

    /** 3D模型文件URL */
    @TableField("model_3d_url")
    private String model3dUrl;

    /** 3D模型缩放比例 */
    @TableField("model_3d_scale")
    private BigDecimal model3dScale;

    /** 安装日期 */
    private LocalDate installDate;

    /** 保修到期日 */
    private LocalDate warrantyExpiresAt;

    /** 维保周期（天） */
    private Integer maintenanceCycleDays;

    /** 负责人姓名 */
    private String managerName;

    /** 负责人电话 */
    private String managerPhone;

    /** 上次维保日期 */
    private LocalDate lastMaintenanceAt;

    /** 下次维保日期 */
    private LocalDate nextMaintenanceAt;

    /** 在线状态：online/offline/fault */
    private String onlineStatus;

    /** 最后心跳时间 */
    private LocalDateTime lastHeartbeatAt;

    /** 设备配置参数（JSON） */
    private String configParams;

    /** 状态：active/inactive/maintenance */
    private String status;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    /** 备注 */
    private String remark;

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedBy;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
