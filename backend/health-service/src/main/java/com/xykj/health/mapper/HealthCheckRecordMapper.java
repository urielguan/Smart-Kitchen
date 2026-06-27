package com.xykj.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.health.entity.HealthCheckRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 晨检记录Mapper
 */
@Mapper
public interface HealthCheckRecordMapper extends BaseMapper<HealthCheckRecord> {

    /**
     * 获取指定日期的下一个序号（用于生成check_no）
     */
    @Select("SELECT COUNT(*) + 1 FROM health_check_record WHERE check_date = #{date}")
    int nextSeqForDate(@Param("date") LocalDate date);
}
