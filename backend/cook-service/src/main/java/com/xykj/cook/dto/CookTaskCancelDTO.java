package com.xykj.cook.dto;

import lombok.Data;

/**
 * 取消烹饪任务DTO
 */
@Data
public class CookTaskCancelDTO {

    /**
     * 取消原因
     */
    private String reason;
}
