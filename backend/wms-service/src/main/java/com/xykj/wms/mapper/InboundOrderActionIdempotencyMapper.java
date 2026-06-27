package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.InboundOrderActionIdempotency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundOrderActionIdempotencyMapper extends BaseMapper<InboundOrderActionIdempotency> {
}
