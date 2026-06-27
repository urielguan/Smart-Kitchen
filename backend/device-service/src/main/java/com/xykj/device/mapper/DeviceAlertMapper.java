package com.xykj.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.device.entity.DeviceAlert;
import com.xykj.device.vo.AlertVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 告警记录Mapper
 */
@Mapper
public interface DeviceAlertMapper extends BaseMapper<DeviceAlert> {

    /**
     * 分页查询告警列表
     * 注意: sys_user表在sys-service中,这里不进行关联查询
     */
    @Select("<script>" +
            "SELECT a.*, d.device_type " +
            "FROM device_alert a " +
            "LEFT JOIN device_info d ON a.device_id = d.id " +
            "WHERE a.deleted = 0 " +
            "<if test='query.orgId != null'> AND a.org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'> AND a.org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'> AND a.id IS NULL</if>" +
            "<if test='query.alertType != null and query.alertType != \"\"'> AND a.alert_type = #{query.alertType}</if>" +
            "<if test='query.alertLevel != null and query.alertLevel != \"\"'> AND a.alert_level = #{query.alertLevel}</if>" +
            "<if test='query.status != null and query.status != \"\"'> AND a.status = #{query.status}</if>" +
            "<if test='query.deviceId != null'> AND a.device_id = #{query.deviceId}</if>" +
            "<if test='query.assignedTo != null'> AND a.assigned_to = #{query.assignedTo}</if>" +
            "<if test='query.startTime != null and query.startTime != \"\"'> AND a.triggered_at &gt;= #{query.startTime}</if>" +
            "<if test='query.endTime != null and query.endTime != \"\"'> AND a.triggered_at &lt;= #{query.endTime}</if>" +
            "<if test='query.showSuppressed == null or query.showSuppressed == false'> AND (a.suppressed = 0 OR a.suppressed IS NULL)</if>" +
            "ORDER BY a.triggered_at DESC " +
            "</script>")
    Page<AlertVO> selectAlertPage(Page<AlertVO> page, @Param("query") com.xykj.device.dto.AlertQueryDTO query);

    /**
     * 按告警类型统计数量
     */
    @Select("<script>" +
            "SELECT alert_type as alertType, COUNT(*) as count FROM device_alert WHERE deleted = 0 " +
            "<if test='query.showSuppressed == null or query.showSuppressed == false'> AND (suppressed = 0 OR suppressed IS NULL)</if>" +
            "<if test='query.orgId != null'> AND org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'> AND id IS NULL</if>" +
            " GROUP BY alert_type" +
            "</script>")
    List<Map<String, Object>> countByAlertType(@Param("query") com.xykj.device.dto.AlertQueryDTO query);

    /**
     * 按告警级别统计数量
     */
    @Select("<script>" +
            "SELECT alert_level as alertLevel, COUNT(*) as count FROM device_alert WHERE deleted = 0 " +
            "<if test='query.showSuppressed == null or query.showSuppressed == false'> AND (suppressed = 0 OR suppressed IS NULL)</if>" +
            "<if test='query.orgId != null'> AND org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'> AND id IS NULL</if>" +
            " GROUP BY alert_level" +
            "</script>")
    List<Map<String, Object>> countByAlertLevel(@Param("query") com.xykj.device.dto.AlertQueryDTO query);

    /**
     * 按状态统计数量
     */
    @Select("<script>" +
            "SELECT status, COUNT(*) as count FROM device_alert WHERE deleted = 0 " +
            "<if test='query.showSuppressed == null or query.showSuppressed == false'> AND (suppressed = 0 OR suppressed IS NULL)</if>" +
            "<if test='query.orgId != null'> AND org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'> AND id IS NULL</if>" +
            " GROUP BY status" +
            "</script>")
    List<Map<String, Object>> countByStatus(@Param("query") com.xykj.device.dto.AlertQueryDTO query);

    /**
     * 统计最近7天告警趋势
     */
    @Select("<script>" +
            "SELECT DATE(triggered_at) as date, COUNT(*) as count FROM device_alert " +
            "WHERE deleted = 0 AND triggered_at >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
            "<if test='query.showSuppressed == null or query.showSuppressed == false'> AND (suppressed = 0 OR suppressed IS NULL)</if>" +
            "<if test='query.orgId != null'> AND org_id = #{query.orgId}</if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() > 0'> AND org_id IN " +
            "<foreach collection='query.orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach></if>" +
            "<if test='query.orgId == null and query.orgIds != null and query.orgIds.size() == 0'> AND id IS NULL</if>" +
            " GROUP BY DATE(triggered_at) ORDER BY date" +
            "</script>")
    List<Map<String, Object>> countTrend7Days(@Param("query") com.xykj.device.dto.AlertQueryDTO query);
}
