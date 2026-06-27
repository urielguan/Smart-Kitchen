package com.xykj.sample.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 留样记录编辑DTO
 */
@Data
public class SampleRecordUpdateDTO {

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** 存储位置 */
    private String storageLocation;

    /** 存储温度（℃） */
    private BigDecimal storageTemp;

    /** 留样照片URL列表 */
    private List<String> sampleImages;
}
