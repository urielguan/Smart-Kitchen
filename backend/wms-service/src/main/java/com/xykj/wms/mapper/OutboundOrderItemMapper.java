package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.OutboundOrderItem;
import com.xykj.wms.vo.OutboundOrderItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OutboundOrderItemMapper extends BaseMapper<OutboundOrderItem> {

    List<OutboundOrderItemVO> selectByOutboundId(@Param("outboundId") Long outboundId);

    void deleteByOutboundId(@Param("outboundId") Long outboundId);

    boolean existsWarehouseOutboundItemOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseOutboundItemHistory(@Param("warehouseId") Long warehouseId);

    boolean existsLocationOutboundOccupancy(@Param("locationId") Long locationId, @Param("statuses") List<String> statuses);

    boolean existsLocationOutboundHistory(@Param("locationId") Long locationId);

}
