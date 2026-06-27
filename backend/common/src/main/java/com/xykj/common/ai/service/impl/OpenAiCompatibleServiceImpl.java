package com.xykj.common.ai.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiTextGenerateResult;
import com.xykj.common.ai.model.AiVisionDetectionResult;
import com.xykj.common.ai.service.AiConfigCryptoService;
import com.xykj.common.ai.service.AiRequestLogService;
import com.xykj.common.ai.service.OpenAiCompatibleService;
import com.xykj.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiCompatibleServiceImpl implements OpenAiCompatibleService {

    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;
    private final AiConfigCryptoService cryptoService;
    private final AiRequestLogService logService;

    @Override
    public AiTextGenerateResult generateText(AiServiceConfig config, String systemPrompt, String userPrompt, String moduleCode, String requestType) {
        long start = System.currentTimeMillis();
        AiRequestLog requestLog = buildLog(config, moduleCode, requestType);
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", config.getModelName());
            payload.put("temperature", 0);
            payload.put("max_tokens", 800);
            payload.put("response_format", Map.of("type", "json_object"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            ));
            String content = doRequest(config, payload);
            requestLog.setStatus("success");
            requestLog.setResponseSummary(truncate(content));
            requestLog.setDurationMs((int) (System.currentTimeMillis() - start));
            logService.save(requestLog);
            return new AiTextGenerateResult(true, sanitizeText(content), null);
        } catch (Exception ex) {
            log.warn("AI文本调用失败: {}", ex.getMessage());
            requestLog.setStatus("failed");
            requestLog.setErrorMessage(truncate(ex.getMessage()));
            requestLog.setDurationMs((int) (System.currentTimeMillis() - start));
            logService.save(requestLog);
            return new AiTextGenerateResult(false, null, ex.getMessage());
        }
    }

    @Override
    public AiVisionDetectionResult analyzeImage(AiServiceConfig config, byte[] imageBytes, String mimeType, String prompt, String moduleCode, String requestType) {
        long start = System.currentTimeMillis();
        AiRequestLog requestLog = buildLog(config, moduleCode, requestType);
        AiVisionDetectionResult result = new AiVisionDetectionResult();
        try {
            String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", config.getModelName());
            payload.put("temperature", 0.1);
            payload.put("max_tokens", 1000);
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", "你是后厨违规识别助手，只输出JSON。"),
                    Map.of("role", "user", "content", List.of(
                            Map.of("type", "text", "text", prompt),
                            Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
                    ))
            ));
            String content = doRequest(config, payload);
            parseVisionResult(content, result);
            result.setSuccess(true);
            result.setRawResponse(content);
            requestLog.setStatus("success");
            requestLog.setResponseSummary(truncate(content));
            requestLog.setDurationMs((int) (System.currentTimeMillis() - start));
            logService.save(requestLog);
            return result;
        } catch (Exception ex) {
            log.warn("AI视觉调用失败: {}", ex.getMessage());
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            requestLog.setStatus("failed");
            requestLog.setErrorMessage(truncate(ex.getMessage()));
            requestLog.setDurationMs((int) (System.currentTimeMillis() - start));
            logService.save(requestLog);
            return result;
        }
    }

    private String doRequest(AiServiceConfig config, Map<String, Object> payload) throws Exception {
        String url = StrUtil.removeSuffix(config.getBaseUrl(), "/") + "/chat/completions";
        String requestBody = objectMapper.writeValueAsString(payload);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(cryptoService.decrypt(config.getApiKeyEncrypted()));
        headers.setContentLength(requestBody.getBytes(StandardCharsets.UTF_8).length);
        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        String body = aiRestTemplate.postForObject(url, entity, String.class);
        if (CharSequenceUtil.isBlank(body)) {
            throw new IllegalStateException("AI返回为空");
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode item : contentNode) {
                JsonNode textNode = item.get("text");
                if (textNode != null) {
                    builder.append(textNode.asText());
                }
            }
            return builder.toString();
        }
        String content = contentNode.asText();
        if (CharSequenceUtil.isBlank(content)) {
            throw new IllegalStateException("AI未返回有效内容");
        }
        return content;
    }

    private void parseVisionResult(String content, AiVisionDetectionResult result) throws Exception {
        String jsonText = extractJson(content);
        JsonNode root = objectMapper.readTree(jsonText);
        result.setSummary(root.path("summary").asText(""));
        result.setModelVersion(root.path("modelVersion").asText(""));
        JsonNode violations = root.path("violations");
        if (violations.isArray()) {
            for (JsonNode node : violations) {
                AiVisionDetectionResult.ViolationItem item = new AiVisionDetectionResult.ViolationItem();
                item.setViolationType(node.path("violationType").asText());
                item.setViolationTypeName(node.path("violationTypeName").asText());
                item.setConfidence(node.path("confidence").asDouble());
                item.setPersonCount(node.path("personCount").isMissingNode() ? 1 : node.path("personCount").asInt(1));
                item.setExplanation(node.path("explanation").asText(""));
                result.getViolations().add(item);
            }
        }
    }

    private String extractJson(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private AiRequestLog buildLog(AiServiceConfig config, String moduleCode, String requestType) {
        AiRequestLog log = new AiRequestLog();
        log.setServiceConfigId(config.getId());
        log.setRequestType(requestType);
        log.setServiceType(config.getServiceType());
        log.setModuleCode(moduleCode);
        log.setModelName(config.getModelName());
        log.setTargetUrl(StrUtil.removeSuffix(config.getBaseUrl(), "/") + "/chat/completions");
        log.setOrgId(UserContext.getOrgId());
        log.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        return log;
    }

    private String sanitizeText(String text) {
        return text.replace("```", "").trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }
}
