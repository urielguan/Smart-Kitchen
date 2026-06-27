package com.xykj.scm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 供应商导入预校验冲突明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierImportValidationConflictVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Excel 行号
     */
    private Integer rowNum;

    /**
     * 冲突字段
     */
    private String field;

    /**
     * 冲突值
     */
    private String conflictValue;

    /**
     * 冲突提示
     */
    private String message;
}
