package com.xykj.sample.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 新增留样记录DTO
 */
@Data
public class SampleRecordCreateDTO {

    /** 关联烹饪任务ID */
    private Long taskId;

    /** 菜谱ID */
    private Long menuId;

    /** 菜谱名称 */
    private String menuName;

    /** 留样日期 */
    private LocalDate sampleDate;

    /** 餐次 */
    private String mealType;

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

    /** 所属组织ID */
    private Long orgId;

    /** 租户ID */
    private Long tenantId;
}
