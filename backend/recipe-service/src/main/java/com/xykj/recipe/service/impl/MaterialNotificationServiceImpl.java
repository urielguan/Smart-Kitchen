package com.xykj.recipe.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.recipe.dto.MaterialNotificationQueryDTO;
import com.xykj.recipe.entity.MaterialNotification;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.WmsStock;
import com.xykj.recipe.mapper.MaterialNotificationMapper;
import com.xykj.recipe.mapper.RecipeIngredientMapper;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.mapper.WmsStockMapper;
import com.xykj.recipe.service.MaterialNotificationService;
import com.xykj.recipe.vo.MaterialNotificationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物料预警通知服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialNotificationServiceImpl implements MaterialNotificationService {

    private final MaterialNotificationMapper notificationMapper;
    private final WmsStockMapper wmsStockMapper;
    private final RecipeMapper recipeMapper;
    private final RecipeIngredientMapper recipeIngredientMapper;

    /**
     * 定时扫描临期物料 - 每天早上6点执行
     */
    @Scheduled(cron = "0 0 6 * * ?")
    @Transactional
    public void scheduledScanExpiringMaterials() {
        log.info("开始定时扫描临期物料...");
        int count = scanExpiringMaterials(7);
        log.info("临期物料扫描完成，生成通知数量: {}", count);
    }

    @Override
    @Transactional
    public int scanExpiringMaterials(int days) {
        // 查询指定天数内到期的库存
        LocalDate today = LocalDate.now();
        LocalDate expiryThreshold = today.plusDays(days);

        // 使用MyBatis Plus查询wms_inventory表
        LambdaQueryWrapper<WmsStock> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(w -> w.eq(WmsStock::getStatus, "normal")
                .or()
                .eq(WmsStock::getStatus, "warning"))
                .isNotNull(WmsStock::getExpiryDate)
                .le(WmsStock::getExpiryDate, expiryThreshold)
                .gt(WmsStock::getQuantity, BigDecimal.ZERO);

        List<WmsStock> expiringStocks = wmsStockMapper.selectList(queryWrapper);

        if (CollectionUtils.isEmpty(expiringStocks)) {
            return 0;
        }

        int notificationCount = 0;
        for (WmsStock stock : expiringStocks) {
            // 检查是否已存在相同批次的未处理通知
            LambdaQueryWrapper<MaterialNotification> existQuery = new LambdaQueryWrapper<>();
            existQuery.eq(MaterialNotification::getInventoryId, stock.getId())
                    .in(MaterialNotification::getStatus, Arrays.asList("unread", "read"));

            if (notificationMapper.selectCount(existQuery) > 0) {
                continue;
            }

            // 计算剩余天数
            int daysRemaining = (int) ChronoUnit.DAYS.between(today, stock.getExpiryDate());

            // 创建通知
            MaterialNotification notification = new MaterialNotification();
            notification.setNotificationType(daysRemaining < 0 ? "expired" : "expiring");
            notification.setMaterialId(stock.getMaterialId());
            notification.setMaterialName(stock.getMaterialName());
            notification.setInventoryId(stock.getId());
            notification.setBatchNo(stock.getBatchNo());
            notification.setQuantity(stock.getQuantity());
            notification.setUnit(stock.getUnit());
            notification.setExpiryDate(stock.getExpiryDate());
            notification.setDaysRemaining(daysRemaining);
            notification.setOrgId(stock.getOrgId());
            notification.setTenantId(stock.getTenantId());
            notification.setCreatedAt(LocalDateTime.now());
            notification.setUpdatedAt(LocalDateTime.now());

            // 设置优先级
            if (daysRemaining < 0) {
                notification.setPriority("high");
            } else if (daysRemaining <= 3) {
                notification.setPriority("high");
            } else if (daysRemaining <= 5) {
                notification.setPriority("medium");
            } else {
                notification.setPriority("low");
            }
            notification.setStatus("unread");

            // 查找使用该物料的菜谱
            List<Recipe> recipes = findRecipesByMaterialId(stock.getMaterialId());
            if (!recipes.isEmpty()) {
                List<Long> recipeIds = recipes.stream().map(Recipe::getId).collect(Collectors.toList());
                List<String> recipeNames = recipes.stream().map(Recipe::getRecipeName).collect(Collectors.toList());

                notification.setRecommendedRecipeIds(recipeIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
                notification.setRecommendedRecipeNames(String.join(",", recipeNames));
            }

            // 生成通知标题和内容
            if (daysRemaining < 0) {
                notification.setTitle("物料已过期预警");
                notification.setContent(String.format("物料【%s】（批次：%s）已于%s过期，当前库存%s%s，请立即处理！",
                        stock.getMaterialName(), stock.getBatchNo(), stock.getExpiryDate(),
                        stock.getQuantity(), stock.getUnit()));
            } else if (daysRemaining == 0) {
                notification.setTitle("物料今日到期预警");
                notification.setContent(String.format("物料【%s】（批次：%s）今日到期，当前库存%s%s，请尽快使用！",
                        stock.getMaterialName(), stock.getBatchNo(),
                        stock.getQuantity(), stock.getUnit()));
            } else {
                notification.setTitle("物料临期预警");
                notification.setContent(String.format("物料【%s】（批次：%s）将在%d天后到期（%s），当前库存%s%s，建议优先使用。",
                        stock.getMaterialName(), stock.getBatchNo(), daysRemaining, stock.getExpiryDate(),
                        stock.getQuantity(), stock.getUnit()));
            }

            notificationMapper.insert(notification);
            notificationCount++;
        }

        return notificationCount;
    }

    /**
     * 根据物料ID查找使用该物料的菜谱
     */
    private List<Recipe> findRecipesByMaterialId(Long materialId) {
        // 查询菜谱配料关系
        LambdaQueryWrapper<RecipeIngredient> ingredientQuery = new LambdaQueryWrapper<>();
        ingredientQuery.eq(RecipeIngredient::getMaterialId, materialId);
        List<RecipeIngredient> ingredients = recipeIngredientMapper.selectList(ingredientQuery);

        if (CollectionUtils.isEmpty(ingredients)) {
            return Collections.emptyList();
        }

        // 获取菜谱ID列表
        List<Long> recipeIds = ingredients.stream()
                .map(RecipeIngredient::getRecipeId)
                .distinct()
                .collect(Collectors.toList());

        // 查询菜谱
        LambdaQueryWrapper<Recipe> recipeQuery = new LambdaQueryWrapper<>();
        recipeQuery.in(Recipe::getId, recipeIds)
                .eq(Recipe::getStatus, "active");
        List<Recipe> recipes = recipeMapper.selectList(recipeQuery);

        // 限制返回前5个
        return recipes.stream().limit(5).collect(Collectors.toList());
    }

    @Override
    @DataScope
    public Page<MaterialNotificationVO> getNotifications(MaterialNotificationQueryDTO query) {
        Page<MaterialNotification> page = new Page<>(query.getPageNum(), query.getPageSize());

        LambdaQueryWrapper<MaterialNotification> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getNotificationType())) {
            queryWrapper.eq(MaterialNotification::getNotificationType, query.getNotificationType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            queryWrapper.eq(MaterialNotification::getStatus, query.getStatus());
        }
        if (StringUtils.hasText(query.getPriority())) {
            queryWrapper.eq(MaterialNotification::getPriority, query.getPriority());
        }
        if (query.getOrgId() != null) {
            queryWrapper.eq(MaterialNotification::getOrgId, query.getOrgId());
        } else if (query.getOrgIds() != null && !query.getOrgIds().isEmpty()) {
            queryWrapper.in(MaterialNotification::getOrgId, query.getOrgIds());
        } else if (query.getOrgIds() != null) {
            queryWrapper.isNull(MaterialNotification::getId);
        }
        if (StringUtils.hasText(query.getKeyword())) {
            queryWrapper.like(MaterialNotification::getMaterialName, query.getKeyword());
        }

        queryWrapper.orderByDesc(MaterialNotification::getPriority)
                .orderByDesc(MaterialNotification::getCreatedAt);

        Page<MaterialNotification> resultPage = notificationMapper.selectPage(page, queryWrapper);

        // 转换为VO
        Page<MaterialNotificationVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        List<MaterialNotificationVO> voList = resultPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }

    @Override
    public MaterialNotificationVO getNotificationDetail(Long id) {
        MaterialNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            return null;
        }
        return convertToVO(notification);
    }

    @Override
    @Transactional
    public void markAsRead(Long id) {
        MaterialNotification notification = notificationMapper.selectById(id);
        if (notification != null && "unread".equals(notification.getStatus())) {
            notification.setStatus("read");
            notification.setUpdatedAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    @Override
    @Transactional
    public void batchMarkAsRead(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        for (Long id : ids) {
            markAsRead(id);
        }
    }

    @Override
    @Transactional
    public void markAsHandled(Long id, Long handlerId, String remark) {
        MaterialNotification notification = notificationMapper.selectById(id);
        if (notification != null) {
            notification.setStatus("handled");
            notification.setHandledBy(handlerId);
            notification.setHandledAt(LocalDateTime.now());
            notification.setHandleRemark(remark);
            notification.setUpdatedAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    @Override
    @Transactional
    public void dismiss(Long id) {
        MaterialNotification notification = notificationMapper.selectById(id);
        if (notification != null) {
            notification.setStatus("dismissed");
            notification.setUpdatedAt(LocalDateTime.now());
            notificationMapper.updateById(notification);
        }
    }

    @Override
    public int getUnreadCount(Long orgId) {
        LambdaQueryWrapper<MaterialNotification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialNotification::getStatus, "unread");
        if (orgId != null) {
            queryWrapper.eq(MaterialNotification::getOrgId, orgId);
        }
        return Math.toIntExact(notificationMapper.selectCount(queryWrapper));
    }

    @Override
    public List<MaterialNotificationVO> getHighPriorityNotifications(Long orgId, int limit) {
        LambdaQueryWrapper<MaterialNotification> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MaterialNotification::getStatus, "unread")
                .eq(MaterialNotification::getPriority, "high");
        if (orgId != null) {
            queryWrapper.eq(MaterialNotification::getOrgId, orgId);
        }
        queryWrapper.orderByDesc(MaterialNotification::getCreatedAt)
                .last("LIMIT " + limit);

        List<MaterialNotification> notifications = notificationMapper.selectList(queryWrapper);
        return notifications.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    @Override
    public NotificationStats getStats(Long orgId) {
        LambdaQueryWrapper<MaterialNotification> totalQuery = new LambdaQueryWrapper<>();
        if (orgId != null) {
            totalQuery.eq(MaterialNotification::getOrgId, orgId);
        }
        long total = notificationMapper.selectCount(totalQuery);

        LambdaQueryWrapper<MaterialNotification> unreadQuery = new LambdaQueryWrapper<>();
        if (orgId != null) {
            unreadQuery.eq(MaterialNotification::getOrgId, orgId);
        }
        unreadQuery.eq(MaterialNotification::getStatus, "unread");
        long unread = notificationMapper.selectCount(unreadQuery);

        LambdaQueryWrapper<MaterialNotification> highPriorityQuery = new LambdaQueryWrapper<>();
        if (orgId != null) {
            highPriorityQuery.eq(MaterialNotification::getOrgId, orgId);
        }
        highPriorityQuery.eq(MaterialNotification::getPriority, "high")
                .eq(MaterialNotification::getStatus, "unread");
        long highPriority = notificationMapper.selectCount(highPriorityQuery);

        LambdaQueryWrapper<MaterialNotification> expiringQuery = new LambdaQueryWrapper<>();
        if (orgId != null) {
            expiringQuery.eq(MaterialNotification::getOrgId, orgId);
        }
        expiringQuery.eq(MaterialNotification::getNotificationType, "expiring")
                .eq(MaterialNotification::getStatus, "unread");
        long expiring = notificationMapper.selectCount(expiringQuery);

        LambdaQueryWrapper<MaterialNotification> expiredQuery = new LambdaQueryWrapper<>();
        if (orgId != null) {
            expiredQuery.eq(MaterialNotification::getOrgId, orgId);
        }
        expiredQuery.eq(MaterialNotification::getNotificationType, "expired")
                .eq(MaterialNotification::getStatus, "unread");
        long expired = notificationMapper.selectCount(expiredQuery);

        return new NotificationStats((int) total, (int) unread, (int) highPriority, (int) expiring, (int) expired);
    }

    /**
     * 转换为VO
     */
    private MaterialNotificationVO convertToVO(MaterialNotification notification) {
        MaterialNotificationVO vo = new MaterialNotificationVO();
        BeanUtils.copyProperties(notification, vo);

        // 设置类型名称
        vo.setNotificationTypeName(getNotificationTypeName(notification.getNotificationType()));
        vo.setPriorityName(getPriorityName(notification.getPriority()));
        vo.setStatusName(getStatusName(notification.getStatus()));

        // 解析推荐菜谱
        if (StringUtils.hasText(notification.getRecommendedRecipeIds())) {
            List<MaterialNotificationVO.RecommendedRecipe> recipes = new ArrayList<>();
            String[] ids = notification.getRecommendedRecipeIds().split(",");
            String[] names = notification.getRecommendedRecipeNames() != null
                    ? notification.getRecommendedRecipeNames().split(",")
                    : new String[0];

            for (int i = 0; i < ids.length && i < names.length; i++) {
                MaterialNotificationVO.RecommendedRecipe recipe = new MaterialNotificationVO.RecommendedRecipe();
                recipe.setId(Long.parseLong(ids[i].trim()));
                recipe.setRecipeName(names[i].trim());
                recipes.add(recipe);
            }
            vo.setRecommendedRecipes(recipes);
        }

        return vo;
    }

    private String getNotificationTypeName(String type) {
        if (type == null) return "";
        return switch (type) {
            case "expiring" -> "临期预警";
            case "expired" -> "已过期";
            case "low_stock" -> "库存不足";
            default -> type;
        };
    }

    private String getPriorityName(String priority) {
        if (priority == null) return "";
        return switch (priority) {
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            default -> priority;
        };
    }

    private String getStatusName(String status) {
        if (status == null) return "";
        return switch (status) {
            case "unread" -> "未读";
            case "read" -> "已读";
            case "handled" -> "已处理";
            case "dismissed" -> "已忽略";
            default -> status;
        };
    }
}
