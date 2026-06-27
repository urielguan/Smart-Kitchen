package com.xykj.sample.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量归档DTO
 */
@Data
public class BatchArchiveDTO {
    private List<Long> ids;
}
