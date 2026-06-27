package com.xykj.sample.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 留样登记DTO
 */
@Data
public class SampleRecordRegisterDTO {

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** 留样照片URL列表 */
    private List<String> sampleImages;

    /** 存储位置 */
    private String storageLocation;

    /** 存储温度（℃） */
    private BigDecimal storageTemp;

    /** 留样人ID */
    private Long sampledBy;
}
