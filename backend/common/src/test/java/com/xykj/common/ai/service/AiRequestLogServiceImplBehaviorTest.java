package com.xykj.common.ai.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.ai.entity.AiRequestLog;
import com.xykj.common.ai.mapper.AiRequestLogMapper;
import com.xykj.common.ai.service.impl.AiRequestLogServiceImpl;
import org.junit.jupiter.api.Test;

import java.sql.SQLSyntaxErrorException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiRequestLogServiceImplBehaviorTest {

    @Test
    void save_skips_when_ai_request_log_table_is_missing() {
        AiRequestLogMapper mapper = mock(AiRequestLogMapper.class);
        when(mapper.insert(any(AiRequestLog.class))).thenThrow(new RuntimeException(
                new SQLSyntaxErrorException("Table 'smart_food_safety.sys_ai_request_log' doesn't exist")
        ));

        AiRequestLogServiceImpl service = new AiRequestLogServiceImpl(mapper);

        assertDoesNotThrow(() -> service.save(new AiRequestLog()));
    }

    @Test
    void page_returns_empty_when_ai_request_log_table_is_missing() {
        AiRequestLogMapper mapper = mock(AiRequestLogMapper.class);
        when(mapper.selectPage(any(), any())).thenThrow(new RuntimeException(
                new SQLSyntaxErrorException("Table 'smart_food_safety.sys_ai_request_log' doesn't exist")
        ));

        AiRequestLogServiceImpl service = new AiRequestLogServiceImpl(mapper);
        Page<AiRequestLog> page = service.page(1L, null, null, 1, 20);

        assertEquals(0, page.getTotal());
        assertTrue(page.getRecords().isEmpty());
    }
}
