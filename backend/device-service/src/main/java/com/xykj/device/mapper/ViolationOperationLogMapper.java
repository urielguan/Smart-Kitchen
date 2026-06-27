package com.xykj.device.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.device.entity.ViolationOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 违规事件操作日志Mapper
 */
@Mapper
public interface ViolationOperationLogMapper extends BaseMapper<ViolationOperationLog> {
}
