package com.xykj.sys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.sys.dto.ReviewCreateDTO;
import com.xykj.sys.dto.ReviewQueryDTO;
import com.xykj.sys.dto.ReviewReplyDTO;
import com.xykj.sys.entity.Employee;
import com.xykj.sys.entity.MealReview;
import com.xykj.sys.entity.Organization;
import com.xykj.sys.mapper.EmployeeMapper;
import com.xykj.sys.mapper.MealReviewMapper;
import com.xykj.sys.mapper.OrganizationMapper;
import com.xykj.sys.service.ReviewService;
import com.xykj.sys.service.SensitiveWordService;
import com.xykj.sys.vo.ReviewVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 评价服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final MealReviewMapper mealReviewMapper;
    private final EmployeeMapper employeeMapper;
    private final OrganizationMapper organizationMapper;
    private final AuditLogService auditLogService;
    private final SensitiveWordService sensitiveWordService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @DataScope
    public PageResult<ReviewVO> list(ReviewQueryDTO query) {
        // 构建查询条件
        LambdaQueryWrapper<MealReview> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(query.getSource()), MealReview::getSource, query.getSource())
                .eq(query.getOrgId() != null, MealReview::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), MealReview::getOrgId, query.getOrgIds())
                .eq(query.getOverallScore() != null, MealReview::getOverallScore, query.getOverallScore())
                .orderByDesc(MealReview::getCreatedAt);

        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(MealReview::getId);
        }

        // 关键词筛选（评价人姓名或菜品名称模糊匹配）
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(i -> i.like(MealReview::getEmployeeName, query.getKeyword())
                    .or().like(MealReview::getMenuName, query.getKeyword()));
        }

        // 时间范围筛选（按创建时间）
        if (StrUtil.isNotBlank(query.getStartTime())) {
            wrapper.ge(MealReview::getCreatedAt, query.getStartTime() + " 00:00:00");
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(MealReview::getCreatedAt, query.getEndTime() + " 23:59:59");
        }

        // 分页查询
        IPage<MealReview> page = mealReviewMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 转换为VO
        List<ReviewVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充组织名称
        fillOrgNames(voList);

        return PageResult.of(page, voList);
    }

    @Override
    public ReviewVO getDetail(Long id) {
        MealReview review = mealReviewMapper.selectById(id);
        if (review == null) {
            throw BizException.notFound("评价不存在");
        }
        ReviewVO vo = convertToVO(review);

        // 填充组织名称
        if (review.getOrgId() != null) {
            Organization org = organizationMapper.selectById(review.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getOrgName());
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(ReviewCreateDTO dto) {
        // 敏感词校验
        if (StrUtil.isNotBlank(dto.getContent())) {
            if (sensitiveWordService.containsSensitiveWord(dto.getContent())) {
                auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.CREATE, null, null,
                    "新增评价失败：内容包含敏感信息", null,
                    JSONUtil.toJsonStr(Map.of("content", dto.getContent())),
                    "failure", "内容包含敏感信息");
                throw BizException.validationFailed("内容包含敏感信息，提交失败，请修改后重试");
            }
        }

        // 查询评价人信息
        Employee employee = employeeMapper.selectById(dto.getEmployeeId());
        if (employee == null) {
            throw BizException.notFound("评价人不存在");
        }

        // 创建评价实体
        MealReview review = new MealReview();
        BeanUtil.copyProperties(dto, review);
        review.setTenantId(UserContext.getTenantId());
        review.setReviewNo(mealReviewMapper.generateReviewNo());
        review.setEmployeeName(employee.getRealName());
        review.setImages(dto.getImages() != null ? JSONUtil.toJsonStr(dto.getImages()) : null);
        review.setTags(dto.getTags() != null ? JSONUtil.toJsonStr(dto.getTags()) : null);

        // 计算积分（根据评分）
        int points = calculatePoints(dto.getOverallScore());
        review.setPoints(points);

        mealReviewMapper.insert(review);

        log.info("创建评价成功: id={}, reviewNo={}", review.getId(), review.getReviewNo());

        auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.CREATE, review.getId(), review.getReviewNo(),
            "新增评价：" + review.getReviewNo() + "，员工：" + employee.getRealName() + "，评分：" + review.getOverallScore(),
            null, JSONUtil.toJsonStr(review));

        Map<String, Object> result = new HashMap<>();
        result.put("id", review.getId());
        result.put("reviewNo", review.getReviewNo());
        result.put("points", points);
        return result;
    }

    // ==================== 私有方法 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> reply(Long id, ReviewReplyDTO dto) {
        MealReview review = mealReviewMapper.selectById(id);
        if (review == null) {
            throw BizException.notFound("评价不存在");
        }
        if (review.getReplyContent() != null) {
            throw BizException.conflict("该评价已回复");
        }

        // 敏感词校验
        if (sensitiveWordService.containsSensitiveWord(dto.getReplyContent())) {
            auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.REPLY, review.getId(), review.getReviewNo(),
                "回复评价失败：内容包含敏感信息", null,
                JSONUtil.toJsonStr(Map.of("replyContent", dto.getReplyContent())),
                "failure", "内容包含敏感信息");
            throw BizException.validationFailed("内容包含敏感信息，提交失败，请修改后重试");
        }

        review.setReplyContent(dto.getReplyContent());
        review.setReplyBy(UserContext.getUserId());
        review.setReplyByName(UserContext.getRealName());
        review.setReplyAt(LocalDateTime.now());
        mealReviewMapper.updateById(review);

        log.info("回复评价成功: id={}, replyBy={}", id, UserContext.getUserId());

        auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.REPLY, review.getId(), review.getReviewNo(),
                "回复评价：" + review.getReviewNo(),
                null, JSONUtil.toJsonStr(Map.of("replyContent", dto.getReplyContent())));

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("replyByName", review.getReplyByName());
        result.put("replyAt", review.getReplyAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    @Override
    @DataScope
    public void exportReviews(ReviewQueryDTO query, HttpServletResponse response) {
        try {
            // 复用 list() 的查询条件构建
            LambdaQueryWrapper<MealReview> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StrUtil.isNotBlank(query.getSource()), MealReview::getSource, query.getSource())
                    .eq(query.getOrgId() != null, MealReview::getOrgId, query.getOrgId())
                    .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), MealReview::getOrgId, query.getOrgIds())
                    .eq(query.getOverallScore() != null, MealReview::getOverallScore, query.getOverallScore())
                    .orderByDesc(MealReview::getCreatedAt);

            if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
                wrapper.isNull(MealReview::getId);
            }

            if (StrUtil.isNotBlank(query.getKeyword())) {
                wrapper.and(i -> i.like(MealReview::getEmployeeName, query.getKeyword())
                        .or().like(MealReview::getMenuName, query.getKeyword()));
            }

            if (StrUtil.isNotBlank(query.getStartTime())) {
                wrapper.ge(MealReview::getCreatedAt, query.getStartTime() + " 00:00:00");
            }
            if (StrUtil.isNotBlank(query.getEndTime())) {
                wrapper.le(MealReview::getCreatedAt, query.getEndTime() + " 23:59:59");
            }

            List<MealReview> reviews = mealReviewMapper.selectList(wrapper);

            // 构建组织名称映射
            Map<Long, String> orgNameMap = buildOrgNameMap(reviews);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = URLEncoder.encode("评价数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            String[] headers = {"序号", "评价编号", "来源", "菜品名称", "评价人", "门店", "餐次", "综合评分", "口味评分", "营养评分", "份量评分", "评价内容", "评价标签", "积分", "评价时间", "回复内容", "回复人", "回复时间"};
            int[] colWidths = {6, 18, 10, 15, 10, 15, 8, 8, 8, 8, 8, 30, 20, 6, 20, 30, 10, 20};

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("评价数据");

            // 表头样式
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);

            int rowNum = 0;

            // 表头
            Row headerRow = sheet.createRow(rowNum++);
            headerRow.setHeightInPoints(30);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, colWidths[i] * 256);
            }

            // 数据
            DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            int index = 1;
            for (MealReview r : reviews) {
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        String.valueOf(index++),
                        r.getReviewNo() != null ? r.getReviewNo() : "",
                        getSourceName(r.getSource()),
                        r.getMenuName() != null ? r.getMenuName() : "",
                        r.getEmployeeName() != null ? r.getEmployeeName() : "",
                        orgNameMap.getOrDefault(r.getOrgId(), ""),
                        getMealTypeName(r.getMealType()),
                        r.getOverallScore() != null ? String.valueOf(r.getOverallScore()) : "",
                        r.getTasteScore() != null ? String.valueOf(r.getTasteScore()) : "",
                        r.getNutritionScore() != null ? String.valueOf(r.getNutritionScore()) : "",
                        r.getPortionScore() != null ? String.valueOf(r.getPortionScore()) : "",
                        r.getContent() != null ? r.getContent() : "",
                        r.getTags() != null ? r.getTags() : "",
                        r.getPoints() != null ? String.valueOf(r.getPoints()) : "",
                        r.getCreatedAt() != null ? r.getCreatedAt().format(dtFormatter) : "",
                        r.getReplyContent() != null ? r.getReplyContent() : "",
                        r.getReplyByName() != null ? r.getReplyByName() : "",
                        r.getReplyAt() != null ? r.getReplyAt().format(dtFormatter) : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();

            auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.EXPORT, null, null,
                    "导出评价数据：" + reviews.size() + "条",
                    null, JSONUtil.toJsonStr(Map.of("count", reviews.size())), "success", null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出评价失败", e);
            try {
                auditLogService.log(AuditModule.SYS_REVIEW, AuditOperationType.EXPORT, null, null,
                        "导出评价数据失败", null, null, "failure", e.getMessage());
            } catch (Exception ignored) {
            }
            throw new BizException("导出失败");
        }
    }

    private Map<Long, String> buildOrgNameMap(List<MealReview> reviews) {
        List<Long> orgIds = reviews.stream()
                .map(MealReview::getOrgId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());
        if (orgIds.isEmpty()) return new HashMap<>();
        List<Organization> orgs = organizationMapper.selectBatchIds(orgIds);
        return orgs.stream().collect(Collectors.toMap(Organization::getId, Organization::getOrgName));
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("微软雅黑");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private boolean hasPermission(String permissionCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) return false;
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? AND r.deleted = 0 AND r.status = 'active' AND p.status = 'active' AND p.permission_code = ?",
                Long.class, userId, permissionCode);
        return count != null && count > 0L;
    }

    /**
     * 转换为VO
     */
    private ReviewVO convertToVO(MealReview review) {
        ReviewVO vo = new ReviewVO();
        BeanUtil.copyProperties(review, vo);
        vo.setSourceName(getSourceName(review.getSource()));
        vo.setMealTypeName(getMealTypeName(review.getMealType()));
        vo.setImages(review.getImages() != null ? JSONUtil.toList(review.getImages(), String.class) : null);
        vo.setTags(review.getTags() != null ? JSONUtil.toList(review.getTags(), String.class) : null);
        return vo;
    }

    /**
     * 填充组织名称
     */
    private void fillOrgNames(List<ReviewVO> voList) {
        List<Long> orgIds = voList.stream()
                .map(ReviewVO::getOrgId)
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

        if (orgIds.isEmpty()) {
            return;
        }

        List<Organization> orgs = organizationMapper.selectBatchIds(orgIds);
        Map<Long, String> orgNameMap = orgs.stream()
                .collect(Collectors.toMap(Organization::getId, Organization::getOrgName));

        voList.forEach(vo -> {
            if (vo.getOrgId() != null) {
                vo.setOrgName(orgNameMap.get(vo.getOrgId()));
            }
        });
    }

    /**
     * 计算积分
     */
    private int calculatePoints(Integer overallScore) {
        if (overallScore == null) return 0;
        // 5星=10积分，4星=5积分，3星=2积分，其他=0积分
        switch (overallScore) {
            case 5:
                return 10;
            case 4:
                return 5;
            case 3:
                return 2;
            default:
                return 0;
        }
    }

    /**
     * 获取来源名称
     */
    private String getSourceName(String source) {
        if (source == null) return "";
        switch (source) {
            case "meal":
                return "用餐评价";
            case "supervision":
                return "监管反馈";
            case "manual":
                return "人工录入";
            default:
                return source;
        }
    }

    /**
     * 获取餐次名称
     */
    private String getMealTypeName(String mealType) {
        if (mealType == null) return "";
        switch (mealType) {
            case "breakfast":
                return "早餐";
            case "lunch":
                return "午餐";
            case "dinner":
                return "晚餐";
            default:
                return mealType;
        }
    }
}
