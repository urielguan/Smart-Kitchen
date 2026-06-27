package com.xykj.common.ai.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiTextGenerateResult {

    private boolean success;
    private String content;
    private String errorMessage;
}
