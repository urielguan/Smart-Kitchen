package com.xykj.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sample.entity.SampleRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 留样记录Mapper
 */
@Mapper
public interface SampleRecordMapper extends BaseMapper<SampleRecord> {

    @Select("""
            SELECT *
            FROM sample_record
            WHERE id = #{id}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    SampleRecord selectByIdForUpdate(@Param("id") Long id);

    /**
     * 获取指定日期的下一个序号（用于生成sample_no）
     */
    @Select("SELECT COUNT(*) + 1 FROM sample_record WHERE sample_date = #{date} AND deleted = 0")
    int nextSeqForDate(@Param("date") LocalDate date);
}
