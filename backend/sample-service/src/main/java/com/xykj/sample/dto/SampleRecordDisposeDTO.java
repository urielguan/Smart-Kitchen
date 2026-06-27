package com.xykj.sample.dto;

import lombok.Data;

import java.util.List;

/**
 * 销样操作DTO
 */
@Data
public class SampleRecordDisposeDTO {

    /** 销样照片URL列表（必填） */
    private List<String> disposalImages;

    /** 销样备注 */
    private String disposalRemark;
}
