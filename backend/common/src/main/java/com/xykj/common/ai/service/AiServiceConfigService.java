package com.xykj.common.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiServiceConfig;

import java.util.List;

public interface AiServiceConfigService {

    Page<AiServiceConfig> page(String keyword, String serviceType, String status, long pageNum, long pageSize);

    AiServiceConfig getByIdOrThrow(Long id);

    Long create(AiServiceConfig config, String plainApiKey);

    void update(AiServiceConfig config, String plainApiKey);

    void delete(Long id);

    void changeStatus(Long id, String status);

    AiServiceConfig getActiveByModule(String serviceType, String moduleCode);

    List<String> parseModules(String applicableModules);
}
