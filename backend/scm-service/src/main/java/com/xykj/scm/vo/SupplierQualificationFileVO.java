package com.xykj.scm.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 供应商资质文件
 */
@Data
public class SupplierQualificationFileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String size;
    private String url;
}
