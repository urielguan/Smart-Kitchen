package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AiServiceConfigCreateDTO {

    @NotBlank
    private String serviceName;
    @NotBlank
    private String serviceType;
    @NotBlank
    private String baseUrl;
    @NotBlank
    private String apiKey;
    @NotBlank
    private String modelName;
    @NotEmpty
    private List<String> applicableModules;
    private String remark;
}
