package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.InboundOrderItem;
import com.xykj.wms.vo.InboundOrderItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InboundOrderItemMapper extends BaseMapper<InboundOrderItem> {

    List<InboundOrderItemVO> selectItemsByInboundId(@Param("inboundId") Long inboundId);

    List<InboundOrderItem> selectEntityItemsByInboundId(@Param("inboundId") Long inboundId);

    boolean existsWarehouseInboundItemOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseInboundItemHistory(@Param("warehouseId") Long warehouseId);

    boolean existsLocationInboundOccupancy(@Param("locationId") Long locationId, @Param("statuses") List<String> statuses);

    boolean existsLocationInboundHistory(@Param("locationId") Long locationId);

}
