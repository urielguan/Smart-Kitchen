package com.xykj.common.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.AiModuleCode;
import com.xykj.common.ai.AiServiceType;
import com.xykj.common.ai.config.AiProperties;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.mapper.AiServiceConfigMapper;
import com.xykj.common.ai.service.impl.AiConfigCryptoServiceImpl;
import com.xykj.common.ai.service.impl.AiServiceConfigServiceImpl;
import org.junit.jupiter.api.Test;

import java.sql.SQLSyntaxErrorException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiServiceConfigServiceImplBehaviorTest {

    @Test
    void page_returns_empty_when_ai_config_table_is_missing() {
        AiServiceConfigMapper mapper = mock(AiServiceConfigMapper.class);
        when(mapper.selectPage(any(), any())).thenThrow(new RuntimeException(
                new SQLSyntaxErrorException("Table 'smart_food_safety.sys_ai_service_config' doesn't exist")
        ));

        AiServiceConfigServiceImpl service = buildService(mapper, new AiProperties());
        Page<AiServiceConfig> page = service.page(null, null, null, 1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }

    @Test
    void getActiveByModule_uses_nacos_fallback_when_ai_config_table_is_missing() {
        AiServiceConfigMapper mapper = mock(AiServiceConfigMapper.class);
        when(mapper.selectList(any())).thenThrow(new RuntimeException(
                new SQLSyntaxErrorException("Table 'smart_food_safety.sys_ai_service_config' doesn't exist")
        ));

        AiProperties properties = new AiProperties();
        AiProperties.DefaultService defaultService = new AiProperties.DefaultService();
        defaultService.setServiceName("共享文本模型");
        defaultService.setServiceType(AiServiceType.TEXT);
        defaultService.setBaseUrl("https://example.com/v1");
        defaultService.setApiKeyEncrypted("cipher");
        defaultService.setModelName("deepseek-test");
        defaultService.setApplicableModules(List.of(AiModuleCode.NUTRITION_SUGGESTION));
        defaultService.setStatus("active");
        properties.setDefaultServices(List.of(defaultService));

        AiServiceConfigServiceImpl service = buildService(mapper, properties);
        AiServiceConfig config = service.getActiveByModule(AiServiceType.TEXT, AiModuleCode.NUTRITION_SUGGESTION);

        assertEquals("共享文本模型", config.getServiceName());
        assertEquals("https://example.com/v1", config.getBaseUrl());
        assertEquals("cipher", config.getApiKeyEncrypted());
    }

    private AiServiceConfigServiceImpl buildService(AiServiceConfigMapper mapper, AiProperties properties) {
        return new AiServiceConfigServiceImpl(
                mapper,
                properties,
                new AiConfigCryptoServiceImpl(properties),
                new ObjectMapper()
        );
    }
}
