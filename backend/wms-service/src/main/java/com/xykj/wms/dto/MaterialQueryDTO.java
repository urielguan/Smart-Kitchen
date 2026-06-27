package com.xykj.wms.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 物料查询参数
 */
@Data
public class MaterialQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码（从1开始）
     */
    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码最小为1")
    private Integer pageNum = 1;

    /**
     * 每页条数
     */
    @NotNull(message = "每页条数不能为空")
    @Min(value = 1, message = "每页条数最小为1")
    private Integer pageSize = 20;

    /**
     * 物料名称（模糊搜索）
     */
    private String materialName;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料类别名称
     */
    private String categoryName;

    /**
     * 库存状态: normal/low/high/expired
     */
    private String stockStatus;

    /**
     * 物料状态: active/inactive
     */
    private String status;

    /**
     * 组织ID（业务筛选）
     */
    private Long orgId;

    /**
     * 数据权限注入组织ID列表
     */
    private List<Long> orgIds;
}