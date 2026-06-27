package com.xykj.scm.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.scm.dto.PurchasePlanAuditDTO;
import com.xykj.scm.dto.PurchasePlanAttachmentDTO;
import com.xykj.scm.dto.PurchasePlanCreateDTO;
import com.xykj.scm.dto.PurchasePlanGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanItemDTO;
import com.xykj.scm.dto.PurchasePlanMergeGenerateOrderDTO;
import com.xykj.scm.dto.PurchasePlanQueryDTO;
import com.xykj.scm.dto.PurchasePlanReverseAuditDTO;
import com.xykj.scm.dto.PurchasePlanUpdateDTO;
import com.xykj.scm.dto.PurchasePlanVoidApplyDTO;
import com.xykj.scm.dto.PurchasePlanVoidAuditDTO;
import com.xykj.scm.entity.PurchaseOrder;
import com.xykj.scm.entity.PurchaseOrderItem;
import com.xykj.scm.entity.PurchasePlan;
import com.xykj.scm.entity.PurchasePlanAttachment;
import com.xykj.scm.entity.PurchasePlanItem;
import com.xykj.scm.mapper.PurchaseOrderItemMapper;
import com.xykj.scm.mapper.PurchaseOrderMapper;
import com.xykj.scm.mapper.PurchasePlanAttachmentMapper;
import com.xykj.scm.mapper.PurchasePlanItemMapper;
import com.xykj.scm.mapper.PurchasePlanMapper;
import com.xykj.scm.service.PurchasePlanService;
import com.xykj.scm.support.PurchaseDemandForecastLinkageSupport;
import com.xykj.scm.support.RecipePlanLinkageSupport;
import com.xykj.scm.vo.PurchaseOrderGenerateResultVO;
import com.xykj.scm.vo.PurchaseOrderLinkItemVO;
import com.xykj.scm.vo.PurchaseOrderLinkVO;
import com.xykj.scm.vo.PurchasePlanAttachmentVO;
import com.xykj.scm.vo.PurchasePlanItemVO;
import com.xykj.scm.vo.PurchasePlanLinkedOrderRecordVO;
import com.xykj.scm.vo.PurchasePlanMaterialOptionVO;
import com.xykj.scm.vo.PurchasePlanRecipeMaterialLinkageVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentItemPrefillVO;
import com.xykj.scm.vo.PurchasePlanRelatedDocumentOptionVO;
import com.xykj.scm.vo.PurchasePlanReverseAuditResultVO;
import com.xykj.scm.vo.PurchasePlanStatisticsVO;
import com.xykj.scm.vo.PurchasePlanVO;
import com.xykj.scm.vo.SelectablePurchasePlanVO;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 采购计划服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasePlanServiceImpl implements PurchasePlanService {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_USER_ID = 1L;
    private static final String ATTACHMENT_DIR = "scm/purchase-plans";
    private static final String PURCHASE_ORDER_ATTACHMENT_DIR = "scm/purchase-orders";
    private static final long MAX_ATTACHMENT_FILE_SIZE = 10L * 1024 * 1024;
    private static final String SUPPLIER_STATUS_ACTIVE = "active";
    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_PENDING_VOID_APPROVE = "pending_void_approve";
    private static final String STATUS_VOIDED = "voided";
    private static final String STATUS_PENDING_SUBMIT = "pending_submit";
    private static final String PURCHASE_ORDER_STATUS_PENDING_APPROVE = "pending_approve";
    private static final String PURCHASE_ORDER_STATUS_APPROVED = "approved";
    private static final String PURCHASE_ORDER_STATUS_PENDING_VOID_APPROVE = "pending_void_approve";
    private static final String PURCHASE_ORDER_STATUS_VOIDED = "voided";
    private static final String PURCHASE_ORDER_STATUS_DELIVERING = "delivering";
    private static final String PURCHASE_ORDER_STATUS_PENDING_RECEIPT = "pending_receipt";
    private static final String PURCHASE_ORDER_STATUS_COMPLETED = "completed";
    private static final String SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE = "所选供应商关键资质已过期，禁止新增采购业务关联";
    private static final String PURCHASE_PLAN_DELETE_PERMISSION = "purchasePlan:delete";
    private static final String PURCHASE_PLAN_APPROVE_PERMISSION = "purchasePlan:approve";
    private static final String PURCHASE_PLAN_GENERATE_ORDER_PERMISSION = "purchasePlan:generateOrder";
    private static final String REVERSE_AUDIT_PERMISSION_MESSAGE = "无权限执行反审核";
    private static final String REVERSE_AUDIT_STATUS_BLOCK_MESSAGE = "当前状态不允许执行反审核";
    private static final String REVERSE_AUDIT_LINKED_ORDER_STATUS_BLOCK_MESSAGE = "关联采购订单当前状态不允许执行反审核";
    private static final String REVERSE_AUDIT_BLOCK_MESSAGE = "该采购计划已存在正式下游业务，不允许反审核。";
    private static final String REVERSE_AUDIT_CONCURRENT_BLOCK_MESSAGE = "采购计划状态已发生变化，请刷新页面后重试";
    private static final String DELETE_BLOCK_MESSAGE = "该采购计划已存在下游采购订单或入库业务，不允许删除";
    private static final String REJECTED_DELETE_BLOCK_MESSAGE = "已驳回采购计划属于食安溯源台账，不允许删除";
    private static final String CONCURRENT_DELETE_BLOCK_MESSAGE = "当前采购计划状态或下游业务已发生变化，暂不允许删除，请刷新后重试或核查关联单据";
    private static final String VOID_APPLY_BLOCK_MESSAGE = "该采购计划已存在下游采购订单或入库业务，不允许发起作废";
    private static final String CONCURRENT_VOID_APPLY_BLOCK_MESSAGE = "当前采购计划状态或下游业务已发生变化，暂不允许发起作废，请刷新后重试或核查关联单据";
    private static final String MERGE_GENERATE_CONCURRENT_BLOCK_MESSAGE = "当前存在不满足合并生成条件的采购计划，请刷新后重试或核查关联计划状态";
    private static final String MERGE_GENERATE_SINGLE_SELECT_MESSAGE = "请至少选择2条采购计划进行合并生成，单条请使用原有关联生成采购订单";
    private static final Long MERGE_PLACEHOLDER_SUPPLIER_ID = 0L;
    private static final String MERGE_PLACEHOLDER_SUPPLIER_NAME = "待补充供应商";
    private static final String FORECAST_LINKAGE_QUANTITY_BLOCK_MESSAGE = "关联数量不能超过当前可关联数量";
    private static final String RELATED_DOCUMENT_TYPE_RECIPE_PLAN = "recipePlan";
    private static final String RELATED_DOCUMENT_TYPE_PURCHASE_DEMAND_FORECAST = "purchaseDemandForecast";
    private static final String RELATED_DOCUMENT_TYPE_RECIPE_PLAN_LABEL = "菜谱计划单";
    private static final String RELATED_DOCUMENT_TYPE_PURCHASE_DEMAND_FORECAST_LABEL = "采购需求预测单";
    private static final int LINKED_PURCHASE_ORDER_ORDER_LIMIT = 100;
    private static final int LINKED_PURCHASE_ORDER_ROW_LIMIT = 500;
    private static final String PLAN_STATUS_COMMENT = "状态：draft=草稿，pending=待审核，approved=已审核，rejected=已驳回，pending_void_approve=待作废审核，voided=已作废";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PurchasePlanMapper purchasePlanMapper;
    private final PurchasePlanAttachmentMapper purchasePlanAttachmentMapper;
    private final PurchasePlanItemMapper purchasePlanItemMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;
    private final AuditLogService auditLogService;
    private final FileStorageService fileStorageService;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
    private final ObjectMapper objectMapper;
    private final PurchaseDemandForecastLinkageSupport purchaseDemandForecastLinkageSupport;
    private final RecipePlanLinkageSupport recipePlanLinkageSupport;

    @PostConstruct
    public void ensurePurchasePlanVoidColumns() {
        try {
            ensurePurchasePlanVoidColumn(
                    "void_origin_status",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_origin_status VARCHAR(20) DEFAULT NULL COMMENT '发起作废前原始状态：approved=已审核，rejected=已驳回' AFTER approve_remark"
            );
            ensurePurchasePlanVoidColumn(
                    "void_reason",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_reason VARCHAR(500) DEFAULT NULL COMMENT '作废原因' AFTER void_origin_status"
            );
            ensurePurchasePlanVoidColumn(
                    "void_requested_by",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_requested_by BIGINT DEFAULT NULL COMMENT '作废申请人ID' AFTER void_reason"
            );
            ensurePurchasePlanVoidColumn(
                    "void_requested_at",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_requested_at DATETIME DEFAULT NULL COMMENT '作废申请时间' AFTER void_requested_by"
            );
            ensurePurchasePlanVoidColumn(
                    "void_audit_by",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_audit_by BIGINT DEFAULT NULL COMMENT '作废审核人ID' AFTER void_requested_at"
            );
            ensurePurchasePlanVoidColumn(
                    "void_audit_at",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_audit_at DATETIME DEFAULT NULL COMMENT '作废审核时间' AFTER void_audit_by"
            );
            ensurePurchasePlanVoidColumn(
                    "void_audit_remark",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN void_audit_remark VARCHAR(500) DEFAULT NULL COMMENT '作废审核意见' AFTER void_audit_at"
            );
            ensurePurchasePlanVoidColumn(
                    "merge_locked",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN merge_locked TINYINT NOT NULL DEFAULT 0 COMMENT '是否被多选合并生成采购订单占用：0=否，1=是' AFTER void_audit_remark"
            );
            ensurePurchasePlanVoidColumn(
                    "merge_order_id",
                    "ALTER TABLE scm_purchase_plan ADD COLUMN merge_order_id BIGINT DEFAULT NULL COMMENT '多选合并生成关联采购订单ID' AFTER merge_locked"
            );
            Integer mergeOrderIndexCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scm_purchase_plan' AND INDEX_NAME = 'idx_plan_merge_order'",
                    Integer.class
            );
            if (mergeOrderIndexCount != null && mergeOrderIndexCount == 0) {
                jdbcTemplate.execute("ALTER TABLE scm_purchase_plan ADD INDEX idx_plan_merge_order (merge_order_id)");
            }
            jdbcTemplate.execute(
                    "ALTER TABLE scm_purchase_plan " +
                            "MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'draft' COMMENT '" + PLAN_STATUS_COMMENT + "'"
            );
            log.info("采购计划作废流程字段检查完成");
        } catch (Exception e) {
            log.warn("采购计划作废流程字段迁移异常: {}", e.getMessage());
        }
    }

    @Override
    @DataScope
    public PageResult<PurchasePlanVO> list(PurchasePlanQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String normalizedKeyword = normalizeOptionalText(query.getKeyword());
        String normalizedPlanName = normalizeOptionalText(query.getPlanName());
        String normalizedPlanNo = normalizeOptionalText(query.getPlanNo());
        String normalizedStatus = normalizeOptionalText(query.getStatus());
        StringBuilder whereSql = new StringBuilder(" WHERE p.deleted = ?");
        List<Object> args = new ArrayList<>();
        args.add(0);
        if (query.getOrgId() != null) {
            whereSql.append(" AND p.org_id = ?");
            args.add(query.getOrgId());
        } else if (query.getOrgIds() != null) {
            if (query.getOrgIds().isEmpty()) {
                return PageResult.empty((long) pageNum, (long) pageSize);
            }
            whereSql.append(" AND p.org_id IN (").append(placeholders(query.getOrgIds().size())).append(")");
            args.addAll(query.getOrgIds());
        }
        if (StrUtil.isNotBlank(normalizedKeyword)) {
            whereSql.append(" AND (p.plan_name LIKE ? OR p.plan_no LIKE ?)");
            String likeKeyword = like(normalizedKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        if (StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedPlanName)) {
            whereSql.append(" AND p.plan_name LIKE ?");
            args.add(like(normalizedPlanName));
        }
        if (StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedPlanNo)) {
            whereSql.append(" AND p.plan_no LIKE ?");
            args.add(like(normalizedPlanNo));
        }
        if (StrUtil.isNotBlank(normalizedStatus)) {
            whereSql.append(" AND p.status = ?");
            args.add(normalizedStatus);
        }

        String countSql = "SELECT COUNT(*) FROM scm_purchase_plan p" + whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        if (total == null || total == 0L) {
            return PageResult.empty((long) pageNum, (long) pageSize);
        }

        String listSql = "SELECT p.id, p.plan_no AS planNo, p.plan_name AS planName, p.org_id AS orgId, " +
                "o.org_name AS orgName, p.plan_date AS planDate, p.budget_amount AS budgetAmount, " +
                "p.total_amount AS totalAmount, p.related_document AS relatedDocument, " +
                "p.attachment_name AS attachmentName, p.attachment_url AS attachmentUrl, p.remark, p.status, " +
                "p.deleted AS deleted, " +
                "p.approve_remark AS auditRemark, p.approved_by AS approvedById, p.approved_at AS approvedAt, " +
                "p.void_origin_status AS voidOriginalStatus, p.void_reason AS voidReason, " +
                "p.void_requested_by AS voidRequestedById, p.void_requested_at AS voidRequestedAt, " +
                "p.void_audit_by AS voidAuditById, p.void_audit_at AS voidAuditAt, p.void_audit_remark AS voidAuditRemark, " +
                "COALESCE(p.merge_locked, 0) AS mergeLocked, p.merge_order_id AS mergeOrderId, " +
                "p.created_by AS createdById, p.created_at AS createdAt " +
                "FROM scm_purchase_plan p " +
                "LEFT JOIN sys_organization o ON o.id = p.org_id AND o.deleted = 0" +
                whereSql +
                " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?";

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((pageNum - 1L) * pageSize);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(listSql, listArgs.toArray());
        List<Long> planIds = new ArrayList<>();
        Map<Long, Long> createdByMap = new HashMap<>();
        Map<Long, Long> approvedByMap = new HashMap<>();
        Map<Long, Long> voidRequestedByMap = new HashMap<>();
        Map<Long, Long> voidAuditByMap = new HashMap<>();
        List<PurchasePlanVO> list = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            PurchasePlanVO vo = new PurchasePlanVO();
            Long planId = toLong(row.get("id"));
            vo.setId(planId);
            vo.setPlanNo(asString(row.get("planNo")));
            vo.setPlanName(asString(row.get("planName")));
            vo.setOrgId(toLong(row.get("orgId")));
            vo.setOrgName(asString(row.get("orgName")));
            vo.setPlanDate(formatDate(row.get("planDate")));
            vo.setCreatedAt(formatDateTime(row.get("createdAt")));
            vo.setBudgetAmount(zeroIfNull(toBigDecimal(row.get("budgetAmount"))));
            vo.setTotalAmount(zeroIfNull(toBigDecimal(row.get("totalAmount"))));
            vo.setRelatedDocument(asString(row.get("relatedDocument")));
            vo.setAttachmentName(asString(row.get("attachmentName")));
            vo.setAttachmentUrl(asString(row.get("attachmentUrl")));
            vo.setRemark(asString(row.get("remark")));
            vo.setStatus(asString(row.get("status")));
            vo.setDeleted(toInteger(row.get("deleted")) != 0);
            vo.setAuditRemark(asString(row.get("auditRemark")));
            vo.setAuditAt(formatDateTime(row.get("approvedAt")));
            vo.setVoidOriginalStatus(asString(row.get("voidOriginalStatus")));
            vo.setVoidReason(asString(row.get("voidReason")));
            vo.setVoidRequestedAt(formatDateTime(row.get("voidRequestedAt")));
            vo.setVoidAuditAt(formatDateTime(row.get("voidAuditAt")));
            vo.setVoidAuditRemark(asString(row.get("voidAuditRemark")));
            vo.setMergeLocked(toInteger(row.get("mergeLocked")) != 0);
            vo.setMergeOrderId(toLong(row.get("mergeOrderId")));
            list.add(vo);
            planIds.add(planId);
            createdByMap.put(planId, toLong(row.get("createdById")));
            approvedByMap.put(planId, toLong(row.get("approvedById")));
            voidRequestedByMap.put(planId, toLong(row.get("voidRequestedById")));
            voidAuditByMap.put(planId, toLong(row.get("voidAuditById")));
        }

        Map<Long, String> operatorCache = new HashMap<>();
        Map<Long, List<PurchasePlanItemVO>> itemMap = loadPlanItems(planIds);
        Map<Long, Integer> orderCountMap = loadGeneratedOrderCountMap(planIds);
        for (PurchasePlanVO vo : list) {
            vo.setCreatedBy(resolveOperatorName(createdByMap.get(vo.getId()), operatorCache));
            vo.setAuditBy(resolveOperatorName(approvedByMap.get(vo.getId()), operatorCache));
            vo.setVoidRequestedBy(resolveOperatorName(voidRequestedByMap.get(vo.getId()), operatorCache));
            vo.setVoidAuditBy(resolveOperatorName(voidAuditByMap.get(vo.getId()), operatorCache));
            List<PurchasePlanItemVO> items = itemMap.getOrDefault(vo.getId(), Collections.emptyList());
            vo.setItems(new ArrayList<>(items));
            vo.setGeneratedOrderCount(orderCountMap.getOrDefault(vo.getId(), 0));
            vo.setAllItemsGenerated(isAllItemsGenerated(items));
        }

        return PageResult.of(list, (long) pageNum, (long) pageSize, total);
    }

    @Override
    public PurchasePlanStatisticsVO getStatistics(Long orgId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        PurchasePlanStatisticsVO vo = new PurchasePlanStatisticsVO();
        if (allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            vo.setTotal(0L);
            vo.setPending(0L);
            vo.setApproved(0L);
            vo.setTotalBudget(BigDecimal.ZERO);
            return vo;
        }

        Long currentOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && currentOrgId == null) {
            vo.setTotal(0L);
            vo.setPending(0L);
            vo.setApproved(0L);
            vo.setTotalBudget(BigDecimal.ZERO);
            return vo;
        }

        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total, " +
                "SUM(CASE WHEN status = 'pending' THEN 1 ELSE 0 END) AS pending, " +
                "SUM(CASE WHEN status = 'approved' THEN 1 ELSE 0 END) AS approved, " +
                "COALESCE(SUM(COALESCE(budget_amount, 0)), 0) AS totalBudget " +
                "FROM scm_purchase_plan WHERE deleted = 0");
        List<Object> args = new ArrayList<>();
        if (currentOrgId != null) {
            sql.append(" AND org_id = ?");
            args.add(currentOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        vo.setTotal(toLong(row.get("total")));
        vo.setPending(toLong(row.get("pending")));
        vo.setApproved(toLong(row.get("approved")));
        vo.setTotalBudget(zeroIfNull(toBigDecimal(row.get("totalBudget"))));
        return vo;
    }

    @Override
    public PurchasePlanVO getDetail(Long id) {
        PurchasePlan plan = getPlanById(id, true, true);
        if (isDeleted(plan) && !dataScopeService.isAdminUser()) {
            throw BizException.notFound("采购计划不存在");
        }
        PurchasePlanVO vo = toVO(plan);
        Map<Long, String> operatorCache = new HashMap<>();
        vo.setCreatedBy(resolveOperatorName(plan.getCreatedBy(), operatorCache));
        vo.setAuditBy(resolveOperatorName(plan.getApprovedBy(), operatorCache));
        vo.setVoidRequestedBy(resolveOperatorName(plan.getVoidRequestedBy(), operatorCache));
        vo.setVoidAuditBy(resolveOperatorName(plan.getVoidAuditBy(), operatorCache));
        List<PurchasePlanItemVO> items = loadPlanItems(Collections.singletonList(id)).getOrDefault(id, Collections.emptyList());
        vo.setItems(new ArrayList<>(items));
        vo.setAttachments(loadPlanAttachments(plan));
        List<PurchaseOrderLinkVO> orderLinks = loadOrderLinks(id, operatorCache);
        vo.setOrderLinks(orderLinks);
        vo.setGeneratedOrderCount(orderLinks.size());
        vo.setAllItemsGenerated(isAllItemsGenerated(items));
        return vo;
    }

    @Override
    public List<PurchasePlanLinkedOrderRecordVO> listLinkedPurchaseOrders(Long id) {
        PurchasePlan plan = getPlanById(id, true, true);
        if (isDeleted(plan) && !dataScopeService.isAdminUser()) {
            throw BizException.notFound("采购计划不存在");
        }
        return loadLinkedPurchaseOrders(plan);
    }

    @Override
    public PurchasePlanAttachmentVO uploadAttachment(MultipartFile file) {
        validateAttachmentFile(file);

        PurchasePlanAttachmentVO vo = new PurchasePlanAttachmentVO();
        vo.setId(ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
        vo.setName(resolveAttachmentName(file));
        vo.setSize(formatFileSize(file.getSize()));
        vo.setUrl(fileStorageService.upload(file, ATTACHMENT_DIR));
        vo.setSortOrder(1);

        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.UPDATE,
                null,
                vo.getName(),
                "上传采购计划附件：" + vo.getName(),
                null,
                toJson(vo)
        );
        return vo;
    }

    @Override
    public void deleteAttachment(String fileUrl, String fileName) {
        String normalizedUrl = normalizeRequiredText(fileUrl, "文件地址不能为空");
        String normalizedFileName = StrUtil.blankToDefault(normalizeOptionalText(fileName), normalizedUrl);

        fileStorageService.delete(normalizedUrl);
        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("fileName", normalizedFileName);
        beforeData.put("fileUrl", normalizedUrl);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.DELETE,
                null,
                normalizedFileName,
                "删除采购计划附件：" + normalizedFileName,
                toJson(beforeData),
                null
        );
    }

    @Override
    public void downloadAttachment(String fileUrl, String fileName, HttpServletResponse response) {
        String normalizedUrl = normalizeRequiredText(fileUrl, "文件地址不能为空");
        String normalizedFileName = StrUtil.blankToDefault(normalizeOptionalText(fileName), "attachment");

        writeAttachment(normalizedFileName, normalizedUrl, response);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("fileName", normalizedFileName);
        afterData.put("fileUrl", normalizedUrl);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.EXPORT,
                null,
                normalizedFileName,
                "下载采购计划附件：" + normalizedFileName,
                null,
                toJson(afterData)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchasePlanCreateDTO dto, MultipartFile file) {
        Long targetOrgId = requireManageableOrgId(dto.getOrgId());
        String status = normalizeEditableStatus(dto.getStatus());
        validateRecipePlanLinkedQuantities(dto.getRelatedDocument(), targetOrgId, dto.getItems(), null);
        List<PurchasePlanItem> items = buildPlanItems(dto.getItems(), targetOrgId, dto.getRelatedDocument());
        validateForecastLinkedQuantities(dto.getRelatedDocument(), targetOrgId, items, null);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(PurchasePlanItem::getMaterialId).toList(),
                "保存采购计划"
        );
        List<PurchasePlanAttachmentDTO> attachments = resolveSubmittedAttachments(dto.getAttachments(), file);

        PurchasePlan plan = new PurchasePlan();
        plan.setPlanNo(resolvePlanNo(dto.getPlanNo(), null));
        fillPlanFields(plan, dto.getPlanName(), targetOrgId, dto.getPlanDate(), dto.getBudgetAmount(),
                dto.getRelatedDocument(), dto.getRemark(), status, items);
        syncLegacyAttachmentFields(plan, attachments);
        purchasePlanMapper.insert(plan);
        savePlanItems(plan.getId(), items);
        replacePlanAttachments(plan.getId(), targetOrgId, plan.getTenantId(), attachments);

        log.info("新增采购计划成功: id={}, planNo={}", plan.getId(), plan.getPlanNo());
        return plan.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchasePlanUpdateDTO dto, MultipartFile file) {
        PurchasePlan plan = getPlanById(id, true);
        ensureEditable(plan.getStatus());
        Long targetOrgId = requireManageableOrgId(dto.getOrgId());
        String status = normalizeEditableStatus(dto.getStatus());
        validateRecipePlanLinkedQuantities(dto.getRelatedDocument(), targetOrgId, dto.getItems(), id);
        List<PurchasePlanItem> items = buildPlanItems(dto.getItems(), targetOrgId, dto.getRelatedDocument());
        validateForecastLinkedQuantities(dto.getRelatedDocument(), targetOrgId, items, id);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(PurchasePlanItem::getMaterialId).toList(),
                "保存采购计划"
        );
        List<String> beforeAttachmentUrls = loadPersistedAttachmentUrls(plan);
        List<PurchasePlanAttachmentDTO> attachments = resolveSubmittedAttachments(dto.getAttachments(), file);

        plan.setPlanNo(resolvePlanNo(dto.getPlanNo(), id));
        fillPlanFields(plan, dto.getPlanName(), targetOrgId, dto.getPlanDate(), dto.getBudgetAmount(),
                dto.getRelatedDocument(), dto.getRemark(), status, items);
        syncLegacyAttachmentFields(plan, attachments);
        purchasePlanMapper.updateById(plan);
        syncNullableEditableFields(id, plan);
        purchasePlanItemMapper.deleteByPlanId(id);
        savePlanItems(id, items);
        replacePlanAttachments(id, targetOrgId, plan.getTenantId(), attachments);
        deleteRemovedAttachmentUrls(beforeAttachmentUrls, attachments);

        log.info("编辑采购计划成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, PurchasePlanAuditDTO dto) {
        String status = normalizeAuditStatus(dto.getStatus());
        PurchasePlan plan = getPlanById(id, true);
        if (!STATUS_PENDING.equals(plan.getStatus())) {
            throw BizException.badRequest("仅待审核状态可执行审核");
        }

        plan.setStatus(status);
        plan.setApprovedBy(DEFAULT_USER_ID);
        plan.setApprovedAt(LocalDateTime.now());
        plan.setApproveRemark(StrUtil.isNotBlank(dto.getRemark())
                ? dto.getRemark().trim()
                : (STATUS_APPROVED.equals(status) ? "审核通过" : "审核驳回"));
        purchasePlanMapper.updateById(plan);

        log.info("审核采购计划成功: id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchasePlanReverseAuditResultVO reverseAudit(Long id, PurchasePlanReverseAuditDTO dto) {
        ensurePermission(PURCHASE_PLAN_APPROVE_PERMISSION, REVERSE_AUDIT_PERMISSION_MESSAGE);
        String reason = normalizeRequiredText(dto == null ? null : dto.getReason(), "请填写反审核原因");
        PurchasePlan plan = getPlanById(id, false, false);
        ensureReverseAuditOrgAllowed(plan.getOrgId());
        PurchasePlanReverseAuditCheckResult initialCheck = inspectReverseAuditPreconditions(plan, id, false);
        String beforeData = toJson(buildReverseAuditAuditPayload(plan, initialCheck, reason, "initial_check", false, null));

        try {
            assertReverseAuditCheckPassed(initialCheck, false);
            PurchasePlanReverseAuditRecheckSnapshot recheckSnapshot = performReverseAuditConcurrentRecheck(id, reason);
            PurchasePlan latestPlan = recheckSnapshot.plan();
            PurchasePlanReverseAuditCheckResult finalCheck = recheckSnapshot.checkResult();

            jdbcTemplate.update(
                    "UPDATE scm_purchase_plan " +
                            "SET status = ?, submitted_at = ?, approved_by = NULL, approved_at = NULL, approve_remark = NULL, " +
                            "updated_by = ?, updated_at = NOW() " +
                            "WHERE id = ?",
                    STATUS_DRAFT,
                    null,
                    resolveCurrentUserId(),
                    latestPlan.getId()
            );

            latestPlan.setStatus(STATUS_DRAFT);
            latestPlan.setSubmittedAt(null);
            latestPlan.setApprovedBy(null);
            latestPlan.setApprovedAt(null);
            latestPlan.setApproveRemark(null);

            PurchasePlanReverseAuditResultVO result = new PurchasePlanReverseAuditResultVO();
            result.setAffectedOrderCount(finalCheck.linkedPurchaseOrderCount());
            result.setAffectedOrderNos(buildLinkedOrderNoList(finalCheck.linkedOrders()));

            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "采购计划反审核成功",
                    beforeData,
                    toJson(buildReverseAuditAuditPayload(
                            latestPlan,
                            finalCheck,
                            reason,
                            "reverse_audit_applied",
                            true,
                            null
                    ))
            );
            log.info(
                    "采购计划反审核成功: id={}, planNo={}, linkedOrderCount={}",
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    finalCheck.linkedPurchaseOrderCount()
            );
            return result;
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    plan.getId(),
                    plan.getPlanNo(),
                    resolveReverseAuditBlockedAuditAction(ex.getMessage()),
                    beforeData,
                    toJson(buildReverseAuditAuditPayload(plan, initialCheck, reason, "reverse_audit_blocked", false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyVoid(Long id, PurchasePlanVoidApplyDTO dto) {
        ensurePermission(PURCHASE_PLAN_DELETE_PERMISSION, "当前用户无采购计划作废权限");
        PurchasePlan plan = getPlanById(id, true, false);
        String reason = normalizeRequiredText(dto.getReason(), "作废原因不能为空");
        PurchasePlanVoidCheckResult initialCheck = inspectVoidApplyPreconditions(plan, id);
        String beforeData = toJson(buildVoidApplyAuditPayload(plan, initialCheck, "initial_check", false, null));

        try {
            validateVoidApplyPreconditions(plan, id, false);
            PurchasePlanVoidRecheckSnapshot recheckSnapshot = performVoidApplyConcurrentRecheck(id);
            PurchasePlan latestPlan = recheckSnapshot.plan();
            String originalStatus = latestPlan.getStatus();

            latestPlan.setStatus(STATUS_PENDING_VOID_APPROVE);
            latestPlan.setVoidOriginStatus(originalStatus);
            latestPlan.setVoidReason(reason);
            latestPlan.setVoidRequestedBy(resolveCurrentUserId());
            latestPlan.setVoidRequestedAt(LocalDateTime.now());
            latestPlan.setVoidAuditBy(null);
            latestPlan.setVoidAuditAt(null);
            latestPlan.setVoidAuditRemark(null);
            purchasePlanMapper.updateById(latestPlan);

            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "发起采购计划作废申请",
                    beforeData,
                    toJson(buildVoidApplyAuditPayload(latestPlan, recheckSnapshot.checkResult(), "void_apply_submitted", true, null))
            );
            log.info("发起采购计划作废申请成功: id={}, planNo={}", latestPlan.getId(), latestPlan.getPlanNo());
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    plan.getId(),
                    plan.getPlanNo(),
                    "发起采购计划作废申请失败",
                    beforeData,
                    toJson(buildVoidApplyAuditPayload(plan, initialCheck, "initial_check", false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditVoid(Long id, PurchasePlanVoidAuditDTO dto) {
        ensurePermission(PURCHASE_PLAN_APPROVE_PERMISSION, "当前用户无采购计划作废审核权限");
        PurchasePlan plan = getPlanById(id, true, false);
        ensureVoidAuditable(plan.getStatus());

        Boolean approved = Boolean.TRUE.equals(dto.getApproved());
        String remark = StrUtil.isNotBlank(dto.getRemark())
                ? dto.getRemark().trim()
                : approved ? "作废审核通过" : "作废审核驳回";
        String beforeData = toJson(buildVoidAuditPayload(plan, approved, remark, false, null));

        try {
            plan.setVoidAuditBy(resolveCurrentUserId());
            plan.setVoidAuditAt(LocalDateTime.now());
            plan.setVoidAuditRemark(remark);
            plan.setStatus(approved ? STATUS_VOIDED : resolveStatusAfterVoidReject(plan));
            purchasePlanMapper.updateById(plan);

            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    plan.getId(),
                    plan.getPlanNo(),
                    approved ? "采购计划作废审核通过" : "采购计划作废审核驳回",
                    beforeData,
                    toJson(buildVoidAuditPayload(plan, approved, remark, true, null))
            );
            log.info("采购计划作废审核完成: id={}, approved={}", plan.getId(), approved);
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    plan.getId(),
                    plan.getPlanNo(),
                    "采购计划作废审核失败",
                    beforeData,
                    toJson(buildVoidAuditPayload(plan, approved, remark, false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ensureDeletePermission();
        PurchasePlan plan = getPlanById(id, true, false);
        PurchasePlanDeleteCheckResult initialCheck = validateDeletePreconditions(plan, id, false);
        String beforeData = toJson(buildDeleteAuditPayload(plan, initialCheck, "initial_check", false, null));

        PurchasePlanDeleteRecheckSnapshot recheckSnapshot = performDeleteConcurrentRecheck(id);
        purchasePlanMapper.deleteById(id);
        PurchasePlan deletedPlan = getPlanById(id, true, true);

        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.DELETE,
                plan.getId(),
                plan.getPlanNo(),
                "删除采购计划：" + plan.getPlanName(),
                beforeData,
                toJson(buildDeleteAuditPayload(deletedPlan, recheckSnapshot.checkResult(), "logical_delete", true, null))
        );
        log.info("删除采购计划成功: id={}, logicalDelete=true", id);
    }

    @Override
    public List<PurchasePlanMaterialOptionVO> listMaterialOptions(Long orgId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (orgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT m.id, m.material_name AS name, m.unit, m.spec, " +
                        "COALESCE((SELECT AVG(oi.unit_price) " +
                        "         FROM scm_purchase_order_item oi " +
                        "         JOIN scm_purchase_order o ON o.id = oi.order_id AND o.deleted = 0 " +
                        "         WHERE oi.material_id = m.id AND o.org_id = m.org_id), 0) AS referencePrice " +
                        "FROM wms_material m " +
                        "WHERE m.deleted = 0 AND m.status = 'active'"
        );
        List<Object> args = new ArrayList<>();
        if (targetOrgId != null) {
            sql.append(" AND m.org_id = ?");
            args.add(targetOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND m.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        sql.append(" ORDER BY m.material_name ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<PurchasePlanMaterialOptionVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            PurchasePlanMaterialOptionVO vo = new PurchasePlanMaterialOptionVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(asString(row.get("name")));
            vo.setUnit(asString(row.get("unit")));
            vo.setSpec(asString(row.get("spec")));
            vo.setReferencePrice(zeroIfNull(toBigDecimal(row.get("referencePrice"))));
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<PurchasePlanRelatedDocumentOptionVO> listRelatedDocuments(Long orgId, String keyword) {
        Long targetOrgId = resolveCurrentUserRelatedDocumentOrgId(orgId);
        if (targetOrgId == null) {
            return Collections.emptyList();
        }

        Long tenantId = resolveTenantId();
        String normalizedKeyword = normalizeOptionalText(keyword);
        List<PurchasePlanRelatedDocumentOptionVO> options = new ArrayList<>();
        options.addAll(queryRecipePlanRelatedDocumentOptions(targetOrgId, tenantId, normalizedKeyword));
        options.addAll(queryForecastRelatedDocumentOptions(targetOrgId, tenantId, normalizedKeyword));
        options.sort(Comparator
                .comparing(PurchasePlanRelatedDocumentOptionVO::getDocumentTypeLabel, Comparator.nullsLast(String::compareTo))
                .thenComparing(PurchasePlanRelatedDocumentOptionVO::getDocumentNo, Comparator.nullsLast(Comparator.reverseOrder())));
        return options;
    }

    @Override
    public List<PurchasePlanRelatedDocumentItemPrefillVO> listRelatedDocumentItems(String documentType, Long documentId) {
        String normalizedDocumentType = normalizeRequiredText(documentType, "关联单据类型不能为空");
        if (documentId == null || documentId <= 0L) {
            throw BizException.badRequest("关联单据ID不能为空");
        }
        if (RELATED_DOCUMENT_TYPE_RECIPE_PLAN.equals(normalizedDocumentType)) {
            return loadRecipePlanRelatedDocumentItems(documentId);
        }
        if (RELATED_DOCUMENT_TYPE_PURCHASE_DEMAND_FORECAST.equals(normalizedDocumentType)) {
            return loadForecastRelatedDocumentItems(documentId);
        }
        throw BizException.badRequest("不支持的关联单据类型");
    }

    @Override
    public PurchasePlanRecipeMaterialLinkageVO getRecipePlanMaterialLinkage(Long documentId, Long excludePlanId) {
        if (documentId == null || documentId <= 0L) {
            return null;
        }

        RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot =
                recipePlanLinkageSupport.loadByRecipePlanId(documentId, resolveTenantId(), excludePlanId, false);
        if (linkageSnapshot == null) {
            return null;
        }

        ensureOrgAllowed(linkageSnapshot.getOrgId());
        return buildRecipePlanMaterialLinkageVO(linkageSnapshot);
    }

    private List<PurchasePlanRelatedDocumentOptionVO> queryRecipePlanRelatedDocumentOptions(
            Long targetOrgId,
            Long tenantId,
            String keyword
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT rp.id, rp.plan_code AS documentNo, rp.plan_date AS planDate, rp.start_date AS startDate, rp.end_date AS endDate, " +
                        "rp.org_id AS orgId, so.org_name AS orgName " +
                        "FROM recipe_plan rp " +
                        "LEFT JOIN sys_organization so ON so.id = rp.org_id AND so.deleted = 0 " +
                        "WHERE rp.deleted = 0 AND (rp.tenant_id = ? OR rp.tenant_id IS NULL OR rp.tenant_id = 0) " +
                        "  AND rp.status = 'approved' " +
                        "  AND COALESCE(rp.end_date, rp.start_date, rp.plan_date) >= CURDATE()"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        sql.append(" AND rp.org_id = ?");
        args.add(targetOrgId);
        if (keyword != null) {
            sql.append(" AND rp.plan_code LIKE ?");
            args.add(like(keyword));
        }
        sql.append(" ORDER BY COALESCE(rp.start_date, rp.plan_date) ASC, rp.id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> recipePlanIds = rows.stream()
                .map(row -> toLong(row.get("id")))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, RecipePlanLinkageSupport.RecipePlanLinkageSnapshot> linkageMap =
                recipePlanLinkageSupport.loadByRecipePlanIds(recipePlanIds, tenantId, null);

        List<PurchasePlanRelatedDocumentOptionVO> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long recipePlanId = toLong(row.get("id"));
            RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot = linkageMap.get(recipePlanId);
            if (!hasAllRecipePlanMaterialsAvailable(linkageSnapshot)) {
                continue;
            }
            if (!hasAllRecipePlanMaterialsResolvable(linkageSnapshot, targetOrgId)) {
                continue;
            }

            PurchasePlanRelatedDocumentOptionVO option = new PurchasePlanRelatedDocumentOptionVO();
            option.setId(recipePlanId);
            option.setDocumentType(RELATED_DOCUMENT_TYPE_RECIPE_PLAN);
            option.setDocumentTypeLabel(RELATED_DOCUMENT_TYPE_RECIPE_PLAN_LABEL);
            option.setDocumentNo(asString(row.get("documentNo")));
            option.setOrgId(toLong(row.get("orgId")));
            option.setOrgName(asString(row.get("orgName")));
            option.setTitle(buildRecipePlanOptionTitle(
                    parseLocalDate(row.get("planDate")),
                    parseLocalDate(row.get("startDate")),
                    parseLocalDate(row.get("endDate"))
            ));
            option.setOptionLabel(option.getDocumentTypeLabel() + " " + option.getDocumentNo() + "｜" + option.getTitle());
            options.add(option);
        }
        return options;
    }

    private List<PurchasePlanRelatedDocumentOptionVO> queryForecastRelatedDocumentOptions(
            Long targetOrgId,
            Long tenantId,
            String keyword
    ) {
        StringBuilder sql = new StringBuilder(
                "SELECT f.id, f.forecast_no AS documentNo, f.forecast_name AS forecastName, " +
                        "f.horizon_start_date AS horizonStartDate, f.horizon_end_date AS horizonEndDate, " +
                        "f.org_id AS orgId, so.org_name AS orgName " +
                        "FROM scm_purchase_demand_forecast f " +
                        "LEFT JOIN sys_organization so ON so.id = f.org_id AND so.deleted = 0 " +
                        "WHERE f.deleted = 0 AND f.tenant_id = ? " +
                        "  AND f.horizon_end_date >= CURDATE()"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        sql.append(" AND f.org_id = ?");
        args.add(targetOrgId);
        if (keyword != null) {
            sql.append(" AND (f.forecast_no LIKE ? OR f.forecast_name LIKE ?)");
            String likeKeyword = like(keyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        sql.append(" ORDER BY f.horizon_end_date ASC, f.id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> forecastIds = rows.stream()
                .map(row -> toLong(row.get("id")))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot> linkageMap =
                purchaseDemandForecastLinkageSupport.loadByForecastIds(forecastIds, tenantId, null);

        List<PurchasePlanRelatedDocumentOptionVO> options = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long forecastId = toLong(row.get("id"));
            PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot = linkageMap.get(forecastId);
            if (linkageSnapshot == null || linkageSnapshot.getItems().isEmpty()) {
                continue;
            }
            boolean allMaterialsAvailable = linkageSnapshot.getItems().stream()
                    .allMatch(item -> scaleQuantity(item.getAvailableQty()).compareTo(BigDecimal.ZERO) > 0);
            if (!allMaterialsAvailable) {
                continue;
            }

            PurchasePlanRelatedDocumentOptionVO option = new PurchasePlanRelatedDocumentOptionVO();
            option.setId(forecastId);
            option.setDocumentType(RELATED_DOCUMENT_TYPE_PURCHASE_DEMAND_FORECAST);
            option.setDocumentTypeLabel(RELATED_DOCUMENT_TYPE_PURCHASE_DEMAND_FORECAST_LABEL);
            option.setDocumentNo(asString(row.get("documentNo")));
            option.setOrgId(toLong(row.get("orgId")));
            option.setOrgName(asString(row.get("orgName")));
            option.setTitle(buildForecastOptionTitle(
                    asString(row.get("forecastName")),
                    parseLocalDate(row.get("horizonStartDate")),
                    parseLocalDate(row.get("horizonEndDate"))
            ));
            option.setOptionLabel(option.getDocumentTypeLabel() + " " + option.getDocumentNo() + "｜" + option.getTitle());
            options.add(option);
        }
        return options;
    }

    private boolean hasAllRecipePlanMaterialsAvailable(
            RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot
    ) {
        return linkageSnapshot != null
                && linkageSnapshot.getItems() != null
                && !linkageSnapshot.getItems().isEmpty()
                && linkageSnapshot.getItems().stream()
                .allMatch(item -> scaleQuantity(item.getAvailableQty()).compareTo(BigDecimal.ZERO) > 0);
    }

    private boolean hasAllRecipePlanMaterialsResolvable(
            RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot,
            Long orgId
    ) {
        return linkageSnapshot != null
                && linkageSnapshot.getItems() != null
                && !linkageSnapshot.getItems().isEmpty()
                && buildRecipePlanResolvedMaterialMap(linkageSnapshot, orgId).size() == linkageSnapshot.getItems().size();
    }

    private List<PurchasePlanRelatedDocumentItemPrefillVO> loadForecastRelatedDocumentItems(Long forecastId) {
        Long tenantId = resolveTenantId();
        Long relatedDocumentOrgId = requireCurrentUserRelatedDocumentOrgId();
        List<Map<String, Object>> headerRows = jdbcTemplate.queryForList(
                "SELECT id, forecast_no AS forecastNo, org_id AS orgId " +
                        "FROM scm_purchase_demand_forecast " +
                        "WHERE deleted = 0 AND tenant_id = ? AND id = ? AND org_id = ? AND horizon_end_date >= CURDATE() LIMIT 1",
                tenantId,
                forecastId,
                relatedDocumentOrgId
        );
        if (headerRows.isEmpty()) {
            throw BizException.badRequest("采购需求预测单不存在或不满足关联条件");
        }

        Map<String, Object> headerRow = headerRows.get(0);
        Long orgId = toLong(headerRow.get("orgId"));
        ensureOrgAllowed(orgId);

        PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot =
                purchaseDemandForecastLinkageSupport.loadByForecastIds(Collections.singletonList(forecastId), tenantId, null)
                        .get(forecastId);
        if (linkageSnapshot == null || linkageSnapshot.getItems().isEmpty()) {
            throw BizException.badRequest("当前采购需求预测单暂无可回填物料");
        }

        Map<Long, BigDecimal> estimatedPriceMap = loadForecastEstimatedUnitPriceMap(forecastId, tenantId);
        List<PurchasePlanRelatedDocumentItemPrefillVO> items = new ArrayList<>();
        for (PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem : linkageSnapshot.getItems()) {
            BigDecimal availableQty = scaleQuantity(linkageItem.getAvailableQty());
            if (availableQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            PurchasePlanRelatedDocumentItemPrefillVO item = new PurchasePlanRelatedDocumentItemPrefillVO();
            item.setSourceForecastDetailId(linkageItem.getForecastDetailId());
            item.setMaterialId(linkageItem.getMaterialId());
            item.setMaterialName(linkageItem.getMaterialName());
            item.setMaterialSpec(linkageItem.getMaterialSpec());
            item.setUnit(linkageItem.getUnit());
            item.setQuantity(availableQty);
            item.setUnitPrice(scalePrice(estimatedPriceMap.get(linkageItem.getMaterialId())));
            items.add(item);
        }
        if (items.isEmpty()) {
            throw BizException.badRequest("当前采购需求预测单暂无可回填物料");
        }
        return items;
    }

    private List<PurchasePlanRelatedDocumentItemPrefillVO> loadRecipePlanRelatedDocumentItems(Long recipePlanId) {
        Long tenantId = resolveTenantId();
        Long relatedDocumentOrgId = requireCurrentUserRelatedDocumentOrgId();
        List<Map<String, Object>> planRows = jdbcTemplate.queryForList(
                "SELECT id, org_id AS orgId " +
                        "FROM recipe_plan " +
                        "WHERE deleted = 0 AND (tenant_id = ? OR tenant_id IS NULL OR tenant_id = 0) AND id = ? AND org_id = ? " +
                        "  AND status = 'approved' " +
                        "  AND COALESCE(end_date, start_date, plan_date) >= CURDATE() LIMIT 1",
                tenantId,
                recipePlanId,
                relatedDocumentOrgId
        );
        if (planRows.isEmpty()) {
            throw BizException.badRequest("菜谱计划单不存在或不满足关联条件");
        }

        Long orgId = toLong(planRows.get(0).get("orgId"));
        ensureOrgAllowed(orgId);

        RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot =
                recipePlanLinkageSupport.loadByRecipePlanId(recipePlanId, tenantId, null, false);
        if (linkageSnapshot == null || linkageSnapshot.getItems().isEmpty()) {
            throw BizException.badRequest("当前菜谱计划单暂无可回填物料");
        }
        Map<Long, Map<String, Object>> resolvedMaterialMap = buildRecipePlanResolvedMaterialMap(linkageSnapshot, orgId);
        if (resolvedMaterialMap.size() != linkageSnapshot.getItems().size()) {
            throw BizException.badRequest("当前菜谱计划单存在无效物料，不满足关联条件");
        }

        List<Long> materialIds = linkageSnapshot.getItems().stream()
                .map(RecipePlanLinkageSupport.RecipeMaterialLinkage::getMaterialId)
                .map(resolvedMaterialMap::get)
                .filter(Objects::nonNull)
                .map(material -> toLong(material.get("id")))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, BigDecimal> referencePriceMap = loadMaterialReferencePriceMap(materialIds, orgId);

        List<PurchasePlanRelatedDocumentItemPrefillVO> items = new ArrayList<>();
        for (RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem : linkageSnapshot.getItems()) {
            BigDecimal quantity = scaleQuantity(linkageItem.getAvailableQty());
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Map<String, Object> resolvedMaterial = resolvedMaterialMap.get(linkageItem.getMaterialId());
            if (resolvedMaterial == null) {
                continue;
            }
            Long materialId = toLong(resolvedMaterial.get("id"));
            PurchasePlanRelatedDocumentItemPrefillVO item = new PurchasePlanRelatedDocumentItemPrefillVO();
            item.setMaterialId(materialId);
            item.setMaterialName(linkageItem.getMaterialName());
            item.setMaterialSpec(linkageItem.getMaterialSpec());
            item.setUnit(linkageItem.getUnit());
            item.setQuantity(quantity);
            item.setUnitPrice(scalePrice(referencePriceMap.get(materialId)));
            items.add(item);
        }
        if (items.isEmpty()) {
            throw BizException.badRequest("当前菜谱计划单暂无可回填物料");
        }
        return items;
    }

    private Map<Long, BigDecimal> loadForecastEstimatedUnitPriceMap(Long forecastId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT material_id AS materialId, COALESCE(AVG(estimated_unit_price), 0) AS estimatedUnitPrice " +
                        "FROM scm_purchase_demand_forecast_item " +
                        "WHERE deleted = 0 AND tenant_id = ? AND forecast_id = ? " +
                        "GROUP BY material_id",
                tenantId,
                forecastId
        );
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (materialId == null) {
                continue;
            }
            result.put(materialId, scalePrice(toBigDecimal(row.get("estimatedUnitPrice"))));
        }
        return result;
    }

    private Map<Long, BigDecimal> loadMaterialReferencePriceMap(Collection<Long> materialIds, Long orgId) {
        List<Long> validMaterialIds = materialIds == null
                ? Collections.emptyList()
                : materialIds.stream().filter(Objects::nonNull).filter(id -> id > 0L).distinct().toList();
        if (validMaterialIds.isEmpty() || orgId == null) {
            return Collections.emptyMap();
        }

        List<Object> args = new ArrayList<>();
        args.add(orgId);
        args.addAll(validMaterialIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT oi.material_id AS materialId, COALESCE(AVG(oi.unit_price), 0) AS referencePrice " +
                        "FROM scm_purchase_order_item oi " +
                        "JOIN scm_purchase_order o ON o.id = oi.order_id AND o.deleted = 0 " +
                        "WHERE o.org_id = ? AND oi.material_id IN (" + placeholders(validMaterialIds.size()) + ") " +
                        "GROUP BY oi.material_id",
                args.toArray()
        );
        Map<Long, BigDecimal> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(row.get("materialId"));
            if (materialId == null) {
                continue;
            }
            result.put(materialId, scalePrice(toBigDecimal(row.get("referencePrice"))));
        }
        return result;
    }

    private Map<Long, Map<String, Object>> loadRecipePlanResolvedMaterialMap(
            String relatedDocument,
            Long orgId,
            Long excludePlanId
    ) {
        String normalizedRelatedDocument = normalizeOptionalText(relatedDocument);
        if (normalizedRelatedDocument == null || orgId == null || orgId <= 0L) {
            return Collections.emptyMap();
        }

        RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot =
                recipePlanLinkageSupport.loadByRecipePlanCode(
                        normalizedRelatedDocument,
                        resolveTenantId(),
                        excludePlanId,
                        false
                );
        if (linkageSnapshot == null || !Objects.equals(orgId, linkageSnapshot.getOrgId())) {
            return Collections.emptyMap();
        }
        return buildRecipePlanResolvedMaterialMap(linkageSnapshot, orgId);
    }

    private Map<Long, Map<String, Object>> buildRecipePlanResolvedMaterialMap(
            RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot,
            Long orgId
    ) {
        if (linkageSnapshot == null || linkageSnapshot.getItems() == null || linkageSnapshot.getItems().isEmpty()
                || orgId == null || orgId <= 0L) {
            return Collections.emptyMap();
        }

        Set<String> materialNames = linkageSnapshot.getItems().stream()
                .map(RecipePlanLinkageSupport.RecipeMaterialLinkage::getMaterialName)
                .map(this::normalizeOptionalText)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (materialNames.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, List<Map<String, Object>>> activeMaterialsByName = loadActiveOrgMaterialsByName(orgId, materialNames);
        Map<Long, Map<String, Object>> resolvedMaterialMap = new LinkedHashMap<>();
        for (RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem : linkageSnapshot.getItems()) {
            if (linkageItem == null || linkageItem.getMaterialId() == null) {
                continue;
            }
            Map<String, Object> resolvedMaterial = resolveRecipePlanMaterialForOrg(linkageItem, activeMaterialsByName);
            if (resolvedMaterial != null) {
                resolvedMaterialMap.put(linkageItem.getMaterialId(), resolvedMaterial);
            }
        }
        return resolvedMaterialMap;
    }

    private Map<String, List<Map<String, Object>>> loadActiveOrgMaterialsByName(Long orgId, Collection<String> materialNames) {
        if (orgId == null || orgId <= 0L || materialNames == null || materialNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> validNames = materialNames.stream()
                .map(this::normalizeOptionalText)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (validNames.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Object> args = new ArrayList<>(validNames.size() + 1);
        args.add(orgId);
        args.addAll(validNames);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, material_name, spec, unit " +
                        "FROM wms_material " +
                        "WHERE org_id = ? AND deleted = 0 AND status = 'active' " +
                        "  AND material_name IN (" + placeholders(validNames.size()) + ") " +
                        "ORDER BY id ASC",
                args.toArray()
        );

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String materialName = normalizeOptionalText(asString(row.get("material_name")));
            if (materialName == null) {
                continue;
            }
            result.computeIfAbsent(materialName, key -> new ArrayList<>()).add(row);
        }
        return result;
    }

    private Map<String, Object> resolveRecipePlanMaterialForOrg(
            RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem,
            Map<String, List<Map<String, Object>>> activeMaterialsByName
    ) {
        if (linkageItem == null || linkageItem.getMaterialId() == null || activeMaterialsByName == null || activeMaterialsByName.isEmpty()) {
            return null;
        }

        String materialName = normalizeOptionalText(linkageItem.getMaterialName());
        if (materialName == null) {
            return null;
        }
        List<Map<String, Object>> candidates = activeMaterialsByName.get(materialName);
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        for (Map<String, Object> candidate : candidates) {
            if (Objects.equals(toLong(candidate.get("id")), linkageItem.getMaterialId())) {
                return candidate;
            }
        }

        String linkageSpec = normalizeOptionalText(linkageItem.getMaterialSpec());
        if (linkageSpec != null) {
            List<Map<String, Object>> specMatched = candidates.stream()
                    .filter(candidate -> Objects.equals(
                            normalizeOptionalText(asString(candidate.get("spec"))),
                            linkageSpec
                    ))
                    .toList();
            if (specMatched.size() == 1) {
                return specMatched.get(0);
            }
        }

        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private PurchasePlanRecipeMaterialLinkageVO buildRecipePlanMaterialLinkageVO(
            RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot
    ) {
        if (linkageSnapshot == null) {
            return null;
        }
        Map<Long, Map<String, Object>> resolvedMaterialMap =
                buildRecipePlanResolvedMaterialMap(linkageSnapshot, linkageSnapshot.getOrgId());
        if (resolvedMaterialMap.size() != linkageSnapshot.getItems().size()) {
            return null;
        }
        PurchasePlanRecipeMaterialLinkageVO vo = new PurchasePlanRecipeMaterialLinkageVO();
        vo.setRecipePlanId(linkageSnapshot.getRecipePlanId());
        vo.setPlanCode(linkageSnapshot.getPlanCode());
        vo.setOrgId(linkageSnapshot.getOrgId());
        vo.setOrgName(resolveOrgName(linkageSnapshot.getOrgId()));
        vo.setMaterialPlanStatus(linkageSnapshot.getMaterialPlanStatus());
        for (RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem : linkageSnapshot.getItems()) {
            Map<String, Object> resolvedMaterial = resolvedMaterialMap.get(linkageItem.getMaterialId());
            if (resolvedMaterial == null) {
                continue;
            }
            PurchasePlanRecipeMaterialLinkageVO.Item item = new PurchasePlanRecipeMaterialLinkageVO.Item();
            item.setMaterialId(toLong(resolvedMaterial.get("id")));
            item.setMaterialName(linkageItem.getMaterialName());
            item.setMaterialSpec(linkageItem.getMaterialSpec());
            item.setUnit(linkageItem.getUnit());
            item.setOriginalQty(scaleQuantity(linkageItem.getOriginalQty()));
            item.setOccupiedQty(scaleQuantity(linkageItem.getOccupiedQty()));
            item.setAvailableQty(scaleQuantity(linkageItem.getAvailableQty()));
            item.setMaterialPlanStatus(linkageItem.getMaterialPlanStatus());
            vo.getItems().add(item);
        }
        return vo;
    }

    private void appendOrgScopeCondition(
            StringBuilder sql,
            List<Object> args,
            String columnName,
            Long targetOrgId,
            List<Long> allowedOrgIds
    ) {
        if (targetOrgId != null) {
            sql.append(" AND ").append(columnName).append(" = ?");
            args.add(targetOrgId);
            return;
        }
        if (allowedOrgIds != null) {
            sql.append(" AND ").append(columnName).append(" IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
    }

    private String buildRecipePlanOptionTitle(LocalDate planDate, LocalDate startDate, LocalDate endDate) {
        LocalDate displayStartDate = startDate != null ? startDate : planDate;
        LocalDate displayEndDate = endDate != null ? endDate : displayStartDate;
        if (displayStartDate == null && displayEndDate == null) {
            return "实施日期待定";
        }
        if (displayStartDate == null) {
            displayStartDate = displayEndDate;
        }
        if (displayEndDate == null) {
            displayEndDate = displayStartDate;
        }
        if (Objects.equals(displayStartDate, displayEndDate)) {
            return "实施日期：" + formatDate(displayStartDate);
        }
        return "实施日期：" + formatDate(displayStartDate) + " 至 " + formatDate(displayEndDate);
    }

    private String buildForecastOptionTitle(String forecastName, LocalDate horizonStartDate, LocalDate horizonEndDate) {
        if (horizonStartDate == null && horizonEndDate == null) {
            return StrUtil.blankToDefault(forecastName, "预测周期待定");
        }
        if (horizonStartDate == null) {
            horizonStartDate = horizonEndDate;
        }
        if (horizonEndDate == null) {
            horizonEndDate = horizonStartDate;
        }
        if (StrUtil.isNotBlank(forecastName)) {
            if (Objects.equals(horizonStartDate, horizonEndDate)) {
                return forecastName + "（截止：" + formatDate(horizonEndDate) + "）";
            }
            return forecastName + "（" + formatDate(horizonStartDate) + " 至 " + formatDate(horizonEndDate) + "）";
        }
        if (Objects.equals(horizonStartDate, horizonEndDate)) {
            return "周期截止：" + formatDate(horizonEndDate);
        }
        return "预测周期：" + formatDate(horizonStartDate) + " 至 " + formatDate(horizonEndDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<PurchaseOrderGenerateResultVO> generateOrders(Long id, PurchasePlanGenerateOrderDTO dto, MultipartFile file) {
        PurchasePlan plan = lockSinglePlanForOrderGenerate(id);
        if (!STATUS_APPROVED.equals(plan.getStatus())) {
            throw BizException.badRequest("仅已审核状态可关联生成采购订单");
        }
        String supplierName = fetchSupplierName(dto.getSupplierId(), plan.getOrgId(), plan.getId());

        Map<Long, PurchasePlanItemVO> itemMap = new LinkedHashMap<>();
        for (PurchasePlanItemVO item : loadPlanItems(Collections.singletonList(id)).getOrDefault(id, Collections.emptyList())) {
            itemMap.put(item.getId(), item);
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                itemMap.values().stream().map(PurchasePlanItemVO::getMaterialId).toList(),
                "生成采购订单"
        );

        Map<Long, BigDecimal> requestedMap = new LinkedHashMap<>();
        Map<Long, PurchasePlanGenerateOrderDTO.Item> requestedItemMap = new LinkedHashMap<>();
        for (PurchasePlanGenerateOrderDTO.Item item : dto.getItems()) {
            PurchasePlanItemVO planItem = itemMap.get(item.getPlanItemId());
            if (planItem == null) {
                throw BizException.badRequest("存在无效的采购计划明细");
            }
            BigDecimal quantity = scaleQuantity(item.getQuantity());
            BigDecimal remaining = remainingQuantity(planItem);
            if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                throw BizException.badRequest("生成数量必须大于0");
            }
            if (quantity.compareTo(remaining) > 0) {
                throw BizException.badRequest("物料“" + planItem.getMaterialName() + "”生成数量不能超过待关联数量");
            }
            requestedMap.merge(item.getPlanItemId(), quantity, BigDecimal::add);
            requestedItemMap.put(item.getPlanItemId(), item);
        }

        List<PurchaseOrderItem> orderItems = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : requestedMap.entrySet()) {
            PurchasePlanItemVO planItem = itemMap.get(entry.getKey());
            PurchasePlanGenerateOrderDTO.Item requestItem = requestedItemMap.get(entry.getKey());
            BigDecimal unitPrice = resolveGeneratedOrderUnitPrice(requestItem, planItem);
            PurchaseOrderItem orderItem = new PurchaseOrderItem();
            orderItem.setPlanItemId(planItem.getId());
            orderItem.setMaterialId(planItem.getMaterialId());
            orderItem.setMaterialName(planItem.getMaterialName());
            orderItem.setMaterialSpec(planItem.getMaterialSpec());
            orderItem.setMaterialUnit(planItem.getUnit());
            orderItem.setOrderQty(scaleQuantity(entry.getValue()));
            orderItem.setReceivedQty(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            orderItem.setUnitPrice(unitPrice);
            orderItem.setTotalAmount(resolveGeneratedOrderSubtotal(requestItem, orderItem.getOrderQty(), unitPrice));
            orderItem.setRemark(planItem.getRemark());
            orderItems.add(orderItem);
        }

        List<PurchaseOrderGenerateResultVO> results = new ArrayList<>();
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(generateUniqueOrderNo());
        order.setPlanId(plan.getId());
        order.setSupplierId(dto.getSupplierId());
        order.setSupplierName(supplierName);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(scaleAmount(orderItems.stream()
                .map(PurchaseOrderItem::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)));
        order.setRemark("采购计划 " + plan.getPlanNo() + " 关联生成");
        order.setStatus("pending_submit");
        order.setOrgId(plan.getOrgId());
        order.setTenantId(plan.getTenantId() == null || plan.getTenantId() == 0L ? DEFAULT_TENANT_ID : plan.getTenantId());
        applyGeneratedOrderAttachment(order, file);
        purchaseOrderMapper.insert(order);

        for (PurchaseOrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            purchaseOrderItemMapper.insert(orderItem);
        }

        PurchaseOrderGenerateResultVO result = new PurchaseOrderGenerateResultVO();
        result.setId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setTotalAmount(order.getTotalAmount());
        results.add(result);

        log.info("采购计划关联生成采购订单成功: planId={}, orderCount={}", id, results.size());
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderGenerateResultVO mergeGenerateOrder(PurchasePlanMergeGenerateOrderDTO dto) {
        ensurePermission(PURCHASE_PLAN_GENERATE_ORDER_PERMISSION, "当前用户无采购计划关联生成采购订单权限");
        List<Long> selectedPlanIds = normalizeMergePlanIds(dto == null ? null : dto.getPlanIds());
        if (selectedPlanIds.size() < 2) {
            throw BizException.badRequest(MERGE_GENERATE_SINGLE_SELECT_MESSAGE);
        }

        List<PurchasePlan> lockedPlans = lockPlansForMergeGenerate(selectedPlanIds);
        Map<Long, List<PurchasePlanItemVO>> planItemMap = loadPlanItems(selectedPlanIds);
        validateMergeGeneratePlans(lockedPlans, planItemMap, false);
        List<PurchaseOrderItem> orderItems = buildMergeOrderItems(lockedPlans, planItemMap);
        if (orderItems.isEmpty()) {
            throw BizException.badRequest("所选采购计划暂无可合并的未履约物料明细");
        }
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                orderItems.stream().map(PurchaseOrderItem::getMaterialId).toList(),
                "生成采购订单"
        );

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(generateUniqueOrderNo());
        order.setPlanId(lockedPlans.get(0).getId());
        order.setSupplierId(MERGE_PLACEHOLDER_SUPPLIER_ID);
        order.setSupplierName(MERGE_PLACEHOLDER_SUPPLIER_NAME);
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        order.setRemark(buildMergeOrderRemark(lockedPlans));
        order.setStatus(STATUS_PENDING_SUBMIT);
        order.setOrgId(resolveMergeOrderOrgId(lockedPlans));
        order.setTenantId(resolveTenantId());
        purchaseOrderMapper.insert(order);

        for (PurchaseOrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
            purchaseOrderItemMapper.insert(orderItem);
        }
        markPlansMergeLocked(selectedPlanIds, order.getId());

        PurchaseOrderGenerateResultVO result = new PurchaseOrderGenerateResultVO();
        result.setId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setTotalAmount(order.getTotalAmount());

        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("sourcePlanIds", selectedPlanIds);
        auditData.put("sourcePlanNos", lockedPlans.stream().map(PurchasePlan::getPlanNo).toList());
        auditData.put("generatedOrderId", order.getId());
        auditData.put("generatedOrderNo", order.getOrderNo());
        auditData.put("generatedOrderStatus", order.getStatus());
        auditData.put("generatedItemCount", orderItems.size());
        auditData.put("lockPlanCount", selectedPlanIds.size());
        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.CREATE,
                order.getId(),
                order.getOrderNo(),
                "多选合并生成采购订单",
                null,
                toJson(auditData)
        );
        log.info("采购计划多选合并生成采购订单成功: orderId={}, sourcePlanCount={}", order.getId(), selectedPlanIds.size());
        return result;
    }

    @Override
    public List<SelectablePurchasePlanVO> listSelectablePlansForOrders(Long orgId, String keyword) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (targetOrgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.plan_no AS planNo, p.plan_name AS planName, p.org_id AS orgId, o.org_name AS orgName, " +
                        "SUM(GREATEST(i.plan_qty - COALESCE(gen.ordered_qty, 0), 0)) AS remainingQuantity, " +
                        "SUM(GREATEST(i.estimate_amount - COALESCE(gen.ordered_amount, 0), 0)) AS remainingAmount " +
                        "FROM scm_purchase_plan p " +
                        "JOIN scm_purchase_plan_item i ON i.plan_id = p.id " +
                        "LEFT JOIN sys_organization o ON o.id = p.org_id AND o.deleted = 0 " +
                        "LEFT JOIN ( " +
                        "    SELECT oi.plan_item_id, SUM(oi.order_qty) AS ordered_qty, SUM(oi.total_amount) AS ordered_amount " +
                        "    FROM scm_purchase_order_item oi " +
                        "    JOIN scm_purchase_order po ON po.id = oi.order_id AND po.deleted = 0 " +
                        "    WHERE oi.plan_item_id IS NOT NULL " +
                        "    GROUP BY oi.plan_item_id " +
                        ") gen ON gen.plan_item_id = i.id " +
                        "WHERE p.deleted = 0 AND p.status = 'approved'");

        List<Object> args = new ArrayList<>();
        if (targetOrgId != null) {
            sql.append(" AND p.org_id = ?");
            args.add(targetOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND p.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        if (StrUtil.isNotBlank(keyword)) {
            sql.append(" AND (p.plan_no LIKE ? OR p.plan_name LIKE ?)");
            String likeKeyword = like(keyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        sql.append(" GROUP BY p.id, p.plan_no, p.plan_name, p.org_id, o.org_name " +
                "HAVING SUM(GREATEST(i.plan_qty - COALESCE(gen.ordered_qty, 0), 0)) > 0 " +
                "ORDER BY p.created_at DESC, p.id DESC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<SelectablePurchasePlanVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            SelectablePurchasePlanVO vo = new SelectablePurchasePlanVO();
            vo.setId(toLong(row.get("id")));
            vo.setPlanNo(asString(row.get("planNo")));
            vo.setPlanName(asString(row.get("planName")));
            vo.setOrgId(toLong(row.get("orgId")));
            vo.setOrgName(asString(row.get("orgName")));
            vo.setRemainingQuantity(scaleQuantity(toBigDecimal(row.get("remainingQuantity"))));
            vo.setRemainingAmount(scaleAmount(toBigDecimal(row.get("remainingAmount"))));
            list.add(vo);
        }
        return list;
    }

    private List<Long> normalizeMergePlanIds(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        return planIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private PurchasePlan lockSinglePlanForOrderGenerate(Long planId) {
        List<PurchasePlan> plans = lockPlansForUpdate(Collections.singletonList(planId));
        if (plans.isEmpty()) {
            throw BizException.notFound("采购计划不存在");
        }
        PurchasePlan plan = plans.get(0);
        if (isDeleted(plan)) {
            throw BizException.notFound("采购计划不存在");
        }
        ensureOrgAllowed(plan.getOrgId());
        return plan;
    }

    private List<PurchasePlan> lockPlansForMergeGenerate(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<PurchasePlan> plans = lockPlansForUpdate(planIds);
        if (plans.size() != planIds.size()) {
            throw BizException.badRequest(MERGE_GENERATE_CONCURRENT_BLOCK_MESSAGE);
        }
        Map<Long, PurchasePlan> planMap = new LinkedHashMap<>();
        for (PurchasePlan plan : plans) {
            planMap.put(plan.getId(), plan);
        }
        List<PurchasePlan> orderedPlans = new ArrayList<>(planIds.size());
        for (Long planId : planIds) {
            PurchasePlan plan = planMap.get(planId);
            if (plan == null) {
                throw BizException.badRequest(MERGE_GENERATE_CONCURRENT_BLOCK_MESSAGE);
            }
            ensureOrgAllowed(plan.getOrgId());
            orderedPlans.add(plan);
        }
        return orderedPlans;
    }

    private List<PurchasePlan> lockPlansForUpdate(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, plan_no AS planNo, plan_name AS planName, plan_date AS planDate, source_type AS sourceType, " +
                "source_ref_id AS sourceRefId, budget_amount AS budgetAmount, total_amount AS totalAmount, " +
                "related_document AS relatedDocument, attachment_name AS attachmentName, attachment_url AS attachmentUrl, " +
                "remark, status, submitted_at AS submittedAt, approved_by AS approvedBy, approved_at AS approvedAt, " +
                "approve_remark AS approveRemark, void_origin_status AS voidOriginStatus, void_reason AS voidReason, " +
                "void_requested_by AS voidRequestedBy, void_requested_at AS voidRequestedAt, " +
                "void_audit_by AS voidAuditBy, void_audit_at AS voidAuditAt, void_audit_remark AS voidAuditRemark, " +
                "COALESCE(merge_locked, 0) AS mergeLocked, merge_order_id AS mergeOrderId, " +
                "org_id AS orgId, tenant_id AS tenantId, created_by AS createdBy, " +
                "created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt, deleted " +
                "FROM scm_purchase_plan WHERE id IN (" + placeholders(planIds.size()) + ") FOR UPDATE NOWAIT";
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(sql, planIds.toArray());
        } catch (DataAccessException ex) {
            if (isPlanLockConflict(ex)) {
                throw BizException.badRequest(MERGE_GENERATE_CONCURRENT_BLOCK_MESSAGE);
            }
            throw ex;
        }
        List<PurchasePlan> plans = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            plans.add(mapPurchasePlan(row));
        }
        return plans;
    }

    private boolean isPlanLockConflict(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                int errorCode = sqlException.getErrorCode();
                if (errorCode == 3572 || errorCode == 1205 || errorCode == 1213) {
                    return true;
                }
            }
            String message = current.getMessage();
            if (message != null && (
                    message.contains("NOWAIT is set")
                            || message.contains("Lock wait timeout exceeded")
                            || message.contains("Deadlock found")
            )) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void validateMergeGeneratePlans(
            List<PurchasePlan> plans,
            Map<Long, List<PurchasePlanItemVO>> planItemMap,
            boolean concurrentRecheck
    ) {
        List<String> invalidPlans = new ArrayList<>();
        for (PurchasePlan plan : plans) {
            String invalidReason = resolveMergeGenerateInvalidReason(plan, planItemMap);
            if (invalidReason != null) {
                invalidPlans.add(plan.getPlanNo() + "（" + invalidReason + "）");
            }
        }
        if (!invalidPlans.isEmpty()) {
            String prefix = concurrentRecheck ? MERGE_GENERATE_CONCURRENT_BLOCK_MESSAGE + "：" : "以下采购计划不满足合并生成条件：";
            throw BizException.badRequest(prefix + String.join("、", invalidPlans));
        }
    }

    private String resolveMergeGenerateInvalidReason(
            PurchasePlan plan,
            Map<Long, List<PurchasePlanItemVO>> planItemMap
    ) {
        if (plan == null || plan.getId() == null || isDeleted(plan)) {
            return "采购计划不存在";
        }
        if (!STATUS_APPROVED.equals(plan.getStatus())) {
            return "当前状态不是已审核";
        }
        List<PurchasePlanItemVO> items = planItemMap.getOrDefault(plan.getId(), Collections.emptyList());
        boolean hasRemainingItems = items.stream().anyMatch(item -> remainingQuantity(item).compareTo(BigDecimal.ZERO) > 0);
        if (!hasRemainingItems) {
            return "无可合并的未履约物料";
        }
        return null;
    }

    private List<PurchaseOrderItem> buildMergeOrderItems(
            List<PurchasePlan> plans,
            Map<Long, List<PurchasePlanItemVO>> planItemMap
    ) {
        List<PurchaseOrderItem> orderItems = new ArrayList<>();
        for (PurchasePlan plan : plans) {
            List<PurchasePlanItemVO> items = planItemMap.getOrDefault(plan.getId(), Collections.emptyList());
            for (PurchasePlanItemVO item : items) {
                BigDecimal remaining = remainingQuantity(item);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                PurchaseOrderItem orderItem = new PurchaseOrderItem();
                orderItem.setPlanItemId(item.getId());
                orderItem.setMaterialId(item.getMaterialId());
                orderItem.setMaterialName(item.getMaterialName());
                orderItem.setMaterialSpec(item.getMaterialSpec());
                orderItem.setMaterialUnit(item.getUnit());
                orderItem.setOrderQty(remaining);
                orderItem.setReceivedQty(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                BigDecimal unitPrice = scalePrice(item.getUnitPrice());
                orderItem.setUnitPrice(unitPrice);
                orderItem.setTotalAmount(scaleAmount(remaining.multiply(unitPrice)));
                orderItem.setRemark(item.getRemark());
                orderItems.add(orderItem);
            }
        }
        return orderItems;
    }

    private void markPlansMergeLocked(List<Long> planIds, Long orderId) {
        if (planIds == null || planIds.isEmpty() || orderId == null) {
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(orderId);
        args.add(resolveCurrentUserId());
        args.addAll(planIds);
        jdbcTemplate.update(
                "UPDATE scm_purchase_plan SET merge_locked = 1, merge_order_id = ?, updated_by = ?, updated_at = NOW() " +
                        "WHERE id IN (" + placeholders(planIds.size()) + ")",
                args.toArray()
        );
    }

    private String buildMergeOrderRemark(List<PurchasePlan> plans) {
        List<String> planNos = plans.stream()
                .map(PurchasePlan::getPlanNo)
                .filter(StrUtil::isNotBlank)
                .toList();
        if (planNos.isEmpty()) {
            return "采购计划多选合并生成";
        }
        if (planNos.size() <= 3) {
            return "采购计划 " + String.join("、", planNos) + " 合并生成";
        }
        return "采购计划 " + String.join("、", planNos.subList(0, 3)) + " 等" + planNos.size() + "条合并生成";
    }

    private Long resolveMergeOrderOrgId(List<PurchasePlan> plans) {
        Long currentOrgId = UserContext.getOrgId();
        if (currentOrgId != null) {
            try {
                return requireManageableOrgId(currentOrgId);
            } catch (BizException ex) {
                log.warn("当前用户组织不在可管理范围内，合并采购订单回退为首条计划组织: {}", ex.getMessage());
            }
        }
        return plans.isEmpty() ? null : plans.get(0).getOrgId();
    }

    private PurchasePlan getPlanById(Long id, boolean checkDataScope) {
        return getPlanById(id, checkDataScope, false);
    }

    private PurchasePlan getPlanById(Long id, boolean checkDataScope, boolean allowDeleted) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, plan_no AS planNo, plan_name AS planName, plan_date AS planDate, source_type AS sourceType, " +
                        "source_ref_id AS sourceRefId, budget_amount AS budgetAmount, total_amount AS totalAmount, " +
                        "related_document AS relatedDocument, attachment_name AS attachmentName, attachment_url AS attachmentUrl, " +
                "remark, status, submitted_at AS submittedAt, approved_by AS approvedBy, approved_at AS approvedAt, " +
                "approve_remark AS approveRemark, void_origin_status AS voidOriginStatus, void_reason AS voidReason, " +
                "void_requested_by AS voidRequestedBy, void_requested_at AS voidRequestedAt, " +
                "void_audit_by AS voidAuditBy, void_audit_at AS voidAuditAt, void_audit_remark AS voidAuditRemark, " +
                "COALESCE(merge_locked, 0) AS mergeLocked, merge_order_id AS mergeOrderId, " +
                "org_id AS orgId, tenant_id AS tenantId, created_by AS createdBy, " +
                "created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt, deleted " +
                        "FROM scm_purchase_plan WHERE id = ?",
                id
        );
        if (rows.isEmpty()) {
            throw BizException.notFound("采购计划不存在");
        }
        PurchasePlan plan = mapPurchasePlan(rows.get(0));
        if (!allowDeleted && isDeleted(plan)) {
            throw BizException.notFound("采购计划不存在");
        }
        if (checkDataScope) {
            ensureOrgAllowed(plan.getOrgId());
        }
        return plan;
    }

    private PurchasePlan mapPurchasePlan(Map<String, Object> row) {
        PurchasePlan plan = new PurchasePlan();
        plan.setId(toLong(row.get("id")));
        plan.setPlanNo(asString(row.get("planNo")));
        plan.setPlanName(asString(row.get("planName")));
        plan.setPlanDate(parseLocalDate(row.get("planDate")));
        plan.setSourceType(asString(row.get("sourceType")));
        plan.setSourceRefId(toLong(row.get("sourceRefId")));
        plan.setBudgetAmount(toBigDecimal(row.get("budgetAmount")));
        plan.setTotalAmount(toBigDecimal(row.get("totalAmount")));
        plan.setRelatedDocument(asString(row.get("relatedDocument")));
        plan.setAttachmentName(asString(row.get("attachmentName")));
        plan.setAttachmentUrl(asString(row.get("attachmentUrl")));
        plan.setRemark(asString(row.get("remark")));
        plan.setStatus(asString(row.get("status")));
        plan.setSubmittedAt(parseLocalDateTime(row.get("submittedAt")));
        plan.setApprovedBy(toLong(row.get("approvedBy")));
        plan.setApprovedAt(parseLocalDateTime(row.get("approvedAt")));
        plan.setApproveRemark(asString(row.get("approveRemark")));
        plan.setVoidOriginStatus(asString(row.get("voidOriginStatus")));
        plan.setVoidReason(asString(row.get("voidReason")));
        plan.setVoidRequestedBy(toLong(row.get("voidRequestedBy")));
        plan.setVoidRequestedAt(parseLocalDateTime(row.get("voidRequestedAt")));
        plan.setVoidAuditBy(toLong(row.get("voidAuditBy")));
        plan.setVoidAuditAt(parseLocalDateTime(row.get("voidAuditAt")));
        plan.setVoidAuditRemark(asString(row.get("voidAuditRemark")));
        plan.setMergeLocked(toInteger(row.get("mergeLocked")));
        plan.setMergeOrderId(toLong(row.get("mergeOrderId")));
        plan.setOrgId(toLong(row.get("orgId")));
        plan.setTenantId(toLong(row.get("tenantId")));
        plan.setCreatedBy(toLong(row.get("createdBy")));
        plan.setCreatedAt(parseLocalDateTime(row.get("createdAt")));
        plan.setUpdatedBy(toLong(row.get("updatedBy")));
        plan.setUpdatedAt(parseLocalDateTime(row.get("updatedAt")));
        plan.setDeleted(toInteger(row.get("deleted")));
        return plan;
    }

    private PurchasePlanVO toVO(PurchasePlan plan) {
        PurchasePlanVO vo = new PurchasePlanVO();
        vo.setId(plan.getId());
        vo.setPlanNo(plan.getPlanNo());
        vo.setPlanName(plan.getPlanName());
        vo.setOrgId(plan.getOrgId());
        vo.setOrgName(resolveOrgName(plan.getOrgId()));
        vo.setPlanDate(formatDate(plan.getPlanDate()));
        vo.setCreatedAt(formatDateTime(plan.getCreatedAt()));
        vo.setBudgetAmount(zeroIfNull(plan.getBudgetAmount()));
        vo.setTotalAmount(zeroIfNull(plan.getTotalAmount()));
        vo.setRelatedDocument(plan.getRelatedDocument());
        vo.setAttachmentName(plan.getAttachmentName());
        vo.setAttachmentUrl(plan.getAttachmentUrl());
        vo.setRemark(plan.getRemark());
        vo.setStatus(plan.getStatus());
        vo.setDeleted(isDeleted(plan));
        vo.setAuditRemark(plan.getApproveRemark());
        vo.setAuditAt(formatDateTime(plan.getApprovedAt()));
        vo.setVoidOriginalStatus(plan.getVoidOriginStatus());
        vo.setVoidReason(plan.getVoidReason());
        vo.setVoidRequestedAt(formatDateTime(plan.getVoidRequestedAt()));
        vo.setVoidAuditAt(formatDateTime(plan.getVoidAuditAt()));
        vo.setVoidAuditRemark(plan.getVoidAuditRemark());
        vo.setMergeLocked(plan.getMergeLocked() != null && plan.getMergeLocked() != 0);
        vo.setMergeOrderId(plan.getMergeOrderId());
        return vo;
    }

    private void ensureDeletePermission() {
        ensurePermission(PURCHASE_PLAN_DELETE_PERMISSION, "当前用户无采购计划删除权限");
    }

    private PurchasePlanDeleteCheckResult validateDeletePreconditions(PurchasePlan plan, Long planId, boolean concurrentRecheck) {
        PurchasePlanDeleteCheckResult checkResult = inspectDeletePreconditions(plan, planId);
        if (!checkResult.passed()) {
            throwDeleteBlocked(concurrentRecheck, checkResult.blockedReason());
        }
        return checkResult;
    }

    private PurchasePlanDeleteCheckResult inspectDeletePreconditions(PurchasePlan plan, Long planId) {
        int linkedPurchaseOrderCount = countLinkedPurchaseOrders(planId);
        int unfinishedInboundOrderCount = countUnfinishedInboundOrders(planId);

        String blockedReason = null;
        if (plan == null || plan.getId() == null || isDeleted(plan)) {
            blockedReason = "采购计划不存在";
        } else if (STATUS_REJECTED.equals(plan.getStatus())) {
            blockedReason = REJECTED_DELETE_BLOCK_MESSAGE;
        } else if (!STATUS_DRAFT.equals(plan.getStatus()) && !STATUS_PENDING.equals(plan.getStatus())) {
            blockedReason = "仅草稿或待审核状态可删除";
        } else if (linkedPurchaseOrderCount > 0 || unfinishedInboundOrderCount > 0) {
            blockedReason = DELETE_BLOCK_MESSAGE;
        }

        return new PurchasePlanDeleteCheckResult(
                linkedPurchaseOrderCount,
                unfinishedInboundOrderCount,
                blockedReason
        );
    }

    private Map<String, Object> buildDeleteAuditPayload(
            PurchasePlan plan,
            PurchasePlanDeleteCheckResult checkResult,
            String stage,
            boolean operationApplied,
            String blockedReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan == null ? null : plan.getId());
        payload.put("planNo", plan == null ? null : plan.getPlanNo());
        payload.put("planName", plan == null ? null : plan.getPlanName());
        payload.put("status", plan == null ? null : plan.getStatus());
        payload.put("deleted", plan != null && isDeleted(plan));
        payload.put("orgId", plan == null ? null : plan.getOrgId());
        payload.put("tenantId", plan == null ? null : plan.getTenantId());
        payload.put("stage", stage);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        if (checkResult != null) {
            payload.put("linkedPurchaseOrderCount", checkResult.linkedPurchaseOrderCount());
            payload.put("unfinishedInboundOrderCount", checkResult.unfinishedInboundOrderCount());
            payload.put("validationPassed", checkResult.passed());
            payload.put("checkBlockedReason", checkResult.blockedReason());
        } else {
            payload.put("linkedPurchaseOrderCount", 0);
            payload.put("unfinishedInboundOrderCount", 0);
            payload.put("validationPassed", false);
            payload.put("checkBlockedReason", null);
        }
        payload.put("blockedReason", blockedReason);
        payload.put("deleteMode", "logical");
        return payload;
    }

    private PurchasePlanDeleteRecheckSnapshot performDeleteConcurrentRecheck(Long id) {
        PurchasePlan latestPlan = getPlanById(id, true, true);
        PurchasePlanDeleteCheckResult checkResult = inspectDeletePreconditions(latestPlan, id);
        String beforeData = toJson(buildDeleteAuditPayload(latestPlan, checkResult, "concurrent_final_recheck", false, null));
        try {
            PurchasePlanDeleteCheckResult validated = validateDeletePreconditions(latestPlan, id, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.DELETE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "采购计划删除并发二次重校验通过",
                    beforeData,
                    toJson(buildDeleteAuditPayload(latestPlan, validated, "concurrent_final_recheck", false, null))
            );
            return new PurchasePlanDeleteRecheckSnapshot(latestPlan, validated);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.DELETE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "采购计划删除并发二次重校验拦截",
                    beforeData,
                    toJson(buildDeleteAuditPayload(latestPlan, checkResult, "concurrent_final_recheck", false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private void throwDeleteBlocked(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? CONCURRENT_DELETE_BLOCK_MESSAGE : defaultMessage);
    }

    private PurchasePlanVoidCheckResult validateVoidApplyPreconditions(PurchasePlan plan, Long planId, boolean concurrentRecheck) {
        PurchasePlanVoidCheckResult checkResult = inspectVoidApplyPreconditions(plan, planId);
        if (!checkResult.passed()) {
            throwVoidApplyBlocked(concurrentRecheck, checkResult.blockedReason());
        }
        return checkResult;
    }

    private PurchasePlanVoidCheckResult inspectVoidApplyPreconditions(PurchasePlan plan, Long planId) {
        int linkedPurchaseOrderCount = countLinkedPurchaseOrders(planId);
        int unfinishedInboundOrderCount = countUnfinishedInboundOrders(planId);

        String blockedReason = null;
        if (plan == null || plan.getId() == null || isDeleted(plan)) {
            blockedReason = "采购计划不存在";
        } else if (STATUS_REJECTED.equals(plan.getStatus())) {
            blockedReason = null;
        } else if (STATUS_APPROVED.equals(plan.getStatus())) {
            if (linkedPurchaseOrderCount > 0 || unfinishedInboundOrderCount > 0) {
                blockedReason = VOID_APPLY_BLOCK_MESSAGE;
            }
        } else if (STATUS_PENDING_VOID_APPROVE.equals(plan.getStatus())) {
            blockedReason = "当前采购计划已提交作废审核，请勿重复发起作废";
        } else if (STATUS_VOIDED.equals(plan.getStatus())) {
            blockedReason = "已作废采购计划不可再次发起作废";
        } else {
            blockedReason = "仅已审核（无下游业务）或已驳回状态可发起作废";
        }

        return new PurchasePlanVoidCheckResult(
                linkedPurchaseOrderCount,
                unfinishedInboundOrderCount,
                blockedReason
        );
    }

    private Map<String, Object> buildVoidApplyAuditPayload(
            PurchasePlan plan,
            PurchasePlanVoidCheckResult checkResult,
            String stage,
            boolean operationApplied,
            String blockedReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan == null ? null : plan.getId());
        payload.put("planNo", plan == null ? null : plan.getPlanNo());
        payload.put("planName", plan == null ? null : plan.getPlanName());
        payload.put("status", plan == null ? null : plan.getStatus());
        payload.put("voidOriginalStatus", plan == null ? null : plan.getVoidOriginStatus());
        payload.put("voidReason", plan == null ? null : plan.getVoidReason());
        payload.put("voidRequestedBy", plan == null ? null : plan.getVoidRequestedBy());
        payload.put("voidRequestedAt", plan == null ? null : formatDateTime(plan.getVoidRequestedAt()));
        payload.put("voidAuditBy", plan == null ? null : plan.getVoidAuditBy());
        payload.put("voidAuditAt", plan == null ? null : formatDateTime(plan.getVoidAuditAt()));
        payload.put("voidAuditRemark", plan == null ? null : plan.getVoidAuditRemark());
        payload.put("deleted", plan != null && isDeleted(plan));
        payload.put("stage", stage);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        if (checkResult != null) {
            payload.put("linkedPurchaseOrderCount", checkResult.linkedPurchaseOrderCount());
            payload.put("unfinishedInboundOrderCount", checkResult.unfinishedInboundOrderCount());
            payload.put("validationPassed", checkResult.passed());
            payload.put("checkBlockedReason", checkResult.blockedReason());
        } else {
            payload.put("linkedPurchaseOrderCount", 0);
            payload.put("unfinishedInboundOrderCount", 0);
            payload.put("validationPassed", false);
            payload.put("checkBlockedReason", null);
        }
        payload.put("blockedReason", blockedReason);
        return payload;
    }

    private Map<String, Object> buildVoidAuditPayload(
            PurchasePlan plan,
            Boolean approved,
            String remark,
            boolean operationApplied,
            String blockedReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("planId", plan == null ? null : plan.getId());
        payload.put("planNo", plan == null ? null : plan.getPlanNo());
        payload.put("planName", plan == null ? null : plan.getPlanName());
        payload.put("status", plan == null ? null : plan.getStatus());
        payload.put("voidOriginalStatus", plan == null ? null : plan.getVoidOriginStatus());
        payload.put("voidReason", plan == null ? null : plan.getVoidReason());
        payload.put("voidRequestedBy", plan == null ? null : plan.getVoidRequestedBy());
        payload.put("voidRequestedAt", plan == null ? null : formatDateTime(plan.getVoidRequestedAt()));
        payload.put("voidAuditBy", plan == null ? null : plan.getVoidAuditBy());
        payload.put("voidAuditAt", plan == null ? null : formatDateTime(plan.getVoidAuditAt()));
        payload.put("voidAuditRemark", plan == null ? null : plan.getVoidAuditRemark());
        payload.put("auditApproved", approved);
        payload.put("auditRemark", remark);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        payload.put("blockedReason", blockedReason);
        return payload;
    }

    private PurchasePlanVoidRecheckSnapshot performVoidApplyConcurrentRecheck(Long id) {
        PurchasePlan latestPlan = getPlanById(id, true, true);
        PurchasePlanVoidCheckResult checkResult = inspectVoidApplyPreconditions(latestPlan, id);
        String beforeData = toJson(buildVoidApplyAuditPayload(latestPlan, checkResult, "concurrent_final_recheck", false, null));
        try {
            PurchasePlanVoidCheckResult validated = validateVoidApplyPreconditions(latestPlan, id, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "采购计划作废申请并发二次重校验通过",
                    beforeData,
                    toJson(buildVoidApplyAuditPayload(latestPlan, validated, "concurrent_final_recheck", false, null))
            );
            return new PurchasePlanVoidRecheckSnapshot(latestPlan, validated);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "采购计划作废申请并发二次重校验拦截",
                    beforeData,
                    toJson(buildVoidApplyAuditPayload(latestPlan, checkResult, "concurrent_final_recheck", false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private PurchasePlanReverseAuditCheckResult validateReverseAuditPreconditions(
            PurchasePlan plan,
            Long planId,
            boolean concurrentRecheck,
            boolean lockOrderRows
    ) {
        PurchasePlanReverseAuditCheckResult checkResult = inspectReverseAuditPreconditions(plan, planId, lockOrderRows);
        assertReverseAuditCheckPassed(checkResult, concurrentRecheck);
        return checkResult;
    }

    private void assertReverseAuditCheckPassed(
            PurchasePlanReverseAuditCheckResult checkResult,
            boolean concurrentRecheck
    ) {
        if (checkResult == null || !checkResult.passed()) {
            throwReverseAuditBlocked(
                    concurrentRecheck,
                    checkResult == null ? REVERSE_AUDIT_STATUS_BLOCK_MESSAGE : checkResult.blockedReason()
            );
        }
    }

    private PurchasePlanReverseAuditCheckResult inspectReverseAuditPreconditions(
            PurchasePlan plan,
            Long planId,
            boolean lockOrderRows
    ) {
        List<LinkedPurchaseOrderState> linkedOrders = loadLinkedPurchaseOrdersForReverseAudit(planId, lockOrderRows);
        List<LinkedPurchaseOrderState> blockedOrders = new ArrayList<>();
        for (LinkedPurchaseOrderState linkedOrder : linkedOrders) {
            if (isReverseAuditBlockedOrderStatus(linkedOrder.status())) {
                blockedOrders.add(linkedOrder);
            }
        }

        int linkedInboundOrderCount = countLinkedInboundOrdersForReverseAudit(linkedOrders);
        int linkedReceiptRecordCount = countLinkedReceiptRecordsForReverseAudit(linkedOrders);
        String blockedReason = null;
        if (plan == null || plan.getId() == null || isDeleted(plan)) {
            blockedReason = "采购计划不存在";
        } else if (!STATUS_APPROVED.equals(plan.getStatus())) {
            blockedReason = REVERSE_AUDIT_STATUS_BLOCK_MESSAGE;
        } else if (!blockedOrders.isEmpty()) {
            blockedReason = REVERSE_AUDIT_LINKED_ORDER_STATUS_BLOCK_MESSAGE;
        } else if (linkedInboundOrderCount > 0 || linkedReceiptRecordCount > 0) {
            blockedReason = REVERSE_AUDIT_BLOCK_MESSAGE;
        }

        return new PurchasePlanReverseAuditCheckResult(
                linkedOrders.size(),
                linkedInboundOrderCount,
                linkedReceiptRecordCount,
                linkedOrders,
                blockedOrders,
                blockedReason
        );
    }

    private Map<String, Object> buildReverseAuditAuditPayload(
            PurchasePlan plan,
            PurchasePlanReverseAuditCheckResult checkResult,
            String reason,
            String stage,
            boolean operationApplied,
            String blockedReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String currentStatus = plan == null ? null : plan.getStatus();
        payload.put("planId", plan == null ? null : plan.getId());
        payload.put("planNo", plan == null ? null : plan.getPlanNo());
        payload.put("planName", plan == null ? null : plan.getPlanName());
        payload.put("status", currentStatus);
        payload.put("orgId", plan == null ? null : plan.getOrgId());
        payload.put("tenantId", plan == null ? null : plan.getTenantId());
        payload.put("reverseAuditReason", reason);
        payload.put("stage", stage);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        payload.put(
                "reviewNodeStatus",
                STATUS_DRAFT.equals(currentStatus) ? "草稿" : (STATUS_APPROVED.equals(currentStatus) ? "已审核" : null)
        );
        if (checkResult != null) {
            payload.put("linkedPurchaseOrderCount", checkResult.linkedPurchaseOrderCount());
            payload.put("linkedInboundOrderCount", checkResult.linkedInboundOrderCount());
            payload.put("linkedReceiptRecordCount", checkResult.linkedReceiptRecordCount());
            payload.put("affectedOrderCount", checkResult.linkedPurchaseOrderCount());
            payload.put("affectedOrders", buildLinkedOrderPayloadList(checkResult.linkedOrders()));
            payload.put("blockedOrderCount", checkResult.blockedOrders().size());
            payload.put("blockedOrders", buildLinkedOrderPayloadList(checkResult.blockedOrders()));
            payload.put("validationPassed", checkResult.passed());
            payload.put("checkBlockedReason", checkResult.blockedReason());
        } else {
            payload.put("linkedPurchaseOrderCount", 0);
            payload.put("linkedInboundOrderCount", 0);
            payload.put("linkedReceiptRecordCount", 0);
            payload.put("affectedOrderCount", 0);
            payload.put("affectedOrders", Collections.emptyList());
            payload.put("blockedOrderCount", 0);
            payload.put("blockedOrders", Collections.emptyList());
            payload.put("validationPassed", false);
            payload.put("checkBlockedReason", null);
        }
        payload.put("downstreamOrderStatusChanged", false);
        payload.put("downstreamRelationChanged", false);
        payload.put("occupancyReleased", false);
        payload.put("blockedReason", blockedReason);
        return payload;
    }

    private PurchasePlanReverseAuditRecheckSnapshot performReverseAuditConcurrentRecheck(Long id, String reason) {
        PurchasePlan latestPlan = lockSinglePlanForReverseAudit(id);
        PurchasePlanReverseAuditCheckResult checkResult = inspectReverseAuditPreconditions(latestPlan, id, true);
        String beforeData = toJson(buildReverseAuditAuditPayload(latestPlan, checkResult, reason, "concurrent_final_recheck", false, null));
        try {
            PurchasePlanReverseAuditCheckResult validated = validateReverseAuditPreconditions(latestPlan, id, true, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    "关联采购订单状态校验通过",
                    beforeData,
                    toJson(buildReverseAuditAuditPayload(latestPlan, validated, reason, "concurrent_final_recheck", false, null))
            );
            return new PurchasePlanReverseAuditRecheckSnapshot(latestPlan, validated);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_PLAN,
                    AuditOperationType.STATUS_CHANGE,
                    latestPlan.getId(),
                    latestPlan.getPlanNo(),
                    resolveReverseAuditBlockedAuditAction(ex.getMessage()),
                    beforeData,
                    toJson(buildReverseAuditAuditPayload(latestPlan, checkResult, reason, "concurrent_final_recheck", false, ex.getMessage())),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private PurchasePlan lockSinglePlanForReverseAudit(Long planId) {
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, plan_no AS planNo, plan_name AS planName, plan_date AS planDate, source_type AS sourceType, " +
                            "source_ref_id AS sourceRefId, budget_amount AS budgetAmount, total_amount AS totalAmount, " +
                            "related_document AS relatedDocument, attachment_name AS attachmentName, attachment_url AS attachmentUrl, " +
                            "remark, status, submitted_at AS submittedAt, approved_by AS approvedBy, approved_at AS approvedAt, " +
                            "approve_remark AS approveRemark, void_origin_status AS voidOriginStatus, void_reason AS voidReason, " +
                            "void_requested_by AS voidRequestedBy, void_requested_at AS voidRequestedAt, " +
                            "void_audit_by AS voidAuditBy, void_audit_at AS voidAuditAt, void_audit_remark AS voidAuditRemark, " +
                            "COALESCE(merge_locked, 0) AS mergeLocked, merge_order_id AS mergeOrderId, " +
                            "org_id AS orgId, tenant_id AS tenantId, created_by AS createdBy, " +
                            "created_at AS createdAt, updated_by AS updatedBy, updated_at AS updatedAt, deleted " +
                            "FROM scm_purchase_plan WHERE id = ? FOR UPDATE NOWAIT",
                    planId
            );
        } catch (DataAccessException ex) {
            if (isPlanLockConflict(ex)) {
                throw BizException.badRequest(REVERSE_AUDIT_CONCURRENT_BLOCK_MESSAGE);
            }
            throw ex;
        }
        if (rows.isEmpty()) {
            throw BizException.notFound("采购计划不存在");
        }
        PurchasePlan plan = mapPurchasePlan(rows.get(0));
        if (isDeleted(plan)) {
            throw BizException.notFound("采购计划不存在");
        }
        ensureReverseAuditOrgAllowed(plan.getOrgId());
        return plan;
    }

    private List<Map<String, Object>> buildLinkedOrderPayloadList(List<LinkedPurchaseOrderState> linkedOrders) {
        if (linkedOrders == null || linkedOrders.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> payload = new ArrayList<>(linkedOrders.size());
        for (LinkedPurchaseOrderState linkedOrder : linkedOrders) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderId", linkedOrder.orderId());
            item.put("orderNo", linkedOrder.orderNo());
            item.put("status", linkedOrder.status());
            payload.add(item);
        }
        return payload;
    }

    private List<LinkedPurchaseOrderState> loadLinkedPurchaseOrdersForReverseAudit(Long planId, boolean lockOrderRows) {
        if (planId == null) {
            return Collections.emptyList();
        }
        List<Long> orderIds = jdbcTemplate.query(
                "SELECT DISTINCT relation.orderId " +
                        "FROM ( " +
                        "    SELECT po.id AS orderId FROM scm_purchase_order po WHERE po.deleted = 0 AND po.plan_id = ? " +
                        "    UNION " +
                        "    SELECT po.id AS orderId " +
                        "    FROM scm_purchase_order_item poi " +
                        "    JOIN scm_purchase_order po ON po.id = poi.order_id AND po.deleted = 0 " +
                        "    JOIN scm_purchase_plan_item pi ON pi.id = poi.plan_item_id " +
                        "    WHERE pi.plan_id = ? " +
                        "    UNION " +
                        "    SELECT p.merge_order_id AS orderId " +
                        "    FROM scm_purchase_plan p " +
                        "    JOIN scm_purchase_order po ON po.id = p.merge_order_id AND po.deleted = 0 " +
                        "    WHERE p.id = ? AND p.merge_order_id IS NOT NULL " +
                        ") relation ORDER BY relation.orderId ASC",
                (rs, rowNum) -> rs.getLong("orderId"),
                planId,
                planId,
                planId
        );
        if (orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        String sql = "SELECT id, order_no AS orderNo, status " +
                "FROM scm_purchase_order WHERE deleted = 0 AND id IN (" + placeholders(orderIds.size()) + ")" +
                (lockOrderRows ? " FOR UPDATE" : "");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orderIds.toArray());
        List<LinkedPurchaseOrderState> linkedOrders = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long orderId = toLong(row.get("id"));
            if (orderId == null) {
                continue;
            }
            linkedOrders.add(new LinkedPurchaseOrderState(
                    orderId,
                    asString(row.get("orderNo")),
                    asString(row.get("status"))
            ));
        }
        linkedOrders.sort(Comparator.comparing(LinkedPurchaseOrderState::orderId));
        return linkedOrders;
    }

    private int countLinkedInboundOrdersForReverseAudit(List<LinkedPurchaseOrderState> linkedOrders) {
        if (linkedOrders == null || linkedOrders.isEmpty()) {
            return 0;
        }
        List<Long> orderIds = new ArrayList<>(linkedOrders.size());
        for (LinkedPurchaseOrderState linkedOrder : linkedOrders) {
            if (linkedOrder.orderId() != null) {
                orderIds.add(linkedOrder.orderId());
            }
        }
        if (orderIds.isEmpty()) {
            return 0;
        }
        List<Object> args = new ArrayList<>(orderIds.size());
        args.addAll(orderIds);
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT io.id) " +
                        "FROM wms_inbound_order io " +
                        "WHERE io.deleted = 0 " +
                        "  AND io.source_type = 'purchase' " +
                        "  AND io.status <> 'cancelled' " +
                        "  AND io.source_id IN (" + placeholders(orderIds.size()) + ")",
                Integer.class,
                args.toArray()
        );
        return count == null ? 0 : count;
    }

    private int countLinkedReceiptRecordsForReverseAudit(List<LinkedPurchaseOrderState> linkedOrders) {
        if (linkedOrders == null || linkedOrders.isEmpty()) {
            return 0;
        }
        List<Long> orderIds = new ArrayList<>(linkedOrders.size());
        for (LinkedPurchaseOrderState linkedOrder : linkedOrders) {
            if (linkedOrder.orderId() != null) {
                orderIds.add(linkedOrder.orderId());
            }
        }
        if (orderIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT id) FROM scm_receipt_record WHERE order_id IN (" + placeholders(orderIds.size()) + ")",
                Integer.class,
                orderIds.toArray()
        );
        return count == null ? 0 : count;
    }

    private boolean isReverseAuditBlockedOrderStatus(String status) {
        return STATUS_PENDING_SUBMIT.equals(status)
                || PURCHASE_ORDER_STATUS_PENDING_APPROVE.equals(status)
                || PURCHASE_ORDER_STATUS_DELIVERING.equals(status)
                || PURCHASE_ORDER_STATUS_PENDING_RECEIPT.equals(status)
                || PURCHASE_ORDER_STATUS_COMPLETED.equals(status)
                || PURCHASE_ORDER_STATUS_PENDING_VOID_APPROVE.equals(status)
                || STATUS_REJECTED.equals(status);
    }

    private String resolveReverseAuditBlockedAuditAction(String blockedReason) {
        if (Objects.equals(REVERSE_AUDIT_LINKED_ORDER_STATUS_BLOCK_MESSAGE, blockedReason)
                || Objects.equals(REVERSE_AUDIT_BLOCK_MESSAGE, blockedReason)) {
            return "关联采购订单状态阻断";
        }
        return "采购计划反审核拦截";
    }

    private List<String> buildLinkedOrderNoList(List<LinkedPurchaseOrderState> linkedOrders) {
        if (linkedOrders == null || linkedOrders.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> orderNos = new ArrayList<>(linkedOrders.size());
        for (LinkedPurchaseOrderState linkedOrder : linkedOrders) {
            if (linkedOrder != null && StrUtil.isNotBlank(linkedOrder.orderNo())) {
                orderNos.add(linkedOrder.orderNo());
            }
        }
        return orderNos;
    }

    private void throwReverseAuditBlocked(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? REVERSE_AUDIT_CONCURRENT_BLOCK_MESSAGE : defaultMessage);
    }

    private void throwVoidApplyBlocked(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? CONCURRENT_VOID_APPLY_BLOCK_MESSAGE : defaultMessage);
    }

    private int countLinkedPurchaseOrders(Long planId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT po.id) " +
                        "FROM scm_purchase_order po " +
                        "LEFT JOIN scm_purchase_order_item poi ON poi.order_id = po.id " +
                        "LEFT JOIN scm_purchase_plan_item pi ON pi.id = poi.plan_item_id " +
                        "LEFT JOIN scm_purchase_plan p ON p.id = ? AND p.merge_order_id = po.id " +
                        "WHERE po.deleted = 0 AND (po.plan_id = ? OR pi.plan_id = ? OR p.id IS NOT NULL)",
                Integer.class,
                planId,
                planId,
                planId
        );
        return count == null ? 0 : count;
    }

    private int countUnfinishedInboundOrders(Long planId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT io.id) " +
                        "FROM wms_inbound_order io " +
                        "JOIN scm_purchase_order po ON po.id = io.source_id AND po.deleted = 0 " +
                        "LEFT JOIN scm_purchase_order_item poi ON poi.order_id = po.id " +
                        "LEFT JOIN scm_purchase_plan_item pi ON pi.id = poi.plan_item_id " +
                        "LEFT JOIN scm_purchase_plan p ON p.id = ? AND p.merge_order_id = po.id " +
                        "WHERE io.deleted = 0 " +
                        "  AND io.source_type = 'purchase' " +
                        "  AND io.status NOT IN ('approved', 'cancelled') " +
                        "  AND (po.plan_id = ? OR pi.plan_id = ? OR p.id IS NOT NULL)",
                Integer.class,
                planId,
                planId,
                planId
        );
        return count == null ? 0 : count;
    }

    private void fillPlanFields(
            PurchasePlan plan,
            String planName,
            Long orgId,
            String planDate,
            BigDecimal budgetAmount,
            String relatedDocument,
            String remark,
            String status,
            List<PurchasePlanItem> items
    ) {
        plan.setPlanName(planName == null ? null : planName.trim());
        plan.setOrgId(orgId);
        plan.setPlanDate(parseDate(planDate));
        plan.setSourceType("manual");
        plan.setSourceRefId(null);
        plan.setBudgetAmount(scaleAmount(budgetAmount));
        plan.setTotalAmount(scaleAmount(sumEstimateAmount(items)));
        plan.setRelatedDocument(StrUtil.isBlank(relatedDocument) ? null : relatedDocument.trim());
        plan.setRemark(StrUtil.isBlank(remark) ? null : remark.trim());
        plan.setStatus(status);
        plan.setSubmittedAt(STATUS_PENDING.equals(status) ? LocalDateTime.now() : null);
        if (!STATUS_APPROVED.equals(status) && !STATUS_REJECTED.equals(status)) {
            plan.setApprovedBy(null);
            plan.setApprovedAt(null);
            plan.setApproveRemark(null);
        }
        plan.setTenantId(resolveTenantId());
    }

    private void validateForecastLinkedQuantities(
            String relatedDocument,
            Long orgId,
            List<PurchasePlanItem> items,
            Long excludePlanId
    ) {
        String normalizedRelatedDocument = normalizeOptionalText(relatedDocument);
        if (normalizedRelatedDocument == null || items == null || items.isEmpty()) {
            return;
        }

        PurchaseDemandForecastLinkageSupport.ForecastLinkageSnapshot linkageSnapshot =
                purchaseDemandForecastLinkageSupport.loadByForecastNo(
                        normalizedRelatedDocument,
                        resolveTenantId(),
                        excludePlanId,
                        true
                );
        if (linkageSnapshot == null) {
            return;
        }
        if (!Objects.equals(orgId, linkageSnapshot.getOrgId())) {
            throw BizException.badRequest("关联预测单所属组织与采购计划所属组织不一致");
        }

        Map<Long, PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage> linkageItemMap = linkageSnapshot.toMaterialMap();
        if (linkageItemMap.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> requestedQtyMap = new LinkedHashMap<>();
        Map<Long, String> materialNameMap = new LinkedHashMap<>();
        for (PurchasePlanItem item : items) {
            if (item == null || item.getMaterialId() == null) {
                continue;
            }
            PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem = linkageItemMap.get(item.getMaterialId());
            if (linkageItem == null) {
                continue;
            }
            BigDecimal quantity = scaleQuantity(item.getPlanQty());
            requestedQtyMap.merge(item.getMaterialId(), quantity, BigDecimal::add);
            materialNameMap.putIfAbsent(item.getMaterialId(), item.getMaterialName());
        }

        for (Map.Entry<Long, BigDecimal> entry : requestedQtyMap.entrySet()) {
            PurchaseDemandForecastLinkageSupport.ForecastMaterialLinkage linkageItem = linkageItemMap.get(entry.getKey());
            if (linkageItem == null) {
                continue;
            }
            BigDecimal availableQty = scaleQuantity(linkageItem.getAvailableQty());
            BigDecimal requestedQty = scaleQuantity(entry.getValue());
            if (requestedQty.compareTo(availableQty) > 0) {
                String materialName = StrUtil.blankToDefault(materialNameMap.get(entry.getKey()), "当前物料");
                throw BizException.badRequest(
                        "物料“" + materialName + "”" + FORECAST_LINKAGE_QUANTITY_BLOCK_MESSAGE +
                                "（当前可关联数量：" + formatQuantityValue(availableQty) + "）"
                );
            }
        }
    }

    private void validateRecipePlanLinkedQuantities(
            String relatedDocument,
            Long orgId,
            List<PurchasePlanItemDTO> items,
            Long excludePlanId
    ) {
        String normalizedRelatedDocument = normalizeOptionalText(relatedDocument);
        if (normalizedRelatedDocument == null || items == null || items.isEmpty()) {
            return;
        }

        RecipePlanLinkageSupport.RecipePlanLinkageSnapshot linkageSnapshot =
                recipePlanLinkageSupport.loadByRecipePlanCode(
                        normalizedRelatedDocument,
                        resolveTenantId(),
                        excludePlanId,
                        true
                );
        if (linkageSnapshot == null) {
            return;
        }
        if (!Objects.equals(orgId, linkageSnapshot.getOrgId())) {
            throw BizException.badRequest("关联菜谱计划单所属组织与采购计划所属组织不一致");
        }

        Map<Long, RecipePlanLinkageSupport.RecipeMaterialLinkage> linkageItemMap = linkageSnapshot.toMaterialMap();
        if (linkageItemMap.isEmpty()) {
            return;
        }
        Map<Long, Map<String, Object>> resolvedMaterialMap = buildRecipePlanResolvedMaterialMap(linkageSnapshot, orgId);
        Map<Long, Long> resolvedMaterialIdToSourceMaterialId = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<String, Object>> entry : resolvedMaterialMap.entrySet()) {
            Long resolvedMaterialId = toLong(entry.getValue().get("id"));
            if (resolvedMaterialId != null) {
                resolvedMaterialIdToSourceMaterialId.putIfAbsent(resolvedMaterialId, entry.getKey());
            }
        }

        Map<Long, BigDecimal> requestedQtyMap = new LinkedHashMap<>();
        Map<Long, String> materialNameMap = new LinkedHashMap<>();
        for (PurchasePlanItemDTO item : items) {
            if (item == null || item.getMaterialId() == null) {
                continue;
            }
            Long sourceMaterialId = item.getMaterialId();
            RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem = linkageItemMap.get(sourceMaterialId);
            if (linkageItem == null) {
                Long mappedSourceMaterialId = resolvedMaterialIdToSourceMaterialId.get(item.getMaterialId());
                if (mappedSourceMaterialId != null) {
                    sourceMaterialId = mappedSourceMaterialId;
                    linkageItem = linkageItemMap.get(mappedSourceMaterialId);
                }
            }
            if (linkageItem == null) {
                continue;
            }
            BigDecimal quantity = scaleQuantity(item.getQuantity());
            requestedQtyMap.merge(sourceMaterialId, quantity, BigDecimal::add);
            materialNameMap.putIfAbsent(sourceMaterialId, linkageItem.getMaterialName());
        }

        for (Map.Entry<Long, BigDecimal> entry : requestedQtyMap.entrySet()) {
            RecipePlanLinkageSupport.RecipeMaterialLinkage linkageItem = linkageItemMap.get(entry.getKey());
            if (linkageItem == null) {
                continue;
            }
            BigDecimal availableQty = scaleQuantity(linkageItem.getAvailableQty());
            BigDecimal requestedQty = scaleQuantity(entry.getValue());
            if (requestedQty.compareTo(availableQty) > 0) {
                String materialName = StrUtil.blankToDefault(materialNameMap.get(entry.getKey()), "当前物料");
                throw BizException.badRequest(
                        "物料“" + materialName + "”" + FORECAST_LINKAGE_QUANTITY_BLOCK_MESSAGE +
                                "（当前可关联数量：" + formatQuantityValue(availableQty) + "）"
                );
            }
        }
    }

    private List<PurchasePlanAttachmentDTO> resolveSubmittedAttachments(List<PurchasePlanAttachmentDTO> attachments, MultipartFile file) {
        List<PurchasePlanAttachmentDTO> normalizedAttachments = normalizeSubmittedAttachments(attachments);
        if (!normalizedAttachments.isEmpty() || file == null || file.isEmpty()) {
            return normalizedAttachments;
        }

        validateAttachmentFile(file);
        PurchasePlanAttachmentDTO legacyAttachment = new PurchasePlanAttachmentDTO();
        legacyAttachment.setName(resolveAttachmentName(file));
        legacyAttachment.setSize(formatFileSize(file.getSize()));
        legacyAttachment.setUrl(fileStorageService.upload(file, ATTACHMENT_DIR));
        legacyAttachment.setSortOrder(1);
        return new ArrayList<>(Collections.singletonList(legacyAttachment));
    }

    private List<PurchasePlanAttachmentDTO> normalizeSubmittedAttachments(List<PurchasePlanAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }

        List<PurchasePlanAttachmentDTO> normalizedAttachments = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int sortOrder = 1;
        for (PurchasePlanAttachmentDTO attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String fileUrl = normalizeRequiredText(attachment.getUrl(), "附件地址不能为空");
            if (!seenUrls.add(fileUrl)) {
                continue;
            }

            PurchasePlanAttachmentDTO normalizedAttachment = new PurchasePlanAttachmentDTO();
            normalizedAttachment.setId(attachment.getId());
            normalizedAttachment.setName(normalizeRequiredText(attachment.getName(), "附件名称不能为空"));
            normalizedAttachment.setSize(normalizeOptionalText(attachment.getSize()));
            normalizedAttachment.setUrl(fileUrl);
            normalizedAttachment.setSortOrder(attachment.getSortOrder() != null && attachment.getSortOrder() > 0
                    ? attachment.getSortOrder()
                    : sortOrder);
            normalizedAttachments.add(normalizedAttachment);
            sortOrder += 1;
        }
        return normalizedAttachments;
    }

    private void syncLegacyAttachmentFields(PurchasePlan plan, List<PurchasePlanAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            plan.setAttachmentName(null);
            plan.setAttachmentUrl(null);
            return;
        }

        PurchasePlanAttachmentDTO firstAttachment = attachments.get(0);
        plan.setAttachmentName(firstAttachment.getName());
        plan.setAttachmentUrl(firstAttachment.getUrl());
    }

    private void syncNullableEditableFields(Long planId, PurchasePlan plan) {
        purchasePlanMapper.update(
                null,
                new LambdaUpdateWrapper<PurchasePlan>()
                        .eq(PurchasePlan::getId, planId)
                        .set(PurchasePlan::getRelatedDocument, plan.getRelatedDocument())
                        .set(PurchasePlan::getRemark, plan.getRemark())
                        .set(PurchasePlan::getAttachmentName, plan.getAttachmentName())
                        .set(PurchasePlan::getAttachmentUrl, plan.getAttachmentUrl())
        );
    }

    private void replacePlanAttachments(Long planId, Long orgId, Long tenantId, List<PurchasePlanAttachmentDTO> attachments) {
        purchasePlanAttachmentMapper.delete(new LambdaQueryWrapper<PurchasePlanAttachment>()
                .eq(PurchasePlanAttachment::getPlanId, planId));
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        int defaultSortOrder = 1;
        for (PurchasePlanAttachmentDTO attachment : attachments) {
            PurchasePlanAttachment entity = new PurchasePlanAttachment();
            entity.setPlanId(planId);
            entity.setFileName(attachment.getName());
            entity.setFileSize(normalizeOptionalText(attachment.getSize()));
            entity.setFileUrl(attachment.getUrl());
            entity.setSortOrder(attachment.getSortOrder() != null && attachment.getSortOrder() > 0
                    ? attachment.getSortOrder()
                    : defaultSortOrder);
            entity.setOrgId(orgId);
            entity.setTenantId(tenantId);
            purchasePlanAttachmentMapper.insert(entity);
            defaultSortOrder += 1;
        }
    }

    private List<PurchasePlanAttachmentVO> loadPlanAttachments(PurchasePlan plan) {
        if (plan == null || plan.getId() == null) {
            return new ArrayList<>();
        }
        List<PurchasePlanAttachmentVO> attachments = new ArrayList<>(
                loadPlanAttachmentMap(Collections.singletonList(plan.getId())).getOrDefault(plan.getId(), Collections.emptyList())
        );
        if (!attachments.isEmpty()) {
            return attachments;
        }
        if (StrUtil.isBlank(plan.getAttachmentUrl())) {
            return attachments;
        }

        PurchasePlanAttachmentVO legacyAttachment = new PurchasePlanAttachmentVO();
        legacyAttachment.setName(StrUtil.blankToDefault(plan.getAttachmentName(), "附件"));
        legacyAttachment.setUrl(plan.getAttachmentUrl());
        legacyAttachment.setSortOrder(1);
        attachments.add(legacyAttachment);
        return attachments;
    }

    private void deleteRemovedAttachmentUrls(List<String> beforeAttachmentUrls, List<PurchasePlanAttachmentDTO> currentAttachments) {
        if (beforeAttachmentUrls == null || beforeAttachmentUrls.isEmpty()) {
            return;
        }

        Set<String> currentUrls = new LinkedHashSet<>();
        if (currentAttachments != null) {
            for (PurchasePlanAttachmentDTO attachment : currentAttachments) {
                String fileUrl = normalizeOptionalText(attachment == null ? null : attachment.getUrl());
                if (fileUrl != null) {
                    currentUrls.add(fileUrl);
                }
            }
        }
        List<String> removedUrls = beforeAttachmentUrls.stream()
                .filter(url -> !currentUrls.contains(url))
                .distinct()
                .toList();
        deleteAttachmentUrls(removedUrls);
    }

    private List<String> loadPersistedAttachmentUrls(PurchasePlan plan) {
        if (plan == null) {
            return Collections.emptyList();
        }

        List<String> attachmentUrls = loadPlanAttachments(plan).stream()
                .map(PurchasePlanAttachmentVO::getUrl)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        return attachmentUrls;
    }

    private void deleteAttachmentUrls(List<String> attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }
        attachmentUrls.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .forEach(fileStorageService::delete);
    }

    private void applyGeneratedOrderAttachment(PurchaseOrder order, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateAttachmentFile(file);
        order.setAttachmentName(resolveAttachmentName(file));
        order.setAttachmentUrl(fileStorageService.upload(file, PURCHASE_ORDER_ATTACHMENT_DIR));
    }

    private List<PurchasePlanItem> buildPlanItems(List<PurchasePlanItemDTO> itemDTOs, Long orgId, String relatedDocument) {
        if (itemDTOs == null || itemDTOs.isEmpty()) {
            throw BizException.badRequest("请至少填写一条物料明细");
        }

        Map<Long, Map<String, Object>> recipePlanResolvedMaterialMap =
                loadRecipePlanResolvedMaterialMap(relatedDocument, orgId, null);
        List<PurchasePlanItem> items = new ArrayList<>();
        for (PurchasePlanItemDTO dto : itemDTOs) {
            Map<String, Object> material = findMaterial(dto.getMaterialId(), orgId);
            if (material == null && dto.getMaterialId() != null) {
                material = recipePlanResolvedMaterialMap.get(dto.getMaterialId());
            }
            if (material == null) {
                throw BizException.badRequest("所选物料不存在或已停用");
            }

            PurchasePlanItem item = new PurchasePlanItem();
            item.setMaterialId(toLong(material.get("id")));
            item.setMaterialName(asString(material.get("material_name")));
            item.setMaterialSpec(StrUtil.isNotBlank(dto.getMaterialSpec())
                    ? dto.getMaterialSpec().trim()
                    : asString(material.get("spec")));
            item.setMaterialUnit(asString(material.get("unit")));
            item.setPlanQty(scaleQuantity(dto.getQuantity()));
            item.setEstimatePrice(scalePrice(dto.getUnitPrice()));
            item.setEstimateAmount(scaleAmount(item.getPlanQty().multiply(item.getEstimatePrice())));
            item.setRemark(StrUtil.isBlank(dto.getRemark()) ? null : dto.getRemark().trim());
            items.add(item);
        }
        return items;
    }

    private void savePlanItems(Long planId, List<PurchasePlanItem> items) {
        for (PurchasePlanItem item : items) {
            item.setPlanId(planId);
            purchasePlanItemMapper.insert(item);
        }
    }

    private Map<Long, List<PurchasePlanAttachmentVO>> loadPlanAttachmentMap(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT id, plan_id AS planId, file_name AS fileName, file_size AS fileSize, " +
                "file_url AS fileUrl, sort_order AS sortOrder " +
                "FROM scm_purchase_plan_attachment " +
                "WHERE deleted = 0 AND plan_id IN (" + placeholders(planIds.size()) + ") " +
                "ORDER BY plan_id ASC, sort_order ASC, id ASC";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, planIds.toArray());
        Map<Long, List<PurchasePlanAttachmentVO>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            PurchasePlanAttachmentVO vo = new PurchasePlanAttachmentVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(asString(row.get("fileName")));
            vo.setSize(asString(row.get("fileSize")));
            vo.setUrl(asString(row.get("fileUrl")));
            vo.setSortOrder(toInteger(row.get("sortOrder")));
            map.computeIfAbsent(toLong(row.get("planId")), key -> new ArrayList<>()).add(vo);
        }
        return map;
    }

    private Map<Long, List<PurchasePlanItemVO>> loadPlanItems(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholder = placeholders(planIds.size());
        String sql = "SELECT i.id, i.plan_id AS planId, " +
                "i.material_id AS materialId, i.material_name AS materialName, i.material_spec AS materialSpec, " +
                "i.material_unit AS unit, i.plan_qty AS quantity, i.estimate_price AS unitPrice, " +
                "i.estimate_amount AS subtotal, COALESCE(gen.ordered_qty, 0) AS orderedQuantity, i.remark " +
                "FROM scm_purchase_plan_item i " +
                "LEFT JOIN ( " +
                "    SELECT oi.plan_item_id, SUM(oi.order_qty) AS ordered_qty " +
                "    FROM scm_purchase_order_item oi " +
                "    JOIN scm_purchase_order o ON o.id = oi.order_id AND o.deleted = 0 " +
                "    JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id AND pi.plan_id IN (" + placeholder + ") " +
                "    WHERE oi.plan_item_id IS NOT NULL " +
                "    GROUP BY oi.plan_item_id " +
                ") gen ON gen.plan_item_id = i.id " +
                "WHERE i.plan_id IN (" + placeholder + ") " +
                "ORDER BY i.plan_id ASC, i.id ASC";

        List<Object> args = new ArrayList<>(planIds.size() * 2);
        args.addAll(planIds);
        args.addAll(planIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<Long, List<PurchasePlanItemVO>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            PurchasePlanItemVO vo = new PurchasePlanItemVO();
            vo.setId(toLong(row.get("id")));
            vo.setMaterialId(toLong(row.get("materialId")));
            vo.setMaterialName(asString(row.get("materialName")));
            vo.setMaterialSpec(asString(row.get("materialSpec")));
            vo.setUnit(asString(row.get("unit")));
            vo.setQuantity(scaleQuantity(toBigDecimal(row.get("quantity"))));
            vo.setUnitPrice(scalePrice(toBigDecimal(row.get("unitPrice"))));
            vo.setSubtotal(scaleAmount(toBigDecimal(row.get("subtotal"))));
            vo.setOrderedQuantity(scaleQuantity(toBigDecimal(row.get("orderedQuantity"))));
            vo.setRemark(asString(row.get("remark")));
            map.computeIfAbsent(toLong(row.get("planId")), key -> new ArrayList<>()).add(vo);
        }
        return map;
    }

    private Map<Long, Integer> loadGeneratedOrderCountMap(List<Long> planIds) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholder = placeholders(planIds.size());
        String sql = "SELECT link.planId, COUNT(DISTINCT link.orderId) AS orderCount " +
                "FROM ( " +
                "    SELECT po.plan_id AS planId, po.id AS orderId " +
                "    FROM scm_purchase_order po " +
                "    WHERE po.deleted = 0 AND po.plan_id IN (" + placeholder + ") " +
                "    UNION " +
                "    SELECT pi.plan_id AS planId, oi.order_id AS orderId " +
                "    FROM scm_purchase_order_item oi " +
                "    JOIN scm_purchase_order po ON po.id = oi.order_id AND po.deleted = 0 " +
                "    JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                "    WHERE pi.plan_id IN (" + placeholder + ") " +
                "    UNION " +
                "    SELECT p.id AS planId, p.merge_order_id AS orderId " +
                "    FROM scm_purchase_plan p " +
                "    JOIN scm_purchase_order po ON po.id = p.merge_order_id AND po.deleted = 0 " +
                "    WHERE p.id IN (" + placeholder + ") AND p.merge_order_id IS NOT NULL " +
                ") link " +
                "GROUP BY link.planId";
        List<Object> args = new ArrayList<>(planIds.size() * 3);
        args.addAll(planIds);
        args.addAll(planIds);
        args.addAll(planIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        Map<Long, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(toLong(row.get("planId")), toInteger(row.get("orderCount")));
        }
        return map;
    }

    private List<PurchaseOrderLinkVO> loadOrderLinks(Long planId, Map<Long, String> operatorCache) {
        List<Map<String, Object>> orderRows = jdbcTemplate.queryForList(
                "SELECT DISTINCT po.id, po.order_no AS orderNo, po.created_at AS createdAt, po.created_by AS createdById " +
                        "FROM scm_purchase_order po " +
                        "LEFT JOIN scm_purchase_order_item oi ON oi.order_id = po.id " +
                        "LEFT JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                        "LEFT JOIN scm_purchase_plan p ON p.id = ? AND p.merge_order_id = po.id " +
                        "WHERE po.deleted = 0 AND (po.plan_id = ? OR pi.plan_id = ? OR p.id IS NOT NULL) " +
                        "ORDER BY po.created_at DESC, po.id DESC",
                planId,
                planId,
                planId
        );
        if (orderRows.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> orderIds = new ArrayList<>();
        Map<Long, PurchaseOrderLinkVO> orderMap = new LinkedHashMap<>();
        for (Map<String, Object> row : orderRows) {
            PurchaseOrderLinkVO vo = new PurchaseOrderLinkVO();
            Long orderId = toLong(row.get("id"));
            vo.setId(orderId);
            vo.setOrderNo(asString(row.get("orderNo")));
            vo.setCreatedAt(formatDateTime(row.get("createdAt")));
            vo.setCreatedBy(resolveOperatorName(toLong(row.get("createdById")), operatorCache));
            orderIds.add(orderId);
            orderMap.put(orderId, vo);
        }

        String itemSql = "SELECT id, order_id AS orderId, plan_item_id AS planItemId, material_name AS materialName, " +
                "material_spec AS materialSpec, material_unit AS unit, order_qty AS quantity, unit_price AS unitPrice, " +
                "total_amount AS subtotal, remark FROM scm_purchase_order_item WHERE order_id IN (" +
                placeholders(orderIds.size()) + ") ORDER BY order_id ASC, id ASC";
        List<Map<String, Object>> itemRows = jdbcTemplate.queryForList(itemSql, orderIds.toArray());
        for (Map<String, Object> row : itemRows) {
            PurchaseOrderLinkItemVO item = new PurchaseOrderLinkItemVO();
            item.setId(toLong(row.get("id")));
            item.setPlanItemId(toLong(row.get("planItemId")));
            item.setMaterialName(asString(row.get("materialName")));
            item.setMaterialSpec(asString(row.get("materialSpec")));
            item.setUnit(asString(row.get("unit")));
            item.setQuantity(scaleQuantity(toBigDecimal(row.get("quantity"))));
            item.setUnitPrice(scalePrice(toBigDecimal(row.get("unitPrice"))));
            item.setSubtotal(scaleAmount(toBigDecimal(row.get("subtotal"))));
            item.setRemark(asString(row.get("remark")));

            PurchaseOrderLinkVO order = orderMap.get(toLong(row.get("orderId")));
            if (order != null) {
                order.getItems().add(item);
            }
        }
        return new ArrayList<>(orderMap.values());
    }

    private List<PurchasePlanLinkedOrderRecordVO> loadLinkedPurchaseOrders(PurchasePlan plan) {
        if (plan == null || plan.getId() == null) {
            return Collections.emptyList();
        }
        List<LinkedPurchaseOrderSummary> summaries = loadLinkedPurchaseOrderSummaries(plan, LINKED_PURCHASE_ORDER_ORDER_LIMIT);
        if (summaries.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, LinkedPurchaseOrderSummary> summaryMap = new LinkedHashMap<>();
        Map<Long, Integer> orderPositionMap = new HashMap<>();
        List<Long> directOrderIds = new ArrayList<>();
        List<Long> indirectOrderIds = new ArrayList<>();
        for (int index = 0; index < summaries.size(); index++) {
            LinkedPurchaseOrderSummary summary = summaries.get(index);
            if (summary == null || summary.orderId() == null) {
                continue;
            }
            summaryMap.put(summary.orderId(), summary);
            orderPositionMap.put(summary.orderId(), index);
            if (summary.directLink()) {
                directOrderIds.add(summary.orderId());
            } else {
                indirectOrderIds.add(summary.orderId());
            }
        }
        if (summaryMap.isEmpty()) {
            return Collections.emptyList();
        }

        List<PurchasePlanLinkedOrderRecordVO> records = new ArrayList<>(Math.min(LINKED_PURCHASE_ORDER_ROW_LIMIT, summaryMap.size() * 4));
        appendLinkedPurchaseOrderRecords(records, summaryMap, buildDirectLinkedPurchaseOrderItemsSql(directOrderIds), buildDirectLinkedPurchaseOrderItemsArgs(directOrderIds));
        if (records.size() < LINKED_PURCHASE_ORDER_ROW_LIMIT) {
            appendLinkedPurchaseOrderRecords(
                    records,
                    summaryMap,
                    buildIndirectLinkedPurchaseOrderItemsSql(indirectOrderIds),
                    buildIndirectLinkedPurchaseOrderItemsArgs(plan.getId(), indirectOrderIds, LINKED_PURCHASE_ORDER_ROW_LIMIT - records.size())
            );
        }

        if (records.size() > 1) {
            records.sort(Comparator.comparingInt(record -> orderPositionMap.getOrDefault(record.getOrderId(), Integer.MAX_VALUE)));
        }
        if (records.size() > LINKED_PURCHASE_ORDER_ROW_LIMIT) {
            return new ArrayList<>(records.subList(0, LINKED_PURCHASE_ORDER_ROW_LIMIT));
        }
        return records;
    }

    private List<LinkedPurchaseOrderSummary> loadLinkedPurchaseOrderSummaries(PurchasePlan plan, int limit) {
        if (plan == null || plan.getId() == null || limit <= 0) {
            return Collections.emptyList();
        }
        Long tenantId = plan.getTenantId() == null || plan.getTenantId() == 0L ? resolveTenantId() : plan.getTenantId();
        String sql = "SELECT summary.orderId, summary.orderNo, summary.status, summary.createdAt, summary.createdById, summary.directLink, " +
                "COALESCE(NULLIF(TRIM(se.real_name), ''), NULLIF(TRIM(au.real_name), ''), " +
                "NULLIF(TRIM(au.username), ''), CAST(summary.createdById AS CHAR)) AS operatorName " +
                "FROM ( " +
                "    SELECT linked.orderId, linked.orderNo, linked.status, linked.createdAt, linked.createdById, MAX(linked.directLink) AS directLink " +
                "    FROM ( " +
                "        SELECT po.id AS orderId, po.order_no AS orderNo, po.status, po.created_at AS createdAt, po.created_by AS createdById, 1 AS directLink " +
                "        FROM scm_purchase_order po " +
                "        WHERE po.deleted = 0 AND po.tenant_id = ? AND po.plan_id = ? " +
                "        UNION ALL " +
                "        SELECT po.id AS orderId, po.order_no AS orderNo, po.status, po.created_at AS createdAt, po.created_by AS createdById, 0 AS directLink " +
                "        FROM scm_purchase_plan_item pi " +
                "        JOIN scm_purchase_order_item poi ON poi.plan_item_id = pi.id " +
                "        JOIN scm_purchase_order po ON po.id = poi.order_id AND po.deleted = 0 AND po.tenant_id = ? " +
                "        WHERE pi.plan_id = ? " +
                "    ) linked " +
                "    GROUP BY linked.orderId, linked.orderNo, linked.status, linked.createdAt, linked.createdById " +
                "    ORDER BY linked.createdAt DESC, linked.orderId DESC " +
                "    LIMIT ? " +
                ") summary " +
                "LEFT JOIN auth_user au ON au.id = summary.createdById AND au.deleted = 0 " +
                "LEFT JOIN sys_employee se ON se.user_id = au.id AND se.deleted = 0 " +
                "ORDER BY summary.createdAt DESC, summary.orderId DESC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, tenantId, plan.getId(), tenantId, plan.getId(), limit);
        List<LinkedPurchaseOrderSummary> summaries = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long orderId = toLong(row.get("orderId"));
            if (orderId == null) {
                continue;
            }
            summaries.add(new LinkedPurchaseOrderSummary(
                    orderId,
                    asString(row.get("orderNo")),
                    asString(row.get("status")),
                    formatDateTime(row.get("createdAt")),
                    asString(row.get("operatorName")),
                    toInteger(row.get("directLink")) != null && toInteger(row.get("directLink")) > 0
            ));
        }
        return summaries;
    }

    private String buildDirectLinkedPurchaseOrderItemsSql(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }
        return "SELECT order_id AS orderId, material_name AS materialName, material_spec AS materialSpec, " +
                "material_unit AS unit, order_qty AS quantity " +
                "FROM scm_purchase_order_item WHERE order_id IN (" + placeholders(orderIds.size()) + ") " +
                "ORDER BY order_id ASC, id ASC LIMIT ?";
    }

    private List<Object> buildDirectLinkedPurchaseOrderItemsArgs(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>(orderIds.size() + 1);
        args.addAll(orderIds);
        args.add(LINKED_PURCHASE_ORDER_ROW_LIMIT);
        return args;
    }

    private String buildIndirectLinkedPurchaseOrderItemsSql(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return null;
        }
        return "SELECT poi.order_id AS orderId, poi.material_name AS materialName, poi.material_spec AS materialSpec, " +
                "poi.material_unit AS unit, poi.order_qty AS quantity " +
                "FROM scm_purchase_plan_item pi " +
                "JOIN scm_purchase_order_item poi ON poi.plan_item_id = pi.id " +
                "WHERE pi.plan_id = ? AND poi.order_id IN (" + placeholders(orderIds.size()) + ") " +
                "ORDER BY poi.order_id ASC, poi.id ASC LIMIT ?";
    }

    private List<Object> buildIndirectLinkedPurchaseOrderItemsArgs(Long planId, List<Long> orderIds, int limit) {
        if (planId == null || orderIds == null || orderIds.isEmpty() || limit <= 0) {
            return Collections.emptyList();
        }
        List<Object> args = new ArrayList<>(orderIds.size() + 2);
        args.add(planId);
        args.addAll(orderIds);
        args.add(limit);
        return args;
    }

    private void appendLinkedPurchaseOrderRecords(
            List<PurchasePlanLinkedOrderRecordVO> records,
            Map<Long, LinkedPurchaseOrderSummary> summaryMap,
            String sql,
            List<Object> args
    ) {
        if (records == null || summaryMap == null || summaryMap.isEmpty() || StrUtil.isBlank(sql) || args == null || args.isEmpty()) {
            return;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args.toArray());
        for (Map<String, Object> row : rows) {
            if (records.size() >= LINKED_PURCHASE_ORDER_ROW_LIMIT) {
                return;
            }
            Long orderId = toLong(row.get("orderId"));
            LinkedPurchaseOrderSummary summary = summaryMap.get(orderId);
            if (summary == null || orderId == null) {
                continue;
            }
            PurchasePlanLinkedOrderRecordVO record = new PurchasePlanLinkedOrderRecordVO();
            record.setOrderId(orderId);
            record.setOrderNo(summary.orderNo());
            record.setStatus(summary.status());
            record.setMaterialName(asString(row.get("materialName")));
            record.setMaterialSpec(asString(row.get("materialSpec")));
            record.setUnit(asString(row.get("unit")));
            record.setQuantity(scaleQuantity(toBigDecimal(row.get("quantity"))));
            record.setOperatorName(summary.operatorName());
            record.setCreatedAt(summary.createdAt());
            records.add(record);
        }
    }

    private record LinkedPurchaseOrderSummary(
            Long orderId,
            String orderNo,
            String status,
            String createdAt,
            String operatorName,
            boolean directLink
    ) {
    }

    private String resolvePlanNo(String requestedPlanNo, Long excludeId) {
        String candidate = StrUtil.isBlank(requestedPlanNo) ? generatePlanNo() : requestedPlanNo.trim();
        if (!existsPlanNo(candidate, excludeId)) {
            return candidate;
        }

        String generated;
        do {
            generated = generatePlanNo();
        } while (existsPlanNo(generated, excludeId));
        return generated;
    }

    private boolean existsPlanNo(String planNo, Long excludeId) {
        LambdaQueryWrapper<PurchasePlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchasePlan::getPlanNo, planNo)
                .ne(excludeId != null, PurchasePlan::getId, excludeId);
        Long count = purchasePlanMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private String generatePlanNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
        return "PP-" + datePart + "-" + suffix;
    }

    private String generateUniqueOrderNo() {
        String candidate;
        do {
            String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
            candidate = "PO-" + datePart + "-" + suffix;
        } while (existsOrderNo(candidate));
        return candidate;
    }

    private boolean existsOrderNo(String orderNo) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_order WHERE deleted = 0 AND order_no = ?",
                Long.class,
                orderNo
        );
        return count != null && count > 0;
    }

    private void ensureEditable(String status) {
        if (!STATUS_DRAFT.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw BizException.badRequest("仅草稿或已驳回状态可编辑");
        }
    }

    private String normalizeEditableStatus(String status) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(status), STATUS_DRAFT);
        if (!STATUS_DRAFT.equals(normalized) && !STATUS_PENDING.equals(normalized)) {
            throw BizException.badRequest("采购计划仅支持保存为草稿或提交待审核");
        }
        return normalized;
    }

    private String normalizeAuditStatus(String status) {
        String normalized = StrUtil.trim(status);
        if (!STATUS_APPROVED.equals(normalized) && !STATUS_REJECTED.equals(normalized)) {
            throw BizException.badRequest("审核状态仅支持 approved 或 rejected");
        }
        return normalized;
    }

    private void ensureVoidAuditable(String status) {
        if (!STATUS_PENDING_VOID_APPROVE.equals(status)) {
            throw BizException.badRequest("仅待作废审核状态可执行作废审核");
        }
    }

    private void ensurePermission(String permissionCode, String errorMessage) {
        if (dataScopeService.isAdminUser()) {
            return;
        }

        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden(errorMessage);
        }

        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " +
                        "FROM auth_user_role ur " +
                        "JOIN auth_role r ON r.id = ur.role_id " +
                        "JOIN auth_role_permission rp ON rp.role_id = r.id " +
                        "JOIN auth_permission p ON p.id = rp.permission_id " +
                        "WHERE ur.user_id = ? " +
                        "  AND r.deleted = 0 " +
                        "  AND r.status = 'active' " +
                        "  AND p.status = 'active' " +
                        "  AND p.permission_code = ?",
                Long.class,
                userId,
                permissionCode
        );
        if (count == null || count <= 0L) {
            throw BizException.forbidden(errorMessage);
        }
    }

    private Map<String, Object> findMaterial(Long materialId, Long orgId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, material_name, unit, spec FROM wms_material " +
                        "WHERE id = ? AND org_id = ? AND deleted = 0 AND status = 'active' LIMIT 1",
                materialId, orgId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> fetchMaterial(Long materialId, Long orgId) {
        Map<String, Object> material = findMaterial(materialId, orgId);
        if (material == null) {
            throw BizException.badRequest("所选物料不存在或已停用");
        }
        return material;
    }

    private Map<String, Object> fetchSupplier(Long supplierId, Long orgId, Long planId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, supplier_name, status, license_expires_at AS licenseExpiresAt, " +
                        "food_license_expires_at AS foodLicenseExpiresAt " +
                        "FROM scm_supplier WHERE id = ? AND org_id = ? AND deleted = 0 LIMIT 1",
                supplierId, orgId
        );
        if (rows.isEmpty()) {
            throw BizException.badRequest("所选供应商不存在或未审核");
        }
        Map<String, Object> supplier = rows.get(0);
        if (!SUPPLIER_STATUS_ACTIVE.equals(asString(supplier.get("status")))) {
            throw BizException.badRequest("所选供应商不存在或未审核");
        }
        if (isSupplierQualificationExpired(supplier)) {
            logSupplierQualificationBlocked(planId, supplier);
            throw BizException.badRequest(SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE);
        }
        return supplier;
    }

    private String fetchSupplierName(Long supplierId, Long orgId, Long planId) {
        return asString(fetchSupplier(supplierId, orgId, planId).get("supplier_name"));
    }

    private boolean isSupplierQualificationExpired(Map<String, Object> supplier) {
        LocalDateTime now = LocalDateTime.now();
        return isExpiredAt(toLocalDateTime(supplier.get("licenseExpiresAt")), now)
                || isExpiredAt(toLocalDateTime(supplier.get("foodLicenseExpiresAt")), now);
    }

    private boolean isExpiredAt(LocalDateTime value, LocalDateTime now) {
        return value != null && value.isBefore(now);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return null;
    }

    private void logSupplierQualificationBlocked(Long planId, Map<String, Object> supplier) {
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("supplierId", toLong(supplier.get("id")));
        auditData.put("supplierName", asString(supplier.get("supplier_name")));
        auditData.put("licenseExpiresAt", formatDate(supplier.get("licenseExpiresAt")));
        auditData.put("foodLicenseExpiresAt", formatDate(supplier.get("foodLicenseExpiresAt")));
        auditData.put("rule", "supplier_qualification_expired_block");
        auditLogService.log(
                AuditModule.SCM_PURCHASE_PLAN,
                AuditOperationType.CREATE,
                planId,
                null,
                "采购计划关联生成采购订单失败：供应商关键资质已过期",
                null,
                toJson(auditData),
                "failure",
                SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE
        );
    }

    private String resolveOrgName(Long orgId) {
        if (orgId == null) {
            return null;
        }
        List<String> names = jdbcTemplate.query(
                "SELECT NULLIF(TRIM(org_name), '') AS org_name FROM sys_organization " +
                        "WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("org_name"),
                orgId
        );
        return names.isEmpty() ? null : names.get(0);
    }

    private String resolveOperatorName(Long userId, Map<Long, String> cache) {
        if (userId == null) {
            return null;
        }
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }

        List<String> userNames = jdbcTemplate.query(
                "SELECT COALESCE(NULLIF(TRIM(e.real_name), ''), NULLIF(TRIM(u.real_name), ''), NULLIF(TRIM(u.username), '')) AS operator_name " +
                        "FROM auth_user u " +
                        "LEFT JOIN sys_employee e ON e.user_id = u.id AND e.deleted = 0 " +
                        "WHERE u.id = ? AND u.deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("operator_name"),
                userId
        );
        String name = !userNames.isEmpty() && StrUtil.isNotBlank(userNames.get(0))
                ? userNames.get(0).trim()
                : null;
        if (name == null) {
            List<String> employeeNames = jdbcTemplate.query(
                    "SELECT NULLIF(TRIM(real_name), '') AS operator_name FROM sys_employee " +
                            "WHERE id = ? AND deleted = 0 LIMIT 1",
                    (rs, rowNum) -> rs.getString("operator_name"),
                    userId
            );
            if (!employeeNames.isEmpty() && StrUtil.isNotBlank(employeeNames.get(0))) {
                name = employeeNames.get(0).trim();
            }
        }
        cache.put(userId, name);
        return name;
    }

    private boolean isAllItemsGenerated(List<PurchasePlanItemVO> items) {
        return !items.isEmpty() && items.stream().allMatch(item -> remainingQuantity(item).compareTo(BigDecimal.ZERO) <= 0);
    }

    private BigDecimal remainingQuantity(PurchasePlanItemVO item) {
        return scaleQuantity(zeroIfNull(item.getQuantity()).subtract(zeroIfNull(item.getOrderedQuantity())).max(BigDecimal.ZERO));
    }

    private BigDecimal sumEstimateAmount(List<PurchasePlanItem> items) {
        return items.stream()
                .map(PurchasePlanItem::getEstimateAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private List<Long> resolveAllowedOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (scope.isAllAccess()) {
            return null;
        }
        return new ArrayList<>(scope.getOrgIds());
    }

    private Long resolveRequestedOrgId(Long requestOrgId, List<Long> allowedOrgIds) {
        if (requestOrgId != null) {
            if (allowedOrgIds == null || allowedOrgIds.contains(requestOrgId)) {
                return requestOrgId;
            }
            return null;
        }

        if (allowedOrgIds == null) {
            return null;
        }
        return null;
    }

    private Long resolveCurrentUserRelatedDocumentOrgId(Long requestOrgId) {
        Long currentOrgId = resolveCurrentUserOrgId();
        if (currentOrgId == null) {
            return null;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(currentOrgId)) {
            return null;
        }
        if (requestOrgId != null && !Objects.equals(requestOrgId, currentOrgId)) {
            return null;
        }
        return currentOrgId;
    }

    private Long requireCurrentUserRelatedDocumentOrgId() {
        Long currentOrgId = resolveCurrentUserRelatedDocumentOrgId(null);
        if (currentOrgId == null) {
            throw BizException.forbidden("无权访问当前组织关联单据");
        }
        return currentOrgId;
    }

    private Long resolveCurrentUserOrgId() {
        Long currentOrgId = UserContext.getOrgId();
        if (currentOrgId != null && currentOrgId > 0L) {
            return currentOrgId;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return null;
        }
        List<Long> orgIds = jdbcTemplate.query(
                "SELECT COALESCE(u.org_id, e.org_id) AS org_id " +
                        "FROM auth_user u " +
                        "LEFT JOIN sys_employee e ON e.user_id = u.id AND e.deleted = 0 " +
                        "WHERE u.id = ? AND u.deleted = 0 LIMIT 1",
                (rs, rowNum) -> {
                    long value = rs.getLong("org_id");
                    return rs.wasNull() ? null : value;
                },
                userId
        );
        if (orgIds.isEmpty()) {
            return null;
        }
        Long dbOrgId = orgIds.get(0);
        return dbOrgId != null && dbOrgId > 0L ? dbOrgId : null;
    }

    private Long requireManageableOrgId(Long orgId) {
        if (orgId == null) {
            throw BizException.badRequest("所属组织不能为空");
        }
        ensureOrgAllowed(orgId);
        return orgId;
    }

    private void ensureOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private void ensureReverseAuditOrgAllowed(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden(REVERSE_AUDIT_PERMISSION_MESSAGE);
        }
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
    }

    private Long resolveCurrentUserId() {
        return UserContext.getUserId() != null ? UserContext.getUserId() : DEFAULT_USER_ID;
    }

    private void ensurePurchasePlanVoidColumn(String columnName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scm_purchase_plan' AND COLUMN_NAME = ?",
                Integer.class,
                columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    private String resolveStatusAfterVoidReject(PurchasePlan plan) {
        String originalStatus = normalizeOptionalText(plan == null ? null : plan.getVoidOriginStatus());
        if (STATUS_APPROVED.equals(originalStatus) || STATUS_REJECTED.equals(originalStatus)) {
            return originalStatus;
        }

        String fallbackStatus = findStatusBeforeVoidApply(plan);
        if (STATUS_APPROVED.equals(fallbackStatus) || STATUS_REJECTED.equals(fallbackStatus)) {
            return fallbackStatus;
        }

        log.warn("采购计划作废驳回未找到原始状态快照，按已审核兜底恢复: id={}", plan == null ? null : plan.getId());
        return STATUS_APPROVED;
    }

    private String findStatusBeforeVoidApply(PurchasePlan plan) {
        if (plan == null || plan.getId() == null) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT before_data FROM sys_audit_log " +
                "WHERE module_code = ? AND target_id = ? AND operation_desc = ? AND result = ?");
        List<Object> args = new ArrayList<>();
        args.add(AuditModule.SCM_PURCHASE_PLAN.getCode());
        args.add(plan.getId());
        args.add("发起采购计划作废申请");
        args.add("success");
        if (plan.getVoidRequestedAt() != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.valueOf(plan.getVoidRequestedAt().minusSeconds(1)));
        }
        sql.append(" ORDER BY id DESC LIMIT 1");
        List<String> beforeDataList = jdbcTemplate.query(
                sql.toString(),
                (rs, rowNum) -> rs.getString("before_data"),
                args.toArray()
        );
        if (beforeDataList.isEmpty()) {
            return null;
        }
        String beforeData = beforeDataList.get(0);
        if (StrUtil.isBlank(beforeData)) {
            return null;
        }
        try {
            return objectMapper.readTree(beforeData).path("status").asText(null);
        } catch (Exception ex) {
            log.warn("采购计划作废前状态审计快照解析失败: {}", ex.getMessage());
            return null;
        }
    }

    private LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            throw BizException.badRequest("计划日期不能为空");
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("计划日期格式错误，正确格式应为 yyyy-MM-dd");
        }
    }

    private LocalDate parseLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        if (value instanceof Date date) {
            return date.toLocalDate();
        }
        String text = StrUtil.trim(String.valueOf(value));
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(text, DATE_TIME_FORMATTER).toLocalDate();
            } catch (DateTimeParseException ignored) {
                return LocalDate.parse(text);
            }
        }
    }

    private String formatDate(LocalDate value) {
        return value == null ? null : value.format(DATE_FORMATTER);
    }

    private String formatDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return formatDate(localDate);
        }
        if (value instanceof Date date) {
            return date.toLocalDate().format(DATE_FORMATTER);
        }
        return value.toString();
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }

    private String formatDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return formatDateTime(localDateTime);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
        }
        return value.toString();
    }

    private LocalDateTime parseLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        String text = StrUtil.trim(String.valueOf(value));
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
            }
        }
    }

    private boolean isDeleted(PurchasePlan plan) {
        return plan != null && plan.getDeleted() != null && plan.getDeleted() != 0;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String resolveAttachmentName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        return StrUtil.isBlank(originalFilename) ? "attachment" : originalFilename.trim();
    }

    private void validateAttachmentFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("上传文件不能为空");
        }
        if (file.getSize() > MAX_ATTACHMENT_FILE_SIZE) {
            throw BizException.badRequest("文件大小不能超过10MB");
        }
    }

    private String formatFileSize(long fileSize) {
        if (fileSize >= 1024L * 1024) {
            return String.format("%.2f MB", fileSize / 1024D / 1024D);
        }
        if (fileSize >= 1024L) {
            return String.format("%.2f KB", fileSize / 1024D);
        }
        return fileSize + " B";
    }

    private String normalizeOptionalText(String value) {
        String normalized = StrUtil.trim(value);
        return StrUtil.isBlank(normalized) ? null : normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw BizException.badRequest(message);
        }
        return normalized;
    }

    private void writeAttachment(String attachmentName, String attachmentUrl, HttpServletResponse response) {
        if (StrUtil.isBlank(attachmentUrl)) {
            throw BizException.notFound("附件不存在");
        }

        FileStorageService.StoredFile storedFile = fileStorageService.download(attachmentUrl);
        String fileName = StrUtil.blankToDefault(attachmentName, "attachment");
        try (InputStream inputStream = storedFile.inputStream()) {
            response.setContentType(
                    storedFile.contentType() == null || storedFile.contentType().isBlank()
                            ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                            : storedFile.contentType()
            );
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            if (storedFile.size() != null) {
                response.setContentLengthLong(storedFile.size());
            }
            response.setHeader(
                    "Content-Disposition",
                    "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20")
            );
            StreamUtils.copy(inputStream, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            throw new RuntimeException("附件下载失败", e);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("采购计划附件审计数据序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private BigDecimal resolveGeneratedOrderUnitPrice(PurchasePlanGenerateOrderDTO.Item item, PurchasePlanItemVO planItem) {
        if (item != null && item.getUnitPrice() != null) {
            return scalePrice(item.getUnitPrice());
        }
        return scalePrice(planItem == null ? null : planItem.getUnitPrice());
    }

    private BigDecimal resolveGeneratedOrderSubtotal(PurchasePlanGenerateOrderDTO.Item item, BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal calculatedSubtotal = scaleAmount((quantity == null ? BigDecimal.ZERO : quantity)
                .multiply(unitPrice == null ? BigDecimal.ZERO : unitPrice));
        if (item == null || item.getSubtotal() == null) {
            return calculatedSubtotal;
        }
        BigDecimal submittedSubtotal = scaleAmount(item.getSubtotal());
        return submittedSubtotal.compareTo(calculatedSubtotal) == 0 ? submittedSubtotal : calculatedSubtotal;
    }

    private BigDecimal scaleAmount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private String formatQuantityValue(BigDecimal value) {
        BigDecimal normalized = scaleQuantity(value).stripTrailingZeros();
        return normalized.scale() < 0 ? normalized.setScale(0, RoundingMode.HALF_UP).toPlainString() : normalized.toPlainString();
    }

    private record PurchasePlanDeleteCheckResult(
            int linkedPurchaseOrderCount,
            int unfinishedInboundOrderCount,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

    private record PurchasePlanDeleteRecheckSnapshot(
            PurchasePlan plan,
            PurchasePlanDeleteCheckResult checkResult
    ) {
    }

    private record PurchasePlanVoidCheckResult(
            int linkedPurchaseOrderCount,
            int unfinishedInboundOrderCount,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

    private record PurchasePlanVoidRecheckSnapshot(
            PurchasePlan plan,
            PurchasePlanVoidCheckResult checkResult
    ) {
    }

    private record PurchasePlanReverseAuditCheckResult(
            int linkedPurchaseOrderCount,
            int linkedInboundOrderCount,
            int linkedReceiptRecordCount,
            List<LinkedPurchaseOrderState> linkedOrders,
            List<LinkedPurchaseOrderState> blockedOrders,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

    private record PurchasePlanReverseAuditRecheckSnapshot(
            PurchasePlan plan,
            PurchasePlanReverseAuditCheckResult checkResult
    ) {
    }

    private record LinkedPurchaseOrderState(
            Long orderId,
            String orderNo,
            String status
    ) {
    }
}
