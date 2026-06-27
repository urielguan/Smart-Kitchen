package com.xykj.wms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.wms.dto.MaterialFoodMappingCreateDTO;
import com.xykj.wms.dto.MaterialFoodMappingQueryDTO;
import com.xykj.wms.entity.Material;
import com.xykj.wms.entity.MaterialFoodMapping;
import com.xykj.wms.mapper.MaterialFoodMappingMapper;
import com.xykj.wms.mapper.MaterialMapper;
import com.xykj.wms.service.MaterialFoodMappingService;
import com.xykj.wms.service.MaterialService;
import com.xykj.wms.vo.MaterialFoodMappingVO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MaterialFoodMappingServiceImpl implements MaterialFoodMappingService {

    private final MaterialFoodMappingMapper mappingMapper;
    private final MaterialMapper materialMapper;
    private final MaterialService materialService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<MaterialFoodMappingVO> list(MaterialFoodMappingQueryDTO query) {
        Page<MaterialFoodMapping> page = mappingMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<MaterialFoodMapping>()
                        .eq(MaterialFoodMapping::getDeleted, 0)
                        .eq(query.getMaterialId() != null, MaterialFoodMapping::getMaterialId, query.getMaterialId())
                        .eq(query.getMatchStatus() != null && !query.getMatchStatus().isBlank(), MaterialFoodMapping::getMatchStatus, query.getMatchStatus())
                        .orderByDesc(MaterialFoodMapping::getUpdatedAt)
        );
        List<MaterialFoodMappingVO> list = page.getRecords().stream().map(this::toVO).filter(vo ->
                query.getMaterialName() == null || query.getMaterialName().isBlank() || (vo.getMaterialName() != null && vo.getMaterialName().contains(query.getMaterialName()))
        ).toList();
        return PageResult.of(list, page.getCurrent(), page.getSize(), page.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MaterialFoodMappingVO create(MaterialFoodMappingCreateDTO dto) {
        Material material = materialMapper.selectById(dto.getMaterialId());
        if (material == null || Integer.valueOf(1).equals(material.getDeleted())) {
            throw BizException.notFound("物料不存在");
        }
        Map<String, Object> foodRow = getFoodItemRow(dto.getFoodItemId());
        if (foodRow == null) {
            throw BizException.notFound("标准食品不存在");
        }

        MaterialFoodMapping mapping = mappingMapper.selectOne(new LambdaQueryWrapper<MaterialFoodMapping>()
                .eq(MaterialFoodMapping::getMaterialId, dto.getMaterialId())
                .eq(MaterialFoodMapping::getDeleted, 0));
        if (mapping == null) {
            mapping = new MaterialFoodMapping();
            mapping.setMaterialId(dto.getMaterialId());
            mapping.setCreatedAt(LocalDateTime.now());
        }
        mapping.setFoodItemId(dto.getFoodItemId());
        mapping.setMatchStatus(dto.getMatchStatus() == null || dto.getMatchStatus().isBlank() ? "confirmed" : dto.getMatchStatus());
        mapping.setRemark(dto.getRemark());
        mapping.setConfirmedBy(UserContext.getUserId());
        mapping.setConfirmedAt(LocalDateTime.now());
        mapping.setUpdatedAt(LocalDateTime.now());
        if (mapping.getId() == null) {
            mappingMapper.insert(mapping);
        } else {
            mappingMapper.updateById(mapping);
        }

        materialService.syncNutrition(dto.getMaterialId());
        return toVO(mappingMapper.selectById(mapping.getId()));
    }

    private MaterialFoodMappingVO toVO(MaterialFoodMapping mapping) {
        MaterialFoodMappingVO vo = new MaterialFoodMappingVO();
        vo.setId(mapping.getId());
        vo.setMaterialId(mapping.getMaterialId());
        vo.setFoodItemId(mapping.getFoodItemId());
        vo.setMatchStatus(mapping.getMatchStatus());
        vo.setRemark(mapping.getRemark());
        vo.setConfirmedAt(mapping.getConfirmedAt());

        Material material = materialMapper.selectById(mapping.getMaterialId());
        if (material != null) {
            vo.setMaterialCode(material.getMaterialCode());
            vo.setMaterialName(material.getMaterialName());
        }

        Map<String, Object> foodRow = getFoodItemRow(mapping.getFoodItemId());
        if (foodRow != null) {
            vo.setFoodCode((String) foodRow.get("food_code"));
            vo.setFoodName((String) foodRow.get("food_name"));
        }
        return vo;
    }

    private Map<String, Object> getFoodItemRow(Long foodItemId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, food_code, food_name FROM food_item WHERE id = ? AND deleted = 0",
                foodItemId
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }
}
