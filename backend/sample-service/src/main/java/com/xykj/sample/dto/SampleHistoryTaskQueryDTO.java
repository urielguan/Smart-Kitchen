package com.xykj.sample.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 历史补录可选烹饪任务查询DTO
 */
@Data
public class SampleHistoryTaskQueryDTO {

    /** 业务日期 */
    private LocalDate businessDate;

    /** 任务编号/菜品关键字 */
    private String keyword;
}
