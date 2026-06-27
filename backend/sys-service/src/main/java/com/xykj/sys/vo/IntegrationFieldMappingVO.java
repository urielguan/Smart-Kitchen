package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IntegrationFieldMappingVO {

    private Long id;
    private Long configId;
    private String configName;
    private String providerCode;
    private String sourceField;
    private String sourcePath;
    private String targetField;
    private String transformType;
    private String transformRule;
    private String defaultValue;
    private Integer requiredFlag;
    private Integer sortNo;
    private Integer enabled;
    private String errorStrategy;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
