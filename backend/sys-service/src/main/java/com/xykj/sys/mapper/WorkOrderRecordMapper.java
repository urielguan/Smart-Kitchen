package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.WorkOrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工单处理记录Mapper接口
 */
@Mapper
public interface WorkOrderRecordMapper extends BaseMapper<WorkOrderRecord> {

    /**
     * 根据派单ID查询处理记录列表
     */
    @Select("SELECT * FROM sys_work_order_record WHERE dispatch_id = #{dispatchId} ORDER BY created_at ASC")
    List<WorkOrderRecord> selectByDispatchId(@Param("dispatchId") Long dispatchId);

    /**
     * 根据投诉ID查询处理记录列表
     */
    @Select("SELECT * FROM sys_work_order_record WHERE complaint_id = #{complaintId} ORDER BY created_at ASC")
    List<WorkOrderRecord> selectByComplaintId(@Param("complaintId") Long complaintId);
}
