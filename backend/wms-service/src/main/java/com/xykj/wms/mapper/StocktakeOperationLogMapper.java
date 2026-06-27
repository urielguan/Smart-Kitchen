package com.xykj.wms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xykj.wms.entity.StocktakeOperationLog;
import com.xykj.wms.vo.StocktakeOperationLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StocktakeOperationLogMapper extends BaseMapper<StocktakeOperationLog> {

    List<StocktakeOperationLogVO> selectByStocktakeId(@Param("stocktakeId") Long stocktakeId,
                                                      @Param("orgId") Long orgId,
                                                      @Param("tenantId") Long tenantId);
}
