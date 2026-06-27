package com.xykj.common.service;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLSyntaxErrorException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MaterialCategoryCoefficientLockServiceBehaviorTest {

    @Test
    void assertUnlockedByMaterialIds_skips_when_lock_table_is_missing() {
        MaterialCategoryCoefficientLockService service = new MaterialCategoryCoefficientLockService(new MissingLockTableJdbcTemplate());

        assertDoesNotThrow(() -> service.assertUnlockedByMaterialIds(List.of(101L), "提交入库单"));
    }

    @Test
    void getActiveLockByDictId_returns_null_when_lock_table_is_missing() {
        MaterialCategoryCoefficientLockService service = new MaterialCategoryCoefficientLockService(new MissingLockTableJdbcTemplate());

        assertNull(service.getActiveLockByDictId(1L));
        assertFalse(service.hasActiveLockByDictId(1L));
    }

    private static final class MissingLockTableJdbcTemplate extends JdbcTemplate {
        @Override
        public java.util.List<java.util.Map<String, Object>> queryForList(String sql, Object... args) {
            throw new BadSqlGrammarException(
                    "queryForList",
                    sql,
                    new SQLSyntaxErrorException("Table 'smart_food_safety.sys_dict_area_coefficient_lock' doesn't exist")
            );
        }
    }
}
