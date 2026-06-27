package com.xykj.scm.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商列表查询参数
 */
@Data
public class SupplierQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 10;

    /**
     * 统一关键字（模糊匹配供应商名称/供应商编码/统一社会信用代码）
     */
    private String keyword;

    /**
     * 供应商名称（模糊搜索）
     */
    private String supplierName;

    /**
     * 供应商编码（模糊搜索）
     */
    private String supplierCode;

    /**
     * 状态：draft/pending/active/rejected/disabled/cancelled，兼容 approved -> active
     */
    private String status;

    /**
     * 所属组织ID（业务筛选）
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;
}
