package com.xykj.common.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.common.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 操作审计日志 Mapper
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {

}
