package com.xykj.sys.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiServiceConfigStatusDTO {

    @NotBlank
    private String status;
}
