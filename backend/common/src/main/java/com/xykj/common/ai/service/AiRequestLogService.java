package com.xykj.common.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiRequestLog;

public interface AiRequestLogService {

    void save(AiRequestLog log);

    Page<AiRequestLog> page(Long serviceConfigId, String moduleCode, String status, long pageNum, long pageSize);
}
