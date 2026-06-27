package com.xykj.device.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 证据包创建请求
 */
@Data
public class EvidencePackageCreateDTO {

    /** 选中的录像ID列表 */
    private List<Long> recordingIds;

    /** 选中的片段ID列表（可选） */
    private List<Long> clipIds;

    /** 选中的截图ID列表（可选） */
    private List<Long> screenshotIds;

    /** 证据包名称（可选，默认自动生成） */
    @Size(max = 255)
    private String packageName;

    /** 所属组织ID（可选） */
    private Long orgId;
}
