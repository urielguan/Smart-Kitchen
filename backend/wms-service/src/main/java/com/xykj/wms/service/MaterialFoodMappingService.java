package com.xykj.wms.service;

import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.MaterialFoodMappingCreateDTO;
import com.xykj.wms.dto.MaterialFoodMappingQueryDTO;
import com.xykj.wms.vo.MaterialFoodMappingVO;

public interface MaterialFoodMappingService {
    PageResult<MaterialFoodMappingVO> list(MaterialFoodMappingQueryDTO query);
    MaterialFoodMappingVO create(MaterialFoodMappingCreateDTO dto);
}
