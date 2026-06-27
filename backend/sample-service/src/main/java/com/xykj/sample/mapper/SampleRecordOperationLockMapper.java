package com.xykj.sample.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sample.entity.SampleRecordOperationLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 留样记录操作锁 Mapper
 */
@Mapper
public interface SampleRecordOperationLockMapper extends BaseMapper<SampleRecordOperationLock> {

    @Select("""
            SELECT *
            FROM sample_record_operation_lock
            WHERE sample_record_id = #{sampleRecordId}
              AND deleted = 0
            LIMIT 1
            FOR UPDATE
            """)
    SampleRecordOperationLock selectByRecordIdForUpdate(@Param("sampleRecordId") Long sampleRecordId);
}
