package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 违规记录详情VO
 */
@Data
public class SupervisionViolationDetailVO implements Serializable {

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
    private List<String> violationImages;
    private Map<String, Object> relatedData;
    private Long handledBy;
    private String handledByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime handledAt;

    private String handleResult;
    private List<String> handleImages;
}
