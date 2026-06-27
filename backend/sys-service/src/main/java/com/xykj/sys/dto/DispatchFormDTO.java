package com.xykj.sys.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 派单表单DTO
 */
@Data
public class DispatchFormDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 派单方式：auto/manual
     */
    @NotBlank(message = "派单方式不能为空")
    private String dispatchType;

    /**
     * 处理人ID（人工派单必填）
     */
    private Long handlerId;

    /**
     * 优先级：high/medium/low
     */
    private String priority;

    /**
     * 截止时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime deadline;

    /**
     * 派单备注
     */
    private String remark;
}
