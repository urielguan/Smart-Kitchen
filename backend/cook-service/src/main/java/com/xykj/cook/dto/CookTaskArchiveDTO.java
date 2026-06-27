package com.xykj.cook.dto;

import lombok.Data;

/**
 * 归档/交接烹饪任务DTO
 */
@Data
public class CookTaskArchiveDTO {

    /**
     * 交接状态
     */
    private String handoffStatus;

    /**
     * 交接备注
     */
    private String handoffRemark;
}
