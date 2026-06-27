package com.xykj.sys.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 字典项列表 VO
 */
@Data
public class DictCategoryItemVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String categoryType;

    private String categoryName;

    private String dictCode;

    private String dictName;

    private String dictValue;

    /**
     * system/custom
     */
    private String sourceType;

    private Integer sortOrder;

    private String status;

    private BigDecimal areaCoefficient;

    /**
     * system/manual/ai
     */
    private String areaCoefficientSource;

    private Long referenceCount;

    private Boolean system;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
