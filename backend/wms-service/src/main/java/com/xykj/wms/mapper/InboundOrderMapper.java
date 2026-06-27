package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.wms.dto.InboundOrderQueryDTO;
import com.xykj.wms.entity.InboundOrder;
import com.xykj.wms.vo.InboundOrderStatisticsVO;
import com.xykj.wms.vo.InboundOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InboundOrderMapper extends BaseMapper<InboundOrder> {

    Page<InboundOrderVO> selectInboundPage(Page<InboundOrderVO> page,
                                            @Param("q") InboundOrderQueryDTO query);

    InboundOrderVO selectInboundDetail(@Param("id") Long id);

    InboundOrderStatisticsVO selectStatistics(@Param("q") InboundOrderQueryDTO query);

    boolean existsWarehouseInboundOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseInboundHistory(@Param("warehouseId") Long warehouseId);

    Long selectVersionById(@Param("id") Long id);
}
