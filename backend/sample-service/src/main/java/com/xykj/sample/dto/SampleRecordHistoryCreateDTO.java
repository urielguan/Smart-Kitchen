package com.xykj.sample.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 历史留样补录DTO
 */
@Data
public class SampleRecordHistoryCreateDTO {

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 来源类型：manual_history/offline_delayed */
    private String recordOriginType;

    /** 补录原因 */
    private String supplementReason;

    /** 补录备注 */
    private String supplementRemark;

    /** 留样重量（克） */
    private BigDecimal sampleWeight;

    /** 留样照片URL列表 */
    private List<String> sampleImages;

    /** 存放位置 */
    private String storageLocation;

    /** 存放温度（℃） */
    private BigDecimal storageTemp;

    /** 留样人ID */
    private Long sampledBy;
}
