package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.ComplaintDispatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 投诉派单Mapper接口
 */
@Mapper
public interface ComplaintDispatchMapper extends BaseMapper<ComplaintDispatch> {

    /**
     * 生成派单编号
     * 格式：DP-yyyyMMdd{3位序号}
     */
    @Select("SELECT CONCAT('DP-', DATE_FORMAT(NOW(), '%Y%m%d'), LPAD(IFNULL(MAX(CAST(SUBSTRING(dispatch_no, 12) AS UNSIGNED)), 0) + 1, 3, '0')) " +
            "FROM sys_complaint_dispatch " +
            "WHERE dispatch_no LIKE CONCAT('DP-', DATE_FORMAT(NOW(), '%Y%m%d'), '%') AND deleted = 0")
    String generateDispatchNo();

    /**
     * 根据投诉ID查询派单
     */
    @Select("SELECT * FROM sys_complaint_dispatch WHERE complaint_id = #{complaintId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    ComplaintDispatch selectByComplaintId(@Param("complaintId") Long complaintId);

    /**
     * 根据处理人ID统计待处理工单数
     */
    @Select("SELECT COUNT(*) FROM sys_complaint_dispatch WHERE handler_id = #{handlerId} AND status IN ('pending', 'processing') AND deleted = 0")
    Long countPendingByHandlerId(@Param("handlerId") Long handlerId);
}
