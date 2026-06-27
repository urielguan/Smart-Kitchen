package com.xykj.cook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.cook.entity.CookTemperatureRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 烹饪温度记录Mapper
 */
@Mapper
public interface CookTemperatureRecordMapper extends BaseMapper<CookTemperatureRecord> {

    /**
     * 根据任务ID查询温度记录
     */
    @Select("SELECT * FROM cook_temperature_record WHERE task_id = #{taskId} ORDER BY record_time")
    List<CookTemperatureRecord> selectByTaskId(@Param("taskId") Long taskId);

    /**
     * 根据任务ID查询温度记录，仅返回 ID 大于 sinceId 的增量记录
     */
    @Select("SELECT * FROM cook_temperature_record WHERE task_id = #{taskId} AND id > #{sinceId} ORDER BY record_time")
    List<CookTemperatureRecord> selectByTaskIdSince(@Param("taskId") Long taskId, @Param("sinceId") Long sinceId);
}
