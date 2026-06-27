package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.StocktakeOrderItem;
import com.xykj.wms.vo.StocktakeOrderItemVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StocktakeOrderItemMapper extends BaseMapper<StocktakeOrderItem> {

    List<StocktakeOrderItemVO> selectByStocktakeId(@Param("stocktakeId") Long stocktakeId,
                                                   @Param("orgId") Long orgId,
                                                   @Param("tenantId") Long tenantId);

    List<StocktakeOrderItemVO> selectVersionItems(@Param("stocktakeId") Long stocktakeId,
                                                  @Param("versionNo") Integer versionNo,
                                                  @Param("orgId") Long orgId,
                                                  @Param("tenantId") Long tenantId);

    void deleteByStocktakeId(@Param("stocktakeId") Long stocktakeId);

    boolean existsWarehouseStocktakeItemOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseStocktakeItemHistory(@Param("warehouseId") Long warehouseId);

    boolean existsLocationStocktakeItemOccupancy(@Param("locationId") Long locationId, @Param("statuses") List<String> statuses);

    boolean existsLocationStocktakeItemHistory(@Param("locationId") Long locationId);

}
