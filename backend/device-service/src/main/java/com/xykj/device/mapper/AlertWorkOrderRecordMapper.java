package com.xykj.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.device.entity.AlertWorkOrderRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 告警工单操作记录Mapper接口
 */
@Mapper
public interface AlertWorkOrderRecordMapper extends BaseMapper<AlertWorkOrderRecord> {

    @Select("SELECT * FROM device_alert_work_order_record WHERE dispatch_id = #{dispatchId} ORDER BY created_at ASC")
    List<AlertWorkOrderRecord> selectByDispatchId(@Param("dispatchId") Long dispatchId);
}
