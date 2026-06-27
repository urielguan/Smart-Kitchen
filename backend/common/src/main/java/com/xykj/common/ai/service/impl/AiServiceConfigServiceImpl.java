package com.xykj.common.ai.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.config.AiProperties;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.mapper.AiServiceConfigMapper;
import com.xykj.common.ai.service.AiConfigCryptoService;
import com.xykj.common.ai.service.AiServiceConfigService;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceConfigServiceImpl implements AiServiceConfigService {

    private final AiServiceConfigMapper mapper;
    private final AiProperties aiProperties;
    private final AiConfigCryptoService cryptoService;
    private final ObjectMapper objectMapper;

    @Override
    public Page<AiServiceConfig> page(String keyword, String serviceType, String status, long pageNum, long pageSize) {
        try {
            LambdaQueryWrapper<AiServiceConfig> wrapper = new LambdaQueryWrapper<AiServiceConfig>()
                    .like(CharSequenceUtil.isNotBlank(keyword), AiServiceConfig::getServiceName, keyword)
                    .eq(CharSequenceUtil.isNotBlank(serviceType), AiServiceConfig::getServiceType, serviceType)
                    .eq(CharSequenceUtil.isNotBlank(status), AiServiceConfig::getStatus, status)
                    .eq(AiServiceConfig::getDeleted, 0)
                    .orderByDesc(AiServiceConfig::getUpdatedAt);
            return mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        } catch (Exception ex) {
            if (isMissingAiConfigTable(ex)) {
                log.warn("AI服务配置表未部署，返回空列表");
                return new Page<>(pageNum, pageSize, 0);
            }
            throw ex;
        }
    }

    @Override
    public AiServiceConfig getByIdOrThrow(Long id) {
        AiServiceConfig config;
        try {
            config = mapper.selectById(id);
        } catch (Exception ex) {
            if (isMissingAiConfigTable(ex)) {
                throw new BizException("AI服务配置表未初始化");
            }
            throw ex;
        }
        if (config == null || Integer.valueOf(1).equals(config.getDeleted())) {
            throw new BizException("AI服务配置不存在");
        }
        return config;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(AiServiceConfig config, String plainApiKey) {
        validateUniqueName(config.getServiceName(), null);
        config.setApiKeyEncrypted(cryptoService.encrypt(plainApiKey));
        config.setStatus(CharSequenceUtil.blankToDefault(config.getStatus(), "inactive"));
        config.setTenantId(UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L);
        mapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiServiceConfig config, String plainApiKey) {
        AiServiceConfig existing = getByIdOrThrow(config.getId());
        validateUniqueName(config.getServiceName(), config.getId());
        existing.setServiceName(config.getServiceName());
        existing.setServiceType(config.getServiceType());
        existing.setBaseUrl(config.getBaseUrl());
        existing.setModelName(config.getModelName());
        existing.setApplicableModules(config.getApplicableModules());
        existing.setRemark(config.getRemark());
        if (config.getLastTestStatus() != null) {
            existing.setLastTestStatus(config.getLastTestStatus());
        }
        if (config.getLastTestMessage() != null) {
            existing.setLastTestMessage(config.getLastTestMessage());
        }
        if (config.getLastTestAt() != null) {
            existing.setLastTestAt(config.getLastTestAt());
        }
        if (CharSequenceUtil.isNotBlank(plainApiKey)) {
            existing.setApiKeyEncrypted(cryptoService.encrypt(plainApiKey));
        }
        mapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        mapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        AiServiceConfig existing = getByIdOrThrow(id);
        existing.setStatus(status);
        mapper.updateById(existing);
    }

    @Override
    public AiServiceConfig getActiveByModule(String serviceType, String moduleCode) {
        List<AiServiceConfig> configs;
        try {
            configs = mapper.selectList(new LambdaQueryWrapper<AiServiceConfig>()
                    .eq(AiServiceConfig::getServiceType, serviceType)
                    .eq(AiServiceConfig::getStatus, "active")
                    .eq(AiServiceConfig::getDeleted, 0)
                    .orderByDesc(AiServiceConfig::getUpdatedAt));
        } catch (Exception ex) {
            if (isMissingAiConfigTable(ex)) {
                log.warn("AI服务配置表未部署，尝试使用Nacos兜底配置");
                configs = List.of();
            } else {
                throw ex;
            }
        }
        return configs.stream()
                .filter(item -> parseModules(item.getApplicableModules()).contains(moduleCode))
                .findFirst()
                .orElseGet(() -> buildNacosFallback(serviceType, moduleCode)
                        .orElseThrow(() -> new BizException("未配置可用AI服务：" + moduleCode)));
    }

    @Override
    public List<String> parseModules(String applicableModules) {
        if (CharSequenceUtil.isBlank(applicableModules)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(applicableModules, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private void validateUniqueName(String serviceName, Long currentId) {
        LambdaQueryWrapper<AiServiceConfig> wrapper = new LambdaQueryWrapper<AiServiceConfig>()
                .eq(AiServiceConfig::getServiceName, serviceName)
                .eq(AiServiceConfig::getDeleted, 0)
                .ne(currentId != null, AiServiceConfig::getId, currentId);
        if (mapper.selectCount(wrapper) > 0) {
            throw new BizException("AI服务名称已存在");
        }
    }

    private java.util.Optional<AiServiceConfig> buildNacosFallback(String serviceType, String moduleCode) {
        if (!aiProperties.isNacosFallbackEnabled() || aiProperties.getDefaultServices() == null) {
            return java.util.Optional.empty();
        }
        return aiProperties.getDefaultServices().stream()
                .filter(item -> serviceType.equals(item.getServiceType()))
                .filter(item -> "active".equalsIgnoreCase(item.getStatus()))
                .filter(item -> item.getApplicableModules() != null && item.getApplicableModules().contains(moduleCode))
                .filter(item -> CharSequenceUtil.isNotBlank(item.getBaseUrl()))
                .filter(item -> CharSequenceUtil.isNotBlank(item.getApiKey()) || CharSequenceUtil.isNotBlank(item.getApiKeyEncrypted()))
                .filter(item -> CharSequenceUtil.isNotBlank(item.getModelName()))
                .findFirst()
                .map(this::toFallbackConfig);
    }

    private AiServiceConfig toFallbackConfig(AiProperties.DefaultService service) {
        AiServiceConfig config = new AiServiceConfig();
        config.setId(0L);
        config.setServiceName(service.getServiceName());
        config.setServiceType(service.getServiceType());
        config.setBaseUrl(service.getBaseUrl());
        config.setModelName(service.getModelName());
        config.setStatus(service.getStatus());
        config.setRemark(service.getRemark());
        config.setTenantId(1L);
        config.setOrgId(0L);
        config.setDeleted(0);
        if (CharSequenceUtil.isNotBlank(service.getApiKeyEncrypted())) {
            config.setApiKeyEncrypted(service.getApiKeyEncrypted());
        } else {
            config.setApiKeyEncrypted(cryptoService.encrypt(service.getApiKey()));
        }
        try {
            config.setApplicableModules(objectMapper.writeValueAsString(service.getApplicableModules()));
        } catch (JsonProcessingException e) {
            config.setApplicableModules("[]");
        }
        return config;
    }

    private boolean isMissingAiConfigTable(Throwable ex) {
        String message = extractMessage(ex);
        return message != null
                && message.contains("sys_ai_service_config")
                && (message.contains("doesn't exist") || message.contains("does not exist"));
    }

    private String extractMessage(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return null;
    }
}
