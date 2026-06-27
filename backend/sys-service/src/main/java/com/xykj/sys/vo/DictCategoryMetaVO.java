package com.xykj.sys.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 字典分类页签元数据
 */
@Data
public class DictCategoryMetaVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String categoryType;

    private String categoryName;

    private Long itemCount;

    private Long systemCount;

    private Long customCount;
}
