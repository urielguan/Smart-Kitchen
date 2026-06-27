package com.xykj.sys.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.AiModuleCode;
import com.xykj.common.ai.AiServiceType;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiTextGenerateResult;
import com.xykj.common.ai.model.AiVisionDetectionResult;
import com.xykj.common.ai.service.AiConfigCryptoService;
import com.xykj.common.ai.service.AiRequestLogService;
import com.xykj.common.ai.service.AiServiceConfigService;
import com.xykj.common.ai.service.OpenAiCompatibleService;
import com.xykj.common.exception.BizException;
import com.xykj.sys.dto.AiServiceConfigCreateDTO;
import com.xykj.sys.dto.AiServiceConfigQueryDTO;
import com.xykj.sys.dto.AiServiceConfigStatusDTO;
import com.xykj.sys.dto.AiServiceConfigUpdateDTO;
import com.xykj.sys.service.AiServiceAdminService;
import com.xykj.sys.vo.AiServiceConfigVO;
import com.xykj.sys.vo.AiServiceTestVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AiServiceAdminServiceImpl implements AiServiceAdminService {

    private static final byte[] TEST_PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9lEi8AAAAASUVORK5CYII=");

    private final AiServiceConfigService configService;
    private final AiConfigCryptoService cryptoService;
    private final OpenAiCompatibleService openAiCompatibleService;
    private final AiRequestLogService requestLogService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<AiServiceConfigVO> page(AiServiceConfigQueryDTO query) {
        Page<AiServiceConfig> page = configService.page(query.getKeyword(), query.getServiceType(), query.getStatus(), query.getPageNum(), query.getPageSize());
        Page<AiServiceConfigVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toVo).toList());
        return result;
    }

    @Override
    public AiServiceConfigVO detail(Long id) {
        return toVo(configService.getByIdOrThrow(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiServiceConfigCreateDTO dto) {
        AiServiceConfig config = new AiServiceConfig();
        BeanUtils.copyProperties(dto, config);
        config.setApplicableModules(writeModules(dto.getApplicableModules()));
        return configService.create(config, dto.getApiKey());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AiServiceConfigUpdateDTO dto) {
        AiServiceConfig config = new AiServiceConfig();
        BeanUtils.copyProperties(dto, config);
        config.setId(id);
        config.setApplicableModules(writeModules(dto.getApplicableModules()));
        configService.update(config, dto.getApiKey());
    }

    @Override
    public void delete(Long id) {
        configService.delete(id);
    }

    @Override
    public void changeStatus(Long id, AiServiceConfigStatusDTO dto) {
        AiServiceConfig config = configService.getByIdOrThrow(id);
        if ("active".equals(dto.getStatus()) && !"success".equals(config.getLastTestStatus())) {
            throw new BizException("启用前必须至少通过一次连接测试");
        }
        configService.changeStatus(id, dto.getStatus());
    }

    @Override
    public AiServiceTestVO test(Long id) {
        AiServiceConfig config = configService.getByIdOrThrow(id);
        boolean success;
        String message;
        if (AiServiceType.TEXT.equals(config.getServiceType())) {
            AiTextGenerateResult result = openAiCompatibleService.generateText(
                    config,
                    "你是连接测试助手，只返回一句中文。",
                    "请回复：AI连接测试成功",
                    AiModuleCode.NUTRITION_SUGGESTION,
                    "test");
            success = result.isSuccess();
            message = success ? result.getContent() : result.getErrorMessage();
        } else {
            AiVisionDetectionResult result = openAiCompatibleService.analyzeImage(
                    config,
                    TEST_PNG,
                    "image/png",
                    "请识别图像并输出JSON：{\"summary\":\"...\",\"modelVersion\":\"...\",\"violations\":[]}",
                    AiModuleCode.VIOLATION_RECOGNITION,
                    "test");
            success = result.isSuccess();
            message = success ? (result.getSummary() == null || result.getSummary().isBlank() ? "视觉连接测试成功" : result.getSummary()) : result.getErrorMessage();
        }
        AiServiceConfig update = configService.getByIdOrThrow(id);
        update.setLastTestStatus(success ? "success" : "failed");
        update.setLastTestMessage(message);
        update.setLastTestAt(java.time.LocalDateTime.now());
        configService.update(update, null);
        return new AiServiceTestVO(success, message);
    }

    @Override
    public Page<AiRequestLog> logs(Long id, Long pageNum, Long pageSize) {
        return requestLogService.page(id, null, null, pageNum, pageSize);
    }

    private AiServiceConfigVO toVo(AiServiceConfig config) {
        AiServiceConfigVO vo = new AiServiceConfigVO();
        BeanUtils.copyProperties(config, vo);
        vo.setApplicableModules(configService.parseModules(config.getApplicableModules()));
        vo.setApiKeyMasked(cryptoService.mask(cryptoService.decrypt(config.getApiKeyEncrypted())));
        return vo;
    }

    private String writeModules(java.util.List<String> modules) {
        try {
            return objectMapper.writeValueAsString(modules);
        } catch (Exception e) {
            throw new BizException("适用模块格式错误");
        }
    }
}
