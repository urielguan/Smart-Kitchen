package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 字典选项 VO
 */
@Data
public class DictCategoryOptionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String categoryType;

    private String dictCode;

    private String dictName;

    private String value;

    private String status;

    private Boolean system;

    private Integer sortOrder;

    private BigDecimal areaCoefficient;
}
