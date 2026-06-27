package com.xykj.cook.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量归档请求
 */
@Data
public class BatchArchiveRequest {
    private List<Long> ids;
    private CookTaskArchiveDTO dto;
}
