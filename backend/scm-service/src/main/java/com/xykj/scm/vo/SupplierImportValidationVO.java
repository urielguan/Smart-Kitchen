package com.xykj.scm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商导入预校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierImportValidationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 有效数据行数
     */
    private Integer effectiveRowCount;

    /**
     * 是否超出单次导入上限
     */
    private Boolean rowLimitExceeded;

    /**
     * 编码/名称重复冲突明细
     */
    private List<SupplierImportValidationConflictVO> duplicateConflicts;
}
