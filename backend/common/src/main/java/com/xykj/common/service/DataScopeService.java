package com.xykj.common.service;

import com.xykj.common.context.UserContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一解析当前登录用户的数据权限组织范围。
 */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private static final String DATA_SCOPE_ALL = "all";
    private static final String DATA_SCOPE_CUSTOM = "custom";
    private static final String DATA_SCOPE_DEPT = "dept";
    private static final String DATA_SCOPE_DEPT_AND_CHILD = "dept_and_child";
    private static final String DATA_SCOPE_SELF = "self";

    private final JdbcTemplate jdbcTemplate;

    public DataScopeResult resolveCurrentUserOrgScope() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return DataScopeResult.none();
        }
        if (isAdminUser()) {
            return DataScopeResult.all();
        }

        List<Map<String, Object>> roleRows = jdbcTemplate.queryForList(
                "SELECT r.id AS roleId, LOWER(TRIM(COALESCE(r.data_scope, 'all'))) AS dataScope " +
                        "FROM auth_user_role ur " +
                        "INNER JOIN auth_role r ON r.id = ur.role_id " +
                        "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 'active'",
                userId
        );
        if (CollectionUtils.isEmpty(roleRows)) {
            return DataScopeResult.none();
        }

        List<Long> roleIds = new ArrayList<>();
        Set<String> scopes = new LinkedHashSet<>();
        for (Map<String, Object> row : roleRows) {
            Object roleIdObj = row.get("roleId");
            if (roleIdObj instanceof Number number) {
                roleIds.add(number.longValue());
            }
            Object scopeObj = row.get("dataScope");
            scopes.add(scopeObj == null ? DATA_SCOPE_ALL : String.valueOf(scopeObj));
        }

        if (scopes.contains(DATA_SCOPE_ALL)) {
            return DataScopeResult.all();
        }

        Set<Long> allowedOrgIds = new LinkedHashSet<>();
        Long currentOrgId = resolveCurrentUserOrgId(userId);

        if ((scopes.contains(DATA_SCOPE_DEPT) || scopes.contains(DATA_SCOPE_SELF)) && currentOrgId != null) {
            allowedOrgIds.add(currentOrgId);
        }

        if (scopes.contains(DATA_SCOPE_DEPT_AND_CHILD) && currentOrgId != null) {
            allowedOrgIds.addAll(resolveDeptAndChildOrgIds(currentOrgId));
        }

        if (scopes.contains(DATA_SCOPE_CUSTOM) && !roleIds.isEmpty()) {
            allowedOrgIds.addAll(resolveCustomOrgIds(roleIds));
        }

        return DataScopeResult.of(allowedOrgIds);
    }

    public boolean isAdminUser() {
        String username = UserContext.getUsername();
        if ("admin".equalsIgnoreCase(username)) {
            return true;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }

        // 兼容 token 未携带 username 的场景：回表识别 admin 用户
        List<String> usernames = jdbcTemplate.query(
                "SELECT username FROM auth_user WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("username"),
                userId
        );
        return !usernames.isEmpty() && "admin".equalsIgnoreCase(usernames.get(0));
    }

    private Long resolveCurrentUserOrgId(Long userId) {
        Long orgId = UserContext.getOrgId();
        if (orgId != null) {
            return orgId;
        }
        if (userId == null) {
            return null;
        }

        List<Long> orgIds = jdbcTemplate.query(
                "SELECT COALESCE(u.org_id, e.org_id) AS org_id " +
                        "FROM auth_user u " +
                        "LEFT JOIN sys_employee e ON e.user_id = u.id AND e.deleted = 0 " +
                        "WHERE u.id = ? AND u.deleted = 0 LIMIT 1",
                (rs, rowNum) -> {
                    long value = rs.getLong("org_id");
                    return rs.wasNull() ? null : value;
                },
                userId
        );
        if (orgIds.isEmpty()) {
            return null;
        }
        Long dbOrgId = orgIds.get(0);
        return dbOrgId != null && dbOrgId > 0 ? dbOrgId : null;
    }

    private Set<Long> resolveCustomOrgIds(List<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return Collections.emptySet();
        }
        String placeholders = roleIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(roleIds);
        String sql = "SELECT DISTINCT rdso.org_id " +
                "FROM auth_role_data_scope_org rdso " +
                "INNER JOIN sys_organization o ON o.id = rdso.org_id " +
                "WHERE o.deleted = 0 AND rdso.role_id IN (" + placeholders + ")";
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args.toArray());
        return new LinkedHashSet<>(ids);
    }

    private Set<Long> resolveDeptAndChildOrgIds(Long currentOrgId) {
        List<String> paths = jdbcTemplate.query(
                "SELECT path FROM sys_organization WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("path"),
                currentOrgId
        );
        if (paths.isEmpty() || paths.get(0) == null || paths.get(0).isBlank()) {
            return Collections.singleton(currentOrgId);
        }
        String pathPrefix = paths.get(0);
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM sys_organization WHERE deleted = 0 AND (id = ? OR path LIKE CONCAT(?, '%'))",
                (rs, rowNum) -> rs.getLong("id"),
                currentOrgId,
                pathPrefix
        );
        return new LinkedHashSet<>(ids);
    }

    @Getter
    public static final class DataScopeResult {

        private final boolean allAccess;
        private final Set<Long> orgIds;

        private DataScopeResult(boolean allAccess, Set<Long> orgIds) {
            this.allAccess = allAccess;
            this.orgIds = orgIds;
        }

        public static DataScopeResult all() {
            return new DataScopeResult(true, Collections.emptySet());
        }

        public static DataScopeResult none() {
            return new DataScopeResult(false, Collections.emptySet());
        }

        public static DataScopeResult of(Set<Long> orgIds) {
            if (CollectionUtils.isEmpty(orgIds)) {
                return none();
            }
            return new DataScopeResult(false, Collections.unmodifiableSet(new LinkedHashSet<>(orgIds)));
        }

        public boolean isAllowed(Long orgId) {
            if (allAccess) {
                return true;
            }
            return orgId != null && orgIds.contains(orgId);
        }

        public boolean isRestricted() {
            return !allAccess;
        }
    }
}
