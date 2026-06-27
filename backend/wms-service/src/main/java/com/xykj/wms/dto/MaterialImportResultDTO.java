package com.xykj.wms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 物料导入结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MaterialImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private Integer total;

    /** 成功条数 */
    private Integer successCount;

    /** 失败条数 */
    private Integer failCount;

    /** 是否有错误 */
    private Boolean hasErrors;

    /** 错误文件下载地址 */
    private String errorFileUrl;
}
