package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.Inventory;
import com.xykj.wms.vo.OutboundSuggestionStockCandidateVO;
import com.xykj.wms.vo.StocktakeSnapshotPreviewVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存明细 Mapper 接口
 */
@Mapper
public interface InventoryMapper extends BaseMapper<Inventory> {

    /**
     * 查询可用批次号列表
     * 根据物料ID、规格、仓库ID查询有库存的批次号
     *
     * @param materialId 物料ID
     * @param spec 规格（可选）
     * @param warehouseId 仓库ID
     * @return 批次号选项列表
     */
    @Select("<script>" +
            "SELECT batch_no, SUM(quantity) as quantity, AVG(unit_cost) as unit_cost, MIN(expiry_date) as expiry_date " +
            "FROM wms_inventory " +
            "WHERE material_id = #{materialId} " +
            "AND warehouse_id = #{warehouseId} " +
            "AND quantity > 0 " +
            "AND batch_no IS NOT NULL AND batch_no != '' " +
            "<if test='locationId != null'>" +
            "AND location_id = #{locationId} " +
            "</if>" +
            "<if test='spec != null and spec != \"\"'>" +
            "AND spec = #{spec} " +
            "</if>" +
            "GROUP BY batch_no " +
            "ORDER BY expiry_date ASC" +
            "</script>")
    List<Map<String, Object>> selectAvailableBatchNos(
            @Param("materialId") Long materialId,
            @Param("spec") String spec,
            @Param("warehouseId") Long warehouseId,
            @Param("locationId") Long locationId);

    @Select("<script>" +
            "SELECT i.id AS inventoryId, i.warehouse_id AS warehouseId, w.warehouse_name AS warehouseName, " +
            "i.location_id AS locationId, l.location_name AS locationName, i.material_id AS materialId, " +
            "i.material_name AS materialName, i.spec AS spec, i.unit AS unit, i.batch_no AS batchNo, " +
            "i.production_date AS productionDate, i.expiry_date AS expiryDate, i.quantity AS quantity, " +
            "i.unit_cost AS unitCost, i.status AS inventoryStatus, i.source_id AS sourceId, " +
            "i.org_id AS orgId, i.tenant_id AS tenantId, i.created_at AS inboundTime " +
            "FROM wms_inventory i " +
            "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
            "LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 " +
            "WHERE i.quantity > 0 " +
            "AND i.material_id = #{materialId} " +
            "AND COALESCE(i.spec, '') = COALESCE(#{spec}, '') " +
            "AND i.org_id = #{orgId} " +
            "AND i.tenant_id = #{tenantId} " +
            "<if test='warehouseId != null'>AND i.warehouse_id = #{warehouseId} </if>" +
            "<if test='locationId != null'>AND i.location_id = #{locationId} </if>" +
            "ORDER BY i.id ASC" +
            "</script>")
    List<OutboundSuggestionStockCandidateVO> selectOutboundSuggestionCandidates(@Param("materialId") Long materialId,
                                                                                @Param("spec") String spec,
                                                                                @Param("warehouseId") Long warehouseId,
                                                                                @Param("locationId") Long locationId,
                                                                                @Param("orgId") Long orgId,
                                                                                @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT i.id AS inventory_id, i.warehouse_id, w.warehouse_name, i.location_id, l.location_name, " +
            "i.material_id, i.material_name, i.spec, i.unit, i.batch_no, i.production_date, i.expiry_date, " +
            "i.quantity, i.unit_cost, i.total_cost, i.status AS inventory_status, i.org_id, i.tenant_id, i.updated_at " +
            "FROM wms_inventory i " +
            "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
            "LEFT JOIN wms_location l ON l.id = i.location_id " +
            "WHERE 1 = 1 " +
            "<choose>" +
            "<when test='warehouseIds != null and warehouseIds.size() > 0'>" +
            "AND i.warehouse_id IN " +
            "<foreach collection='warehouseIds' item='warehouseId' open='(' separator=',' close=')'>" +
            "#{warehouseId}" +
            "</foreach> " +
            "</when>" +
            "<otherwise>" +
            "AND i.warehouse_id = #{warehouseId} " +
            "</otherwise>" +
            "</choose>" +
            "<choose>" +
            "<when test='locationIds != null and locationIds.size() > 0'>" +
            "AND i.location_id IN " +
            "<foreach collection='locationIds' item='locationId' open='(' separator=',' close=')'>" +
            "#{locationId}" +
            "</foreach> " +
            "</when>" +
            "<when test='locationId != null'>AND i.location_id = #{locationId} </when>" +
            "</choose>" +
            "<if test='orgIds != null and orgIds.size() > 0'>AND i.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>" +
            "#{orgId}" +
            "</foreach> </if>" +
            "<if test='orgIds != null and orgIds.size() == 0'>AND 1 = 0 </if>" +
            "<if test='tenantId != null'>AND i.tenant_id = #{tenantId} </if>" +
            "ORDER BY i.warehouse_id ASC, i.location_id ASC, i.material_name ASC, i.spec ASC, i.batch_no ASC, i.id ASC" +
            "</script>")
    List<StocktakeSnapshotPreviewVO> selectStocktakeSnapshotCandidates(@Param("warehouseId") Long warehouseId,
                                                                       @Param("locationId") Long locationId,
                                                                       @Param("warehouseIds") List<Long> warehouseIds,
                                                                       @Param("locationIds") List<Long> locationIds,
                                                                       @Param("orgIds") List<Long> orgIds,
                                                                       @Param("tenantId") Long tenantId);

    @Select("SELECT id, warehouse_id, location_id, batch_no, quantity, updated_at " +
            "FROM wms_inventory " +
            "WHERE id = #{inventoryId}")
    Map<String, Object> selectStocktakeInventoryCheckRow(@Param("inventoryId") Long inventoryId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_inventory
                WHERE warehouse_id = #{warehouseId}
                  AND quantity IS NOT NULL
                  AND quantity <> 0
            )
            """)
    boolean existsWarehouseNonZeroInventory(@Param("warehouseId") Long warehouseId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_inventory
                WHERE location_id = #{locationId}
                  AND quantity IS NOT NULL
                  AND quantity <> 0
            )
            """)
    boolean existsLocationNonZeroInventory(@Param("locationId") Long locationId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_inventory
                WHERE warehouse_id = #{warehouseId}
            )
            """)
    boolean existsWarehouseInventoryHistory(@Param("warehouseId") Long warehouseId);

    @Select("""
            SELECT EXISTS (
                SELECT 1
                FROM wms_inventory
                WHERE location_id = #{locationId}
            )
            """)
    boolean existsLocationInventoryHistory(@Param("locationId") Long locationId);

    @Select("<script>" +
            "SELECT warehouse_id AS warehouseId, location_id AS locationId, material_id AS materialId, " +
            "COALESCE(SUM(quantity), 0) AS quantity, MAX(org_id) AS orgId, MAX(tenant_id) AS tenantId " +
            "FROM wms_inventory WHERE deleted = 0 AND quantity IS NOT NULL AND quantity &lt;&gt; 0 " +
            "<if test='warehouseIds != null and warehouseIds.size() > 0'>AND warehouse_id IN " +
            "<foreach collection='warehouseIds' item='warehouseId' open='(' separator=',' close=')'>" +
            "#{warehouseId}" +
            "</foreach> </if>" +
            "<if test='locationIds != null and locationIds.size() > 0'>AND location_id IN " +
            "<foreach collection='locationIds' item='locationId' open='(' separator=',' close=')'>" +
            "#{locationId}" +
            "</foreach> </if>" +
            "<if test='orgId != null'>AND org_id = #{orgId} </if>" +
            "<if test='tenantId != null'>AND tenant_id = #{tenantId} </if>" +
            "GROUP BY warehouse_id, location_id, material_id ORDER BY warehouse_id ASC, location_id ASC, material_id ASC" +
            "</script>")
    List<Map<String, Object>> selectAreaBaselineSeeds(@Param("warehouseIds") List<Long> warehouseIds,
                                                      @Param("locationIds") List<Long> locationIds,
                                                      @Param("orgId") Long orgId,
                                                      @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT m.id AS materialId, m.material_code AS materialCode, m.material_name AS materialName, " +
            "m.material_category AS categoryName, m.spec AS materialSpec, m.unit AS unit, m.image_url AS imageUrl, " +
            "m.status AS materialStatus, " +
            "m.min_stock AS minStock, m.max_stock AS maxStock, m.shelf_life_days AS shelfLifeDays, " +
            "COALESCE(SUM(CASE WHEN i.quantity IS NOT NULL THEN i.quantity ELSE 0 END), 0) AS currentStock, " +
            "COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.warehouse_id END) AS warehouseCount, " +
            "COUNT(DISTINCT CASE WHEN i.quantity > 0 THEN i.location_id END) AS locationCount, " +
            "MIN(CASE WHEN i.quantity > 0 AND i.expiry_date IS NOT NULL THEN DATEDIFF(i.expiry_date, CURDATE()) END) AS minRemainingDays, " +
            "SUM(CASE WHEN i.quantity > 0 AND i.expiry_date IS NOT NULL AND DATEDIFF(i.expiry_date, CURDATE()) &lt;= 0 THEN i.quantity ELSE 0 END) AS expiredQty, " +
            "MAX(CASE WHEN i.quantity > 0 THEN i.batch_no END) AS latestBatchNo, " +
            "MAX(CASE WHEN i.quantity > 0 THEN i.production_date END) AS latestProductionDate, " +
            "MAX(COALESCE(i.updated_at, m.updated_at)) AS updatedAt " +
            "FROM wms_material m " +
            "LEFT JOIN wms_inventory i ON i.material_id = m.id " +
            "WHERE m.deleted = 0 " +
            "<if test='materialStatus != null and materialStatus != \"\"'>AND m.status = #{materialStatus} </if>" +
            "<if test='materialStatus == null or materialStatus == \"\"'>AND m.status = 'active' </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.material_name LIKE CONCAT('%', #{keyword}, '%') OR m.material_code LIKE CONCAT('%', #{keyword}, '%') OR m.spec LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='categoryName != null and categoryName != \"\"'>AND m.material_category = #{categoryName} </if>" +
            "<if test='warehouseId != null'>AND i.warehouse_id = #{warehouseId} </if>" +
            "<if test='locationId != null'>AND i.location_id = #{locationId} </if>" +
            "<if test='orgIds != null and orgIds.size() > 0'>AND (i.org_id IS NULL OR i.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach>) </if>" +
            "<if test='orgIds != null and orgIds.size() == 0'>AND 1 = 0 </if>" +
            "<if test='tenantId != null'>AND (i.tenant_id IS NULL OR i.tenant_id = #{tenantId}) </if>" +
            "GROUP BY m.id, m.material_code, m.material_name, m.material_category, m.spec, m.unit, m.image_url, m.min_stock, m.max_stock, m.shelf_life_days " +
            "ORDER BY updatedAt DESC, m.id DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<Map<String, Object>> selectOverviewRows(@Param("keyword") String keyword,
                                                 @Param("categoryName") String categoryName,
                                                 @Param("materialStatus") String materialStatus,
                                                 @Param("warehouseId") Long warehouseId,
                                                 @Param("locationId") Long locationId,
                                                 @Param("orgIds") List<Long> orgIds,
                                                 @Param("tenantId") Long tenantId,
                                                 @Param("offset") long offset,
                                                 @Param("pageSize") int pageSize);

    @Select("<script>" +
            "SELECT COUNT(1) FROM (" +
            "SELECT m.id FROM wms_material m " +
            "LEFT JOIN wms_inventory i ON i.material_id = m.id " +
            "WHERE m.deleted = 0 " +
            "<if test='materialStatus != null and materialStatus != \"\"'>AND m.status = #{materialStatus} </if>" +
            "<if test='materialStatus == null or materialStatus == \"\"'>AND m.status = 'active' </if>" +
            "<if test='keyword != null and keyword != \"\"'>AND (m.material_name LIKE CONCAT('%', #{keyword}, '%') OR m.material_code LIKE CONCAT('%', #{keyword}, '%') OR m.spec LIKE CONCAT('%', #{keyword}, '%')) </if>" +
            "<if test='categoryName != null and categoryName != \"\"'>AND m.material_category = #{categoryName} </if>" +
            "<if test='warehouseId != null'>AND i.warehouse_id = #{warehouseId} </if>" +
            "<if test='locationId != null'>AND i.location_id = #{locationId} </if>" +
            "<if test='orgIds != null and orgIds.size() > 0'>AND (i.org_id IS NULL OR i.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach>) </if>" +
            "<if test='orgIds != null and orgIds.size() == 0'>AND 1 = 0 </if>" +
            "<if test='tenantId != null'>AND (i.tenant_id IS NULL OR i.tenant_id = #{tenantId}) </if>" +
            "GROUP BY m.id) t" +
            "</script>")
    Long countOverviewRows(@Param("keyword") String keyword,
                           @Param("categoryName") String categoryName,
                           @Param("materialStatus") String materialStatus,
                           @Param("warehouseId") Long warehouseId,
                           @Param("locationId") Long locationId,
                           @Param("orgIds") List<Long> orgIds,
                           @Param("tenantId") Long tenantId);

    @Select("<script>" +
            "SELECT i.warehouse_id AS warehouseId, w.warehouse_name AS warehouseName, i.location_id AS locationId, l.location_name AS locationName, " +
            "i.batch_no AS batchNo, COALESCE(SUM(i.quantity), 0) AS quantity, i.production_date AS productionDate, i.expiry_date AS expiryDate " +
            "FROM wms_inventory i " +
            "LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 " +
            "LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 " +
            "WHERE i.material_id = #{materialId} AND i.quantity > 0 " +
            "<if test='orgIds != null and orgIds.size() > 0'>AND i.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach> </if>" +
            "<if test='orgIds != null and orgIds.size() == 0'>AND 1 = 0 </if>" +
            "<if test='tenantId != null'>AND i.tenant_id = #{tenantId} </if>" +
            "GROUP BY i.warehouse_id, w.warehouse_name, i.location_id, l.location_name, i.batch_no, i.production_date, i.expiry_date " +
            "ORDER BY w.warehouse_name ASC, l.location_name ASC, i.expiry_date ASC, i.batch_no ASC" +
            "</script>")
    List<Map<String, Object>> selectDistributionRows(@Param("materialId") Long materialId,
                                                     @Param("orgIds") List<Long> orgIds,
                                                     @Param("tenantId") Long tenantId);

    @Select("SELECT EXISTS (SELECT 1 FROM wms_material m WHERE m.id = #{materialId} AND m.deleted = 0 AND m.status = 'active')")
    boolean existsActiveMaterial(@Param("materialId") Long materialId);

    @Select("<script>" +
            "SELECT EXISTS (" +
            "SELECT 1 FROM wms_inventory i " +
            "WHERE i.material_id = #{materialId} AND i.quantity > 0 " +
            "<if test='orgIds != null and orgIds.size() > 0'>AND i.org_id IN " +
            "<foreach collection='orgIds' item='orgId' open='(' separator=',' close=')'>#{orgId}</foreach> </if>" +
            "<if test='orgIds != null and orgIds.size() == 0'>AND 1 = 0 </if>" +
            "<if test='tenantId != null'>AND i.tenant_id = #{tenantId} </if>" +
            ")" +
            "</script>")
    boolean existsMaterialVisibleInScope(@Param("materialId") Long materialId,
                                         @Param("orgIds") List<Long> orgIds,
                                         @Param("tenantId") Long tenantId);

    @Select("SELECT m.id AS materialId, m.near_expiry_days AS nearExpiryDays, m.warning_days AS warningDays, m.shelf_life_days AS shelfLifeDays " +
            "FROM wms_material m WHERE m.id = #{materialId} AND m.deleted = 0 LIMIT 1")
    Map<String, Object> selectMaterialShelfLifeConfig(@Param("materialId") Long materialId);


}
