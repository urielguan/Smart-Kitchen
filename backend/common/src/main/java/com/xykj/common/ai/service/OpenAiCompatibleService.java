package com.xykj.common.ai.service;

import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiTextGenerateResult;
import com.xykj.common.ai.model.AiVisionDetectionResult;

public interface OpenAiCompatibleService {

    AiTextGenerateResult generateText(AiServiceConfig config, String systemPrompt, String userPrompt, String moduleCode, String requestType);

    AiVisionDetectionResult analyzeImage(AiServiceConfig config, byte[] imageBytes, String mimeType, String prompt, String moduleCode, String requestType);
}
