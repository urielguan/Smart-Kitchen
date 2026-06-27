package com.xykj.device.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("device_vision_analysis_task")
public class DeviceVisionAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long screenshotId;
    private Long recordingId;
    private Long deviceId;
    private String deviceName;
    private Long orgId;
    private String taskStatus;
    private String violationType;
    private Integer confidence;
    private String summary;
    private String modelVersion;
    private String rawResponse;
    private Long alertId;
    private String errorMessage;
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
