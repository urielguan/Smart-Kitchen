package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.wms.dto.StocktakeOrderQueryDTO;
import com.xykj.wms.entity.StocktakeOrder;
import com.xykj.wms.vo.StocktakeOrderDetailVO;
import com.xykj.wms.vo.StocktakeOrderListVO;
import com.xykj.wms.vo.StocktakeStatisticsVO;
import com.xykj.wms.vo.StocktakeVersionDetailVO;
import com.xykj.wms.vo.StocktakeVersionSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface StocktakeOrderMapper extends BaseMapper<StocktakeOrder> {

    Page<StocktakeOrderListVO> selectStocktakePage(Page<StocktakeOrderListVO> page,
                                                   @Param("q") StocktakeOrderQueryDTO query);

    StocktakeOrderDetailVO selectStocktakeDetail(@Param("id") Long id,
                                                 @Param("orgId") Long orgId,
                                                 @Param("tenantId") Long tenantId);

    List<StocktakeVersionSummaryVO> selectVersionSummaryList(@Param("stocktakeId") Long stocktakeId,
                                                             @Param("orgId") Long orgId,
                                                             @Param("tenantId") Long tenantId);

    StocktakeVersionDetailVO selectVersionDetail(@Param("stocktakeId") Long stocktakeId,
                                                 @Param("versionNo") Integer versionNo,
                                                 @Param("orgId") Long orgId,
                                                 @Param("tenantId") Long tenantId);

    StocktakeStatisticsVO selectStatistics(@Param("monthStart") LocalDate monthStart,
                                           @Param("nextMonthStart") LocalDate nextMonthStart,
                                           @Param("q") StocktakeOrderQueryDTO query);

    Long countActiveRange(@Param("warehouseId") Long warehouseId,
                          @Param("locationId") Long locationId,
                          @Param("excludeId") Long excludeId,
                          @Param("orgId") Long orgId,
                          @Param("tenantId") Long tenantId);

    boolean existsWarehouseStocktakeOccupancy(@Param("warehouseId") Long warehouseId, @Param("statuses") List<String> statuses);

    boolean existsWarehouseStocktakeHistory(@Param("warehouseId") Long warehouseId);

    boolean existsLocationStocktakeHeaderOccupancy(@Param("locationId") Long locationId, @Param("statuses") List<String> statuses);

    boolean existsLocationStocktakeHeaderHistory(@Param("locationId") Long locationId);

}
