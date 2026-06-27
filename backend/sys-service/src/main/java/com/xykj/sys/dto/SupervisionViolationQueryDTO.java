package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 违规记录查询DTO
 */
@Data
public class SupervisionViolationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 20;

    private Long orgId;

    private List<Long> orgIds;

    private String status;

    private String violationType;

    /**
     * yyyy-MM-dd HH:mm:ss
     */
    private String startTime;

    /**
     * yyyy-MM-dd HH:mm:ss
     */
    private String endTime;
}
