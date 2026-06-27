package com.xykj.common.service;

import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 物料类别面积系数冻结锁校验服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialCategoryCoefficientLockService {

    private final JdbcTemplate jdbcTemplate;

    public void assertUnlockedByMaterialIds(Collection<Long> materialIds, String scene) {
        List<Long> normalizedIds = normalizeLongIds(materialIds);
        if (normalizedIds.isEmpty()) {
            return;
        }

        String sql = """
                SELECT DISTINCT l.dict_id AS dictId,
                                l.dict_name AS categoryName,
                                l.lock_type AS lockType,
                                l.lock_reason AS lockReason,
                                l.correction_version AS correctionVersion,
                                l.task_id AS taskId
                  FROM sys_dict_area_coefficient_lock l
                  JOIN wms_material m
                    ON m.material_category = l.dict_name
                   AND m.deleted = 0
                   AND m.tenant_id = l.tenant_id
                 WHERE l.deleted = 0
                   AND l.lock_status = 'locked'
                   AND l.tenant_id = ?
                   AND m.id IN (%s)
                """.formatted(placeholders(normalizedIds.size()));
        List<Object> args = new ArrayList<>();
        args.add(resolveTenantId());
        args.addAll(normalizedIds);
        failIfLocked(queryLocks(sql, args), scene);
    }

    public void assertUnlockedByCategoryNames(Collection<String> categoryNames, String scene) {
        List<String> normalizedNames = normalizeStrings(categoryNames);
        if (normalizedNames.isEmpty()) {
            return;
        }

        String sql = """
                SELECT l.dict_id AS dictId,
                       l.dict_name AS categoryName,
                       l.lock_type AS lockType,
                       l.lock_reason AS lockReason,
                       l.correction_version AS correctionVersion,
                       l.task_id AS taskId
                  FROM sys_dict_area_coefficient_lock l
                 WHERE l.deleted = 0
                   AND l.lock_status = 'locked'
                   AND l.tenant_id = ?
                   AND l.dict_name IN (%s)
                """.formatted(placeholders(normalizedNames.size()));
        List<Object> args = new ArrayList<>();
        args.add(resolveTenantId());
        args.addAll(normalizedNames);
        failIfLocked(queryLocks(sql, args), scene);
    }

    public void assertUnlockedByRecipeIds(Collection<Long> recipeIds, String scene) {
        List<Long> normalizedIds = normalizeLongIds(recipeIds);
        if (normalizedIds.isEmpty()) {
            return;
        }

        String sql = """
                SELECT DISTINCT l.dict_id AS dictId,
                                l.dict_name AS categoryName,
                                l.lock_type AS lockType,
                                l.lock_reason AS lockReason,
                                l.correction_version AS correctionVersion,
                                l.task_id AS taskId
                  FROM sys_dict_area_coefficient_lock l
                  JOIN wms_material m
                    ON m.material_category = l.dict_name
                   AND m.deleted = 0
                   AND m.tenant_id = l.tenant_id
                  JOIN recipe_ingredient ri
                    ON ri.material_id = m.id
                   AND ri.deleted = 0
                 WHERE l.deleted = 0
                   AND l.lock_status = 'locked'
                   AND l.tenant_id = ?
                   AND ri.recipe_id IN (%s)
                """.formatted(placeholders(normalizedIds.size()));
        List<Object> args = new ArrayList<>();
        args.add(resolveTenantId());
        args.addAll(normalizedIds);
        failIfLocked(queryLocks(sql, args), scene);
    }

    public boolean hasActiveLockByDictId(Long dictId) {
        return getActiveLockByDictId(dictId) != null;
    }

    public LockInfo getActiveLockByDictId(Long dictId) {
        if (dictId == null) {
            return null;
        }
        List<Map<String, Object>> rows = queryLockRows(
                """
                SELECT dict_id AS dictId,
                       dict_name AS categoryName,
                       lock_type AS lockType,
                       lock_reason AS lockReason,
                       correction_version AS correctionVersion,
                       task_id AS taskId
                  FROM sys_dict_area_coefficient_lock
                 WHERE deleted = 0
                   AND lock_status = 'locked'
                   AND tenant_id = ?
                   AND dict_id = ?
                 LIMIT 1
                """,
                resolveTenantId(),
                dictId
        );
        if (rows.isEmpty()) {
            return null;
        }
        return mapLock(rows.get(0));
    }

    private void failIfLocked(List<LockInfo> locks, String scene) {
        if (locks.isEmpty()) {
            return;
        }
        Map<Long, LockInfo> unique = new LinkedHashMap<>();
        for (LockInfo lock : locks) {
            unique.putIfAbsent(lock.getDictId(), lock);
        }
        LockInfo firstLock = unique.values().iterator().next();
        String lockAction = switch (Objects.toString(firstLock.getLockType(), "")) {
            case "coefficient_update" -> "面积系数修正";
            case "history_recalc" -> "历史回溯重算";
            default -> "面积系数处理";
        };
        String message = "物料类别“" + firstLock.getCategoryName() + "”正在执行" + lockAction
                + "，当前不允许" + scene + "，请稍后重试";
        throw BizException.conflict(message);
    }

    private List<LockInfo> queryLocks(String sql, List<Object> args) {
        List<Map<String, Object>> rows = queryLockRows(sql, args.toArray());
        List<LockInfo> locks = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            locks.add(mapLock(row));
        }
        return locks;
    }

    private List<Map<String, Object>> queryLockRows(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForList(sql, args);
        } catch (BadSqlGrammarException ex) {
            if (isMissingLockTable(ex)) {
                log.warn("跳过物料类别面积系数锁校验：冻结锁表未部署");
                return List.of();
            }
            throw ex;
        }
    }

    private boolean isMissingLockTable(BadSqlGrammarException ex) {
        Throwable cause = ex.getCause();
        String message = cause != null ? cause.getMessage() : ex.getMessage();
        return message != null && message.contains("sys_dict_area_coefficient_lock") && message.contains("doesn't exist");
    }

    private LockInfo mapLock(Map<String, Object> row) {
        LockInfo info = new LockInfo();
        info.setDictId(toLong(row.get("dictId")));
        info.setCategoryName(Objects.toString(row.get("categoryName"), null));
        info.setLockType(Objects.toString(row.get("lockType"), null));
        info.setLockReason(Objects.toString(row.get("lockReason"), null));
        info.setCorrectionVersion(toInteger(row.get("correctionVersion")));
        info.setTaskId(toLong(row.get("taskId")));
        return info;
    }

    private List<Long> normalizeLongIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id != null && id > 0) {
                normalized.add(id);
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<String> normalizeStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    @Getter
    public static class LockInfo {
        private Long dictId;
        private String categoryName;
        private String lockType;
        private String lockReason;
        private Integer correctionVersion;
        private Long taskId;

        public void setDictId(Long dictId) {
            this.dictId = dictId;
        }

        public void setCategoryName(String categoryName) {
            this.categoryName = categoryName;
        }

        public void setLockType(String lockType) {
            this.lockType = lockType;
        }

        public void setLockReason(String lockReason) {
            this.lockReason = lockReason;
        }

        public void setCorrectionVersion(Integer correctionVersion) {
            this.correctionVersion = correctionVersion;
        }

        public void setTaskId(Long taskId) {
            this.taskId = taskId;
        }
    }
}
