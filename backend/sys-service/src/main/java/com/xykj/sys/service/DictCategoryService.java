package com.xykj.sys.service;

import com.xykj.common.result.PageResult;
import com.xykj.sys.dto.DictCategoryAreaSuggestionDTO;
import com.xykj.sys.dto.DictCategoryAreaCoefficientRecalcDTO;
import com.xykj.sys.dto.DictCategoryCreateDTO;
import com.xykj.sys.dto.DictCategoryQueryDTO;
import com.xykj.sys.dto.DictCategoryStatusDTO;
import com.xykj.sys.dto.DictCategoryUpdateDTO;
import com.xykj.sys.vo.DictCategoryAreaSuggestionVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientHistoryVO;
import com.xykj.sys.vo.DictCategoryAreaCoefficientRecalcTaskVO;
import com.xykj.sys.vo.DictCategoryDetailVO;
import com.xykj.sys.vo.DictCategoryItemVO;
import com.xykj.sys.vo.DictCategoryMetaVO;
import com.xykj.sys.vo.DictCategoryOptionVO;

import java.util.List;
import java.util.Map;

/**
 * 字典分类维护服务
 */
public interface DictCategoryService {

    List<DictCategoryMetaVO> listCategories();

    PageResult<DictCategoryItemVO> list(DictCategoryQueryDTO queryDTO);

    DictCategoryDetailVO getDetail(String categoryType, Long id);

    Map<String, Object> create(DictCategoryCreateDTO dto);

    Map<String, Object> update(String categoryType, Long id, DictCategoryUpdateDTO dto);

    Map<String, Object> updateStatus(String categoryType, Long id, DictCategoryStatusDTO dto);

    void delete(String categoryType, Long id);

    List<DictCategoryOptionVO> getOptions(String categoryType, boolean includeInactive);

    DictCategoryAreaSuggestionVO getAreaCoefficientSuggestion(DictCategoryAreaSuggestionDTO dto);

    List<DictCategoryAreaCoefficientHistoryVO> getAreaCoefficientHistory(String categoryType, Long id);

    Map<String, Object> startAreaCoefficientRecalc(String categoryType, Long id, DictCategoryAreaCoefficientRecalcDTO dto);

    DictCategoryAreaCoefficientRecalcTaskVO getAreaCoefficientRecalcTaskDetail(String categoryType, Long id, Long taskId);
}
