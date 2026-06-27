package com.xykj.sample.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量作废DTO
 */
@Data
public class BatchVoidDTO {
    private List<Long> ids;
    private String reason;
}
