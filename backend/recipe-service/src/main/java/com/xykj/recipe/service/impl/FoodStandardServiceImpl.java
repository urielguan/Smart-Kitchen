package com.xykj.recipe.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.recipe.dto.FoodItemQueryDTO;
import com.xykj.recipe.entity.FoodCategory;
import com.xykj.recipe.entity.FoodItem;
import com.xykj.recipe.mapper.FoodCategoryMapper;
import com.xykj.recipe.mapper.FoodItemMapper;
import com.xykj.recipe.service.FoodStandardService;
import com.xykj.recipe.vo.FoodCategoryVO;
import com.xykj.recipe.vo.FoodImportResultVO;
import com.xykj.recipe.vo.FoodItemVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodStandardServiceImpl implements FoodStandardService {

    private static final String JSON_SOURCE_VERSION = "json_data_vision_251206_Qwen2-5-VL-72B-Instruct";
    private static final String EXCEL_SOURCE_VERSION = "china-food-composition-standard-6th-edition-excel";
    private static final String COMBINED_SOURCE_VERSION = JSON_SOURCE_VERSION + " + " + EXCEL_SOURCE_VERSION;
    private static final String CANDIDATE_LEVEL1_NAME = "候选补充食品";
    private static final String CANDIDATE_LEVEL2_NAME = "01食品营养成分数据库";
    private static final Pattern NAME_NORMALIZE_PATTERN = Pattern.compile("[\\s\\p{Punct}（）()\\[\\]【】《》“”‘’·、，。；：]+");

    private final FoodCategoryMapper foodCategoryMapper;
    private final FoodItemMapper foodItemMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataFormatter dataFormatter = new DataFormatter(Locale.US);

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FoodImportResultVO importJson() {
        Integer existingCategoryCount = foodCategoryMapper.selectCount(new LambdaQueryWrapper<FoodCategory>()
                .eq(FoodCategory::getDeleted, 0)).intValue();
        Integer existingItemCount = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getDeleted, 0)).intValue();
        if (existingCategoryCount > 0 && existingItemCount > 0 && isFoodLibraryReadyForFastReturn()) {
            log.info("标准食品库已初始化，直接返回当前统计: categoryCount={}, itemCount={}, sourceVersion={}",
                    existingCategoryCount, existingItemCount, COMBINED_SOURCE_VERSION);
            ImportStats stats = new ImportStats();
            stats.sourceStats.put("cached", existingItemCount);
            return buildImportResult(0, existingCategoryCount, existingItemCount, stats);
        }
        boolean allowMissingSource = existingCategoryCount > 0 && existingItemCount > 0;

        Path jsonBaseDir = resolveJsonBaseDirIfPresent();
        Path excelBaseDir = resolveExcelBaseDirIfPresent();
        if (allowMissingSource && jsonBaseDir == null && excelBaseDir == null) {
            log.info("标准食品库已初始化，且当前环境未提供食品源目录，直接返回当前统计: categoryCount={}, itemCount={}, sourceVersion={}",
                    existingCategoryCount, existingItemCount, COMBINED_SOURCE_VERSION);
            return buildImportResult(0, existingCategoryCount, existingItemCount, new ImportStats());
        }

        ImportStats stats = new ImportStats();
        ItemCache itemCache = loadItemCache();
        CategoryCache categoryCache = loadCategoryCache();

        importJsonBaseline(stats, itemCache, categoryCache, jsonBaseDir, allowMissingSource);
        importExcelStandardFiles(stats, itemCache, categoryCache, excelBaseDir, allowMissingSource);
        importExcelCandidateWorkbook(stats, itemCache, categoryCache, excelBaseDir, allowMissingSource);

        return buildImportResult(
                stats.processedFileCount,
                foodCategoryMapper.selectCount(new LambdaQueryWrapper<FoodCategory>()
                        .eq(FoodCategory::getDeleted, 0)).intValue(),
                foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                        .eq(FoodItem::getDeleted, 0)).intValue(),
                stats
        );
    }

    private boolean isFoodLibraryReadyForFastReturn() {
        Integer candidateCategoryCount = foodCategoryMapper.selectCount(new LambdaQueryWrapper<FoodCategory>()
                .eq(FoodCategory::getDeleted, 0)
                .in(FoodCategory::getCategoryName, List.of(CANDIDATE_LEVEL1_NAME, CANDIDATE_LEVEL2_NAME))).intValue();
        Integer excelItemCount = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getDeleted, 0)
                .like(FoodItem::getSourceVersion, EXCEL_SOURCE_VERSION)).intValue();
        return candidateCategoryCount >= 2 && excelItemCount > 0;
    }

    @Override
    public List<FoodCategoryVO> listCategories() {
        return foodCategoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                        .eq(FoodCategory::getDeleted, 0)
                        .orderByAsc(FoodCategory::getCategoryLevel, FoodCategory::getSortOrder, FoodCategory::getId))
                .stream()
                .map(this::toCategoryVO)
                .toList();
    }

    @Override
    public PageResult<FoodItemVO> listItems(FoodItemQueryDTO query) {
        Page<FoodItem> page = foodItemMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                new LambdaQueryWrapper<FoodItem>()
                        .eq(FoodItem::getDeleted, 0)
                        .like(query.getFoodName() != null && !query.getFoodName().isBlank(), FoodItem::getFoodName, query.getFoodName())
                        .and(query.getCategoryId() != null, wrapper -> wrapper.eq(FoodItem::getCategoryLevel1Id, query.getCategoryId())
                                .or().eq(FoodItem::getCategoryLevel2Id, query.getCategoryId()))
                        .orderByAsc(FoodItem::getFoodCode)
        );
        List<FoodItemVO> list = page.getRecords().stream().map(this::toItemVO).toList();
        return PageResult.of(page, list);
    }

    @Override
    public FoodItemVO getItem(Long id) {
        FoodItem item = foodItemMapper.selectById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw BizException.notFound("标准食品不存在");
        }
        return toItemVO(item);
    }

    private void importJsonBaseline(ImportStats stats, ItemCache itemCache, CategoryCache categoryCache,
                                    Path baseDir, boolean allowMissingSource) {
        Integer existingJsonItemCount = foodItemMapper.selectCount(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getDeleted, 0)
                .like(FoodItem::getSourceVersion, JSON_SOURCE_VERSION)).intValue();
        if (baseDir == null) {
            if (allowMissingSource) {
                if (existingJsonItemCount > 0) {
                    stats.skippedItemCount += existingJsonItemCount;
                }
                log.info("当前环境未提供标准食品 JSON 目录，跳过 JSON 基线导入: itemCount={}", existingJsonItemCount);
                return;
            }
            throw BizException.notFound("未找到标准食品 JSON 目录");
        }

        List<Path> files = listFiles(baseDir, ".json");
        stats.processedFileCount += files.size();
        if (existingJsonItemCount > 0) {
            stats.skippedItemCount += existingJsonItemCount;
            log.info("标准食品 JSON 基础库已存在，跳过基线导入: itemCount={}", existingJsonItemCount);
            return;
        }

        int topSort = 10;
        for (Path file : files) {
            String fileName = file.getFileName().toString().replace(".json", "");
            String[] parts = fileName.replace("merged_", "").split("-", 2);
            if (parts.length < 2) {
                stats.skippedItemCount++;
                continue;
            }

            FoodCategory level1 = upsertCategory(categoryCache, null, parts[0], 1, topSort, fileName);
            FoodCategory level2 = upsertCategory(categoryCache, level1.getId(), parts[1], 2, topSort, fileName);
            topSort += 10;

            List<Map<String, Object>> rows = readRows(file);
            stats.sourceStats.put(file.getFileName().toString(), rows.size());
            for (Map<String, Object> row : rows) {
                String foodCode = stringify(row.get("foodCode"));
                if (foodCode == null || foodCode.isBlank()) {
                    stats.skippedItemCount++;
                    continue;
                }

                FoodItem item = itemCache.byCode.get(foodCode);
                if (item != null) {
                    stats.duplicateItemCount++;
                    continue;
                }

                FoodItem created = new FoodItem();
                created.setFoodCode(foodCode);
                created.setFoodName(stringify(row.get("foodName")));
                created.setCategoryLevel1Id(level1.getId());
                created.setCategoryLevel2Id(level2.getId());
                created.setEdibleRatio(decimalValue(row.get("edible")));
                created.setEnergyKcal(decimalValue(row.get("energyKCal")));
                created.setProtein(decimalValue(row.get("protein")));
                created.setFat(decimalValue(row.get("fat")));
                created.setCarbohydrate(decimalValue(row.get("CHO")));
                created.setDietaryFiber(decimalValue(row.get("dietaryFiber")));
                created.setSodium(decimalValue(row.get("Na")));
                created.setVitaminA(decimalValue(row.get("vitaminA")));
                created.setVitaminB1(decimalValue(row.get("thiamin")));
                created.setVitaminB2(decimalValue(row.get("riboflavin")));
                created.setVitaminC(decimalValue(row.get("vitaminC")));
                created.setVitaminE(decimalValue(row.get("vitaminETotal")));
                created.setCalcium(decimalValue(row.get("Ca")));
                created.setIron(decimalValue(row.get("Fe")));
                created.setZinc(decimalValue(row.get("Zn")));
                created.setSourceFile(fileName);
                created.setSourceVersion(JSON_SOURCE_VERSION);
                created.setRawPayload(JSONUtil.toJsonStr(row));
                created.setStatus("active");
                created.setCreatedAt(LocalDateTime.now());
                created.setUpdatedAt(LocalDateTime.now());
                foodItemMapper.insert(created);
                itemCache.put(created);
                stats.newFoodItemCount++;
            }
        }
    }

    private void importExcelStandardFiles(ImportStats stats, ItemCache itemCache, CategoryCache categoryCache,
                                          Path excelDir, boolean allowMissingSource) {
        if (excelDir == null) {
            if (allowMissingSource) {
                log.info("当前环境未提供《中国食物成分表》第6版目录，跳过 Excel 标准库导入");
                return;
            }
            throw BizException.notFound("未找到《中国食物成分表》第6版目录");
        }
        List<Path> files = listFiles(excelDir, ".xlsx").stream()
                .filter(path -> !path.getFileName().toString().startsWith("01"))
                .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                .toList();
        stats.processedFileCount += files.size();

        int workbookSort = 1000;
        for (Path file : files) {
            int processedRows = 0;
            try (InputStream inputStream = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(inputStream)) {
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    String level1Name = cellText(sheet.getRow(0), 0);
                    if (level1Name == null || level1Name.isBlank()) {
                        continue;
                    }

                    List<String> columnLabels = buildColumnLabels(sheet);
                    FoodCategory level1 = upsertCategory(categoryCache, null, level1Name, 1, workbookSort, file.getFileName().toString());
                    String currentLevel2Name = null;
                    int level2Sort = workbookSort;

                    for (int rowIndex = 4; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) {
                            continue;
                        }

                        String firstCell = cellText(row, 0);
                        String secondCell = cellText(row, 1);
                        if ((firstCell == null || firstCell.isBlank()) && (secondCell == null || secondCell.isBlank())) {
                            continue;
                        }

                        if (firstCell != null && !firstCell.isBlank() && (secondCell == null || secondCell.isBlank())) {
                            currentLevel2Name = firstCell.trim();
                            level2Sort += 10;
                            continue;
                        }

                        if (currentLevel2Name == null || currentLevel2Name.isBlank()) {
                            stats.skippedItemCount++;
                            continue;
                        }

                        FoodCategory level2 = upsertCategory(categoryCache, level1.getId(), currentLevel2Name, 2, level2Sort, file.getFileName().toString());
                        ExcelFoodRow excelRow = toExcelFoodRow(row, columnLabels, level1Name, currentLevel2Name, file.getFileName().toString(), sheet.getSheetName());
                        if (excelRow.foodCode() == null || excelRow.foodName() == null) {
                            stats.skippedItemCount++;
                            continue;
                        }

                        processedRows++;
                        upsertOrSupplementExcelRow(excelRow, level1.getId(), level2.getId(), itemCache, stats);
                    }
                }
                workbookSort += 100;
            } catch (Exception ex) {
                stats.exceptionCount++;
                log.warn("读取第6版标准食品 Excel 失败: {}", file.getFileName(), ex);
            }
            stats.sourceStats.put(file.getFileName().toString(), processedRows);
        }
    }

    private void importExcelCandidateWorkbook(ImportStats stats, ItemCache itemCache, CategoryCache categoryCache,
                                              Path excelDir, boolean allowMissingSource) {
        if (excelDir == null) {
            if (allowMissingSource) {
                log.info("当前环境未提供《中国食物成分表》第6版目录，跳过候选营养整理表导入");
                return;
            }
            throw BizException.notFound("未找到《中国食物成分表》第6版目录");
        }

        Path candidateFile = excelDir.resolve("01食品营养成分数据库.xlsx");
        if (!Files.isRegularFile(candidateFile)) {
            return;
        }

        stats.processedFileCount++;
        int processedRows = 0;
        try (InputStream inputStream = Files.newInputStream(candidateFile); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            FoodCategory level1 = upsertCategory(categoryCache, null, CANDIDATE_LEVEL1_NAME, 1, 9000, candidateFile.getFileName().toString());
            FoodCategory level2 = upsertCategory(categoryCache, level1.getId(), CANDIDATE_LEVEL2_NAME, 2, 9010, candidateFile.getFileName().toString());
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                String foodName = cellText(row, 1);
                if (foodName == null || foodName.isBlank()) {
                    continue;
                }

                processedRows++;
                FoodItem existing = itemCache.byNormalizedName.get(normalizeFoodName(foodName));
                if (existing == null) {
                    FoodItem created = new FoodItem();
                    created.setFoodCode(buildCandidateFoodCode(foodName));
                    created.setFoodName(foodName);
                    created.setCategoryLevel1Id(level1.getId());
                    created.setCategoryLevel2Id(level2.getId());
                    created.setEdibleRatio(decimalValue(cellText(row, 8)));
                    created.setEnergyKcal(kjToKcal(decimalValue(cellText(row, 2))));
                    created.setProtein(decimalValue(cellText(row, 3)));
                    created.setFat(decimalValue(cellText(row, 4)));
                    created.setCarbohydrate(decimalValue(cellText(row, 5)));
                    created.setSodium(decimalValue(cellText(row, 6)));
                    created.setSourceFile(candidateFile.getFileName().toString());
                    created.setSourceVersion(EXCEL_SOURCE_VERSION);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("foodName", foodName);
                    payload.put("edibleRatio", created.getEdibleRatio());
                    payload.put("energyKj", decimalValue(cellText(row, 2)));
                    payload.put("energyKcal", created.getEnergyKcal());
                    payload.put("protein", created.getProtein());
                    payload.put("fat", created.getFat());
                    payload.put("carbohydrate", created.getCarbohydrate());
                    payload.put("sodium", created.getSodium());
                    payload.put("sourceType", "candidate_excel_insert");
                    created.setRawPayload(JSONUtil.toJsonStr(payload));
                    created.setStatus("active");
                    created.setCreatedAt(LocalDateTime.now());
                    created.setUpdatedAt(LocalDateTime.now());
                    foodItemMapper.insert(created);
                    itemCache.put(created);
                    stats.newFoodItemCount++;
                    continue;
                }

                ExcelFoodRow candidate = new ExcelFoodRow(
                        existing.getFoodCode(),
                        foodName,
                        null,
                        decimalValue(cellText(row, 8)),
                        kjToKcal(decimalValue(cellText(row, 2))),
                        decimalValue(cellText(row, 3)),
                        decimalValue(cellText(row, 4)),
                        decimalValue(cellText(row, 5)),
                        null,
                        decimalValue(cellText(row, 6)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        candidateFile.getFileName().toString(),
                        "食品营养成分数据表",
                        "候选补充",
                        "第6版整理库"
                );
                int filledCount = fillMissingNutrition(existing, candidate);
                if (filledCount > 0) {
                    existing.setSourceFile(appendSourceValue(existing.getSourceFile(), candidate.fileName()));
                    existing.setSourceVersion(appendSourceValue(existing.getSourceVersion(), EXCEL_SOURCE_VERSION));
                    existing.setRawPayload(mergeRawPayload(existing.getRawPayload(), candidate.toPayloadMap(), "candidate_excel"));
                    existing.setUpdatedAt(LocalDateTime.now());
                    foodItemMapper.updateById(existing);
                    stats.supplementedFieldCount += filledCount;
                } else {
                    stats.duplicateItemCount++;
                }
            }
        } catch (Exception ex) {
            stats.exceptionCount++;
            log.warn("读取候选营养整理表失败: {}", candidateFile.getFileName(), ex);
        }
        stats.sourceStats.put(candidateFile.getFileName().toString(), processedRows);
    }

    private String buildCandidateFoodCode(String foodName) {
        return "C01" + DigestUtil.md5Hex(normalizeFoodName(foodName)).substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private void upsertOrSupplementExcelRow(ExcelFoodRow excelRow, Long level1Id, Long level2Id, ItemCache itemCache, ImportStats stats) {
        FoodItem existing = itemCache.byCode.get(excelRow.foodCode());
        if (existing == null) {
            existing = itemCache.byNormalizedName.get(normalizeFoodName(excelRow.foodName()));
        }

        if (existing == null) {
            FoodItem created = new FoodItem();
            created.setFoodCode(excelRow.foodCode());
            created.setFoodName(excelRow.foodName());
            created.setCategoryLevel1Id(level1Id);
            created.setCategoryLevel2Id(level2Id);
            created.setEdibleRatio(excelRow.edibleRatio());
            created.setEnergyKcal(excelRow.energyKcal());
            created.setProtein(excelRow.protein());
            created.setFat(excelRow.fat());
            created.setCarbohydrate(excelRow.carbohydrate());
            created.setDietaryFiber(excelRow.dietaryFiber());
            created.setSodium(excelRow.sodium());
            created.setVitaminA(excelRow.vitaminA());
            created.setVitaminB1(excelRow.vitaminB1());
            created.setVitaminB2(excelRow.vitaminB2());
            created.setVitaminC(excelRow.vitaminC());
            created.setVitaminE(excelRow.vitaminE());
            created.setCalcium(excelRow.calcium());
            created.setIron(excelRow.iron());
            created.setZinc(excelRow.zinc());
            created.setSourceFile(excelRow.fileName());
            created.setSourceVersion(EXCEL_SOURCE_VERSION);
            created.setRawPayload(JSONUtil.toJsonStr(excelRow.toPayloadMap()));
            created.setStatus("active");
            created.setCreatedAt(LocalDateTime.now());
            created.setUpdatedAt(LocalDateTime.now());
            foodItemMapper.insert(created);
            itemCache.put(created);
            stats.newFoodItemCount++;
            return;
        }

        int filledCount = fillMissingNutrition(existing, excelRow);
        if (filledCount <= 0) {
            stats.duplicateItemCount++;
            return;
        }

        if (existing.getFoodName() == null || existing.getFoodName().isBlank()) {
            existing.setFoodName(excelRow.foodName());
        }
        if (existing.getCategoryLevel1Id() == null) {
            existing.setCategoryLevel1Id(level1Id);
        }
        if (existing.getCategoryLevel2Id() == null) {
            existing.setCategoryLevel2Id(level2Id);
        }
        existing.setSourceFile(appendSourceValue(existing.getSourceFile(), excelRow.fileName()));
        existing.setSourceVersion(appendSourceValue(existing.getSourceVersion(), EXCEL_SOURCE_VERSION));
        existing.setRawPayload(mergeRawPayload(existing.getRawPayload(), excelRow.toPayloadMap(), "excel_supplement"));
        existing.setUpdatedAt(LocalDateTime.now());
        foodItemMapper.updateById(existing);
        itemCache.put(existing);
        stats.supplementedFieldCount += filledCount;
    }

    private int fillMissingNutrition(FoodItem item, ExcelFoodRow row) {
        int filledCount = 0;
        if (item.getEdibleRatio() == null && row.edibleRatio() != null) {
            item.setEdibleRatio(row.edibleRatio());
            filledCount++;
        }
        if (item.getEnergyKcal() == null && row.energyKcal() != null) {
            item.setEnergyKcal(row.energyKcal());
            filledCount++;
        }
        if (item.getProtein() == null && row.protein() != null) {
            item.setProtein(row.protein());
            filledCount++;
        }
        if (item.getFat() == null && row.fat() != null) {
            item.setFat(row.fat());
            filledCount++;
        }
        if (item.getCarbohydrate() == null && row.carbohydrate() != null) {
            item.setCarbohydrate(row.carbohydrate());
            filledCount++;
        }
        if (item.getDietaryFiber() == null && row.dietaryFiber() != null) {
            item.setDietaryFiber(row.dietaryFiber());
            filledCount++;
        }
        if (item.getSodium() == null && row.sodium() != null) {
            item.setSodium(row.sodium());
            filledCount++;
        }
        if (item.getVitaminA() == null && row.vitaminA() != null) {
            item.setVitaminA(row.vitaminA());
            filledCount++;
        }
        if (item.getVitaminB1() == null && row.vitaminB1() != null) {
            item.setVitaminB1(row.vitaminB1());
            filledCount++;
        }
        if (item.getVitaminB2() == null && row.vitaminB2() != null) {
            item.setVitaminB2(row.vitaminB2());
            filledCount++;
        }
        if (item.getVitaminC() == null && row.vitaminC() != null) {
            item.setVitaminC(row.vitaminC());
            filledCount++;
        }
        if (item.getVitaminE() == null && row.vitaminE() != null) {
            item.setVitaminE(row.vitaminE());
            filledCount++;
        }
        if (item.getCalcium() == null && row.calcium() != null) {
            item.setCalcium(row.calcium());
            filledCount++;
        }
        if (item.getIron() == null && row.iron() != null) {
            item.setIron(row.iron());
            filledCount++;
        }
        if (item.getZinc() == null && row.zinc() != null) {
            item.setZinc(row.zinc());
            filledCount++;
        }
        return filledCount;
    }

    private FoodCategory upsertCategory(CategoryCache cache, Long parentId, String name, int level, int sortOrder, String fileName) {
        String code = "FC_" + DigestUtil.md5Hex((parentId == null ? "ROOT" : parentId) + ":" + level + ":" + name).substring(0, 16);
        FoodCategory existing = cache.byCode.get(code);
        if (existing != null) {
            existing.setCategoryName(name);
            existing.setSortOrder(sortOrder);
            existing.setSourceFile(appendSourceValue(existing.getSourceFile(), fileName));
            existing.setUpdatedAt(LocalDateTime.now());
            foodCategoryMapper.updateById(existing);
            return existing;
        }

        FoodCategory category = new FoodCategory();
        category.setParentId(parentId);
        category.setCategoryCode(code);
        category.setCategoryName(name);
        category.setCategoryLevel(level);
        category.setSortOrder(sortOrder);
        category.setSourceFile(fileName);
        category.setStatus("active");
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        foodCategoryMapper.insert(category);
        cache.byCode.put(code, category);
        return category;
    }

    private ItemCache loadItemCache() {
        List<FoodItem> items = foodItemMapper.selectList(new LambdaQueryWrapper<FoodItem>()
                .eq(FoodItem::getDeleted, 0));
        ItemCache cache = new ItemCache();
        for (FoodItem item : items) {
            cache.put(item);
        }
        return cache;
    }

    private CategoryCache loadCategoryCache() {
        List<FoodCategory> categories = foodCategoryMapper.selectList(new LambdaQueryWrapper<FoodCategory>()
                .eq(FoodCategory::getDeleted, 0));
        CategoryCache cache = new CategoryCache();
        for (FoodCategory category : categories) {
            cache.byCode.put(category.getCategoryCode(), category);
        }
        return cache;
    }

    private List<String> buildColumnLabels(Sheet sheet) {
        Row zhHeader = sheet.getRow(1);
        Row enHeader = sheet.getRow(2);
        int maxColumn = Math.max(zhHeader == null ? 0 : zhHeader.getLastCellNum(), enHeader == null ? 0 : enHeader.getLastCellNum());
        List<String> labels = new ArrayList<>(maxColumn);
        for (int i = 0; i < maxColumn; i++) {
            String zh = cellText(zhHeader, i);
            String en = cellText(enHeader, i);
            labels.add(((zh == null ? "" : zh) + "|" + (en == null ? "" : en)).trim());
        }
        return labels;
    }

    private ExcelFoodRow toExcelFoodRow(Row row, List<String> columnLabels, String level1, String level2, String fileName, String sheetName) {
        String foodCode = trimNumericText(cellText(row, findColumn(columnLabels, "食物编码", "foodcode")));
        String foodName = cellText(row, findColumn(columnLabels, "食物名称", "foodname"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("foodCode", foodCode);
        payload.put("foodName", foodName);
        payload.put("categoryLevel1", level1);
        payload.put("categoryLevel2", level2);
        payload.put("sheetName", sheetName);
        payload.put("sourceType", "standard_excel");

        BigDecimal edibleRatio = readDecimal(row, columnLabels, payload, "edible", "食部", "edible");
        BigDecimal energyKcal = readDecimal(row, columnLabels, payload, "energyKcal", "能量", "energy");
        BigDecimal protein = readDecimal(row, columnLabels, payload, "protein", "蛋白质", "protein");
        BigDecimal fat = readDecimal(row, columnLabels, payload, "fat", "脂肪", "fat");
        BigDecimal carbohydrate = readDecimal(row, columnLabels, payload, "carbohydrate", "碳水化合物", "cho");
        BigDecimal dietaryFiber = readDecimal(row, columnLabels, payload, "dietaryFiber", "膳食纤维", "dietaryfiber");
        BigDecimal sodium = readDecimal(row, columnLabels, payload, "sodium", "钠", "|na");
        BigDecimal vitaminA = readDecimal(row, columnLabels, payload, "vitaminA", "总维生素a", "vitamina");
        BigDecimal vitaminB1 = readDecimal(row, columnLabels, payload, "vitaminB1", "硫胺素", "thiamin");
        BigDecimal vitaminB2 = readDecimal(row, columnLabels, payload, "vitaminB2", "核黄素", "riboflavin");
        BigDecimal vitaminC = readDecimal(row, columnLabels, payload, "vitaminC", "维生素c", "vitaminc");
        BigDecimal vitaminE = readDecimal(row, columnLabels, payload, "vitaminE", "维生素e", "total");
        BigDecimal calcium = readDecimal(row, columnLabels, payload, "calcium", "钙", "|ca");
        BigDecimal iron = readDecimal(row, columnLabels, payload, "iron", "铁", "|fe");
        BigDecimal zinc = readDecimal(row, columnLabels, payload, "zinc", "锌", "|zn");

        return new ExcelFoodRow(
                foodCode,
                foodName,
                null,
                edibleRatio,
                energyKcal,
                protein,
                fat,
                carbohydrate,
                dietaryFiber,
                sodium,
                vitaminA,
                vitaminB1,
                vitaminB2,
                vitaminC,
                vitaminE,
                calcium,
                iron,
                zinc,
                fileName,
                sheetName,
                level2,
                level1
        );
    }

    private BigDecimal readDecimal(Row row, List<String> columnLabels, Map<String, Object> payload, String fieldName, String... candidates) {
        int columnIndex = findColumn(columnLabels, candidates);
        if (columnIndex < 0) {
            payload.put(fieldName, null);
            return null;
        }
        String rawText = cellText(row, columnIndex);
        payload.put(fieldName, rawText);
        return decimalValue(rawText);
    }

    private int findColumn(List<String> columnLabels, String... candidates) {
        for (int i = 0; i < columnLabels.size(); i++) {
            String normalized = normalizeHeader(columnLabels.get(i));
            for (String candidate : candidates) {
                if (normalized.contains(normalizeHeader(candidate))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private String normalizeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("（", "")
                .replace("）", "")
                .replace("(", "")
                .replace(")", "")
                .replace("/", "")
                .replace("-", "")
                .replace("_", "");
    }

    private BigDecimal kjToKcal(BigDecimal kj) {
        if (kj == null) {
            return null;
        }
        return kj.divide(BigDecimal.valueOf(4.184d), 2, BigDecimal.ROUND_HALF_UP);
    }

    private List<Map<String, Object>> readRows(Path file) {
        try {
            return objectMapper.readValue(file.toFile(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            throw new BizException("FOOD_IMPORT_ERROR", "读取标准食品文件失败: " + file.getFileName(), e);
        }
    }

    private FoodImportResultVO buildImportResult(int fileCount, int categoryCount, int itemCount, ImportStats stats) {
        FoodImportResultVO vo = new FoodImportResultVO();
        vo.setFileCount(fileCount);
        vo.setCategoryCount(categoryCount);
        vo.setItemCount(itemCount);
        vo.setSourceVersion(COMBINED_SOURCE_VERSION);
        vo.setNewFoodItemCount(stats.newFoodItemCount);
        vo.setSupplementedFieldCount(stats.supplementedFieldCount);
        vo.setSkippedItemCount(stats.skippedItemCount);
        vo.setDuplicateItemCount(stats.duplicateItemCount);
        vo.setExceptionCount(stats.exceptionCount);
        vo.setSourceStats(stats.sourceStats);
        return vo;
    }

    private Path resolveJsonBaseDirIfPresent() {
        return resolveRelativeDirIfPresent("doc/china-food-composition-data-main/" + JSON_SOURCE_VERSION);
    }

    private Path resolveExcelBaseDirIfPresent() {
        return resolveRelativeDirIfPresent("doc/中国食物成分表  标准版  第6版");
    }

    private Path resolveRelativeDir(String relativePath, String errorMessage) {
        Path path = resolveRelativeDirIfPresent(relativePath);
        if (path == null) {
            throw BizException.notFound(errorMessage);
        }
        return path;
    }

    private Path resolveRelativeDirIfPresent(String relativePath) {
        List<Path> candidates = new ArrayList<>();
        Path current = Path.of("").toAbsolutePath();
        for (int i = 0; i < 5 && current != null; i++) {
            candidates.add(current.resolve(relativePath));
            current = current.getParent();
        }
        return candidates.stream().filter(Files::isDirectory).findFirst().orElse(null);
    }

    private List<Path> listFiles(Path dir, String suffix) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(suffix))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new BizException("FOOD_IMPORT_ERROR", "读取标准食品目录失败: " + dir, e);
        }
    }

    private FoodCategoryVO toCategoryVO(FoodCategory category) {
        FoodCategoryVO vo = new FoodCategoryVO();
        BeanUtils.copyProperties(category, vo);
        return vo;
    }

    private FoodItemVO toItemVO(FoodItem item) {
        FoodItemVO vo = new FoodItemVO();
        BeanUtils.copyProperties(item, vo);
        return vo;
    }

    private BigDecimal decimalValue(Object raw) {
        String value = stringify(raw);
        if (value == null || value.isBlank() || "—".equals(value)) {
            return null;
        }
        if ("Tr".equalsIgnoreCase(value)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private String cellText(Row row, int cellIndex) {
        if (row == null || cellIndex < 0) {
            return null;
        }
        try {
            return trimNumericText(dataFormatter.formatCellValue(row.getCell(cellIndex)).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String trimNumericText(String text) {
        if (text == null) {
            return null;
        }
        if (text.endsWith(".0")) {
            return text.substring(0, text.length() - 2);
        }
        return text;
    }

    private String normalizeFoodName(String foodName) {
        if (foodName == null) {
            return "";
        }
        return NAME_NORMALIZE_PATTERN.matcher(foodName.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    private String appendSourceValue(String existingValue, String newValue) {
        if (newValue == null || newValue.isBlank()) {
            return existingValue;
        }
        Set<String> values = new LinkedHashSet<>();
        if (existingValue != null && !existingValue.isBlank()) {
            for (String item : existingValue.split("\\s*;\\s*")) {
                if (!item.isBlank()) {
                    values.add(item);
                }
            }
        }
        values.add(newValue);
        return String.join("; ", values);
    }

    private String mergeRawPayload(String existingRawPayload, Map<String, Object> supplementPayload, String sourceType) {
        ObjectNode supplementNode = objectMapper.valueToTree(supplementPayload);
        supplementNode.put("sourceType", sourceType);
        if (existingRawPayload == null || existingRawPayload.isBlank()) {
            return JSONUtil.toJsonStr(supplementPayload);
        }

        try {
            JsonNode existingNode = objectMapper.readTree(existingRawPayload);
            ObjectNode root;
            if (existingNode.isObject() && existingNode.has("supplements")) {
                root = (ObjectNode) existingNode.deepCopy();
            } else {
                root = objectMapper.createObjectNode();
                root.set("base", existingNode);
                root.putArray("supplements");
            }
            ArrayNode supplements = root.withArray("supplements");
            supplements.add(supplementNode);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("baseRaw", existingRawPayload);
            root.putArray("supplements").add(supplementNode);
            try {
                return objectMapper.writeValueAsString(root);
            } catch (JsonProcessingException ex) {
                return existingRawPayload;
            }
        }
    }

    private static class ImportStats {
        private int processedFileCount;
        private int newFoodItemCount;
        private int supplementedFieldCount;
        private int skippedItemCount;
        private int duplicateItemCount;
        private int exceptionCount;
        private final Map<String, Integer> sourceStats = new LinkedHashMap<>();
    }

    private static class ItemCache {
        private final Map<String, FoodItem> byCode = new LinkedHashMap<>();
        private final Map<String, FoodItem> byNormalizedName = new LinkedHashMap<>();

        private void put(FoodItem item) {
            if (item.getFoodCode() != null && !item.getFoodCode().isBlank()) {
                byCode.put(item.getFoodCode(), item);
            }
            if (item.getFoodName() != null && !item.getFoodName().isBlank()) {
                byNormalizedName.putIfAbsent(NAME_NORMALIZE_PATTERN.matcher(item.getFoodName().toLowerCase(Locale.ROOT)).replaceAll(""), item);
            }
        }
    }

    private static class CategoryCache {
        private final Map<String, FoodCategory> byCode = new LinkedHashMap<>();
    }

    private record ExcelFoodRow(
            String foodCode,
            String foodName,
            BigDecimal water,
            BigDecimal edibleRatio,
            BigDecimal energyKcal,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal carbohydrate,
            BigDecimal dietaryFiber,
            BigDecimal sodium,
            BigDecimal vitaminA,
            BigDecimal vitaminB1,
            BigDecimal vitaminB2,
            BigDecimal vitaminC,
            BigDecimal vitaminE,
            BigDecimal calcium,
            BigDecimal iron,
            BigDecimal zinc,
            String fileName,
            String sheetName,
            String level2Name,
            String level1Name
    ) {
        private Map<String, Object> toPayloadMap() {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("foodCode", foodCode);
            payload.put("foodName", foodName);
            payload.put("edibleRatio", edibleRatio);
            payload.put("energyKcal", energyKcal);
            payload.put("protein", protein);
            payload.put("fat", fat);
            payload.put("carbohydrate", carbohydrate);
            payload.put("dietaryFiber", dietaryFiber);
            payload.put("sodium", sodium);
            payload.put("vitaminA", vitaminA);
            payload.put("vitaminB1", vitaminB1);
            payload.put("vitaminB2", vitaminB2);
            payload.put("vitaminC", vitaminC);
            payload.put("vitaminE", vitaminE);
            payload.put("calcium", calcium);
            payload.put("iron", iron);
            payload.put("zinc", zinc);
            payload.put("fileName", fileName);
            payload.put("sheetName", sheetName);
            payload.put("level1Name", level1Name);
            payload.put("level2Name", level2Name);
            return payload;
        }
    }
}
