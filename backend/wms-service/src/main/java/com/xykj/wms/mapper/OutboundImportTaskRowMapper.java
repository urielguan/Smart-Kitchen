package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.OutboundImportTaskRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboundImportTaskRowMapper extends BaseMapper<OutboundImportTaskRow> {

    List<OutboundImportTaskRow> selectByTaskId(@Param("taskId") Long taskId);
}
