package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.OutboundOrderAllocation;
import com.xykj.wms.vo.OutboundOrderAllocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboundOrderAllocationMapper extends BaseMapper<OutboundOrderAllocation> {

    List<OutboundOrderAllocationVO> selectByOutboundId(@Param("outboundId") Long outboundId);

    void deleteByOutboundId(@Param("outboundId") Long outboundId);
}
