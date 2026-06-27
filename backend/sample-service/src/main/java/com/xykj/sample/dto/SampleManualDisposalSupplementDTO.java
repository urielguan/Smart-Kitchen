package com.xykj.sample.dto;

import lombok.Data;

import java.util.List;

/**
 * 销样手工补录DTO
 */
@Data
public class SampleManualDisposalSupplementDTO {

    /** 补录场景 */
    private String supplementScene;

    /** 补录备注 */
    private String supplementRemark;

    /** 销样照片URL列表（必填） */
    private List<String> disposalImages;

    /** 销样备注 */
    private String disposalRemark;
}
