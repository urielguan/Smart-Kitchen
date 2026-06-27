package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.OutboundImportTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OutboundImportTaskMapper extends BaseMapper<OutboundImportTask> {

    OutboundImportTask selectByTaskNo(@Param("taskNo") String taskNo);
}
