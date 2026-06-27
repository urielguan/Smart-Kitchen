package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 违规记录VO
 */
@Data
public class SupervisionViolationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String violationNo;
    private String title;
    private String description;
    private String violationType;
    private String violationTypeName;
    private String location;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggeredAt;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    private String handlerName;
}
