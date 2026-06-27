package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.Complaint;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 投诉Mapper接口
 */
@Mapper
public interface ComplaintMapper extends BaseMapper<Complaint> {

    /**
     * 生成投诉编号
     * 格式：CP-yyyyMMdd{3位序号}
     */
    @Select("SELECT CONCAT('CP-', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(CAST(SUBSTRING(complaint_no, 12) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM sys_complaint " +
            "WHERE complaint_no LIKE CONCAT('CP-', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0")
    String generateComplaintNo();

    /**
     * 根据状态统计投诉数
     */
    @Select("SELECT COUNT(*) FROM sys_complaint WHERE status = #{status} AND deleted = 0 AND (org_id = #{orgId} OR #{orgId} IS NULL)")
    Long countByStatus(@Param("status") String status, @Param("orgId") Long orgId);
}
