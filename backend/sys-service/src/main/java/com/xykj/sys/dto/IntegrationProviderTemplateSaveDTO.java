package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IntegrationProviderTemplateSaveDTO {

    @NotBlank(message = "【平台编码】为必填项，请完成填写后再保存")
    private String providerCode;

    @NotBlank(message = "【平台名称】为必填项，请完成填写后再保存")
    private String providerName;

    @NotBlank(message = "【平台类型】为必填项，请完成填写后再保存")
    private String providerType;

    @NotBlank(message = "【鉴权类型】为必填项，请完成填写后再保存")
    private String authType;

    @NotBlank(message = "【协议类型】为必填项，请完成填写后再保存")
    private String protocolType;

    private Integer callbackSupported = 0;
    private Integer filePullSupported = 0;

    @NotEmpty(message = "【支持场景】为必填项，请完成填写后再保存")
    private List<String> sceneCodes;

    private String requestTemplate;
    private String responseTemplate;
    private String status = "active";
    private String remark;
}
