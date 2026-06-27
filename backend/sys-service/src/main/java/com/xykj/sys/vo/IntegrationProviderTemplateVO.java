package com.xykj.sys.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class IntegrationProviderTemplateVO {

    private Long id;
    private Integer builtinFlag;
    private String providerCode;
    private String providerName;
    private String providerType;
    private String authType;
    private String protocolType;
    private Integer callbackSupported;
    private Integer filePullSupported;
    private List<String> sceneCodeList;
    private String requestTemplate;
    private String responseTemplate;
    private String status;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
