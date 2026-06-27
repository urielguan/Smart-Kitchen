package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AiServiceConfigVO {

    private Long id;
    private String serviceName;
    private String serviceType;
    private String baseUrl;
    private String apiKeyMasked;
    private String modelName;
    private List<String> applicableModules;
    private String status;
    private String lastTestStatus;
    private String lastTestMessage;
    private LocalDateTime lastTestAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
