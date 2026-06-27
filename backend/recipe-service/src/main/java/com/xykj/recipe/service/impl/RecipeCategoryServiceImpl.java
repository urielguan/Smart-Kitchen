package com.xykj.recipe.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xykj.common.context.UserContext;
import com.xykj.recipe.dto.RecipeCategoryCreateDTO;
import com.xykj.recipe.dto.RecipeCategoryQueryDTO;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeCategory;
import com.xykj.recipe.mapper.RecipeCategoryMapper;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.service.RecipeCategoryService;
import com.xykj.recipe.vo.RecipeCategoryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜谱类别服务实现
 */
@Service
@RequiredArgsConstructor
public class RecipeCategoryServiceImpl extends ServiceImpl<RecipeCategoryMapper, RecipeCategory> implements RecipeCategoryService {

    private static final Set<String> BUILTIN_CATEGORY_CODES = Set.of("STAPLE", "MAIN_DISH", "SOUP", "SIDE_DISH", "DESSERT");

    private final RecipeMapper recipeMapper;

    @Override
    public Page<RecipeCategoryVO> list(RecipeCategoryQueryDTO query) {
        Page<RecipeCategory> pageDto = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<RecipeCategory> wrapper = buildVisibleCategoryWrapper()
                .like(StrUtil.isNotBlank(query.getCategoryName()), RecipeCategory::getCategoryName, query.getCategoryName())
                .eq(StrUtil.isNotBlank(query.getStatus()), RecipeCategory::getStatus, query.getStatus())
                .orderByAsc(RecipeCategory::getSortOrder)
                .orderByDesc(RecipeCategory::getCreatedAt);

        Page<RecipeCategory> page = page(pageDto, wrapper);

        List<RecipeCategoryVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Page<RecipeCategoryVO> resultPage = new Page<>(query.getPageNum(), query.getPageSize());
        resultPage.setRecords(voList);
        resultPage.setTotal(page.getTotal());
        return resultPage;
    }

    @Override
    public List<RecipeCategoryVO> listActive() {
        List<RecipeCategory> categories = list(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getStatus, "active")
                        .orderByAsc(RecipeCategory::getSortOrder)
        );
        return categories.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public RecipeCategoryVO getDetail(Long id) {
        return convertToVO(getVisibleCategoryById(id));
    }

    @Override
    public Long create(RecipeCategoryCreateDTO dto) {
        Long count = baseMapper.selectCount(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getCategoryCode, dto.getCategoryCode())
        );
        if (count > 0) {
            throw new RuntimeException("类别编码已存在");
        }

        Long nameCount = baseMapper.selectCount(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getCategoryName, dto.getCategoryName())
        );
        if (nameCount > 0) {
            throw new RuntimeException("类别名称已存在");
        }

        RecipeCategory category = new RecipeCategory();
        BeanUtils.copyProperties(dto, category);
        category.setStatus("active");
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        category.setOrgId(0L);
        category.setTenantId(getCurrentTenantId());

        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        save(category);
        return category.getId();
    }

    @Override
    public void update(Long id, RecipeCategoryCreateDTO dto) {
        RecipeCategory category = getVisibleCategoryById(id);
        ensureCustomEditable(category);

        Long count = baseMapper.selectCount(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getCategoryCode, dto.getCategoryCode())
                        .ne(RecipeCategory::getId, id)
        );
        if (count > 0) {
            throw new RuntimeException("类别编码已存在");
        }

        Long nameCount = baseMapper.selectCount(
                buildVisibleCategoryWrapper()
                        .eq(RecipeCategory::getCategoryName, dto.getCategoryName())
                        .ne(RecipeCategory::getId, id)
        );
        if (nameCount > 0) {
            throw new RuntimeException("类别名称已存在");
        }

        BeanUtils.copyProperties(dto, category);
        category.setOrgId(0L);
        category.setTenantId(getCurrentTenantId());
        category.setUpdatedAt(LocalDateTime.now());
        updateById(category);
    }

    @Override
    public void delete(Long id) {
        RecipeCategory category = getVisibleCategoryById(id);
        ensureCustomEditable(category);

        Long recipeCount = recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getCategoryId, id)
                        .eq(Recipe::getTenantId, getCurrentTenantId())
                        .eq(Recipe::getDeleted, 0)
        );
        if (recipeCount > 0) {
            throw new RuntimeException("该类别下存在菜谱，无法删除");
        }

        removeById(id);
    }

    @Override
    public void toggleStatus(Long id) {
        RecipeCategory category = getVisibleCategoryById(id);
        ensureCustomEditable(category);

        category.setStatus("active".equals(category.getStatus()) ? "inactive" : "active");
        category.setUpdatedAt(LocalDateTime.now());
        updateById(category);
    }

    /**
     * 转换为VO
     */
    private RecipeCategoryVO convertToVO(RecipeCategory category) {
        RecipeCategoryVO vo = new RecipeCategoryVO();
        BeanUtils.copyProperties(category, vo);

        Long recipeCount = recipeMapper.selectCount(
                new LambdaQueryWrapper<Recipe>()
                        .eq(Recipe::getCategoryId, category.getId())
                        .eq(Recipe::getTenantId, getCurrentTenantId())
                        .eq(Recipe::getDeleted, 0)
        );
        vo.setRecipeCount(recipeCount.intValue());

        return vo;
    }

    private LambdaQueryWrapper<RecipeCategory> buildVisibleCategoryWrapper() {
        LambdaQueryWrapper<RecipeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RecipeCategory::getDeleted, 0)
                .and(query -> query
                        .in(RecipeCategory::getCategoryCode, BUILTIN_CATEGORY_CODES)
                        .or()
                        .eq(RecipeCategory::getTenantId, getCurrentTenantId()));
        return wrapper;
    }

    private RecipeCategory getVisibleCategoryById(Long id) {
        RecipeCategory category = getById(id);
        if (category == null
                || !Objects.equals(category.getDeleted(), 0)
                || (!isBuiltinCategory(category) && !Objects.equals(category.getTenantId(), getCurrentTenantId()))) {
            throw new RuntimeException("类别不存在");
        }
        return category;
    }

    private boolean isBuiltinCategory(RecipeCategory category) {
        return category != null && BUILTIN_CATEGORY_CODES.contains(category.getCategoryCode());
    }

    private void ensureCustomEditable(RecipeCategory category) {
        if (isBuiltinCategory(category)) {
            throw new RuntimeException("系统内置分类项仅支持查看，不允许修改或删除");
        }
    }

    private Long getCurrentTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }
}
