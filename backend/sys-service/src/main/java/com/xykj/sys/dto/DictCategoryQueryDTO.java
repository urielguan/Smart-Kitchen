package com.xykj.sys.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 字典分类查询参数
 */
@Data
public class DictCategoryQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer pageNum = 1;

    private Integer pageSize = 10;

    /**
     * 固定分类编码
     */
    private String categoryType;

    /**
     * 关键字（编码/名称）
     */
    private String keyword;

    /**
     * 来源类型：system/custom
     */
    private String sourceType;

    /**
     * 状态：active/inactive
     */
    private String status;
}
