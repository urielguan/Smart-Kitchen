package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.SensitiveWord;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 敏感词Mapper
 */
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {

    @Select("SELECT word FROM sys_sensitive_word WHERE status = 'active' AND deleted = 0")
    List<String> selectAllActiveWords();
}
