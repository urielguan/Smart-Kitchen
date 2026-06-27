package com.xykj.sys.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.sys.entity.SysDictItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统字典 Mapper
 */
@Mapper
public interface SysDictItemMapper extends BaseMapper<SysDictItem> {

    @Delete("DELETE FROM sys_dict WHERE id = #{id} AND deleted = 0")
    int physicalDeleteById(Long id);
}
