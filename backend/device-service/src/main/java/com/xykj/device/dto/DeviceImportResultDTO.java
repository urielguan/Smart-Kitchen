package com.xykj.device.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 设备导入结果DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeviceImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private Integer total;

    /** 成功数 */
    private Integer successCount;

    /** 失败数 */
    private Integer failCount;

    /** 是否有错误 */
    private Boolean hasErrors;

    /** 错误文件下载URL（Base64编码，前端直接下载） */
    private String errorFileBase64;

    /** 错误详情列表（用于前端直接展示） */
    private List<ImportErrorDetail> errorDetails;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportErrorDetail implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 行号 */
        private Integer rowNum;
        /** 设备编码 */
        private String deviceCode;
        /** 设备名称 */
        private String deviceName;
        /** 错误原因 */
        private String errorMessage;
    }
}
