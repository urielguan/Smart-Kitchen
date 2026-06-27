package com.xykj.recipe.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.xykj.common.ai.AiModuleCode;
import com.xykj.common.ai.AiServiceType;
import com.xykj.common.ai.entity.AiServiceConfig;
import com.xykj.common.ai.model.AiTextGenerateResult;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.ai.service.AiServiceConfigService;
import com.xykj.common.ai.service.OpenAiCompatibleService;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.common.service.DataScopeService.DataScopeResult;
import com.xykj.recipe.config.NutritionTargetConfig;
import com.xykj.recipe.config.NutritionTargetConfig.GroupNutritionTarget;
import com.xykj.recipe.controller.RecipePlanController.AdjustmentDTO;
import com.xykj.recipe.dto.RecipePlanCreateDTO;
import com.xykj.recipe.dto.RecipePlanImportDTO;
import com.xykj.recipe.dto.RecipePlanImportRecordResultDTO;
import com.xykj.recipe.dto.RecipePlanImportResultDTO;
import com.xykj.recipe.dto.RecipePlanItemImportDTO;
import com.xykj.recipe.dto.RecipePlanQueryDTO;
import com.xykj.recipe.dto.StockValidationDTO;
import com.xykj.recipe.dto.RecipePlanCreateDTO.MealScheduleDTO;
import com.xykj.recipe.dto.RecipePlanCreateDTO.RecipePlanItemDTO;
import com.xykj.recipe.entity.CookTask;
import com.xykj.recipe.entity.Recipe;
import com.xykj.recipe.entity.RecipeCategory;
import com.xykj.recipe.entity.RecipeIngredient;
import com.xykj.recipe.entity.RecipePlan;
import com.xykj.recipe.entity.RecipePlanAdjustment;
import com.xykj.recipe.entity.RecipePlanAuditLog;
import com.xykj.recipe.entity.RecipePlanItem;
import com.xykj.recipe.mapper.RecipeCategoryMapper;
import com.xykj.recipe.mapper.RecipePlanAuditLogMapper;
import com.xykj.recipe.mapper.RecipeIngredientMapper;
import com.xykj.recipe.mapper.RecipeMapper;
import com.xykj.recipe.mapper.RecipePlanAdjustmentMapper;
import com.xykj.recipe.mapper.RecipePlanItemMapper;
import com.xykj.recipe.mapper.RecipePlanMapper;
import com.xykj.recipe.service.CookTaskService;
import com.xykj.recipe.service.InventoryValidationService;
import com.xykj.recipe.service.RecipePlanService;
import com.xykj.recipe.service.RecipeNutritionSupportService;
import com.xykj.recipe.vo.*;
import com.xykj.recipe.vo.RecipePlanAdjustmentDetailVO.AdjustItemVO;
import com.xykj.recipe.vo.RecipePlanDetailVO.RecipePlanItemVO;
import com.xykj.recipe.vo.RecipeVO.NutritionInfoVO;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Generated;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class RecipePlanServiceImpl extends ServiceImpl<RecipePlanMapper, RecipePlan> implements RecipePlanService {
   @Generated
   private static final Logger log = LoggerFactory.getLogger(RecipePlanServiceImpl.class);
   private static final ObjectMapper LENIENT_AI_JSON_MAPPER = JsonMapper.builder()
      .enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS)
      .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
      .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
      .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
      .build();
   private static final Set<String> BUILTIN_CATEGORY_CODES = Set.of("STAPLE", "MAIN_DISH", "SOUP", "SIDE_DISH", "DESSERT");
   private static final Set<String> STOCK_RECHECK_ACTIVE_STATUSES = Set.of("pending", "approved");
   private static final List<Integer> STOCK_RECHECK_DAYS = List.of(7, 3, 1);
   private static final LocalTime STOCK_RECHECK_TIME = LocalTime.of(9, 0);
   private static final DateTimeFormatter PLAN_IMPORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
   private static final String PLAN_IMPORT_ERROR_DIR = System.getProperty("java.io.tmpdir") + "/recipe-plan-import-errors/";
   private static final String PLAN_IMPORT_PLAN_SHEET = "\u8BA1\u5212\u4FE1\u606F";
   private static final String PLAN_IMPORT_ITEM_SHEET = "\u83DC\u8C31\u660E\u7EC6";
   private static final String PLAN_IMPORT_GUIDE_SHEET = "\u586B\u5199\u8BF4\u660E";
   private static final String[] PLAN_IMPORT_MEAL_TYPE_DISPLAY_OPTIONS = new String[]{"\u65E9\u9910", "\u5348\u9910", "\u665A\u9910", "\u591C\u5BB5"};
   private static final String[] PLAN_IMPORT_TARGET_GROUP_DISPLAY_OPTIONS = new String[]{"\u666E\u901A\u6210\u4EBA", "\u8001\u5E74\u4EBA", "\u513F\u7AE5", "\u9752\u5C11\u5E74", "\u75C5\u60A3", "\u4F53\u529B\u52B3\u52A8\u8005"};
   private static final String ADJUSTMENT_MODE_FUTURE_ONLY = "future_only";
   private static final String ADJUSTMENT_MODE_HISTORY_MIXED = "history_mixed";
   private static final Set<String> VALID_MEAL_TYPES = Set.of("breakfast", "lunch", "dinner", "supper");
   private static final Set<String> VALID_TARGET_GROUPS = Set.of("adult", "elderly", "child", "teenager", "patient", "worker");
   private static final Set<String> IMPORT_DIRECT_UPDATE_STATUSES = Set.of("draft");
   private static final Set<String> IMPORT_FORBIDDEN_UPDATE_STATUSES = Set.of("pending", "completed");
   private static final Map<String, String> PLAN_IMPORT_MEAL_TYPE_ALIASES = Map.ofEntries(
      Map.entry("\u65E9\u9910", "breakfast"),
      Map.entry("breakfast", "breakfast"),
      Map.entry("\u5348\u9910", "lunch"),
      Map.entry("lunch", "lunch"),
      Map.entry("\u665A\u9910", "dinner"),
      Map.entry("dinner", "dinner"),
      Map.entry("\u591C\u5BB5", "supper"),
      Map.entry("\u5BB5\u591C", "supper"),
      Map.entry("\u52A0\u9910", "supper"),
      Map.entry("supper", "supper")
   );
   private static final Map<String, String> PLAN_IMPORT_TARGET_GROUP_ALIASES = Map.ofEntries(
      Map.entry("\u666E\u901A\u6210\u4EBA", "adult"),
      Map.entry("\u6210\u4EBA", "adult"),
      Map.entry("adult", "adult"),
      Map.entry("\u8001\u5E74\u4EBA", "elderly"),
      Map.entry("\u8001\u4EBA", "elderly"),
      Map.entry("elderly", "elderly"),
      Map.entry("\u513F\u7AE5", "child"),
      Map.entry("child", "child"),
      Map.entry("\u9752\u5C11\u5E74", "teenager"),
      Map.entry("teenager", "teenager"),
      Map.entry("\u75C5\u60A3", "patient"),
      Map.entry("\u75C5\u4EBA", "patient"),
      Map.entry("patient", "patient"),
      Map.entry("\u4F53\u529B\u52B3\u52A8\u8005", "worker"),
      Map.entry("worker", "worker")
   );
   private final RecipePlanItemMapper planItemMapper;
   private final RecipeMapper recipeMapper;
   private final RecipeCategoryMapper categoryMapper;
   private final RecipeIngredientMapper ingredientMapper;
   private final RecipePlanAdjustmentMapper adjustmentMapper;
   private final RecipePlanAuditLogMapper auditLogMapper;
   private final InventoryValidationService inventoryValidationService;
   private final CookTaskService cookTaskService;
   private final DataScopeService dataScopeService;
   private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
   private final ObjectMapper objectMapper;
   private final NutritionTargetConfig nutritionTargetConfig;
   private final JdbcTemplate jdbcTemplate;
   private final TransactionTemplate transactionTemplate;
   private final AiServiceConfigService aiServiceConfigService;
   private final OpenAiCompatibleService openAiCompatibleService;

   @Lazy
   @Autowired
   private RecipePlanService selfProxy;

   @DataScope
   public Page<RecipePlanVO> list(RecipePlanQueryDTO query) {
      Page<RecipePlan> pageDto = new Page(query.getPageNum().intValue(), query.getPageSize().intValue());
      LambdaQueryWrapper<RecipePlan> wrapper = this.buildPlanQueryWrapper(query);
      Page<RecipePlan> page = (Page<RecipePlan>)this.page(pageDto, wrapper);
      Map<Long, RecipePlanAdjustment> latestAdjustmentMap = this.loadLatestAdjustmentMap(page.getRecords().stream().<Long>map(RecipePlan::getId).toList());
      List<RecipePlanVO> voList = page.getRecords()
         .stream()
         .map(plan -> this.convertToVO(plan, latestAdjustmentMap.get(plan.getId())))
         .collect(Collectors.toList());
      Page<RecipePlanVO> resultPage = new Page(query.getPageNum().intValue(), query.getPageSize().intValue());
      resultPage.setRecords(voList);
      resultPage.setTotal(page.getTotal());
      return resultPage;
   }

   private final RecipeNutritionSupportService recipeNutritionSupportService;

   private LambdaQueryWrapper<RecipePlan> buildPlanQueryWrapper(RecipePlanQueryDTO query) {
      LambdaQueryWrapper<RecipePlan> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(query.getPlanDate() != null, RecipePlan::getPlanDate, query.getPlanDate());
      wrapper.ge(query.getPlanDateStart() != null, RecipePlan::getPlanDate, query.getPlanDateStart());
      wrapper.le(query.getPlanDateEnd() != null, RecipePlan::getPlanDate, query.getPlanDateEnd());
      wrapper.like(StrUtil.isNotBlank(query.getPlanCode()), RecipePlan::getPlanCode, query.getPlanCode());
      wrapper.ge(query.getStartDateStart() != null, RecipePlan::getStartDate, query.getStartDateStart());
      wrapper.le(query.getStartDateEnd() != null, RecipePlan::getStartDate, query.getStartDateEnd());
      wrapper.eq(StrUtil.isNotBlank(query.getStatus()), RecipePlan::getStatus, query.getStatus());
      wrapper.eq(query.getOrgId() != null, RecipePlan::getOrgId, query.getOrgId());
      wrapper.in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), RecipePlan::getOrgId, query.getOrgIds());
      if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
         wrapper.isNull(RecipePlan::getId);
      }

      if (StrUtil.isNotBlank(query.getOrgName())) {
         try {
            List<Long> matchedOrgIds = this.jdbcTemplate
               .queryForList(
                  "SELECT id FROM sys_organization WHERE org_name LIKE ? AND deleted = 0", Long.class, new Object[]{"%" + query.getOrgName().trim() + "%"}
               );
            if (!matchedOrgIds.isEmpty()) {
               wrapper.in(RecipePlan::getOrgId, matchedOrgIds);
            } else {
               wrapper.isNull(RecipePlan::getId);
            }
         } catch (Exception var4) {
            log.warn("闂傚倷绀佸﹢閬嶁€﹂崼銉嬪洭妫冨ù铏洴椤㈡鎷呴悷鏉垮Τ婵犲痉鏉库偓鎰板磻閹剧粯鐓欐い鏃囧Г缁€鍫㈢磼椤旇姤顥堥柟顔ㄥ洤閱囬柣鏃囨腹閾忓海绱撻崒娆愵樂缂佸苯鐖煎畷纭呫亹閹烘嚦锕傛煙鐎电校閻庢艾顭烽弻鏇熷緞濡櫣浠紓浣插亾濠㈣埖鍔楅崣鎾绘煕閵夈垺娅嗘繛鍛礋閺岋繝宕卞Ο鍝勵潕闂佸綊顥撴繛鈧┑锛勫厴閺佸倿宕愰悢绋款仼婵? {}", var4.getMessage());
         }
      }

      if (StrUtil.isNotBlank(query.getMealType())) {
         List<Long> matchedPlanIds = this.planItemMapper.selectList(
            new LambdaQueryWrapper<RecipePlanItem>()
               .eq(RecipePlanItem::getMealType, query.getMealType())
               .select(RecipePlanItem::getPlanId)
         ).stream().map(RecipePlanItem::getPlanId).distinct().toList();
         if (!matchedPlanIds.isEmpty()) {
            wrapper.in(RecipePlan::getId, matchedPlanIds);
         } else {
            wrapper.isNull(RecipePlan::getId);
         }
      }

      wrapper.orderByDesc(RecipePlan::getCreatedAt);
      return wrapper;
   }

   @DataScope
   public void exportPlans(RecipePlanQueryDTO query, HttpServletResponse response) {
      List<RecipePlan> plans = this.list(this.buildPlanQueryWrapper(query));
      List<Long> planIds = plans.stream().map(RecipePlan::getId).toList();
      Map<Long, List<RecipePlanItem>> planItemsMap = this.loadPlanItemsByPlanId(planIds);
      Map<Long, RecipePlanAdjustment> latestAdjustmentMap = this.loadLatestAdjustmentMap(planIds);

      try (Workbook workbook = new XSSFWorkbook()) {
         CellStyle headerStyle = this.createExportHeaderStyle(workbook);
         this.buildPlanExportSummarySheet(workbook, headerStyle, plans, planItemsMap, latestAdjustmentMap);
         this.buildPlanExportDetailSheet(workbook, headerStyle, plans, planItemsMap);
         response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
         response.setCharacterEncoding(StandardCharsets.UTF_8.name());
         String fileName = URLEncoder.encode(
            "\u83DC\u8C31\u8BA1\u5212\u5217\u8868_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), StandardCharsets.UTF_8
         ).replaceAll("\\+", "%20");
         response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
         workbook.write(response.getOutputStream());
      } catch (IOException e) {
         throw new RuntimeException("\u5BFC\u51FA\u83DC\u8C31\u8BA1\u5212\u5931\u8D25", e);
      }
   }

   private CellStyle createExportHeaderStyle(Workbook workbook) {
      CellStyle headerStyle = workbook.createCellStyle();
      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      headerStyle.setFont(headerFont);
      return headerStyle;
   }

   private void buildPlanExportSummarySheet(
      Workbook workbook,
      CellStyle headerStyle,
      List<RecipePlan> plans,
      Map<Long, List<RecipePlanItem>> planItemsMap,
      Map<Long, RecipePlanAdjustment> latestAdjustmentMap
   ) {
      Sheet sheet = workbook.createSheet("\u83DC\u8C31\u8BA1\u5212");
      String[] headers = new String[]{
         "\u8BA1\u5212\u5355\u53F7",
         "\u8BA1\u5212\u65E5\u671F",
         "\u5F00\u59CB\u65E5\u671F",
         "\u7ED3\u675F\u65E5\u671F",
         "\u9910\u6B21",
         "\u5C31\u9910\u4EBA\u6570",
         "\u76EE\u6807\u4EBA\u7FA4",
         "\u72B6\u6001",
         "\u83DC\u8C31\u6570",
         "\u83DC\u8C31\u540D\u79F0",
         "\u9884\u8BA1\u6210\u672C(\u5143)",
         "\u8C03\u6574\u72B6\u6001",
         "\u521B\u5EFA\u65F6\u95F4"
      };
      this.createExportHeaderRow(sheet, headers, headerStyle);
      int rowNum = 1;

      for (RecipePlan plan : plans) {
         Row row = sheet.createRow(rowNum++);
         List<RecipePlanItem> items = planItemsMap.getOrDefault(plan.getId(), List.of());
         RecipePlanAdjustment latestAdjustment = latestAdjustmentMap.get(plan.getId());
         this.setCellValue(row, 0, plan.getPlanCode());
         this.setCellValue(row, 1, this.formatLocalDate(plan.getPlanDate()));
         this.setCellValue(row, 2, this.formatLocalDate(plan.getStartDate()));
         this.setCellValue(row, 3, this.formatLocalDate(plan.getEndDate()));
         RecipePlanVO planVO = this.convertToVO(plan, latestAdjustment);
         this.setCellValue(row, 4, planVO.getMealDisplayName());
         this.setCellValue(row, 5, planVO.getExpectedCountDisplay());
         this.setCellValue(row, 6, this.getTargetGroupDisplayName(plan.getTargetGroup()));
         this.setCellValue(row, 7, this.getStatusName(plan.getStatus()));
         this.setCellValue(row, 8, String.valueOf(items.size()));
         this.setCellValue(row, 9, this.buildExportRecipeNames(items));
         this.setCellValue(row, 10, plan.getEstimatedCost() == null ? "" : plan.getEstimatedCost().toPlainString());
         this.setCellValue(row, 11, latestAdjustment == null ? "" : this.getAdjustmentStatusName(latestAdjustment.getStatus()));
         this.setCellValue(row, 12, plan.getCreatedAt() == null ? "" : plan.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
      }

      this.autoSizeSheetColumns(sheet, headers.length);
   }

   private void buildPlanExportDetailSheet(
      Workbook workbook, CellStyle headerStyle, List<RecipePlan> plans, Map<Long, List<RecipePlanItem>> planItemsMap
   ) {
      Sheet sheet = workbook.createSheet("\u83DC\u8C31\u660E\u7EC6");
      String[] headers = new String[]{
         "\u8BA1\u5212\u5355\u53F7",
         "\u8BA1\u5212\u65E5\u671F",
         "\u5F00\u59CB\u65E5\u671F",
         "\u7ED3\u675F\u65E5\u671F",
         "\u9910\u6B21",
         "\u5C31\u9910\u4EBA\u6570",
         "\u76EE\u6807\u4EBA\u7FA4",
         "\u83DC\u8C31\u7F16\u7801",
         "\u83DC\u8C31\u540D\u79F0",
         "\u83DC\u8C31\u5206\u7C7B",
         "\u8BA1\u5212\u4EFD\u6570",
         "\u6392\u5E8F",
         "\u5907\u6CE8"
      };
      this.createExportHeaderRow(sheet, headers, headerStyle);
      int rowNum = 1;

      for (RecipePlan plan : plans) {
         for (RecipePlanItem item : planItemsMap.getOrDefault(plan.getId(), List.of())) {
            Row row = sheet.createRow(rowNum++);
            this.setCellValue(row, 0, plan.getPlanCode());
            this.setCellValue(row, 1, this.formatLocalDate(plan.getPlanDate()));
            this.setCellValue(row, 2, this.formatLocalDate(plan.getStartDate()));
            this.setCellValue(row, 3, this.formatLocalDate(plan.getEndDate()));
            this.setCellValue(row, 4, StrUtil.blankToDefault(item.getMealName(), this.getMealTypeDisplayName(item.getMealType())));
            this.setCellValue(row, 5, item.getMealExpectedCount() == null ? "" : String.valueOf(item.getMealExpectedCount()));
            this.setCellValue(row, 6, this.getTargetGroupDisplayName(plan.getTargetGroup()));
            this.setCellValue(row, 7, item.getRecipeCode());
            this.setCellValue(row, 8, item.getRecipeName());
            this.setCellValue(row, 9, item.getCategoryName());
            this.setCellValue(row, 10, item.getPlannedServings() == null ? "" : String.valueOf(item.getPlannedServings()));
            this.setCellValue(row, 11, item.getSortOrder() == null ? "" : String.valueOf(item.getSortOrder()));
            this.setCellValue(row, 12, item.getRemark());
         }
      }

      this.autoSizeSheetColumns(sheet, headers.length);
   }

   private void createExportHeaderRow(Sheet sheet, String[] headers, CellStyle headerStyle) {
      Row headerRow = sheet.createRow(0);

      for (int i = 0; i < headers.length; i++) {
         Cell cell = headerRow.createCell(i);
         cell.setCellValue(headers[i]);
         cell.setCellStyle(headerStyle);
      }
   }

   private void autoSizeSheetColumns(Sheet sheet, int columnCount) {
      for (int i = 0; i < columnCount; i++) {
         sheet.autoSizeColumn(i);
      }
   }

   @Override
   @DataScope
   public void exportAdjustments(RecipePlanQueryDTO query, HttpServletResponse response) {
      // 1. Build query (reuse listAdjustments logic without pagination)
      LambdaQueryWrapper<RecipePlanAdjustment> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(query.getPlanId() != null, RecipePlanAdjustment::getPlanId, query.getPlanId())
         .eq(StrUtil.isNotBlank(query.getStatus()), RecipePlanAdjustment::getStatus, query.getStatus())
         .eq(StrUtil.isNotBlank(query.getAdjustType()), RecipePlanAdjustment::getAdjustType, query.getAdjustType())
         .ge(query.getPlanDateStart() != null, RecipePlanAdjustment::getCreatedAt,
            query.getPlanDateStart() != null ? query.getPlanDateStart().atStartOfDay() : null)
         .le(query.getPlanDateEnd() != null, RecipePlanAdjustment::getCreatedAt,
            query.getPlanDateEnd() != null ? query.getPlanDateEnd().plusDays(1L).atStartOfDay() : null)
         .eq(query.getOrgId() != null, RecipePlanAdjustment::getOrgId, query.getOrgId())
         .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(),
            RecipePlanAdjustment::getOrgId, query.getOrgIds())
         .orderByDesc(RecipePlanAdjustment::getCreatedAt);

      if (StrUtil.isNotBlank(query.getPlanCode())) {
         List<Long> matchedPlanIds = this.list(new LambdaQueryWrapper<RecipePlan>()
               .like(RecipePlan::getPlanCode, query.getPlanCode()))
            .stream().map(RecipePlan::getId).collect(Collectors.toList());
         if (!matchedPlanIds.isEmpty()) {
            wrapper.in(RecipePlanAdjustment::getPlanId, matchedPlanIds);
         } else {
            wrapper.isNull(RecipePlanAdjustment::getId);
         }
      }

      if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
         wrapper.isNull(RecipePlanAdjustment::getId);
      }

      List<RecipePlanAdjustment> adjustments = this.adjustmentMapper.selectList(wrapper);

      // 2. Batch resolve plans (avoid N+1)
      Set<Long> planIds = adjustments.stream()
         .map(RecipePlanAdjustment::getPlanId)
         .filter(Objects::nonNull)
         .collect(Collectors.toSet());
      Map<Long, RecipePlan> planMap = planIds.isEmpty() ? Map.of()
         : this.listByIds(planIds).stream().collect(Collectors.toMap(RecipePlan::getId, p -> p, (a, b) -> a));

      // 3. Convert to VO for display names
      List<RecipePlanAdjustmentVO> voList = adjustments.stream()
         .map(this::convertAdjustmentToVO)
         .collect(Collectors.toList());

      // 4. Build workbook
      try (Workbook workbook = new XSSFWorkbook()) {
         CellStyle headerStyle = this.createExportHeaderStyle(workbook);
         this.buildAdjustmentExportSummarySheet(workbook, headerStyle, voList);
         this.buildAdjustmentExportDetailSheet(workbook, headerStyle, adjustments, planMap);

         response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
         response.setCharacterEncoding(StandardCharsets.UTF_8.name());
         String fileName = URLEncoder.encode(
            "调整申请列表_" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE), StandardCharsets.UTF_8
         ).replaceAll("\\+", "%20");
         response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
         workbook.write(response.getOutputStream());
      } catch (IOException e) {
         throw new RuntimeException("导出调整申请失败", e);
      }
   }

   private void buildAdjustmentExportSummarySheet(
      Workbook workbook, CellStyle headerStyle, List<RecipePlanAdjustmentVO> voList
   ) {
      Sheet sheet = workbook.createSheet("调整列表");
      String[] headers = {
         "调整单号", "计划单号", "计划日期", "调整类型", "调整原因",
         "申请状态", "申请人", "申请时间", "审核人", "审核时间", "审核意见"
      };
      this.createExportHeaderRow(sheet, headers, headerStyle);
      int rowNum = 1;

      for (RecipePlanAdjustmentVO vo : voList) {
         Row row = sheet.createRow(rowNum++);
         this.setCellValue(row, 0, vo.getAdjustCode());
         this.setCellValue(row, 1, vo.getPlanCode());
         this.setCellValue(row, 2, vo.getPlanDate() != null ? vo.getPlanDate().toString() : "");
         this.setCellValue(row, 3, vo.getAdjustTypeName());
         this.setCellValue(row, 4, vo.getAdjustReason());
         this.setCellValue(row, 5, vo.getStatusName());
         this.setCellValue(row, 6, vo.getAppliedByName());
         this.setCellValue(row, 7, vo.getAppliedAt() != null
            ? vo.getAppliedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
         this.setCellValue(row, 8, vo.getAuditedByName());
         this.setCellValue(row, 9, vo.getAuditedAt() != null
            ? vo.getAuditedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "");
         this.setCellValue(row, 10, vo.getAuditRemark());
      }

      this.autoSizeSheetColumns(sheet, headers.length);
   }

   private void buildAdjustmentExportDetailSheet(
      Workbook workbook, CellStyle headerStyle,
      List<RecipePlanAdjustment> adjustments, Map<Long, RecipePlan> planMap
   ) {
      Sheet sheet = workbook.createSheet("调整明细");
      String[] headers = {"调整单号", "计划单号", "调整项", "调整前", "调整后"};
      this.createExportHeaderRow(sheet, headers, headerStyle);
      int rowNum = 1;

      for (RecipePlanAdjustment adjustment : adjustments) {
         String adjustCode = adjustment.getAdjustCode();
         RecipePlan plan = planMap.get(adjustment.getPlanId());
         String planCode = plan != null ? plan.getPlanCode() : "";

         List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items = this.parseAdjustItems(adjustment);
         for (RecipePlanAdjustmentDetailVO.AdjustItemVO item : items) {
            Row row = sheet.createRow(rowNum++);
            this.setCellValue(row, 0, adjustCode);
            this.setCellValue(row, 1, planCode);
            this.setCellValue(row, 2, item.getFieldLabel());
            this.setCellValue(row, 3, item.getBeforeValue());
            this.setCellValue(row, 4, item.getAfterValue());
         }
      }

      this.autoSizeSheetColumns(sheet, headers.length);
   }

   private Map<Long, List<RecipePlanItem>> loadPlanItemsByPlanId(List<Long> planIds) {
      if (planIds == null || planIds.isEmpty()) {
         return Map.of();
      } else {
         List<RecipePlanItem> items = this.planItemMapper.selectList(
            new LambdaQueryWrapper<RecipePlanItem>().in(RecipePlanItem::getPlanId, planIds)
               .orderByAsc(RecipePlanItem::getPlanId)
               .orderByAsc(RecipePlanItem::getSortOrder)
               .orderByAsc(RecipePlanItem::getId)
         );
         return items.stream().collect(Collectors.groupingBy(RecipePlanItem::getPlanId, LinkedHashMap::new, Collectors.toList()));
      }
   }

   private String buildExportRecipeNames(List<RecipePlanItem> items) {
      if (items == null || items.isEmpty()) {
         return "";
      } else {
         return items.stream()
            .map(item -> StrUtil.blankToDefault(item.getRecipeName(), item.getRecipeCode()))
            .filter(StrUtil::isNotBlank)
            .distinct()
            .collect(Collectors.joining("\u3001"));
      }
   }

   public void downloadImportTemplate(HttpServletResponse response) {
      try (Workbook workbook = new XSSFWorkbook()) {
         this.buildPlanImportGuideSheet(workbook);
         this.buildPlanImportPlanSheet(workbook);
         this.buildPlanImportItemSheet(workbook);
         response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
         response.setCharacterEncoding(StandardCharsets.UTF_8.name());
         String fileName = URLEncoder.encode("\u83DC\u8C31\u8BA1\u5212\u5BFC\u5165\u6A21\u677F", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
         response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + fileName + ".xlsx");
         workbook.write(response.getOutputStream());
      } catch (IOException e) {
         throw new RuntimeException("\u4E0B\u8F7D\u83DC\u8C31\u8BA1\u5212\u5BFC\u5165\u6A21\u677F\u5931\u8D25", e);
      }
   }

   public RecipePlanImportResultDTO importPlans(MultipartFile file) {
      if (file == null || file.isEmpty()) {
         throw BizException.badRequest("\u5BFC\u5165\u6587\u4EF6\u4E0D\u80FD\u4E3A\u7A7A");
      } else {
         List<RecipePlanImportDTO> errorList = new ArrayList<>();
         List<RecipePlanImportRecordResultDTO> recordResults = new ArrayList<>();

         try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            List<RecipePlanImportDTO> plans = this.parsePlanSheet(workbook);
            Map<Integer, List<RecipePlanItemImportDTO>> itemMap = this.parseItemSheet(workbook);
            Map<String, Recipe> recipeCodeMap = this.loadImportRecipeCodeMap(itemMap);
            Set<Integer> duplicateSeqNos = this.findDuplicatePlanSeqNos(plans);
            int total = plans.size();
            int successCount = 0;
            int createdCount = 0;
            int updatedCount = 0;
            int adjustmentCreatedCount = 0;
            int skippedCount = 0;
            int failCount = 0;

            for (RecipePlanImportDTO planDTO : plans) {
               String validationError = this.validatePlanRow(planDTO, itemMap, recipeCodeMap, duplicateSeqNos);
               if (validationError != null) {
                  planDTO.setErrorMessage(validationError);
                  errorList.add(planDTO);
                  recordResults.add(this.buildImportRecord(planDTO, null, "failed", validationError));
                  failCount++;
               } else {
                  List<RecipePlanItemImportDTO> items = itemMap.getOrDefault(planDTO.getSeqNo(), List.of());

                  try {
                     RecipePlanImportRecordResultDTO record = (RecipePlanImportRecordResultDTO)this.transactionTemplate
                        .execute(status -> this.importSinglePlan(planDTO, items, recipeCodeMap));
                     recordResults.add(record);
                     switch (record.getAction()) {
                        case "created":
                           successCount++;
                           createdCount++;
                           break;
                        case "updated":
                           successCount++;
                           updatedCount++;
                           break;
                        case "adjustment_created":
                           successCount++;
                           adjustmentCreatedCount++;
                           break;
                        case "skipped_no_change":
                           successCount++;
                           skippedCount++;
                           break;
                        default:
                           throw new IllegalStateException("\u672A\u77E5\u7684\u5BFC\u5165\u52A8\u4F5C: " + record.getAction());
                     }
                  } catch (Exception e) {
                     String errorMessage = StrUtil.blankToDefault(e.getMessage(), "\u5BFC\u5165\u5931\u8D25");
                     planDTO.setErrorMessage(errorMessage);
                     errorList.add(planDTO);
                     recordResults.add(this.buildImportRecord(planDTO, null, "failed", errorMessage));
                     failCount++;
                  }
               }
            }

            String errorFileUrl = errorList.isEmpty() ? null : this.generatePlanImportErrorFile(errorList);
            RecipePlanImportResultDTO result = new RecipePlanImportResultDTO();
            result.setTotal(total);
            result.setSuccessCount(successCount);
            result.setCreatedCount(createdCount);
            result.setUpdatedCount(updatedCount);
            result.setAdjustmentCreatedCount(adjustmentCreatedCount);
            result.setSkippedCount(skippedCount);
            result.setFailCount(failCount);
            result.setHasErrors(!errorList.isEmpty());
            result.setErrorFileUrl(errorFileUrl);
            result.setRecords(recordResults);
            return result;
         } catch (IOException e) {
            throw BizException.badRequest("\u89E3\u6790\u5BFC\u5165\u6587\u4EF6\u5931\u8D25\uFF0C\u8BF7\u786E\u8BA4\u6587\u4EF6\u4E3A\u6700\u65B0\u6A21\u677F\u4E14\u683C\u5F0F\u6B63\u786E");
         }
      }
   }

   public void downloadImportErrorFile(String fileName, HttpServletResponse response) {
      try {
         if (StrUtil.isBlank(fileName) || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw BizException.badRequest("\u9519\u8BEF\u6587\u4EF6\u540D\u4E0D\u5408\u6CD5");
         }

         File file = new File(PLAN_IMPORT_ERROR_DIR, fileName);
         if (!file.getCanonicalPath().startsWith(new File(PLAN_IMPORT_ERROR_DIR).getCanonicalPath())) {
            throw BizException.badRequest("\u9519\u8BEF\u6587\u4EF6\u8DEF\u5F84\u4E0D\u5408\u6CD5");
         }

         if (!file.exists()) {
            throw BizException.notFound("\u9519\u8BEF\u6587\u4EF6\u4E0D\u5B58\u5728\u6216\u5DF2\u8FC7\u671F");
         }

         response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
         response.setCharacterEncoding(StandardCharsets.UTF_8.name());
         response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20"));
         try (FileInputStream fis = new FileInputStream(file)) {
            fis.transferTo(response.getOutputStream());
         }
      } catch (IOException e) {
         throw new RuntimeException("\u4E0B\u8F7D\u5BFC\u5165\u9519\u8BEF\u6587\u4EF6\u5931\u8D25", e);
      }
   }

   private void buildPlanImportGuideSheet(Workbook workbook) {
      Sheet sheet = workbook.createSheet(PLAN_IMPORT_GUIDE_SHEET);
      String[] guideLines = new String[]{
         "1. Sheet\u3010\u8BA1\u5212\u4FE1\u606F\u3011\u4E00\u884C\u8868\u793A\u4E00\u6761\u83DC\u8C31\u8BA1\u5212\uFF0CSheet\u3010\u83DC\u8C31\u660E\u7EC6\u3011\u53EF\u4E3A\u540C\u4E00\u8BA1\u5212\u586B\u5199\u591A\u9053\u83DC\u8C31\u3002",
         "2. \u5FC5\u586B\u9879\uFF1A\u8BA1\u5212\u65E5\u671F\u3001\u5B9E\u65BD\u5F00\u59CB\u65E5\u671F\u3001\u5B9E\u65BD\u7ED3\u675F\u65E5\u671F\u3001\u9910\u6B21\u3001\u5C31\u9910\u4EBA\u6570\uFF1B\u76EE\u6807\u4EBA\u7FA4\u4E3A\u9009\u586B\u3002",
         "3. \u4E24\u4E2A Sheet \u901A\u8FC7\u201C\u5BFC\u5165\u5E8F\u53F7\u201D\u5173\u8054\uFF0C\u5BFC\u5165\u5E8F\u53F7\u5728\u672C\u6B21\u5BFC\u5165\u6587\u4EF6\u5185\u5FC5\u987B\u552F\u4E00\u3002",
         "4. \u83DC\u8C31\u53EA\u80FD\u586B\u5199\u83DC\u8C31\u5E93\u7BA1\u7406\u4E2D\u5DF2\u5B58\u5728\u4E14\u72B6\u6001\u4E3A\u542F\u7528\u7684\u201C\u83DC\u8C31\u7F16\u7801\u201D\u3002",
         "5. \u8BA1\u5212\u5355\u53F7\u53EF\u9009\uFF1A\u586B\u5199\u65F6\u6309\u8BA1\u5212\u5355\u53F7\u66F4\u65B0\uFF1B\u4E0D\u586B\u5199\u65F6\u4E00\u5F8B\u65B0\u589E\u8BA1\u5212\u3002",
         "6. \u5DF2\u5BA1\u6838\u8BA1\u5212\u5982\u5B58\u5728\u6539\u52A8\uFF0C\u4F1A\u81EA\u52A8\u751F\u6210\u5F85\u5BA1\u6838\u7684\u8C03\u6574\u7533\u8BF7\uFF1B\u8349\u7A3F/\u5DF2\u62D2\u7EDD\u8BA1\u5212\u4F1A\u76F4\u63A5\u66F4\u65B0\uFF1B\u5F85\u5BA1\u6838/\u5DF2\u5B8C\u6210\u8BA1\u5212\u5BFC\u5165\u5931\u8D25\u3002",
         "7. \u652F\u6301\u4E2D\u6587\u6216\u82F1\u6587\u9910\u6B21\uFF1A\u65E9\u9910/breakfast\u3001\u5348\u9910/lunch\u3001\u665A\u9910/dinner\u3001\u591C\u5BB5/supper\u3002",
         "8. \u652F\u6301\u4E2D\u6587\u6216\u82F1\u6587\u76EE\u6807\u4EBA\u7FA4\uFF1A\u666E\u901A\u6210\u4EBA/adult\u3001\u8001\u5E74\u4EBA/elderly\u3001\u513F\u7AE5/child\u3001\u9752\u5C11\u5E74/teenager\u3001\u75C5\u60A3/patient\u3001\u4F53\u529B\u52B3\u52A8\u8005/worker\uFF1B\u8BE5\u5B57\u6BB5\u53EF\u7559\u7A7A\u3002"
      };

      for (int i = 0; i < guideLines.length; i++) {
         Row row = sheet.createRow(i);
         row.createCell(0).setCellValue(guideLines[i]);
      }

      sheet.autoSizeColumn(0);
   }

   private void buildPlanImportPlanSheet(Workbook workbook) {
      Sheet sheet = workbook.createSheet(PLAN_IMPORT_PLAN_SHEET);
      String[] headers = new String[]{"\u5BFC\u5165\u5E8F\u53F7*", "\u8BA1\u5212\u65E5\u671F*", "\u9910\u6B21*", "\u5C31\u9910\u4EBA\u6570*", "\u76EE\u6807\u4EBA\u7FA4", "\u5B9E\u65BD\u5F00\u59CB\u65E5\u671F*", "\u5B9E\u65BD\u7ED3\u675F\u65E5\u671F*", "\u8BA1\u5212\u5355\u53F7", "\u5907\u6CE8"};
      CellStyle headerStyle = this.createImportHeaderStyle(workbook, IndexedColors.LIGHT_TURQUOISE);
      CellStyle hintStyle = this.createImportHintStyle(workbook);
      Row headerRow = sheet.createRow(0);

      for (int i = 0; i < headers.length; i++) {
         Cell cell = headerRow.createCell(i);
         cell.setCellValue(headers[i]);
         cell.setCellStyle(headerStyle);
      }

      Row hintRow = sheet.createRow(1);
      this.setStyledCellValue(hintRow, 0, "\u793A\u4F8B", hintStyle);
      this.setStyledCellValue(hintRow, 1, "2026-06-03", hintStyle);
      this.setStyledCellValue(hintRow, 2, "\u65E9\u9910", hintStyle);
      this.setStyledCellValue(hintRow, 3, "100", hintStyle);
      this.setStyledCellValue(hintRow, 4, "", hintStyle);
      this.setStyledCellValue(hintRow, 5, "2026-06-03", hintStyle);
      this.setStyledCellValue(hintRow, 6, "2026-06-03", hintStyle);
      this.setStyledCellValue(hintRow, 7, "", hintStyle);
      this.setStyledCellValue(hintRow, 8, "\u76EE\u6807\u4EBA\u7FA4\u9009\u586B\uFF1B\u9910\u6B21\u4F18\u5148\u4F7F\u7528\u4E2D\u6587", hintStyle);
      this.addSheetPrompt(sheet, 1, "\u8BA1\u5212\u65E5\u671F\u5FC5\u586B\uFF0C\u683C\u5F0F\u4E3A yyyy-MM-dd");
      this.addSheetPrompt(sheet, 2, "\u9910\u6B21\u5FC5\u586B\uFF1A\u65E9\u9910\u3001\u5348\u9910\u3001\u665A\u9910\u3001\u591C\u5BB5\uFF1B\u4E5F\u517C\u5BB9\u82F1\u6587\u7F16\u7801");
      this.addSheetPrompt(sheet, 3, "\u5C31\u9910\u4EBA\u6570\u5FC5\u586B\uFF0C\u5FC5\u987B\u4E3A\u6B63\u6574\u6570");
      this.addSheetPrompt(sheet, 4, "\u76EE\u6807\u4EBA\u7FA4\u9009\u586B\uFF1A\u666E\u901A\u6210\u4EBA\u3001\u8001\u5E74\u4EBA\u3001\u513F\u7AE5\u3001\u9752\u5C11\u5E74\u3001\u75C5\u60A3\u3001\u4F53\u529B\u52B3\u52A8\u8005\uFF1B\u4E5F\u517C\u5BB9\u82F1\u6587\u7F16\u7801");
      this.addSheetPrompt(sheet, 5, "\u5B9E\u65BD\u5F00\u59CB\u65E5\u671F\u5FC5\u586B\uFF0C\u683C\u5F0F\u4E3A yyyy-MM-dd");
      this.addSheetPrompt(sheet, 6, "\u5B9E\u65BD\u7ED3\u675F\u65E5\u671F\u5FC5\u586B\uFF0C\u683C\u5F0F\u4E3A yyyy-MM-dd\uFF0C\u4E14\u4E0D\u80FD\u65E9\u4E8E\u5B9E\u65BD\u5F00\u59CB\u65E5\u671F");
      this.addExplicitListValidation(sheet, PLAN_IMPORT_MEAL_TYPE_DISPLAY_OPTIONS, 2, 1000, 2, 2);
      this.addExplicitListValidation(sheet, PLAN_IMPORT_TARGET_GROUP_DISPLAY_OPTIONS, 2, 1000, 4, 4);

      for (int i = 0; i < headers.length; i++) {
         sheet.setColumnWidth(i, 4608);
      }
   }

   private void buildPlanImportItemSheet(Workbook workbook) {
      Sheet sheet = workbook.createSheet(PLAN_IMPORT_ITEM_SHEET);
      String[] headers = new String[]{"\u5BFC\u5165\u5E8F\u53F7*", "\u83DC\u8C31\u7F16\u7801*", "\u83DC\u8C31\u540D\u79F0", "\u8BA1\u5212\u4EFD\u6570*", "\u6392\u5E8F", "\u5907\u6CE8"};
      CellStyle headerStyle = this.createImportHeaderStyle(workbook, IndexedColors.LIGHT_YELLOW);
      Row headerRow = sheet.createRow(0);

      for (int i = 0; i < headers.length; i++) {
         Cell cell = headerRow.createCell(i);
         cell.setCellValue(headers[i]);
         cell.setCellStyle(headerStyle);
      }

      for (int i = 0; i < headers.length; i++) {
         sheet.setColumnWidth(i, 4608);
      }
   }
   private CellStyle createImportHeaderStyle(Workbook workbook, IndexedColors backgroundColor) {
      CellStyle style = workbook.createCellStyle();
      style.setFillForegroundColor(backgroundColor.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      style.setAlignment(HorizontalAlignment.CENTER);
      style.setVerticalAlignment(VerticalAlignment.CENTER);
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderTop(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);
      Font font = workbook.createFont();
      font.setBold(true);
      style.setFont(font);
      return style;
   }

   private CellStyle createImportHintStyle(Workbook workbook) {
      CellStyle style = workbook.createCellStyle();
      style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
      style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
      style.setVerticalAlignment(VerticalAlignment.CENTER);
      style.setBorderBottom(BorderStyle.THIN);
      style.setBorderTop(BorderStyle.THIN);
      style.setBorderLeft(BorderStyle.THIN);
      style.setBorderRight(BorderStyle.THIN);
      style.setWrapText(true);
      Font font = workbook.createFont();
      font.setColor(IndexedColors.GREY_80_PERCENT.getIndex());
      style.setFont(font);
      return style;
   }

   private void setStyledCellValue(Row row, int columnIndex, String value, CellStyle style) {
      Cell cell = row.createCell(columnIndex);
      cell.setCellValue(value);
      cell.setCellStyle(style);
   }

   private void addSheetPrompt(Sheet sheet, int columnIndex, String promptMessage) {
      Workbook workbook = sheet.getWorkbook();
      CreationHelper creationHelper = workbook.getCreationHelper();
      Drawing<?> drawing = sheet.createDrawingPatriarch();
      ClientAnchor anchor = creationHelper.createClientAnchor();
      anchor.setCol1(columnIndex);
      anchor.setCol2(columnIndex + 3);
      anchor.setRow1(0);
      anchor.setRow2(4);
      Comment comment = drawing.createCellComment(anchor);
      comment.setString(creationHelper.createRichTextString(promptMessage));
      sheet.getRow(0).getCell(columnIndex).setCellComment(comment);
   }

   private void addExplicitListValidation(Sheet sheet, String[] values, int firstRow, int lastRow, int firstCol, int lastCol) {
      DataValidationHelper helper = sheet.getDataValidationHelper();
      DataValidationConstraint constraint = helper.createExplicitListConstraint(values);
      CellRangeAddressList regions = new CellRangeAddressList(firstRow, lastRow, firstCol, lastCol);
      DataValidation validation = helper.createValidation(constraint, regions);
      validation.setSuppressDropDownArrow(false);
      validation.setShowErrorBox(true);
      sheet.addValidationData(validation);
   }

   private List<RecipePlanImportDTO> parsePlanSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(PLAN_IMPORT_PLAN_SHEET);
        if (sheet == null) {
            Sheet sheet2 = sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        }
        if (sheet == null) {
            throw BizException.badRequest((String)"\u5bfc\u5165\u6587\u4ef6\u7f3a\u5c11\u8ba1\u5212\u4fe1\u606f Sheet");
        }
        Map<String, String> headerAliasMap = this.buildPlanSheetHeaderAliasMap();
        Row headerRow = this.resolveImportHeaderRow(sheet, headerAliasMap, PLAN_IMPORT_PLAN_SHEET);
        Map<String, Integer> columnMap = this.buildImportColumnMap(headerRow, headerAliasMap);
        this.requireImportColumns(columnMap, List.of("seqNo", "planDate", "mealType", "expectedCount", "startDate", "endDate"), PLAN_IMPORT_PLAN_SHEET);
        ArrayList<RecipePlanImportDTO> plans = new ArrayList<RecipePlanImportDTO>();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); ++rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || this.isImportRowEmpty(row, columnMap.values()) || this.isPlanImportExampleRow(row, columnMap)) continue;
            RecipePlanImportDTO dto = new RecipePlanImportDTO();
            dto.setRowNum(rowIndex + 1);
            dto.setSeqNo(this.parseInteger(this.getCellStringValue(row.getCell(columnMap.get("seqNo").intValue()))));
            dto.setPlanDate(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("planDate").intValue()))));
            dto.setMealType(this.normalizeImportedMealType(this.getCellStringValue(row.getCell(columnMap.get("mealType").intValue()))));
            dto.setExpectedCount(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("expectedCount").intValue()))));
            dto.setTargetGroup(this.normalizeImportedTargetGroup(this.getCellStringValue(row.getCell(columnMap.get("targetGroup").intValue()))));
            dto.setStartDate(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("startDate").intValue()))));
            dto.setEndDate(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("endDate").intValue()))));
            dto.setPlanCode(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("planCode").intValue()))));
            dto.setRemark(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("remark").intValue()))));
            plans.add(dto);
        }
        if (plans.isEmpty()) {
            throw BizException.badRequest((String)"\u5bfc\u5165\u6587\u4ef6\u4e2d\u6ca1\u6709\u53ef\u5bfc\u5165\u7684\u8ba1\u5212\u6570\u636e");
        }
        return plans;
    }

    private Map<Integer, List<RecipePlanItemImportDTO>> parseItemSheet(Workbook workbook) {
        Sheet sheet = workbook.getSheet(PLAN_IMPORT_ITEM_SHEET);
        if (sheet == null) {
            Sheet sheet2 = sheet = workbook.getNumberOfSheets() > 1 ? workbook.getSheetAt(1) : null;
        }
        if (sheet == null) {
            throw BizException.badRequest((String)"\u5bfc\u5165\u6587\u4ef6\u7f3a\u5c11\u83dc\u8c31\u660e\u7ec6 Sheet");
        }
        Map<String, String> headerAliasMap = this.buildItemSheetHeaderAliasMap();
        Row headerRow = this.resolveImportHeaderRow(sheet, headerAliasMap, PLAN_IMPORT_ITEM_SHEET);
        Map<String, Integer> columnMap = this.buildImportColumnMap(headerRow, headerAliasMap);
        this.requireImportColumns(columnMap, List.of("planSeqNo", "recipeCode", "plannedServings"), PLAN_IMPORT_ITEM_SHEET);
        LinkedHashMap<Integer, List<RecipePlanItemImportDTO>> itemMap = new LinkedHashMap<Integer, List<RecipePlanItemImportDTO>>();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); ++rowIndex) {
            Row row = sheet.getRow(rowIndex);
            if (row == null || this.isImportRowEmpty(row, columnMap.values())) continue;
            RecipePlanItemImportDTO dto = new RecipePlanItemImportDTO();
            dto.setPlanSeqNo(this.parseInteger(this.getCellStringValue(row.getCell(columnMap.get("planSeqNo").intValue()))));
            dto.setRecipeCode(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("recipeCode").intValue()))));
            dto.setRecipeName(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("recipeName").intValue()))));
            dto.setPlannedServings(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("plannedServings").intValue()))));
            dto.setSortOrder(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("sortOrder").intValue()))));
            dto.setRemark(this.normalizeImportCellText(this.getCellStringValue(row.getCell(columnMap.get("remark").intValue()))));
            itemMap.computeIfAbsent(dto.getPlanSeqNo(), key -> new ArrayList()).add(dto);
        }
        return itemMap;
    }

    private RecipePlanImportRecordResultDTO importSinglePlan(RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        RecipePlan existingPlan = this.findExistingPlanForImport(planDTO);
        if (existingPlan == null) {
            String createdPlanCode = this.handleNewPlanImport(planDTO, items, recipeCodeMap);
            return this.buildImportRecord(planDTO, createdPlanCode, "created", "\u5df2\u65b0\u589e\u83dc\u8c31\u8ba1\u5212");
        }
        if (IMPORT_FORBIDDEN_UPDATE_STATUSES.contains(existingPlan.getStatus())) {
            throw BizException.badRequest((String)("\u8ba1\u5212[" + existingPlan.getPlanCode() + "]\u5f53\u524d\u72b6\u6001\u4e3a" + this.getStatusName(existingPlan.getStatus()) + "\uff0c\u4e0d\u5141\u8bb8\u901a\u8fc7\u5bfc\u5165\u4fee\u6539"));
        }
        if ("rejected".equals(existingPlan.getStatus())) {
            this.handleDraftPlanImport(existingPlan, planDTO, items, recipeCodeMap);
            return this.buildImportRecord(planDTO, existingPlan.getPlanCode(), "updated", "\u5df2\u5c06\u5df2\u62d2\u7edd\u8ba1\u5212\u6062\u590d\u4e3a\u8349\u7a3f\u5e76\u66f4\u65b0\u5bfc\u5165\u4fe1\u606f");
        }
        if (IMPORT_DIRECT_UPDATE_STATUSES.contains(existingPlan.getStatus())) {
            if (!this.hasImportedPlanChanges(existingPlan, planDTO, items, recipeCodeMap)) {
                return this.buildImportRecord(planDTO, existingPlan.getPlanCode(), "skipped_no_change", "\u5bfc\u5165\u5185\u5bb9\u4e0e\u73b0\u6709\u8ba1\u5212\u4e00\u81f4\uff0c\u5df2\u8df3\u8fc7");
            }
            this.handleDraftPlanImport(existingPlan, planDTO, items, recipeCodeMap);
            return this.buildImportRecord(planDTO, existingPlan.getPlanCode(), "updated", "\u5df2\u76f4\u63a5\u66f4\u65b0\u8349\u7a3f/\u5df2\u62d2\u7edd\u8ba1\u5212");
        }
        if ("approved".equals(existingPlan.getStatus())) {
            if (this.handleApprovedPlanImport(existingPlan, planDTO, items, recipeCodeMap)) {
                return this.buildImportRecord(planDTO, existingPlan.getPlanCode(), "adjustment_created", "\u5df2\u751f\u6210\u5f85\u5ba1\u6838\u7684\u8c03\u6574\u7533\u8bf7");
            }
            return this.buildImportRecord(planDTO, existingPlan.getPlanCode(), "skipped_no_change", "\u5bfc\u5165\u5185\u5bb9\u4e0e\u5df2\u5ba1\u6838\u8ba1\u5212\u4e00\u81f4\uff0c\u5df2\u8df3\u8fc7");
        }
        throw BizException.badRequest((String)("\u8ba1\u5212[" + existingPlan.getPlanCode() + "]\u72b6\u6001\u4e0d\u652f\u6301\u5bfc\u5165\u5904\u7406"));
    }

    private Map<String, String> buildPlanSheetHeaderAliasMap() {
        HashMap<String, String> aliasMap = new HashMap<String, String>();
        aliasMap.put("\u5bfc\u5165\u5e8f\u53f7", "seqNo");
        aliasMap.put("\u8ba1\u5212\u5e8f\u53f7", "seqNo");
        aliasMap.put("\u5e8f\u53f7", "seqNo");
        aliasMap.put("seqno", "seqNo");
        aliasMap.put("\u8ba1\u5212\u65e5\u671f", "planDate");
        aliasMap.put("plandate", "planDate");
        aliasMap.put("\u9910\u6b21", "mealType");
        aliasMap.put("mealtype", "mealType");
        aliasMap.put("\u5c31\u9910\u4eba\u6570", "expectedCount");
        aliasMap.put("\u4eba\u6570", "expectedCount");
        aliasMap.put("expectedcount", "expectedCount");
        aliasMap.put("\u76ee\u6807\u4eba\u7fa4", "targetGroup");
        aliasMap.put("targetgroup", "targetGroup");
        aliasMap.put("\u5b9e\u65bd\u5f00\u59cb\u65e5\u671f", "startDate");
        aliasMap.put("\u5b9e\u65bd\u5f00\u59cb\u65f6\u95f4", "startDate");
        aliasMap.put("\u5f00\u59cb\u65e5\u671f", "startDate");
        aliasMap.put("startdate", "startDate");
        aliasMap.put("\u5b9e\u65bd\u7ed3\u675f\u65e5\u671f", "endDate");
        aliasMap.put("\u5b9e\u65bd\u7ed3\u675f\u65f6\u95f4", "endDate");
        aliasMap.put("\u7ed3\u675f\u65e5\u671f", "endDate");
        aliasMap.put("enddate", "endDate");
        aliasMap.put("\u8ba1\u5212\u7f16\u7801", "planCode");
        aliasMap.put("\u8ba1\u5212\u5355\u53f7", "planCode");
        aliasMap.put("plancode", "planCode");
        aliasMap.put("\u5907\u6ce8", "remark");
        aliasMap.put("remark", "remark");
        return aliasMap;
    }

    private Map<String, String> buildItemSheetHeaderAliasMap() {
        HashMap<String, String> aliasMap = new HashMap<String, String>();
        aliasMap.put("\u5bfc\u5165\u5e8f\u53f7", "planSeqNo");
        aliasMap.put("\u8ba1\u5212\u5e8f\u53f7", "planSeqNo");
        aliasMap.put("planseqno", "planSeqNo");
        aliasMap.put("\u83dc\u8c31\u7f16\u7801", "recipeCode");
        aliasMap.put("\u83dc\u8c31\u7f16\u53f7", "recipeCode");
        aliasMap.put("recipecode", "recipeCode");
        aliasMap.put("\u83dc\u8c31\u540d\u79f0", "recipeName");
        aliasMap.put("recipename", "recipeName");
        aliasMap.put("\u8ba1\u5212\u4efd\u6570", "plannedServings");
        aliasMap.put("\u4efd\u6570", "plannedServings");
        aliasMap.put("plannedservings", "plannedServings");
        aliasMap.put("\u6392\u5e8f", "sortOrder");
        aliasMap.put("sortorder", "sortOrder");
        aliasMap.put("\u5907\u6ce8", "remark");
        aliasMap.put("remark", "remark");
        return aliasMap;
    }

    private Row resolveImportHeaderRow(Sheet sheet, Map<String, String> headerAliasMap, String sheetName) {
        int maxCheckRow = Math.min(sheet.getLastRowNum(), 5);
        for (int rowIndex = 0; rowIndex <= maxCheckRow; ++rowIndex) {
            Map<String, Integer> columnMap;
            Row row = sheet.getRow(rowIndex);
            if (row == null || (columnMap = this.buildImportColumnMap(row, headerAliasMap)).size() < 3) continue;
            return row;
        }
        throw BizException.badRequest((String)("Sheet\u3010" + sheetName + "\u3011\u672a\u8bc6\u522b\u5230\u8868\u5934\uff0c\u8bf7\u4f7f\u7528\u6700\u65b0\u5bfc\u5165\u6a21\u677f"));
    }

    private Map<String, Integer> buildImportColumnMap(Row headerRow, Map<String, String> headerAliasMap) {
        HashMap<String, Integer> columnMap = new HashMap<String, Integer>();
        for (int i = 0; i < headerRow.getLastCellNum(); ++i) {
            String field;
            String header = this.normalizeImportHeader(this.getCellStringValue(headerRow.getCell(i)));
            if (StrUtil.isBlank((CharSequence)header) || (field = headerAliasMap.get(header)) == null) continue;
            columnMap.putIfAbsent(field, i);
        }
        return columnMap;
    }

    private void requireImportColumns(Map<String, Integer> columnMap, List<String> requiredFields, String sheetName) {
        List<String> missingFields = requiredFields.stream().filter(field -> !columnMap.containsKey(field)).toList();
        if (!missingFields.isEmpty()) {
            throw BizException.badRequest((String)("Sheet\u3010" + sheetName + "\u3011\u7f3a\u5c11\u5fc5\u8981\u5217\uff1a" + String.join((CharSequence)"\u3001", missingFields)));
        }
    }

    private boolean isImportRowEmpty(Row row, Iterable<Integer> columns) {
        for (Integer column : columns) {
            if (column == null || !StrUtil.isNotBlank((CharSequence)this.getCellStringValue(row.getCell(column.intValue())))) continue;
            return false;
        }
        return true;
    }

    private boolean isPlanImportExampleRow(Row row, Map<String, Integer> columnMap) {
        Integer seqNoColumn = columnMap.get("seqNo");
        if (seqNoColumn == null) {
            return false;
        }
        String seqNoText = this.normalizeImportCellText(this.getCellStringValue(row.getCell(seqNoColumn.intValue())));
        return "\u793a\u4f8b".equals(seqNoText) || "\u6837\u4f8b".equals(seqNoText);
    }

    private String validatePlanRow(RecipePlanImportDTO planDTO, Map<Integer, List<RecipePlanItemImportDTO>> itemMap, Map<String, Recipe> recipeCodeMap, Set<Integer> duplicateSeqNos) {
        if (planDTO.getSeqNo() == null) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5bfc\u5165\u5e8f\u53f7\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (duplicateSeqNos.contains(planDTO.getSeqNo())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5bfc\u5165\u5e8f\u53f7\u91cd\u590d";
        }
        if (StrUtil.isBlank((CharSequence)planDTO.getPlanDate())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u8ba1\u5212\u65e5\u671f\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (StrUtil.isBlank((CharSequence)planDTO.getMealType())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u9910\u6b21\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (StrUtil.isBlank((CharSequence)planDTO.getExpectedCount())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5c31\u9910\u4eba\u6570\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (StrUtil.isBlank((CharSequence)planDTO.getStartDate())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5f00\u59cb\u65e5\u671f\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (StrUtil.isBlank((CharSequence)planDTO.getEndDate())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u7ed3\u675f\u65e5\u671f\u4e0d\u80fd\u4e3a\u7a7a";
        }
        if (!VALID_MEAL_TYPES.contains(planDTO.getMealType())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u9910\u6b21\u65e0\u6548\uff0c\u4ec5\u652f\u6301 \u65e9\u9910/\u5348\u9910/\u665a\u9910/\u591c\u5bb5 \u6216 breakfast/lunch/dinner/supper";
        }
        if (StrUtil.isNotBlank((CharSequence)planDTO.getTargetGroup()) && !VALID_TARGET_GROUPS.contains(planDTO.getTargetGroup())) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u76ee\u6807\u4eba\u7fa4\u65e0\u6548";
        }
        Integer expectedCount = this.parseInteger(planDTO.getExpectedCount());
        if (expectedCount == null || expectedCount <= 0) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5c31\u9910\u4eba\u6570\u5fc5\u987b\u4e3a\u6b63\u6574\u6570";
        }
        LocalDate planDate = this.parseImportLocalDate(planDTO.getPlanDate());
        LocalDate startDate = this.parseImportLocalDate(planDTO.getStartDate());
        LocalDate endDate = this.parseImportLocalDate(planDTO.getEndDate());
        if (planDate == null) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u8ba1\u5212\u65e5\u671f\u683c\u5f0f\u9519\u8bef\uff0c\u5e94\u4e3a yyyy-MM-dd";
        }
        if (startDate == null) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u5f00\u59cb\u65e5\u671f\u683c\u5f0f\u9519\u8bef\uff0c\u5e94\u4e3a yyyy-MM-dd";
        }
        if (endDate == null) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u7ed3\u675f\u65e5\u671f\u683c\u5f0f\u9519\u8bef\uff0c\u5e94\u4e3a yyyy-MM-dd";
        }
        if (endDate.isBefore(startDate)) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u7ed3\u675f\u65e5\u671f\u4e0d\u80fd\u65e9\u4e8e\u5f00\u59cb\u65e5\u671f";
        }
        List<RecipePlanItemImportDTO> items = itemMap.get(planDTO.getSeqNo());
        if (items == null || items.isEmpty()) {
            return "\u7b2c" + planDTO.getRowNum() + "\u884c\uff1a\u8be5\u8ba1\u5212\u672a\u586b\u5199\u83dc\u8c31\u660e\u7ec6";
        }
        for (RecipePlanItemImportDTO item : items) {
            if (item.getPlanSeqNo() == null) {
                return "\u5bfc\u5165\u5e8f\u53f7[" + planDTO.getSeqNo() + "]\u5b58\u5728\u660e\u7ec6\u672a\u586b\u5199\u5bfc\u5165\u5e8f\u53f7";
            }
            if (StrUtil.isBlank((CharSequence)item.getRecipeCode())) {
                return "\u5bfc\u5165\u5e8f\u53f7[" + planDTO.getSeqNo() + "]\u5b58\u5728\u660e\u7ec6\u672a\u586b\u5199\u83dc\u8c31\u7f16\u7801";
            }
            if (!recipeCodeMap.containsKey(item.getRecipeCode())) {
                return "\u5bfc\u5165\u5e8f\u53f7[" + planDTO.getSeqNo() + "]\u7684\u83dc\u8c31\u7f16\u7801[" + item.getRecipeCode() + "]\u4e0d\u5b58\u5728\u6216\u672a\u542f\u7528";
            }
            Integer plannedServings = this.parseInteger(item.getPlannedServings());
            if (plannedServings == null || plannedServings <= 0) {
                return "\u5bfc\u5165\u5e8f\u53f7[" + planDTO.getSeqNo() + "]\u7684\u83dc\u8c31\u7f16\u7801[" + item.getRecipeCode() + "]\u8ba1\u5212\u4efd\u6570\u5fc5\u987b\u4e3a\u6b63\u6574\u6570";
            }
            if (!StrUtil.isNotBlank((CharSequence)item.getSortOrder()) || this.parseInteger(item.getSortOrder()) != null) continue;
            return "\u5bfc\u5165\u5e8f\u53f7[" + planDTO.getSeqNo() + "]\u7684\u83dc\u8c31\u7f16\u7801[" + item.getRecipeCode() + "]\u6392\u5e8f\u5fc5\u987b\u4e3a\u6574\u6570";
        }
        return null;
    }

    private RecipePlan findExistingPlanForImport(RecipePlanImportDTO planDTO) {
        if (StrUtil.isNotBlank((CharSequence)planDTO.getPlanCode())) {
            RecipePlan existingPlan = (RecipePlan)this.getOne(
                new LambdaQueryWrapper<RecipePlan>()
                    .eq(RecipePlan::getPlanCode, planDTO.getPlanCode())
                    .eq(UserContext.getOrgId() != null, RecipePlan::getOrgId, UserContext.getOrgId())
                    .last("LIMIT 1")
            );
            if (existingPlan == null) {
                throw BizException.badRequest((String)("\u8ba1\u5212\u5355\u53f7[" + planDTO.getPlanCode() + "]\u4e0d\u5b58\u5728"));
            }
            return existingPlan;
        }
        return null;
    }
    private String handleNewPlanImport(RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        RecipePlan plan = new RecipePlan();
        this.fillPlanFromImport(plan, planDTO);
        plan.setPlanCode(this.generatePlanCode());
        plan.setStatus("draft");
        plan.setOrgId(UserContext.getOrgId());
        plan.setTenantId(UserContext.getTenantId());
        plan.setCreatedAt(LocalDateTime.now());
        ((RecipePlanMapper)this.baseMapper).insert(plan);
        this.replacePlanItems(plan.getId(), items, recipeCodeMap);
        this.calculateTotals(plan);
        this.updateById(plan);
        return plan.getPlanCode();
    }

    private void handleDraftPlanImport(RecipePlan existingPlan, RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        this.fillPlanFromImport(existingPlan, planDTO);
        if ("rejected".equals(existingPlan.getStatus())) {
            existingPlan.setStatus("draft");
            existingPlan.setSubmittedBy(null);
            existingPlan.setSubmittedAt(null);
            existingPlan.setAuditedBy(null);
            existingPlan.setAuditedAt(null);
            existingPlan.setAuditRemark(null);
        }
        existingPlan.setUpdatedAt(LocalDateTime.now());
        this.updateById(existingPlan);
        this.replacePlanItems(existingPlan.getId(), items, recipeCodeMap);
        this.calculateTotals(existingPlan);
        this.updateById(existingPlan);
    }

    private boolean handleApprovedPlanImport(RecipePlan existingPlan, RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        if (!this.hasImportedPlanChanges(existingPlan, planDTO, items, recipeCodeMap)) {
            return false;
        }
        Long pendingCount = this.adjustmentMapper.selectCount(
            new LambdaQueryWrapper<RecipePlanAdjustment>()
                .eq(RecipePlanAdjustment::getPlanId, existingPlan.getId())
                .eq(RecipePlanAdjustment::getStatus, "pending")
        );
        if (pendingCount != null && pendingCount > 0L) {
            throw BizException.badRequest((String)("\u8ba1\u5212[" + existingPlan.getPlanCode() + "]\u5df2\u6709\u5f85\u5ba1\u6838\u7684\u8c03\u6574\u7533\u8bf7\uff0c\u8bf7\u5148\u5b8c\u6210\u5ba1\u6838"));
        }
        String beforeData = this.getPlanSnapshotJson(existingPlan.getId());
        String afterData = this.writeAdjustmentSnapshot(this.buildImportedSnapshot(existingPlan, planDTO, items, recipeCodeMap));
        String resolvedType = this.resolveAdjustType(beforeData, afterData, "modify");
        LocalDateTime now = LocalDateTime.now();
        RecipePlanAdjustment adjustment = new RecipePlanAdjustment();
        adjustment.setAdjustCode(this.generateAdjustmentCode());
        adjustment.setPlanId(existingPlan.getId());
        adjustment.setAdjustReason("\u5bfc\u5165\u83dc\u8c31\u8ba1\u5212\u81ea\u52a8\u751f\u6210\u8c03\u6574\u7533\u8bf7");
        adjustment.setAdjustType(resolvedType);
        adjustment.setBeforeData(beforeData);
        adjustment.setAfterData(afterData);
        adjustment.setStatus("pending");
        adjustment.setAppliedBy(UserContext.getUserId());
        adjustment.setAppliedAt(now);
        adjustment.setOrgId(existingPlan.getOrgId());
        adjustment.setTenantId(existingPlan.getTenantId());
        adjustment.setCreatedAt(now);
        this.adjustmentMapper.insert(adjustment);
        return true;
    }

    private boolean hasImportedPlanChanges(RecipePlan existingPlan, RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        String afterData;
        String beforeData = this.getPlanSnapshotJson(existingPlan.getId());
        return !this.buildAdjustItems(beforeData, afterData = this.writeAdjustmentSnapshot(this.buildImportedSnapshot(existingPlan, planDTO, items, recipeCodeMap)), "modify").isEmpty();
    }

    private AdjustmentPlanSnapshot buildImportedSnapshot(RecipePlan existingPlan, RecipePlanImportDTO planDTO, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        AdjustmentPlanSnapshot snapshot = new AdjustmentPlanSnapshot();
        snapshot.setPlanId(existingPlan.getId());
        snapshot.setPlanCode(existingPlan.getPlanCode());
        snapshot.setPlanDate(planDTO.getPlanDate());
        snapshot.setStartDate(planDTO.getStartDate());
        snapshot.setEndDate(planDTO.getEndDate());
        snapshot.setMealType(planDTO.getMealType());
        snapshot.setExpectedCount(this.parseInteger(planDTO.getExpectedCount()));
        snapshot.setTargetGroup(planDTO.getTargetGroup());
        snapshot.setHealthStatus(this.splitHealthStatuses(existingPlan.getHealthStatus()));
        snapshot.setDietRestrictions(existingPlan.getDietRestrictions());
        snapshot.setRemark(planDTO.getRemark());
        snapshot.setUseAiRecommend(existingPlan.getUseAiRecommend());
        AdjustmentMealScheduleSnapshot mealSchedule = new AdjustmentMealScheduleSnapshot();
        mealSchedule.setMealKey("import-" + planDTO.getSeqNo());
        mealSchedule.setMealType(planDTO.getMealType());
        mealSchedule.setMealName(this.resolveMealScheduleName(planDTO.getMealType(), null));
        mealSchedule.setExpectedCount(this.parseInteger(planDTO.getExpectedCount()));
        mealSchedule.setSortOrder(1);
        ArrayList<AdjustmentPlanRecipeSnapshot> recipeSnapshots = new ArrayList<AdjustmentPlanRecipeSnapshot>();
        int defaultSortOrder = 1;
        for (RecipePlanItemImportDTO item : items) {
            Recipe recipe = recipeCodeMap.get(item.getRecipeCode());
            if (recipe == null) continue;
            AdjustmentPlanRecipeSnapshot recipeSnapshot = new AdjustmentPlanRecipeSnapshot();
            recipeSnapshot.setRecipeId(recipe.getId());
            recipeSnapshot.setRecipeName(StrUtil.blankToDefault((CharSequence)item.getRecipeName(), (String)recipe.getRecipeName()));
            recipeSnapshot.setCategoryName(this.resolveRecipeCategoryName(recipe));
            recipeSnapshot.setPlannedServings(this.parseInteger(item.getPlannedServings()));
            recipeSnapshot.setSortOrder(this.parseInteger(StrUtil.blankToDefault((CharSequence)item.getSortOrder(), (String)String.valueOf(defaultSortOrder++))));
            recipeSnapshot.setRemark(item.getRemark());
            recipeSnapshot.setMealKey(mealSchedule.getMealKey());
            recipeSnapshot.setMealType(mealSchedule.getMealType());
            recipeSnapshot.setMealName(mealSchedule.getMealName());
            recipeSnapshot.setMealExpectedCount(mealSchedule.getExpectedCount());
            recipeSnapshot.setMealSortOrder(mealSchedule.getSortOrder());
            recipeSnapshots.add(recipeSnapshot);
        }
        mealSchedule.setRecipes(recipeSnapshots);
        snapshot.setMealSchedules(List.of(mealSchedule));
        snapshot.setRecipes(recipeSnapshots);
        return snapshot;
    }

    private String writeAdjustmentSnapshot(AdjustmentPlanSnapshot snapshot) {
        try {
            return this.objectMapper.writeValueAsString((Object)snapshot);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException("\u5e8f\u5217\u5316\u5bfc\u5165\u8ba1\u5212\u5feb\u7167\u5931\u8d25", e);
        }
    }

    private void fillPlanFromImport(RecipePlan plan, RecipePlanImportDTO planDTO) {
        plan.setPlanDate(this.parseImportLocalDate(planDTO.getPlanDate()));
        plan.setStartDate(this.parseImportLocalDate(planDTO.getStartDate()));
        plan.setEndDate(this.parseImportLocalDate(planDTO.getEndDate()));
        plan.setMealType(planDTO.getMealType());
        plan.setExpectedCount(this.parseInteger(planDTO.getExpectedCount()));
        plan.setTargetGroup(this.emptyToNull(planDTO.getTargetGroup()));
        plan.setRemark(this.emptyToNull(planDTO.getRemark()));
    }

    private void replacePlanItems(Long planId, List<RecipePlanItemImportDTO> items, Map<String, Recipe> recipeCodeMap) {
        this.planItemMapper.delete(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId));
        RecipePlan plan = this.getById(planId);
        int defaultSortOrder = 1;
        for (RecipePlanItemImportDTO itemDTO : items) {
            Recipe recipe = recipeCodeMap.get(itemDTO.getRecipeCode());
            if (recipe == null) continue;
            RecipePlanItem item = new RecipePlanItem();
            item.setPlanId(planId);
            item.setRecipeId(recipe.getId());
            item.setRecipeName(recipe.getRecipeName());
            item.setRecipeCode(recipe.getRecipeCode());
            item.setMealKey("import-" + planId);
            item.setMealType(plan != null ? plan.getMealType() : null);
            item.setMealName(this.resolveMealScheduleName(plan != null ? plan.getMealType() : null, null));
            item.setMealExpectedCount(plan != null ? plan.getExpectedCount() : null);
            item.setMealSortOrder(1);
            item.setPlannedServings(this.parseInteger(itemDTO.getPlannedServings()));
            item.setSortOrder(this.parseInteger(StrUtil.blankToDefault((CharSequence)itemDTO.getSortOrder(), (String)String.valueOf(defaultSortOrder++))));
            item.setStatus("pending");
            item.setRemark(this.emptyToNull(itemDTO.getRemark()));
            item.setCategoryName(this.resolveRecipeCategoryName(recipe));
            item.setUnitCost(recipe.getUnitCost());
            if (recipe.getUnitCost() != null && item.getPlannedServings() != null) {
                item.setTotalCost(recipe.getUnitCost().multiply(BigDecimal.valueOf(item.getPlannedServings().intValue())));
            }
            this.planItemMapper.insert(item);
        }
    }

    private String resolveRecipeCategoryName(Recipe recipe) {
        if (recipe == null || recipe.getCategoryId() == null) {
            return null;
        }
        RecipeCategory category = this.getVisibleCategoryById(recipe.getCategoryId());
        return category == null ? null : category.getCategoryName();
    }

    private Map<String, Recipe> loadImportRecipeCodeMap(Map<Integer, List<RecipePlanItemImportDTO>> itemMap) {
        Set<String> recipeCodes = itemMap.values()
            .stream()
            .flatMap(Collection::stream)
            .map(RecipePlanItemImportDTO::getRecipeCode)
            .filter(CharSequenceUtil::isNotBlank)
            .map(String::trim)
            .collect(Collectors.toSet());
        if (recipeCodes.isEmpty()) {
            return Map.of();
        }
        return this.recipeMapper.selectList(
            new LambdaQueryWrapper<Recipe>().in(Recipe::getRecipeCode, recipeCodes).eq(Recipe::getStatus, "active")
        ).stream().collect(Collectors.toMap(Recipe::getRecipeCode, recipe -> recipe, (left, right) -> left));
    }

    private Set<Integer> findDuplicatePlanSeqNos(List<RecipePlanImportDTO> plans) {
        Map<Integer, Long> counts = plans.stream().map(RecipePlanImportDTO::getSeqNo).filter(Objects::nonNull).collect(Collectors.groupingBy(seqNo -> seqNo, Collectors.counting()));
        return counts.entrySet().stream().filter(entry -> (Long)entry.getValue() > 1L).map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private String generatePlanImportErrorFile(List<RecipePlanImportDTO> errorList) {
        try {
            File dir = new File(PLAN_IMPORT_ERROR_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("\u521b\u5efa\u8ba1\u5212\u5bfc\u5165\u9519\u8bef\u6587\u4ef6\u76ee\u5f55\u5931\u8d25: {}", (Object)dir.getAbsolutePath());
            }
            String fileName = "recipe_plan_import_errors_" + System.currentTimeMillis() + ".xlsx";
            File file = new File(dir, fileName);
            try (XSSFWorkbook workbook = new XSSFWorkbook();){
                int i;
                Sheet sheet = workbook.createSheet("\u5bfc\u5165\u5931\u8d25\u8bb0\u5f55");
                String[] headers = new String[]{"\u5bfc\u5165\u5e8f\u53f7", "\u8ba1\u5212\u65e5\u671f", "\u9910\u6b21", "\u5c31\u9910\u4eba\u6570", "\u76ee\u6807\u4eba\u7fa4", "\u5f00\u59cb\u65e5\u671f", "\u7ed3\u675f\u65e5\u671f", "\u8ba1\u5212\u5355\u53f7", "\u5907\u6ce8", "\u5931\u8d25\u539f\u56e0"};
                Row headerRow = sheet.createRow(0);
                CellStyle headerStyle = this.createImportHeaderStyle((Workbook)workbook, IndexedColors.ROSE);
                for (i = 0; i < headers.length; ++i) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }
                for (i = 0; i < errorList.size(); ++i) {
                    RecipePlanImportDTO dto = errorList.get(i);
                    Row row = sheet.createRow(i + 1);
                    this.setCellValue(row, 0, dto.getSeqNo() == null ? "" : String.valueOf(dto.getSeqNo()));
                    this.setCellValue(row, 1, dto.getPlanDate());
                    this.setCellValue(row, 2, this.getMealTypeDisplayName(dto.getMealType()));
                    this.setCellValue(row, 3, dto.getExpectedCount());
                    this.setCellValue(row, 4, this.getTargetGroupDisplayName(dto.getTargetGroup()));
                    this.setCellValue(row, 5, dto.getStartDate());
                    this.setCellValue(row, 6, dto.getEndDate());
                    this.setCellValue(row, 7, dto.getPlanCode());
                    this.setCellValue(row, 8, dto.getRemark());
                    this.setCellValue(row, 9, dto.getErrorMessage());
                }
                for (i = 0; i < headers.length; ++i) {
                    sheet.autoSizeColumn(i);
                }
                try (FileOutputStream fos = new FileOutputStream(file);){
                    workbook.write(fos);
                }
            }
            return "/api/v1/recipe/plans/import/errors/" + fileName;
        }
        catch (IOException e) {
            log.error("\u751f\u6210\u8ba1\u5212\u5bfc\u5165\u9519\u8bef\u6587\u4ef6\u5931\u8d25", (Throwable)e);
            return null;
        }
    }

    private RecipePlanImportRecordResultDTO buildImportRecord(RecipePlanImportDTO planDTO, String planCode, String action, String message) {
        RecipePlanImportRecordResultDTO record = new RecipePlanImportRecordResultDTO();
        record.setSeqNo(planDTO.getSeqNo());
        record.setPlanCode(StrUtil.blankToDefault((CharSequence)planCode, (String)planDTO.getPlanCode()));
        record.setPlanDate(planDTO.getPlanDate());
        record.setAction(action);
        record.setActionName(this.resolveImportActionName(action));
        record.setMessage(message);
        return record;
    }

    private String resolveImportActionName(String action) {
        return switch (action) {
            case "created" -> "\u65b0\u589e\u8ba1\u5212";
            case "updated" -> "\u76f4\u63a5\u66f4\u65b0";
            case "adjustment_created" -> "\u751f\u6210\u8c03\u6574\u7533\u8bf7";
            case "skipped_no_change" -> "\u8df3\u8fc7\u65e0\u53d8\u66f4";
            case "failed" -> "\u5bfc\u5165\u5931\u8d25";
            default -> action;
        };
    }

    private String normalizeImportedMealType(String rawValue) {
        return this.normalizeImportedAlias(rawValue, PLAN_IMPORT_MEAL_TYPE_ALIASES);
    }

    private String normalizeImportedTargetGroup(String rawValue) {
        return this.normalizeImportedAlias(rawValue, PLAN_IMPORT_TARGET_GROUP_ALIASES);
    }

    private String normalizeImportedAlias(String rawValue, Map<String, String> aliasMap) {
        String value = this.normalizeImportCellText(rawValue);
        if (StrUtil.isBlank((CharSequence)value)) {
            return value;
        }
        String normalizedValue = value.toLowerCase();
        return aliasMap.getOrDefault(value, aliasMap.getOrDefault(normalizedValue, value));
    }

    private String normalizeImportCellText(String value) {
        String normalized = StrUtil.trimToEmpty((CharSequence)value);
        if (normalized.isEmpty()) {
            return null;
        }
        return switch (normalized) {
            case "\u7559\u7a7a\u5219\u65b0\u589e", "\u7559\u7a7a\u5219\u65b0\u589e\u6216\u81ea\u52a8\u5339\u914d" -> null;
            default -> normalized;
        };
    }
    private String emptyToNull(String value) {
        String normalized = StrUtil.trimToEmpty((CharSequence)value);
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeImportHeader(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("*", "").replace("\uff08", "(").replace("\uff09", ")").replaceAll("\\s+", "").trim().toLowerCase();
    }

    private LocalDate parseImportLocalDate(String value) {
        if (StrUtil.isBlank((CharSequence)value)) {
            return null;
        }
        String normalized = value.trim().replace('.', '-').replace('/', '-');
        List<DateTimeFormatter> formatters = List.of(PLAN_IMPORT_DATE_FORMATTER, DateTimeFormatter.ofPattern("yyyy-M-d"), DateTimeFormatter.ofPattern("yyyy-MM-d"), DateTimeFormatter.ofPattern("yyyy-M-dd"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(normalized, formatter);
            }
            catch (Exception exception) {
            }
        }
        return null;
    }

    private Integer parseInteger(String value) {
      if (StrUtil.isBlank(value)) {
         return null;
      } else {
         try {
            return Integer.parseInt(value.trim());
         } catch (NumberFormatException var3) {
            return null;
         }
      }
   }

   private String getCellStringValue(Cell cell) {
      if (cell == null) {
         return null;
      } else {
         return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
               if (DateUtil.isCellDateFormatted(cell)) {
                  yield PLAN_IMPORT_DATE_FORMATTER.format(cell.getLocalDateTimeCellValue().toLocalDate());
               } else {
                  double value = cell.getNumericCellValue();
                  yield value == Math.floor(value) && !Double.isInfinite(value)
                     ? String.valueOf((long)value)
                     : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
               }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK, ERROR, _NONE -> null;
            default -> null;
         };
      }
   }

   private void setCellValue(Row row, int columnIndex, String value) {
      row.createCell(columnIndex).setCellValue(StrUtil.blankToDefault(value, ""));
   }
    public RecipePlanDetailVO getDetail(Long id) {
        RecipePlan plan = (RecipePlan)this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        RecipePlanDetailVO vo = new RecipePlanDetailVO();
        BeanUtils.copyProperties((Object)plan, (Object)vo);
        vo.setMealTypeName(this.getMealTypeName(plan.getMealType()));
        vo.setStatusName(this.getStatusName(plan.getStatus()));
        vo.setStockRiskStatusName(this.getStockRiskStatusName(plan.getStockRiskStatus()));
        vo.setTargetGroupName(this.getTargetGroupDisplayName(plan.getTargetGroup()));
        if (plan.getSubmittedBy() != null) {
            vo.setSubmittedByName(this.resolveEmployeeName(plan.getSubmittedBy()));
        }
        if (plan.getAuditedBy() != null) {
            vo.setAuditedByName(this.resolveEmployeeName(plan.getAuditedBy()));
        }
        if (plan.getCreatedBy() != null) {
            vo.setCreatedByName(this.resolveEmployeeName(plan.getCreatedBy()));
        }
        vo.setOrgName(this.resolveOrgName(plan.getOrgId()));
        List<RecipePlanItem> items = this.planItemMapper.selectList(
            new LambdaQueryWrapper<RecipePlanItem>()
                .eq(RecipePlanItem::getPlanId, id)
                .orderByAsc(RecipePlanItem::getMealSortOrder)
                .orderByAsc(RecipePlanItem::getSortOrder)
                .orderByAsc(RecipePlanItem::getId)
        );
        List<RecipePlanDetailVO.RecipePlanItemVO> itemVOList = items.stream().map(item -> {
            RecipePlanDetailVO.RecipePlanItemVO itemVO = new RecipePlanDetailVO.RecipePlanItemVO();
            BeanUtils.copyProperties((Object)item, (Object)itemVO);
            itemVO.setRecipeName(item.getRecipeName());
            itemVO.setCategoryName(item.getCategoryName());
            Recipe recipe = (Recipe)this.recipeMapper.selectById(item.getRecipeId());
            if (recipe != null) {
                RecipeCategory category;
                itemVO.setRecipeName(recipe.getRecipeName());
                if (recipe.getCategoryId() != null && (category = this.getVisibleCategoryById(recipe.getCategoryId())) != null) {
                    itemVO.setCategoryName(category.getCategoryName());
                }
                List<RecipeIngredient> ingredients = this.ingredientMapper.selectList(
                    new LambdaQueryWrapper<RecipeIngredient>()
                        .eq(RecipeIngredient::getRecipeId, recipe.getId())
                        .eq(RecipeIngredient::getDeleted, 0)
                        .orderByDesc(RecipeIngredient::getIsMain)
                        .orderByAsc(RecipeIngredient::getSortOrder)
                );
                List<RecipePlanDetailVO.IngredientBrief> briefs = ingredients.stream().map(ing -> {
                    RecipePlanDetailVO.IngredientBrief brief = new RecipePlanDetailVO.IngredientBrief();
                    brief.setMaterialName(ing.getMaterialName());
                    brief.setQuantity(ing.getQuantity());
                    brief.setUnit(ing.getUnit());
                    brief.setIsMain(ing.getIsMain());
                    return brief;
                }).collect(Collectors.toList());
                itemVO.setIngredients(briefs);
            }
            return itemVO;
        }).collect(Collectors.toList());
        vo.setRecipes(itemVOList);
        List<RecipePlanDetailVO.MealScheduleVO> mealSchedules = this.buildMealScheduleVOs(itemVOList, plan);
        vo.setMealSchedules(mealSchedules);
        vo.setMealScheduleCount(mealSchedules.size());
        vo.setMealDisplayName(this.buildMealDisplayName(mealSchedules, plan));
        vo.setExpectedCountDisplay(this.buildExpectedCountDisplay(mealSchedules, plan));
        vo.setRecipeCount(itemVOList.size());
        RecipePlanAdjustment latestAdjustment = this.loadLatestAdjustment(id);
        vo.setHasPendingAdjustment(latestAdjustment != null && "pending".equals(latestAdjustment.getStatus()));
        if (latestAdjustment != null) {
            vo.setAdjustmentStatus(latestAdjustment.getStatus());
            vo.setAdjustmentStatusName(this.getAdjustmentStatusName(latestAdjustment.getStatus()));
        }
        AdjustmentContext adjustmentContext = this.resolveAdjustmentContext(plan.getId());
        vo.setAdjustmentMode(adjustmentContext.getMode());
        vo.setAdjustmentModeName(this.getAdjustmentModeName(adjustmentContext.getMode()));
        vo.setAdjustmentHint(this.buildAdjustmentHint(adjustmentContext));
        vo.setCanAdjust(this.canAdjustPlan(plan, adjustmentContext));
        vo.setAuditLogs(this.getAuditLog(id));
        if (vo.getNutritionPassRate() == null && ("approved".equals(plan.getStatus()) || "completed".equals(plan.getStatus()))) {
            try {
                BigDecimal quickRate = this.calculateQuickPassRate(id);
                if (quickRate != null) {
                    vo.setNutritionPassRate(quickRate);
                }
            }
            catch (Exception e) {
                log.warn("\u5feb\u901f\u8ba1\u7b97\u8fbe\u6807\u7387\u5931\u8d25, planId={}: {}", (Object)id, (Object)e.getMessage());
            }
        }
        return vo;
    }

    private List<RecipePlanDetailVO.MealScheduleVO> buildMealScheduleVOs(List<RecipePlanDetailVO.RecipePlanItemVO> items, RecipePlan plan) {
        Map<String, RecipePlanDetailVO.MealScheduleVO> scheduleMap = new LinkedHashMap<>();
        if (items == null) {
            return new ArrayList<>();
        }
        for (RecipePlanDetailVO.RecipePlanItemVO item : items) {
            String scheduleKey = StrUtil.blankToDefault(item.getMealKey(), "legacy-" + StrUtil.blankToDefault(item.getMealType(), plan.getMealType()));
            RecipePlanDetailVO.MealScheduleVO schedule = scheduleMap.computeIfAbsent(scheduleKey, key -> {
                RecipePlanDetailVO.MealScheduleVO vo = new RecipePlanDetailVO.MealScheduleVO();
                vo.setMealKey(scheduleKey);
                vo.setMealType(StrUtil.blankToDefault(item.getMealType(), plan.getMealType()));
                vo.setMealName(this.resolveMealScheduleName(item.getMealType(), item.getMealName()));
                vo.setMealTypeName(this.getMealTypeDisplayName(vo.getMealType()));
                vo.setExpectedCount(item.getMealExpectedCount() != null ? item.getMealExpectedCount() : plan.getExpectedCount());
                vo.setSortOrder(item.getMealSortOrder() != null ? item.getMealSortOrder() : 1);
                vo.setRecipes(new ArrayList<>());
                return vo;
            });
            schedule.getRecipes().add(item);
        }
        return scheduleMap.values().stream()
            .sorted(java.util.Comparator.comparing(RecipePlanDetailVO.MealScheduleVO::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    private String buildMealDisplayName(List<RecipePlanDetailVO.MealScheduleVO> mealSchedules, RecipePlan plan) {
        if (mealSchedules == null || mealSchedules.isEmpty()) {
            return this.getMealTypeDisplayName(plan.getMealType());
        }
        return mealSchedules.stream()
            .map(schedule -> StrUtil.blankToDefault(schedule.getMealName(), this.getMealTypeDisplayName(schedule.getMealType())))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.joining(" / "));
    }

    private String buildExpectedCountDisplay(List<RecipePlanDetailVO.MealScheduleVO> mealSchedules, RecipePlan plan) {
        if (mealSchedules == null || mealSchedules.isEmpty()) {
            return plan.getExpectedCount() == null ? "" : plan.getExpectedCount() + "人";
        }
        if (mealSchedules.size() == 1) {
            Integer expectedCount = mealSchedules.get(0).getExpectedCount();
            return expectedCount == null ? "" : expectedCount + "人";
        }
        return mealSchedules.stream()
            .map(schedule -> StrUtil.blankToDefault(schedule.getMealName(), this.getMealTypeDisplayName(schedule.getMealType())) + " " + schedule.getExpectedCount() + "人")
            .collect(Collectors.joining(" / "));
    }

    private List<PlanMealScheduleData> resolveMealSchedules(RecipePlanCreateDTO dto) {
        List<PlanMealScheduleData> schedules = new ArrayList<>();
        if (dto != null && dto.getMealSchedules() != null && !dto.getMealSchedules().isEmpty()) {
            int sortOrder = 1;
            for (MealScheduleDTO scheduleDTO : dto.getMealSchedules()) {
                if (scheduleDTO == null) {
                    continue;
                }
                PlanMealScheduleData schedule = new PlanMealScheduleData();
                schedule.setMealKey(StrUtil.blankToDefault(StrUtil.trim(scheduleDTO.getMealKey()), "meal-" + UUID.randomUUID()));
                schedule.setMealType(StrUtil.trim(scheduleDTO.getMealType()));
                schedule.setMealName(StrUtil.trim(scheduleDTO.getMealName()));
                schedule.setExpectedCount(scheduleDTO.getExpectedCount());
                schedule.setSortOrder(scheduleDTO.getSortOrder() != null ? scheduleDTO.getSortOrder() : sortOrder);
                schedule.setRecipes(scheduleDTO.getRecipes() == null ? new ArrayList<>() : new ArrayList<>(scheduleDTO.getRecipes()));
                schedules.add(schedule);
                sortOrder++;
            }
        } else if (dto != null) {
            PlanMealScheduleData schedule = new PlanMealScheduleData();
            schedule.setMealKey("meal-1");
            schedule.setMealType(StrUtil.trim(dto.getMealType()));
            schedule.setMealName(null);
            schedule.setExpectedCount(dto.getExpectedCount());
            schedule.setSortOrder(1);
            schedule.setRecipes(dto.getRecipes() == null ? new ArrayList<>() : new ArrayList<>(dto.getRecipes()));
            schedules.add(schedule);
        }
        this.validateMealSchedules(schedules);
        return schedules;
    }

    private void validateMealSchedules(List<PlanMealScheduleData> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            throw BizException.badRequest("至少需要配置一个餐次");
        }
        Set<String> builtinMealTypes = new java.util.HashSet<>();
        Set<String> customMealNames = new java.util.HashSet<>();
        for (PlanMealScheduleData schedule : schedules) {
            if (schedule == null || StrUtil.isBlank(schedule.getMealType())) {
                throw BizException.badRequest("餐次不能为空");
            }
            if ("custom".equals(schedule.getMealType())) {
                String customName = StrUtil.trim(schedule.getMealName());
                if (StrUtil.isBlank(customName)) {
                    throw BizException.badRequest("自定义餐次名称不能为空");
                }
                if (!customMealNames.add(customName)) {
                    throw BizException.badRequest("自定义餐次名称不能重复");
                }
            } else if (!builtinMealTypes.add(schedule.getMealType())) {
                throw BizException.badRequest("同一计划内内置餐次不能重复");
            }
            if (schedule.getExpectedCount() == null || schedule.getExpectedCount() <= 0) {
                throw BizException.badRequest("就餐人数必须大于0");
            }
            if (schedule.getRecipes() == null || schedule.getRecipes().isEmpty()) {
                throw BizException.badRequest("每个餐次至少需要选择一道菜谱");
            }
            Set<Long> recipeIds = new java.util.HashSet<>();
            for (RecipePlanItemDTO itemDTO : schedule.getRecipes()) {
                if (itemDTO == null || itemDTO.getRecipeId() == null) {
                    throw BizException.badRequest("菜谱不能为空");
                }
                if (!recipeIds.add(itemDTO.getRecipeId())) {
                    throw BizException.badRequest("同一餐次下菜谱不能重复");
                }
                if (itemDTO.getPlannedServings() == null || itemDTO.getPlannedServings() <= 0) {
                    throw BizException.badRequest("计划份数必须大于0");
                }
            }
        }
    }

    private List<Long> collectRecipeIds(List<PlanMealScheduleData> schedules) {
        if (schedules == null || schedules.isEmpty()) {
            return List.of();
        }
        return schedules.stream()
            .flatMap(schedule -> schedule.getRecipes().stream())
            .map(RecipePlanItemDTO::getRecipeId)
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private void applyPlanBaseFields(RecipePlan plan, RecipePlanCreateDTO dto, List<PlanMealScheduleData> schedules) {
        plan.setPlanDate(dto.getPlanDate());
        plan.setStartDate(dto.getStartDate());
        plan.setEndDate(dto.getEndDate());
        plan.setOrgId(this.resolvePlanOrgId(plan, dto));
        plan.setTargetGroup(StrUtil.emptyToNull(StrUtil.trim(dto.getTargetGroup())));
        plan.setHealthStatus(StrUtil.emptyToNull(StrUtil.trim(dto.getHealthStatus())));
        plan.setDietRestrictions(StrUtil.emptyToNull(StrUtil.trim(dto.getDietRestrictions())));
        // 忽略客户端传入的 AI 营养 JSON，保留旧值，待服务端按最新计划内容重算成功后再覆盖。
        plan.setRemark(StrUtil.emptyToNull(StrUtil.trim(dto.getRemark())));
        if (dto.getUseAiRecommend() != null) {
            plan.setUseAiRecommend(dto.getUseAiRecommend());
        }
        if (schedules.size() == 1) {
            PlanMealScheduleData schedule = schedules.get(0);
            plan.setMealType(schedule.getMealType());
            plan.setExpectedCount(schedule.getExpectedCount());
        } else {
            plan.setMealType("multi");
            plan.setExpectedCount(null);
        }
    }

    private Long resolvePlanOrgId(RecipePlan plan, RecipePlanCreateDTO dto) {
        Long requestedOrgId = dto.getOrgId();
        Long currentOrgId = UserContext.getOrgId();
        if (requestedOrgId == null) {
            return currentOrgId;
        }

        if (plan != null && plan.getId() != null && plan.getOrgId() != null && !Objects.equals(plan.getOrgId(), requestedOrgId)) {
            this.ensurePlanOrgChangeAllowed(plan.getId());
        }

        this.ensureSelectableCanteenOrg(requestedOrgId);
        return requestedOrgId;
    }

    private void ensureSelectableCanteenOrg(Long orgId) {
        DataScopeResult scope = this.dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权选择该实施组织");
        }

        try {
            Map<String, Object> orgInfo = this.jdbcTemplate.queryForMap(
                "SELECT org_type, deleted FROM sys_organization WHERE id = ?",
                orgId
            );
            Object deleted = orgInfo.get("deleted");
            Object orgType = orgInfo.get("org_type");
            if ((deleted instanceof Number && ((Number) deleted).intValue() != 0) || !"canteen".equals(orgType)) {
                throw BizException.badRequest("实施组织仅允许选择食堂类型组织");
            }
        }
        catch (BizException ex) {
            throw ex;
        }
        catch (Exception ex) {
            throw BizException.badRequest("实施组织不存在或不可用");
        }
    }

    private void ensurePlanOrgChangeAllowed(Long planId) {
        Long cookTaskCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cook_task WHERE plan_id = ? AND deleted = 0",
            Long.class,
            planId
        );
        if (cookTaskCount != null && cookTaskCount > 0) {
            throw BizException.conflict("计划已生成烹饪任务，不能修改实施组织");
        }

        Long outboundCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wms_outbound_order WHERE source_order_id = ? AND deleted = 0",
            Long.class,
            planId
        );
        if (outboundCount != null && outboundCount > 0) {
            throw BizException.conflict("计划已关联出库单，不能修改实施组织");
        }
    }

    private void replacePlanItemsBySchedules(Long planId, List<PlanMealScheduleData> schedules) {
        this.planItemMapper.delete(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId));
        int globalSortOrder = 1;
        for (PlanMealScheduleData schedule : schedules) {
            int recipeSortOrder = 1;
            for (RecipePlanItemDTO itemDTO : schedule.getRecipes()) {
                RecipePlanItem item = new RecipePlanItem();
                BeanUtils.copyProperties(itemDTO, item);
                item.setPlanId(planId);
                item.setSortOrder(itemDTO.getSortOrder() != null ? itemDTO.getSortOrder() : recipeSortOrder);
                item.setMealKey(schedule.getMealKey());
                item.setMealType(schedule.getMealType());
                item.setMealName(this.resolveMealScheduleName(schedule.getMealType(), schedule.getMealName()));
                item.setMealExpectedCount(schedule.getExpectedCount());
                item.setMealSortOrder(schedule.getSortOrder());
                item.setStatus("pending");
                Recipe recipe = (Recipe)this.recipeMapper.selectById(itemDTO.getRecipeId());
                if (recipe != null) {
                    RecipeCategory category;
                    item.setRecipeName(recipe.getRecipeName());
                    item.setRecipeCode(recipe.getRecipeCode());
                    item.setUnitCost(recipe.getUnitCost());
                    if (recipe.getCategoryId() != null && (category = this.getVisibleCategoryById(recipe.getCategoryId())) != null) {
                        item.setCategoryName(category.getCategoryName());
                    }
                    if (recipe.getUnitCost() != null && itemDTO.getPlannedServings() != null) {
                        item.setTotalCost(recipe.getUnitCost().multiply(BigDecimal.valueOf(itemDTO.getPlannedServings().intValue())));
                    }
                }
                item.setSortOrder(globalSortOrder++);
                this.planItemMapper.insert(item);
                recipeSortOrder++;
            }
        }
    }

    private String resolveMealScheduleName(String mealType, String mealName) {
        if ("custom".equals(mealType)) {
            return StrUtil.trim(mealName);
        }
        if (StrUtil.isNotBlank(mealName)) {
            return StrUtil.trim(mealName);
        }
        return this.getMealTypeDisplayName(mealType);
    }

    private List<AdjustmentMealScheduleSnapshot> buildAdjustmentMealSchedulesFromItems(List<RecipePlanItem> items, RecipePlan plan) {
        Map<String, AdjustmentMealScheduleSnapshot> scheduleMap = new LinkedHashMap<>();
        if (items == null) {
            return new ArrayList<>();
        }
        for (RecipePlanItem item : items) {
            String mealKey = StrUtil.blankToDefault(item.getMealKey(), "legacy-" + StrUtil.blankToDefault(item.getMealType(), plan.getMealType()));
            AdjustmentMealScheduleSnapshot schedule = scheduleMap.computeIfAbsent(mealKey, key -> {
                AdjustmentMealScheduleSnapshot snapshot = new AdjustmentMealScheduleSnapshot();
                snapshot.setMealKey(mealKey);
                snapshot.setMealType(StrUtil.blankToDefault(item.getMealType(), plan.getMealType()));
                snapshot.setMealName(this.resolveMealScheduleName(item.getMealType(), item.getMealName()));
                snapshot.setExpectedCount(item.getMealExpectedCount() != null ? item.getMealExpectedCount() : plan.getExpectedCount());
                snapshot.setSortOrder(item.getMealSortOrder() != null ? item.getMealSortOrder() : 1);
                snapshot.setRecipes(new ArrayList<>());
                return snapshot;
            });
            AdjustmentPlanRecipeSnapshot recipeSnapshot = new AdjustmentPlanRecipeSnapshot();
            recipeSnapshot.setRecipeId(item.getRecipeId());
            recipeSnapshot.setRecipeName(item.getRecipeName());
            recipeSnapshot.setCategoryName(item.getCategoryName());
            recipeSnapshot.setPlannedServings(item.getPlannedServings());
            recipeSnapshot.setRemark(item.getRemark());
            recipeSnapshot.setSortOrder(item.getSortOrder());
            recipeSnapshot.setMealKey(schedule.getMealKey());
            recipeSnapshot.setMealType(schedule.getMealType());
            recipeSnapshot.setMealName(schedule.getMealName());
            recipeSnapshot.setMealExpectedCount(schedule.getExpectedCount());
            recipeSnapshot.setMealSortOrder(schedule.getSortOrder());
            schedule.getRecipes().add(recipeSnapshot);
        }
        return scheduleMap.values().stream()
            .sorted(java.util.Comparator.comparing(AdjustmentMealScheduleSnapshot::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo)))
            .toList();
    }

    private List<AdjustmentMealScheduleSnapshot> buildAdjustmentMealSchedulesFromPlanSchedules(List<PlanMealScheduleData> schedules) {
        if (schedules == null) {
            return new ArrayList<>();
        }
        List<AdjustmentMealScheduleSnapshot> snapshots = new ArrayList<>();
        for (PlanMealScheduleData schedule : schedules) {
            AdjustmentMealScheduleSnapshot snapshot = new AdjustmentMealScheduleSnapshot();
            snapshot.setMealKey(schedule.getMealKey());
            snapshot.setMealType(schedule.getMealType());
            snapshot.setMealName(this.resolveMealScheduleName(schedule.getMealType(), schedule.getMealName()));
            snapshot.setExpectedCount(schedule.getExpectedCount());
            snapshot.setSortOrder(schedule.getSortOrder());
            List<AdjustmentPlanRecipeSnapshot> recipeSnapshots = new ArrayList<>();
            for (RecipePlanItemDTO recipeDTO : schedule.getRecipes()) {
                AdjustmentPlanRecipeSnapshot recipeSnapshot = new AdjustmentPlanRecipeSnapshot();
                recipeSnapshot.setRecipeId(recipeDTO.getRecipeId());
                Recipe recipe = this.recipeMapper.selectById(recipeDTO.getRecipeId());
                recipeSnapshot.setRecipeName(recipe != null ? recipe.getRecipeName() : null);
                recipeSnapshot.setCategoryName(recipe != null ? this.resolveRecipeCategoryName(recipe) : null);
                recipeSnapshot.setPlannedServings(recipeDTO.getPlannedServings());
                recipeSnapshot.setRemark(recipeDTO.getRemark());
                recipeSnapshot.setSortOrder(recipeDTO.getSortOrder());
                recipeSnapshot.setMealKey(snapshot.getMealKey());
                recipeSnapshot.setMealType(snapshot.getMealType());
                recipeSnapshot.setMealName(snapshot.getMealName());
                recipeSnapshot.setMealExpectedCount(snapshot.getExpectedCount());
                recipeSnapshot.setMealSortOrder(snapshot.getSortOrder());
                recipeSnapshots.add(recipeSnapshot);
            }
            snapshot.setRecipes(recipeSnapshots);
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private List<AdjustmentPlanRecipeSnapshot> flattenAdjustmentRecipes(List<AdjustmentMealScheduleSnapshot> mealSchedules) {
        if (mealSchedules == null) {
            return new ArrayList<>();
        }
        return mealSchedules.stream()
            .flatMap(schedule -> schedule.getRecipes() == null ? java.util.stream.Stream.empty() : schedule.getRecipes().stream())
            .toList();
    }

    private String buildAdjustmentMealSummary(List<AdjustmentMealScheduleSnapshot> mealSchedules) {
        if (mealSchedules == null || mealSchedules.isEmpty()) {
            return "\u2014";
        }
        return mealSchedules.stream()
            .sorted(java.util.Comparator.comparing(AdjustmentMealScheduleSnapshot::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo)))
            .map(schedule -> StrUtil.blankToDefault(schedule.getMealName(), this.getMealTypeDisplayName(schedule.getMealType())) + "(" + schedule.getExpectedCount() + "\u4eba)")
            .collect(Collectors.joining(" / "));
    }

    private String buildAdjustmentRecipeSummary(List<AdjustmentMealScheduleSnapshot> mealSchedules) {
        if (mealSchedules == null || mealSchedules.isEmpty()) {
            return "\u2014";
        }
        return mealSchedules.stream()
            .sorted(java.util.Comparator.comparing(AdjustmentMealScheduleSnapshot::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo)))
            .map(schedule -> {
                String recipes = (schedule.getRecipes() == null || schedule.getRecipes().isEmpty())
                    ? "\u65e0"
                    : schedule.getRecipes().stream()
                        .map(recipe -> this.buildRecipeDisplayName(recipe) + "(" + recipe.getPlannedServings() + "\u4efd)")
                        .collect(Collectors.joining("\u3001"));
                return StrUtil.blankToDefault(schedule.getMealName(), this.getMealTypeDisplayName(schedule.getMealType())) + ": " + recipes;
            })
            .collect(Collectors.joining(" / "));
    }

    private List<PlanMealScheduleData> convertSnapshotMealSchedules(List<AdjustmentMealScheduleSnapshot> mealSchedules) {
        if (mealSchedules == null || mealSchedules.isEmpty()) {
            return new ArrayList<>();
        }
        List<PlanMealScheduleData> schedules = new ArrayList<>();
        for (AdjustmentMealScheduleSnapshot mealSchedule : mealSchedules) {
            PlanMealScheduleData schedule = new PlanMealScheduleData();
            schedule.setMealKey(StrUtil.blankToDefault(mealSchedule.getMealKey(), "snapshot-" + UUID.randomUUID()));
            schedule.setMealType(mealSchedule.getMealType());
            schedule.setMealName(mealSchedule.getMealName());
            schedule.setExpectedCount(mealSchedule.getExpectedCount());
            schedule.setSortOrder(mealSchedule.getSortOrder());
            List<RecipePlanItemDTO> recipes = new ArrayList<>();
            if (mealSchedule.getRecipes() != null) {
                for (AdjustmentPlanRecipeSnapshot recipeSnapshot : mealSchedule.getRecipes()) {
                    RecipePlanItemDTO itemDTO = new RecipePlanItemDTO();
                    itemDTO.setRecipeId(recipeSnapshot.getRecipeId());
                    itemDTO.setPlannedServings(recipeSnapshot.getPlannedServings());
                    itemDTO.setSortOrder(recipeSnapshot.getSortOrder());
                    itemDTO.setRemark(recipeSnapshot.getRemark());
                    recipes.add(itemDTO);
                }
            }
            schedule.setRecipes(recipes);
            schedules.add(schedule);
        }
        return schedules;
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public Long create(RecipePlanCreateDTO dto) {
        List<PlanMealScheduleData> mealSchedules = this.resolveMealSchedules(dto);
        this.materialCategoryCoefficientLockService.assertUnlockedByRecipeIds(this.collectRecipeIds(mealSchedules), "\u521b\u5efa\u83dc\u8c31\u8ba1\u5212");
        RecipePlan plan = new RecipePlan();
        this.applyPlanBaseFields(plan, dto, mealSchedules);
        plan.setPlanCode(this.generatePlanCode());
        plan.setStatus("draft");
        plan.setCreatedAt(LocalDateTime.now());
        ((RecipePlanMapper)this.baseMapper).insert(plan);
        this.replacePlanItemsBySchedules(plan.getId(), mealSchedules);
        this.calculateTotals(plan);
        this.updateById(plan);
        this.refreshPersistedAiNutritionAssessment(plan.getId());
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public RecipePlanDetailVO update(Long id, RecipePlanCreateDTO dto) {
        RecipePlan plan = (RecipePlan)this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if (!"draft".equals(plan.getStatus()) && !"rejected".equals(plan.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u8349\u7a3f\u6216\u5df2\u9a73\u56de\u72b6\u6001\u7684\u8ba1\u5212\u53ef\u4ee5\u4fee\u6539");
        }
        String originalStatus = plan.getStatus();
        List<PlanMealScheduleData> mealSchedules = this.resolveMealSchedules(dto);
        this.materialCategoryCoefficientLockService.assertUnlockedByRecipeIds(this.collectRecipeIds(mealSchedules), "\u7f16\u8f91\u83dc\u8c31\u8ba1\u5212");
        this.applyPlanBaseFields(plan, dto, mealSchedules);
        this.replacePlanItemsBySchedules(id, mealSchedules);
        this.calculateTotals(plan);
        plan.setUpdatedAt(LocalDateTime.now());
        // 驳回后保存为草稿：重置审核字段
        if ("rejected".equals(originalStatus)) {
            plan.setStatus("draft");
            plan.setSubmittedBy(null);
            plan.setSubmittedAt(null);
            plan.setAuditedBy(null);
            plan.setAuditedAt(null);
            plan.setAuditRemark(null);
            this.logAuditAction(id, this.getCurrentRound(id), "save_draft", "\u9a73\u56de\u540e\u4fdd\u5b58\u4e3a\u8349\u7a3f");
        }
        this.updateById(plan);
        this.refreshPersistedAiNutritionAssessment(id);
        return this.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public void delete(Long id) {
        RecipePlan plan = (RecipePlan)this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        // 删除前置校验 — 4步校验（PRD 第10节）
        String deleteBlockMsg = "\u8be5\u83dc\u8c31\u8ba1\u5212\u5df2\u63d0\u4ea4\u5ba1\u6279\u3001\u5df2\u6267\u884c\u98df\u6750\u51fa\u5e93\u6216\u5df2\u751f\u6210\u70f9\u996a\u4efb\u52a1\uff0c\u4e3a\u4fdd\u8bc1\u5e93\u5b58\u51c6\u786e\u4e0e\u6eaf\u6e90\u5b8c\u6574\uff0c\u4e0d\u5141\u8bb8\u5220\u9664\u3002";

        // 1. 草稿状态校验
        if (!"draft".equals(plan.getStatus())) {
            throw new BizException(deleteBlockMsg);
        }

        // 2. 审批流转记录校验
        Long auditLogCount = this.auditLogMapper.selectCount(
            new LambdaQueryWrapper<RecipePlanAuditLog>()
                .eq(RecipePlanAuditLog::getPlanId, id)
                .in(RecipePlanAuditLog::getAction, "submit", "approve"));
        if (auditLogCount != null && auditLogCount > 0) {
            throw new BizException(deleteBlockMsg);
        }

        // 3. 食材出库、库存扣减校验
        Long outboundCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wms_outbound_order WHERE source_order_id = ? AND deleted = 0",
            Long.class, id);
        if (outboundCount != null && outboundCount > 0) {
            throw new BizException(deleteBlockMsg);
        }

        // 4. 烹饪任务执行记录校验
        Long completedTaskCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cook_task WHERE plan_id = ? AND status = 'completed' AND deleted = 0",
            Long.class, id);
        if (completedTaskCount != null && completedTaskCount > 0) {
            throw new BizException(deleteBlockMsg);
        }

        // 删除前记录审计日志（在数据删除前写入）
        this.logAuditAction(id, this.getCurrentRound(id), "delete", "\u5220\u9664\u83dc\u8c31\u8ba1\u5212");

        this.planItemMapper.delete(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, id));
        ((RecipePlanMapper)this.baseMapper).deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public CopyPlanResultVO copyPlan(Long sourceId) {
        // 1. 加载源计划
        RecipePlan source = (RecipePlan) this.getById(sourceId);
        if (source == null) {
            throw BizException.notFound("菜谱计划不存在");
        }

        // 2. 调整中的计划不允许复制
        RecipePlanAdjustment latestAdj = this.loadLatestAdjustment(sourceId);
        if (latestAdj != null && "pending".equals(latestAdj.getStatus())) {
            throw BizException.badRequest("正在调整中的计划不允许复制，请等待审核完成");
        }

        // 3. 加载源计划的 plan items
        List<RecipePlanItem> sourceItems = this.planItemMapper.selectList(
            new LambdaQueryWrapper<RecipePlanItem>()
                .eq(RecipePlanItem::getPlanId, sourceId)
                .eq(RecipePlanItem::getDeleted, 0)
                .orderByAsc(RecipePlanItem::getSortOrder)
                .orderByAsc(RecipePlanItem::getId));

        // 4. 逐条检查菜谱可用性
        List<CopyPlanResultVO.RecipeAnomalyItem> anomalyItems = new ArrayList<>();
        List<RecipePlanItem> validItems = new ArrayList<>();
        for (RecipePlanItem item : sourceItems) {
            Recipe recipe = (Recipe) this.recipeMapper.selectById(item.getRecipeId());
            if (recipe == null || (recipe.getDeleted() != null && recipe.getDeleted() == 1)) {
                anomalyItems.add(CopyPlanResultVO.RecipeAnomalyItem.builder()
                    .recipeId(item.getRecipeId())
                    .recipeName(item.getRecipeName())
                    .anomalyType("deleted")
                    .anomalyMessage("菜谱已被删除")
                    .build());
            } else if (!"active".equals(recipe.getStatus())) {
                anomalyItems.add(CopyPlanResultVO.RecipeAnomalyItem.builder()
                    .recipeId(item.getRecipeId())
                    .recipeName(item.getRecipeName())
                    .anomalyType("disabled")
                    .anomalyMessage("菜谱已被停用")
                    .build());
            } else {
                validItems.add(item);
            }
        }

        // 5. 创建新计划（仅包含有效菜谱）
        RecipePlan newPlan = new RecipePlan();
        newPlan.setPlanCode(this.generatePlanCode());
        newPlan.setStatus("draft");
        newPlan.setPlanDate(source.getPlanDate());
        newPlan.setStartDate(source.getStartDate());
        newPlan.setEndDate(source.getEndDate());
        newPlan.setMealType(source.getMealType());
        newPlan.setExpectedCount(source.getExpectedCount());
        newPlan.setTargetGroup(source.getTargetGroup());
        newPlan.setHealthStatus(source.getHealthStatus());
        newPlan.setDietRestrictions(source.getDietRestrictions());
        newPlan.setRemark(source.getRemark());
        newPlan.setOrgId(source.getOrgId());
        newPlan.setTenantId(source.getTenantId());
        newPlan.setTotalServings(0);
        newPlan.setEstimatedCost(BigDecimal.ZERO);
        newPlan.setDeleted(0);
        newPlan.setCreatedAt(LocalDateTime.now());
        newPlan.setUpdatedAt(LocalDateTime.now());
        try {
            Long userId = UserContext.getUserId();
            newPlan.setCreatedBy(userId);
            newPlan.setUpdatedBy(userId);
        } catch (Exception ignored) {}
        ((RecipePlanMapper) this.baseMapper).insert(newPlan);

        // 6. 复制有效的 plan items
        for (RecipePlanItem srcItem : validItems) {
            RecipePlanItem newItem = new RecipePlanItem();
            newItem.setPlanId(newPlan.getId());
            newItem.setRecipeId(srcItem.getRecipeId());
            newItem.setRecipeName(srcItem.getRecipeName());
            newItem.setRecipeCode(srcItem.getRecipeCode());
            newItem.setCategoryName(srcItem.getCategoryName());
            newItem.setMealKey(srcItem.getMealKey());
            newItem.setMealType(srcItem.getMealType());
            newItem.setMealName(srcItem.getMealName());
            newItem.setMealExpectedCount(srcItem.getMealExpectedCount());
            newItem.setMealSortOrder(srcItem.getMealSortOrder());
            newItem.setPlannedServings(srcItem.getPlannedServings());
            newItem.setCookedServings(0);
            newItem.setUnitCost(srcItem.getUnitCost());
            newItem.setTotalCost(srcItem.getTotalCost());
            newItem.setSortOrder(srcItem.getSortOrder());
            newItem.setRemark(srcItem.getRemark());
            newItem.setStatus("pending");
            newItem.setDeleted(0);
            newItem.setCreatedAt(LocalDateTime.now());
            newItem.setUpdatedAt(LocalDateTime.now());
            this.planItemMapper.insert(newItem);
        }

        // 7. 计算总量
        this.calculateTotals(newPlan);
        this.updateById(newPlan);

        // 8. 记录审计日志
        this.logAuditAction(newPlan.getId(), 1, "copy",
            "复制自菜谱计划[" + source.getPlanCode() + "]");

        return CopyPlanResultVO.builder()
            .newPlanId(newPlan.getId())
            .newPlanCode(newPlan.getPlanCode())
            .hasAnomalies(!anomalyItems.isEmpty())
            .invalidRecipeCount(anomalyItems.size())
            .anomalyItems(anomalyItems)
            .build();
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public void withdraw(Long planId, String reason) {
        RecipePlan plan = (RecipePlan) this.getById(planId);
        if (plan == null) {
            throw BizException.notFound("菜谱计划不存在");
        }
        if (!"approved".equals(plan.getStatus())) {
            throw BizException.badRequest("只有已审核的计划可以撤回");
        }

        Long activeTaskCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cook_task WHERE plan_id = ? AND status IN ('in_progress', 'completed') AND deleted = 0",
            Long.class, planId);
        if (activeTaskCount != null && activeTaskCount > 0) {
            throw BizException.conflict("存在已执行的烹饪任务，无法撤回");
        }

        Long executedOutboundCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM wms_outbound_order WHERE source_order_id = ? AND status NOT IN ('draft', 'cancelled') AND deleted = 0",
            Long.class, planId);
        if (executedOutboundCount != null && executedOutboundCount > 0) {
            throw BizException.conflict("已有出库单执行，无法撤回");
        }

        Long sampleCount = this.jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sample_record sr INNER JOIN cook_task ct ON sr.task_id = ct.id " +
            "WHERE ct.plan_id = ? AND sr.status NOT IN ('pending_sample', 'voided') AND sr.deleted = 0",
            Long.class, planId);
        if (sampleCount != null && sampleCount > 0) {
            throw BizException.conflict("存在关联的留样记录，无法撤回");
        }

        int cancelledTaskCount = this.cookTaskService.cancelPendingFutureTasksByPlanId(planId);
        this.inventoryValidationService.releaseReservedStock(planId);

        plan.setStatus("draft");
        plan.setSubmittedBy(null);
        plan.setSubmittedAt(null);
        plan.setAuditedBy(null);
        plan.setAuditedAt(null);
        plan.setAuditRemark(null);
        plan.setUpdatedAt(LocalDateTime.now());
        this.updateById(plan);

        this.logAuditAction(
            planId,
            this.getCurrentRound(planId),
            "withdraw",
            String.format("撤回至草稿，仅取消未来未执行任务%d个。撤回原因: %s", cancelledTaskCount, reason)
        );
    }

    /**
     * 级联作废下游留样/销样记录
     */
    private void cascadeVoidDownstream(Long planId, String reason) {
        // 查询所有非取消的烹饪任务
        List<Map<String, Object>> tasks = this.jdbcTemplate.queryForList(
            "SELECT id, status FROM cook_task WHERE plan_id = ? AND deleted = 0", planId);
        for (Map<String, Object> task : tasks) {
            Long taskId = ((Number) task.get("id")).longValue();
            // 作废该任务下的未作废留样记录
            this.jdbcTemplate.update(
                "UPDATE sample_record SET status = 'voided', supplement_remark = CONCAT(COALESCE(supplement_remark, ''), ?) " +
                "WHERE task_id = ? AND status NOT IN ('voided', 'disposed', 'archived') AND deleted = 0",
                " [系统级联作废: " + reason + "]", taskId);
        }
    }

    @Override
    public BatchOperationResultVO batchDelete(List<Long> planIds) {
        String batchId = "BD-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        List<BatchOperationResultVO.BatchItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Long planId : planIds) {
            String planCode = "";
            try {
                RecipePlan plan = this.getById(planId);
                planCode = plan != null ? plan.getPlanCode() : "ID:" + planId;
                selfProxy.delete(planId);
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(true).build());
                successCount++;
            } catch (BizException e) {
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(false)
                        .failCategory("business_rule").failReason(e.getMessage()).build());
                failCount++;
            } catch (Exception e) {
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(false)
                        .failCategory("system_error").failReason(e.getMessage()).build());
                failCount++;
            }
        }

        return BatchOperationResultVO.builder()
                .batchId(batchId).totalCount(planIds.size())
                .successCount(successCount).failCount(failCount)
                .results(results).build();
    }

    @Override
    public BatchOperationResultVO batchAudit(List<Long> planIds, String status, String remark) {
        String batchId = "BA-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        List<BatchOperationResultVO.BatchItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (Long planId : planIds) {
            String planCode = "";
            try {
                RecipePlan plan = this.getById(planId);
                planCode = plan != null ? plan.getPlanCode() : "ID:" + planId;
                selfProxy.audit(planId, status, remark);
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(true).build());
                successCount++;
            } catch (BizException e) {
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(false)
                        .failCategory("business_rule").failReason(e.getMessage()).build());
                failCount++;
            } catch (Exception e) {
                results.add(BatchOperationResultVO.BatchItemResult.builder()
                        .planId(planId).planCode(planCode).success(false)
                        .failCategory("system_error").failReason(e.getMessage()).build());
                failCount++;
            }
        }

        return BatchOperationResultVO.builder()
                .batchId(batchId).totalCount(planIds.size())
                .successCount(successCount).failCount(failCount)
                .results(results).build();
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public String submit(Long id) {
        RecipePlan plan = (RecipePlan)this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if (!"draft".equals(plan.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u8349\u7a3f\u72b6\u6001\u7684\u8ba1\u5212\u53ef\u4ee5\u63d0\u4ea4\u5ba1\u6838");
        }
        this.materialCategoryCoefficientLockService.assertUnlockedByRecipeIds(this.loadPlanRecipeIds(id), "\u63d0\u4ea4\u83dc\u8c31\u8ba1\u5212");
        StockValidationDTO validation = this.validateStockSafely(id, "\u63d0\u4ea4\u83dc\u8c31\u8ba1\u5212");
        this.inventoryValidationService.reserveStock(id);
        plan.setStatus("pending");
        plan.setSubmittedBy(UserContext.getUserId());
        plan.setSubmittedAt(LocalDateTime.now());
        this.applyStockRiskSnapshot(plan, validation, plan.getStatus());
        plan.setUpdatedAt(LocalDateTime.now());
        this.updateById(plan);
        this.logAuditAction(id, this.getCurrentRound(id) + 1, "submit", null);
        return plan.getStatus();
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public AuditResultVO audit(Long id, String status, String remark) {
        RecipePlan plan = (RecipePlan)this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if (!"pending".equals(plan.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u5f85\u5ba1\u6838\u72b6\u6001\u7684\u8ba1\u5212\u53ef\u4ee5\u8fdb\u884c\u5ba1\u6838");
        }
        if (!"approved".equals(status) && !"rejected".equals(status)) {
            throw new RuntimeException("\u5ba1\u6838\u72b6\u6001\u65e0\u6548");
        }
        if ("approved".equals(status)) {
            this.materialCategoryCoefficientLockService.assertUnlockedByRecipeIds(this.loadPlanRecipeIds(id), "\u5ba1\u6838\u901a\u8fc7\u5e76\u751f\u6210\u70f9\u996a\u6392\u73ed");
        }
        StockValidationDTO validation = this.validateStockSafely(id, "\u5ba1\u6838\u83dc\u8c31\u8ba1\u5212");
        plan.setStatus(status);
        plan.setAuditRemark(remark);
        plan.setAuditedBy(UserContext.getUserId());
        plan.setAuditedAt(LocalDateTime.now());
        this.applyStockRiskSnapshot(plan, validation, plan.getStatus());
        plan.setUpdatedAt(LocalDateTime.now());
        boolean cookTaskGenerated = false;
        if ("approved".equals(status)) {
            cookTaskGenerated = this.generateCookTasks(id);
            try {
                Object nutritionResult = this.getAiNutritionAssessment(id);
                if (nutritionResult instanceof AINutritionAssessmentVO) {
                    AINutritionAssessmentVO vo = (AINutritionAssessmentVO)nutritionResult;
                    plan.setNutritionPassRate(vo.getPassRate());
                    plan.setAiNutritionAssessment(this.objectMapper.writeValueAsString(nutritionResult));
                }
            }
            catch (Exception e) {
                log.warn("\u8ba1\u7b97\u8425\u517b\u8bc4\u4f30\u6570\u636e\u5931\u8d25, planId={}: {}", (Object)id, (Object)e.getMessage());
            }
        }
        if ("rejected".equals(status)) {
            this.inventoryValidationService.releaseReservedStock(id);
            this.cookTaskService.cancelTasksByPlanId(id);
        }
        this.updateById(plan);
        int round = this.getCurrentRound(id);
        this.logAuditAction(id, round, "rejected".equals(status) ? "reject" : "approve", remark);
        return AuditResultVO.builder().id(id).status(status).cookTaskGenerated(cookTaskGenerated).build();
    }

    private boolean generateCookTasks(Long planId) {
        List<CookTask> tasks = this.cookTaskService.generateTasksFromPlan(planId);
        log.info("\u4e3a\u8ba1\u5212[{}]\u751f\u6210{}\u4e2a\u70f9\u996a\u4efb\u52a1", (Object)planId, (Object)tasks.size());
        return !tasks.isEmpty();
    }

    @Override
    @Transactional(rollbackFor = {Exception.class})
    public RecipePlanDetailVO resubmit(Long id, RecipePlanCreateDTO dto) {
        RecipePlan plan = this.getById(id);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if (!"rejected".equals(plan.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u5df2\u9a73\u56de\u72b6\u6001\u7684\u8ba1\u5212\u53ef\u4ee5\u91cd\u65b0\u63d0\u4ea4");
        }

        // 内容变更检测
        String beforeData = this.getPlanSnapshotJson(id);
        String afterData = this.buildResubmitSnapshot(plan, dto);
        if (beforeData != null && afterData != null) {
            List<AdjustItemVO> changes = this.buildAdjustItems(beforeData, afterData, "modify");
            if (changes.isEmpty()) {
                throw BizException.badRequest("\u672a\u4fee\u6539\u4efb\u4f55\u5185\u5bb9\uff0c\u8bf7\u4fee\u6539\u540e\u518d\u91cd\u65b0\u63d0\u4ea4");
            }
        }

        List<PlanMealScheduleData> mealSchedules = this.resolveMealSchedules(dto);
        this.materialCategoryCoefficientLockService.assertUnlockedByRecipeIds(
            this.collectRecipeIds(mealSchedules),
            "\u91cd\u65b0\u63d0\u4ea4\u83dc\u8c31\u8ba1\u5212");

        // 更新计划内容
        this.applyPlanBaseFields(plan, dto, mealSchedules);
        this.replacePlanItemsBySchedules(id, mealSchedules);
        this.calculateTotals(plan);

        // 清除旧审核字段，设置新提交信息
        plan.setSubmittedBy(UserContext.getUserId());
        plan.setSubmittedAt(LocalDateTime.now());
        plan.setAuditedBy(null);
        plan.setAuditedAt(null);
        plan.setAuditRemark(null);
        plan.setStatus("pending");

        StockValidationDTO validation = this.validateStockSafely(id, "\u91cd\u65b0\u63d0\u4ea4\u83dc\u8c31\u8ba1\u5212");
        this.inventoryValidationService.reserveStock(id);
        this.applyStockRiskSnapshot(plan, validation, plan.getStatus());
        plan.setUpdatedAt(LocalDateTime.now());
        this.updateById(plan);
        this.refreshPersistedAiNutritionAssessment(id);

        int round = this.getCurrentRound(id) + 1;
        this.logAuditAction(id, round, "resubmit", "\u9a73\u56de\u540e\u91cd\u65b0\u63d0\u4ea4");

        return this.getDetail(id);
    }

    private String buildResubmitSnapshot(RecipePlan plan, RecipePlanCreateDTO dto) {
        try {
            List<PlanMealScheduleData> mealSchedules = this.resolveMealSchedules(dto);
            AdjustmentPlanSnapshot snapshot = new AdjustmentPlanSnapshot();
            snapshot.setPlanId(plan.getId());
            snapshot.setPlanCode(plan.getPlanCode());
            snapshot.setPlanDate(dto.getPlanDate() != null ? this.formatLocalDate(dto.getPlanDate()) : this.formatLocalDate(plan.getPlanDate()));
            snapshot.setStartDate(dto.getStartDate() != null ? this.formatLocalDate(dto.getStartDate()) : this.formatLocalDate(plan.getStartDate()));
            snapshot.setEndDate(dto.getEndDate() != null ? this.formatLocalDate(dto.getEndDate()) : this.formatLocalDate(plan.getEndDate()));
            snapshot.setMealType(mealSchedules.size() == 1 ? mealSchedules.get(0).getMealType() : "multi");
            snapshot.setExpectedCount(mealSchedules.size() == 1 ? mealSchedules.get(0).getExpectedCount() : null);
            snapshot.setTargetGroup(dto.getTargetGroup() != null ? dto.getTargetGroup() : plan.getTargetGroup());
            snapshot.setHealthStatus(dto.getHealthStatus() != null ? this.splitHealthStatuses(dto.getHealthStatus()) : this.splitHealthStatuses(plan.getHealthStatus()));
            snapshot.setDietRestrictions(dto.getDietRestrictions() != null ? dto.getDietRestrictions() : plan.getDietRestrictions());
            snapshot.setRemark(dto.getRemark() != null ? dto.getRemark() : plan.getRemark());
            snapshot.setUseAiRecommend(dto.getUseAiNutrition() != null ? dto.getUseAiNutrition() : plan.getUseAiRecommend());
            List<AdjustmentMealScheduleSnapshot> mealScheduleSnapshots = this.buildAdjustmentMealSchedulesFromPlanSchedules(mealSchedules);
            snapshot.setMealSchedules(mealScheduleSnapshots);
            snapshot.setRecipes(this.flattenAdjustmentRecipes(mealScheduleSnapshots));
            return this.objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<RecipePlanAuditLogVO> getAuditLog(Long planId) {
        List<RecipePlanAuditLog> logs = this.auditLogMapper.selectList(
            new LambdaQueryWrapper<RecipePlanAuditLog>()
                .eq(RecipePlanAuditLog::getPlanId, planId)
                .orderByAsc(RecipePlanAuditLog::getRound)
                .orderByAsc(RecipePlanAuditLog::getCreatedAt));
        Map<String, String> actionNameMap = Map.of(
            "submit", "提交审核",
            "resubmit", "重新提交",
            "approve", "审核通过",
            "reject", "审核驳回",
            "save_draft", "保存为草稿",
            "withdraw", "撤回计划");
        return logs.stream().map(log -> {
            RecipePlanAuditLogVO vo = new RecipePlanAuditLogVO();
            BeanUtils.copyProperties(log, vo);
            vo.setActionName(actionNameMap.getOrDefault(log.getAction(), log.getAction()));
            return vo;
        }).toList();
    }

    private void logAuditAction(Long planId, int round, String action, String remark) {
        RecipePlanAuditLog auditLog = new RecipePlanAuditLog();
        auditLog.setPlanId(planId);
        auditLog.setRound(round);
        auditLog.setAction(action);
        auditLog.setRemark(remark);
        auditLog.setCreatedAt(LocalDateTime.now());
        try {
            Long userId = UserContext.getUserId();
            auditLog.setOperatorId(userId);
            if (userId != null) {
                String name = this.resolveEmployeeName(userId);
                auditLog.setOperatorName(name);
            }
        } catch (Exception e) {
            log.warn("\u83b7\u53d6\u64cd\u4f5c\u4eba\u4fe1\u606f\u5931\u8d25: {}", e.getMessage());
        }
        this.auditLogMapper.insert(auditLog);
    }

    private int getCurrentRound(Long planId) {
        List<RecipePlanAuditLog> logs = this.auditLogMapper.selectList(
            new LambdaQueryWrapper<RecipePlanAuditLog>()
                .eq(RecipePlanAuditLog::getPlanId, planId)
                .orderByDesc(RecipePlanAuditLog::getRound)
                .last("LIMIT 1"));
        return logs.isEmpty() ? 0 : logs.get(0).getRound();
    }

   private List<Long> loadPlanRecipeIds(Long planId) {
      return planId == null
         ? List.of()
         : this.planItemMapper
            .selectList(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId).eq(RecipePlanItem::getDeleted, 0))
            .stream()
            .<Long>map(RecipePlanItem::getRecipeId)
            .distinct()
            .toList();
   }

    private void refreshPersistedAiNutritionAssessment(Long planId) {
        if (planId == null) {
            return;
        }
        try {
            this.getAiNutritionAssessment(planId);
        } catch (Exception e) {
            log.warn("重算菜谱计划AI营养评估失败, planId={}: {}", planId, e.getMessage());
        }
    }

    public Object getAiNutritionAssessment(Long id) {
        NutritionTargetConfig.GroupNutritionTarget target;
        RecipePlanDetailVO detail = this.getDetail(id);
        if (detail == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        List<RecipePlanDetailVO.RecipePlanItemVO> recipes = detail.getRecipes();
        if (recipes == null || recipes.isEmpty()) {
            return AINutritionAssessmentVO.builder()
                .planId(id)
                .overallScore(0)
                .assessment(null)
                .suggestions(List.of())
                .aiOptimizationSuggestions(null)
                .aiStatus("failed")
                .aiStatusMessage("\u6682\u65e0\u53ef\u5206\u6790\u7684\u83dc\u8c31\u6570\u636e\uff0c\u672a\u751f\u6210AI\u5efa\u8bae")
                .build();
        }
        String portraitType = detail.getTargetGroup();
        if (portraitType == null || portraitType.isEmpty()) {
            portraitType = "general";
        }
        if ((target = this.nutritionTargetConfig.getPerMealTarget(portraitType)) == null) {
            target = this.nutritionTargetConfig.getPerMealTarget("general");
        }
        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbohydrate = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        BigDecimal totalSodium = BigDecimal.ZERO;
        BigDecimal totalFiber = BigDecimal.ZERO;
        BigDecimal totalVitaminA = BigDecimal.ZERO;
        BigDecimal totalVitaminB1 = BigDecimal.ZERO;
        BigDecimal totalVitaminB2 = BigDecimal.ZERO;
        BigDecimal totalVitaminC = BigDecimal.ZERO;
        BigDecimal totalVitaminD = BigDecimal.ZERO;
        BigDecimal totalVitaminE = BigDecimal.ZERO;
        BigDecimal totalCalcium = BigDecimal.ZERO;
        BigDecimal totalIron = BigDecimal.ZERO;
        BigDecimal totalZinc = BigDecimal.ZERO;
        int totalServings = detail.getTotalServings() != null ? detail.getTotalServings() : 1;
        int recipeCount = 0;
        for (RecipePlanDetailVO.RecipePlanItemVO item : recipes) {
            Recipe recipe;
            if (item.getPlannedServings() == null || (recipe = (Recipe)this.recipeMapper.selectById(item.getRecipeId())) == null) continue;
            recipeNutritionSupportService.recalculateRecipeNutrition(recipe);

            int servings = item.getPlannedServings();
            ++recipeCount;
            BigDecimal multiplier = BigDecimal.valueOf(servings).multiply(BigDecimal.valueOf(1L));
            if (recipe.getCalories() != null) {
                totalCalories = totalCalories.add(recipe.getCalories().multiply(multiplier));
            }
            if (recipe.getProtein() != null) {
                totalProtein = totalProtein.add(recipe.getProtein().multiply(multiplier));
            }
            if (recipe.getCarbohydrate() != null) {
                totalCarbohydrate = totalCarbohydrate.add(recipe.getCarbohydrate().multiply(multiplier));
            }
            if (recipe.getFat() != null) {
                totalFat = totalFat.add(recipe.getFat().multiply(multiplier));
            }
            if (recipe.getSodium() != null) {
                totalSodium = totalSodium.add(recipe.getSodium().multiply(multiplier));
            }
            if (recipe.getFiber() != null) {
                totalFiber = totalFiber.add(recipe.getFiber().multiply(multiplier));
            }
            if (recipe.getVitaminA() != null) {
                totalVitaminA = totalVitaminA.add(recipe.getVitaminA().multiply(multiplier));
            }
            if (recipe.getVitaminB1() != null) {
                totalVitaminB1 = totalVitaminB1.add(recipe.getVitaminB1().multiply(multiplier));
            }
            if (recipe.getVitaminB2() != null) {
                totalVitaminB2 = totalVitaminB2.add(recipe.getVitaminB2().multiply(multiplier));
            }
            if (recipe.getVitaminC() != null) {
                totalVitaminC = totalVitaminC.add(recipe.getVitaminC().multiply(multiplier));
            }
            if (recipe.getVitaminD() != null) {
                totalVitaminD = totalVitaminD.add(recipe.getVitaminD().multiply(multiplier));
            }
            if (recipe.getVitaminE() != null) {
                totalVitaminE = totalVitaminE.add(recipe.getVitaminE().multiply(multiplier));
            }
            if (recipe.getCalcium() != null) {
                totalCalcium = totalCalcium.add(recipe.getCalcium().multiply(multiplier));
            }
            if (recipe.getIron() != null) {
                totalIron = totalIron.add(recipe.getIron().multiply(multiplier));
            }
            if (recipe.getZinc() == null) continue;
            totalZinc = totalZinc.add(recipe.getZinc().multiply(multiplier));
        }
        int score = this.calculateNutritionScore(totalCalories, totalProtein, totalCarbohydrate, totalFat, totalServings);
        AINutritionAssessmentVO.DietStructureAnalysis dietStructure = this.calculateDietStructure(totalProtein, totalCarbohydrate, totalFat, totalCalories);
        BigDecimal passRate = this.calculatePassRate(totalProtein, totalCarbohydrate, totalFat, totalFiber, totalVitaminA, totalVitaminC, totalCalcium, totalIron, totalServings);
        String grade = this.calculateNutritionGrade(score);
        String gradeDescription = this.getGradeDescription(grade);
        BigDecimal avgCalories = totalServings > 0 ? totalCalories.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgProtein = totalServings > 0 ? totalProtein.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgCarbohydrate = totalServings > 0 ? totalCarbohydrate.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgFat = totalServings > 0 ? totalFat.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgFiber = totalServings > 0 ? totalFiber.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal avgSodium = totalServings > 0 ? totalSodium.divide(BigDecimal.valueOf(totalServings), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        AINutritionAssessmentVO.PortraitInfo portraitInfo = this.buildPortraitInfo(portraitType, target);
        AINutritionAssessmentVO.SelectedRecipeNutrition selectedRecipeNutrition = AINutritionAssessmentVO.SelectedRecipeNutrition.builder().totalProtein(totalProtein.setScale(2, RoundingMode.HALF_UP)).proteinPerCapita(avgProtein).totalCarbs(totalCarbohydrate.setScale(2, RoundingMode.HALF_UP)).carbsPerCapita(avgCarbohydrate).totalFat(totalFat.setScale(2, RoundingMode.HALF_UP)).fatPerCapita(avgFat).totalCalories(totalCalories.intValue()).caloriesPerCapita(avgCalories.intValue()).totalFiber(totalFiber.setScale(2, RoundingMode.HALF_UP)).fiberPerCapita(avgFiber).totalSodium(totalSodium.setScale(2, RoundingMode.HALF_UP)).sodiumPerCapita(avgSodium).build();
        List<AINutritionAssessmentVO.NutritionComparison> nutritionComparison = this.buildNutritionComparisonList(avgProtein, avgCarbohydrate, avgFat, avgCalories, avgFiber, avgSodium, target);
        AINutritionAssessmentVO.NutritionBalanceScore nutritionBalanceScore = this.buildNutritionBalanceScore(score, grade, gradeDescription, avgProtein, avgCarbohydrate, avgFat, avgCalories, target);
        PlanAiNarrative narrative = this.generatePlanAiNarrative(id, detail, score, passRate, dietStructure, nutritionComparison,
            totalCalories, avgCalories, totalProtein, avgProtein, totalCarbohydrate, avgCarbohydrate, totalFat, avgFat, totalFiber, totalSodium, totalServings);
        AINutritionAssessmentVO assessment = AINutritionAssessmentVO.builder().planId(id).portraitInfo(portraitInfo).selectedRecipeNutrition(selectedRecipeNutrition).nutritionComparison(nutritionComparison).nutritionBalanceScore(nutritionBalanceScore).aiOptimizationSuggestions(narrative.summary).aiStatus(narrative.status).aiStatusMessage(narrative.message).overallScore(score).grade(grade).gradeDescription(gradeDescription).assessment(narrative.report).totalCalories(totalCalories.setScale(2, RoundingMode.HALF_UP)).totalProtein(totalProtein.setScale(2, RoundingMode.HALF_UP)).totalCarbohydrate(totalCarbohydrate.setScale(2, RoundingMode.HALF_UP)).totalFat(totalFat.setScale(2, RoundingMode.HALF_UP)).totalSodium(totalSodium.setScale(2, RoundingMode.HALF_UP)).totalFiber(totalFiber.setScale(2, RoundingMode.HALF_UP)).avgCalories(avgCalories).avgProtein(avgProtein).avgCarbohydrate(avgCarbohydrate).avgFat(avgFat).passRate(passRate).dietStructure(dietStructure).servingCount(totalServings).suggestions(narrative.suggestions).nutritionComparisons(this.buildNutritionComparisons(avgCalories, avgProtein, avgCarbohydrate, avgFat, totalServings)).build();
        this.persistPlanAiNutritionAssessment(id, assessment);
        return assessment;
    }

    private void persistPlanAiNutritionAssessment(Long planId, AINutritionAssessmentVO assessment) {
        if (planId == null || assessment == null) {
            return;
        }
        try {
            RecipePlan plan = new RecipePlan();
            plan.setId(planId);
            plan.setNutritionPassRate(assessment.getPassRate());
            plan.setAiNutritionAssessment(this.objectMapper.writeValueAsString(assessment));
            plan.setUpdatedAt(LocalDateTime.now());
            this.updateById(plan);
        } catch (Exception e) {
            log.warn("回写菜谱计划AI营养评估失败, planId={}: {}", planId, e.getMessage());
        }
    }

    private PlanAiNarrative generatePlanAiNarrative(Long planId,
                                                    RecipePlanDetailVO detail,
                                                    int score,
                                                    BigDecimal passRate,
                                                    AINutritionAssessmentVO.DietStructureAnalysis dietStructure,
                                                    List<AINutritionAssessmentVO.NutritionComparison> nutritionComparison,
                                                    BigDecimal totalCalories,
                                                    BigDecimal avgCalories,
                                                    BigDecimal totalProtein,
                                                    BigDecimal avgProtein,
                                                    BigDecimal totalCarbohydrate,
                                                    BigDecimal avgCarbohydrate,
                                                    BigDecimal totalFat,
                                                    BigDecimal avgFat,
                                                    BigDecimal totalFiber,
                                                    BigDecimal totalSodium,
                                                    int servingCount) {
        try {
            AiServiceConfig config = this.aiServiceConfigService.getActiveByModule(AiServiceType.TEXT, AiModuleCode.NUTRITION_SUGGESTION);
            String prompt = this.buildPlanAiPrompt(PlanNutritionAiPromptPayload.builder()
                .planId(planId)
                .targetGroupName(CharSequenceUtil.blankToDefault(detail.getTargetGroupName(), "普通成人"))
                .healthStatus(CharSequenceUtil.blankToDefault(detail.getHealthStatus(), "无"))
                .servingCount(servingCount)
                .recipes(detail.getRecipes() == null ? List.of() : detail.getRecipes().stream()
                    .map(item -> new PlanNutritionAiPromptPayload.PlanRecipeItem(item.getRecipeName(), item.getPlannedServings()))
                    .toList())
                .overallScore(score)
                .passRate(passRate)
                .dietStructureEvaluation(dietStructure == null ? null : dietStructure.getEvaluation())
                .metrics(new PlanNutritionAiPromptPayload.PlanNutritionMetrics(
                    totalCalories.setScale(2, RoundingMode.HALF_UP),
                    avgCalories.setScale(2, RoundingMode.HALF_UP),
                    totalProtein.setScale(2, RoundingMode.HALF_UP),
                    avgProtein.setScale(2, RoundingMode.HALF_UP),
                    totalCarbohydrate.setScale(2, RoundingMode.HALF_UP),
                    avgCarbohydrate.setScale(2, RoundingMode.HALF_UP),
                    totalFat.setScale(2, RoundingMode.HALF_UP),
                    avgFat.setScale(2, RoundingMode.HALF_UP),
                    totalFiber.setScale(2, RoundingMode.HALF_UP),
                    totalSodium.setScale(2, RoundingMode.HALF_UP)
                ))
                .comparisons(nutritionComparison == null ? List.of() : nutritionComparison.stream()
                    .map(item -> new PlanNutritionAiPromptPayload.PlanNutritionComparison(
                        item.getNutrientName(), item.getActualValue(), item.getTargetValue(), item.getStatus()
                    ))
                    .toList())
                .build());
            AiTextGenerateResult result = this.openAiCompatibleService.generateText(
                config,
                "你是智慧厨房平台的资深营养师。你根据菜谱计划的结构化营养分析结果，生成简洁、专业、可执行的中文建议。你必须只返回 JSON，不要返回 Markdown、代码块、解释说明或免责声明。",
                prompt,
                AiModuleCode.NUTRITION_SUGGESTION,
                "business"
            );
            if (!result.isSuccess() || CharSequenceUtil.isBlank(result.getContent())) {
                return new PlanAiNarrative(null, List.of(), null, "failed", "AI调用失败，未生成AI文本内容");
            }
            return this.parsePlanAiNarrative(result.getContent());
        } catch (BizException ex) {
            if (CharSequenceUtil.contains(ex.getMessage(), "未配置可用AI服务")) {
                return new PlanAiNarrative(null, List.of(), null, "not_configured", "未配置AI文本服务，请在系统管理 / AI接口管理中配置后重试");
            }
            log.warn("生成菜谱计划AI营养评估失败, planId={}: {}", planId, ex.getMessage());
            return new PlanAiNarrative(null, List.of(), null, "failed", "AI调用失败，未生成AI文本内容");
        } catch (Exception ex) {
            log.warn("生成菜谱计划AI营养评估失败, planId={}: {}", planId, ex.getMessage());
            return new PlanAiNarrative(null, List.of(), null, "failed", "AI调用失败，未生成AI文本内容");
        }
    }

    private String buildPlanAiPrompt(PlanNutritionAiPromptPayload payload) {
        try {
            String payloadJson = this.objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            return "请基于以下菜谱计划结构化营养分析结果，生成仅用于页面展示的 AI 文本内容。\n"
                + "你必须只返回 JSON 对象，不要返回 Markdown、代码块、额外说明、免责声明或表格。\n"
                + "返回 JSON Schema：\n"
                + "{\n"
                + "  \"optimizationSuggestions\": [\"建议1\", \"建议2\", \"建议3\"],\n"
                + "  \"nutritionAdvice\": \"100到150字的摘要建议\",\n"
                + "  \"assessmentReport\": \"300到600字的详细营养评估报告\"\n"
                + "}\n"
                + "输出要求：\n"
                + "1. optimizationSuggestions 返回 3 到 6 条中文短句，必须可执行，优先给出食材、份量、烹饪方式、搭配调整建议。\n"
                + "2. nutritionAdvice 为一段摘要，不重复原始输入，不编造不存在的数据。\n"
                + "3. assessmentReport 为详细报告，需优先指出不足项和过量项；若整体良好，也要给出维持建议。\n"
                + "4. 任一字段可为空字符串，但不要缺少字段。\n"
                + "业务输入数据如下：\n"
                + payloadJson;
        } catch (JsonProcessingException ex) {
            throw new BizException("营养分析提示词构造失败");
        }
    }

    private PlanAiNarrative parsePlanAiNarrative(String content) {
        try {
            String jsonText = this.extractJsonObject(content);
            ParsedNarrative parsed = this.parsePlanAiNarrativeJson(jsonText);
            if (parsed == null) {
                parsed = this.parsePlanAiNarrativeByRegex(jsonText);
            }
            if (parsed == null || (CharSequenceUtil.isBlank(parsed.summary()) && parsed.suggestions().isEmpty() && CharSequenceUtil.isBlank(parsed.report()))) {
                return new PlanAiNarrative(null, List.of(), null, "failed", "AI返回内容为空，未生成AI文本内容");
            }
            return new PlanAiNarrative(parsed.summary(), parsed.suggestions(), parsed.report(), "success", "AI营养建议生成成功");
        } catch (Exception ex) {
            return new PlanAiNarrative(null, List.of(), null, "failed", "AI返回解析失败，未生成AI文本内容");
        }
    }

    private ParsedNarrative parsePlanAiNarrativeJson(String jsonText) {
        JsonNode root = this.readAiJsonNode(jsonText);
        if (root == null) {
            return null;
        }
        String summary = firstNonBlank(
            root.path("nutritionAdvice").asText(""),
            root.path("summary").asText("")
        );
        String report = firstNonBlank(
            root.path("assessmentReport").asText(""),
            root.path("report").asText("")
        );
        List<String> suggestionItems = new ArrayList<>();
        JsonNode suggestionNode = root.has("optimizationSuggestions")
            ? root.path("optimizationSuggestions")
            : root.path("suggestions");
        if (suggestionNode.isArray()) {
            suggestionNode.forEach(item -> {
                String value = item.asText("");
                if (CharSequenceUtil.isNotBlank(value)) {
                    suggestionItems.add(value.trim());
                }
            });
        }
        return new ParsedNarrative(summary, normalizeAiSuggestions(suggestionItems), report);
    }

    private JsonNode readAiJsonNode(String jsonText) {
        try {
            return this.objectMapper.readTree(jsonText);
        } catch (Exception ignore) {
        }
        try {
            return LENIENT_AI_JSON_MAPPER.readTree(jsonText);
        } catch (Exception ignore) {
        }
        return null;
    }

    private ParsedNarrative parsePlanAiNarrativeByRegex(String content) {
        String summary = firstNonBlank(
            extractJsonStringField(content, "nutritionAdvice"),
            extractJsonStringField(content, "summary")
        );
        String report = firstNonBlank(
            extractJsonStringField(content, "assessmentReport"),
            extractJsonStringField(content, "report")
        );
        List<String> suggestions = extractJsonStringArray(content, "optimizationSuggestions");
        if (suggestions.isEmpty()) {
            suggestions = extractJsonStringArray(content, "suggestions");
        }
        return new ParsedNarrative(summary, normalizeAiSuggestions(suggestions), report);
    }

    private static List<String> normalizeAiSuggestions(List<String> suggestions) {
        return suggestions == null
            ? List.of()
            : suggestions.stream().map(String::trim).filter(CharSequenceUtil::isNotBlank).distinct().limit(8).toList();
    }

    private static String extractJsonStringField(String content, String fieldName) {
        Pattern pattern = Pattern.compile("(?:\\\"|" + Pattern.quote(fieldName) + "\\\"?\\s*:\\s*\\\"?)" + Pattern.quote(fieldName) + "\"?\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (!matcher.find()) {
            pattern = Pattern.compile("\"?" + Pattern.quote(fieldName) + "\"?\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", Pattern.DOTALL);
            matcher = pattern.matcher(content);
            if (!matcher.find()) {
                return null;
            }
        }
        return unescapeJsonLikeString(matcher.group(1));
    }

    private static List<String> extractJsonStringArray(String content, String fieldName) {
        Pattern arrayPattern = Pattern.compile("\"?" + Pattern.quote(fieldName) + "\"?\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
        Matcher arrayMatcher = arrayPattern.matcher(content);
        if (!arrayMatcher.find()) {
            return List.of();
        }
        String arrayBody = arrayMatcher.group(1);
        Matcher itemMatcher = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"").matcher(arrayBody);
        List<String> results = new ArrayList<>();
        while (itemMatcher.find()) {
            String value = unescapeJsonLikeString(itemMatcher.group(1));
            if (CharSequenceUtil.isNotBlank(value)) {
                results.add(value);
            }
        }
        return results;
    }

    private static String unescapeJsonLikeString(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = CharSequenceUtil.emptyToNull(StrUtil.trim(value));
            if (CharSequenceUtil.isNotBlank(normalized)) {
                return normalized;
            }
        }
        return null;
    }

    private String extractJsonObject(String content) {
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private static class PlanAiNarrative {
        private final String summary;
        private final List<String> suggestions;
        private final String report;
        private final String status;
        private final String message;

        private PlanAiNarrative(String summary, List<String> suggestions, String report, String status, String message) {
            this.summary = summary;
            this.suggestions = suggestions;
            this.report = report;
            this.status = status;
            this.message = message;
        }
    }

    private record ParsedNarrative(String summary, List<String> suggestions, String report) {
    }

    @lombok.Builder
    @lombok.Data
    private static class PlanNutritionAiPromptPayload {
        private Long planId;
        private String targetGroupName;
        private String healthStatus;
        private Integer servingCount;
        private List<PlanRecipeItem> recipes;
        private Integer overallScore;
        private BigDecimal passRate;
        private String dietStructureEvaluation;
        private PlanNutritionMetrics metrics;
        private List<PlanNutritionComparison> comparisons;

        @lombok.AllArgsConstructor
        @lombok.Data
        private static class PlanRecipeItem {
            private String recipeName;
            private Integer plannedServings;
        }

        @lombok.AllArgsConstructor
        @lombok.Data
        private static class PlanNutritionMetrics {
            private BigDecimal totalCalories;
            private BigDecimal avgCalories;
            private BigDecimal totalProtein;
            private BigDecimal avgProtein;
            private BigDecimal totalCarbohydrate;
            private BigDecimal avgCarbohydrate;
            private BigDecimal totalFat;
            private BigDecimal avgFat;
            private BigDecimal totalFiber;
            private BigDecimal totalSodium;
        }

        @lombok.AllArgsConstructor
        @lombok.Data
        private static class PlanNutritionComparison {
            private String nutrientName;
            private BigDecimal actualValue;
            private BigDecimal targetValue;
            private String status;
        }
    }

    private AINutritionAssessmentVO.PortraitInfo buildPortraitInfo(String portraitType, NutritionTargetConfig.GroupNutritionTarget target) {
        String portraitName = this.getPortraitName(portraitType);
        String dietaryRestrictions = this.getDietaryRestrictions(portraitType);
        AINutritionAssessmentVO.NutritionTargets nutritionTargets = AINutritionAssessmentVO.NutritionTargets.builder().proteinTarget(target.getProtein()).carbsTarget(target.getCarbohydrate()).fatTarget(target.getFat()).caloriesTarget(target.getCalories().intValue()).fiberTarget(target.getFiber()).sodiumTarget(BigDecimal.valueOf(2000L)).build();
        return AINutritionAssessmentVO.PortraitInfo.builder().portraitType(portraitType).portraitName(portraitName).nutritionTargets(nutritionTargets).dietaryRestrictions(dietaryRestrictions).build();
    }

    private String getPortraitName(String portraitType) {
        if (portraitType == null) {
            return "\u666e\u901a\u6210\u4eba";
        }
        return switch (portraitType) {
            case "elderly" -> "\u8001\u5e74\u4eba";
            case "child" -> "\u513f\u7ae5";
            case "patient" -> "\u75c5\u4eba";
            case "teenager" -> "\u9752\u5c11\u5e74";
            case "worker" -> "\u4f53\u529b\u52b3\u52a8\u8005";
            default -> "\u666e\u901a\u6210\u4eba";
        };
    }

    private String getDietaryRestrictions(String portraitType) {
        if (portraitType == null) {
            return "\u65e0\u7279\u6b8a\u996e\u98df\u9650\u5236";
        }
        return switch (portraitType) {
            case "elderly" -> "\u5efa\u8bae\u4f4e\u76d0\u4f4e\u8102\uff0c\u6613\u4e8e\u6d88\u5316\uff0c\u907f\u514d\u8fc7\u786c\u3001\u8fc7\u51b7\u98df\u7269";
            case "child" -> "\u907f\u514d\u8f9b\u8fa3\u523a\u6fc0\uff0c\u63a7\u5236\u7cd6\u5206\u6444\u5165\uff0c\u4fdd\u8bc1\u9499\u8d28\u548c\u86cb\u767d\u8d28";
            case "patient" -> "\u6839\u636e\u533b\u5631\u8c03\u6574\uff0c\u901a\u5e38\u9700\u8981\u6e05\u6de1\u6613\u6d88\u5316\uff0c\u63a7\u5236\u6cb9\u8102\u548c\u76d0\u5206";
            case "teenager" -> "\u4fdd\u8bc1\u5145\u8db3\u86cb\u767d\u8d28\u548c\u9499\u8d28\uff0c\u652f\u6301\u751f\u957f\u53d1\u80b2";
            case "worker" -> "\u589e\u52a0\u86cb\u767d\u8d28\u548c\u70ed\u91cf\u4f9b\u7ed9\uff0c\u8865\u5145\u4f53\u529b\u6d88\u8017";
            default -> "\u65e0\u7279\u6b8a\u996e\u98df\u9650\u5236";
        };
    }

    private List<AINutritionAssessmentVO.NutritionComparison> buildNutritionComparisonList(BigDecimal avgProtein, BigDecimal avgCarbs, BigDecimal avgFat, BigDecimal avgCalories, BigDecimal avgFiber, BigDecimal avgSodium, NutritionTargetConfig.GroupNutritionTarget target) {
        ArrayList<AINutritionAssessmentVO.NutritionComparison> comparisons = new ArrayList<AINutritionAssessmentVO.NutritionComparison>();
        comparisons.add(this.createNutritionComparison("\u86cb\u767d\u8d28", avgProtein, target.getProtein()));
        comparisons.add(this.createNutritionComparison("\u78b3\u6c34\u5316\u5408\u7269", avgCarbs, target.getCarbohydrate()));
        comparisons.add(this.createNutritionComparison("\u8102\u80aa", avgFat, target.getFat()));
        comparisons.add(this.createNutritionComparison("\u70ed\u91cf", avgCalories, target.getCalories()));
        comparisons.add(this.createNutritionComparison("\u81b3\u98df\u7ea4\u7ef4", avgFiber, target.getFiber()));
        comparisons.add(this.createNutritionComparison("\u94a0", avgSodium, BigDecimal.valueOf(2000L)));
        return comparisons;
    }

    private AINutritionAssessmentVO.NutritionComparison createNutritionComparison(String nutrientName, BigDecimal actualValue, BigDecimal targetValue) {
        BigDecimal percentage;
        if (actualValue == null) {
            actualValue = BigDecimal.ZERO;
        }
        if (targetValue == null) {
            targetValue = BigDecimal.ZERO;
        }
        BigDecimal bigDecimal = percentage = targetValue.compareTo(BigDecimal.ZERO) > 0 ? actualValue.divide(targetValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L)).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String comparisonStatus = percentage.compareTo(BigDecimal.valueOf(80L)) >= 0 && percentage.compareTo(BigDecimal.valueOf(120L)) <= 0 ? "adequate" : (percentage.compareTo(BigDecimal.valueOf(80L)) < 0 ? "insufficient" : "excessive");
        return AINutritionAssessmentVO.NutritionComparison.builder().nutrientName(nutrientName).perCapitaAmount(actualValue.setScale(2, RoundingMode.HALF_UP)).actualValue(actualValue.setScale(2, RoundingMode.HALF_UP)).targetAmount(targetValue.setScale(2, RoundingMode.HALF_UP)).targetValue(targetValue.setScale(2, RoundingMode.HALF_UP)).comparisonStatus(comparisonStatus).status(comparisonStatus).percentage(percentage).build();
    }

    private AINutritionAssessmentVO.NutritionBalanceScore buildNutritionBalanceScore(int score, String grade, String gradeDescription, BigDecimal avgProtein, BigDecimal avgCarbs, BigDecimal avgFat, BigDecimal avgCalories, NutritionTargetConfig.GroupNutritionTarget target) {
        ArrayList<AINutritionAssessmentVO.DimensionScore> dimensionScores = new ArrayList<AINutritionAssessmentVO.DimensionScore>();
        dimensionScores.add(this.createDimensionScore("\u86cb\u767d\u8d28", avgProtein, target.getProtein()));
        dimensionScores.add(this.createDimensionScore("\u78b3\u6c34\u5316\u5408\u7269", avgCarbs, target.getCarbohydrate()));
        dimensionScores.add(this.createDimensionScore("\u8102\u80aa", avgFat, target.getFat()));
        dimensionScores.add(this.createDimensionScore("\u70ed\u91cf", avgCalories, target.getCalories()));
        return AINutritionAssessmentVO.NutritionBalanceScore.builder().score(score).grade(grade).gradeDescription(gradeDescription).dimensionScores(dimensionScores).build();
    }

    private AINutritionAssessmentVO.DimensionScore createDimensionScore(String dimensionName, BigDecimal actualValue, BigDecimal targetValue) {
        int score;
        String description;
        String status;
        BigDecimal ratio;
        if (actualValue == null) {
            actualValue = BigDecimal.ZERO;
        }
        if (targetValue == null) {
            targetValue = BigDecimal.ZERO;
        }
        BigDecimal bigDecimal = ratio = targetValue.compareTo(BigDecimal.ZERO) > 0 ? actualValue.divide(targetValue, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (ratio.compareTo(BigDecimal.valueOf(0.8)) >= 0 && ratio.compareTo(BigDecimal.valueOf(1.2)) <= 0) {
            status = "adequate";
            description = "\u6444\u5165\u91cf\u5408\u7406\uff0c\u7b26\u5408\u8425\u517b\u76ee\u6807";
            score = 100;
        } else if (ratio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
            status = "insufficient";
            description = "\u6444\u5165\u91cf\u4e0d\u8db3\uff0c\u5efa\u8bae\u589e\u52a0";
            score = Math.max(0, ratio.multiply(BigDecimal.valueOf(100L)).intValue());
        } else {
            status = "excessive";
            description = "\u6444\u5165\u91cf\u8fc7\u591a\uff0c\u5efa\u8bae\u51cf\u5c11";
            score = Math.max(0, 100 - ratio.subtract(BigDecimal.valueOf(1.2)).multiply(BigDecimal.valueOf(50L)).intValue());
        }
        return AINutritionAssessmentVO.DimensionScore.builder().dimensionName(dimensionName).score(score).status(status).description(description).build();
    }

    private String generateAIOptimizationSuggestions(int score, BigDecimal avgProtein, BigDecimal avgCarbs, BigDecimal avgFat, BigDecimal avgCalories, NutritionTargetConfig.GroupNutritionTarget target) {
        BigDecimal calorieRatio;
        BigDecimal fatRatio;
        BigDecimal carbRatio;
        BigDecimal proteinRatio;
        StringBuilder suggestions = new StringBuilder();
        if (score >= 80) {
            suggestions.append("\u6574\u4f53\u8425\u517b\u642d\u914d\u5408\u7406\uff0c\u7ee7\u7eed\u4fdd\u6301\u5f53\u524d\u996e\u98df\u7ed3\u6784\u3002");
        } else if (score >= 60) {
            suggestions.append("\u8425\u517b\u642d\u914d\u57fa\u672c\u5408\u7406\uff0c\u53ef\u8fdb\u884c\u9002\u5f53\u4f18\u5316\u3002");
        } else {
            suggestions.append("\u8425\u517b\u642d\u914d\u9700\u8981\u6539\u8fdb\uff0c\u5efa\u8bae\u53c2\u8003\u4ee5\u4e0b\u5efa\u8bae\u8fdb\u884c\u8c03\u6574\u3002");
        }
        BigDecimal bigDecimal = proteinRatio = target.getProtein().compareTo(BigDecimal.ZERO) > 0 ? avgProtein.divide(target.getProtein(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (proteinRatio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
            suggestions.append("\u86cb\u767d\u8d28\u6444\u5165\u4e0d\u8db3\uff0c\u5efa\u8bae\u589e\u52a0\u8089\u7c7b\u3001\u86cb\u7c7b\u3001\u5976\u7c7b\u6216\u8c46\u5236\u54c1\u3002");
        } else if (proteinRatio.compareTo(BigDecimal.valueOf(1.2)) > 0) {
            suggestions.append("\u86cb\u767d\u8d28\u6444\u5165\u8fc7\u91cf\uff0c\u5efa\u8bae\u9002\u5f53\u51cf\u5c11\u9ad8\u86cb\u767d\u98df\u6750\u3002");
        }
        BigDecimal bigDecimal2 = carbRatio = target.getCarbohydrate().compareTo(BigDecimal.ZERO) > 0 ? avgCarbs.divide(target.getCarbohydrate(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (carbRatio.compareTo(BigDecimal.valueOf(0.7)) < 0) {
            suggestions.append("\u78b3\u6c34\u5316\u5408\u7269\u6444\u5165\u4e0d\u8db3\uff0c\u5efa\u8bae\u9002\u5f53\u589e\u52a0\u4e3b\u98df\u7c7b\u98df\u7269\u3002");
        } else if (carbRatio.compareTo(BigDecimal.valueOf(1.3)) > 0) {
            suggestions.append("\u78b3\u6c34\u5316\u5408\u7269\u6444\u5165\u504f\u9ad8\uff0c\u5efa\u8bae\u51cf\u5c11\u4e3b\u98df\uff0c\u589e\u52a0\u852c\u83dc\u7c7b\u83dc\u54c1\u3002");
        }
        BigDecimal bigDecimal3 = fatRatio = target.getFat().compareTo(BigDecimal.ZERO) > 0 ? avgFat.divide(target.getFat(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (fatRatio.compareTo(BigDecimal.valueOf(1.3)) > 0) {
            suggestions.append("\u8102\u80aa\u6444\u5165\u504f\u9ad8\uff0c\u5efa\u8bae\u51cf\u5c11\u6cb9\u70b8\u7c7b\u548c\u9ad8\u8102\u98df\u6750\u3002");
        }
        BigDecimal bigDecimal4 = calorieRatio = target.getCalories().compareTo(BigDecimal.ZERO) > 0 ? avgCalories.divide(target.getCalories(), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (calorieRatio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
            suggestions.append("\u70ed\u91cf\u6444\u5165\u4e0d\u8db3\uff0c\u53ef\u80fd\u5bfc\u81f4\u80fd\u91cf\u4f9b\u7ed9\u4e0d\u591f\uff0c\u5efa\u8bae\u9002\u5f53\u589e\u52a0\u83dc\u54c1\u4efd\u91cf\u6216\u6570\u91cf\u3002");
        } else if (calorieRatio.compareTo(BigDecimal.valueOf(1.2)) > 0) {
            suggestions.append("\u70ed\u91cf\u6444\u5165\u504f\u9ad8\uff0c\u5efa\u8bae\u63a7\u5236\u603b\u4efd\u91cf\uff0c\u9009\u62e9\u4f4e\u70ed\u91cf\u98df\u6750\u3002");
        }
        return suggestions.toString();
    }

    private AINutritionAssessmentVO.DietStructureAnalysis calculateDietStructure(BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, BigDecimal calories) {
        if (calories.compareTo(BigDecimal.ZERO) == 0) {
            return AINutritionAssessmentVO.DietStructureAnalysis.builder().proteinRatio(BigDecimal.ZERO).carbohydrateRatio(BigDecimal.ZERO).fatRatio(BigDecimal.ZERO).evaluation("\u6682\u65e0\u6570\u636e").build();
        }
        BigDecimal proteinCalories = protein.multiply(BigDecimal.valueOf(4L));
        BigDecimal proteinRatio = proteinCalories.divide(calories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L));
        BigDecimal carbCalories = carbohydrate.multiply(BigDecimal.valueOf(4L));
        BigDecimal carbRatio = carbCalories.divide(calories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L));
        BigDecimal fatCalories = fat.multiply(BigDecimal.valueOf(9L));
        BigDecimal fatRatio = fatCalories.divide(calories, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L));
        String evaluation = this.evaluateDietStructure(proteinRatio, carbRatio, fatRatio);
        return AINutritionAssessmentVO.DietStructureAnalysis.builder().proteinRatio(proteinRatio.setScale(1, RoundingMode.HALF_UP)).carbohydrateRatio(carbRatio.setScale(1, RoundingMode.HALF_UP)).fatRatio(fatRatio.setScale(1, RoundingMode.HALF_UP)).evaluation(evaluation).build();
    }

    private String evaluateDietStructure(BigDecimal proteinRatio, BigDecimal carbRatio, BigDecimal fatRatio) {
        boolean fatOk;
        boolean carbOk;
        StringBuilder sb = new StringBuilder();
        boolean proteinOk = proteinRatio.compareTo(BigDecimal.valueOf(10L)) >= 0 && proteinRatio.compareTo(BigDecimal.valueOf(20L)) <= 0;
        int passCount = (proteinOk ? 1 : 0) + ((carbOk = carbRatio.compareTo(BigDecimal.valueOf(50L)) >= 0 && carbRatio.compareTo(BigDecimal.valueOf(65L)) <= 0) ? 1 : 0) + ((fatOk = fatRatio.compareTo(BigDecimal.valueOf(20L)) >= 0 && fatRatio.compareTo(BigDecimal.valueOf(30L)) <= 0) ? 1 : 0);
        if (passCount == 3) {
            sb.append("\u996e\u98df\u7ed3\u6784\u5747\u8861\uff0c\u4e09\u5927\u8425\u517b\u7d20\u6bd4\u4f8b\u7b26\u5408\u81b3\u98df\u6307\u5357\u63a8\u8350\u3002");
        } else if (passCount == 2) {
            sb.append("\u996e\u98df\u7ed3\u6784\u57fa\u672c\u5408\u7406\uff0c");
            if (!proteinOk) {
                if (proteinRatio.compareTo(BigDecimal.valueOf(10L)) < 0) {
                    sb.append("\u86cb\u767d\u8d28\u6bd4\u4f8b\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u8089\u86cb\u5976\u6216\u8c46\u5236\u54c1\u3002");
                } else {
                    sb.append("\u86cb\u767d\u8d28\u6bd4\u4f8b\u504f\u9ad8\uff0c\u5efa\u8bae\u9002\u5f53\u51cf\u5c11\u9ad8\u86cb\u767d\u98df\u6750\u3002");
                }
            } else if (!carbOk) {
                if (carbRatio.compareTo(BigDecimal.valueOf(50L)) < 0) {
                    sb.append("\u78b3\u6c34\u5316\u5408\u7269\u6bd4\u4f8b\u504f\u4f4e\uff0c\u5efa\u8bae\u9002\u5f53\u589e\u52a0\u4e3b\u98df\u3002");
                } else {
                    sb.append("\u78b3\u6c34\u5316\u5408\u7269\u6bd4\u4f8b\u504f\u9ad8\uff0c\u5efa\u8bae\u51cf\u5c11\u4e3b\u98df\u589e\u52a0\u852c\u83dc\u3002");
                }
            } else if (fatRatio.compareTo(BigDecimal.valueOf(20L)) < 0) {
                sb.append("\u8102\u80aa\u6bd4\u4f8b\u504f\u4f4e\uff0c\u5efa\u8bae\u9002\u5f53\u589e\u52a0\u5065\u5eb7\u6cb9\u8102\u3002");
            } else {
                sb.append("\u8102\u80aa\u6bd4\u4f8b\u504f\u9ad8\uff0c\u5efa\u8bae\u51cf\u5c11\u6cb9\u70b8\u6216\u9ad8\u8102\u98df\u6750\u3002");
            }
        } else {
            sb.append("\u996e\u98df\u7ed3\u6784\u9700\u8981\u8c03\u6574\u3002");
            if (proteinRatio.compareTo(BigDecimal.valueOf(10L)) < 0) {
                sb.append("\u86cb\u767d\u8d28\u4e0d\u8db3\uff0c");
            }
            if (carbRatio.compareTo(BigDecimal.valueOf(50L)) < 0) {
                sb.append("\u78b3\u6c34\u5316\u5408\u7269\u504f\u4f4e\uff0c");
            }
            if (fatRatio.compareTo(BigDecimal.valueOf(30L)) > 0) {
                sb.append("\u8102\u80aa\u8fc7\u9ad8\uff0c");
            }
            sb.append("\u5efa\u8bae\u53c2\u8003AI\u63a8\u8350\u4f18\u5316\u83dc\u8c31\u642d\u914d\u3002");
        }
        return sb.toString();
    }

   private BigDecimal calculateQuickPassRate(Long planId) {
      List<RecipePlanItem> items = this.planItemMapper.selectList(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId));
      if (items.isEmpty()) {
         return null;
      } else {
         int totalServings = 0;
         BigDecimal totalProtein = BigDecimal.ZERO;
         BigDecimal totalCarb = BigDecimal.ZERO;
         BigDecimal totalFat = BigDecimal.ZERO;
         BigDecimal totalFiber = BigDecimal.ZERO;
         BigDecimal totalVitaminA = BigDecimal.ZERO;
         BigDecimal totalVitaminC = BigDecimal.ZERO;
         BigDecimal totalCalcium = BigDecimal.ZERO;
         BigDecimal totalIron = BigDecimal.ZERO;

         for (RecipePlanItem item : items) {
            Recipe recipe = (Recipe)this.recipeMapper.selectById(item.getRecipeId());
            if (recipe != null) {
               int servings = item.getPlannedServings() != null ? item.getPlannedServings() : 1;
               totalServings += servings;
               BigDecimal mul = BigDecimal.valueOf((long)servings);
               if (recipe.getProtein() != null) {
                  totalProtein = totalProtein.add(recipe.getProtein().multiply(mul));
               }

               recipeNutritionSupportService.recalculateRecipeNutrition(recipe);

               if (recipe.getCarbohydrate() != null) {
                  totalCarb = totalCarb.add(recipe.getCarbohydrate().multiply(mul));
               }

               if (recipe.getFat() != null) {
                  totalFat = totalFat.add(recipe.getFat().multiply(mul));
               }

               if (recipe.getFiber() != null) {
                  totalFiber = totalFiber.add(recipe.getFiber().multiply(mul));
               }

               if (recipe.getVitaminA() != null) {
                  totalVitaminA = totalVitaminA.add(recipe.getVitaminA().multiply(mul));
               }

               if (recipe.getVitaminC() != null) {
                  totalVitaminC = totalVitaminC.add(recipe.getVitaminC().multiply(mul));
               }

               if (recipe.getCalcium() != null) {
                  totalCalcium = totalCalcium.add(recipe.getCalcium().multiply(mul));
               }

               if (recipe.getIron() != null) {
                  totalIron = totalIron.add(recipe.getIron().multiply(mul));
               }
            }
         }

         return totalServings == 0
            ? null
            : this.calculatePassRate(totalProtein, totalCarb, totalFat, totalFiber, totalVitaminA, totalVitaminC, totalCalcium, totalIron, totalServings);
      }
   }

   private BigDecimal calculatePassRate(
      BigDecimal protein,
      BigDecimal carb,
      BigDecimal fat,
      BigDecimal fiber,
      BigDecimal vitaminA,
      BigDecimal vitaminC,
      BigDecimal calcium,
      BigDecimal iron,
      int servings
   ) {
      if (servings == 0) {
         return BigDecimal.ZERO;
      } else {
         BigDecimal standardProtein = BigDecimal.valueOf((long)(servings * 20));
         BigDecimal standardCarb = BigDecimal.valueOf((long)(servings * 60));
         BigDecimal standardFiber = BigDecimal.valueOf((long)(servings * 5));
         BigDecimal standardVitaminA = BigDecimal.valueOf((long)(servings * 300));
         BigDecimal standardVitaminC = BigDecimal.valueOf((long)(servings * 30));
         BigDecimal standardCalcium = BigDecimal.valueOf((long)(servings * 200));
         BigDecimal standardIron = BigDecimal.valueOf((long)(servings * 3));
         int passCount = 0;
         int totalItems = 7;
         BigDecimal threshold = BigDecimal.valueOf(0.8);
         if (protein.compareTo(standardProtein.multiply(threshold)) >= 0) {
            passCount++;
         }

         if (carb.compareTo(standardCarb.multiply(threshold)) >= 0) {
            passCount++;
         }

         if (fat.compareTo(BigDecimal.ZERO) > 0) {
            passCount++;
         }

         if (fiber.compareTo(standardFiber.multiply(threshold)) >= 0) {
            passCount++;
         }

         if (vitaminA.compareTo(standardVitaminA.multiply(threshold)) >= 0) {
            passCount++;
         }

         if (vitaminC.compareTo(standardVitaminC.multiply(threshold)) >= 0) {
            passCount++;
         }

         if (calcium.compareTo(standardCalcium.multiply(threshold)) >= 0 || iron.compareTo(standardIron.multiply(threshold)) >= 0) {
            passCount++;
         }

         return BigDecimal.valueOf(passCount * 100.0 / totalItems).setScale(1, RoundingMode.HALF_UP);
      }
   }

    private String generateEnhancedNutritionAssessment(int score, BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, BigDecimal fiber, BigDecimal vitaminA, BigDecimal vitaminC, BigDecimal calcium, BigDecimal iron, int servings, int recipeCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u3010AI\u8425\u517b\u8bc4\u4f30\u62a5\u544a\u3011\n\n");
        if (score >= 80) {
            sb.append("\u2705 \u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u4f18\u79c0\uff0c\u5404\u8425\u517b\u7d20\u914d\u6bd4\u5408\u7406\u3002\n\n");
        } else if (score >= 60) {
            sb.append("\u2713 \u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u826f\u597d\uff0c\u90e8\u5206\u8425\u517b\u7d20\u53ef\u4f18\u5316\u3002\n\n");
        } else {
            sb.append("\u26a0 \u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u6709\u5f85\u6539\u5584\uff0c\u5efa\u8bae\u53c2\u8003AI\u63a8\u8350\u8fdb\u884c\u8c03\u6574\u3002\n\n");
        }
        sb.append("\ud83d\udcca \u8425\u517b\u6982\u51b5\n");
        sb.append("\u251c\u2500 \u83dc\u8c31\u6570\u91cf\uff1a").append(recipeCount).append(" \u9053\n");
        sb.append("\u251c\u2500 \u603b\u4efd\u6570\uff1a").append(servings).append(" \u4efd\n");
        sb.append("\u251c\u2500 \u603b\u70ed\u91cf\uff1a").append(calories.setScale(0, RoundingMode.HALF_UP)).append(" \u5343\u5361\n");
        sb.append("\u251c\u2500 \u86cb\u767d\u8d28\uff1a").append(protein.setScale(1, RoundingMode.HALF_UP)).append(" g\n");
        sb.append("\u251c\u2500 \u78b3\u6c34\u5316\u5408\u7269\uff1a").append(carbohydrate.setScale(1, RoundingMode.HALF_UP)).append(" g\n");
        sb.append("\u2514\u2500 \u8102\u80aa\uff1a").append(fat.setScale(1, RoundingMode.HALF_UP)).append(" g\n\n");
        sb.append("\ud83d\udd2c \u5fae\u91cf\u8425\u517b\u7d20\n");
        sb.append("\u251c\u2500 \u81b3\u98df\u7ea4\u7ef4\uff1a").append(fiber.setScale(1, RoundingMode.HALF_UP)).append(" g\n");
        sb.append("\u251c\u2500 \u7ef4\u751f\u7d20A\uff1a").append(vitaminA.setScale(0, RoundingMode.HALF_UP)).append(" \u03bcg\n");
        sb.append("\u251c\u2500 \u7ef4\u751f\u7d20C\uff1a").append(vitaminC.setScale(0, RoundingMode.HALF_UP)).append(" mg\n");
        sb.append("\u251c\u2500 \u9499\uff1a").append(calcium.setScale(0, RoundingMode.HALF_UP)).append(" mg\n");
        sb.append("\u2514\u2500 \u94c1\uff1a").append(iron.setScale(1, RoundingMode.HALF_UP)).append(" mg\n");
        return sb.toString();
    }

    private List<String> generateEnhancedNutritionSuggestions(int score, BigDecimal calories, BigDecimal protein, BigDecimal fat, BigDecimal fiber, BigDecimal vitaminA, BigDecimal vitaminC, BigDecimal calcium, BigDecimal iron, int servings) {
        ArrayList<String> suggestions = new ArrayList<String>();
        if (servings == 0) {
            suggestions.add("\u6682\u65e0\u83dc\u8c31\u6570\u636e\uff0c\u8bf7\u5148\u6dfb\u52a0\u83dc\u8c31");
            return suggestions;
        }
        BigDecimal avgCalories = calories.divide(BigDecimal.valueOf(servings), 0, RoundingMode.HALF_UP);
        BigDecimal avgProtein = protein.divide(BigDecimal.valueOf(servings), 1, RoundingMode.HALF_UP);
        if (avgCalories.compareTo(BigDecimal.valueOf(700L)) > 0) {
            suggestions.add("\u5355\u4efd\u70ed\u91cf\u8f83\u9ad8\uff08" + String.valueOf(avgCalories) + "\u5343\u5361\uff09\uff0c\u5efa\u8bae\u51cf\u5c11\u9ad8\u70ed\u91cf\u98df\u6750\u6216\u8c03\u6574\u4efd\u91cf");
        } else if (avgCalories.compareTo(BigDecimal.valueOf(300L)) < 0) {
            suggestions.add("\u5355\u4efd\u70ed\u91cf\u504f\u4f4e\uff08" + String.valueOf(avgCalories) + "\u5343\u5361\uff09\uff0c\u5efa\u8bae\u589e\u52a0\u4e3b\u98df\u6216\u9ad8\u80fd\u91cf\u98df\u6750");
        }
        if (avgProtein.compareTo(BigDecimal.valueOf(25L)) > 0) {
            suggestions.add("\u86cb\u767d\u8d28\u542b\u91cf\u5145\u8db3\uff0c\u7ee7\u7eed\u4fdd\u6301\u4f18\u8d28\u86cb\u767d\u6444\u5165");
        } else if (avgProtein.compareTo(BigDecimal.valueOf(15L)) < 0) {
            suggestions.add("\u86cb\u767d\u8d28\u542b\u91cf\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u8089\u7c7b\u3001\u9c7c\u7c7b\u3001\u86cb\u7c7b\u6216\u8c46\u5236\u54c1");
        }
        BigDecimal avgFiber = fiber.divide(BigDecimal.valueOf(servings), 1, RoundingMode.HALF_UP);
        if (avgFiber.compareTo(BigDecimal.valueOf(5L)) < 0) {
            suggestions.add("\u81b3\u98df\u7ea4\u7ef4\u6444\u5165\u4e0d\u8db3\uff0c\u5efa\u8bae\u589e\u52a0\u852c\u83dc\u3001\u6c34\u679c\u548c\u5168\u8c37\u7269");
        }
        if (vitaminA.compareTo(BigDecimal.valueOf(300L).multiply(BigDecimal.valueOf(servings))) < 0) {
            suggestions.add("\u7ef4\u751f\u7d20A\u6444\u5165\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u80e1\u841d\u535c\u3001\u83e0\u83dc\u3001\u52a8\u7269\u809d\u810f\u7b49\u5bcc\u542b\u7ef4A\u7684\u98df\u6750");
        }
        if (vitaminC.compareTo(BigDecimal.valueOf(30L).multiply(BigDecimal.valueOf(servings))) < 0) {
            suggestions.add("\u7ef4\u751f\u7d20C\u6444\u5165\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u65b0\u9c9c\u852c\u679c\u5982\u897f\u7ea2\u67ff\u3001\u9752\u6912\u3001\u67d1\u6a58\u7b49");
        }
        if (calcium.compareTo(BigDecimal.valueOf(200L).multiply(BigDecimal.valueOf(servings))) < 0) {
            suggestions.add("\u9499\u6444\u5165\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u5976\u5236\u54c1\u3001\u8c46\u5236\u54c1\u6216\u6df1\u7eff\u8272\u852c\u83dc");
        }
        if (iron.compareTo(BigDecimal.valueOf(3L).multiply(BigDecimal.valueOf(servings))) < 0) {
            suggestions.add("\u94c1\u6444\u5165\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u7ea2\u8089\u3001\u52a8\u7269\u8840\u6216\u9ed1\u6728\u8033\u7b49\u5bcc\u94c1\u98df\u6750");
        }
        if (score < 70) {
            suggestions.add("\ud83d\udca1 \u5efa\u8bae\u53c2\u8003AI\u63a8\u8350\u83dc\u8c31\u4f18\u5316\u642d\u914d\uff0c\u63d0\u5347\u6574\u4f53\u8425\u517b\u6c34\u5e73");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("\u2728 \u8425\u517b\u642d\u914d\u5747\u8861\uff0c\u5404\u9879\u6307\u6807\u8fbe\u6807\uff0c\u7ee7\u7eed\u4fdd\u6301\uff01");
        }
        return suggestions;
    }

    @Override

   public List<RecipeVO> getAiRecommendRecipes(RecipePlanQueryDTO query) {
      List<RecipeVO> recommendations = new ArrayList<>();
      Map<Long, Integer> categoryCount = new HashMap<>();
      List<Long> excludeIds = new ArrayList<>();
      if (query.getPlanId() != null) {
         for (RecipePlanItem item : this.planItemMapper.selectList(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, query.getPlanId()))) {
            excludeIds.add(item.getRecipeId());
            Recipe recipe = (Recipe)this.recipeMapper.selectById(item.getRecipeId());
            if (recipe != null && recipe.getCategoryId() != null) {
               categoryCount.merge(recipe.getCategoryId(), 1, Integer::sum);
            }
         }
      }

      NutritionGap gap = this.analyzeNutritionGap(query.getPlanId());
      List<Long> preferenceExcludeIds = this.filterByDiningPreferences(query);
      excludeIds.addAll(preferenceExcludeIds);
      List<RecipeVO> nutritionBased = this.recommendByNutritionGap(gap, excludeIds, 2);
      recommendations.addAll(nutritionBased);
      List<RecipeVO> diversityBased = this.recommendByDiversity(
         categoryCount, excludeIds, recommendations.stream().<Long>map(RecipeVO::getId).collect(Collectors.toList()), 2
      );
      recommendations.addAll(diversityBased);
      List<Long> alreadyRecommended = recommendations.stream().<Long>map(RecipeVO::getId).collect(Collectors.toList());
      alreadyRecommended.addAll(excludeIds);
      List<RecipeVO> scoreBased = this.recommendByScore(alreadyRecommended, 1);
      recommendations.addAll(scoreBased);
      this.addRecommendReasons(recommendations, gap, categoryCount);
      this.addDiningPreferenceReasons(recommendations, query);
      return recommendations;
   }

   private List<Long> filterByDiningPreferences(RecipePlanQueryDTO query) {
      List<Long> excludeIds = new ArrayList<>();
      if (query.getAvoidIngredientIds() != null && !query.getAvoidIngredientIds().isEmpty()) {
         List<Long> avoidIds = Arrays.stream(query.getAvoidIngredientIds().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
         if (!avoidIds.isEmpty()) {
            LambdaQueryWrapper<RecipeIngredient> ingredientWrapper = new LambdaQueryWrapper<>();
            ingredientWrapper.in(RecipeIngredient::getMaterialId, avoidIds);

            for (RecipeIngredient ingredient : this.ingredientMapper.selectList(ingredientWrapper)) {
               excludeIds.add(ingredient.getRecipeId());
            }
         }
      }

      if (query.getDietTags() != null && !query.getDietTags().isEmpty()) {
         List<String> dietTags = Arrays.stream(query.getDietTags().split(",")).map(String::trim).collect(Collectors.toList());
         if (dietTags.contains("vegetarian")) {
            excludeIds.addAll(this.getNonVegetarianRecipeIds());
         }

         if (dietTags.contains("lowfat")) {
            excludeIds.addAll(this.getHighFatRecipeIds());
         }

         if (dietTags.contains("lowsugar")) {
            excludeIds.addAll(this.getHighCarbRecipeIds());
         }
      }

      return excludeIds.stream().distinct().collect(Collectors.toList());
   }

   private List<Long> getNonVegetarianRecipeIds() {
      LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Recipe::getStatus, "active").eq(Recipe::getCategoryId, this.getCategoryIdByCode("MAIN_DISH"));
      List<Recipe> recipes = this.recipeMapper.selectList(wrapper);
      return recipes.stream().<Long>map(Recipe::getId).collect(Collectors.toList());
   }

   private List<Long> getHighFatRecipeIds() {
      LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Recipe::getStatus, "active").gt(Recipe::getFat, BigDecimal.valueOf(20L));
      List<Recipe> recipes = this.recipeMapper.selectList(wrapper);
      return recipes.stream().<Long>map(Recipe::getId).collect(Collectors.toList());
   }

   private List<Long> getHighCarbRecipeIds() {
      LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Recipe::getStatus, "active").gt(Recipe::getCarbohydrate, BigDecimal.valueOf(50L));
      List<Recipe> recipes = this.recipeMapper.selectList(wrapper);
      return recipes.stream().<Long>map(Recipe::getId).collect(Collectors.toList());
   }

   private Long getCategoryIdByCode(String code) {
      RecipeCategory category = this.findVisibleCategoryByCode(code);
      return category != null ? category.getId() : null;
   }

    private void addDiningPreferenceReasons(List<RecipeVO> recommendations, RecipePlanQueryDTO query) {
        for (RecipeVO vo : recommendations) {
            ArrayList<String> preferenceReasons = new ArrayList<String>();
            if (query.getFlavorPreferences() != null) {
                preferenceReasons.add("\u7b26\u5408\u53e3\u5473\u504f\u597d");
            }
            if (query.getSpicyLevel() != null) {
                preferenceReasons.add("\u8fa3\u5ea6\u9002\u4e2d");
            }
            if (query.getDietTags() != null) {
                preferenceReasons.add("\u7b26\u5408\u996e\u98df\u504f\u597d");
            }
            if (query.getAvoidIngredientIds() != null) {
                preferenceReasons.add("\u4e0d\u542b\u7981\u5fcc\u98df\u6750");
            }
            if (!preferenceReasons.isEmpty() && vo.getRecommendReason() != null) {
                vo.setRecommendReason(vo.getRecommendReason() + "\uff1b" + String.join((CharSequence)"\uff0c", preferenceReasons));
                continue;
            }
            if (preferenceReasons.isEmpty()) continue;
            vo.setRecommendReason(String.join((CharSequence)"\uff0c", preferenceReasons));
        }
    }

   private NutritionGap analyzeNutritionGap(Long planId) {
      NutritionGap gap = new NutritionGap();
      if (planId == null) {
         gap.setProteinNeed(true);
         gap.setFiberNeed(true);
         gap.setVitaminNeed(true);
         return gap;
      } else {
         RecipePlanDetailVO detail = this.getDetail(planId);
         if (detail != null && detail.getRecipes() != null && !detail.getRecipes().isEmpty()) {
            int totalServings = 0;
            BigDecimal totalProtein = BigDecimal.ZERO;
            BigDecimal totalFiber = BigDecimal.ZERO;
            BigDecimal totalVitaminA = BigDecimal.ZERO;
            BigDecimal totalVitaminC = BigDecimal.ZERO;
            BigDecimal totalCalcium = BigDecimal.ZERO;

            for (RecipePlanItemVO item : detail.getRecipes()) {
               if (item.getPlannedServings() != null) {
                  Recipe recipe = (Recipe)this.recipeMapper.selectById(item.getRecipeId());
                  if (recipe != null) {
                     int servings = item.getPlannedServings();
                     totalServings += servings;
                     if (recipe.getProtein() != null) {
                        totalProtein = totalProtein.add(recipe.getProtein().multiply(BigDecimal.valueOf((long)servings)));
                     }

                     if (recipe.getFiber() != null) {
                        totalFiber = totalFiber.add(recipe.getFiber().multiply(BigDecimal.valueOf((long)servings)));
                     }

                     if (recipe.getVitaminA() != null) {
                        totalVitaminA = totalVitaminA.add(recipe.getVitaminA().multiply(BigDecimal.valueOf((long)servings)));
                     }

                     if (recipe.getVitaminC() != null) {
                        totalVitaminC = totalVitaminC.add(recipe.getVitaminC().multiply(BigDecimal.valueOf((long)servings)));
                     }

                     if (recipe.getCalcium() != null) {
                        totalCalcium = totalCalcium.add(recipe.getCalcium().multiply(BigDecimal.valueOf((long)servings)));
                     }
                  }
               }
            }

            if (totalServings > 0) {
               BigDecimal perServingProtein = totalProtein.divide(BigDecimal.valueOf((long)totalServings), 2, RoundingMode.HALF_UP);
               BigDecimal perServingFiber = totalFiber.divide(BigDecimal.valueOf((long)totalServings), 2, RoundingMode.HALF_UP);
               BigDecimal perServingVitaminA = totalVitaminA.divide(BigDecimal.valueOf((long)totalServings), 2, RoundingMode.HALF_UP);
               BigDecimal perServingVitaminC = totalVitaminC.divide(BigDecimal.valueOf((long)totalServings), 2, RoundingMode.HALF_UP);
               BigDecimal perServingCalcium = totalCalcium.divide(BigDecimal.valueOf((long)totalServings), 2, RoundingMode.HALF_UP);
               gap.setProteinNeed(perServingProtein.compareTo(BigDecimal.valueOf(15L)) < 0);
               gap.setFiberNeed(perServingFiber.compareTo(BigDecimal.valueOf(4L)) < 0);
               gap.setVitaminNeed(perServingVitaminA.compareTo(BigDecimal.valueOf(200L)) < 0 || perServingVitaminC.compareTo(BigDecimal.valueOf(20L)) < 0);
               gap.setCalciumNeed(perServingCalcium.compareTo(BigDecimal.valueOf(150L)) < 0);
            }

            return gap;
         } else {
            gap.setProteinNeed(true);
            gap.setFiberNeed(true);
            gap.setVitaminNeed(true);
            return gap;
         }
      }
   }

   private List<RecipeVO> recommendByNutritionGap(NutritionGap gap, List<Long> excludeIds, int limit) {
      List<RecipeVO> results = new ArrayList<>();
      LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Recipe::getStatus, "active");
      if (!excludeIds.isEmpty()) {
         wrapper.notIn(Recipe::getId, excludeIds);
      }

      if (gap.isProteinNeed()) {
         wrapper.orderByDesc(Recipe::getProtein);
      } else if (gap.isFiberNeed()) {
         wrapper.orderByDesc(Recipe::getFiber);
      } else if (gap.isVitaminNeed()) {
         wrapper.orderByDesc(Recipe::getVitaminC);
      } else if (gap.isCalciumNeed()) {
         wrapper.orderByDesc(Recipe::getCalcium);
      } else {
         wrapper.orderByDesc(Recipe::getNutritionScore);
      }

      wrapper.last("LIMIT " + limit);

      for (Recipe recipe : this.recipeMapper.selectList(wrapper)) {
         RecipeVO vo = this.convertRecipeToVO(recipe);
         results.add(vo);
         excludeIds.add(recipe.getId());
      }

      return results;
   }

   private List<RecipeVO> recommendByDiversity(Map<Long, Integer> categoryCount, List<Long> excludeIds, List<Long> alreadyRecommended, int limit) {
      List<RecipeVO> results = new ArrayList<>();
      List<RecipeCategory> allCategories = this.categoryMapper
         .selectList(
            this.buildVisibleCategoryWrapper().eq(RecipeCategory::getStatus, "active").orderByAsc(RecipeCategory::getSortOrder)
         );
      List<Long> sortedCategoryIds = allCategories.stream().<Long>map(RecipeCategory::getId).sorted((a, b) -> {
         int countA = categoryCount.getOrDefault(a, 0);
         int countB = categoryCount.getOrDefault(b, 0);
         return Integer.compare(countA, countB);
      }).collect(Collectors.toList());
      List<Long> allExcluded = new ArrayList<>(excludeIds);
      allExcluded.addAll(alreadyRecommended);

      for (Long categoryId : sortedCategoryIds) {
         if (results.size() >= limit) {
            break;
         }

         LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
         wrapper.eq(Recipe::getStatus, "active").eq(Recipe::getCategoryId, categoryId);
         if (!allExcluded.isEmpty()) {
            wrapper.notIn(Recipe::getId, allExcluded);
         }

         wrapper.orderByDesc(Recipe::getNutritionScore).last("LIMIT 1");
         Recipe recipe = (Recipe)this.recipeMapper.selectOne(wrapper);
         if (recipe != null) {
            RecipeVO vo = this.convertRecipeToVO(recipe);
            results.add(vo);
            allExcluded.add(recipe.getId());
         }
      }

      return results;
   }

   private List<RecipeVO> recommendByScore(List<Long> excludeIds, int limit) {
      LambdaQueryWrapper<Recipe> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(Recipe::getStatus, "active");
      if (!excludeIds.isEmpty()) {
         wrapper.notIn(Recipe::getId, excludeIds);
      }

      wrapper.orderByDesc(Recipe::getNutritionScore).last("LIMIT " + limit);
      List<Recipe> recipes = this.recipeMapper.selectList(wrapper);
      return recipes.stream().map(this::convertRecipeToVO).collect(Collectors.toList());
   }

    private void addRecommendReasons(List<RecipeVO> recommendations, NutritionGap gap, Map<Long, Integer> categoryCount) {
        for (RecipeVO vo : recommendations) {
            int count;
            Long categoryId;
            ArrayList<String> reasons = new ArrayList<String>();
            if (gap.isProteinNeed() && vo.getNutritionInfo() != null && vo.getNutritionInfo().getProtein() != null && vo.getNutritionInfo().getProtein().compareTo(BigDecimal.valueOf(15L)) >= 0) {
                reasons.add("\u9ad8\u86cb\u767d\uff0c\u8865\u5145\u86cb\u767d\u8d28\u7f3a\u53e3");
            }
            if (vo.getNutritionScore() != null && vo.getNutritionScore() >= 80) {
                reasons.add("\u8425\u517b\u8bc4\u5206\u4f18\u79c0");
            }
            if ((categoryId = vo.getCategoryId()) != null && (count = categoryCount.getOrDefault(categoryId, 0).intValue()) == 0) {
                reasons.add("\u4e30\u5bcc\u83dc\u8c31\u7c7b\u522b\uff0c\u589e\u52a0\u996e\u98df\u591a\u6837\u6027");
            }
            if (!reasons.isEmpty()) continue;
            reasons.add("\u8425\u517b\u5747\u8861\uff0c\u63a8\u8350\u5c1d\u8bd5");
        }
    }

   private RecipeVO convertRecipeToVO(Recipe recipe) {
      RecipeVO vo = new RecipeVO();
      vo.setId(recipe.getId());
      vo.setMenuCode(recipe.getRecipeCode());
      vo.setMenuName(recipe.getRecipeName());
      vo.setCategoryId(recipe.getCategoryId());
      vo.setImageUrl(recipe.getImageUrl());
      vo.setNutritionScore(recipe.getNutritionScore());
      vo.setStatus(recipe.getStatus());
      vo.setUpdatedAt(recipe.getUpdatedAt());
      if (recipe.getCategoryId() != null) {
         RecipeCategory category = this.getVisibleCategoryById(recipe.getCategoryId());
         if (category != null) {
            vo.setCategoryName(category.getCategoryName());
            vo.setMenuCategory(category.getCategoryCode());
         }
      }

      vo.setCookingTime(recipe.getTargetCookTime());
      vo.setCookingTempMin(recipe.getTargetTempMin());
      vo.setCookingTempMax(recipe.getTargetTempMax());
      NutritionInfoVO nutritionInfo = new NutritionInfoVO();
      nutritionInfo.setProtein(recipe.getProtein());
      nutritionInfo.setCarbohydrate(recipe.getCarbohydrate());
      nutritionInfo.setFat(recipe.getFat());
      nutritionInfo.setCalories(recipe.getCalories());
      vo.setNutritionInfo(nutritionInfo);
      return vo;
   }

   @Transactional(
      rollbackFor = {Exception.class}
   )
    public AdjustmentResultVO createAdjustment(Long planId, AdjustmentDTO dto) {
        String afterData;
        RecipePlan plan = (RecipePlan)this.getById(planId);
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if (!"approved".equals(plan.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u5df2\u5ba1\u6838\u7684\u8ba1\u5212\u53ef\u4ee5\u7533\u8bf7\u8c03\u6574");
        }
        AdjustmentContext adjustmentContext = this.resolveAdjustmentContext(planId);
        if (!adjustmentContext.isSupported()) {
            throw BizException.conflict(adjustmentContext.getUnsupportedReason());
        }
        if (adjustmentContext.hasCurrentDayPendingTasks()) {
            throw BizException.conflict("计划存在当天待执行任务，暂不支持调整，请待当天任务执行完成后再操作");
        }
        Long pendingCount = this.adjustmentMapper.selectCount(
            new LambdaQueryWrapper<RecipePlanAdjustment>()
                .eq(RecipePlanAdjustment::getPlanId, planId)
                .eq(RecipePlanAdjustment::getStatus, "pending")
        );
        if (pendingCount != null && pendingCount > 0L) {
            throw new RuntimeException("\u8be5\u8ba1\u5212\u5df2\u6709\u5f85\u5ba1\u6838\u7684\u8c03\u6574\u7533\u8bf7\uff0c\u8bf7\u7b49\u5f85\u5ba1\u6838\u5b8c\u6210\u540e\u518d\u63d0\u4ea4");
        }
        String beforeData = this.getPlanSnapshotJson(planId);
        List<RecipePlanAdjustmentDetailVO.AdjustItemVO> adjustItems = this.buildAdjustItems(beforeData, afterData = dto.getAfterData(), dto.getAdjustType());
        if (adjustItems.isEmpty()) {
            throw new RuntimeException("\u8bf7\u5148\u8c03\u6574\u8ba1\u5212\u5185\u5bb9\u540e\u518d\u63d0\u4ea4\u7533\u8bf7");
        }
        String resolvedAdjustType = this.resolveAdjustType(beforeData, afterData, dto.getAdjustType());
        LocalDateTime now = LocalDateTime.now();
        RecipePlanAdjustment adjustment = new RecipePlanAdjustment();
        adjustment.setAdjustCode(this.generateAdjustmentCode());
        adjustment.setPlanId(planId);
        adjustment.setAdjustReason(StrUtil.trim((CharSequence)dto.getAdjustReason()));
        adjustment.setAdjustType(resolvedAdjustType);
        adjustment.setBeforeData(beforeData);
        adjustment.setAfterData(afterData);
        adjustment.setStatus("pending");
        adjustment.setAppliedBy(UserContext.getUserId());
        adjustment.setAppliedAt(now);
        adjustment.setOrgId(plan.getOrgId());
        adjustment.setTenantId(plan.getTenantId());
        adjustment.setCreatedAt(now);
        this.adjustmentMapper.insert(adjustment);
        return AdjustmentResultVO.builder().id(adjustment.getId()).planId(planId).adjustCode(adjustment.getAdjustCode()).status("pending").build();
    }

    @Override
    @Transactional(rollbackFor={Exception.class})
    public AdjustmentAuditResultVO auditAdjustment(Long adjustmentId, String status, String remark) {
        RecipePlanAdjustment adjustment = (RecipePlanAdjustment)this.adjustmentMapper.selectById(adjustmentId);
        if (adjustment == null) {
            throw new RuntimeException("\u8c03\u6574\u7533\u8bf7\u4e0d\u5b58\u5728");
        }
        this.ensureAdjustmentVisible(adjustment);
        if (!"pending".equals(adjustment.getStatus())) {
            throw new RuntimeException("\u53ea\u6709\u5f85\u5ba1\u6838\u7684\u8c03\u6574\u7533\u8bf7\u53ef\u4ee5\u8fdb\u884c\u5ba1\u6838");
        }
        RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
        if (plan == null) {
            throw new RuntimeException("\u83dc\u8c31\u8ba1\u5212\u4e0d\u5b58\u5728");
        }
        if ("approved".equals(status) && !"approved".equals(plan.getStatus())) {
            throw BizException.conflict("\u8ba1\u5212\u5f53\u524d\u4e0d\u662f\u5df2\u5ba1\u6838\u72b6\u6001\uff0c\u4e0d\u80fd\u901a\u8fc7\u5386\u53f2\u8c03\u6574\u7533\u8bf7");
        }
        AdjustmentContext adjustmentContext = this.resolveAdjustmentContext(adjustment.getPlanId());
        if ("approved".equals(status) && adjustmentContext.hasCurrentDayPendingTasks()) {
            throw BizException.conflict("计划存在当天待执行任务，暂不支持调整审批通过，请待当天任务执行完成后再操作");
        }
        if ("approved".equals(status) && !adjustmentContext.isSupported()) {
            throw BizException.conflict(adjustmentContext.getUnsupportedReason());
        }
        adjustment.setStatus(status);
        adjustment.setAuditRemark(remark);
        adjustment.setAuditedBy(UserContext.getUserId());
        adjustment.setAuditedAt(LocalDateTime.now());
        adjustment.setUpdatedAt(LocalDateTime.now());
        boolean cookTaskUpdated = false;
        if ("approved".equals(status)) {
            cookTaskUpdated = this.applyAdjustmentByMode(adjustment);
        }
        this.adjustmentMapper.updateById(adjustment);
        return AdjustmentAuditResultVO.builder().id(adjustmentId).planId(adjustment.getPlanId()).status(status).cookTaskUpdated(cookTaskUpdated).build();
    }

    private boolean applyAdjustmentByMode(RecipePlanAdjustment adjustment) {
        AdjustmentContext context = this.resolveAdjustmentContext(adjustment.getPlanId());
        if (!context.isSupported()) {
            throw BizException.conflict(context.getUnsupportedReason());
        }
        if (context.hasFuturePendingTasks() && context.getFirstFuturePendingTaskDate() == null) {
            throw BizException.conflict("计划存在当天待执行任务，暂不支持在保留历史执行事实的同时调整当天任务，请次日再调整");
        }
        return switch (context.getMode()) {
            case ADJUSTMENT_MODE_HISTORY_MIXED -> this.applyHistoryMixedAdjustment(adjustment, context);
            case ADJUSTMENT_MODE_FUTURE_ONLY -> this.applyFutureOnlyAdjustment(adjustment);
            default -> this.applyFutureOnlyAdjustment(adjustment);
        };
    }

    private boolean applyFutureOnlyAdjustment(RecipePlanAdjustment adjustment) {
        this.applyAdjustment(adjustment);
        return this.updateCookTasks(adjustment.getPlanId());
    }

    private boolean applyHistoryMixedAdjustment(RecipePlanAdjustment adjustment, AdjustmentContext context) {
        try {
            JsonNode rootNode = this.objectMapper.readTree(adjustment.getAfterData());
            if (rootNode == null || !rootNode.isObject()) {
                throw new RuntimeException("含历史执行事实的计划仅支持快照式调整");
            }
            if (context.hasInProgressTasks()) {
                throw BizException.conflict("计划存在执行中的烹饪任务，请先完成当前任务后再调整未来计划");
            }
            AdjustmentPlanSnapshot snapshot = this.parseAdjustmentSnapshot(rootNode.toString());
            RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
            if (plan == null) {
                throw new RuntimeException("菜谱计划不存在");
            }
            LocalDate effectiveDate = this.resolveHistoryMixedEffectiveDate(context);
            this.applyPlanHeaderFromSnapshot(plan, snapshot);
            List<PlanMealScheduleData> mealSchedules = this.convertSnapshotMealSchedules(snapshot.getMealSchedules());
            List<AdjustmentPlanRecipeSnapshot> targetRecipes = !mealSchedules.isEmpty() ? this.flattenAdjustmentRecipes(snapshot.getMealSchedules()) : snapshot.getRecipes();
            this.ensureNoDuplicatePlanItemRecipeAssignments(this.planItemMapper.selectList(
                    new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, adjustment.getPlanId())), "当前计划");
            this.ensureNoDuplicateSnapshotRecipeAssignments(targetRecipes, "调整后计划");
            Map<Long, RecipePlanItem> currentItems = this.loadPlanItemsByRecipeId(adjustment.getPlanId());
            Map<Long, AdjustmentPlanRecipeSnapshot> targetRecipeMap = this.indexAdjustmentRecipesByRecipeId(targetRecipes);
            Set<Long> removedRecipeIds = new LinkedHashSet<>(currentItems.keySet());
            removedRecipeIds.removeAll(targetRecipeMap.keySet());
            Set<Long> addedRecipeIds = new LinkedHashSet<>(targetRecipeMap.keySet());
            addedRecipeIds.removeAll(currentItems.keySet());
            Set<Long> retainedRecipeIds = new LinkedHashSet<>(targetRecipeMap.keySet());
            retainedRecipeIds.retainAll(currentItems.keySet());
            if (!removedRecipeIds.isEmpty()) {
                this.cookTaskService.cancelPendingFutureTasksByPlanIdAndRecipeIds(adjustment.getPlanId(), removedRecipeIds, effectiveDate);
            }
            this.upsertHistoryMixedPlanItems(adjustment.getPlanId(), plan, targetRecipeMap, retainedRecipeIds, addedRecipeIds);
            Map<Long, RecipePlanItem> targetItems = this.loadPlanItemsByRecipeId(adjustment.getPlanId());
            if (!retainedRecipeIds.isEmpty()) {
                Map<Long, RecipePlanItem> retainedItems = targetItems.entrySet().stream()
                    .filter(entry -> retainedRecipeIds.contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right, LinkedHashMap::new));
                this.cookTaskService.syncPendingFutureTasksForPlanItems(adjustment.getPlanId(), retainedItems, effectiveDate);
            }
            if (!addedRecipeIds.isEmpty()) {
                this.cookTaskService.generateFutureTasksForPlan(adjustment.getPlanId(), effectiveDate, addedRecipeIds);
            }
            this.calculateTotals(plan);
            this.refreshPlanNutritionAssessment(adjustment.getPlanId(), plan);
            plan.setUpdatedAt(LocalDateTime.now());
            this.updateById(plan);
            return true;
        } catch (JsonProcessingException e) {
            log.error("解析调整数据失败", e);
            throw new RuntimeException("调整数据格式错误");
        }
    }

    private boolean updateCookTasks(Long planId) {
        this.cookTaskService.adjustTasksForPlan(planId);
        log.info("更新计划[{}]的烹饪任务", (Object)planId);
        return true;
    }

    @Override
    @DataScope
   public Page<RecipePlanAdjustmentVO> listAdjustments(RecipePlanQueryDTO query) {
      Page<RecipePlanAdjustment> page = new Page(query.getPageNum().intValue(), query.getPageSize().intValue());
      LambdaQueryWrapper<RecipePlanAdjustment> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(query.getPlanId() != null, RecipePlanAdjustment::getPlanId, query.getPlanId())
         .eq(query.getAdjustmentId() != null, RecipePlanAdjustment::getId, query.getAdjustmentId())
         .eq(StrUtil.isNotBlank(query.getStatus()), RecipePlanAdjustment::getStatus, query.getStatus())
         .eq(StrUtil.isNotBlank(query.getAdjustType()), RecipePlanAdjustment::getAdjustType, query.getAdjustType())
         .ge(
            query.getPlanDateStart() != null,
            RecipePlanAdjustment::getCreatedAt,
            query.getPlanDateStart() != null ? query.getPlanDateStart().atStartOfDay() : null
         )
         .le(
            query.getPlanDateEnd() != null,
            RecipePlanAdjustment::getCreatedAt,
            query.getPlanDateEnd() != null ? query.getPlanDateEnd().plusDays(1L).atStartOfDay() : null
         )
         .eq(query.getOrgId() != null, RecipePlanAdjustment::getOrgId, query.getOrgId())
         .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), RecipePlanAdjustment::getOrgId, query.getOrgIds())
         .orderByDesc(RecipePlanAdjustment::getCreatedAt);
      if (StrUtil.isNotBlank(query.getPlanCode())) {
         List<Long> matchedPlanIds = this.list(new LambdaQueryWrapper<RecipePlan>().like(RecipePlan::getPlanCode, query.getPlanCode()))
            .stream()
            .<Long>map(RecipePlan::getId)
            .collect(Collectors.toList());
         if (!matchedPlanIds.isEmpty()) {
            wrapper.in(RecipePlanAdjustment::getPlanId, matchedPlanIds);
         } else {
            wrapper.isNull(RecipePlanAdjustment::getId);
         }
      }

      if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
         wrapper.isNull(RecipePlanAdjustment::getId);
      }

      Page<RecipePlanAdjustment> resultPage = (Page<RecipePlanAdjustment>)this.adjustmentMapper.selectPage(page, wrapper);
      Page<RecipePlanAdjustmentVO> voPage = new Page(query.getPageNum().intValue(), query.getPageSize().intValue());
      voPage.setTotal(resultPage.getTotal());
      voPage.setRecords(resultPage.getRecords().stream().map(this::convertAdjustmentToVO).collect(Collectors.toList()));
      return voPage;
   }
    public RecipePlanAdjustmentDetailVO getAdjustmentDetail(Long id) {
        RecipePlanAdjustment adjustment = (RecipePlanAdjustment)this.adjustmentMapper.selectById(id);
        if (adjustment == null) {
            throw new RuntimeException("\u8c03\u6574\u7533\u8bf7\u4e0d\u5b58\u5728");
        }
        this.ensureAdjustmentVisible(adjustment);
        RecipePlanAdjustmentDetailVO vo = new RecipePlanAdjustmentDetailVO();
        BeanUtils.copyProperties((Object)adjustment, (Object)vo);
        RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
        if (plan != null) {
            vo.setPlanCode(plan.getPlanCode());
            vo.setPlanDate(plan.getPlanDate());
        }
        vo.setAdjustTypeName(this.getAdjustTypeName(adjustment.getAdjustType()));
        vo.setStatusName(this.getAdjustmentStatusName(adjustment.getStatus()));
        if (adjustment.getAppliedBy() != null) {
            vo.setAppliedByName(this.resolveEmployeeName(adjustment.getAppliedBy()));
        }
        vo.setAuditedByName(adjustment.getAuditedBy() != null ? this.resolveEmployeeName(adjustment.getAuditedBy()) : null);
        vo.setAdjustItems(this.parseAdjustItems(adjustment));
        return vo;
    }

    private void ensureAdjustmentVisible(RecipePlanAdjustment adjustment) {
        DataScopeService.DataScopeResult scope;
        if (adjustment == null || this.dataScopeService.isAdminUser()) {
            return;
        }
        Long orgId = adjustment.getOrgId();
        if (orgId == null && adjustment.getPlanId() != null) {
            RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
            Long l = orgId = plan != null ? plan.getOrgId() : null;
        }
        if (!(scope = this.dataScopeService.resolveCurrentUserOrgScope()).isAllowed(orgId)) {
            throw BizException.forbidden((String)"\u65e0\u6743\u8bbf\u95ee\u8be5\u8c03\u6574\u7533\u8bf7");
        }
    }

    private RecipePlanAdjustmentVO convertAdjustmentToVO(RecipePlanAdjustment adjustment) {
        RecipePlanAdjustmentVO vo = new RecipePlanAdjustmentVO();
        BeanUtils.copyProperties((Object)adjustment, (Object)vo);
        RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
        if (plan != null) {
            vo.setPlanCode(plan.getPlanCode());
            vo.setPlanDate(plan.getPlanDate());
        }
        vo.setAdjustTypeName(this.getAdjustTypeName(adjustment.getAdjustType()));
        vo.setStatusName(this.getAdjustmentStatusName(adjustment.getStatus()));
        if (adjustment.getAppliedBy() != null) {
            vo.setAppliedByName(this.resolveEmployeeName(adjustment.getAppliedBy()));
        }
        vo.setAuditedByName(adjustment.getAuditedBy() != null ? this.resolveEmployeeName(adjustment.getAuditedBy()) : null);
        vo.setAuditRemark(adjustment.getAuditRemark());
        return vo;
    }

    private String getAdjustTypeName(String adjustType) {
        if (adjustType == null) {
            return "";
        }
        return switch (adjustType) {
            case "add" -> "\u65b0\u589e\u83dc\u8c31";
            case "remove" -> "\u79fb\u9664\u83dc\u8c31";
            case "modify" -> "\u8c03\u6574\u8ba1\u5212";
            default -> adjustType;
        };
    }

    private String getAdjustmentStatusName(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "pending" -> "\u5f85\u8c03\u6574\u5ba1\u6838";
            case "approved" -> "\u8c03\u6574\u5df2\u5ba1\u6838";
            case "rejected" -> "\u8c03\u6574\u5df2\u9a73\u56de";
            default -> status;
        };
    }

    private void applyAdjustment(RecipePlanAdjustment adjustment) {
        try {
            JsonNode rootNode = this.objectMapper.readTree(adjustment.getAfterData());
            if (rootNode != null && rootNode.isObject()) {
                this.applySnapshotAdjustment(adjustment, rootNode);
                return;
            }
            this.applyLegacyAdjustment(adjustment);
        }
        catch (JsonProcessingException e) {
            log.error("解析调整数据失败", (Throwable)e);
            throw new RuntimeException("调整数据格式错误");
        }
    }

    private void applySnapshotAdjustment(RecipePlanAdjustment adjustment, JsonNode rootNode) throws JsonProcessingException {
        AdjustmentPlanSnapshot snapshot = this.parseAdjustmentSnapshot(rootNode.toString());
        RecipePlan plan = (RecipePlan)this.getById(adjustment.getPlanId());
        if (plan == null) {
            throw new RuntimeException("菜谱计划不存在");
        }
        this.applyPlanHeaderFromSnapshot(plan, snapshot);
        List<PlanMealScheduleData> mealSchedules = this.convertSnapshotMealSchedules(snapshot.getMealSchedules());
        if (!mealSchedules.isEmpty()) {
            this.replacePlanItemsBySchedules(adjustment.getPlanId(), mealSchedules);
        } else {
            this.replacePlanItemsBySnapshot(adjustment.getPlanId(), snapshot.getRecipes());
        }
        this.calculateTotals(plan);
        this.refreshPlanNutritionAssessment(adjustment.getPlanId(), plan);
        plan.setUpdatedAt(LocalDateTime.now());
        this.updateById(plan);
    }

    private void applyPlanHeaderFromSnapshot(RecipePlan plan, AdjustmentPlanSnapshot snapshot) {
        plan.setPlanDate(this.parseLocalDate(snapshot.getPlanDate(), plan.getPlanDate()));
        plan.setStartDate(this.parseLocalDate(snapshot.getStartDate(), plan.getStartDate()));
        plan.setEndDate(this.parseLocalDate(snapshot.getEndDate(), plan.getEndDate()));
        if (StrUtil.isNotBlank((CharSequence)snapshot.getTargetGroup())) {
            plan.setTargetGroup(snapshot.getTargetGroup());
        }
        plan.setHealthStatus(this.joinHealthStatuses(snapshot.getHealthStatus()));
        plan.setDietRestrictions(StrUtil.emptyToNull((CharSequence)StrUtil.trim((CharSequence)snapshot.getDietRestrictions())));
        plan.setRemark(StrUtil.emptyToNull((CharSequence)StrUtil.trim((CharSequence)snapshot.getRemark())));
        if (snapshot.getUseAiRecommend() != null) {
            plan.setUseAiRecommend(snapshot.getUseAiRecommend());
        }
        List<PlanMealScheduleData> mealSchedules = this.convertSnapshotMealSchedules(snapshot.getMealSchedules());
        if (!mealSchedules.isEmpty()) {
            if (mealSchedules.size() == 1) {
                plan.setMealType(mealSchedules.get(0).getMealType());
                plan.setExpectedCount(mealSchedules.get(0).getExpectedCount());
            } else {
                plan.setMealType("multi");
                plan.setExpectedCount(null);
            }
        }
    }

    private Map<Long, RecipePlanItem> loadPlanItemsByRecipeId(Long planId) {
        return this.planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
                        .orderByAsc(RecipePlanItem::getMealSortOrder)
                        .orderByAsc(RecipePlanItem::getSortOrder)
                        .orderByAsc(RecipePlanItem::getId)
        ).stream().collect(Collectors.toMap(RecipePlanItem::getRecipeId, item -> item, (left, right) -> right, LinkedHashMap::new));
    }

    private Map<Long, AdjustmentPlanRecipeSnapshot> indexAdjustmentRecipesByRecipeId(List<AdjustmentPlanRecipeSnapshot> recipes) {
        if (recipes == null) {
            return new LinkedHashMap<>();
        }
        return recipes.stream()
                .filter(Objects::nonNull)
                .filter(recipe -> recipe.getRecipeId() != null)
                .collect(Collectors.toMap(AdjustmentPlanRecipeSnapshot::getRecipeId, recipe -> recipe, (left, right) -> right, LinkedHashMap::new));
    }

    private LocalDate resolveHistoryMixedEffectiveDate(AdjustmentContext context) {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        if (context != null && context.getFirstFuturePendingTaskDate() != null) {
            return context.getFirstFuturePendingTaskDate();
        }
        return tomorrow;
    }

    private void ensureNoDuplicatePlanItemRecipeAssignments(List<RecipePlanItem> items, String scene) {
        Set<Long> seenRecipeIds = new HashSet<>();
        for (RecipePlanItem item : items) {
            if (item == null || item.getRecipeId() == null) {
                continue;
            }
            if (!seenRecipeIds.add(item.getRecipeId())) {
                throw BizException.conflict(scene + "中存在同一菜谱被多个餐次重复引用，当前调整模式暂不支持该场景");
            }
        }
    }

    private void ensureNoDuplicateSnapshotRecipeAssignments(List<AdjustmentPlanRecipeSnapshot> recipes, String scene) {
        Set<Long> seenRecipeIds = new HashSet<>();
        if (recipes == null) {
            return;
        }
        for (AdjustmentPlanRecipeSnapshot recipe : recipes) {
            if (recipe == null || recipe.getRecipeId() == null) {
                continue;
            }
            if (!seenRecipeIds.add(recipe.getRecipeId())) {
                throw BizException.conflict(scene + "中存在同一菜谱被多个餐次重复引用，当前调整模式暂不支持该场景");
            }
        }
    }

    private void upsertHistoryMixedPlanItems(Long planId, RecipePlan plan, Map<Long, AdjustmentPlanRecipeSnapshot> targetRecipeMap, Set<Long> retainedRecipeIds, Set<Long> addedRecipeIds) {
        Map<Long, RecipePlanItem> currentItems = this.loadPlanItemsByRecipeId(planId);
        for (Long recipeId : retainedRecipeIds) {
            RecipePlanItem currentItem = currentItems.get(recipeId);
            AdjustmentPlanRecipeSnapshot targetRecipe = targetRecipeMap.get(recipeId);
            if (currentItem == null || targetRecipe == null) {
                continue;
            }
            this.applySnapshotToPlanItem(currentItem, targetRecipe, plan);
            this.planItemMapper.updateById(currentItem);
        }
        for (Long recipeId : addedRecipeIds) {
            AdjustmentPlanRecipeSnapshot targetRecipe = targetRecipeMap.get(recipeId);
            if (targetRecipe == null) {
                continue;
            }
            RecipePlanItem item = new RecipePlanItem();
            item.setPlanId(planId);
            item.setRecipeId(recipeId);
            item.setStatus(RecipePlanItem.STATUS_PENDING);
            this.applySnapshotToPlanItem(item, targetRecipe, plan);
            this.planItemMapper.insert(item);
        }
    }

    private void applySnapshotToPlanItem(RecipePlanItem item, AdjustmentPlanRecipeSnapshot snapshot, RecipePlan plan) {
        item.setRecipeId(snapshot.getRecipeId());
        item.setPlannedServings(snapshot.getPlannedServings());
        item.setRemark(StrUtil.emptyToNull((CharSequence)StrUtil.trim((CharSequence)snapshot.getRemark())));
        item.setMealKey(snapshot.getMealKey());
        item.setMealType(StrUtil.blankToDefault(snapshot.getMealType(), plan.getMealType()));
        item.setMealName(this.resolveMealScheduleName(snapshot.getMealType(), snapshot.getMealName()));
        item.setMealExpectedCount(snapshot.getMealExpectedCount() != null ? snapshot.getMealExpectedCount() : plan.getExpectedCount());
        item.setMealSortOrder(snapshot.getMealSortOrder());
        item.setSortOrder(snapshot.getSortOrder());
        Recipe recipe = (Recipe)this.recipeMapper.selectById(snapshot.getRecipeId());
        if (recipe != null) {
            item.setRecipeName(recipe.getRecipeName());
            item.setRecipeCode(recipe.getRecipeCode());
            item.setUnitCost(recipe.getUnitCost());
            item.setCategoryName(this.resolveRecipeCategoryName(recipe));
            if (recipe.getUnitCost() != null && snapshot.getPlannedServings() != null) {
                item.setTotalCost(recipe.getUnitCost().multiply(BigDecimal.valueOf(snapshot.getPlannedServings().longValue())));
            } else {
                item.setTotalCost(null);
            }
        }
    }

    private void refreshPlanNutritionAssessment(Long planId, RecipePlan plan) {
        try {
            Object nutritionResult = this.getAiNutritionAssessment(planId);
            if (nutritionResult instanceof AINutritionAssessmentVO nutritionVO) {
                plan.setNutritionPassRate(nutritionVO.getPassRate());
                plan.setAiNutritionAssessment(this.objectMapper.writeValueAsString(nutritionResult));
            }
        }
        catch (Exception e) {
            log.warn("调整审核后重新计算营养评估失败, planId={}: {}", (Object)planId, (Object)e.getMessage());
        }
    }

    private String getAdjustmentModeName(String mode) {
        return switch (mode) {
            case ADJUSTMENT_MODE_HISTORY_MIXED -> "历史保留，未来调整";
            case ADJUSTMENT_MODE_FUTURE_ONLY -> "未来整体调整";
            default -> "未来整体调整";
        };
    }

    private String buildAdjustmentHint(AdjustmentContext context) {
        if (!context.isSupported()) {
            return context.getUnsupportedReason();
        }
        if (context.hasCurrentDayPendingTasks()) {
            return "该计划存在当天待执行任务，暂不支持调整，请待当天任务执行完成后再操作。";
        }
        if (ADJUSTMENT_MODE_HISTORY_MIXED.equals(context.getMode())) {
            return "该计划已存在历史或执行事实，历史记录将保留，仅调整未来未执行任务。";
        }
        return "该计划尚未形成历史执行事实，审批通过后将按新内容重建未来任务。";
    }

    private boolean canAdjustPlan(RecipePlan plan, AdjustmentContext context) {
        return "approved".equals(plan.getStatus()) && context.isSupported() && !context.hasCurrentDayPendingTasks();
    }

    private AdjustmentContext resolveAdjustmentContext(Long planId) {
        List<CookTask> tasks = this.cookTaskService.getTasksByPlanId(planId);
        boolean hasHistoricalTasks = tasks.stream()
                .map(CookTask::getTaskDate)
                .filter(Objects::nonNull)
                .anyMatch(taskDate -> taskDate.isBefore(LocalDate.now()));
        boolean hasInProgressTasks = tasks.stream().anyMatch(task -> "in_progress".equals(task.getStatus()));
        boolean hasCompletedTasks = tasks.stream().anyMatch(task -> "completed".equals(task.getStatus()));
        boolean hasCurrentDayPendingTasks = tasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .map(CookTask::getTaskDate)
                .filter(Objects::nonNull)
                .anyMatch(LocalDate.now()::equals);
        boolean hasCurrentOrFuturePendingTasks = tasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .map(CookTask::getTaskDate)
                .filter(Objects::nonNull)
                .anyMatch(taskDate -> !taskDate.isBefore(LocalDate.now()));
        LocalDate firstFuturePendingTaskDate = tasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .map(CookTask::getTaskDate)
                .filter(Objects::nonNull)
                .filter(taskDate -> taskDate.isAfter(LocalDate.now()))
                .min(LocalDate::compareTo)
                .orElse(null);
        boolean hasDuplicateRecipeAssignments = this.hasDuplicateRecipeAssignments(planId);
        String mode = (hasHistoricalTasks || hasInProgressTasks || hasCompletedTasks || hasCurrentOrFuturePendingTasks) ? ADJUSTMENT_MODE_HISTORY_MIXED : ADJUSTMENT_MODE_FUTURE_ONLY;
        String unsupportedReason = hasDuplicateRecipeAssignments && ADJUSTMENT_MODE_HISTORY_MIXED.equals(mode)
                ? "该计划存在同一菜谱被多个餐次重复引用，暂不支持统一调整，请先拆分菜谱后再操作。"
                : null;
        return new AdjustmentContext(
                mode,
                hasHistoricalTasks,
                hasInProgressTasks,
                hasCompletedTasks,
                hasCurrentOrFuturePendingTasks,
                hasCurrentDayPendingTasks,
                firstFuturePendingTaskDate,
                unsupportedReason == null,
                unsupportedReason
        );
    }

    private boolean hasDuplicateRecipeAssignments(Long planId) {
        List<RecipePlanItem> items = this.planItemMapper.selectList(
                new LambdaQueryWrapper<RecipePlanItem>()
                        .eq(RecipePlanItem::getPlanId, planId)
                        .orderByAsc(RecipePlanItem::getMealSortOrder)
                        .orderByAsc(RecipePlanItem::getSortOrder)
                        .orderByAsc(RecipePlanItem::getId)
        );
        Set<Long> seenRecipeIds = new HashSet<>();
        for (RecipePlanItem item : items) {
            if (item == null || item.getRecipeId() == null) {
                continue;
            }
            if (!seenRecipeIds.add(item.getRecipeId())) {
                return true;
            }
        }
        return false;
    }

   private void replacePlanItemsBySnapshot(Long planId, List<AdjustmentPlanRecipeSnapshot> recipes) {
      List<PlanMealScheduleData> schedules = new ArrayList<>();
      PlanMealScheduleData schedule = new PlanMealScheduleData();
      schedule.setMealKey("legacy-1");
      schedule.setMealType(null);
      schedule.setMealName(null);
      schedule.setExpectedCount(null);
      schedule.setSortOrder(1);
      List<RecipePlanItemDTO> items = new ArrayList<>();
      if (recipes != null) {
         for (AdjustmentPlanRecipeSnapshot recipeSnapshot : recipes) {
            if (recipeSnapshot == null || recipeSnapshot.getRecipeId() == null) {
               continue;
            }
            RecipePlanItemDTO itemDTO = new RecipePlanItemDTO();
            itemDTO.setRecipeId(recipeSnapshot.getRecipeId());
            itemDTO.setPlannedServings(recipeSnapshot.getPlannedServings());
            itemDTO.setSortOrder(recipeSnapshot.getSortOrder());
            itemDTO.setRemark(recipeSnapshot.getRemark());
            items.add(itemDTO);
         }
      }
      schedule.setRecipes(items);
      schedules.add(schedule);
      this.replacePlanItemsBySchedules(planId, schedules);
   }

   private void applyLegacyAdjustment(RecipePlanAdjustment adjustment) throws JsonProcessingException {
      Long planId = adjustment.getPlanId();
      String adjustType = adjustment.getAdjustType();
      String afterData = adjustment.getAfterData();
      List<Map<String, Object>> items = (List<Map<String, Object>>)this.objectMapper
         .readValue(afterData, this.objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
      if (!"add".equals(adjustType) && !"modify".equals(adjustType)) {
         if ("remove".equals(adjustType)) {
            Set<Long> afterRecipeIds = items.stream().map(m -> Long.valueOf(m.get("recipeId").toString())).collect(Collectors.toSet());
            String beforeData = adjustment.getBeforeData();

            List<?> beforeItems = (List<?>)this.objectMapper.readValue(
               beforeData,
               this.objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );

            for (Object beforeItemObj : beforeItems) {
               Map<String, Object> beforeItem = (Map<String, Object>)beforeItemObj;
               Long recipeId = Long.valueOf(beforeItem.get("recipeId").toString());
               if (!afterRecipeIds.contains(recipeId)) {
                  this.planItemMapper
                     .delete(
                        new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId).eq(RecipePlanItem::getRecipeId, recipeId)
                     );
               }
            }
         }
      } else {
         for (Map<String, Object> item : items) {
            Long recipeId = Long.valueOf(item.get("recipeId").toString());
            Integer servings = Integer.valueOf(item.get("plannedServings").toString());
            RecipePlanItem existing = (RecipePlanItem)this.planItemMapper
               .selectOne(
                  new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId).eq(RecipePlanItem::getRecipeId, recipeId)
               );
            if (existing != null) {
               existing.setPlannedServings(servings);
               Recipe recipe = (Recipe)this.recipeMapper.selectById(recipeId);
               if (recipe != null && recipe.getUnitCost() != null) {
                  existing.setTotalCost(recipe.getUnitCost().multiply(BigDecimal.valueOf((long)servings.intValue())));
               }

               this.planItemMapper.updateById(existing);
            } else {
               RecipePlanItem newItem = new RecipePlanItem();
               newItem.setPlanId(planId);
               newItem.setRecipeId(recipeId);
               newItem.setPlannedServings(servings);
               newItem.setStatus("pending");
               Recipe recipe = (Recipe)this.recipeMapper.selectById(recipeId);
               if (recipe != null) {
                  newItem.setRecipeName(recipe.getRecipeName());
                  newItem.setRecipeCode(recipe.getRecipeCode());
                  newItem.setUnitCost(recipe.getUnitCost());
                  if (recipe.getUnitCost() != null) {
                     newItem.setTotalCost(recipe.getUnitCost().multiply(BigDecimal.valueOf((long)servings.intValue())));
                  }
               }

               Integer maxSort = this.planItemMapper.selectMaxSortByPlanId(planId);
               newItem.setSortOrder(maxSort == null ? 1 : maxSort + 1);
               this.planItemMapper.insert(newItem);
            }
         }
      }

      RecipePlan plan = (RecipePlan)this.getById(planId);
      this.calculateTotals(plan);
      this.updateById(plan);
   }

   private String generatePlanCode() {
      String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      String prefix = "RP" + dateStr;
      String maxCode = null;

      try {
         maxCode = (String)this.jdbcTemplate
            .queryForObject("SELECT MAX(plan_code) FROM recipe_plan WHERE plan_code LIKE ?", String.class, new Object[]{prefix + "%"});
      } catch (Exception var7) {
      }

      int nextSeq = 1;
      if (maxCode != null && maxCode.length() > prefix.length()) {
         try {
            nextSeq = Integer.parseInt(maxCode.substring(prefix.length())) + 1;
         } catch (NumberFormatException var6) {
         }
      }

      return prefix + String.format("%04d", nextSeq);
   }

   private String generateAdjustmentCode() {
      String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
      String prefix = "RPA" + dateStr;
      String maxCode = null;

      try {
         maxCode = (String)this.jdbcTemplate
            .queryForObject("SELECT MAX(adjust_code) FROM recipe_plan_adjustment WHERE adjust_code LIKE ?", String.class, new Object[]{prefix + "%"});
      } catch (Exception var7) {
         log.warn("闂傚倷绀侀崥瀣磿閹惰棄搴婇柤鑹扮堪娴滃綊鏌涢妷顔荤暗濞存粌缍婇弻鐔煎箚瑜忛幗鐘绘煥濞戞﹩妯€闁哄矉绻濆畷鐓庮潩椤戝灝顥氭繝鐢靛О閸ㄥ綊鎮ラ姀銈呭耿婵炲棙鍔﹂崯鈧梻鍌欐祰濡椼劎绮堟笟鈧、姘额敇閻樻剚娼熷銈呯箰閻楀﹪宕曞Ο濂藉綊鏁愰崱妯轰哗婵炲瓨绮岄妶鎼佸箖瑜版帒绠涢柍杞扮婵绱撴担浠嬪摵缂佽鐗撻悰顕€骞囬鐐电獮婵犵數濮撮崐缁樼鐎涙绡€婵炲牆鐏濋弸娑欍亜閹存繃鍣介柤娲憾椤㈡﹢濮€閻樼數鍘┑锛勫仜椤戝懐鈧稈鏅犲鏌ュ箹娴ｅ湱鍘? {}", var7.getMessage());
      }

      int nextSeq = 1;
      if (StrUtil.isNotBlank(maxCode) && maxCode.length() > prefix.length()) {
         try {
            nextSeq = Integer.parseInt(maxCode.substring(prefix.length())) + 1;
         } catch (NumberFormatException var6) {
            log.warn("闂備浇宕甸崰鎰版偡鏉堚晛绶ゅΔ锝呭暞閸婄敻鏌ｉ敐鍛拱閻庢凹鍓熼弻娑㈠箛閵婏附鐝曞┑鐐茬墣瀹曠數妲愰幘璇茬＜婵炴垶鑹鹃埛鍫ユ⒑缂佹澧紒顔肩Ч楠炲棝寮崶鈺冩澑闂佽鍎抽崯鍧楀煝韫囨梻绡€闁靛繆鈧啿濮哥紓渚囧枛婢т粙骞夐幘顔芥櫇闁稿本绋掑▍鏍煟韫囨洖浠﹂柛搴㈠▕閹焦绻濆顓犲幈闂佸湱鍎ら幐鍓х不閹炬番浜滄い鎰剁到婵牓鏌熼娑欘梿缂佺姵鐩獮姗€宕樺ù瀣€奸梻鍌欑劍閻綊宕规繝姘獥闁圭増婢橀崹? {}", maxCode);
         }
      }

      return prefix + String.format("%04d", nextSeq);
   }

   private Map<Long, RecipePlanAdjustment> loadLatestAdjustmentMap(List<Long> planIds) {
      if (planIds != null && !planIds.isEmpty()) {
         List<RecipePlanAdjustment> adjustments = this.adjustmentMapper
            .selectList(
               new LambdaQueryWrapper<RecipePlanAdjustment>().in(RecipePlanAdjustment::getPlanId, planIds)
                  .orderByDesc(RecipePlanAdjustment::getCreatedAt)
                  .orderByDesc(RecipePlanAdjustment::getId)
            );
         Map<Long, RecipePlanAdjustment> latestMap = new HashMap<>();

         for (RecipePlanAdjustment adjustment : adjustments) {
            if (adjustment.getPlanId() != null) {
               latestMap.putIfAbsent(adjustment.getPlanId(), adjustment);
            }
         }

         return latestMap;
      } else {
         return Map.of();
      }
   }

   private RecipePlanAdjustment loadLatestAdjustment(Long planId) {
      return planId == null
         ? null
         : (RecipePlanAdjustment)this.adjustmentMapper
            .selectOne(
               new LambdaQueryWrapper<RecipePlanAdjustment>().eq(RecipePlanAdjustment::getPlanId, planId)
                  .orderByDesc(RecipePlanAdjustment::getCreatedAt)
                  .orderByDesc(RecipePlanAdjustment::getId)
                  .last("LIMIT 1")
            );
   }

   private RecipePlanVO convertToVO(RecipePlan plan) {
      return this.convertToVO(plan, this.loadLatestAdjustment(plan.getId()));
   }

   private RecipePlanVO convertToVO(RecipePlan plan, RecipePlanAdjustment latestAdjustment) {
      RecipePlanVO vo = new RecipePlanVO();
      BeanUtils.copyProperties(plan, vo);
      vo.setMealTypeName(this.getMealTypeName(plan.getMealType()));
      vo.setStatusName(this.getStatusName(plan.getStatus()));
      vo.setStockRiskStatusName(this.getStockRiskStatusName(plan.getStockRiskStatus()));
      if (plan.getCreatedBy() != null) {
         vo.setCreatedByName(this.resolveEmployeeName(plan.getCreatedBy()));
      }
      vo.setOrgName(this.resolveOrgName(plan.getOrgId()));

      List<RecipePlanItem> planItems = this.planItemMapper.selectList(
         new LambdaQueryWrapper<RecipePlanItem>()
            .eq(RecipePlanItem::getPlanId, plan.getId())
            .orderByAsc(RecipePlanItem::getMealSortOrder)
            .orderByAsc(RecipePlanItem::getSortOrder)
            .orderByAsc(RecipePlanItem::getId)
      );
      Long recipeCount = (long)planItems.size();
      vo.setRecipeCount(recipeCount.intValue());
      List<RecipePlanDetailVO.RecipePlanItemVO> itemVOs = planItems.stream().map(item -> {
         RecipePlanDetailVO.RecipePlanItemVO itemVO = new RecipePlanDetailVO.RecipePlanItemVO();
         BeanUtils.copyProperties(item, itemVO);
         return itemVO;
      }).toList();
      List<RecipePlanDetailVO.MealScheduleVO> mealSchedules = this.buildMealScheduleVOs(itemVOs, plan);
      vo.setMealScheduleCount(mealSchedules.size());
      vo.setMealDisplayName(this.buildMealDisplayName(mealSchedules, plan));
      vo.setExpectedCountDisplay(this.buildExpectedCountDisplay(mealSchedules, plan));
      if (latestAdjustment != null) {
         vo.setAdjustmentStatus(latestAdjustment.getStatus());
         vo.setAdjustmentStatusName(this.getAdjustmentStatusName(latestAdjustment.getStatus()));
      }
      AdjustmentContext adjustmentContext = this.resolveAdjustmentContext(plan.getId());
      vo.setAdjustmentMode(adjustmentContext.getMode());
      vo.setAdjustmentModeName(this.getAdjustmentModeName(adjustmentContext.getMode()));
      vo.setAdjustmentHint(this.buildAdjustmentHint(adjustmentContext));
      vo.setCanAdjust("approved".equals(plan.getStatus()));

      return vo;
   }

   private String getMealTypeName(String mealType) {
      return this.getMealTypeDisplayName(mealType);
   }

    private String getMealTypeDisplayName(String mealType) {
        if (mealType == null) {
            return "";
        }
        return switch (mealType) {
            case "breakfast" -> "\u65e9\u9910";
            case "lunch" -> "\u5348\u9910";
            case "dinner" -> "\u665a\u9910";
            case "supper" -> "\u591c\u5bb5";
            case "custom" -> "\u81ea\u5b9a\u4e49\u9910\u6b21";
            case "multi" -> "\u591a\u9910\u6b21";
            default -> mealType;
        };
    }

    private String getTargetGroupDisplayName(String targetGroup) {
        if (targetGroup == null) {
            return "";
        }
        return switch (targetGroup) {
            case "adult" -> "\u666e\u901a\u6210\u4eba";
            case "elderly" -> "\u8001\u5e74\u4eba";
            case "child" -> "\u513f\u7ae5";
            case "teenager" -> "\u9752\u5c11\u5e74";
            case "patient" -> "\u75c5\u60a3";
            case "worker" -> "\u4f53\u529b\u52b3\u52a8\u8005";
            default -> targetGroup;
        };
    }

    private String getStatusName(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "draft" -> "\u8349\u7a3f";
            case "pending" -> "\u5f85\u5ba1\u6838";
            case "approved" -> "\u5df2\u5ba1\u6838";
            case "rejected" -> "\u5df2\u62d2\u7edd";
            case "completed" -> "\u5df2\u5b8c\u6210";
            default -> status;
        };
    }

    private String getStockRiskStatusName(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "normal" -> "\u6b63\u5e38";
            case "warning" -> "\u4e34\u671f\u9884\u8b66";
            case "expired" -> "\u5df2\u8fc7\u671f";
            case "shortage" -> "\u5e93\u5b58\u4e0d\u8db3";
            default -> "\u5f85\u4eba\u5de5\u786e\u8ba4";
        };
    }

    private String resolveEmployeeName(Long userId) {
        String name2;
        if (userId == null) {
            return "\u672a\u77e5";
        }
        try {
            name2 = (String)this.jdbcTemplate.queryForObject("SELECT real_name FROM sys_employee WHERE user_id = ?", String.class, new Object[]{userId});
            if (name2 != null) {
                return name2;
            }
        }
        catch (Exception ignored) {
            // empty catch block
        }
        try {
            name2 = (String)this.jdbcTemplate.queryForObject("SELECT real_name FROM auth_user WHERE id = ?", String.class, new Object[]{userId});
            if (name2 != null) {
                return name2;
            }
        }
        catch (Exception name3) {
            // empty catch block
        }
        UserContext ctx = UserContext.get();
        if (ctx != null) {
            if (userId.equals(ctx.getUserId())) {
                if (ctx.getRealName() != null) {
                    return ctx.getRealName();
                }
            }
        }
        return "\u672a\u77e5";
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        try {
            return (String)this.jdbcTemplate.queryForObject(
                "SELECT org_name FROM sys_organization WHERE id = ? AND deleted = 0",
                String.class,
                new Object[]{orgId}
            );
        }
        catch (Exception ignored) {
            return null;
        }
    }

   private void calculateTotals(RecipePlan plan) {
      List<RecipePlanItem> items = this.planItemMapper.selectList(new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, plan.getId()));
      int totalServings = 0;
      BigDecimal estimatedCost = BigDecimal.ZERO;

      for (RecipePlanItem item : items) {
         if (item.getPlannedServings() != null) {
            totalServings += item.getPlannedServings();
         }

         if (item.getTotalCost() != null) {
            estimatedCost = estimatedCost.add(item.getTotalCost());
         }
      }

      plan.setTotalServings(totalServings);
      plan.setEstimatedCost(estimatedCost);
   }

   private String getPlanSnapshotJson(Long planId) {
      RecipePlan plan = (RecipePlan)this.getById(planId);
      if (plan == null) {
         return "{}";
      } else {
         List<RecipePlanItem> items = this.planItemMapper
            .selectList(
               new LambdaQueryWrapper<RecipePlanItem>().eq(RecipePlanItem::getPlanId, planId)
                  .orderByAsc(RecipePlanItem::getSortOrder)
                  .orderByAsc(RecipePlanItem::getId)
            );
         AdjustmentPlanSnapshot snapshot = new AdjustmentPlanSnapshot();
         snapshot.setPlanId(plan.getId());
         snapshot.setPlanCode(plan.getPlanCode());
         snapshot.setPlanDate(this.formatLocalDate(plan.getPlanDate()));
         snapshot.setStartDate(this.formatLocalDate(plan.getStartDate()));
         snapshot.setEndDate(this.formatLocalDate(plan.getEndDate()));
         snapshot.setMealType(plan.getMealType());
         snapshot.setExpectedCount(plan.getExpectedCount());
         snapshot.setTargetGroup(plan.getTargetGroup());
         snapshot.setHealthStatus(this.splitHealthStatuses(plan.getHealthStatus()));
         snapshot.setDietRestrictions(plan.getDietRestrictions());
         snapshot.setRemark(plan.getRemark());
         snapshot.setUseAiRecommend(plan.getUseAiRecommend());
         List<AdjustmentMealScheduleSnapshot> mealSchedules = this.buildAdjustmentMealSchedulesFromItems(items, plan);
         snapshot.setMealSchedules(mealSchedules);
         snapshot.setRecipes(this.flattenAdjustmentRecipes(mealSchedules));

         try {
            return this.objectMapper.writeValueAsString(snapshot);
         } catch (JsonProcessingException var9) {
            log.error("闂備胶鎳撻崥瀣焽濞嗘垶宕查柟鐗堟緲閸ㄥ倹銇勮箛鎾跺缂佲偓瀹€鍕厸鐎广儱楠告晶顔剧磽瀹ュ懏鍤囬柟顔兼贡閳ь剨缍嗛崢鍓ц姳閹稿簺浜滈柟鎯х摠閸婃劗鈧娲樺畝鎼佺嵁閹烘绠ｉ柣鎰级缁楀酣姊绘担铏瑰笡闁圭妫濆畷姗€鏁傞幐搴㈢窔闂? planId={}", planId, var9);
            return "{}";
         }
      }
   }

    private List<RecipePlanAdjustmentDetailVO.AdjustItemVO> buildAdjustItems(String beforeData, String afterData, String fallbackType) {
        ArrayList<RecipePlanAdjustmentDetailVO.AdjustItemVO> items = new ArrayList<RecipePlanAdjustmentDetailVO.AdjustItemVO>();
        AdjustmentPlanSnapshot beforeSnapshot = this.safeParseAdjustmentSnapshot(beforeData);
        AdjustmentPlanSnapshot afterSnapshot = this.safeParseAdjustmentSnapshot(afterData);
        try {
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u8ba1\u5212\u65e5\u671f", this.formatLocalDateDisplay(beforeSnapshot.getPlanDate()), this.formatLocalDateDisplay(afterSnapshot.getPlanDate()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u5b9e\u65bd\u5f00\u59cb\u65e5\u671f", this.formatLocalDateDisplay(beforeSnapshot.getStartDate()), this.formatLocalDateDisplay(afterSnapshot.getStartDate()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u5b9e\u65bd\u7ed3\u675f\u65e5\u671f", this.formatLocalDateDisplay(beforeSnapshot.getEndDate()), this.formatLocalDateDisplay(afterSnapshot.getEndDate()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u9910\u6b21\u5b89\u6392", this.buildAdjustmentMealSummary(beforeSnapshot.getMealSchedules()), this.buildAdjustmentMealSummary(afterSnapshot.getMealSchedules()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u83dc\u8c31\u660e\u7ec6", this.buildAdjustmentRecipeSummary(beforeSnapshot.getMealSchedules()), this.buildAdjustmentRecipeSummary(afterSnapshot.getMealSchedules()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u76ee\u6807\u4eba\u7fa4", this.formatTargetGroupDisplay(beforeSnapshot.getTargetGroup()), this.formatTargetGroupDisplay(afterSnapshot.getTargetGroup()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u5065\u5eb7\u72b6\u51b5", this.formatHealthStatusDisplay(beforeSnapshot.getHealthStatus()), this.formatHealthStatusDisplay(afterSnapshot.getHealthStatus()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u996e\u98df\u9650\u5236\u8bf4\u660e", this.formatTextDisplay(beforeSnapshot.getDietRestrictions()), this.formatTextDisplay(afterSnapshot.getDietRestrictions()));
            this.appendFieldChange(items, PLAN_IMPORT_PLAN_SHEET, "\u5907\u6ce8\u4fe1\u606f", this.formatTextDisplay(beforeSnapshot.getRemark()), this.formatTextDisplay(afterSnapshot.getRemark()));
        }
        catch (Exception e) {
            log.warn("\u6784\u5efa\u8c03\u6574\u6458\u8981\u660e\u7ec6\u5931\u8d25\uff0c\u6539\u7528\u539f\u59cb\u5feb\u7167\u5dee\u5f02\u7ee7\u7eed\u8bc6\u522b", e);
        }
        this.appendRawDateFieldChangeIfMissing(items, PLAN_IMPORT_PLAN_SHEET, "\u8ba1\u5212\u65e5\u671f", beforeData, afterData, "planDate");
        this.appendRawDateFieldChangeIfMissing(items, PLAN_IMPORT_PLAN_SHEET, "\u5b9e\u65bd\u5f00\u59cb\u65e5\u671f", beforeData, afterData, "startDate");
        this.appendRawDateFieldChangeIfMissing(items, PLAN_IMPORT_PLAN_SHEET, "\u5b9e\u65bd\u7ed3\u675f\u65e5\u671f", beforeData, afterData, "endDate");
        return items;
    }

   private String resolveAdjustType(String beforeData, String afterData, String fallbackType) {
      try {
         AdjustmentPlanSnapshot beforeSnapshot = this.parseAdjustmentSnapshot(beforeData);
         AdjustmentPlanSnapshot afterSnapshot = this.parseAdjustmentSnapshot(afterData);
         boolean baseInfoChanged = !Objects.equals(this.normalizeText(beforeSnapshot.getPlanDate()), this.normalizeText(afterSnapshot.getPlanDate()))
            || !Objects.equals(this.normalizeText(beforeSnapshot.getStartDate()), this.normalizeText(afterSnapshot.getStartDate()))
            || !Objects.equals(this.normalizeText(beforeSnapshot.getEndDate()), this.normalizeText(afterSnapshot.getEndDate()))
            || !Objects.equals(this.buildAdjustmentMealSummary(beforeSnapshot.getMealSchedules()), this.buildAdjustmentMealSummary(afterSnapshot.getMealSchedules()))
            || !Objects.equals(this.buildAdjustmentRecipeSummary(beforeSnapshot.getMealSchedules()), this.buildAdjustmentRecipeSummary(afterSnapshot.getMealSchedules()))
            || !Objects.equals(this.normalizeText(beforeSnapshot.getTargetGroup()), this.normalizeText(afterSnapshot.getTargetGroup()))
            || !Objects.equals(
               this.normalizeText(this.joinHealthStatuses(beforeSnapshot.getHealthStatus())),
               this.normalizeText(this.joinHealthStatuses(afterSnapshot.getHealthStatus()))
            )
            || !Objects.equals(this.normalizeText(beforeSnapshot.getDietRestrictions()), this.normalizeText(afterSnapshot.getDietRestrictions()))
            || !Objects.equals(this.normalizeText(beforeSnapshot.getRemark()), this.normalizeText(afterSnapshot.getRemark()));
         if (baseInfoChanged) {
            return "modify";
         }
      } catch (Exception var15) {
         log.warn("闂備浇宕甸崰鎰版偡鏉堚晛绶ゅΔ锝呭暞閸婄敻鏌ｉ敐鍛拱閻庢凹鍓熼弻娑㈠箛閵婏附鐝曞┑鐐茬墣瀹曠數妲愰幒妤婃晝闁靛鍠栧▓顓㈡⒑鐠囨煡鍙勯柡鈧崡鐑嗗殫闁告洦鍓氱紞鍥ㄣ亜閹扳晛鍓鹃柡鍥ュ灪閻撱儲绻涢幋鐏活亪銆冨▎蹇婃斀闁绘劕寮堕崰姗€鏌熼鑽ょ煓濠碘剝鎮傛俊鐑藉Ψ椤旂晫鈧偆绱撻崒娆戝妽婵☆偄绻樺畷褰掝敍濠婂嫷娼熼梺鍓插亝濞叉牜绮堥崒鐐寸厪濠㈣鍨板Λ顓烆焽閸洘鈷? {}", var15.getMessage());
      }

      return StrUtil.blankToDefault(fallbackType, "modify");
   }

   private AdjustmentPlanSnapshot parseAdjustmentSnapshot(String json) throws JsonProcessingException {
      AdjustmentPlanSnapshot snapshot = new AdjustmentPlanSnapshot();
      snapshot.setRecipes(new ArrayList());
      snapshot.setMealSchedules(new ArrayList());
      snapshot.setHealthStatus(new ArrayList());
      if (StrUtil.isBlank(json)) {
         return snapshot;
      } else {
         JsonNode rootNode = this.objectMapper.readTree(json);
         if (rootNode == null || rootNode.isNull()) {
            return snapshot;
         } else if (rootNode.isObject()) {
            ObjectNode normalizedRootNode = ((ObjectNode)rootNode).deepCopy();
            this.normalizeAdjustmentSnapshotNode(normalizedRootNode);
            AdjustmentPlanSnapshot resolved = (AdjustmentPlanSnapshot)this.objectMapper.treeToValue(normalizedRootNode, AdjustmentPlanSnapshot.class);
            if (resolved.getRecipes() == null) {
               resolved.setRecipes(new ArrayList());
            }

            if (resolved.getMealSchedules() == null) {
               resolved.setMealSchedules(new ArrayList());
            }

            if (resolved.getHealthStatus() == null) {
               resolved.setHealthStatus(new ArrayList());
            }

            if (resolved.getMealSchedules().isEmpty() && !resolved.getRecipes().isEmpty()) {
               AdjustmentMealScheduleSnapshot legacySchedule = new AdjustmentMealScheduleSnapshot();
               legacySchedule.setMealKey("legacy-1");
               legacySchedule.setMealType(resolved.getMealType());
               legacySchedule.setMealName(this.resolveMealScheduleName(resolved.getMealType(), null));
               legacySchedule.setExpectedCount(resolved.getExpectedCount());
               legacySchedule.setSortOrder(1);
               legacySchedule.setRecipes(resolved.getRecipes());
               resolved.setMealSchedules(List.of(legacySchedule));
            }
            return resolved;
         } else if (rootNode.isArray()) {
            AdjustmentPlanSnapshot legacySnapshot = new AdjustmentPlanSnapshot();
            List<AdjustmentPlanRecipeSnapshot> recipes = (List)this.objectMapper
                  .readValue(json, this.objectMapper.getTypeFactory().constructCollectionType(List.class, AdjustmentPlanRecipeSnapshot.class))
            ;
            legacySnapshot.setRecipes(recipes);
            AdjustmentMealScheduleSnapshot legacySchedule = new AdjustmentMealScheduleSnapshot();
            legacySchedule.setMealKey("legacy-1");
            legacySchedule.setMealType(legacySnapshot.getMealType());
            legacySchedule.setMealName(this.resolveMealScheduleName(legacySnapshot.getMealType(), null));
            legacySchedule.setExpectedCount(legacySnapshot.getExpectedCount());
            legacySchedule.setSortOrder(1);
            legacySchedule.setRecipes(recipes);
            legacySnapshot.setMealSchedules(List.of(legacySchedule));
            legacySnapshot.setHealthStatus(new ArrayList());
            return legacySnapshot;
         } else {
            return snapshot;
         }
      }
   }

   private void normalizeAdjustmentSnapshotNode(ObjectNode rootNode) {
      if (rootNode == null) {
         return;
      }
      this.normalizeHealthStatusField(rootNode, "healthStatus");
      this.normalizeBlankStringCollectionField(rootNode, "mealSchedules");
      this.normalizeBlankStringCollectionField(rootNode, "recipes");
      JsonNode mealSchedulesNode = rootNode.get("mealSchedules");
      if (mealSchedulesNode != null && mealSchedulesNode.isArray()) {
         for (JsonNode mealScheduleNode : mealSchedulesNode) {
            if (mealScheduleNode instanceof ObjectNode mealScheduleObjectNode) {
               this.normalizeBlankStringCollectionField(mealScheduleObjectNode, "recipes");
            }
         }
      }
   }

   private void normalizeHealthStatusField(ObjectNode rootNode, String fieldName) {
      JsonNode fieldNode = rootNode.get(fieldName);
      if (fieldNode == null || fieldNode.isNull() || fieldNode.isArray()) {
         return;
      }
      if (fieldNode.isTextual()) {
         String value = fieldNode.asText();
         rootNode.set(fieldName, this.objectMapper.valueToTree(this.splitHealthStatuses(value)));
      }
   }

   private void normalizeBlankStringCollectionField(ObjectNode rootNode, String fieldName) {
      JsonNode fieldNode = rootNode.get(fieldName);
      if (fieldNode != null && fieldNode.isTextual() && StrUtil.isBlank(fieldNode.asText())) {
         rootNode.set(fieldName, this.objectMapper.createArrayNode());
      }
   }

   private AdjustmentPlanSnapshot safeParseAdjustmentSnapshot(String json) {
      try {
         return this.parseAdjustmentSnapshot(json);
      } catch (Exception ex) {
         log.warn("解析调整快照失败，改用空快照继续识别差异", ex);
         AdjustmentPlanSnapshot snapshot = new AdjustmentPlanSnapshot();
         snapshot.setRecipes(new ArrayList());
         snapshot.setMealSchedules(new ArrayList());
         snapshot.setHealthStatus(new ArrayList());
         return snapshot;
      }
   }

   private Map<Long, AdjustmentPlanRecipeSnapshot> buildRecipeSnapshotMap(List<AdjustmentPlanRecipeSnapshot> recipes) {
      Map<Long, AdjustmentPlanRecipeSnapshot> recipeMap = new LinkedHashMap<>();
      if (recipes == null) {
         return recipeMap;
      } else {
         for (AdjustmentPlanRecipeSnapshot recipe : recipes) {
            if (recipe != null && recipe.getRecipeId() != null && !recipeMap.containsKey(recipe.getRecipeId())) {
               recipeMap.put(recipe.getRecipeId(), recipe);
            }
         }

         return recipeMap;
      }
   }

    private void appendFieldChange(List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items, String fieldName, String fieldLabel, String beforeValue, String afterValue) {
        if (!Objects.equals(this.normalizeText(beforeValue), this.normalizeText(afterValue))) {
            items.add(this.buildAdjustItem(fieldName, fieldLabel, beforeValue, afterValue));
        }
    }

    private void appendRawDateFieldChange(List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items, String fieldName, String fieldLabel, String beforeData, String afterData, String jsonFieldName) {
        String beforeValue = this.extractRawSnapshotText(beforeData, jsonFieldName);
        String afterValue = this.extractRawSnapshotText(afterData, jsonFieldName);
        if (beforeValue == null && afterValue == null) {
            return;
        }
        this.appendFieldChange(items, fieldName, fieldLabel, this.formatLocalDateDisplay(beforeValue), this.formatLocalDateDisplay(afterValue));
    }

    private void appendRawDateFieldChangeIfMissing(List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items, String fieldName, String fieldLabel, String beforeData, String afterData, String jsonFieldName) {
        if (this.hasAdjustItem(items, fieldName, fieldLabel)) {
            return;
        }
        this.appendRawDateFieldChange(items, fieldName, fieldLabel, beforeData, afterData, jsonFieldName);
    }

    private boolean hasAdjustItem(List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items, String fieldName, String fieldLabel) {
        if (items == null || items.isEmpty()) {
            return false;
        }
        for (RecipePlanAdjustmentDetailVO.AdjustItemVO item : items) {
            if (item == null) {
                continue;
            }
            if (Objects.equals(this.normalizeText(item.getFieldName()), this.normalizeText(fieldName))
                && Objects.equals(this.normalizeText(item.getFieldLabel()), this.normalizeText(fieldLabel))) {
                return true;
            }
        }
        return false;
    }

    private String extractRawSnapshotText(String json, String fieldName) {
        if (StrUtil.isBlank(json) || StrUtil.isBlank(fieldName)) {
            return null;
        }
        try {
            JsonNode rootNode = this.objectMapper.readTree(json);
            if (rootNode == null || rootNode.isNull() || !rootNode.isObject()) {
                return null;
            }
            JsonNode fieldNode = rootNode.get(fieldName);
            if (fieldNode == null || fieldNode.isNull()) {
                return null;
            }
            return StrUtil.trimToNull(fieldNode.asText());
        } catch (Exception ex) {
            log.warn("提取调整快照日期字段失败: fieldName={}", fieldName, ex);
            return null;
        }
    }

    private String buildRecipeDisplayName(AdjustmentPlanRecipeSnapshot recipe) {
        if (recipe == null) {
            return "\u672a\u77e5\u83dc\u8c31";
        }
        if (StrUtil.isNotBlank((CharSequence)recipe.getRecipeName())) {
            return recipe.getRecipeName();
        }
        return this.resolveRecipeName(null, recipe.getRecipeId());
    }

    private String normalizeText(String value) {
        return StrUtil.trimToEmpty((CharSequence)value);
    }

   private String formatLocalDate(LocalDate value) {
      return value == null ? null : value.toString();
   }

   private LocalDate parseLocalDate(String value, LocalDate fallbackValue) {
      if (StrUtil.isBlank(value)) {
         return fallbackValue;
      } else {
         try {
            return LocalDate.parse(value);
         } catch (Exception var4) {
            log.warn("闂備浇宕甸崰鎰版偡鏉堚晛绶ゅΔ锝呭暞閸婇潧霉閻樺樊鍎忕紒顐㈢Ч閺屾洘寰勫☉銏☆€嶉梺绯曟櫅閸婃悂鈥﹂崸妤€绠氶柟娈垮枤缂堥亶鏌ｉ悢鍝ユ嚂缂佺姵鎹囧顐㈩吋閸ワ附鍕冮梺缁樻尭缁ㄥ爼寮歌箛娑欌拺婵炶尙绮繛鍥煕閺冣偓閸ㄥ綊宕氶幒妤婃晬婵犲﹤瀚娑㈡⒑闂堟稓澧曢柟鍐差樀瀹? {}", value);
            return fallbackValue;
         }
      }
   }

   private List<String> splitHealthStatuses(String healthStatus) {
      return (List<String>)(StrUtil.isBlank(healthStatus)
         ? new ArrayList<>()
         : Arrays.stream(healthStatus.split(",")).map(String::trim).filter(CharSequenceUtil::isNotBlank).collect(Collectors.toCollection(ArrayList::new)));
   }

   private String joinHealthStatuses(List<String> healthStatus) {
      return healthStatus != null && !healthStatus.isEmpty()
         ? healthStatus.stream().map(String::trim).filter(CharSequenceUtil::isNotBlank).collect(Collectors.joining(","))
         : null;
   }

    private String formatLocalDateDisplay(String value) {
        return StrUtil.isBlank((CharSequence)value) ? "\u2014" : value;
    }

    private String formatMealTypeDisplay(String mealType) {
        if (StrUtil.isBlank((CharSequence)mealType)) {
            return "\u2014";
        }
        return this.getMealTypeName(mealType);
    }

    private String formatTargetGroupDisplay(String targetGroup) {
        if (StrUtil.isBlank((CharSequence)targetGroup)) {
            return "\u2014";
        }
        return this.getPortraitName(targetGroup);
    }

    private String formatHealthStatusDisplay(List<String> healthStatus) {
        if (healthStatus == null || healthStatus.isEmpty()) {
            return "\u2014";
        }
        return healthStatus.stream().map(this::getHealthStatusName).collect(Collectors.joining("\u3001"));
    }

    private String formatIntegerDisplay(Integer value, String suffix) {
        if (value == null) {
            return "\u2014";
        }
        return value + suffix;
    }

    private String formatTextDisplay(String value) {
        return StrUtil.isBlank((CharSequence)value) ? "\u2014" : value.trim();
    }

    private String getHealthStatusName(String code) {
        if (StrUtil.isBlank((CharSequence)code)) {
            return "\u672a\u77e5";
        }
        return switch (code) {
            case "diabetes" -> "\u7cd6\u5c3f\u75c5";
            case "hypertension" -> "\u9ad8\u8840\u538b";
            case "hyperlipidemia" -> "\u9ad8\u8840\u8102";
            case "obesity" -> "\u80a5\u80d6";
            case "gout" -> "\u75db\u98ce";
            case "kidney_disease" -> "\u80be\u75c5";
            case "stomach_disease" -> "\u80c3\u75c5";
            case "anemia" -> "\u8d2b\u8840";
            default -> code;
        };
    }

   private int calculateNutritionScore(BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, int servings) {
      if (servings == 0) {
         return 0;
      } else {
         BigDecimal standardCalories = BigDecimal.valueOf((long)(servings * 500));
         BigDecimal standardProtein = BigDecimal.valueOf((long)(servings * 20));
         int score = 100;
         BigDecimal caloriesRatio = calories.divide(standardCalories, 2, RoundingMode.HALF_UP);
         if (caloriesRatio.compareTo(BigDecimal.valueOf(1.2)) > 0) {
            score -= caloriesRatio.subtract(BigDecimal.valueOf(1.2)).multiply(BigDecimal.valueOf(50L)).intValue();
         } else if (caloriesRatio.compareTo(BigDecimal.valueOf(0.8)) < 0) {
            score -= BigDecimal.valueOf(0.8).subtract(caloriesRatio).multiply(BigDecimal.valueOf(30L)).intValue();
         }

         BigDecimal proteinRatio = protein.divide(standardProtein, 2, RoundingMode.HALF_UP);
         if (proteinRatio.compareTo(BigDecimal.valueOf(0.9)) < 0) {
            score -= 10;
         }

         return Math.max(0, Math.min(100, score));
      }
   }

   private String calculateNutritionGrade(int score) {
      if (score >= 90) {
         return "excessive";
      } else if (score >= 80) {
         return "balanced";
      } else {
         return score >= 60 ? "good" : "needs_improvement";
      }
   }

    private String getGradeDescription(String grade) {
        switch (grade) {
            case "excessive": {
                return "\u8425\u517b\u6444\u5165\u8fc7\u91cf\uff0c\u5efa\u8bae\u9002\u5f53\u63a7\u5236\u603b\u70ed\u91cf\u548c\u8425\u517b\u7d20\u6444\u5165\uff0c\u5173\u6ce8\u996e\u98df\u5e73\u8861";
            }
            case "balanced": {
                return "\u8425\u517b\u642d\u914d\u8fbe\u6807\uff0c\u5404\u7c7b\u8425\u517b\u7d20\u6bd4\u4f8b\u5408\u7406\uff0c\u7ee7\u7eed\u4fdd\u6301\u5f53\u524d\u996e\u98df\u7ed3\u6784";
            }
            case "good": {
                return "\u8425\u517b\u642d\u914d\u826f\u597d\uff0c\u6574\u4f53\u6c34\u5e73\u4e0d\u9519\uff0c\u53ef\u9002\u5f53\u4f18\u5316\u4ee5\u8fbe\u5230\u66f4\u4f73\u72b6\u6001";
            }
        }
        return "\u8425\u517b\u642d\u914d\u9700\u6539\u8fdb\uff0c\u5efa\u8bae\u53c2\u8003AI\u63a8\u8350\u83dc\u8c31\u8fdb\u884c\u8c03\u6574\uff0c\u63d0\u5347\u6574\u4f53\u8425\u517b\u6c34\u5e73";
    }

    private String generateNutritionAssessment(int score, BigDecimal calories, BigDecimal protein, BigDecimal carbohydrate, BigDecimal fat, int servings) {
        StringBuilder sb = new StringBuilder();
        sb.append("\u3010AI\u8425\u517b\u8bc4\u4f30\u62a5\u544a\u3011\n\n");
        if (score >= 80) {
            sb.append("\u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u4f18\u79c0\uff0c");
        } else if (score >= 60) {
            sb.append("\u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u826f\u597d\uff0c");
        } else {
            sb.append("\u672c\u6b21\u83dc\u8c31\u8ba1\u5212\u8425\u517b\u642d\u914d\u6709\u5f85\u6539\u5584\uff0c");
        }
        sb.append("\u603b\u4efd\u6570\uff1a").append(servings).append("\u4efd\n");
        sb.append("\u603b\u70ed\u91cf\uff1a").append(calories.setScale(0, RoundingMode.HALF_UP)).append("\u5343\u5361\n");
        sb.append("\u86cb\u767d\u8d28\uff1a").append(protein.setScale(1, RoundingMode.HALF_UP)).append("g\n");
        sb.append("\u78b3\u6c34\u5316\u5408\u7269\uff1a").append(carbohydrate.setScale(1, RoundingMode.HALF_UP)).append("g\n");
        sb.append("\u8102\u80aa\uff1a").append(fat.setScale(1, RoundingMode.HALF_UP)).append("g\n");
        return sb.toString();
    }

    private List<String> generateNutritionSuggestions(int score, BigDecimal calories, BigDecimal protein, BigDecimal fat, int servings) {
        BigDecimal avgProtein;
        ArrayList<String> suggestions = new ArrayList<String>();
        BigDecimal avgCalories = servings > 0 ? calories.divide(BigDecimal.valueOf(servings), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal bigDecimal = avgProtein = servings > 0 ? protein.divide(BigDecimal.valueOf(servings), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        if (avgCalories.compareTo(BigDecimal.valueOf(600L)) > 0) {
            suggestions.add("\u5355\u4efd\u70ed\u91cf\u504f\u9ad8\uff0c\u5efa\u8bae\u51cf\u5c11\u9ad8\u70ed\u91cf\u98df\u6750\u7528\u91cf");
        } else if (avgCalories.compareTo(BigDecimal.valueOf(300L)) < 0) {
            suggestions.add("\u5355\u4efd\u70ed\u91cf\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u4e3b\u98df\u6216\u9ad8\u80fd\u91cf\u98df\u6750");
        }
        if (avgProtein.compareTo(BigDecimal.valueOf(15L)) < 0) {
            suggestions.add("\u86cb\u767d\u8d28\u542b\u91cf\u504f\u4f4e\uff0c\u5efa\u8bae\u589e\u52a0\u8089\u7c7b\u3001\u86cb\u7c7b\u6216\u8c46\u5236\u54c1");
        }
        if (score < 70) {
            suggestions.add("\u5efa\u8bae\u53c2\u8003AI\u63a8\u8350\u83dc\u8c31\u4f18\u5316\u642d\u914d");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("\u8425\u517b\u642d\u914d\u5747\u8861\uff0c\u7ee7\u7eed\u4fdd\u6301");
        }
        return suggestions;
    }

    @Override
    public AIRecommendResultVO getAiRecommendRecipesEnhanced(RecipePlanQueryDTO query) {
        List<RecipeVO> baseRecommendations = this.getAiRecommendRecipes(query);
        AIRecommendResultVO result = new AIRecommendResultVO();
        result.setRecipes(baseRecommendations);
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalCalories = BigDecimal.ZERO;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbohydrate = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        int totalNutritionScore = 0;
        int recipeCount = 0;
        for (RecipeVO vo : baseRecommendations) {
            if (vo.getEstimatedCost() != null) {
                totalCost = totalCost.add(vo.getEstimatedCost());
            }
            if (vo.getNutritionInfo() != null) {
                RecipeVO.NutritionInfoVO nutrition = vo.getNutritionInfo();
                if (nutrition.getCalories() != null) {
                    totalCalories = totalCalories.add(nutrition.getCalories());
                }
                if (nutrition.getProtein() != null) {
                    totalProtein = totalProtein.add(nutrition.getProtein());
                }
                if (nutrition.getCarbohydrate() != null) {
                    totalCarbohydrate = totalCarbohydrate.add(nutrition.getCarbohydrate());
                }
                if (nutrition.getFat() != null) {
                    totalFat = totalFat.add(nutrition.getFat());
                }
            }
            if (vo.getNutritionScore() == null) continue;
            totalNutritionScore += vo.getNutritionScore().intValue();
            ++recipeCount;
        }
        result.setTotalEstimatedCost(totalCost);
        int servingCount = query.getExpectedCount() != null ? query.getExpectedCount() : 1;
        BigDecimal perCapitaCost = totalCost.divide(BigDecimal.valueOf(servingCount), 2, RoundingMode.HALF_UP);
        BigDecimal budgetRemaining = BigDecimal.ZERO;
        if (query.getBudgetLimit() != null && query.getBudgetLimit().compareTo(BigDecimal.ZERO) > 0) {
            budgetRemaining = query.getBudgetLimit().subtract(totalCost);
            if (totalCost.compareTo(query.getBudgetLimit()) <= 0) {
                result.setBudgetStatus("within");
            } else if (totalCost.compareTo(query.getBudgetLimit().multiply(BigDecimal.valueOf(1.1))) <= 0) {
                result.setBudgetStatus("near");
                result.setBudgetWarning(String.format("\u9884\u4f30\u6210\u672c%.2f\u5143\uff0c\u63a5\u8fd1\u9884\u7b97%.2f\u5143", totalCost, query.getBudgetLimit()));
            } else {
                result.setBudgetStatus("exceeded");
                result.setBudgetWarning(String.format("\u9884\u4f30\u6210\u672c%.2f\u5143\uff0c\u8d85\u51fa\u9884\u7b97%.2f\u5143", totalCost, query.getBudgetLimit()));
            }
            AIRecommendResultVO.BudgetInfo budgetInfo = AIRecommendResultVO.BudgetInfo.builder().budgetAmount(query.getBudgetLimit()).usedAmount(totalCost).remainingAmount(budgetRemaining).usedPercentage(totalCost.divide(query.getBudgetLimit(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L))).status(result.getBudgetStatus()).suggestion(this.generateBudgetSuggestion(result.getBudgetStatus(), budgetRemaining, query.getBudgetLimit())).build();
            result.setBudgetInfo(budgetInfo);
        }
        BigDecimal avgNutritionScore = recipeCount > 0 ? BigDecimal.valueOf(totalNutritionScore).divide(BigDecimal.valueOf(recipeCount), 1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        AIRecommendResultVO.RecommendStatistics statistics = AIRecommendResultVO.RecommendStatistics.builder().totalRecipes(baseRecommendations.size()).estimatedTotalCost(totalCost).perCapitaCost(perCapitaCost).budgetRemaining(budgetRemaining).avgNutritionScore(avgNutritionScore).build();
        result.setStatistics(statistics);
        AIRecommendResultVO.NutritionOverview nutritionOverview = AIRecommendResultVO.NutritionOverview.builder().totalProtein(totalProtein).totalCarbohydrate(totalCarbohydrate).totalFat(totalFat).totalCalories(totalCalories).avgProtein(totalProtein.divide(BigDecimal.valueOf(servingCount), 2, RoundingMode.HALF_UP)).avgCarbohydrate(totalCarbohydrate.divide(BigDecimal.valueOf(servingCount), 2, RoundingMode.HALF_UP)).avgFat(totalFat.divide(BigDecimal.valueOf(servingCount), 2, RoundingMode.HALF_UP)).avgCalories(totalCalories.divide(BigDecimal.valueOf(servingCount), 2, RoundingMode.HALF_UP)).build();
        result.setNutritionOverview(nutritionOverview);
        StringBuilder reasonBuilder = new StringBuilder();
        if (query.getFlavorPreferences() != null) {
            reasonBuilder.append("\u7b26\u5408\u60a8\u7684\u53e3\u5473\u504f\u597d");
        }
        if (query.getDietTags() != null) {
            if (reasonBuilder.length() > 0) {
                reasonBuilder.append("\uff1b");
            }
            reasonBuilder.append("\u7b26\u5408\u60a8\u7684\u996e\u98df\u9700\u6c42");
        }
        if (query.getTargetGroup() != null) {
            if (reasonBuilder.length() > 0) {
                reasonBuilder.append("\uff1b");
            }
            reasonBuilder.append("\u9002\u5408").append(query.getTargetGroup()).append("\u4eba\u7fa4");
        }
        if (reasonBuilder.length() == 0) {
            reasonBuilder.append("\u57fa\u4e8e\u8425\u517b\u5747\u8861\u548c\u53e3\u5473\u591a\u6837\u6027\u63a8\u8350");
        }
        result.setRecommendReason(reasonBuilder.toString());
        if ("week".equals(query.getPlanDimension())) {
            List<AIRecommendResultVO.DailyPlanVO> weeklyPlan = this.generateWeeklyPlan(query, baseRecommendations, servingCount);
            result.setWeeklyPlan(weeklyPlan);
        }
        if ("month".equals(query.getPlanDimension())) {
            List<AIRecommendResultVO.WeeklyPlanVO> monthlyPlan = this.generateMonthlyPlan(query, baseRecommendations, servingCount);
            result.setMonthlyPlan(monthlyPlan);
        }
        return result;
    }

    private String generateBudgetSuggestion(String status, BigDecimal remaining, BigDecimal budget) {
        if ("within".equals(status)) {
            if (remaining.compareTo(budget.multiply(BigDecimal.valueOf(0.2))) > 0) {
                return "\u9884\u7b97\u5145\u8db3\uff0c\u53ef\u8003\u8651\u589e\u52a0\u83dc\u54c1\u6570\u91cf\u6216\u63d0\u5347\u98df\u6750\u54c1\u8d28";
            }
            return "\u9884\u7b97\u4f7f\u7528\u5408\u7406\uff0c\u5f53\u524d\u65b9\u6848\u6027\u4ef7\u6bd4\u8f83\u9ad8";
        }
        if ("near".equals(status)) {
            return "\u9884\u7b97\u63a5\u8fd1\u4e0a\u9650\uff0c\u5efa\u8bae\u5173\u6ce8\u98df\u6750\u6210\u672c\uff0c\u53ef\u9009\u62e9\u6027\u4ef7\u6bd4\u9ad8\u7684\u66ff\u4ee3\u98df\u6750";
        }
        return "\u9884\u7b97\u8d85\u652f\uff0c\u5efa\u8bae\u51cf\u5c11\u83dc\u54c1\u6570\u91cf\u6216\u9009\u62e9\u66f4\u7ecf\u6d4e\u7684\u98df\u6750\u66ff\u4ee3\u65b9\u6848";
    }

    private List<AINutritionAssessmentVO.NutritionComparison> buildNutritionComparisons(BigDecimal avgCalories, BigDecimal avgProtein, BigDecimal avgCarbohydrate, BigDecimal avgFat, int servingCount) {
        ArrayList<AINutritionAssessmentVO.NutritionComparison> comparisons = new ArrayList<AINutritionAssessmentVO.NutritionComparison>();
        NutritionTargetConfig.GroupNutritionTarget target = this.nutritionTargetConfig.getPerMealTarget("adult");
        comparisons.add(this.buildNutritionComparison("\u70ed\u91cf", avgCalories, target.getCalories()));
        comparisons.add(this.buildNutritionComparison("\u86cb\u767d\u8d28", avgProtein, target.getProtein()));
        comparisons.add(this.buildNutritionComparison("\u78b3\u6c34\u5316\u5408\u7269", avgCarbohydrate, target.getCarbohydrate()));
        comparisons.add(this.buildNutritionComparison("\u8102\u80aa", avgFat, target.getFat()));
        return comparisons;
    }

    private AINutritionAssessmentVO.NutritionComparison buildNutritionComparison(String nutrientName, BigDecimal actualValue, BigDecimal targetValue) {
        BigDecimal percentage;
        if (actualValue == null) {
            actualValue = BigDecimal.ZERO;
        }
        if (targetValue == null) {
            targetValue = BigDecimal.ZERO;
        }
        BigDecimal bigDecimal = percentage = targetValue.compareTo(BigDecimal.ZERO) > 0 ? actualValue.divide(targetValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100L)).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        String status = percentage.compareTo(BigDecimal.valueOf(80L)) >= 0 && percentage.compareTo(BigDecimal.valueOf(120L)) <= 0 ? "\u8fbe\u6807" : (percentage.compareTo(BigDecimal.valueOf(80L)) < 0 ? "\u4e0d\u8db3" : "\u8fc7\u91cf");
        return AINutritionAssessmentVO.NutritionComparison.builder().nutrientName(nutrientName).actualValue(actualValue.setScale(2, RoundingMode.HALF_UP)).targetValue(targetValue.setScale(2, RoundingMode.HALF_UP)).status(status).percentage(percentage).build();
    }

    private List<AIRecommendResultVO.DailyPlanVO> generateWeeklyPlan(RecipePlanQueryDTO query, List<RecipeVO> recipes, int servingCount) {
        ArrayList<AIRecommendResultVO.DailyPlanVO> plan = new ArrayList<AIRecommendResultVO.DailyPlanVO>();
        LocalDate startDate = query.getWeekStartDate() != null ? query.getWeekStartDate() : LocalDate.now();
        int days = query.getDaysCount() != null ? query.getDaysCount() : 7;
        String[] dayNames = new String[]{"\u5468\u65e5", "\u5468\u4e00", "\u5468\u4e8c", "\u5468\u4e09", "\u5468\u56db", "\u5468\u4e94", "\u5468\u516d"};
        String[] mealTypes = new String[]{"\u65e9\u9910", "\u5348\u9910", "\u665a\u9910"};
        for (int i = 0; i < days; ++i) {
            AIRecommendResultVO.DailyPlanVO dailyPlan = new AIRecommendResultVO.DailyPlanVO();
            LocalDate date = startDate.plusDays(i);
            dailyPlan.setDate(date.toString());
            dailyPlan.setDayOfWeek(dayNames[date.getDayOfWeek().getValue() % 7]);
            ArrayList<RecipeVO> dailyRecipes = new ArrayList<RecipeVO>();
            BigDecimal dailyCost = BigDecimal.ZERO;
            int dailyNutritionScore = 0;
            int dailyRecipeCount = 0;
            for (int meal = 0; meal < 3 && meal < recipes.size(); ++meal) {
                int recipeIndex = (i * 3 + meal) % recipes.size();
                RecipeVO recipe = recipes.get(recipeIndex);
                dailyRecipes.add(recipe);
                if (recipe.getEstimatedCost() != null) {
                    dailyCost = dailyCost.add(recipe.getEstimatedCost());
                }
                if (recipe.getNutritionScore() == null) continue;
                dailyNutritionScore += recipe.getNutritionScore().intValue();
                ++dailyRecipeCount;
            }
            dailyPlan.setRecipes(dailyRecipes);
            dailyPlan.setDailyCost(dailyCost);
            dailyPlan.setDailyNutritionScore(dailyRecipeCount > 0 ? dailyNutritionScore / dailyRecipeCount : 70);
            StringBuilder recommendation = new StringBuilder();
            recommendation.append("\u4eca\u65e5\u63a8\u8350").append(dailyRecipes.size()).append("\u9053\u83dc\u54c1");
            if (dailyNutritionScore >= 80) {
                recommendation.append("\uff0c\u8425\u517b\u642d\u914d\u5747\u8861");
            } else if (dailyNutritionScore >= 60) {
                recommendation.append("\uff0c\u8425\u517b\u642d\u914d\u826f\u597d");
            }
            dailyPlan.setRecommendation(recommendation.toString());
            plan.add(dailyPlan);
        }
        return plan;
    }

    private List<AIRecommendResultVO.WeeklyPlanVO> generateMonthlyPlan(RecipePlanQueryDTO query, List<RecipeVO> recipes, int servingCount) {
        ArrayList<AIRecommendResultVO.WeeklyPlanVO> monthlyPlan = new ArrayList<AIRecommendResultVO.WeeklyPlanVO>();
        LocalDate startDate = query.getWeekStartDate() != null ? query.getWeekStartDate() : LocalDate.now();
        for (int week = 0; week < 4; ++week) {
            AIRecommendResultVO.WeeklyPlanVO weeklyPlanVO = new AIRecommendResultVO.WeeklyPlanVO();
            LocalDate weekStart = startDate.plusWeeks(week);
            LocalDate weekEnd = weekStart.plusDays(6L);
            weeklyPlanVO.setWeekNumber(week + 1);
            weeklyPlanVO.setStartDate(weekStart.toString());
            weeklyPlanVO.setEndDate(weekEnd.toString());
            RecipePlanQueryDTO weekQuery = new RecipePlanQueryDTO();
            weekQuery.setWeekStartDate(weekStart);
            weekQuery.setDaysCount(7);
            weekQuery.setExpectedCount(servingCount);
            weekQuery.setBudgetLimit(query.getBudgetLimit() != null ? query.getBudgetLimit().divide(BigDecimal.valueOf(4L), 2, RoundingMode.HALF_UP) : null);
            List<AIRecommendResultVO.DailyPlanVO> dailyPlans = this.generateWeeklyPlan(weekQuery, recipes, servingCount);
            weeklyPlanVO.setDailyPlans(dailyPlans);
            BigDecimal weeklyCost = BigDecimal.ZERO;
            int weeklyNutritionScore = 0;
            int dayCount = 0;
            for (AIRecommendResultVO.DailyPlanVO day : dailyPlans) {
                if (day.getDailyCost() != null) {
                    weeklyCost = weeklyCost.add(day.getDailyCost());
                }
                if (day.getDailyNutritionScore() == null) continue;
                weeklyNutritionScore += day.getDailyNutritionScore().intValue();
                ++dayCount;
            }
            weeklyPlanVO.setWeeklyCost(weeklyCost);
            weeklyPlanVO.setWeeklyNutritionScore(dayCount > 0 ? weeklyNutritionScore / dayCount : 70);
            StringBuilder recommendation = new StringBuilder();
            recommendation.append("\u7b2c").append(week + 1).append("\u5468\u63a8\u8350");
            recommendation.append(dailyPlans.size()).append("\u5929\u83dc\u8c31");
            if (weeklyNutritionScore >= 80 * dayCount) {
                recommendation.append("\uff0c\u6574\u4f53\u8425\u517b\u5747\u8861");
            }
            weeklyPlanVO.setRecommendation(recommendation.toString());
            monthlyPlan.add(weeklyPlanVO);
        }
        return monthlyPlan;
    }

    private List<RecipePlanAdjustmentDetailVO.AdjustItemVO> parseAdjustItems(RecipePlanAdjustment adjustment) {
        List<RecipePlanAdjustmentDetailVO.AdjustItemVO> items = this.buildAdjustItems(adjustment.getBeforeData(), adjustment.getAfterData(), adjustment.getAdjustType());
        if (items.isEmpty()) {
            items.add(this.buildAdjustItem("\u8c03\u6574\u64cd\u4f5c", this.getAdjustTypeName(adjustment.getAdjustType()), "\u8c03\u6574\u524d\u6570\u636e", "\u8c03\u6574\u540e\u6570\u636e"));
        }
        return items;
    }

    private String resolveRecipeName(Object nameFromData, Long recipeId) {
        if (nameFromData != null && !String.valueOf(nameFromData).isEmpty()) {
            return String.valueOf(nameFromData);
        }
        if (recipeId != null) {
            try {
                Recipe recipe = (Recipe)this.recipeMapper.selectById(recipeId);
                if (recipe != null) {
                    return recipe.getRecipeName();
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        return "\u672a\u77e5\u83dc\u8c31";
    }

    private Integer normalizePositiveInteger(Integer value) {
        if (value == null) {
            return null;
        }
        return Math.max(1, value);
    }

    private Long toLong(Object val) {
        if (val == null) {
            return null;
        }
        if (val instanceof Number) {
            return ((Number)val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        }
        catch (Exception e) {
            return null;
        }
    }

    private int toInt(Object val) {
        if (val == null) {
            return 0;
        }
        if (val instanceof Number) {
            return ((Number)val).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(val));
        }
        catch (Exception e) {
            return 0;
        }
    }

   private AdjustItemVO buildAdjustItem(String fieldName, String fieldLabel, String beforeValue, String afterValue) {
      AdjustItemVO item = new AdjustItemVO();
      item.setFieldName(fieldName);
      item.setFieldLabel(fieldLabel);
      item.setBeforeValue(beforeValue);
      item.setAfterValue(afterValue);
      return item;
   }

   public RecipePlanStatisticsVO getStatistics() {
      RecipePlanStatisticsVO vo = new RecipePlanStatisticsVO();
      vo.setTotal(this.count(new LambdaQueryWrapper<RecipePlan>()));
      LambdaQueryWrapper<RecipePlan> approvedWrapper = new LambdaQueryWrapper<>();
      approvedWrapper.eq(RecipePlan::getStatus, "approved");
      vo.setApprovedCount(this.count(approvedWrapper));
      LambdaQueryWrapper<RecipePlan> pendingWrapper = new LambdaQueryWrapper<>();
      pendingWrapper.eq(RecipePlan::getStatus, "pending");
      vo.setPendingCount(this.count(pendingWrapper));

      try {
         Long sum = (Long)this.jdbcTemplate
            .queryForObject(
               "SELECT COALESCE(SUM(total_servings), 0) FROM recipe_plan WHERE deleted = 0 AND status IN ('pending', 'approved', 'completed')", Long.class
            );
         vo.setTotalServings(sum != null ? sum : 0L);
      } catch (Exception var5) {
         vo.setTotalServings(0L);
      }

      return vo;
   }

   @Transactional(
      rollbackFor = {Exception.class}
   )
    public int autoRecheckStockRiskByCadence() {
        LocalDate today = LocalDate.now();
        List<LocalDate> targetPlanDates = STOCK_RECHECK_DAYS.stream().map(today::plusDays).toList();
        List<RecipePlan> plans = this.list(
            new LambdaQueryWrapper<RecipePlan>()
                .in(RecipePlan::getStatus, STOCK_RECHECK_ACTIVE_STATUSES)
                .in(RecipePlan::getPlanDate, targetPlanDates)
                .orderByAsc(RecipePlan::getPlanDate)
                .orderByAsc(RecipePlan::getId)
        );
        int recheckedCount = 0;
        for (RecipePlan plan : plans) {
            long daysUntilPlan;
            if (plan.getPlanDate() == null || plan.getStockValidatedAt() != null && today.equals(plan.getStockValidatedAt().toLocalDate()) || !STOCK_RECHECK_DAYS.contains((int)(daysUntilPlan = ChronoUnit.DAYS.between(today, plan.getPlanDate())))) continue;
            StockValidationDTO validation = this.validateStockSafely(plan.getId(), "\u81ea\u52a8\u590d\u68c0 T-" + daysUntilPlan);
            this.applyStockRiskSnapshot(plan, validation, plan.getStatus());
            plan.setUpdatedAt(LocalDateTime.now());
            this.updateById(plan);
            ++recheckedCount;
        }
        if (recheckedCount > 0) {
            log.info("\u83dc\u8c31\u8ba1\u5212\u5e93\u5b58\u81ea\u52a8\u590d\u68c0\u5b8c\u6210\uff0c\u4eca\u65e5\u5904\u7406{}\u6761\u8ba1\u5212", (Object)recheckedCount);
        }
        return recheckedCount;
    }

    private StockValidationDTO validateStockSafely(Long planId, String scene) {
        try {
            StockValidationDTO validation = this.inventoryValidationService.validateRecipePlanStock(planId);
            if (validation == null) {
                return this.buildUnknownStockValidation("\u5e93\u5b58\u6821\u9a8c\u6682\u65e0\u8fd4\u56de\u7ed3\u679c\uff0c\u7cfb\u7edf\u5df2\u8bb0\u5f55\u63d0\u9192\uff0c\u4e0d\u5f71\u54cd\u5f53\u524d\u6d41\u7a0b\u7ee7\u7eed\u3002");
            }
            if (StrUtil.isBlank((CharSequence)validation.getRiskStatus())) {
                validation.setRiskStatus(this.resolveStockRiskStatus(validation));
            }
            if (StrUtil.isBlank((CharSequence)validation.getRiskStatusName())) {
                validation.setRiskStatusName(this.getStockRiskStatusName(validation.getRiskStatus()));
            }
            if (StrUtil.isBlank((CharSequence)validation.getMessage())) {
                validation.setMessage("\u5e93\u5b58\u6821\u9a8c\u5df2\u5b8c\u6210\uff0c\u8bf7\u4eba\u5de5\u5173\u6ce8\u5e93\u5b58\u4e0e\u6548\u671f\u60c5\u51b5\u3002");
            }
            return validation;
        }
        catch (Exception e) {
            log.warn("{}\u65f6\u5e93\u5b58\u6821\u9a8c\u6267\u884c\u5931\u8d25, planId={}: {}", new Object[]{scene, planId, e.getMessage()});
            return this.buildUnknownStockValidation("\u5e93\u5b58\u6821\u9a8c\u6267\u884c\u5931\u8d25\uff0c\u7cfb\u7edf\u5df2\u8bb0\u5f55\u63d0\u9192\uff0c\u4e0d\u5f71\u54cd\u5f53\u524d\u6d41\u7a0b\u7ee7\u7eed\uff0c\u8bf7\u4eba\u5de5\u5173\u6ce8\u5e93\u5b58\u4e0e\u6548\u671f\u60c5\u51b5\u3002");
        }
    }

   private StockValidationDTO buildUnknownStockValidation(String message) {
      return StockValidationDTO.builder()
         .passed(false)
         .message(message)
         .riskStatus("unknown")
         .riskStatusName(this.getStockRiskStatusName("unknown"))
         .shortageItems(List.of())
         .materialStockStatuses(List.of())
         .build();
   }

   private String resolveStockRiskStatus(StockValidationDTO validation) {
      if (validation == null) {
         return "unknown";
      } else if (StrUtil.isNotBlank(validation.getRiskStatus())) {
         return validation.getRiskStatus();
      } else if (validation.getShortageItems() != null && !validation.getShortageItems().isEmpty()) {
         return "shortage";
      } else {
         if (validation.getMaterialStockStatuses() != null) {
            boolean hasExpired = validation.getMaterialStockStatuses().stream().anyMatch(item -> "expired".equals(item.getExpiryStatus()));
            if (hasExpired) {
               return "expired";
            }

            boolean hasWarning = validation.getMaterialStockStatuses().stream().anyMatch(item -> "warning".equals(item.getExpiryStatus()));
            if (hasWarning) {
               return "warning";
            }
         }

         return "normal";
      }
   }

   private void applyStockRiskSnapshot(RecipePlan plan, StockValidationDTO validation, String effectiveStatus) {
      if (plan != null) {
         String riskStatus = this.resolveStockRiskStatus(validation);
         plan.setStockRiskStatus(riskStatus);
         plan.setStockRiskMessage(validation != null ? validation.getMessage() : null);
         plan.setStockValidatedAt(LocalDateTime.now());
         plan.setStockNextRecheckAt(this.resolveNextStockRecheckAt(plan.getPlanDate(), effectiveStatus));
      }
   }

   private LocalDateTime resolveNextStockRecheckAt(LocalDate planDate, String planStatus) {
      if (planDate != null && STOCK_RECHECK_ACTIVE_STATUSES.contains(planStatus)) {
         LocalDate today = LocalDate.now();
         long daysUntilPlan = ChronoUnit.DAYS.between(today, planDate);
         if (daysUntilPlan > 7L) {
            return planDate.minusDays(7L).atTime(STOCK_RECHECK_TIME);
         } else if (daysUntilPlan > 3L) {
            return planDate.minusDays(3L).atTime(STOCK_RECHECK_TIME);
         } else {
            return daysUntilPlan > 1L ? planDate.minusDays(1L).atTime(STOCK_RECHECK_TIME) : null;
         }
      } else {
         return null;
      }
   }

   private LambdaQueryWrapper<RecipeCategory> buildVisibleCategoryWrapper() {
      LambdaQueryWrapper<RecipeCategory> wrapper = new LambdaQueryWrapper<>();
      wrapper.eq(RecipeCategory::getDeleted, 0)
         .and(query -> query.in(RecipeCategory::getCategoryCode, BUILTIN_CATEGORY_CODES).or().eq(RecipeCategory::getTenantId, this.getCurrentTenantId()));
      return wrapper;
   }

    private RecipeCategory findVisibleCategoryByCode(String code) {
        if (StrUtil.isBlank((CharSequence)code)) {
            return null;
        }
        return (RecipeCategory)this.categoryMapper.selectOne((Wrapper)((LambdaQueryWrapper)this.buildVisibleCategoryWrapper().eq(RecipeCategory::getCategoryCode, (Object)code)).last("LIMIT 1"));
    }

    private RecipeCategory getVisibleCategoryById(Long id) {
        if (id == null) {
            return null;
        }
        RecipeCategory category = this.categoryMapper.selectById(id);
        if (category == null || !Objects.equals(category.getDeleted(), 0) || !BUILTIN_CATEGORY_CODES.contains(category.getCategoryCode()) && !Objects.equals(category.getTenantId(), this.getCurrentTenantId())) {
            return null;
        }
        return category;
    }

   private Long getCurrentTenantId() {
      return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
   }

   private static class PlanMealScheduleData {
      private String mealKey;
      private String mealType;
      private String mealName;
      private Integer expectedCount;
      private Integer sortOrder;
      private List<RecipePlanItemDTO> recipes;

      public String getMealKey() { return this.mealKey; }
      public void setMealKey(String mealKey) { this.mealKey = mealKey; }
      public String getMealType() { return this.mealType; }
      public void setMealType(String mealType) { this.mealType = mealType; }
      public String getMealName() { return this.mealName; }
      public void setMealName(String mealName) { this.mealName = mealName; }
      public Integer getExpectedCount() { return this.expectedCount; }
      public void setExpectedCount(Integer expectedCount) { this.expectedCount = expectedCount; }
      public Integer getSortOrder() { return this.sortOrder; }
      public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
      public List<RecipePlanItemDTO> getRecipes() { return this.recipes; }
      public void setRecipes(List<RecipePlanItemDTO> recipes) { this.recipes = recipes; }
   }

   private static class AdjustmentPlanSnapshot {
      private Long planId;
      private String planCode;
      private String planDate;
      private String startDate;
      private String endDate;
      private String mealType;
      private Integer expectedCount;
      private String targetGroup;
      private List<String> healthStatus;
      private String dietRestrictions;
      private String remark;
      private Boolean useAiRecommend;
      private List<AdjustmentMealScheduleSnapshot> mealSchedules;
      private List<AdjustmentPlanRecipeSnapshot> recipes;

      public Long getPlanId() {
         return this.planId;
      }

      public void setPlanId(Long planId) {
         this.planId = planId;
      }

      public String getPlanCode() {
         return this.planCode;
      }

      public void setPlanCode(String planCode) {
         this.planCode = planCode;
      }

      public String getPlanDate() {
         return this.planDate;
      }

      public void setPlanDate(String planDate) {
         this.planDate = planDate;
      }

      public String getStartDate() {
         return this.startDate;
      }

      public void setStartDate(String startDate) {
         this.startDate = startDate;
      }

      public String getEndDate() {
         return this.endDate;
      }

      public void setEndDate(String endDate) {
         this.endDate = endDate;
      }

      public String getMealType() {
         return this.mealType;
      }

      public void setMealType(String mealType) {
         this.mealType = mealType;
      }

      public Integer getExpectedCount() {
         return this.expectedCount;
      }

      public void setExpectedCount(Integer expectedCount) {
         this.expectedCount = expectedCount;
      }

      public String getTargetGroup() {
         return this.targetGroup;
      }

      public void setTargetGroup(String targetGroup) {
         this.targetGroup = targetGroup;
      }

      public List<String> getHealthStatus() {
         return this.healthStatus;
      }

      public void setHealthStatus(List<String> healthStatus) {
         this.healthStatus = healthStatus;
      }

      public String getDietRestrictions() {
         return this.dietRestrictions;
      }

      public void setDietRestrictions(String dietRestrictions) {
         this.dietRestrictions = dietRestrictions;
      }

      public String getRemark() {
         return this.remark;
      }

      public void setRemark(String remark) {
         this.remark = remark;
      }

      public Boolean getUseAiRecommend() {
         return this.useAiRecommend;
      }

      public void setUseAiRecommend(Boolean useAiRecommend) {
         this.useAiRecommend = useAiRecommend;
      }

      public List<AdjustmentMealScheduleSnapshot> getMealSchedules() {
         return this.mealSchedules;
      }

      public void setMealSchedules(List<AdjustmentMealScheduleSnapshot> mealSchedules) {
         this.mealSchedules = mealSchedules;
      }

      public List<AdjustmentPlanRecipeSnapshot> getRecipes() {
         return this.recipes;
      }

      public void setRecipes(List<AdjustmentPlanRecipeSnapshot> recipes) {
         this.recipes = recipes;
      }
   }

   private static class AdjustmentContext {
      private final String mode;
      private final boolean hasHistoricalTasks;
      private final boolean hasInProgressTasks;
      private final boolean hasCompletedTasks;
      private final boolean hasFuturePendingTasks;
      private final boolean hasCurrentDayPendingTasks;
      private final LocalDate firstFuturePendingTaskDate;
      private final boolean supported;
      private final String unsupportedReason;

      private AdjustmentContext(
              String mode,
              boolean hasHistoricalTasks,
              boolean hasInProgressTasks,
              boolean hasCompletedTasks,
              boolean hasFuturePendingTasks,
              boolean hasCurrentDayPendingTasks,
              LocalDate firstFuturePendingTaskDate,
              boolean supported,
              String unsupportedReason) {
         this.mode = mode;
         this.hasHistoricalTasks = hasHistoricalTasks;
         this.hasInProgressTasks = hasInProgressTasks;
         this.hasCompletedTasks = hasCompletedTasks;
         this.hasFuturePendingTasks = hasFuturePendingTasks;
         this.hasCurrentDayPendingTasks = hasCurrentDayPendingTasks;
         this.firstFuturePendingTaskDate = firstFuturePendingTaskDate;
         this.supported = supported;
         this.unsupportedReason = unsupportedReason;
      }

      public String getMode() {
         return this.mode;
      }

      public boolean hasHistoricalTasks() {
         return this.hasHistoricalTasks;
      }

      public boolean hasInProgressTasks() {
         return this.hasInProgressTasks;
      }

      public boolean hasCompletedTasks() {
         return this.hasCompletedTasks;
      }

      public boolean hasFuturePendingTasks() {
         return this.hasFuturePendingTasks;
      }

      public boolean hasCurrentDayPendingTasks() {
         return this.hasCurrentDayPendingTasks;
      }

      public LocalDate getFirstFuturePendingTaskDate() {
         return this.firstFuturePendingTaskDate;
      }

      public boolean isSupported() {
         return this.supported;
      }

      public String getUnsupportedReason() {
         return this.unsupportedReason;
      }
   }

   private static class AdjustmentMealScheduleSnapshot {
      private String mealKey;
      private String mealType;
      private String mealName;
      private Integer expectedCount;
      private Integer sortOrder;
      private List<AdjustmentPlanRecipeSnapshot> recipes;

      public String getMealKey() { return this.mealKey; }
      public void setMealKey(String mealKey) { this.mealKey = mealKey; }
      public String getMealType() { return this.mealType; }
      public void setMealType(String mealType) { this.mealType = mealType; }
      public String getMealName() { return this.mealName; }
      public void setMealName(String mealName) { this.mealName = mealName; }
      public Integer getExpectedCount() { return this.expectedCount; }
      public void setExpectedCount(Integer expectedCount) { this.expectedCount = expectedCount; }
      public Integer getSortOrder() { return this.sortOrder; }
      public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
      public List<AdjustmentPlanRecipeSnapshot> getRecipes() { return this.recipes; }
      public void setRecipes(List<AdjustmentPlanRecipeSnapshot> recipes) { this.recipes = recipes; }
   }

   private static class AdjustmentPlanRecipeSnapshot {
      private Long recipeId;
      private String recipeName;
      private String categoryName;
      private String mealKey;
      private String mealType;
      private String mealName;
      private Integer mealExpectedCount;
      private Integer mealSortOrder;
      private Integer plannedServings;
      private String remark;
      private Integer sortOrder;

      public Long getRecipeId() {
         return this.recipeId;
      }

      public void setRecipeId(Long recipeId) {
         this.recipeId = recipeId;
      }

      public String getRecipeName() {
         return this.recipeName;
      }

      public void setRecipeName(String recipeName) {
         this.recipeName = recipeName;
      }

      public String getCategoryName() {
         return this.categoryName;
      }

      public void setCategoryName(String categoryName) {
         this.categoryName = categoryName;
      }

      public String getMealKey() {
         return this.mealKey;
      }

      public void setMealKey(String mealKey) {
         this.mealKey = mealKey;
      }

      public String getMealType() {
         return this.mealType;
      }

      public void setMealType(String mealType) {
         this.mealType = mealType;
      }

      public String getMealName() {
         return this.mealName;
      }

      public void setMealName(String mealName) {
         this.mealName = mealName;
      }

      public Integer getMealExpectedCount() {
         return this.mealExpectedCount;
      }

      public void setMealExpectedCount(Integer mealExpectedCount) {
         this.mealExpectedCount = mealExpectedCount;
      }

      public Integer getMealSortOrder() {
         return this.mealSortOrder;
      }

      public void setMealSortOrder(Integer mealSortOrder) {
         this.mealSortOrder = mealSortOrder;
      }

      public Integer getPlannedServings() {
         return this.plannedServings;
      }

      public void setPlannedServings(Integer plannedServings) {
         this.plannedServings = plannedServings;
      }

      public String getRemark() {
         return this.remark;
      }

      public void setRemark(String remark) {
         this.remark = remark;
      }

      public Integer getSortOrder() {
         return this.sortOrder;
      }

      public void setSortOrder(Integer sortOrder) {
         this.sortOrder = sortOrder;
      }
   }

   private static class NutritionGap {
      private boolean proteinNeed;
      private boolean fiberNeed;
      private boolean vitaminNeed;
      private boolean calciumNeed;

      public boolean isProteinNeed() {
         return this.proteinNeed;
      }

      public void setProteinNeed(boolean proteinNeed) {
         this.proteinNeed = proteinNeed;
      }

      public boolean isFiberNeed() {
         return this.fiberNeed;
      }

      public void setFiberNeed(boolean fiberNeed) {
         this.fiberNeed = fiberNeed;
      }

      public boolean isVitaminNeed() {
         return this.vitaminNeed;
      }

      public void setVitaminNeed(boolean vitaminNeed) {
         this.vitaminNeed = vitaminNeed;
      }

      public boolean isCalciumNeed() {
         return this.calciumNeed;
      }

      public void setCalciumNeed(boolean calciumNeed) {
         this.calciumNeed = calciumNeed;
      }
   }

   @Generated
   public RecipePlanServiceImpl(
      final RecipePlanItemMapper planItemMapper,
      final RecipeMapper recipeMapper,
      final RecipeCategoryMapper categoryMapper,
      final RecipeIngredientMapper ingredientMapper,
      final RecipePlanAdjustmentMapper adjustmentMapper,
      final RecipePlanAuditLogMapper auditLogMapper,
      final InventoryValidationService inventoryValidationService,
      final CookTaskService cookTaskService,
      final DataScopeService dataScopeService,
      final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService,
      final ObjectMapper objectMapper,
      final NutritionTargetConfig nutritionTargetConfig,
      final JdbcTemplate jdbcTemplate,
      final TransactionTemplate transactionTemplate,
      final RecipeNutritionSupportService recipeNutritionSupportService,
      final AiServiceConfigService aiServiceConfigService,
      final OpenAiCompatibleService openAiCompatibleService
   ) {
      this.planItemMapper = planItemMapper;
      this.recipeMapper = recipeMapper;
      this.categoryMapper = categoryMapper;
      this.ingredientMapper = ingredientMapper;
      this.adjustmentMapper = adjustmentMapper;
      this.auditLogMapper = auditLogMapper;
      this.inventoryValidationService = inventoryValidationService;
      this.cookTaskService = cookTaskService;
      this.dataScopeService = dataScopeService;
      this.materialCategoryCoefficientLockService = materialCategoryCoefficientLockService;
      this.objectMapper = objectMapper;
      this.nutritionTargetConfig = nutritionTargetConfig;
      this.jdbcTemplate = jdbcTemplate;
      this.transactionTemplate = transactionTemplate;
      this.recipeNutritionSupportService = recipeNutritionSupportService;
      this.aiServiceConfigService = aiServiceConfigService;
      this.openAiCompatibleService = openAiCompatibleService;
   }
}
