package com.xykj.cook.dto;

import lombok.Data;

/**
 * 分派厨师DTO
 */
@Data
public class CookTaskAssignDTO {

    /**
     * 厨师ID（sys_employee.id）
     */
    private Long chefId;

    /**
     * 厨师姓名
     */
    private String chefName;
}
