package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.wms.dto.LocationQueryDTO;
import com.xykj.wms.entity.Location;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.vo.LocationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface LocationMapper extends BaseMapper<Location> {

    /**
     * 分页查询仓位列表
     */
    IPage<LocationVO> selectLocationPage(Page<LocationVO> page, @Param("q") LocationQueryDTO query);

    List<LocationVO> selectLocationExportList(@Param("q") LocationQueryDTO query);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_location
                WHERE warehouse_id = #{warehouseId}
                  AND deleted = 0
            )
            """)
    boolean existsActiveLocationInWarehouse(@Param("warehouseId") Long warehouseId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_location
                WHERE warehouse_id = #{warehouseId}
                  AND status = 'occupied'
                  AND deleted = 0
            )
            """)
    boolean existsOccupiedLocationInWarehouse(@Param("warehouseId") Long warehouseId);

    @Select("""
            SELECT *
            FROM wms_warehouse
            WHERE warehouse_code = #{warehouseCode}
              AND org_id = #{orgId}
              AND deleted = 0
            LIMIT 1
            """)
    Warehouse selectWarehouseByCode(@Param("orgId") Long orgId, @Param("warehouseCode") String warehouseCode);

    @Select("""
            SELECT COALESCE(SUM(quantity), 0)
            FROM wms_inventory
            WHERE location_id = #{locationId}
            """)
    BigDecimal selectLocationInventoryQuantity(@Param("locationId") Long locationId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_location
                WHERE warehouse_id = #{warehouseId}
                  AND location_code = #{locationCode}
                  AND deleted = 0
            )
            """)
    boolean existsLocationCodeInWarehouse(@Param("warehouseId") Long warehouseId, @Param("locationCode") String locationCode);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_location
                WHERE warehouse_id = #{warehouseId}
                  AND region_code = #{regionCode}
                  AND shelf_code = #{shelfCode}
                  AND slot_code = #{slotCode}
                  AND deleted = 0
            )
            """)
    boolean existsHierarchyInWarehouse(
            @Param("warehouseId") Long warehouseId,
            @Param("regionCode") String regionCode,
            @Param("shelfCode") String shelfCode,
            @Param("slotCode") String slotCode
    );

    @Select("<script>" +
            "SELECT * FROM wms_location WHERE deleted = 0 AND id IN " +
            "<foreach collection='locationIds' item='locationId' open='(' separator=',' close=')'>" +
            "#{locationId}" +
            "</foreach>" +
            "</script>")
    List<Location> selectAreaValidationLocations(@Param("locationIds") List<Long> locationIds);

    Location selectByIdForUpdate(@Param("locationId") Long locationId);

    int updateWithVersion(@Param("location") Location location, @Param("version") Long version);

    @Update("""
            UPDATE wms_location
            SET used_capacity = COALESCE(used_capacity, 0) + #{deltaArea},
                updated_at = NOW()
            WHERE id = #{locationId}
              AND deleted = 0
            """)
    int incrementUsedCapacity(@Param("locationId") Long locationId, @Param("deltaArea") BigDecimal deltaArea);

    int replaceUsedCapacity(@Param("locationId") Long locationId, @Param("usedCapacity") BigDecimal usedCapacity);

}

