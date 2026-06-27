package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.LocationAreaLedger;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LocationAreaLedgerMapper extends BaseMapper<LocationAreaLedger> {

    LocationAreaLedger selectLatestByBizItemForUpdate(
            @Param("bizType") String bizType,
            @Param("bizAction") String bizAction,
            @Param("bizOrderId") Long bizOrderId,
            @Param("bizItemId") Long bizItemId
    );

    List<LocationAreaLedger> selectActiveByLocationAndMaterial(
            @Param("locationId") Long locationId,
            @Param("materialId") Long materialId
    );

    int markReversed(@Param("ledgerId") Long ledgerId, @Param("reversedLedgerId") Long reversedLedgerId);

    int deleteActiveByLocationAndMaterial(
            @Param("locationId") Long locationId,
            @Param("materialId") Long materialId
    );

    int deleteByBizActionAndLocations(@Param("bizAction") String bizAction,
                                      @Param("locationIds") List<Long> locationIds);
}
