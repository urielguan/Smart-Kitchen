package com.xykj.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.common.ai.mapper.AiRequestLogMapper;
import com.xykj.common.ai.service.AiRequestLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiRequestLogServiceImpl implements AiRequestLogService {

    private final AiRequestLogMapper mapper;

    @Override
    public void save(AiRequestLog requestLog) {
        try {
            mapper.insert(requestLog);
        } catch (Exception ex) {
            if (isMissingAiRequestLogTable(ex)) {
                log.warn("AI请求日志表未部署，跳过日志落库");
                return;
            }
            throw ex;
        }
    }

    @Override
    public Page<AiRequestLog> page(Long serviceConfigId, String moduleCode, String status, long pageNum, long pageSize) {
        try {
            LambdaQueryWrapper<AiRequestLog> wrapper = new LambdaQueryWrapper<AiRequestLog>()
                    .eq(serviceConfigId != null, AiRequestLog::getServiceConfigId, serviceConfigId)
                    .eq(moduleCode != null && !moduleCode.isBlank(), AiRequestLog::getModuleCode, moduleCode)
                    .eq(status != null && !status.isBlank(), AiRequestLog::getStatus, status)
                    .orderByDesc(AiRequestLog::getCreatedAt);
            return mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        } catch (Exception ex) {
            if (isMissingAiRequestLogTable(ex)) {
                log.warn("AI请求日志表未部署，返回空日志列表");
                return new Page<>(pageNum, pageSize, 0);
            }
            throw ex;
        }
    }

    private boolean isMissingAiRequestLogTable(Throwable ex) {
        String message = extractMessage(ex);
        return message != null
                && message.contains("sys_ai_request_log")
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
