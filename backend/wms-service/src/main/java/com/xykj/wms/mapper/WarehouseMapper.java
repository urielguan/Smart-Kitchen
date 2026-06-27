package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.wms.dto.WarehouseQueryDTO;
import com.xykj.wms.entity.Warehouse;
import com.xykj.wms.vo.WarehouseStatisticsVO;
import com.xykj.wms.vo.WarehouseVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {

    /**
     * 分页查询仓库列表（含仓位聚合统计）
     */
    IPage<WarehouseVO> selectWarehousePage(Page<WarehouseVO> page, @Param("q") WarehouseQueryDTO query);

    /**
     * 查询导出列表（沿用列表筛选和数据权限）
     */
    List<WarehouseVO> selectWarehouseExportList(@Param("q") WarehouseQueryDTO query);

    /**
     * 按版本执行更新，命中 0 行视为并发冲突
     */
    int updateWithVersion(@Param("entity") Warehouse entity, @Param("version") Long version);

    /**
     * 统计卡片数据
     */
    @Select("SELECT " +
            "COUNT(*) AS warehouseTotal, " +
            "SUM(CASE WHEN status='active' THEN 1 ELSE 0 END) AS activeCount, " +
            "SUM(CASE WHEN status='maintenance' THEN 1 ELSE 0 END) AS maintenanceCount, " +
            "(SELECT COUNT(*) FROM wms_location WHERE deleted=0) AS positionTotal " +
            "FROM wms_warehouse WHERE deleted=0")
    WarehouseStatisticsVO selectStatistics();
}
