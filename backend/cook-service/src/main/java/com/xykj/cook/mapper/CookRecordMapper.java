package com.xykj.cook.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.cook.entity.CookRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 烹饪记录Mapper
 */
@Mapper
public interface CookRecordMapper extends BaseMapper<CookRecord> {

    /**
     * 根据任务ID查询操作记录
     */
    @Select("SELECT * FROM cook_record WHERE task_id = #{taskId} ORDER BY record_time")
    List<CookRecord> selectByTaskId(@Param("taskId") Long taskId);
}
