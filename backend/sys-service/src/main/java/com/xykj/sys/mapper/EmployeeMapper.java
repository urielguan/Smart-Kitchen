package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.Employee;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 员工Mapper接口
 */
@Mapper
public interface EmployeeMapper extends BaseMapper<Employee> {

    /**
     * 根据状态统计数量
     */
    @Select("SELECT COUNT(*) FROM sys_employee WHERE status = #{status} AND deleted = 0")
    Long countByStatus(@Param("status") String status);

    /**
     * 检查员工工号是否存在（排除指定ID）
     */
    @Select("SELECT COUNT(*) FROM sys_employee WHERE employee_no = #{employeeNo} AND deleted = 0 AND (#{excludeId} IS NULL OR id != #{excludeId})")
    Long countByEmployeeNoExcludeId(@Param("employeeNo") String employeeNo, @Param("excludeId") Long excludeId);

    /**
     * 生成员工工号
     * 格式: EMP + YYYYMMDD + 4位序号
     */
    @Select("SELECT CONCAT('EMP', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(SUBSTRING(employee_no, 12)), 0) + 1, 4, '0')) " +
            "FROM sys_employee " +
            "WHERE employee_no LIKE CONCAT('EMP', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0")
    String generateEmployeeNo();

    /**
     * 统计组织下员工数量
     */
    @Select("SELECT COUNT(*) FROM sys_employee WHERE org_id = #{orgId} AND deleted = 0 AND status = 'active'")
    Long countByOrgId(@Param("orgId") Long orgId);

    /**
     * 批量统计各组织下员工数量
     */
    @Select("<script>" +
            "SELECT org_id AS orgId, COUNT(*) AS count FROM sys_employee " +
            "WHERE deleted = 0 AND status = 'active' AND org_id IN " +
            "<foreach collection='orgIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " GROUP BY org_id" +
            "</script>")
    List<Map<String, Object>> countByOrgIds(@Param("orgIds") List<Long> orgIds);

    /**
     * 查找负载最少的可用处理人（用于自动派单）
     * 规则：
     * 1. 员工属于指定组织
     * 2. 员工状态为 active（在职）
     * 3. 关联用户状态为 active（启用）
     * 4. 按当前待处理/处理中工单数量升序排序
     * 5. 返回负载最少的员工ID
     */
    /**
     * 根据用户ID查询员工ID
     */
    @Select("SELECT id FROM sys_employee WHERE user_id = #{userId} AND deleted = 0 LIMIT 1")
    Long selectIdByUserId(@Param("userId") Long userId);

    @Select("SELECT e.id " +
            "FROM sys_employee e " +
            "INNER JOIN auth_user u ON e.user_id = u.id " +
            "LEFT JOIN sys_complaint_dispatch d ON e.id = d.handler_id AND d.status IN ('pending', 'processing') AND d.deleted = 0 " +
            "WHERE e.org_id = #{orgId} " +
            "AND e.status = 'active' " +
            "AND u.status = 'active' " +
            "AND e.deleted = 0 " +
            "GROUP BY e.id " +
            "ORDER BY COUNT(d.id) ASC " +
            "LIMIT 1")
    Long selectLeastBusyHandler(@Param("orgId") Long orgId);
}