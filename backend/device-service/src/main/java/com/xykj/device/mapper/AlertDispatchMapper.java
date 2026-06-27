package com.xykj.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.dto.AlertDispatchQueryDTO;
import com.xykj.device.entity.AlertDispatch;
import com.xykj.device.vo.AlertDispatchVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 告警派单Mapper接口
 */
@Mapper
public interface AlertDispatchMapper extends BaseMapper<AlertDispatch> {

    /**
     * 生成派单编号
     * 格式：AD-yyyyMMdd{4位序号}
     */
    @Select("SELECT CONCAT('AD-', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(CAST(SUBSTRING(dispatch_no, 13) AS UNSIGNED)), 0) + 1, 4, '0')) " +
            "FROM device_alert_dispatch " +
            "WHERE dispatch_no LIKE CONCAT('AD-', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0")
    String generateDispatchNo();

    /**
     * 分页查询派单工单列表
     */
    @Select("<script>" +
            "SELECT d.*, " +
            "a.alert_type, a.alert_level, a.alert_content, " +
            "a.assigned_to as alert_assigned_to " +
            "FROM device_alert_dispatch d " +
            "LEFT JOIN device_alert a ON d.alert_id = a.id " +
            "WHERE d.deleted = 0 " +
            "<if test='query.orgId != null'> AND d.org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'>" +
            " AND (d.org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach>" +
            "<if test='query.handlerEmployeeId != null'> OR d.handler_id = #{query.handlerEmployeeId}</if>)</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'>" +
            "<if test='query.handlerEmployeeId != null'> AND d.handler_id = #{query.handlerEmployeeId}</if>" +
            "<if test='query.handlerEmployeeId == null'> AND d.id IS NULL</if></if>" +
            "<if test='query.status != null and query.status != \"\"'> AND d.status = #{query.status}</if>" +
            "<if test='query.dispatchType != null and query.dispatchType != \"\"'> AND d.dispatch_type = #{query.dispatchType}</if>" +
            "<if test='query.handlerName != null and query.handlerName != \"\"'> AND d.handler_name LIKE CONCAT('%', #{query.handlerName}, '%')</if>" +
            "<if test='query.startTime != null and query.startTime != \"\"'> AND d.created_at &gt;= #{query.startTime}</if>" +
            "<if test='query.endTime != null and query.endTime != \"\"'> AND d.created_at &lt;= #{query.endTime}</if>" +
            "ORDER BY d.created_at DESC " +
            "</script>")
    Page<AlertDispatchVO> selectDispatchPage(Page<AlertDispatchVO> page, @Param("query") AlertDispatchQueryDTO query);

    /**
     * 查找负载最少的可用处理人（用于自动派单）
     * 规则：
     * 1. 员工状态为 active（在职）
     * 2. 关联用户状态为 active（启用）
     * 3. 按当前待处理/处理中工单数量升序排序
     * 4. 返回负载最少的员工ID
     * 注：不限制组织，与人工派单保持一致（全公司范围）
     */
    @Select("SELECT e.id " +
            "FROM sys_employee e " +
            "INNER JOIN auth_user u ON e.user_id = u.id " +
            "LEFT JOIN device_alert_dispatch d ON e.id = d.handler_id AND d.status IN ('pending', 'processing') AND d.deleted = 0 " +
            "WHERE e.status = 'active' " +
            "AND u.status = 'active' " +
            "AND e.deleted = 0 " +
            "GROUP BY e.id " +
            "ORDER BY COUNT(d.id) ASC " +
            "LIMIT 1")
    Long selectLeastBusyHandler();

    /**
     * 按角色过滤查找负载最少的可用处理人（用于自动派单）
     * 仅在指定角色范围内选择处理人（全公司范围，不限制组织）
     */
    @Select("<script>" +
            "SELECT e.id " +
            "FROM sys_employee e " +
            "INNER JOIN auth_user u ON e.user_id = u.id " +
            "INNER JOIN auth_user_role ur ON ur.user_id = u.id " +
            "LEFT JOIN device_alert_dispatch d ON e.id = d.handler_id AND d.status IN ('pending', 'processing') AND d.deleted = 0 " +
            "WHERE e.status = 'active' " +
            "AND u.status = 'active' " +
            "AND e.deleted = 0 " +
            "AND ur.role_id IN " +
            "<foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>#{roleId}</foreach>" +
            " GROUP BY e.id " +
            "ORDER BY COUNT(d.id) ASC " +
            "LIMIT 1" +
            "</script>")
    Long selectLeastBusyHandlerByRoles(@Param("roleIds") List<Long> roleIds);
}
