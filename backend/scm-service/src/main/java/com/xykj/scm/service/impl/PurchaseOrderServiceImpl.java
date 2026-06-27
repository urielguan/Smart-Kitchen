package com.xykj.scm.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import jakarta.annotation.PostConstruct;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
import com.xykj.common.service.MaterialCategoryCoefficientLockService;
import com.xykj.scm.dto.PurchaseOrderAttachmentDTO;
import com.xykj.scm.dto.PurchaseOrderAuditDTO;
import com.xykj.scm.dto.PurchaseOrderCreateDTO;
import com.xykj.scm.dto.PurchaseOrderInspectionUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderItemDTO;
import com.xykj.scm.dto.PurchaseOrderLogisticsUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderQueryDTO;
import com.xykj.scm.dto.PurchaseOrderReverseAuditDTO;
import com.xykj.scm.dto.PurchaseOrderSceneIntegrationSyncDTO;
import com.xykj.scm.dto.PurchaseOrderTraceabilityUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderUpdateDTO;
import com.xykj.scm.dto.PurchaseOrderVoidApplyDTO;
import com.xykj.scm.dto.PurchaseOrderVoidAuditDTO;
import com.xykj.scm.entity.PurchaseOrderAttachment;
import com.xykj.scm.entity.PurchaseOrder;
import com.xykj.scm.entity.PurchaseOrderItem;
import com.xykj.scm.mapper.PurchaseOrderAttachmentMapper;
import com.xykj.scm.mapper.PurchaseOrderItemMapper;
import com.xykj.scm.mapper.PurchaseOrderMapper;
import com.xykj.scm.service.PurchaseOrderService;
import com.xykj.scm.support.PurchaseOrderIntegrationClient;
import com.xykj.scm.vo.PurchaseOrderAttachmentVO;
import com.xykj.scm.vo.PurchaseOrderItemVO;
import com.xykj.scm.vo.PurchaseOrderLinkedInboundRecordVO;
import com.xykj.scm.vo.PurchaseOrderMaterialOptionVO;
import com.xykj.scm.vo.PurchaseOrderPlanItemOptionVO;
import com.xykj.scm.vo.PurchaseOrderRelatedPlanVO;
import com.xykj.scm.vo.PurchaseOrderReverseAuditResultVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationLogsVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationMetaVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationTriggerResultVO;
import com.xykj.scm.vo.PurchaseOrderStatisticsVO;
import com.xykj.scm.vo.PurchaseOrderSupplierOptionVO;
import com.xykj.scm.vo.PurchaseOrderVO;
import com.xykj.scm.vo.SelectablePurchasePlanVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * 采购订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseOrderServiceImpl implements PurchaseOrderService {

    private static final Long DEFAULT_TENANT_ID = 1L;
    private static final Long DEFAULT_USER_ID = 1L;
    private static final String ATTACHMENT_DIR = "scm/purchase-orders";
    private static final String LOGISTICS_ATTACHMENT_DIR = ATTACHMENT_DIR + "/logistics";
    private static final String INSPECTION_ATTACHMENT_DIR = ATTACHMENT_DIR + "/inspection";
    private static final String TRACEABILITY_ATTACHMENT_DIR = ATTACHMENT_DIR + "/traceability";
    private static final long MAX_ATTACHMENT_FILE_SIZE = 10L * 1024 * 1024;
    private static final String ATTACHMENT_TYPE_LOGISTICS = "logistics";
    private static final String ATTACHMENT_TYPE_INSPECTION = "inspection";
    private static final String ATTACHMENT_TYPE_TRACEABILITY = "traceability";
    private static final String BIZ_SCENE_LOGISTICS = "logistics";
    private static final String BIZ_SCENE_INSPECTION = "inspection";
    private static final String BIZ_SCENE_TRACEABILITY = "traceability";
    private static final String STATUS_PENDING_SUBMIT = "pending_submit";
    private static final String STATUS_PENDING_APPROVE = "pending_approve";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final String STATUS_PENDING_VOID_APPROVE = "pending_void_approve";
    private static final String STATUS_VOIDED = "voided";
    private static final String STATUS_DELIVERING = "delivering";
    private static final String STATUS_PENDING_RECEIPT = "pending_receipt";
    private static final String STATUS_COMPLETED = "completed";
    private static final String SOURCE_TYPE_MANUAL = "manual";
    private static final String SOURCE_TYPE_THIRD_PARTY = "third_party";
    private static final String LOGISTICS_STATUS_PENDING = "pending";
    private static final String LOGISTICS_STATUS_SHIPPED = "shipped";
    private static final String LOGISTICS_STATUS_IN_TRANSIT = "in_transit";
    private static final String LOGISTICS_STATUS_ARRIVED = "arrived";
    private static final String INBOUND_STATUS_DRAFT = "draft";
    private static final String INBOUND_STATUS_PENDING = "pending";
    private static final String INBOUND_STATUS_APPROVED = "approved";
    private static final String INBOUND_STATUS_COMPLETED = "completed";
    private static final String INBOUND_STATUS_REJECTED = "rejected";
    private static final String INBOUND_STATUS_CANCELLED = "cancelled";
    private static final String INSPECTION_RESULT_QUALIFIED = "合格";
    private static final String INSPECTION_RESULT_UNQUALIFIED = "不合格";
    private static final String SUPPLIER_STATUS_ACTIVE = "active";
    private static final String SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE = "所选供应商关键资质已过期，禁止新增采购业务关联";
    private static final String PURCHASE_ORDER_APPROVE_PERMISSION = "purchase:approve";
    private static final String PURCHASE_ORDER_DELETE_PERMISSION = "purchase:delete";
    private static final String REVERSE_AUDIT_PERMISSION_MESSAGE = "无权限执行反审核";
    private static final String REVERSE_AUDIT_STATUS_BLOCK_MESSAGE = "当前状态不允许执行反审核";
    private static final String REVERSE_AUDIT_LINKED_INBOUND_STATUS_BLOCK_MESSAGE = "关联入库单当前状态不允许执行反审核";
    private static final String REVERSE_AUDIT_BLOCK_MESSAGE = "该采购订单已存在正式下游业务，不允许反审核。";
    private static final String REVERSE_AUDIT_CONCURRENT_BLOCK_MESSAGE = "采购订单状态已发生变化，请刷新页面后重试";
    private static final String PURCHASE_ORDER_DELETE_PERMISSION_MESSAGE = "当前用户无采购订单删除权限";
    private static final String DELETE_BLOCK_MESSAGE = "该采购订单已关联下游履约/结算数据，不允许删除";
    private static final String REJECTED_DELETE_BLOCK_MESSAGE = "已驳回采购订单仅允许走作废申请流程，不允许删除";
    private static final String APPROVED_DELETE_BLOCK_MESSAGE = "已审核采购订单仅允许走作废申请流程，不允许删除";
    private static final String CONCURRENT_DELETE_BLOCK_MESSAGE = "当前采购订单状态或下游业务已发生变化，暂不允许删除，请刷新后重试或核查关联单据";
    private static final String REPEAT_DELETE_BLOCK_MESSAGE = "已删除采购订单不可重复删除";
    private static final String LOGISTICS_STATUS_LOCK_MESSAGE = "物料已全部入库，不允许修改物料状态";
    private static final String LINKED_INBOUND_LOGISTICS_STATUS_LOCK_MESSAGE = "当前订单已关联入库单，不允许修改物流状态";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderAttachmentMapper purchaseOrderAttachmentMapper;
    private final PurchaseOrderItemMapper purchaseOrderItemMapper;
    private final JdbcTemplate jdbcTemplate;
    private final DataScopeService dataScopeService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;
    private final MaterialCategoryCoefficientLockService materialCategoryCoefficientLockService;
    private final PurchaseOrderIntegrationClient purchaseOrderIntegrationClient;
    private final PlatformTransactionManager transactionManager;

    @PostConstruct
    public void migrateInboundQtyColumns() {
        try {
            // 检查 inbound_qty 列是否存在
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scm_purchase_order_item' AND COLUMN_NAME = 'inbound_qty'",
                    Integer.class
            );
            if (count != null && count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE scm_purchase_order_item " +
                                "ADD COLUMN inbound_qty DECIMAL(10,3) DEFAULT 0.000 COMMENT '已入库数量', " +
                                "ADD COLUMN remaining_inbound_qty DECIMAL(10,3) DEFAULT 0.000 COMMENT '剩余待入库数量'"
                );
                // 初始化：将已有数据的 remaining_inbound_qty 设为 order_qty
                jdbcTemplate.execute(
                        "UPDATE scm_purchase_order_item SET remaining_inbound_qty = order_qty WHERE remaining_inbound_qty = 0 OR remaining_inbound_qty IS NULL"
                );
                log.info("采购订单明细表入库数量字段迁移完成，已添加 inbound_qty 和 remaining_inbound_qty 列");
            } else {
                log.info("采购订单明细表入库数量字段已存在，跳过迁移");
            }
        } catch (Exception e) {
            log.warn("采购订单明细表入库数量字段迁移异常: {}", e.getMessage());
        }
    }

    @Override
    @DataScope
    public PageResult<PurchaseOrderVO> list(PurchaseOrderQueryDTO query) {
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        String normalizedKeyword = trimToNull(query.getKeyword());
        String normalizedOrderNo = trimToNull(query.getOrderNo());
        String normalizedSupplierName = trimToNull(query.getSupplierName());
        String normalizedStatus = trimToNull(query.getStatus());

        StringBuilder whereSql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        whereSql.append(" AND po.deleted = 0");
        if (query.getOrgId() != null) {
            whereSql.append(" AND po.org_id = ?");
            args.add(query.getOrgId());
        } else if (query.getOrgIds() != null) {
            if (query.getOrgIds().isEmpty()) {
                return PageResult.empty((long) pageNum, (long) pageSize);
            }
            whereSql.append(" AND po.org_id IN (").append(placeholders(query.getOrgIds().size())).append(")");
            args.addAll(query.getOrgIds());
        }
        if (StrUtil.isNotBlank(normalizedKeyword)) {
            whereSql.append(" AND (po.order_no LIKE ? OR po.supplier_name LIKE ?)");
            String likeKeyword = like(normalizedKeyword);
            args.add(likeKeyword);
            args.add(likeKeyword);
        }
        if (StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedOrderNo)) {
            whereSql.append(" AND po.order_no LIKE ?");
            args.add(like(normalizedOrderNo));
        }
        if (StrUtil.isBlank(normalizedKeyword) && StrUtil.isNotBlank(normalizedSupplierName)) {
            whereSql.append(" AND po.supplier_name LIKE ?");
            args.add(like(normalizedSupplierName));
        }
        if (StrUtil.isNotBlank(normalizedStatus)) {
            whereSql.append(" AND po.status = ?");
            args.add(normalizedStatus);
        }

        LocalDate dateStart = parseNullableDate(query.getDateStart());
        LocalDate dateEnd = parseNullableDate(query.getDateEnd());
        if (dateStart != null) {
            whereSql.append(" AND po.order_date >= ?");
            args.add(dateStart);
        }
        if (dateEnd != null) {
            whereSql.append(" AND po.order_date <= ?");
            args.add(dateEnd);
        }

        String countSql = "SELECT COUNT(*) FROM scm_purchase_order po" + whereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, args.toArray());
        if (total == null || total == 0L) {
            return PageResult.empty((long) pageNum, (long) pageSize);
        }

        String listSql = "SELECT po.id, po.order_no AS orderNo, po.supplier_id AS supplierId, po.supplier_name AS supplierName, s.status AS supplierStatus, " +
                "po.org_id AS orgId, so.org_name AS orgName, po.order_date AS orderDate, " +
                "po.expected_delivery_at AS expectedArrival, po.total_amount AS totalAmount, " +
                "po.attachment_name AS attachmentName, po.attachment_url AS attachmentUrl, " +
                "po.inspection_result AS inspectionResult, po.status, po.remark, " +
                "po.deleted AS deleted, " +
                "CASE WHEN (" +
                "(po.inspection_report_no IS NOT NULL AND TRIM(po.inspection_report_no) <> '') " +
                "OR (po.inspection_result IS NOT NULL AND TRIM(po.inspection_result) <> '') " +
                "OR (po.inspection_agency IS NOT NULL AND TRIM(po.inspection_agency) <> '') " +
                "OR po.inspection_at IS NOT NULL " +
                "OR (po.inspection_remark IS NOT NULL AND TRIM(po.inspection_remark) <> '') " +
                "OR (po.inspection_sync_payload IS NOT NULL AND TRIM(po.inspection_sync_payload) <> '') " +
                "OR (po.inspection_attachment_name IS NOT NULL AND TRIM(po.inspection_attachment_name) <> '') " +
                "OR (po.inspection_attachment_url IS NOT NULL AND TRIM(po.inspection_attachment_url) <> '') " +
                "OR EXISTS (SELECT 1 FROM scm_purchase_order_attachment poa WHERE poa.order_id = po.id AND poa.attachment_type = 'inspection' LIMIT 1)" +
                ") THEN 1 ELSE 0 END AS inspectionFilled, " +
                "CASE WHEN (" +
                "(po.trace_batch_id IS NOT NULL AND TRIM(po.trace_batch_id) <> '') " +
                "OR (po.trace_origin IS NOT NULL AND TRIM(po.trace_origin) <> '') " +
                "OR (po.trace_remark IS NOT NULL AND TRIM(po.trace_remark) <> '') " +
                "OR (po.trace_sync_payload IS NOT NULL AND TRIM(po.trace_sync_payload) <> '') " +
                "OR (po.trace_attachment_name IS NOT NULL AND TRIM(po.trace_attachment_name) <> '') " +
                "OR (po.trace_attachment_url IS NOT NULL AND TRIM(po.trace_attachment_url) <> '') " +
                "OR EXISTS (SELECT 1 FROM scm_purchase_order_attachment poa WHERE poa.order_id = po.id AND poa.attachment_type = 'traceability' LIMIT 1)" +
                ") THEN 1 ELSE 0 END AS traceabilityFilled, " +
                "po.created_by AS createdById, po.created_at AS createdAt, po.updated_at AS updatedAt, " +
                "po.approved_by AS approvedById, po.approved_at AS approvedAt, po.approve_remark AS auditRemark, " +
                "po.void_reason AS voidReason, po.void_requested_by AS voidRequestedById, po.void_requested_at AS voidRequestedAt, " +
                "po.void_audit_by AS voidAuditById, po.void_audit_at AS voidAuditAt, po.void_audit_remark AS voidAuditRemark " +
                "FROM scm_purchase_order po " +
                "LEFT JOIN scm_supplier s ON s.id = po.supplier_id AND s.deleted = 0 " +
                "LEFT JOIN sys_organization so ON so.id = po.org_id AND so.deleted = 0" +
                whereSql +
                " ORDER BY po.order_date DESC, po.created_at DESC, po.id DESC LIMIT ? OFFSET ?";

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(pageSize);
        listArgs.add((pageNum - 1L) * pageSize);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(listSql, listArgs.toArray());
        List<PurchaseOrderVO> list = new ArrayList<>();
        Map<Long, String> operatorCache = new HashMap<>();
        for (Map<String, Object> row : rows) {
            PurchaseOrderVO vo = new PurchaseOrderVO();
            Long orderId = toLong(row.get("id"));
            String creatorName = resolveOperatorName(toLong(row.get("createdById")), operatorCache);
            vo.setId(orderId);
            vo.setOrderNo(asString(row.get("orderNo")));
            vo.setSupplierId(toLong(row.get("supplierId")));
            vo.setSupplierName(buildHistoricalSupplierName(asString(row.get("supplierName")), asString(row.get("supplierStatus"))));
            vo.setOrgId(toLong(row.get("orgId")));
            vo.setOrgName(asString(row.get("orgName")));
            vo.setCreatedById(toLong(row.get("createdById")));
            vo.setOrderDate(formatDate(row.get("orderDate")));
            vo.setExpectedArrival(formatDate(row.get("expectedArrival")));
            vo.setTotalAmount(scaleAmount(toBigDecimal(row.get("totalAmount"))));
            vo.setAttachmentName(asString(row.get("attachmentName")));
            vo.setAttachmentUrl(asString(row.get("attachmentUrl")));
            vo.setInspectionResult(asString(row.get("inspectionResult")));
            vo.setStatus(asString(row.get("status")));
            vo.setDeleted(toInteger(row.get("deleted")) != 0);
            vo.setRemark(asString(row.get("remark")));
            vo.setInspectionFilled(toInteger(row.get("inspectionFilled")) != 0);
            vo.setTraceabilityFilled(toInteger(row.get("traceabilityFilled")) != 0);
            vo.setCreatedBy(creatorName);
            vo.setBuyerName(creatorName);
            vo.setCreatedAt(formatDateTime(row.get("createdAt")));
            vo.setUpdatedAt(formatDateTime(row.get("updatedAt")));
            vo.setAuditBy(resolveOperatorName(toLong(row.get("approvedById")), operatorCache));
            vo.setAuditAt(formatDateTime(row.get("approvedAt")));
            vo.setAuditRemark(asString(row.get("auditRemark")));
            vo.setVoidReason(asString(row.get("voidReason")));
            vo.setVoidRequestedBy(resolveOperatorName(toLong(row.get("voidRequestedById")), operatorCache));
            vo.setVoidRequestedAt(formatDateTime(row.get("voidRequestedAt")));
            vo.setVoidAuditBy(resolveOperatorName(toLong(row.get("voidAuditById")), operatorCache));
            vo.setVoidAuditAt(formatDateTime(row.get("voidAuditAt")));
            vo.setVoidAuditRemark(asString(row.get("voidAuditRemark")));
            list.add(vo);
        }
        return PageResult.of(list, (long) pageNum, (long) pageSize, total);
    }

    @Override
    public PurchaseOrderStatisticsVO getStatistics(Long orgId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        PurchaseOrderStatisticsVO vo = new PurchaseOrderStatisticsVO();
        if (allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            vo.setTotal(0L);
            vo.setPending(0L);
            vo.setApproved(0L);
            vo.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return vo;
        }

        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && targetOrgId == null) {
            vo.setTotal(0L);
            vo.setPending(0L);
            vo.setApproved(0L);
            vo.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            return vo;
        }

        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) AS total, " +
                        "SUM(CASE WHEN status = 'pending_approve' THEN 1 ELSE 0 END) AS pending, " +
                        "SUM(CASE WHEN status = 'approved' THEN 1 ELSE 0 END) AS approved, " +
                        "COALESCE(SUM(COALESCE(total_amount, 0)), 0) AS totalAmount " +
                        "FROM scm_purchase_order WHERE deleted = 0"
        );
        List<Object> args = new ArrayList<>();
        if (targetOrgId != null) {
            sql.append(" AND org_id = ?");
            args.add(targetOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(sql.toString(), args.toArray());
        vo.setTotal(toLong(row.get("total")));
        vo.setPending(toLong(row.get("pending")));
        vo.setApproved(toLong(row.get("approved")));
        vo.setTotalAmount(scaleAmount(toBigDecimal(row.get("totalAmount"))));
        return vo;
    }

    @Override
    public PurchaseOrderVO getDetail(Long id) {
        PurchaseOrder order = getOrderById(id, true, true);
        if (isDeleted(order) && !dataScopeService.isAdminUser()) {
            throw BizException.notFound("采购订单不存在");
        }
        PurchaseOrderVO vo = toVO(order);
        Map<Long, String> operatorCache = new HashMap<>();
        String creatorName = resolveOperatorName(order.getCreatedBy(), operatorCache);
        vo.setCreatedBy(creatorName);
        vo.setBuyerName(creatorName);
        vo.setAuditBy(resolveOperatorName(order.getApprovedBy(), operatorCache));
        vo.setVoidRequestedBy(resolveOperatorName(order.getVoidRequestedBy(), operatorCache));
        vo.setVoidAuditBy(resolveOperatorName(order.getVoidAuditBy(), operatorCache));
        vo.setItems(loadOrderItemsForDetail(Collections.singletonList(id)).getOrDefault(id, Collections.emptyList()));
        loadRelatedPlans(vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(PurchaseOrderCreateDTO dto, MultipartFile file) {
        String status = normalizeEditableStatus(dto.getStatus());
        Long targetOrgId = requireManageableOrgId(dto.getOrgId());
        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNo(resolveOrderNo(dto.getOrderNo(), null));
        fillOrderFields(order, targetOrgId, dto.getSupplierId(), dto.getOrderDate(), dto.getExpectedArrival(),
                dto.getRemark(), status, null);

        BuildResult buildResult = buildOrderItems(targetOrgId, dto.getRelatedPlanIds(),
                dto.getItems(), null);
        order.setPlanId(buildResult.getPrimaryPlanId());
        order.setTotalAmount(buildResult.getTotalAmount());
        applyAttachment(order, file);
        purchaseOrderMapper.insert(order);
        saveOrderItems(order.getId(), buildResult.getItems());

        log.info("新增采购订单成功: id={}, orderNo={}", order.getId(), order.getOrderNo());
        return order.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, PurchaseOrderUpdateDTO dto, MultipartFile file) {
        PurchaseOrder order = getOrderById(id, true);
        ensureEditable(order.getStatus());
        String status = normalizeEditableStatus(dto.getStatus());
        Long targetOrgId = requireManageableOrgId(dto.getOrgId());
        String originalAttachmentUrl = order.getAttachmentUrl();
        boolean clearAttachment = (file == null || file.isEmpty()) && (
                Boolean.TRUE.equals(dto.getClearAttachment())
                        || (StrUtil.isBlank(dto.getAttachmentUrl()) && StrUtil.isBlank(dto.getAttachmentName()) && StrUtil.isNotBlank(originalAttachmentUrl))
        );

        fillOrderFields(order, targetOrgId, dto.getSupplierId(), dto.getOrderDate(), dto.getExpectedArrival(),
                dto.getRemark(), status, order.getSupplierId());
        BuildResult buildResult = buildOrderItems(targetOrgId, dto.getRelatedPlanIds(),
                dto.getItems(), id);
        order.setOrderNo(resolveOrderNo(dto.getOrderNo(), id));
        order.setPlanId(buildResult.getPrimaryPlanId());
        order.setTotalAmount(buildResult.getTotalAmount());
        applyAttachment(order, file);
        if (clearAttachment) {
            order.setAttachmentName(null);
            order.setAttachmentUrl(null);
        }
        purchaseOrderMapper.updateById(order);
        syncNullableEditableFields(id, order);
        purchaseOrderItemMapper.deleteByOrderId(id);
        saveOrderItems(id, buildResult.getItems());
        if (clearAttachment && StrUtil.isNotBlank(originalAttachmentUrl)) {
            fileStorageService.delete(originalAttachmentUrl);
        }

        log.info("编辑采购订单成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void audit(Long id, PurchaseOrderAuditDTO dto) {
        String status = normalizeAuditStatus(dto.getStatus());
        PurchaseOrder order = getOrderById(id, true);
        if (!STATUS_PENDING_APPROVE.equals(order.getStatus())) {
            throw BizException.badRequest("仅待审核状态可执行审核");
        }

        order.setStatus(status);
        order.setApprovedBy(DEFAULT_USER_ID);
        order.setApprovedAt(LocalDateTime.now());
        order.setApproveRemark(StrUtil.isNotBlank(dto.getRemark())
                ? dto.getRemark().trim()
                : (STATUS_APPROVED.equals(status) ? "审核通过" : "审核驳回"));
        purchaseOrderMapper.updateById(order);

        log.info("审核采购订单成功: id={}, status={}", id, status);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderReverseAuditResultVO reverseAudit(Long id, PurchaseOrderReverseAuditDTO dto) {
        ensurePermission(PURCHASE_ORDER_APPROVE_PERMISSION, REVERSE_AUDIT_PERMISSION_MESSAGE);
        String reason = normalizeRequiredText(dto == null ? null : dto.getReason(), "请填写反审核原因");
        PurchaseOrder order = getOrderById(id, false, false);
        ensureOrgAllowed(order.getOrgId());

        PurchaseOrderReverseAuditCheckResult initialCheck = inspectReverseAuditPreconditions(order, id, false);
        String beforeData = JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                order,
                initialCheck,
                reason,
                "initial_check",
                false,
                null
        ));

        try {
            assertReverseAuditCheckPassed(initialCheck, false);
            PurchaseOrderReverseAuditRecheckSnapshot recheckSnapshot = performReverseAuditConcurrentRecheck(id, reason);
            PurchaseOrder latestOrder = recheckSnapshot.order();
            PurchaseOrderReverseAuditCheckResult finalCheck = recheckSnapshot.checkResult();

            jdbcTemplate.update(
                    "UPDATE scm_purchase_order " +
                            "SET status = ?, approved_by = NULL, approved_at = NULL, approve_remark = NULL, " +
                            "updated_by = ?, updated_at = NOW() " +
                            "WHERE id = ?",
                    STATUS_PENDING_APPROVE,
                    resolveCurrentUserId(),
                    latestOrder.getId()
            );

            latestOrder.setStatus(STATUS_PENDING_APPROVE);
            latestOrder.setApprovedBy(null);
            latestOrder.setApprovedAt(null);
            latestOrder.setApproveRemark(null);

            PurchaseOrderReverseAuditResultVO result = new PurchaseOrderReverseAuditResultVO();
            result.setAffectedInboundCount(finalCheck.linkedInboundCount());
            result.setAffectedInboundNos(buildLinkedInboundNoList(finalCheck.linkedInbounds()));

            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    "采购订单反审核成功",
                    beforeData,
                    JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                            latestOrder,
                            finalCheck,
                            reason,
                            "reverse_audit_applied",
                            true,
                            null
                    ))
            );
            log.info(
                    "采购订单反审核成功: id={}, orderNo={}, linkedInboundCount={}",
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    finalCheck.linkedInboundCount()
            );
            return result;
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    order.getId(),
                    order.getOrderNo(),
                    resolveReverseAuditBlockedAuditAction(ex.getMessage()),
                    beforeData,
                    JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                            order,
                            initialCheck,
                            reason,
                            "reverse_audit_blocked",
                            false,
                            ex.getMessage()
                    )),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyVoid(Long id, PurchaseOrderVoidApplyDTO dto) {
        PurchaseOrder order = getOrderById(id, true);
        ensureVoidApplicable(order.getStatus());

        PurchaseOrder beforeOrder = copyOrder(order);
        String reason = dto.getReason().trim();

        try {
            order.setStatus(STATUS_PENDING_VOID_APPROVE);
            order.setVoidReason(reason);
            order.setVoidRequestedBy(resolveCurrentUserId());
            order.setVoidRequestedAt(LocalDateTime.now());
            order.setVoidAuditBy(null);
            order.setVoidAuditAt(null);
            order.setVoidAuditRemark(null);
            purchaseOrderMapper.update(
                    order,
                    new LambdaUpdateWrapper<PurchaseOrder>()
                            .eq(PurchaseOrder::getId, order.getId())
                            .set(PurchaseOrder::getVoidAuditBy, null)
                            .set(PurchaseOrder::getVoidAuditAt, null)
                            .set(PurchaseOrder::getVoidAuditRemark, null)
            );

            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    order.getId(),
                    order.getOrderNo(),
                    "发起采购订单作废申请",
                    JSONUtil.toJsonStr(beforeOrder),
                    JSONUtil.toJsonStr(order)
            );
            log.info("发起采购订单作废申请成功: id={}, orderNo={}", order.getId(), order.getOrderNo());
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    order.getId(),
                    order.getOrderNo(),
                    "发起采购订单作废申请失败",
                    JSONUtil.toJsonStr(beforeOrder),
                    null,
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditVoid(Long id, PurchaseOrderVoidAuditDTO dto) {
        PurchaseOrder order = getOrderById(id, true);
        ensureVoidAuditable(order.getStatus());

        PurchaseOrder beforeOrder = copyOrder(order);
        Long operatorId = resolveCurrentUserId();
        LocalDateTime operateTime = LocalDateTime.now();
        String remark = StrUtil.isNotBlank(dto.getRemark())
                ? dto.getRemark().trim()
                : Boolean.TRUE.equals(dto.getApproved()) ? "作废审核通过" : "作废审核驳回";

        try {
            order.setVoidAuditBy(operatorId);
            order.setVoidAuditAt(operateTime);
            order.setVoidAuditRemark(remark);
            ReleasePlanRelationResult releaseResult = null;
            ReleaseMergeLockedPlanResult mergeLockReleaseResult = null;
            if (Boolean.TRUE.equals(dto.getApproved())) {
                order.setStatus(STATUS_VOIDED);
                releaseResult = releaseRelatedPurchasePlans(order.getId());
                mergeLockReleaseResult = releaseMergeLockedPurchasePlans(order.getId());
                order.setPlanId(null);
            } else {
                order.setStatus(resolveStatusAfterVoidReject(order));
            }
            purchaseOrderMapper.updateById(order);

            Map<String, Object> afterData = new LinkedHashMap<>();
            afterData.put("order", order);
            if (releaseResult != null) {
                afterData.put("releasedPlanIds", releaseResult.getPlanIds());
                afterData.put("releasedItemCount", releaseResult.getReleasedItemCount());
            }
            if (mergeLockReleaseResult != null) {
                afterData.put("releasedMergeLockedPlanIds", mergeLockReleaseResult.getPlanIds());
                afterData.put("releasedMergeLockedPlanCount", mergeLockReleaseResult.getReleasedPlanCount());
            }
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    order.getId(),
                    order.getOrderNo(),
                    Boolean.TRUE.equals(dto.getApproved()) ? "采购订单作废审核通过并解除采购计划关联" : "采购订单作废审核驳回",
                    JSONUtil.toJsonStr(beforeOrder),
                    JSONUtil.toJsonStr(afterData)
            );
            log.info("采购订单作废审核完成: id={}, approved={}", order.getId(), dto.getApproved());
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    order.getId(),
                    order.getOrderNo(),
                    "采购订单作废审核失败",
                    JSONUtil.toJsonStr(beforeOrder),
                    null,
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLogistics(Long id, PurchaseOrderLogisticsUpdateDTO dto, MultipartFile file) {
        PurchaseOrder order = getOrderById(id, true);
        ensureLogisticsMaintainable(order.getStatus());

        PurchaseOrder beforeOrder = copyOrder(order);
        String sourceType = normalizeSourceType(dto.getSourceType());
        String logisticsStatus = normalizeLogisticsStatus(dto.getLogisticsStatus());
        String company = trimToNull(dto.getCompany());
        String trackingNo = trimToNull(dto.getTrackingNo());
        String syncPayload = normalizeSceneSyncPayload(sourceType, dto.getSyncPayload());
        Long integrationConfigId = normalizeThirdPartyIntegrationConfigId(sourceType, dto.getIntegrationConfigId());
        String integrationExternalNo = resolveThirdPartyExternalNo(sourceType, dto.getIntegrationExternalNo(), trackingNo, "物流外部单号");
        LocalDateTime shippedAt = parseNullableDateTime(dto.getShippedAt(), "发货时间");
        LocalDateTime arrivedAt = parseNullableDateTime(dto.getArrivedAt(), "到货时间");
        if (arrivedAt != null && arrivedAt.toLocalDate().isAfter(LocalDate.now())) {
            throw BizException.badRequest("到货时间不能晚于今天");
        }
        if (arrivedAt != null && shippedAt != null && arrivedAt.isBefore(shippedAt)) {
            throw BizException.badRequest("发货时间不能晚于到货时间");
        }
        if (hasEditableLockedInboundOrder(order) && !Objects.equals(logisticsStatus, order.getLogisticsStatus())) {
            throw BizException.badRequest(LINKED_INBOUND_LOGISTICS_STATUS_LOCK_MESSAGE);
        }
        if (isLogisticsStatusLocked(order) && !Objects.equals(logisticsStatus, order.getLogisticsStatus())) {
            throw BizException.badRequest(LOGISTICS_STATUS_LOCK_MESSAGE);
        }

        List<String> beforeAttachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_LOGISTICS);
        List<String> uploadedAttachmentUrls = new ArrayList<>();
        List<PurchaseOrderAttachmentDTO> attachments = resolveMaintenanceAttachments(
                order,
                ATTACHMENT_TYPE_LOGISTICS,
                dto.getAttachments(),
                file,
                LOGISTICS_ATTACHMENT_DIR,
                uploadedAttachmentUrls
        );
        validateLogisticsRequiredFields(logisticsStatus, company, trackingNo, shippedAt, arrivedAt, attachments);
        syncLegacyMaintenanceAttachmentFields(order, ATTACHMENT_TYPE_LOGISTICS, attachments);

        try {
            String nextStatus = resolveStatusAfterLogistics(order.getStatus(), logisticsStatus, shippedAt, arrivedAt);
            LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getId, id)
                    .set(PurchaseOrder::getLogisticsCompany, company)
                    .set(PurchaseOrder::getLogisticsNo, trackingNo)
                    .set(PurchaseOrder::getLogisticsStatus, logisticsStatus)
                    .set(PurchaseOrder::getLogisticsRemark, trimToNull(dto.getRemark()))
                    .set(PurchaseOrder::getLogisticsSourceType, sourceType)
                    .set(PurchaseOrder::getLogisticsSyncPayload, syncPayload)
                    .set(PurchaseOrder::getShippedAt, shippedAt)
                    .set(PurchaseOrder::getArrivedAt, arrivedAt)
                    .set(PurchaseOrder::getActualDeliveryAt, arrivedAt)
                    .set(PurchaseOrder::getLogisticsAttachmentName, order.getLogisticsAttachmentName())
                    .set(PurchaseOrder::getLogisticsAttachmentUrl, order.getLogisticsAttachmentUrl())
                    .set(PurchaseOrder::getStatus, nextStatus);
            purchaseOrderMapper.update(null, updateWrapper);
            replaceOrderAttachments(id, ATTACHMENT_TYPE_LOGISTICS, order.getOrgId(), order.getTenantId(), attachments);
            deleteRemovedAttachmentUrls(beforeAttachmentUrls, attachments);

            order.setLogisticsCompany(company);
            order.setLogisticsNo(trackingNo);
            order.setLogisticsStatus(logisticsStatus);
            order.setLogisticsRemark(trimToNull(dto.getRemark()));
            order.setLogisticsSourceType(sourceType);
            order.setLogisticsSyncPayload(syncPayload);
            order.setShippedAt(shippedAt);
            order.setArrivedAt(arrivedAt);
            order.setActualDeliveryAt(arrivedAt);
            order.setStatus(nextStatus);
            if (SOURCE_TYPE_MANUAL.equals(sourceType)) {
                purchaseOrderIntegrationClient.switchSceneModeBestEffort(order, BIZ_SCENE_LOGISTICS, SOURCE_TYPE_MANUAL, null);
            } else {
                purchaseOrderIntegrationClient.saveSceneBinding(order, BIZ_SCENE_LOGISTICS, integrationConfigId, integrationExternalNo);
            }
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    STATUS_PENDING_RECEIPT.equals(nextStatus)
                            ? "维护采购订单物流信息（" + resolveSourceTypeLabel(sourceType) + "）并流转为待入库"
                            : "维护采购订单物流信息（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    JSONUtil.toJsonStr(order)
            );
            log.info("维护采购订单物流信息成功: id={}, status={}", order.getId(), nextStatus);
        } catch (Exception ex) {
            deleteAttachmentUrls(uploadedAttachmentUrls);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    "维护采购订单物流信息失败（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    null,
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteLogisticsAttachment(Long id) {
        PurchaseOrder order = getOrderById(id, true);
        ensureLogisticsMaintainable(order.getStatus());

        List<String> attachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_LOGISTICS);
        purchaseOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getId, id)
                        .set(PurchaseOrder::getLogisticsAttachmentName, null)
                        .set(PurchaseOrder::getLogisticsAttachmentUrl, null)
        );
        purchaseOrderAttachmentMapper.delete(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .eq(PurchaseOrderAttachment::getOrderId, id)
                .eq(PurchaseOrderAttachment::getAttachmentType, ATTACHMENT_TYPE_LOGISTICS));
        order.setLogisticsAttachmentName(null);
        order.setLogisticsAttachmentUrl(null);
        deleteAttachmentUrls(attachmentUrls);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_ORDER,
                AuditOperationType.UPDATE,
                order.getId(),
                order.getOrderNo(),
                "删除采购订单物流附件",
                null,
                JSONUtil.toJsonStr(order)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateInspection(Long id, PurchaseOrderInspectionUpdateDTO dto, MultipartFile file) {
        PurchaseOrder order = getOrderById(id, true);
        ensureInspectionMaintainable(order);

        PurchaseOrder beforeOrder = copyOrder(order);
        String sourceType = normalizeSourceType(dto.getSourceType());
        String inspectionResult = trimToNull(dto.getResult());
        String reportNo = trimToNull(dto.getReportNo());
        String agency = trimToNull(dto.getAgency());
        String syncPayload = normalizeSceneSyncPayload(sourceType, dto.getSyncPayload());
        Long integrationConfigId = normalizeThirdPartyIntegrationConfigId(sourceType, dto.getIntegrationConfigId());
        String integrationExternalNo = resolveThirdPartyExternalNo(sourceType, dto.getIntegrationExternalNo(), reportNo, "检测外部单号");
        LocalDateTime inspectionAt = parseNullableDateTime(dto.getInspectedAt(), "检测时间");
        if (inspectionAt != null && inspectionAt.isAfter(LocalDateTime.now())) {
            throw BizException.badRequest("检测时间不能选择未来日期");
        }
        List<String> beforeAttachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_INSPECTION);
        List<String> uploadedAttachmentUrls = new ArrayList<>();
        List<PurchaseOrderAttachmentDTO> attachments = resolveMaintenanceAttachments(
                order,
                ATTACHMENT_TYPE_INSPECTION,
                dto.getAttachments(),
                file,
                INSPECTION_ATTACHMENT_DIR,
                uploadedAttachmentUrls
        );
        validateInspectionRequiredFields(inspectionResult, reportNo, agency, inspectionAt, attachments);
        syncLegacyMaintenanceAttachmentFields(order, ATTACHMENT_TYPE_INSPECTION, attachments);

        try {
            LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getId, id)
                    .set(PurchaseOrder::getInspectionReportNo, reportNo)
                    .set(PurchaseOrder::getInspectionResult, inspectionResult)
                    .set(PurchaseOrder::getInspectionAgency, agency)
                    .set(PurchaseOrder::getInspectionAt, inspectionAt)
                    .set(PurchaseOrder::getInspectionRemark, trimToNull(dto.getRemark()))
                    .set(PurchaseOrder::getInspectionSourceType, sourceType)
                    .set(PurchaseOrder::getInspectionSyncPayload, syncPayload)
                    .set(PurchaseOrder::getInspectionAttachmentName, order.getInspectionAttachmentName())
                    .set(PurchaseOrder::getInspectionAttachmentUrl, order.getInspectionAttachmentUrl());
            purchaseOrderMapper.update(null, updateWrapper);
            replaceOrderAttachments(id, ATTACHMENT_TYPE_INSPECTION, order.getOrgId(), order.getTenantId(), attachments);
            deleteRemovedAttachmentUrls(beforeAttachmentUrls, attachments);

            order.setInspectionReportNo(reportNo);
            order.setInspectionResult(inspectionResult);
            order.setInspectionAgency(agency);
            order.setInspectionAt(inspectionAt);
            order.setInspectionRemark(trimToNull(dto.getRemark()));
            order.setInspectionSourceType(sourceType);
            order.setInspectionSyncPayload(syncPayload);
            if (SOURCE_TYPE_MANUAL.equals(sourceType)) {
                purchaseOrderIntegrationClient.switchSceneModeBestEffort(order, BIZ_SCENE_INSPECTION, SOURCE_TYPE_MANUAL, null);
            } else {
                purchaseOrderIntegrationClient.saveSceneBinding(order, BIZ_SCENE_INSPECTION, integrationConfigId, integrationExternalNo);
            }
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    "维护采购订单检测报告（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    JSONUtil.toJsonStr(order)
            );
            log.info("维护采购订单检测报告成功: id={}", order.getId());
        } catch (Exception ex) {
            deleteAttachmentUrls(uploadedAttachmentUrls);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    "维护采购订单检测报告失败（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    null,
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInspectionAttachment(Long id) {
        PurchaseOrder order = getOrderById(id, true);
        ensureInspectionMaintainable(order);

        List<String> attachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_INSPECTION);
        purchaseOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getId, id)
                        .set(PurchaseOrder::getInspectionAttachmentName, null)
                        .set(PurchaseOrder::getInspectionAttachmentUrl, null)
        );
        purchaseOrderAttachmentMapper.delete(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .eq(PurchaseOrderAttachment::getOrderId, id)
                .eq(PurchaseOrderAttachment::getAttachmentType, ATTACHMENT_TYPE_INSPECTION));
        order.setInspectionAttachmentName(null);
        order.setInspectionAttachmentUrl(null);
        deleteAttachmentUrls(attachmentUrls);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_ORDER,
                AuditOperationType.UPDATE,
                order.getId(),
                order.getOrderNo(),
                "删除采购订单检测报告附件",
                null,
                JSONUtil.toJsonStr(order)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTraceability(Long id, PurchaseOrderTraceabilityUpdateDTO dto, MultipartFile file) {
        PurchaseOrder order = getOrderById(id, true);
        ensureTraceabilityMaintainable(order);

        PurchaseOrder beforeOrder = copyOrder(order);
        String sourceType = normalizeSourceType(dto.getSourceType());
        String traceBatchId = trimToNull(dto.getTraceBatchId());
        String origin = trimToNull(dto.getOrigin());
        String syncPayload = normalizeSceneSyncPayload(sourceType, dto.getSyncPayload());
        Long integrationConfigId = normalizeThirdPartyIntegrationConfigId(sourceType, dto.getIntegrationConfigId());
        String integrationExternalNo = resolveThirdPartyExternalNo(sourceType, dto.getIntegrationExternalNo(), traceBatchId, "溯源外部单号");
        List<String> beforeAttachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_TRACEABILITY);
        List<String> uploadedAttachmentUrls = new ArrayList<>();
        List<PurchaseOrderAttachmentDTO> attachments = resolveMaintenanceAttachments(
                order,
                ATTACHMENT_TYPE_TRACEABILITY,
                dto.getAttachments(),
                file,
                TRACEABILITY_ATTACHMENT_DIR,
                uploadedAttachmentUrls
        );
        validateTraceabilityRequiredFields(traceBatchId, origin, attachments);
        syncLegacyMaintenanceAttachmentFields(order, ATTACHMENT_TYPE_TRACEABILITY, attachments);

        try {
            LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<PurchaseOrder>()
                    .eq(PurchaseOrder::getId, id)
                    .set(PurchaseOrder::getTraceBatchId, traceBatchId)
                    .set(PurchaseOrder::getTraceOrigin, origin)
                    .set(PurchaseOrder::getTraceRemark, trimToNull(dto.getRemark()))
                    .set(PurchaseOrder::getTraceSourceType, sourceType)
                    .set(PurchaseOrder::getTraceSyncPayload, syncPayload)
                    .set(PurchaseOrder::getTraceAttachmentName, order.getTraceAttachmentName())
                    .set(PurchaseOrder::getTraceAttachmentUrl, order.getTraceAttachmentUrl());
            purchaseOrderMapper.update(null, updateWrapper);
            replaceOrderAttachments(id, ATTACHMENT_TYPE_TRACEABILITY, order.getOrgId(), order.getTenantId(), attachments);
            deleteRemovedAttachmentUrls(beforeAttachmentUrls, attachments);

            order.setTraceBatchId(traceBatchId);
            order.setTraceOrigin(origin);
            order.setTraceRemark(trimToNull(dto.getRemark()));
            order.setTraceSourceType(sourceType);
            order.setTraceSyncPayload(syncPayload);
            if (SOURCE_TYPE_MANUAL.equals(sourceType)) {
                purchaseOrderIntegrationClient.switchSceneModeBestEffort(order, BIZ_SCENE_TRACEABILITY, SOURCE_TYPE_MANUAL, null);
            } else {
                purchaseOrderIntegrationClient.saveSceneBinding(order, BIZ_SCENE_TRACEABILITY, integrationConfigId, integrationExternalNo);
            }
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    "维护采购订单溯源信息（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    JSONUtil.toJsonStr(order)
            );
            log.info("维护采购订单溯源信息成功: id={}", order.getId());
        } catch (Exception ex) {
            deleteAttachmentUrls(uploadedAttachmentUrls);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.UPDATE,
                    order.getId(),
                    order.getOrderNo(),
                    "维护采购订单溯源信息失败（" + resolveSourceTypeLabel(sourceType) + "）",
                    JSONUtil.toJsonStr(beforeOrder),
                    null,
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTraceabilityAttachment(Long id) {
        PurchaseOrder order = getOrderById(id, true);
        ensureTraceabilityMaintainable(order);

        List<String> attachmentUrls = loadPersistedMaintenanceAttachmentUrls(order, ATTACHMENT_TYPE_TRACEABILITY);
        purchaseOrderMapper.update(
                null,
                new LambdaUpdateWrapper<PurchaseOrder>()
                        .eq(PurchaseOrder::getId, id)
                        .set(PurchaseOrder::getTraceAttachmentName, null)
                        .set(PurchaseOrder::getTraceAttachmentUrl, null)
        );
        purchaseOrderAttachmentMapper.delete(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .eq(PurchaseOrderAttachment::getOrderId, id)
                .eq(PurchaseOrderAttachment::getAttachmentType, ATTACHMENT_TYPE_TRACEABILITY));
        order.setTraceAttachmentName(null);
        order.setTraceAttachmentUrl(null);
        deleteAttachmentUrls(attachmentUrls);
        auditLogService.log(
                AuditModule.SCM_PURCHASE_ORDER,
                AuditOperationType.UPDATE,
                order.getId(),
                order.getOrderNo(),
                "删除采购订单溯源附件",
                null,
                JSONUtil.toJsonStr(order)
        );
    }

    @Override
    public PurchaseOrderSceneIntegrationMetaVO getSceneIntegrationMeta(Long id, String scene) {
        PurchaseOrder order = getOrderById(id, true);
        ensureSceneMaintainable(order, scene);
        return purchaseOrderIntegrationClient.getSceneMeta(order, normalizeScene(scene));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PurchaseOrderSceneIntegrationTriggerResultVO triggerSceneIntegrationSync(Long id, String scene, PurchaseOrderSceneIntegrationSyncDTO dto) {
        PurchaseOrder order = getOrderById(id, true);
        ensureSceneMaintainable(order, scene);
        String normalizedScene = normalizeScene(scene);
        markSceneSourceTypeAsThirdPartyCommitted(order, normalizedScene);
        PurchaseOrderSceneIntegrationTriggerResultVO result = purchaseOrderIntegrationClient.triggerSceneSync(order, normalizedScene, dto);
        purchaseOrderIntegrationClient.switchSceneModeBestEffort(order, normalizedScene, SOURCE_TYPE_THIRD_PARTY, dto.getConfigId());
        return result;
    }

    @Override
    public PurchaseOrderSceneIntegrationLogsVO getSceneIntegrationLogs(Long id, String scene) {
        PurchaseOrder order = getOrderById(id, true);
        ensureSceneMaintainable(order, scene);
        return purchaseOrderIntegrationClient.getSceneLogs(order, normalizeScene(scene));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PurchaseOrder order = getOrderById(id, true, true);
        ensureDeleteAuthorized(order);

        PurchaseOrderDeleteCheckResult initialCheck = inspectDeletePreconditions(order, id);
        String beforeData = JSONUtil.toJsonStr(buildDeleteAuditPayload(order, initialCheck, "initial_check", false, null, null));
        try {
            validateDeletePreconditions(order, id, false);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.DELETE,
                    order.getId(),
                    order.getOrderNo(),
                    "采购订单删除前校验拦截",
                    beforeData,
                    JSONUtil.toJsonStr(buildDeleteAuditPayload(order, initialCheck, "initial_check", false, ex.getMessage(), null)),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }

        PurchaseOrderDeleteRecheckSnapshot recheckSnapshot = performDeleteConcurrentRecheck(id);
        String deleteBeforeData = JSONUtil.toJsonStr(buildDeleteAuditPayload(
                recheckSnapshot.latestOrder(),
                recheckSnapshot.checkResult(),
                "logical_delete",
                false,
                null,
                null
        ));

        try {
            int updatedRows = purchaseOrderMapper.update(
                    null,
                    new LambdaUpdateWrapper<PurchaseOrder>()
                            .eq(PurchaseOrder::getId, id)
                            .eq(PurchaseOrder::getDeleted, 0)
                            .eq(PurchaseOrder::getStatus, recheckSnapshot.latestOrder().getStatus())
                            .set(PurchaseOrder::getDeleted, 1)
                            .set(PurchaseOrder::getUpdatedBy, resolveCurrentUserId())
                            .set(PurchaseOrder::getUpdatedAt, LocalDateTime.now())
            );
            if (updatedRows <= 0) {
                throw BizException.badRequest(CONCURRENT_DELETE_BLOCK_MESSAGE);
            }

            ReleaseMergeLockedPlanResult mergeLockReleaseResult = releaseMergeLockedPurchasePlans(id);
            PurchaseOrder deletedOrder = getOrderById(id, true, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.DELETE,
                    order.getId(),
                    order.getOrderNo(),
                    "删除采购订单：" + order.getOrderNo(),
                    deleteBeforeData,
                    JSONUtil.toJsonStr(buildDeleteAuditPayload(
                            deletedOrder,
                            recheckSnapshot.checkResult(),
                            "logical_delete",
                            true,
                            null,
                            mergeLockReleaseResult
                    ))
            );
            log.info("删除采购订单成功: id={}, logicalDelete=true", id);
        } catch (Exception ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.DELETE,
                    order.getId(),
                    order.getOrderNo(),
                    "采购订单逻辑删除失败",
                    deleteBeforeData,
                    JSONUtil.toJsonStr(buildDeleteAuditPayload(
                            recheckSnapshot.latestOrder(),
                            recheckSnapshot.checkResult(),
                            "logical_delete",
                            false,
                            ex.getMessage(),
                            null
                    )),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    @Override
    public List<PurchaseOrderItemVO> getItems(Long id) {
        getOrderById(id, true);
        return loadRawOrderItems(Collections.singletonList(id)).getOrDefault(id, Collections.emptyList());
    }

    @Override
    public List<PurchaseOrderLinkedInboundRecordVO> listLinkedInboundRecords(Long id) {
        PurchaseOrder order = getOrderById(id, true, true);
        if (isDeleted(order) && !dataScopeService.isAdminUser()) {
            throw BizException.notFound("采购订单不存在");
        }
        return loadLinkedInboundRecords(order);
    }

    @Override
    public List<PurchaseOrderSupplierOptionVO> listSupplierOptions(Long orgId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        Long tenantId = resolveTenantId();
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (orgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, supplier_name AS name, contact_name AS contactName, contact_phone AS contactPhone " +
                        "FROM scm_supplier WHERE deleted = 0 AND status = 'active' " +
                        "AND tenant_id = ? " +
                        "AND (license_expires_at IS NULL OR license_expires_at >= NOW()) " +
                        "AND (food_license_expires_at IS NULL OR food_license_expires_at >= NOW())"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (targetOrgId != null) {
            sql.append(" AND org_id = ?");
            args.add(targetOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        sql.append(" ORDER BY supplier_name ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<PurchaseOrderSupplierOptionVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            PurchaseOrderSupplierOptionVO vo = new PurchaseOrderSupplierOptionVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(asString(row.get("name")));
            vo.setContactName(asString(row.get("contactName")));
            vo.setContactPhone(asString(row.get("contactPhone")));
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<PurchaseOrderMaterialOptionVO> listMaterialOptions(Long orgId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        Long tenantId = resolveTenantId();
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (orgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder(
                "SELECT m.id, m.material_name AS name, m.unit, m.spec, " +
                        "COALESCE((SELECT AVG(oi.unit_price) " +
                        "          FROM scm_purchase_order_item oi " +
                        "          JOIN scm_purchase_order po ON po.id = oi.order_id AND po.deleted = 0 " +
                        "          WHERE oi.material_id = m.id AND po.org_id = m.org_id), 0) AS referencePrice " +
                        "FROM wms_material m " +
                        "WHERE m.deleted = 0 AND m.status = 'active' AND m.tenant_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (targetOrgId != null) {
            sql.append(" AND m.org_id = ?");
            args.add(targetOrgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND m.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        sql.append(" ORDER BY m.material_name ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<PurchaseOrderMaterialOptionVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            PurchaseOrderMaterialOptionVO vo = new PurchaseOrderMaterialOptionVO();
            vo.setId(toLong(row.get("id")));
            vo.setName(asString(row.get("name")));
            vo.setUnit(asString(row.get("unit")));
            vo.setSpec(asString(row.get("spec")));
            vo.setReferencePrice(scalePrice(toBigDecimal(row.get("referencePrice"))));
            list.add(vo);
        }
        return list;
    }

    @Override
    public List<SelectablePurchasePlanVO> listSelectablePlans(Long orgId, String keyword, Long excludeOrderId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (orgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        String aggregateSql = buildPlanItemAggregateSql(excludeOrderId);
        StringBuilder sql = new StringBuilder(
                "SELECT p.id, p.plan_no AS planNo, p.plan_name AS planName, p.org_id AS orgId, o.org_name AS orgName, " +
                        "SUM(GREATEST(i.plan_qty - COALESCE(gen.ordered_qty, 0), 0)) AS remainingQuantity, " +
                        "SUM(GREATEST(i.estimate_amount - COALESCE(gen.ordered_amount, 0), 0)) AS remainingAmount " +
                        "FROM scm_purchase_plan p " +
                        "JOIN scm_purchase_plan_item i ON i.plan_id = p.id " +
                        "LEFT JOIN sys_organization o ON o.id = p.org_id AND o.deleted = 0 " +
                        "LEFT JOIN (" + aggregateSql + ") gen ON gen.plan_item_id = i.id " +
                        "WHERE p.deleted = 0 AND p.status = 'approved'"
        );

        List<Object> args = new ArrayList<>();
        if (excludeOrderId != null) {
            args.add(excludeOrderId);
        }
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
                "ORDER BY p.plan_date DESC, p.created_at DESC, p.id DESC");

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

    @Override
    public List<PurchaseOrderPlanItemOptionVO> listPlanItems(Long orgId, List<Long> planIds, Long excludeOrderId) {
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        Long targetOrgId = resolveRequestedOrgId(orgId, allowedOrgIds);
        if (orgId != null && targetOrgId == null) {
            return Collections.emptyList();
        }
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        return loadPlanItemOptionsResult(targetOrgId, allowedOrgIds, planIds, excludeOrderId).getOptions();
    }

    private PurchaseOrder getOrderById(Long id, boolean checkDataScope) {
        return getOrderById(id, checkDataScope, false);
    }

    private PurchaseOrder getOrderById(Long id, boolean checkDataScope, boolean allowDeleted) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, order_no AS orderNo, plan_id AS planId, supplier_id AS supplierId, " +
                        "supplier_name AS supplierName, order_date AS orderDate, total_amount AS totalAmount, " +
                        "expected_delivery_at AS expectedDeliveryAt, actual_delivery_at AS actualDeliveryAt, " +
                        "delivery_address AS deliveryAddress, logistics_no AS logisticsNo, logistics_company AS logisticsCompany, " +
                        "logistics_status AS logisticsStatus, logistics_remark AS logisticsRemark, " +
                        "logistics_source_type AS logisticsSourceType, logistics_sync_payload AS logisticsSyncPayload, " +
                        "shipped_at AS shippedAt, arrived_at AS arrivedAt, " +
                        "logistics_attachment_name AS logisticsAttachmentName, logistics_attachment_url AS logisticsAttachmentUrl, " +
                        "inspection_report_no AS inspectionReportNo, inspection_result AS inspectionResult, " +
                        "inspection_agency AS inspectionAgency, inspection_at AS inspectionAt, inspection_remark AS inspectionRemark, " +
                        "inspection_source_type AS inspectionSourceType, inspection_sync_payload AS inspectionSyncPayload, " +
                        "inspection_attachment_name AS inspectionAttachmentName, inspection_attachment_url AS inspectionAttachmentUrl, " +
                        "attachment_name AS attachmentName, attachment_url AS attachmentUrl, remark, status, " +
                        "approved_by AS approvedBy, approved_at AS approvedAt, approve_remark AS approveRemark, " +
                        "void_reason AS voidReason, void_requested_by AS voidRequestedBy, void_requested_at AS voidRequestedAt, " +
                        "void_audit_by AS voidAuditBy, void_audit_at AS voidAuditAt, void_audit_remark AS voidAuditRemark, " +
                        "trace_batch_id AS traceBatchId, trace_origin AS traceOrigin, trace_remark AS traceRemark, " +
                        "trace_source_type AS traceSourceType, trace_sync_payload AS traceSyncPayload, " +
                        "trace_attachment_name AS traceAttachmentName, trace_attachment_url AS traceAttachmentUrl, " +
                        "org_id AS orgId, tenant_id AS tenantId, created_by AS createdBy, created_at AS createdAt, " +
                        "updated_by AS updatedBy, updated_at AS updatedAt, deleted " +
                        "FROM scm_purchase_order WHERE id = ?",
                id
        );
        if (rows.isEmpty()) {
            throw BizException.notFound("采购订单不存在");
        }
        PurchaseOrder order = mapPurchaseOrder(rows.get(0));
        if (!allowDeleted && isDeleted(order)) {
            throw BizException.notFound("采购订单不存在");
        }
        if (checkDataScope) {
            ensureOrgAllowed(order.getOrgId());
        }
        return order;
    }

    private PurchaseOrder mapPurchaseOrder(Map<String, Object> row) {
        PurchaseOrder order = new PurchaseOrder();
        order.setId(toLong(row.get("id")));
        order.setOrderNo(asString(row.get("orderNo")));
        order.setPlanId(toLong(row.get("planId")));
        order.setSupplierId(toLong(row.get("supplierId")));
        order.setSupplierName(asString(row.get("supplierName")));
        order.setOrderDate(toLocalDate(row.get("orderDate")));
        order.setTotalAmount(toBigDecimal(row.get("totalAmount")));
        order.setExpectedDeliveryAt(toLocalDateTime(row.get("expectedDeliveryAt")));
        order.setActualDeliveryAt(toLocalDateTime(row.get("actualDeliveryAt")));
        order.setDeliveryAddress(asString(row.get("deliveryAddress")));
        order.setLogisticsNo(asString(row.get("logisticsNo")));
        order.setLogisticsCompany(asString(row.get("logisticsCompany")));
        order.setLogisticsStatus(asString(row.get("logisticsStatus")));
        order.setLogisticsRemark(asString(row.get("logisticsRemark")));
        order.setLogisticsSourceType(asString(row.get("logisticsSourceType")));
        order.setLogisticsSyncPayload(asString(row.get("logisticsSyncPayload")));
        order.setShippedAt(toLocalDateTime(row.get("shippedAt")));
        order.setArrivedAt(toLocalDateTime(row.get("arrivedAt")));
        order.setLogisticsAttachmentName(asString(row.get("logisticsAttachmentName")));
        order.setLogisticsAttachmentUrl(asString(row.get("logisticsAttachmentUrl")));
        order.setInspectionReportNo(asString(row.get("inspectionReportNo")));
        order.setInspectionResult(asString(row.get("inspectionResult")));
        order.setInspectionAgency(asString(row.get("inspectionAgency")));
        order.setInspectionAt(toLocalDateTime(row.get("inspectionAt")));
        order.setInspectionRemark(asString(row.get("inspectionRemark")));
        order.setInspectionSourceType(asString(row.get("inspectionSourceType")));
        order.setInspectionSyncPayload(asString(row.get("inspectionSyncPayload")));
        order.setInspectionAttachmentName(asString(row.get("inspectionAttachmentName")));
        order.setInspectionAttachmentUrl(asString(row.get("inspectionAttachmentUrl")));
        order.setAttachmentName(asString(row.get("attachmentName")));
        order.setAttachmentUrl(asString(row.get("attachmentUrl")));
        order.setRemark(asString(row.get("remark")));
        order.setStatus(asString(row.get("status")));
        order.setApprovedBy(toLong(row.get("approvedBy")));
        order.setApprovedAt(toLocalDateTime(row.get("approvedAt")));
        order.setApproveRemark(asString(row.get("approveRemark")));
        order.setVoidReason(asString(row.get("voidReason")));
        order.setVoidRequestedBy(toLong(row.get("voidRequestedBy")));
        order.setVoidRequestedAt(toLocalDateTime(row.get("voidRequestedAt")));
        order.setVoidAuditBy(toLong(row.get("voidAuditBy")));
        order.setVoidAuditAt(toLocalDateTime(row.get("voidAuditAt")));
        order.setVoidAuditRemark(asString(row.get("voidAuditRemark")));
        order.setTraceBatchId(asString(row.get("traceBatchId")));
        order.setTraceOrigin(asString(row.get("traceOrigin")));
        order.setTraceRemark(asString(row.get("traceRemark")));
        order.setTraceSourceType(asString(row.get("traceSourceType")));
        order.setTraceSyncPayload(asString(row.get("traceSyncPayload")));
        order.setTraceAttachmentName(asString(row.get("traceAttachmentName")));
        order.setTraceAttachmentUrl(asString(row.get("traceAttachmentUrl")));
        order.setOrgId(toLong(row.get("orgId")));
        order.setTenantId(toLong(row.get("tenantId")));
        order.setCreatedBy(toLong(row.get("createdBy")));
        order.setCreatedAt(toLocalDateTime(row.get("createdAt")));
        order.setUpdatedBy(toLong(row.get("updatedBy")));
        order.setUpdatedAt(toLocalDateTime(row.get("updatedAt")));
        order.setDeleted(toInteger(row.get("deleted")));
        return order;
    }

    private PurchaseOrderVO toVO(PurchaseOrder order) {
        PurchaseOrderVO vo = new PurchaseOrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setSupplierId(order.getSupplierId());
        vo.setSupplierName(buildHistoricalSupplierName(order.getSupplierName(), resolveSupplierCurrentStatus(order.getSupplierId())));
        vo.setOrgId(order.getOrgId());
        vo.setOrgName(resolveOrgName(order.getOrgId()));
        vo.setCreatedById(order.getCreatedBy());
        vo.setOrderDate(formatDate(order.getOrderDate()));
        vo.setExpectedArrival(formatDate(order.getExpectedDeliveryAt()));
        vo.setTotalAmount(scaleAmount(order.getTotalAmount()));
        vo.setLogisticsCompany(order.getLogisticsCompany());
        vo.setLogisticsTrackingNo(order.getLogisticsNo());
        vo.setLogisticsStatus(order.getLogisticsStatus());
        vo.setLogisticsRemark(order.getLogisticsRemark());
        vo.setLogisticsSourceType(order.getLogisticsSourceType());
        vo.setLogisticsSyncPayload(order.getLogisticsSyncPayload());
        vo.setShippedAt(formatDateTime(order.getShippedAt()));
        vo.setArrivedAt(formatDateTime(order.getArrivedAt()));
        vo.setLogisticsAttachmentName(order.getLogisticsAttachmentName());
        vo.setLogisticsAttachmentUrl(order.getLogisticsAttachmentUrl());
        vo.setLogisticsAttachments(loadOrderMaintenanceAttachments(order, ATTACHMENT_TYPE_LOGISTICS));
        vo.setInspectionReportNo(order.getInspectionReportNo());
        vo.setInspectionResult(order.getInspectionResult());
        vo.setInspectionAgency(order.getInspectionAgency());
        vo.setInspectionAt(formatDateTime(order.getInspectionAt()));
        vo.setInspectionRemark(order.getInspectionRemark());
        vo.setInspectionSourceType(order.getInspectionSourceType());
        vo.setInspectionSyncPayload(order.getInspectionSyncPayload());
        vo.setInspectionAttachmentName(order.getInspectionAttachmentName());
        vo.setInspectionAttachmentUrl(order.getInspectionAttachmentUrl());
        List<PurchaseOrderAttachmentVO> inspectionAttachments = loadOrderMaintenanceAttachments(order, ATTACHMENT_TYPE_INSPECTION);
        vo.setInspectionAttachments(inspectionAttachments);
        vo.setInspectionFilled(isInspectionInfoFilled(order, inspectionAttachments));
        vo.setAttachmentName(order.getAttachmentName());
        vo.setAttachmentUrl(order.getAttachmentUrl());
        vo.setStatus(order.getStatus());
        vo.setDeleted(isDeleted(order));
        vo.setRemark(order.getRemark());
        vo.setCreatedAt(formatDateTime(order.getCreatedAt()));
        vo.setUpdatedAt(formatDateTime(order.getUpdatedAt()));
        vo.setAuditAt(formatDateTime(order.getApprovedAt()));
        vo.setAuditRemark(order.getApproveRemark());
        vo.setVoidReason(order.getVoidReason());
        vo.setVoidRequestedAt(formatDateTime(order.getVoidRequestedAt()));
        vo.setVoidAuditAt(formatDateTime(order.getVoidAuditAt()));
        vo.setVoidAuditRemark(order.getVoidAuditRemark());
        vo.setTraceBatchId(order.getTraceBatchId());
        vo.setTraceOrigin(order.getTraceOrigin());
        vo.setTraceRemark(order.getTraceRemark());
        vo.setTraceSourceType(order.getTraceSourceType());
        vo.setTraceSyncPayload(order.getTraceSyncPayload());
        vo.setTraceAttachmentName(order.getTraceAttachmentName());
        vo.setTraceAttachmentUrl(order.getTraceAttachmentUrl());
        List<PurchaseOrderAttachmentVO> traceabilityAttachments = loadOrderMaintenanceAttachments(order, ATTACHMENT_TYPE_TRACEABILITY);
        vo.setTraceabilityAttachments(traceabilityAttachments);
        vo.setTraceabilityFilled(isTraceabilityInfoFilled(order, traceabilityAttachments));
        return vo;
    }

    private String resolveSupplierCurrentStatus(Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        List<String> rows = jdbcTemplate.query(
                "SELECT status FROM scm_supplier WHERE id = ? AND deleted = 0 LIMIT 1",
                (rs, rowNum) -> rs.getString("status"),
                supplierId
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String buildHistoricalSupplierName(String supplierName, String supplierStatus) {
        if (StrUtil.isBlank(supplierName)) {
            return supplierName;
        }
        if ("disabled".equals(supplierStatus)) {
            return supplierName + " [禁用]";
        }
        if ("cancelled".equals(supplierStatus)) {
            return supplierName + " [已注销]";
        }
        return supplierName;
    }

    private void loadRelatedPlans(PurchaseOrderVO vo) {
        LinkedHashMap<Long, PurchaseOrderRelatedPlanVO> relatedPlanMap = new LinkedHashMap<>();
        for (PurchaseOrderItemVO item : vo.getItems()) {
            if (item.getPlanId() == null) {
                continue;
            }
            relatedPlanMap.computeIfAbsent(item.getPlanId(), key -> {
                PurchaseOrderRelatedPlanVO planVO = new PurchaseOrderRelatedPlanVO();
                planVO.setId(item.getPlanId());
                planVO.setPlanNo(item.getPlanNo());
                planVO.setPlanName(item.getPlanName());
                planVO.setOrgName(item.getPlanOrgName());
                return planVO;
            });
        }
        List<Map<String, Object>> lockedPlanRows = jdbcTemplate.queryForList(
                "SELECT p.id, p.plan_no AS planNo, p.plan_name AS planName, o.org_name AS orgName " +
                        "FROM scm_purchase_plan p " +
                        "LEFT JOIN sys_organization o ON o.id = p.org_id AND o.deleted = 0 " +
                        "WHERE p.deleted = 0 AND p.merge_order_id = ? " +
                        "ORDER BY p.id ASC",
                vo.getId()
        );
        for (Map<String, Object> row : lockedPlanRows) {
            Long planId = toLong(row.get("id"));
            PurchaseOrderRelatedPlanVO planVO = new PurchaseOrderRelatedPlanVO();
            planVO.setId(planId);
            planVO.setPlanNo(asString(row.get("planNo")));
            planVO.setPlanName(asString(row.get("planName")));
            planVO.setOrgName(asString(row.get("orgName")));
            relatedPlanMap.put(planId, planVO);
        }
        vo.setRelatedPlanIds(new ArrayList<>(relatedPlanMap.keySet()));
        vo.setRelatedPlans(new ArrayList<>(relatedPlanMap.values()));
    }

    private Map<Long, List<PurchaseOrderItemVO>> loadOrderItemsForDetail(List<Long> orderIds) {
        Map<Long, List<PurchaseOrderItemVO>> rawItemMap = loadRawOrderItems(orderIds);
        if (rawItemMap.isEmpty()) {
            return rawItemMap;
        }

        Set<Long> mergeGeneratedOrderIds = loadMergeGeneratedOrderIds(orderIds);
        if (mergeGeneratedOrderIds.isEmpty()) {
            return rawItemMap;
        }

        Map<Long, List<PurchaseOrderItemVO>> result = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            List<PurchaseOrderItemVO> rawItems = rawItemMap.getOrDefault(orderId, Collections.emptyList());
            if (!mergeGeneratedOrderIds.contains(orderId)) {
                result.put(orderId, rawItems);
                continue;
            }
            result.put(orderId, mergeOrderItemsByMaterial(rawItems));
        }
        return result;
    }

    private Map<Long, List<PurchaseOrderItemVO>> loadRawOrderItems(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = "SELECT oi.id, oi.order_id AS orderId, oi.plan_item_id AS planItemId, pi.plan_id AS planId, " +
                "pp.plan_no AS planNo, pp.plan_name AS planName, org.org_name AS planOrgName, oi.material_id AS materialId, " +
                "oi.material_name AS materialName, oi.material_spec AS spec, oi.material_unit AS unit, " +
                "oi.order_qty AS quantity, oi.unit_price AS unitPrice, oi.total_amount AS subtotal, " +
                "oi.received_qty AS receivedQty, " +
                "COALESCE(oi.inbound_qty, 0) AS inboundQty, " +
                "COALESCE(oi.order_qty, 0) - COALESCE(oi.inbound_qty, 0) AS remainingInboundQty, " +
                "oi.remark " +
                "FROM scm_purchase_order_item oi " +
                "LEFT JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                "LEFT JOIN scm_purchase_plan pp ON pp.id = pi.plan_id " +
                "LEFT JOIN sys_organization org ON org.id = pp.org_id AND org.deleted = 0 " +
                "WHERE oi.order_id IN (" + placeholders(orderIds.size()) + ") " +
                "ORDER BY oi.order_id ASC, oi.id ASC";

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, orderIds.toArray());
        Map<Long, List<PurchaseOrderItemVO>> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            PurchaseOrderItemVO vo = new PurchaseOrderItemVO();
            vo.setId(toLong(row.get("id")));
            vo.setPlanItemId(toLong(row.get("planItemId")));
            vo.setPlanId(toLong(row.get("planId")));
            vo.setPlanNo(asString(row.get("planNo")));
            vo.setPlanName(asString(row.get("planName")));
            vo.setPlanOrgName(asString(row.get("planOrgName")));
            vo.setMaterialId(toLong(row.get("materialId")));
            vo.setMaterialName(asString(row.get("materialName")));
            vo.setSpec(asString(row.get("spec")));
            vo.setUnit(asString(row.get("unit")));
            vo.setQuantity(scaleQuantity(toBigDecimal(row.get("quantity"))));
            vo.setUnitPrice(scalePrice(toBigDecimal(row.get("unitPrice"))));
            vo.setUnitCost(vo.getUnitPrice());
            vo.setSubtotal(scaleAmount(toBigDecimal(row.get("subtotal"))));
            vo.setReceivedQty(scaleQuantity(toBigDecimal(row.get("receivedQty"))));
            vo.setInboundQty(scaleQuantity(toBigDecimal(row.get("inboundQty"))));
            vo.setRemainingInboundQty(scaleQuantity(toBigDecimal(row.get("remainingInboundQty"))));
            vo.setRemark(asString(row.get("remark")));
            map.computeIfAbsent(toLong(row.get("orderId")), key -> new ArrayList<>()).add(vo);
        }
        return map;
    }

    private Set<Long> loadMergeGeneratedOrderIds(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<Long> rows = jdbcTemplate.query(
                "SELECT DISTINCT merge_order_id FROM scm_purchase_plan " +
                        "WHERE deleted = 0 AND merge_order_id IN (" + placeholders(orderIds.size()) + ")",
                (rs, rowNum) -> rs.getLong("merge_order_id"),
                orderIds.toArray()
        );
        return new LinkedHashSet<>(rows);
    }

    private List<PurchaseOrderItemVO> mergeOrderItemsByMaterial(List<PurchaseOrderItemVO> rawItems) {
        if (rawItems == null || rawItems.isEmpty()) {
            return Collections.emptyList();
        }

        LinkedHashMap<Long, List<PurchaseOrderItemVO>> groupedMap = new LinkedHashMap<>();
        for (PurchaseOrderItemVO item : rawItems) {
            Long materialId = item.getMaterialId();
            if (materialId == null) {
                groupedMap.put(-System.identityHashCode(item) * 1L, Collections.singletonList(item));
                continue;
            }
            groupedMap.computeIfAbsent(materialId, key -> new ArrayList<>()).add(item);
        }

        List<PurchaseOrderItemVO> mergedItems = new ArrayList<>();
        for (List<PurchaseOrderItemVO> group : groupedMap.values()) {
            if (group.size() == 1 || group.get(0).getMaterialId() == null) {
                mergedItems.add(group.get(0));
                continue;
            }

            PurchaseOrderItemVO representative = selectRepresentativeOrderItem(group);
            PurchaseOrderItemVO merged = new PurchaseOrderItemVO();
            merged.setId(representative.getId());
            merged.setPlanItemId(representative.getPlanItemId());
            merged.setPlanId(representative.getPlanId());
            merged.setPlanNo(buildMergedPlanNo(group));
            merged.setPlanName(buildMergedPlanName(group));
            merged.setPlanOrgName(buildMergedPlanOrgName(group));
            merged.setMaterialId(representative.getMaterialId());
            merged.setMaterialName(representative.getMaterialName());
            merged.setSpec(representative.getSpec());
            merged.setUnit(representative.getUnit());
            merged.setQuantity(scaleQuantity(sumOrderItemField(group, PurchaseOrderItemVO::getQuantity)));
            merged.setUnitPrice(representative.getUnitPrice());
            merged.setUnitCost(representative.getUnitCost());
            merged.setSubtotal(scaleAmount(sumOrderItemField(group, PurchaseOrderItemVO::getSubtotal)));
            merged.setReceivedQty(scaleQuantity(sumOrderItemField(group, PurchaseOrderItemVO::getReceivedQty)));
            merged.setInboundQty(scaleQuantity(sumOrderItemField(group, PurchaseOrderItemVO::getInboundQty)));
            merged.setRemainingInboundQty(scaleQuantity(sumOrderItemField(group, PurchaseOrderItemVO::getRemainingInboundQty)));
            merged.setRemark(resolveMergedRemark(group));
            mergedItems.add(merged);
        }
        return mergedItems;
    }

    private PurchaseOrderItemVO selectRepresentativeOrderItem(List<PurchaseOrderItemVO> items) {
        PurchaseOrderItemVO representative = items.get(0);
        for (PurchaseOrderItemVO item : items) {
            if (item.getPlanItemId() != null && representative.getPlanItemId() != null) {
                if (item.getPlanItemId() < representative.getPlanItemId()) {
                    representative = item;
                }
            } else if (representative.getPlanItemId() == null && item.getPlanItemId() != null) {
                representative = item;
            }
        }
        return representative;
    }

    private void fillOrderFields(
            PurchaseOrder order,
            Long orgId,
            Long supplierId,
            String orderDate,
            String expectedArrival,
            String remark,
            String status,
            Long originalSupplierId
    ) {
        if (orgId == null) {
            throw BizException.badRequest("所属组织不能为空");
        }
        Map<String, Object> supplier = fetchSupplier(
                supplierId,
                orgId,
                Objects.equals(originalSupplierId, supplierId),
                order.getId() == null ? AuditOperationType.CREATE : AuditOperationType.UPDATE,
                order.getId()
        );

        LocalDate orderDateValue = parseDate(orderDate, "订单日期");
        LocalDate expectedArrivalValue = parseDate(expectedArrival, "预计到货日期");
        if (expectedArrivalValue.isBefore(orderDateValue)) {
            throw BizException.badRequest("预计到货日期不能早于订单日期");
        }

        order.setOrgId(orgId);
        order.setSupplierId(supplierId);
        order.setSupplierName(asString(supplier.get("supplier_name")));
        order.setOrderDate(orderDateValue);
        order.setExpectedDeliveryAt(expectedArrivalValue.atStartOfDay());
        order.setRemark(StrUtil.isBlank(remark) ? null : remark.trim());
        order.setStatus(status);
        if (!STATUS_APPROVED.equals(status) && !STATUS_REJECTED.equals(status)) {
            order.setApprovedBy(null);
            order.setApprovedAt(null);
            order.setApproveRemark(null);
        }
        order.setTenantId(resolveTenantId());
    }

    private void syncNullableEditableFields(Long orderId, PurchaseOrder order) {
        jdbcTemplate.update(
                "UPDATE scm_purchase_order SET remark = ?, attachment_name = ?, attachment_url = ? WHERE id = ?",
                order.getRemark(),
                order.getAttachmentName(),
                order.getAttachmentUrl(),
                orderId
        );
    }

    private void applyAttachment(PurchaseOrder order, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        validateAttachmentFile(file);
        String oldAttachmentUrl = order.getAttachmentUrl();
        order.setAttachmentName(resolveAttachmentName(file));
        order.setAttachmentUrl(fileStorageService.upload(file, ATTACHMENT_DIR));
        if (StrUtil.isNotBlank(oldAttachmentUrl) && !StrUtil.equals(oldAttachmentUrl, order.getAttachmentUrl())) {
            fileStorageService.delete(oldAttachmentUrl);
        }
    }

    private BuildResult buildOrderItems(
            Long orgId,
            List<Long> relatedPlanIds,
            List<PurchaseOrderItemDTO> itemDTOs,
            Long excludeOrderId
    ) {
        if (itemDTOs == null || itemDTOs.isEmpty()) {
            throw BizException.badRequest("请至少填写一条物料明细");
        }

        List<Long> normalizedPlanIds = relatedPlanIds == null
                ? Collections.emptyList()
                : relatedPlanIds.stream().filter(Objects::nonNull).distinct().toList();
        List<Long> allowedOrgIds = resolveAllowedOrgIds();
        PlanItemLoadResult planItemResult = null;
        Map<Long, PurchaseOrderPlanItemOptionVO> planItemMap = new HashMap<>();
        if (!normalizedPlanIds.isEmpty()) {
            validateRelatedPlansForOrder(normalizedPlanIds, allowedOrgIds, excludeOrderId);
            planItemResult = loadPlanItemOptionsResult(null, allowedOrgIds, normalizedPlanIds, excludeOrderId);
            for (PurchaseOrderPlanItemOptionVO item : planItemResult.getOptions()) {
                planItemMap.put(item.getId(), item);
            }
        }

        List<PurchaseOrderItem> items = new ArrayList<>();
        LinkedHashMap<Long, Long> relatedPlanMap = new LinkedHashMap<>();
        for (PurchaseOrderItemDTO dto : itemDTOs) {
            if (dto == null || dto.getMaterialId() == null || dto.getQuantity() == null) {
                continue;
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setPlanItemId(dto.getPlanItemId());
            item.setOrderQty(scaleQuantity(dto.getQuantity()));
            item.setReceivedQty(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
            item.setUnitPrice(scalePrice(dto.getUnitPrice()));
            item.setRemark(StrUtil.isBlank(dto.getRemark()) ? null : dto.getRemark().trim());

            if (dto.getPlanItemId() != null) {
                PurchaseOrderPlanItemOptionVO planItem = planItemMap.get(dto.getPlanItemId());
                if (planItem == null) {
                    throw BizException.badRequest("存在不可关联的采购计划物料");
                }
                if (item.getOrderQty().compareTo(planItem.getRemainingQuantity()) > 0) {
                    throw BizException.badRequest("物料“" + planItem.getMaterialName() + "”订购数量不能超过可关联数量");
                }
                if (!normalizedPlanIds.isEmpty() && !normalizedPlanIds.contains(planItem.getPlanId())) {
                    throw BizException.badRequest("存在未勾选采购计划的关联明细");
                }
                if (dto.getUnitPrice() == null) {
                    item.setUnitPrice(scalePrice(planItem.getUnitPrice()));
                }

                List<PurchaseOrderPlanItemOptionVO> sourceItems = planItemResult == null
                        ? Collections.singletonList(planItem)
                        : planItemResult.getSourceItems(planItem.getId());
                BigDecimal remainingToAllocate = item.getOrderQty();
                for (PurchaseOrderPlanItemOptionVO sourceItem : sourceItems) {
                    BigDecimal sourceRemaining = scaleQuantity(sourceItem.getRemainingQuantity());
                    if (sourceRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }
                    BigDecimal allocatedQuantity = remainingToAllocate.min(sourceRemaining);
                    if (allocatedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
                        continue;
                    }

                    PurchaseOrderItem splitItem = new PurchaseOrderItem();
                    splitItem.setPlanItemId(sourceItem.getId());
                    splitItem.setOrderQty(scaleQuantity(allocatedQuantity));
                    splitItem.setReceivedQty(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                    splitItem.setUnitPrice(item.getUnitPrice());
                    splitItem.setRemark(item.getRemark());
                    splitItem.setMaterialId(sourceItem.getMaterialId());
                    splitItem.setMaterialName(sourceItem.getMaterialName());
                    splitItem.setMaterialSpec(sourceItem.getSpec());
                    splitItem.setMaterialUnit(sourceItem.getUnit());
                    splitItem.setTotalAmount(scaleAmount(splitItem.getOrderQty().multiply(splitItem.getUnitPrice())));
                    items.add(splitItem);
                    relatedPlanMap.put(sourceItem.getPlanId(), sourceItem.getPlanId());
                    remainingToAllocate = remainingToAllocate.subtract(allocatedQuantity);
                    if (remainingToAllocate.compareTo(BigDecimal.ZERO) <= 0) {
                        break;
                    }
                }
                if (remainingToAllocate.compareTo(BigDecimal.ZERO) > 0) {
                    throw BizException.badRequest("物料“" + planItem.getMaterialName() + "”订购数量不能超过可关联数量");
                }
                continue;
            } else {
                Map<String, Object> material = fetchMaterial(dto.getMaterialId(), orgId);
                item.setMaterialId(dto.getMaterialId());
                item.setMaterialName(asString(material.get("material_name")));
                item.setMaterialSpec(StrUtil.isNotBlank(dto.getSpec())
                        ? dto.getSpec().trim()
                        : asString(material.get("spec")));
                item.setMaterialUnit(asString(material.get("unit")));
            }
            item.setTotalAmount(scaleAmount(item.getOrderQty().multiply(item.getUnitPrice())));
            items.add(item);
        }

        if (items.isEmpty()) {
            throw BizException.badRequest("请至少填写一条有效的物料明细");
        }

        BigDecimal totalAmount = items.stream()
                .map(PurchaseOrderItem::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        materialCategoryCoefficientLockService.assertUnlockedByMaterialIds(
                items.stream().map(PurchaseOrderItem::getMaterialId).toList(),
                "保存采购订单"
        );

        Long primaryPlanId = relatedPlanMap.isEmpty() ? null : relatedPlanMap.keySet().iterator().next();
        return new BuildResult(items, scaleAmount(totalAmount), primaryPlanId);
    }

    private void saveOrderItems(Long orderId, List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (PurchaseOrderItem item : items) {
            item.setOrderId(orderId);
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO scm_purchase_order_item (" +
                        "order_id, plan_item_id, material_id, material_name, material_spec, material_unit, " +
                        "order_qty, received_qty, unit_price, total_amount, remark" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                items,
                items.size(),
                (ps, item) -> {
                    ps.setLong(1, orderId);
                    if (item.getPlanItemId() == null) {
                        ps.setNull(2, java.sql.Types.BIGINT);
                    } else {
                        ps.setLong(2, item.getPlanItemId());
                    }
                    ps.setLong(3, item.getMaterialId());
                    ps.setString(4, item.getMaterialName());
                    ps.setString(5, item.getMaterialSpec());
                    ps.setString(6, item.getMaterialUnit());
                    ps.setBigDecimal(7, item.getOrderQty());
                    ps.setBigDecimal(8, item.getReceivedQty());
                    ps.setBigDecimal(9, item.getUnitPrice());
                    ps.setBigDecimal(10, item.getTotalAmount());
                    ps.setString(11, item.getRemark());
                }
        );
    }

    private void validateRelatedPlansForOrder(List<Long> planIds, List<Long> allowedOrgIds, Long excludeOrderId) {
        if (planIds == null || planIds.isEmpty()) {
            return;
        }

        String sql = "SELECT id, plan_no AS planNo, status, org_id AS orgId, deleted " +
                "FROM scm_purchase_plan WHERE id IN (" + placeholders(planIds.size()) + ") FOR UPDATE";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, planIds.toArray());
        if (rows.size() != planIds.size()) {
            throw BizException.badRequest("存在不可关联的采购计划");
        }

        Map<Long, Map<String, Object>> planMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            planMap.put(toLong(row.get("id")), row);
        }

        for (Long planId : planIds) {
            Map<String, Object> row = planMap.get(planId);
            if (row == null) {
                throw BizException.badRequest("存在不可关联的采购计划");
            }
            if (toInteger(row.get("deleted")) != 0) {
                throw BizException.badRequest("采购计划“" + asString(row.get("planNo")) + "”已被删除，不允许关联");
            }
            if (!STATUS_APPROVED.equals(asString(row.get("status")))) {
                throw BizException.badRequest("采购计划“" + asString(row.get("planNo")) + "”当前不是已审核状态，不允许关联");
            }
            if (allowedOrgIds != null && !allowedOrgIds.contains(toLong(row.get("orgId")))) {
                throw BizException.forbidden("无权访问采购计划“" + asString(row.get("planNo")) + "”");
            }
        }
    }

    private PlanItemLoadResult loadPlanItemOptionsResult(
            Long orgId,
            List<Long> allowedOrgIds,
            List<Long> planIds,
            Long excludeOrderId
    ) {
        List<PurchaseOrderPlanItemOptionVO> rawOptions = loadRawPlanItemOptions(orgId, allowedOrgIds, planIds, excludeOrderId);
        if (rawOptions.isEmpty()) {
            return new PlanItemLoadResult(Collections.emptyList(), Collections.emptyMap());
        }
        if (excludeOrderId == null || !isMergeGeneratedOrder(excludeOrderId)) {
            Map<Long, List<PurchaseOrderPlanItemOptionVO>> sourceMap = new LinkedHashMap<>();
            for (PurchaseOrderPlanItemOptionVO option : rawOptions) {
                sourceMap.put(option.getId(), Collections.singletonList(option));
            }
            return new PlanItemLoadResult(rawOptions, sourceMap);
        }
        return aggregatePlanItemOptionsByMaterial(rawOptions);
    }

    private List<PurchaseOrderPlanItemOptionVO> loadRawPlanItemOptions(
            Long orgId,
            List<Long> allowedOrgIds,
            List<Long> planIds,
            Long excludeOrderId
    ) {
        if (planIds == null || planIds.isEmpty()) {
            return Collections.emptyList();
        }
        if (orgId == null && allowedOrgIds != null && allowedOrgIds.isEmpty()) {
            return Collections.emptyList();
        }

        String aggregateSql = buildPlanItemAggregateSql(excludeOrderId, planIds.size());
        StringBuilder sql = new StringBuilder(
                "SELECT i.id, i.plan_id AS planId, p.plan_no AS planNo, p.plan_name AS planName, org.org_name AS planOrgName, " +
                        "i.material_id AS materialId, i.material_name AS materialName, i.material_spec AS spec, " +
                        "i.material_unit AS unit, i.plan_qty AS planQuantity, COALESCE(gen.ordered_qty, 0) AS orderedQuantity, " +
                        "GREATEST(i.plan_qty - COALESCE(gen.ordered_qty, 0), 0) AS remainingQuantity, " +
                        "i.estimate_price AS unitPrice, i.remark " +
                        "FROM scm_purchase_plan_item i " +
                        "JOIN scm_purchase_plan p ON p.id = i.plan_id AND p.deleted = 0 AND p.status = 'approved' " +
                        "LEFT JOIN sys_organization org ON org.id = p.org_id AND org.deleted = 0 " +
                        "LEFT JOIN (" + aggregateSql + ") gen ON gen.plan_item_id = i.id " +
                        "WHERE i.plan_id IN (" + placeholders(planIds.size()) + ")"
        );

        List<Object> args = new ArrayList<>();
        if (excludeOrderId != null) {
            args.add(excludeOrderId);
        }
        args.addAll(planIds);
        args.addAll(planIds);

        if (orgId != null) {
            sql.append(" AND p.org_id = ?");
            args.add(orgId);
        } else if (allowedOrgIds != null) {
            sql.append(" AND p.org_id IN (").append(placeholders(allowedOrgIds.size())).append(")");
            args.addAll(allowedOrgIds);
        }
        sql.append(" AND GREATEST(i.plan_qty - COALESCE(gen.ordered_qty, 0), 0) > 0 " +
                "ORDER BY p.plan_date DESC, p.id DESC, i.id ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<PurchaseOrderPlanItemOptionVO> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            PurchaseOrderPlanItemOptionVO vo = new PurchaseOrderPlanItemOptionVO();
            vo.setId(toLong(row.get("id")));
            vo.setPlanId(toLong(row.get("planId")));
            vo.setPlanNo(asString(row.get("planNo")));
            vo.setPlanName(asString(row.get("planName")));
            vo.setPlanOrgName(asString(row.get("planOrgName")));
            vo.setMaterialId(toLong(row.get("materialId")));
            vo.setMaterialName(asString(row.get("materialName")));
            vo.setSpec(asString(row.get("spec")));
            vo.setUnit(asString(row.get("unit")));
            vo.setPlanQuantity(scaleQuantity(toBigDecimal(row.get("planQuantity"))));
            vo.setOrderedQuantity(scaleQuantity(toBigDecimal(row.get("orderedQuantity"))));
            vo.setRemainingQuantity(scaleQuantity(toBigDecimal(row.get("remainingQuantity"))));
            vo.setUnitPrice(scalePrice(toBigDecimal(row.get("unitPrice"))));
            vo.setRemark(asString(row.get("remark")));
            list.add(vo);
        }
        return list;
    }

    private boolean isMergeGeneratedOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_purchase_plan WHERE deleted = 0 AND merge_order_id = ?",
                Integer.class,
                orderId
        );
        return count != null && count > 0;
    }

    private PlanItemLoadResult aggregatePlanItemOptionsByMaterial(List<PurchaseOrderPlanItemOptionVO> rawOptions) {
        LinkedHashMap<Long, List<PurchaseOrderPlanItemOptionVO>> groupedMap = new LinkedHashMap<>();
        for (PurchaseOrderPlanItemOptionVO option : rawOptions) {
            Long materialId = option.getMaterialId();
            if (materialId == null) {
                groupedMap.put(-System.identityHashCode(option) * 1L, Collections.singletonList(option));
                continue;
            }
            groupedMap.computeIfAbsent(materialId, key -> new ArrayList<>()).add(option);
        }

        List<PurchaseOrderPlanItemOptionVO> aggregatedOptions = new ArrayList<>();
        Map<Long, List<PurchaseOrderPlanItemOptionVO>> sourceMap = new LinkedHashMap<>();
        for (List<PurchaseOrderPlanItemOptionVO> group : groupedMap.values()) {
            if (group.size() == 1 || group.get(0).getMaterialId() == null) {
                PurchaseOrderPlanItemOptionVO option = group.get(0);
                aggregatedOptions.add(option);
                sourceMap.put(option.getId(), Collections.singletonList(option));
                continue;
            }

            PurchaseOrderPlanItemOptionVO representative = selectRepresentativePlanItemOption(group);
            PurchaseOrderPlanItemOptionVO merged = new PurchaseOrderPlanItemOptionVO();
            merged.setId(representative.getId());
            merged.setPlanId(representative.getPlanId());
            merged.setPlanNo(buildMergedPlanNoFromOptions(group));
            merged.setPlanName(buildMergedPlanNameFromOptions(group));
            merged.setPlanOrgName(buildMergedPlanOrgNameFromOptions(group));
            merged.setMaterialId(representative.getMaterialId());
            merged.setMaterialName(representative.getMaterialName());
            merged.setSpec(representative.getSpec());
            merged.setUnit(representative.getUnit());
            merged.setPlanQuantity(scaleQuantity(sumPlanItemOptionField(group, PurchaseOrderPlanItemOptionVO::getPlanQuantity)));
            merged.setOrderedQuantity(scaleQuantity(sumPlanItemOptionField(group, PurchaseOrderPlanItemOptionVO::getOrderedQuantity)));
            merged.setRemainingQuantity(scaleQuantity(sumPlanItemOptionField(group, PurchaseOrderPlanItemOptionVO::getRemainingQuantity)));
            merged.setUnitPrice(representative.getUnitPrice());
            merged.setRemark(resolveMergedPlanItemRemark(group));
            aggregatedOptions.add(merged);
            sourceMap.put(merged.getId(), sortPlanItemSources(group));
        }
        return new PlanItemLoadResult(aggregatedOptions, sourceMap);
    }

    private String buildPlanItemAggregateSql(Long excludeOrderId) {
        return "SELECT oi.plan_item_id, SUM(oi.order_qty) AS ordered_qty, SUM(oi.total_amount) AS ordered_amount " +
                "FROM scm_purchase_order_item oi " +
                "JOIN scm_purchase_order po ON po.id = oi.order_id AND po.deleted = 0 " +
                "WHERE oi.plan_item_id IS NOT NULL " +
                (excludeOrderId != null ? "AND po.id <> ? " : "") +
                "GROUP BY oi.plan_item_id";
    }

    private String buildPlanItemAggregateSql(Long excludeOrderId, int planIdSize) {
        return "SELECT oi.plan_item_id, SUM(oi.order_qty) AS ordered_qty, SUM(oi.total_amount) AS ordered_amount " +
                "FROM scm_purchase_order_item oi " +
                "JOIN scm_purchase_order po ON po.id = oi.order_id AND po.deleted = 0 " +
                "JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                "WHERE oi.plan_item_id IS NOT NULL " +
                (excludeOrderId != null ? "AND po.id <> ? " : "") +
                "AND pi.plan_id IN (" + placeholders(planIdSize) + ") " +
                "GROUP BY oi.plan_item_id";
    }

    private String resolveOrderNo(String requestedOrderNo, Long excludeId) {
        String candidate = StrUtil.isBlank(requestedOrderNo) ? generateOrderNo() : requestedOrderNo.trim();
        if (!existsOrderNo(candidate, excludeId)) {
            return candidate;
        }

        String generated;
        do {
            generated = generateOrderNo();
        } while (existsOrderNo(generated, excludeId));
        return generated;
    }

    private String generateOrderNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
        return "PO-" + datePart + "-" + suffix;
    }

    private boolean existsOrderNo(String orderNo, Long excludeId) {
        LambdaQueryWrapper<PurchaseOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PurchaseOrder::getOrderNo, orderNo)
                .ne(excludeId != null, PurchaseOrder::getId, excludeId);
        Long count = purchaseOrderMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private void ensureEditable(String status) {
        if (!STATUS_PENDING_SUBMIT.equals(status) && !STATUS_PENDING_APPROVE.equals(status) && !STATUS_REJECTED.equals(status)) {
            throw BizException.badRequest("仅草稿、待审核或已驳回状态可编辑");
        }
    }

    private void ensureDeleteAuthorized(PurchaseOrder order) {
        if (dataScopeService.isAdminUser()) {
            return;
        }

        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && Objects.equals(order.getCreatedBy(), currentUserId)) {
            return;
        }
        if (hasPermission(PURCHASE_ORDER_DELETE_PERMISSION)) {
            return;
        }
        throw BizException.forbidden(PURCHASE_ORDER_DELETE_PERMISSION_MESSAGE);
    }

    private PurchaseOrderDeleteCheckResult validateDeletePreconditions(PurchaseOrder order, Long orderId, boolean concurrentRecheck) {
        PurchaseOrderDeleteCheckResult checkResult = inspectDeletePreconditions(order, orderId);
        if (!checkResult.passed()) {
            throwDeleteBlocked(concurrentRecheck, checkResult.blockedReason());
        }
        return checkResult;
    }

    private PurchaseOrderDeleteCheckResult inspectDeletePreconditions(PurchaseOrder order, Long orderId) {
        int linkedInboundOrderCount = countLinkedInboundOrders(orderId);
        int linkedReceiptRecordCount = countLinkedReceiptRecords(orderId);

        String status = order == null ? null : order.getStatus();
        String blockedReason = null;
        if (order == null || order.getId() == null) {
            blockedReason = "采购订单不存在";
        } else if (isDeleted(order)) {
            blockedReason = REPEAT_DELETE_BLOCK_MESSAGE;
        } else if (STATUS_REJECTED.equals(status)) {
            blockedReason = REJECTED_DELETE_BLOCK_MESSAGE;
        } else if (STATUS_APPROVED.equals(status)) {
            blockedReason = APPROVED_DELETE_BLOCK_MESSAGE;
        } else if (!STATUS_PENDING_SUBMIT.equals(status) && !STATUS_PENDING_APPROVE.equals(status)) {
            blockedReason = "仅草稿或待审核状态可删除";
        } else if (linkedInboundOrderCount > 0 || linkedReceiptRecordCount > 0) {
            blockedReason = DELETE_BLOCK_MESSAGE;
        }

        return new PurchaseOrderDeleteCheckResult(
                linkedInboundOrderCount,
                linkedReceiptRecordCount,
                blockedReason
        );
    }

    private Map<String, Object> buildDeleteAuditPayload(
            PurchaseOrder order,
            PurchaseOrderDeleteCheckResult checkResult,
            String stage,
            boolean operationApplied,
            String blockedReason,
            ReleaseMergeLockedPlanResult mergeLockReleaseResult
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order == null ? null : order.getId());
        payload.put("orderNo", order == null ? null : order.getOrderNo());
        payload.put("status", order == null ? null : order.getStatus());
        payload.put("deleted", order != null && isDeleted(order));
        payload.put("orgId", order == null ? null : order.getOrgId());
        payload.put("tenantId", order == null ? null : order.getTenantId());
        payload.put("createdById", order == null ? null : order.getCreatedBy());
        payload.put("stage", stage);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        payload.put("deleteMode", "logical");
        payload.put("blockedReason", blockedReason);
        List<Long> relatedPlanIds = order == null || order.getId() == null
                ? Collections.emptyList()
                : loadRelatedPlanIdsByOrderId(order.getId());
        payload.put("relatedPlanIds", relatedPlanIds);
        payload.put("relatedPlanCount", relatedPlanIds.size());
        payload.put("purchasePlanAvailabilityReleased", operationApplied);
        payload.put("planOccupancyReleaseRule", "deleted_order_excluded_from_purchase_plan_statistics");
        if (checkResult != null) {
            payload.put("linkedInboundOrderCount", checkResult.linkedInboundOrderCount());
            payload.put("linkedReceiptRecordCount", checkResult.linkedReceiptRecordCount());
            payload.put("linkedDownstreamDocumentCount",
                    checkResult.linkedInboundOrderCount() + checkResult.linkedReceiptRecordCount());
            payload.put("validationPassed", checkResult.passed());
            payload.put("checkBlockedReason", checkResult.blockedReason());
        } else {
            payload.put("linkedInboundOrderCount", 0);
            payload.put("linkedReceiptRecordCount", 0);
            payload.put("linkedDownstreamDocumentCount", 0);
            payload.put("validationPassed", false);
            payload.put("checkBlockedReason", null);
        }
        payload.put("releasedMergeLockedPlanIds", mergeLockReleaseResult == null ? Collections.emptyList() : mergeLockReleaseResult.getPlanIds());
        payload.put("releasedMergeLockedPlanCount", mergeLockReleaseResult == null ? 0 : mergeLockReleaseResult.getReleasedPlanCount());
        return payload;
    }

    private PurchaseOrderDeleteRecheckSnapshot performDeleteConcurrentRecheck(Long id) {
        PurchaseOrder latestOrder = getOrderById(id, true, true);
        PurchaseOrderDeleteCheckResult checkResult = inspectDeletePreconditions(latestOrder, id);
        String beforeData = JSONUtil.toJsonStr(buildDeleteAuditPayload(
                latestOrder,
                checkResult,
                "concurrent_final_recheck",
                false,
                null,
                null
        ));
        try {
            PurchaseOrderDeleteCheckResult validated = validateDeletePreconditions(latestOrder, id, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.DELETE,
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    "采购订单删除并发二次重校验通过",
                    beforeData,
                    JSONUtil.toJsonStr(buildDeleteAuditPayload(
                            latestOrder,
                            validated,
                            "concurrent_final_recheck",
                            false,
                            null,
                            null
                    ))
            );
            return new PurchaseOrderDeleteRecheckSnapshot(latestOrder, validated);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.DELETE,
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    "采购订单删除并发二次重校验拦截",
                    beforeData,
                    JSONUtil.toJsonStr(buildDeleteAuditPayload(
                            latestOrder,
                            checkResult,
                            "concurrent_final_recheck",
                            false,
                            ex.getMessage(),
                            null
                    )),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private void throwDeleteBlocked(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? CONCURRENT_DELETE_BLOCK_MESSAGE : defaultMessage);
    }

    private PurchaseOrderReverseAuditCheckResult validateReverseAuditPreconditions(
            PurchaseOrder order,
            Long orderId,
            boolean lockInboundRows,
            boolean concurrentRecheck
    ) {
        PurchaseOrderReverseAuditCheckResult checkResult = inspectReverseAuditPreconditions(order, orderId, lockInboundRows);
        assertReverseAuditCheckPassed(checkResult, concurrentRecheck);
        return checkResult;
    }

    private void assertReverseAuditCheckPassed(
            PurchaseOrderReverseAuditCheckResult checkResult,
            boolean concurrentRecheck
    ) {
        if (checkResult == null || !checkResult.passed()) {
            throwReverseAuditBlocked(
                    concurrentRecheck,
                    checkResult == null ? REVERSE_AUDIT_STATUS_BLOCK_MESSAGE : checkResult.blockedReason()
            );
        }
    }

    private PurchaseOrderReverseAuditCheckResult inspectReverseAuditPreconditions(
            PurchaseOrder order,
            Long orderId,
            boolean lockInboundRows
    ) {
        List<Long> relatedPlanIds = order == null || orderId == null
                ? Collections.emptyList()
                : loadRelatedPlanIdsByOrderId(orderId);
        List<LinkedInboundOrderState> linkedInbounds = loadLinkedInboundStatesForReverseAudit(order, lockInboundRows);
        List<LinkedInboundOrderState> blockedInbounds = new ArrayList<>();
        for (LinkedInboundOrderState linkedInbound : linkedInbounds) {
            if (isReverseAuditBlockedInboundStatus(linkedInbound.status(), linkedInbound.postStatus())) {
                blockedInbounds.add(linkedInbound);
            }
        }

        int linkedReceiptRecordCount = countLinkedReceiptRecords(orderId);
        int linkedInventoryRecordCount = countLinkedInventoryRecordsForReverseAudit(linkedInbounds);
        SupplierReverseAuditState supplierState = loadReverseAuditSupplierState(order);

        String blockedReason = null;
        if (order == null || order.getId() == null || isDeleted(order)) {
            blockedReason = "采购订单不存在";
        } else if (!isReverseAuditAllowedStatus(order.getStatus())) {
            blockedReason = REVERSE_AUDIT_STATUS_BLOCK_MESSAGE;
        } else if (!supplierState.passed()) {
            blockedReason = REVERSE_AUDIT_BLOCK_MESSAGE;
        } else if (!blockedInbounds.isEmpty()) {
            blockedReason = REVERSE_AUDIT_LINKED_INBOUND_STATUS_BLOCK_MESSAGE;
        } else if (linkedReceiptRecordCount > 0 || linkedInventoryRecordCount > 0) {
            blockedReason = REVERSE_AUDIT_BLOCK_MESSAGE;
        }

        return new PurchaseOrderReverseAuditCheckResult(
                relatedPlanIds.size(),
                relatedPlanIds,
                linkedInbounds.size(),
                linkedReceiptRecordCount,
                linkedInventoryRecordCount,
                linkedInbounds,
                blockedInbounds,
                supplierState,
                blockedReason
        );
    }

    private Map<String, Object> buildReverseAuditAuditPayload(
            PurchaseOrder order,
            PurchaseOrderReverseAuditCheckResult checkResult,
            String reason,
            String stage,
            boolean operationApplied,
            String blockedReason
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String currentStatus = order == null ? null : order.getStatus();
        payload.put("orderId", order == null ? null : order.getId());
        payload.put("orderNo", order == null ? null : order.getOrderNo());
        payload.put("supplierId", order == null ? null : order.getSupplierId());
        payload.put("supplierName", order == null ? null : order.getSupplierName());
        payload.put("status", currentStatus);
        payload.put("reverseAuditReason", reason);
        payload.put("orgId", order == null ? null : order.getOrgId());
        payload.put("tenantId", order == null ? null : order.getTenantId());
        payload.put("stage", stage);
        payload.put("operationApplied", operationApplied);
        payload.put("checkedAt", formatDateTime(LocalDateTime.now()));
        payload.put("operatorId", UserContext.getUserId());
        payload.put("operatorName", UserContext.getUsername());
        payload.put("reviewNodeStatus", resolveReverseAuditReviewNodeStatus(currentStatus));
        if (checkResult != null) {
            payload.put("linkedPlanCount", checkResult.linkedPlanCount());
            payload.put("linkedPlanIds", checkResult.linkedPlanIds());
            payload.put("linkedInboundCount", checkResult.linkedInboundCount());
            payload.put("linkedReceiptRecordCount", checkResult.linkedReceiptRecordCount());
            payload.put("linkedInventoryRecordCount", checkResult.linkedInventoryRecordCount());
            payload.put("affectedInboundCount", checkResult.linkedInboundCount());
            payload.put("linkedInbounds", buildLinkedInboundPayloadList(checkResult.linkedInbounds()));
            payload.put("blockedInboundCount", checkResult.blockedInbounds().size());
            payload.put("blockedInbounds", buildLinkedInboundPayloadList(checkResult.blockedInbounds()));
            payload.put("supplierValidationPassed", checkResult.supplierState().passed());
            payload.put("supplierStatus", checkResult.supplierState().status());
            payload.put("supplierBlockedReason", checkResult.supplierState().blockedReason());
            payload.put("validationPassed", checkResult.passed());
            payload.put("checkBlockedReason", checkResult.blockedReason());
        } else {
            payload.put("linkedPlanCount", 0);
            payload.put("linkedPlanIds", Collections.emptyList());
            payload.put("linkedInboundCount", 0);
            payload.put("linkedReceiptRecordCount", 0);
            payload.put("linkedInventoryRecordCount", 0);
            payload.put("affectedInboundCount", 0);
            payload.put("linkedInbounds", Collections.emptyList());
            payload.put("blockedInboundCount", 0);
            payload.put("blockedInbounds", Collections.emptyList());
            payload.put("supplierValidationPassed", false);
            payload.put("supplierStatus", null);
            payload.put("supplierBlockedReason", null);
            payload.put("validationPassed", false);
            payload.put("checkBlockedReason", null);
        }
        payload.put("relatedPlanRelationChanged", false);
        payload.put("linkedInboundStatusChanged", false);
        payload.put("linkedInboundApprovalTaskChanged", false);
        payload.put("blockedReason", blockedReason);
        return payload;
    }

    private PurchaseOrderReverseAuditRecheckSnapshot performReverseAuditConcurrentRecheck(Long id, String reason) {
        PurchaseOrder latestOrder = lockSingleOrderForReverseAudit(id);
        PurchaseOrderReverseAuditCheckResult checkResult = inspectReverseAuditPreconditions(latestOrder, id, true);
        String beforeData = JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                latestOrder,
                checkResult,
                reason,
                "concurrent_final_recheck",
                false,
                null
        ));
        try {
            PurchaseOrderReverseAuditCheckResult validated = validateReverseAuditPreconditions(latestOrder, id, true, true);
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    "关联入库单状态校验通过",
                    beforeData,
                    JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                            latestOrder,
                            validated,
                            reason,
                            "concurrent_final_recheck",
                            false,
                            null
                    ))
            );
            return new PurchaseOrderReverseAuditRecheckSnapshot(latestOrder, validated);
        } catch (BizException ex) {
            auditLogService.log(
                    AuditModule.SCM_PURCHASE_ORDER,
                    AuditOperationType.STATUS_CHANGE,
                    latestOrder.getId(),
                    latestOrder.getOrderNo(),
                    resolveReverseAuditBlockedAuditAction(ex.getMessage()),
                    beforeData,
                    JSONUtil.toJsonStr(buildReverseAuditAuditPayload(
                            latestOrder,
                            checkResult,
                            reason,
                            "concurrent_final_recheck",
                            false,
                            ex.getMessage()
                    )),
                    "failure",
                    ex.getMessage()
            );
            throw ex;
        }
    }

    private PurchaseOrder lockSingleOrderForReverseAudit(Long orderId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, order_no AS orderNo, plan_id AS planId, supplier_id AS supplierId, " +
                        "supplier_name AS supplierName, order_date AS orderDate, total_amount AS totalAmount, " +
                        "expected_delivery_at AS expectedDeliveryAt, actual_delivery_at AS actualDeliveryAt, " +
                        "delivery_address AS deliveryAddress, logistics_no AS logisticsNo, logistics_company AS logisticsCompany, " +
                        "logistics_status AS logisticsStatus, logistics_remark AS logisticsRemark, " +
                        "logistics_source_type AS logisticsSourceType, logistics_sync_payload AS logisticsSyncPayload, " +
                        "shipped_at AS shippedAt, arrived_at AS arrivedAt, " +
                        "logistics_attachment_name AS logisticsAttachmentName, logistics_attachment_url AS logisticsAttachmentUrl, " +
                        "inspection_report_no AS inspectionReportNo, inspection_result AS inspectionResult, " +
                        "inspection_agency AS inspectionAgency, inspection_at AS inspectionAt, inspection_remark AS inspectionRemark, " +
                        "inspection_source_type AS inspectionSourceType, inspection_sync_payload AS inspectionSyncPayload, " +
                        "inspection_attachment_name AS inspectionAttachmentName, inspection_attachment_url AS inspectionAttachmentUrl, " +
                        "attachment_name AS attachmentName, attachment_url AS attachmentUrl, remark, status, " +
                        "approved_by AS approvedBy, approved_at AS approvedAt, approve_remark AS approveRemark, " +
                        "void_reason AS voidReason, void_requested_by AS voidRequestedBy, void_requested_at AS voidRequestedAt, " +
                        "void_audit_by AS voidAuditBy, void_audit_at AS voidAuditAt, void_audit_remark AS voidAuditRemark, " +
                        "trace_batch_id AS traceBatchId, trace_origin AS traceOrigin, trace_remark AS traceRemark, " +
                        "trace_source_type AS traceSourceType, trace_sync_payload AS traceSyncPayload, " +
                        "trace_attachment_name AS traceAttachmentName, trace_attachment_url AS traceAttachmentUrl, " +
                        "org_id AS orgId, tenant_id AS tenantId, created_by AS createdBy, created_at AS createdAt, " +
                        "updated_by AS updatedBy, updated_at AS updatedAt, deleted " +
                        "FROM scm_purchase_order WHERE id = ? FOR UPDATE",
                orderId
        );
        if (rows.isEmpty()) {
            throw BizException.notFound("采购订单不存在");
        }
        PurchaseOrder order = mapPurchaseOrder(rows.get(0));
        if (isDeleted(order)) {
            throw BizException.notFound("采购订单不存在");
        }
        ensureOrgAllowed(order.getOrgId());
        return order;
    }

    private List<LinkedInboundOrderState> loadLinkedInboundStatesForReverseAudit(
            PurchaseOrder order,
            boolean lockRows
    ) {
        if (order == null || order.getId() == null) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT o.id AS inboundOrderId, o.inbound_no AS inboundNo, o.status, " +
                        "COALESCE(NULLIF(TRIM(o.post_status), ''), 'unposted') AS postStatus " +
                        "FROM wms_inbound_order o " +
                        "WHERE o.deleted = 0 AND o.source_type = 'purchase' AND o.tenant_id = ? " +
                        "AND (o.source_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(order.getTenantId());
        args.add(order.getId());
        if (hasTableColumn("wms_inbound_order", "source_order_id")) {
            sql.append(" OR o.source_order_id = ?");
            args.add(order.getId());
        }
        sql.append(") ORDER BY o.id ASC");
        if (lockRows) {
            sql.append(" FOR UPDATE");
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<LinkedInboundOrderState> linkedInbounds = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            Long inboundOrderId = toLong(row.get("inboundOrderId"));
            if (inboundOrderId == null) {
                continue;
            }
            linkedInbounds.add(new LinkedInboundOrderState(
                    inboundOrderId,
                    asString(row.get("inboundNo")),
                    asString(row.get("status")),
                    asString(row.get("postStatus"))
            ));
        }
        return linkedInbounds;
    }

    private int countLinkedInventoryRecordsForReverseAudit(List<LinkedInboundOrderState> linkedInbounds) {
        if (linkedInbounds == null || linkedInbounds.isEmpty()) {
            return 0;
        }
        List<Long> inboundIds = new ArrayList<>(linkedInbounds.size());
        for (LinkedInboundOrderState linkedInbound : linkedInbounds) {
            if (linkedInbound != null && linkedInbound.inboundOrderId() != null) {
                inboundIds.add(linkedInbound.inboundOrderId());
            }
        }
        if (inboundIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM wms_inventory " +
                        "WHERE source_type = 'purchase' AND source_id IN (" + placeholders(inboundIds.size()) + ")",
                Integer.class,
                inboundIds.toArray()
        );
        return count == null ? 0 : count;
    }

    private SupplierReverseAuditState loadReverseAuditSupplierState(PurchaseOrder order) {
        if (order == null || order.getSupplierId() == null || order.getOrgId() == null) {
            return new SupplierReverseAuditState(null, null, null, null, null, "供应商不存在或已停用");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, supplier_name, status, license_expires_at AS licenseExpiresAt, " +
                        "food_license_expires_at AS foodLicenseExpiresAt " +
                        "FROM scm_supplier WHERE id = ? AND org_id = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                order.getSupplierId(),
                order.getOrgId(),
                order.getTenantId()
        );
        if (rows.isEmpty()) {
            return new SupplierReverseAuditState(
                    order.getSupplierId(),
                    order.getSupplierName(),
                    null,
                    null,
                    null,
                    "供应商不存在或已停用"
            );
        }
        Map<String, Object> supplier = rows.get(0);
        String status = asString(supplier.get("status"));
        LocalDateTime licenseExpiresAt = toLocalDateTime(supplier.get("licenseExpiresAt"));
        LocalDateTime foodLicenseExpiresAt = toLocalDateTime(supplier.get("foodLicenseExpiresAt"));
        String blockedReason = null;
        if (!SUPPLIER_STATUS_ACTIVE.equals(status)) {
            blockedReason = "供应商状态不可用";
        } else if (isSupplierQualificationExpired(supplier)) {
            blockedReason = SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE;
        }
        return new SupplierReverseAuditState(
                toLong(supplier.get("id")),
                asString(supplier.get("supplier_name")),
                status,
                licenseExpiresAt,
                foodLicenseExpiresAt,
                blockedReason
        );
    }

    private boolean isReverseAuditAllowedStatus(String status) {
        return STATUS_APPROVED.equals(status) || STATUS_PENDING_RECEIPT.equals(status);
    }

    private boolean isReverseAuditBlockedInboundStatus(String status, String postStatus) {
        String normalizedStatus = normalizeOptionalText(status);
        if (normalizedStatus == null || INBOUND_STATUS_CANCELLED.equals(normalizedStatus)) {
            return false;
        }
        if (INBOUND_STATUS_DRAFT.equals(normalizedStatus)
                || INBOUND_STATUS_PENDING.equals(normalizedStatus)
                || INBOUND_STATUS_APPROVED.equals(normalizedStatus)
                || INBOUND_STATUS_COMPLETED.equals(normalizedStatus)
                || INBOUND_STATUS_REJECTED.equals(normalizedStatus)) {
            return true;
        }
        String normalizedPostStatus = normalizeOptionalText(postStatus);
        return INBOUND_STATUS_APPROVED.equals(normalizedStatus)
                && ("posted".equals(normalizedPostStatus) || "success".equals(normalizedPostStatus));
    }

    private String resolveReverseAuditReviewNodeStatus(String status) {
        if (STATUS_PENDING_APPROVE.equals(status)) {
            return "待审核";
        }
        if (STATUS_APPROVED.equals(status)) {
            return "已审核";
        }
        return status;
    }

    private List<Map<String, Object>> buildLinkedInboundPayloadList(List<LinkedInboundOrderState> linkedInbounds) {
        if (linkedInbounds == null || linkedInbounds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> payload = new ArrayList<>(linkedInbounds.size());
        for (LinkedInboundOrderState linkedInbound : linkedInbounds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("inboundOrderId", linkedInbound.inboundOrderId());
            item.put("inboundNo", linkedInbound.inboundNo());
            item.put("status", linkedInbound.status());
            item.put("postStatus", linkedInbound.postStatus());
            payload.add(item);
        }
        return payload;
    }

    private List<String> buildLinkedInboundNoList(List<LinkedInboundOrderState> linkedInbounds) {
        if (linkedInbounds == null || linkedInbounds.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> inboundNos = new ArrayList<>(linkedInbounds.size());
        for (LinkedInboundOrderState linkedInbound : linkedInbounds) {
            if (linkedInbound != null && StrUtil.isNotBlank(linkedInbound.inboundNo())) {
                inboundNos.add(linkedInbound.inboundNo());
            }
        }
        return inboundNos;
    }

    private String resolveReverseAuditBlockedAuditAction(String blockedReason) {
        if (Objects.equals(REVERSE_AUDIT_LINKED_INBOUND_STATUS_BLOCK_MESSAGE, blockedReason)) {
            return "关联入库单状态阻断";
        }
        return "采购订单反审核拦截";
    }

    private void throwReverseAuditBlocked(boolean concurrentRecheck, String defaultMessage) {
        throw BizException.badRequest(concurrentRecheck ? REVERSE_AUDIT_CONCURRENT_BLOCK_MESSAGE : defaultMessage);
    }

    private void ensureVoidApplicable(String status) {
        if (!STATUS_PENDING_APPROVE.equals(status) && !STATUS_APPROVED.equals(status)) {
            throw BizException.badRequest("仅待审核或已审核状态可发起作废申请");
        }
    }

    private void ensureVoidAuditable(String status) {
        if (!STATUS_PENDING_VOID_APPROVE.equals(status)) {
            throw BizException.badRequest("仅待作废审核状态可执行作废审核");
        }
    }

    private void ensureLogisticsMaintainable(String status) {
        ensurePostApproveMaintainable(status, "仅已审核、运输中、待入库状态可维护物流信息");
    }

    private void ensureSceneMaintainable(PurchaseOrder order, String scene) {
        String normalizedScene = normalizeScene(scene);
        if (BIZ_SCENE_LOGISTICS.equals(normalizedScene)) {
            ensureLogisticsMaintainable(order.getStatus());
            return;
        }
        if (BIZ_SCENE_INSPECTION.equals(normalizedScene)) {
            ensureInspectionMaintainable(order);
            return;
        }
        ensureTraceabilityMaintainable(order);
    }

    private void ensureInspectionMaintainable(PurchaseOrder order) {
        ensureCompletedMaintainable(order.getStatus(), "仅已审核、运输中、待入库、已完成状态可维护检测报告");
        if (STATUS_COMPLETED.equals(order.getStatus()) && isInspectionResultConfirmed(order)) {
            throw BizException.badRequest("当前订单检测结果已确认，不可修改");
        }
    }

    private void ensureTraceabilityMaintainable(PurchaseOrder order) {
        ensureCompletedMaintainable(order.getStatus(), "仅已审核、运输中、待入库、已完成状态可维护溯源信息");
        if (STATUS_COMPLETED.equals(order.getStatus()) && isTraceabilityInfoFilled(order)) {
            throw BizException.badRequest("已归档完成，不可修改");
        }
    }

    private boolean isLogisticsStatusLocked(PurchaseOrder order) {
        if (order == null || order.getId() == null || !LOGISTICS_STATUS_ARRIVED.equals(order.getLogisticsStatus())) {
            return false;
        }
        List<PurchaseOrderItemVO> items = loadRawOrderItems(Collections.singletonList(order.getId()))
                .getOrDefault(order.getId(), Collections.emptyList());
        if (items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(item -> scaleQuantity(item.getRemainingInboundQty()).compareTo(BigDecimal.ZERO) <= 0);
    }

    private boolean hasEditableLockedInboundOrder(PurchaseOrder order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(DISTINCT o.id) FROM wms_inbound_order o " +
                        "WHERE o.deleted = 0 AND o.source_type = 'purchase' AND o.tenant_id = ? AND o.org_id = ? " +
                        "AND o.status IN ('draft', 'pending', 'approved', 'completed') AND (o.source_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(order.getTenantId());
        args.add(order.getOrgId());
        args.add(order.getId());
        if (hasTableColumn("wms_inbound_order", "source_order_id")) {
            sql.append(" OR o.source_order_id = ?");
            args.add(order.getId());
        }
        sql.append(")");
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count != null && count > 0;
    }

    private void validateLogisticsRequiredFields(
            String logisticsStatus,
            String company,
            String trackingNo,
            LocalDateTime shippedAt,
            LocalDateTime arrivedAt,
            List<PurchaseOrderAttachmentDTO> attachments
    ) {
        boolean shipmentInfoRequired = LOGISTICS_STATUS_SHIPPED.equals(logisticsStatus)
                || LOGISTICS_STATUS_IN_TRANSIT.equals(logisticsStatus)
                || LOGISTICS_STATUS_ARRIVED.equals(logisticsStatus);
        if (shipmentInfoRequired) {
            if (StrUtil.isBlank(company)) {
                throw BizException.badRequest("请填写物流公司");
            }
            if (StrUtil.isBlank(trackingNo)) {
                throw BizException.badRequest("请填写物流单号");
            }
            if (shippedAt == null) {
                throw BizException.badRequest("请填写发货时间");
            }
        }
        if (LOGISTICS_STATUS_ARRIVED.equals(logisticsStatus)) {
            if (arrivedAt == null) {
                throw BizException.badRequest("请填写到货时间");
            }
            boolean hasAttachment = attachments != null
                    && attachments.stream().anyMatch(attachment -> attachment != null && StrUtil.isNotBlank(attachment.getUrl()));
            if (!hasAttachment) {
                throw BizException.badRequest("请上传物流附件");
            }
        }
    }

    private void validateInspectionRequiredFields(
            String inspectionResult,
            String reportNo,
            String agency,
            LocalDateTime inspectionAt,
            List<PurchaseOrderAttachmentDTO> attachments
    ) {
        if (!isInspectionConclusionResult(inspectionResult)) {
            return;
        }
        if (StrUtil.isBlank(reportNo)) {
            throw BizException.badRequest("请填写报告编号");
        }
        if (StrUtil.isBlank(agency)) {
            throw BizException.badRequest("请填写检测机构");
        }
        if (inspectionAt == null) {
            throw BizException.badRequest("请填写检测时间");
        }
        boolean hasAttachment = attachments != null
                && attachments.stream().anyMatch(attachment -> attachment != null && StrUtil.isNotBlank(attachment.getUrl()));
        if (!hasAttachment) {
            throw BizException.badRequest("请上传检测附件");
        }
    }

    private boolean isInspectionConclusionResult(String inspectionResult) {
        return INSPECTION_RESULT_QUALIFIED.equals(inspectionResult)
                || INSPECTION_RESULT_UNQUALIFIED.equals(inspectionResult);
    }

    private boolean isInspectionResultConfirmed(PurchaseOrder order) {
        return order != null && isInspectionConclusionResult(trimToNull(order.getInspectionResult()));
    }

    private void validateTraceabilityRequiredFields(
            String traceBatchId,
            String origin,
            List<PurchaseOrderAttachmentDTO> attachments
    ) {
        if (StrUtil.isBlank(traceBatchId)) {
            throw BizException.badRequest("请填写溯源批次码");
        }
        if (StrUtil.isBlank(origin)) {
            throw BizException.badRequest("请填写来源/产地");
        }
        boolean hasAttachment = attachments != null
                && attachments.stream().anyMatch(attachment -> attachment != null && StrUtil.isNotBlank(attachment.getUrl()));
        if (!hasAttachment) {
            throw BizException.badRequest("请上传溯源附件");
        }
    }

    private boolean isInspectionInfoFilled(PurchaseOrder order) {
        return isInspectionInfoFilled(order, null);
    }

    private boolean isInspectionInfoFilled(PurchaseOrder order, List<PurchaseOrderAttachmentVO> attachments) {
        if (order == null) {
            return false;
        }
        if (StrUtil.isNotBlank(order.getInspectionReportNo())
                || StrUtil.isNotBlank(order.getInspectionResult())
                || StrUtil.isNotBlank(order.getInspectionAgency())
                || order.getInspectionAt() != null
                || StrUtil.isNotBlank(order.getInspectionRemark())
                || StrUtil.isNotBlank(order.getInspectionSyncPayload())
                || StrUtil.isNotBlank(order.getInspectionAttachmentName())
                || StrUtil.isNotBlank(order.getInspectionAttachmentUrl())) {
            return true;
        }
        return hasMaintenanceAttachments(order.getId(), ATTACHMENT_TYPE_INSPECTION, attachments);
    }

    private boolean isTraceabilityInfoFilled(PurchaseOrder order) {
        return isTraceabilityInfoFilled(order, null);
    }

    private boolean isTraceabilityInfoFilled(PurchaseOrder order, List<PurchaseOrderAttachmentVO> attachments) {
        if (order == null) {
            return false;
        }
        if (StrUtil.isNotBlank(order.getTraceBatchId())
                || StrUtil.isNotBlank(order.getTraceOrigin())
                || StrUtil.isNotBlank(order.getTraceRemark())
                || StrUtil.isNotBlank(order.getTraceSyncPayload())
                || StrUtil.isNotBlank(order.getTraceAttachmentName())
                || StrUtil.isNotBlank(order.getTraceAttachmentUrl())) {
            return true;
        }
        return hasMaintenanceAttachments(order.getId(), ATTACHMENT_TYPE_TRACEABILITY, attachments);
    }

    private boolean hasMaintenanceAttachments(Long orderId, String attachmentType, List<PurchaseOrderAttachmentVO> attachments) {
        if (attachments != null) {
            return attachments.stream().anyMatch(attachment -> StrUtil.isNotBlank(attachment.getUrl()));
        }
        if (orderId == null) {
            return false;
        }
        Long count = purchaseOrderAttachmentMapper.selectCount(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .eq(PurchaseOrderAttachment::getOrderId, orderId)
                .eq(PurchaseOrderAttachment::getAttachmentType, attachmentType));
        return count != null && count > 0;
    }

    private void ensurePostApproveMaintainable(String status, String errorMessage) {
        if (STATUS_COMPLETED.equals(status) || STATUS_VOIDED.equals(status)) {
            throw BizException.badRequest("订单已完成或已作废，不允许修改附属信息");
        }
        if (!STATUS_APPROVED.equals(status) && !STATUS_DELIVERING.equals(status) && !STATUS_PENDING_RECEIPT.equals(status)) {
            throw BizException.badRequest(errorMessage);
        }
    }

    private void ensureCompletedMaintainable(String status, String errorMessage) {
        if (STATUS_VOIDED.equals(status)) {
            throw BizException.badRequest("订单已作废，不允许修改附属信息");
        }
        if (!STATUS_APPROVED.equals(status)
                && !STATUS_DELIVERING.equals(status)
                && !STATUS_PENDING_RECEIPT.equals(status)
                && !STATUS_COMPLETED.equals(status)) {
            throw BizException.badRequest(errorMessage);
        }
    }

    private String normalizeEditableStatus(String status) {
        String normalized = StrUtil.blankToDefault(StrUtil.trim(status), STATUS_PENDING_SUBMIT);
        if (!STATUS_PENDING_SUBMIT.equals(normalized) && !STATUS_PENDING_APPROVE.equals(normalized)) {
            throw BizException.badRequest("采购订单仅支持保存为草稿或提交待审核");
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

    private String normalizeSourceType(String sourceType) {
        String normalized = StrUtil.blankToDefault(trimToNull(sourceType), SOURCE_TYPE_MANUAL);
        if (!SOURCE_TYPE_MANUAL.equals(normalized) && !SOURCE_TYPE_THIRD_PARTY.equals(normalized)) {
            throw BizException.badRequest("数据来源仅支持 manual 或 third_party");
        }
        return normalized;
    }

    private String normalizeSceneSyncPayload(String sourceType, String syncPayload) {
        if (!SOURCE_TYPE_THIRD_PARTY.equals(sourceType)) {
            return null;
        }
        return trimToNull(syncPayload);
    }

    private Long normalizeThirdPartyIntegrationConfigId(String sourceType, Long integrationConfigId) {
        if (!SOURCE_TYPE_THIRD_PARTY.equals(sourceType)) {
            return null;
        }
        if (integrationConfigId == null) {
            throw BizException.badRequest("请选择同步方案");
        }
        return integrationConfigId;
    }

    private String resolveThirdPartyExternalNo(String sourceType, String integrationExternalNo, String fallbackExternalNo, String fieldLabel) {
        if (!SOURCE_TYPE_THIRD_PARTY.equals(sourceType)) {
            return null;
        }
        String externalNo = trimToNull(integrationExternalNo);
        if (externalNo == null) {
            externalNo = trimToNull(fallbackExternalNo);
        }
        if (StrUtil.isBlank(externalNo)) {
            throw BizException.badRequest("请填写" + fieldLabel);
        }
        return externalNo;
    }

    private String resolveSourceTypeLabel(String sourceType) {
        return SOURCE_TYPE_THIRD_PARTY.equals(sourceType) ? "第三方接口" : "手工录入";
    }

    private String normalizeLogisticsStatus(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            throw BizException.badRequest("物流状态不能为空");
        }
        if (!LOGISTICS_STATUS_PENDING.equals(normalized)
                && !LOGISTICS_STATUS_SHIPPED.equals(normalized)
                && !LOGISTICS_STATUS_IN_TRANSIT.equals(normalized)
                && !LOGISTICS_STATUS_ARRIVED.equals(normalized)) {
            throw BizException.badRequest("物流状态仅支持 pending、shipped、in_transit、arrived");
        }
        return normalized;
    }

    private String resolveStatusAfterLogistics(
            String currentStatus,
            String logisticsStatus,
            LocalDateTime shippedAt,
            LocalDateTime arrivedAt
    ) {
        if (arrivedAt != null || LOGISTICS_STATUS_ARRIVED.equals(logisticsStatus)) {
            return STATUS_PENDING_RECEIPT;
        }
        if (STATUS_DELIVERING.equals(currentStatus)) {
            return STATUS_DELIVERING;
        }
        if (shippedAt != null
                || LOGISTICS_STATUS_SHIPPED.equals(logisticsStatus)
                || LOGISTICS_STATUS_IN_TRANSIT.equals(logisticsStatus)) {
            return STATUS_DELIVERING;
        }
        return currentStatus;
    }

    private Map<String, Object> fetchSupplier(
            Long supplierId,
            Long orgId,
            boolean allowExpiredIfAlreadyLinked,
            AuditOperationType operationType,
            Long orderId
    ) {
        Long tenantId = resolveTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, supplier_name, status, license_expires_at AS licenseExpiresAt, " +
                        "food_license_expires_at AS foodLicenseExpiresAt " +
                        "FROM scm_supplier WHERE id = ? AND org_id = ? AND tenant_id = ? AND deleted = 0 LIMIT 1",
                supplierId, orgId, tenantId
        );
        if (rows.isEmpty()) {
            throw BizException.badRequest("所选供应商不存在或未审核");
        }
        Map<String, Object> supplier = rows.get(0);
        if (!SUPPLIER_STATUS_ACTIVE.equals(asString(supplier.get("status")))) {
            throw BizException.badRequest("所选供应商不存在或未审核");
        }
        if (isSupplierQualificationExpired(supplier) && !allowExpiredIfAlreadyLinked) {
            logSupplierQualificationBlocked(orderId, supplier, operationType);
            throw BizException.badRequest(SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE);
        }
        return supplier;
    }

    private boolean isSupplierQualificationExpired(Map<String, Object> supplier) {
        LocalDateTime now = LocalDateTime.now();
        return isExpiredAt(toLocalDateTime(supplier.get("licenseExpiresAt")), now)
                || isExpiredAt(toLocalDateTime(supplier.get("foodLicenseExpiresAt")), now);
    }

    private boolean isExpiredAt(LocalDateTime value, LocalDateTime now) {
        return value != null && value.isBefore(now);
    }

    private LocalDate toLocalDate(Object value) {
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
        return null;
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

    private void logSupplierQualificationBlocked(Long orderId, Map<String, Object> supplier, AuditOperationType operationType) {
        Map<String, Object> auditData = new LinkedHashMap<>();
        auditData.put("supplierId", toLong(supplier.get("id")));
        auditData.put("supplierName", asString(supplier.get("supplier_name")));
        auditData.put("licenseExpiresAt", formatDate(supplier.get("licenseExpiresAt")));
        auditData.put("foodLicenseExpiresAt", formatDate(supplier.get("foodLicenseExpiresAt")));
        auditData.put("rule", "supplier_qualification_expired_block");
        auditLogService.log(
                AuditModule.SCM_PURCHASE_ORDER,
                operationType,
                orderId,
                null,
                "采购订单供应商资质校验失败：关键资质已过期",
                null,
                JSONUtil.toJsonStr(auditData),
                "failure",
                SUPPLIER_QUALIFICATION_EXPIRED_MESSAGE
        );
    }

    private Map<String, Object> fetchMaterial(Long materialId, Long orgId) {
        Long tenantId = resolveTenantId();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, material_name, unit, spec FROM wms_material " +
                        "WHERE id = ? AND org_id = ? AND tenant_id = ? AND deleted = 0 AND status = 'active' LIMIT 1",
                materialId, orgId, tenantId
        );
        if (rows.isEmpty()) {
            throw BizException.badRequest("所选物料不存在或已停用");
        }
        return rows.get(0);
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

    private LocalDate parseDate(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            throw BizException.badRequest(fieldName + "不能为空");
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest(fieldName + "格式错误，正确格式应为 yyyy-MM-dd");
        }
    }

    private LocalDate parseNullableDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("订单日期格式错误，正确格式应为 yyyy-MM-dd");
        }
    }

    private LocalDateTime parseNullableDateTime(String value, String fieldName) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw BizException.badRequest(fieldName + "格式错误，正确格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private int countLinkedInboundOrders(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM wms_inbound_order " +
                        "WHERE deleted = 0 AND source_type = 'purchase' AND (source_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(orderId);
        if (hasTableColumn("wms_inbound_order", "source_order_id")) {
            sql.append(" OR source_order_id = ?");
            args.add(orderId);
        }
        sql.append(")");
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count == null ? 0 : count;
    }

    private List<PurchaseOrderLinkedInboundRecordVO> loadLinkedInboundRecords(PurchaseOrder order) {
        if (order == null || order.getId() == null) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT o.id AS inboundOrderId, o.inbound_no AS inboundNo, " +
                        "COALESCE(o.approved_at, o.submitted_at, o.created_at) AS inboundDate, " +
                        "o.status, COALESCE(NULLIF(TRIM(o.post_status), ''), 'unposted') AS postStatus, " +
                        "ii.material_name AS materialName, ii.spec, ii.unit, ii.quantity AS inboundQuantity, " +
                        "COALESCE(NULLIF(TRIM(o.submitter_name), ''), NULLIF(TRIM(se.real_name), ''), " +
                        "NULLIF(TRIM(au.real_name), ''), NULLIF(TRIM(au.username), ''), CAST(o.created_by AS CHAR)) AS operatorName, " +
                        "o.created_at AS createdAt " +
                        "FROM wms_inbound_order o " +
                        "JOIN wms_inbound_order_item ii ON ii.inbound_id = o.id " +
                        "LEFT JOIN auth_user au ON au.id = o.created_by AND au.deleted = 0 " +
                        "LEFT JOIN sys_employee se ON se.user_id = au.id AND se.deleted = 0 " +
                        "WHERE o.deleted = 0 AND o.source_type = 'purchase' AND o.tenant_id = ? AND (o.source_id = ?"
        );
        List<Object> args = new ArrayList<>();
        args.add(order.getTenantId());
        args.add(order.getId());
        if (hasTableColumn("wms_inbound_order", "source_order_id")) {
            sql.append(" OR o.source_order_id = ?");
            args.add(order.getId());
        }
        sql.append(") ORDER BY o.created_at DESC, o.id DESC, ii.id ASC");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
        List<PurchaseOrderLinkedInboundRecordVO> records = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            PurchaseOrderLinkedInboundRecordVO record = new PurchaseOrderLinkedInboundRecordVO();
            record.setInboundOrderId(toLong(row.get("inboundOrderId")));
            record.setInboundNo(asString(row.get("inboundNo")));
            record.setInboundDate(formatDateTime(row.get("inboundDate")));
            record.setStatus(asString(row.get("status")));
            record.setPostStatus(asString(row.get("postStatus")));
            record.setMaterialName(asString(row.get("materialName")));
            record.setSpec(asString(row.get("spec")));
            record.setUnit(asString(row.get("unit")));
            record.setInboundQuantity(scaleQuantity(toBigDecimal(row.get("inboundQuantity"))));
            record.setOperatorName(asString(row.get("operatorName")));
            record.setCreatedAt(formatDateTime(row.get("createdAt")));
            records.add(record);
        }
        return records;
    }

    private int countLinkedReceiptRecords(Long orderId) {
        if (orderId == null) {
            return 0;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM scm_receipt_record WHERE order_id = ?",
                Integer.class,
                orderId
        );
        return count == null ? 0 : count;
    }

    private boolean hasTableColumn(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName,
                columnName
        );
        return count != null && count > 0;
    }

    private List<Long> loadRelatedPlanIdsByOrderId(Long orderId) {
        if (orderId == null) {
            return Collections.emptyList();
        }
        List<Long> rows = jdbcTemplate.query(
                "SELECT DISTINCT pi.plan_id " +
                        "FROM scm_purchase_order_item oi " +
                        "JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                        "WHERE oi.order_id = ? AND oi.plan_item_id IS NOT NULL " +
                        "ORDER BY pi.plan_id ASC",
                (rs, rowNum) -> rs.getLong("plan_id"),
                orderId
        );
        return new ArrayList<>(new LinkedHashSet<>(rows));
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

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : DEFAULT_TENANT_ID;
    }

    private Long resolveCurrentUserId() {
        return UserContext.getUserId() != null ? UserContext.getUserId() : DEFAULT_USER_ID;
    }

    private boolean isDeleted(PurchaseOrder order) {
        return order != null && order.getDeleted() != null && order.getDeleted() != 0;
    }

    private void ensurePermission(String permissionCode, String errorMessage) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        if (!hasPermission(permissionCode)) {
            throw BizException.forbidden(errorMessage);
        }
    }

    private boolean hasPermission(String permissionCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
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
        return count != null && count > 0L;
    }

    private PurchaseOrder copyOrder(PurchaseOrder order) {
        return BeanUtil.copyProperties(order, PurchaseOrder.class);
    }

    private String resolveStatusAfterVoidReject(PurchaseOrder order) {
        String originalStatus = findStatusBeforeVoidApply(order);
        if (STATUS_PENDING_APPROVE.equals(originalStatus) || STATUS_APPROVED.equals(originalStatus)) {
            return originalStatus;
        }
        if (order.getApprovedBy() != null || order.getApprovedAt() != null || StrUtil.isNotBlank(order.getApproveRemark())) {
            log.warn("采购订单作废驳回未找到原始状态快照，按已审核兜底恢复: id={}", order.getId());
            return STATUS_APPROVED;
        }
        log.warn("采购订单作废驳回未找到原始状态快照，按待审核兜底恢复: id={}", order.getId());
        return STATUS_PENDING_APPROVE;
    }

    private String findStatusBeforeVoidApply(PurchaseOrder order) {
        StringBuilder sql = new StringBuilder("SELECT before_data FROM sys_audit_log " +
                "WHERE module_code = ? AND target_id = ? AND operation_desc = ? AND result = ?");
        List<Object> args = new ArrayList<>();
        args.add(AuditModule.SCM_PURCHASE_ORDER.getCode());
        args.add(order.getId());
        args.add("发起采购订单作废申请");
        args.add("success");
        if (order.getVoidRequestedAt() != null) {
            sql.append(" AND created_at >= ?");
            args.add(Timestamp.valueOf(order.getVoidRequestedAt().minusSeconds(1)));
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
        if (StrUtil.isBlank(beforeData) || !JSONUtil.isTypeJSON(beforeData)) {
            return null;
        }
        return JSONUtil.parseObj(beforeData).getStr("status");
    }

    private List<PurchaseOrderAttachmentDTO> resolveMaintenanceAttachments(
            PurchaseOrder order,
            String attachmentType,
            List<PurchaseOrderAttachmentDTO> attachments,
            MultipartFile file,
            String directory,
            List<String> uploadedAttachmentUrls
    ) {
        List<PurchaseOrderAttachmentDTO> normalizedAttachments = attachments == null
                ? new ArrayList<>()
                : normalizeSubmittedAttachments(attachments);
        if (attachments == null) {
            if (file == null || file.isEmpty()) {
                return toAttachmentDTOs(loadOrderMaintenanceAttachments(order, attachmentType));
            }
            return buildUploadedAttachments(file, directory, uploadedAttachmentUrls);
        }
        if (!normalizedAttachments.isEmpty() || file == null || file.isEmpty()) {
            return normalizedAttachments;
        }
        return buildUploadedAttachments(file, directory, uploadedAttachmentUrls);
    }

    private List<PurchaseOrderAttachmentDTO> buildUploadedAttachments(
            MultipartFile file,
            String directory,
            List<String> uploadedAttachmentUrls
    ) {
        validateAttachmentFile(file);
        PurchaseOrderAttachmentDTO attachment = new PurchaseOrderAttachmentDTO();
        attachment.setName(resolveAttachmentName(file));
        attachment.setSize(formatFileSize(file.getSize()));
        attachment.setUrl(fileStorageService.upload(file, directory));
        attachment.setSortOrder(1);
        if (uploadedAttachmentUrls != null) {
            uploadedAttachmentUrls.add(attachment.getUrl());
        }
        return new ArrayList<>(Collections.singletonList(attachment));
    }

    private List<PurchaseOrderAttachmentDTO> normalizeSubmittedAttachments(List<PurchaseOrderAttachmentDTO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }

        List<PurchaseOrderAttachmentDTO> normalizedAttachments = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int sortOrder = 1;
        for (PurchaseOrderAttachmentDTO attachment : attachments) {
            if (attachment == null) {
                continue;
            }
            String fileUrl = normalizeRequiredText(attachment.getUrl(), "附件地址不能为空");
            if (!seenUrls.add(fileUrl)) {
                continue;
            }

            PurchaseOrderAttachmentDTO normalizedAttachment = new PurchaseOrderAttachmentDTO();
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

    private List<PurchaseOrderAttachmentDTO> toAttachmentDTOs(List<PurchaseOrderAttachmentVO> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return new ArrayList<>();
        }
        List<PurchaseOrderAttachmentDTO> list = new ArrayList<>();
        for (PurchaseOrderAttachmentVO attachment : attachments) {
            PurchaseOrderAttachmentDTO dto = new PurchaseOrderAttachmentDTO();
            dto.setId(attachment.getId());
            dto.setName(attachment.getName());
            dto.setSize(attachment.getSize());
            dto.setUrl(attachment.getUrl());
            dto.setSortOrder(attachment.getSortOrder());
            list.add(dto);
        }
        return list;
    }

    private void syncLegacyMaintenanceAttachmentFields(
            PurchaseOrder order,
            String attachmentType,
            List<PurchaseOrderAttachmentDTO> attachments
    ) {
        PurchaseOrderAttachmentDTO firstAttachment = attachments == null || attachments.isEmpty() ? null : attachments.get(0);
        String fileName = firstAttachment == null ? null : firstAttachment.getName();
        String fileUrl = firstAttachment == null ? null : firstAttachment.getUrl();
        if (ATTACHMENT_TYPE_LOGISTICS.equals(attachmentType)) {
            order.setLogisticsAttachmentName(fileName);
            order.setLogisticsAttachmentUrl(fileUrl);
            return;
        }
        if (ATTACHMENT_TYPE_INSPECTION.equals(attachmentType)) {
            order.setInspectionAttachmentName(fileName);
            order.setInspectionAttachmentUrl(fileUrl);
            return;
        }
        order.setTraceAttachmentName(fileName);
        order.setTraceAttachmentUrl(fileUrl);
    }

    private void replaceOrderAttachments(
            Long orderId,
            String attachmentType,
            Long orgId,
            Long tenantId,
            List<PurchaseOrderAttachmentDTO> attachments
    ) {
        purchaseOrderAttachmentMapper.delete(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .eq(PurchaseOrderAttachment::getOrderId, orderId)
                .eq(PurchaseOrderAttachment::getAttachmentType, attachmentType));
        if (attachments == null || attachments.isEmpty()) {
            return;
        }

        int defaultSortOrder = 1;
        Long resolvedTenantId = tenantId == null ? resolveTenantId() : tenantId;
        for (PurchaseOrderAttachmentDTO attachment : attachments) {
            PurchaseOrderAttachment entity = new PurchaseOrderAttachment();
            entity.setOrderId(orderId);
            entity.setAttachmentType(attachmentType);
            entity.setFileName(attachment.getName());
            entity.setFileSize(normalizeOptionalText(attachment.getSize()));
            entity.setFileUrl(attachment.getUrl());
            entity.setSortOrder(attachment.getSortOrder() != null && attachment.getSortOrder() > 0
                    ? attachment.getSortOrder()
                    : defaultSortOrder);
            entity.setOrgId(orgId);
            entity.setTenantId(resolvedTenantId);
            purchaseOrderAttachmentMapper.insert(entity);
            defaultSortOrder += 1;
        }
    }

    private List<PurchaseOrderAttachmentVO> loadOrderMaintenanceAttachments(PurchaseOrder order, String attachmentType) {
        if (order == null || order.getId() == null) {
            return new ArrayList<>();
        }
        Map<String, List<PurchaseOrderAttachmentVO>> attachmentMap = loadOrderAttachmentMap(Collections.singletonList(order.getId()))
                .getOrDefault(order.getId(), Collections.emptyMap());
        List<PurchaseOrderAttachmentVO> attachments = new ArrayList<>(
                attachmentMap.getOrDefault(attachmentType, Collections.emptyList())
        );
        if (!attachments.isEmpty()) {
            return attachments;
        }

        PurchaseOrderAttachmentVO legacyAttachment = buildLegacyMaintenanceAttachment(order, attachmentType);
        if (legacyAttachment != null) {
            attachments.add(legacyAttachment);
        }
        return attachments;
    }

    private Map<Long, Map<String, List<PurchaseOrderAttachmentVO>>> loadOrderAttachmentMap(List<Long> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PurchaseOrderAttachment> rows = purchaseOrderAttachmentMapper.selectList(new LambdaQueryWrapper<PurchaseOrderAttachment>()
                .in(PurchaseOrderAttachment::getOrderId, orderIds)
                .orderByAsc(PurchaseOrderAttachment::getSortOrder)
                .orderByAsc(PurchaseOrderAttachment::getId));
        Map<Long, Map<String, List<PurchaseOrderAttachmentVO>>> result = new LinkedHashMap<>();
        for (PurchaseOrderAttachment row : rows) {
            PurchaseOrderAttachmentVO vo = new PurchaseOrderAttachmentVO();
            vo.setId(row.getId());
            vo.setName(row.getFileName());
            vo.setSize(row.getFileSize());
            vo.setUrl(row.getFileUrl());
            vo.setSortOrder(row.getSortOrder());
            result.computeIfAbsent(row.getOrderId(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(row.getAttachmentType(), key -> new ArrayList<>())
                    .add(vo);
        }
        return result;
    }

    private PurchaseOrderAttachmentVO buildLegacyMaintenanceAttachment(PurchaseOrder order, String attachmentType) {
        String fileName = null;
        String fileUrl = null;
        if (ATTACHMENT_TYPE_LOGISTICS.equals(attachmentType)) {
            fileName = order.getLogisticsAttachmentName();
            fileUrl = order.getLogisticsAttachmentUrl();
        } else if (ATTACHMENT_TYPE_INSPECTION.equals(attachmentType)) {
            fileName = order.getInspectionAttachmentName();
            fileUrl = order.getInspectionAttachmentUrl();
        } else if (ATTACHMENT_TYPE_TRACEABILITY.equals(attachmentType)) {
            fileName = order.getTraceAttachmentName();
            fileUrl = order.getTraceAttachmentUrl();
        }
        if (StrUtil.isBlank(fileUrl)) {
            return null;
        }

        PurchaseOrderAttachmentVO attachment = new PurchaseOrderAttachmentVO();
        attachment.setName(StrUtil.blankToDefault(fileName, "附件"));
        attachment.setUrl(fileUrl);
        attachment.setSortOrder(1);
        return attachment;
    }

    private List<String> loadPersistedMaintenanceAttachmentUrls(PurchaseOrder order, String attachmentType) {
        return loadOrderMaintenanceAttachments(order, attachmentType).stream()
                .map(PurchaseOrderAttachmentVO::getUrl)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private void deleteRemovedAttachmentUrls(List<String> beforeAttachmentUrls, List<PurchaseOrderAttachmentDTO> currentAttachments) {
        if (beforeAttachmentUrls == null || beforeAttachmentUrls.isEmpty()) {
            return;
        }

        Set<String> currentUrls = new LinkedHashSet<>();
        if (currentAttachments != null) {
            for (PurchaseOrderAttachmentDTO attachment : currentAttachments) {
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

    private void deleteAttachmentUrls(List<String> attachmentUrls) {
        if (attachmentUrls == null || attachmentUrls.isEmpty()) {
            return;
        }
        attachmentUrls.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .forEach(fileStorageService::delete);
    }

    private ReleasePlanRelationResult releaseRelatedPurchasePlans(Long orderId) {
        List<Long> planIds = jdbcTemplate.query(
                "SELECT DISTINCT pi.plan_id " +
                        "FROM scm_purchase_order_item oi " +
                        "JOIN scm_purchase_plan_item pi ON pi.id = oi.plan_item_id " +
                        "WHERE oi.order_id = ? AND oi.plan_item_id IS NOT NULL",
                (rs, rowNum) -> rs.getLong("plan_id"),
                orderId
        );
        int releasedItemCount = jdbcTemplate.update(
                "UPDATE scm_purchase_order_item SET plan_item_id = NULL WHERE order_id = ? AND plan_item_id IS NOT NULL",
                orderId
        );
        return new ReleasePlanRelationResult(planIds, releasedItemCount);
    }

    private ReleaseMergeLockedPlanResult releaseMergeLockedPurchasePlans(Long orderId) {
        List<Long> planIds = jdbcTemplate.query(
                "SELECT id FROM scm_purchase_plan WHERE deleted = 0 AND merge_order_id = ? ORDER BY id ASC",
                (rs, rowNum) -> rs.getLong("id"),
                orderId
        );
        int releasedPlanCount = jdbcTemplate.update(
                "UPDATE scm_purchase_plan " +
                        "SET merge_locked = 0, merge_order_id = NULL, updated_by = ?, updated_at = NOW() " +
                        "WHERE merge_order_id = ?",
                resolveCurrentUserId(),
                orderId
        );
        return new ReleaseMergeLockedPlanResult(planIds, releasedPlanCount);
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
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate().format(DATE_FORMATTER);
        }
        if (value instanceof Date date) {
            return date.toLocalDate().format(DATE_FORMATTER);
        }
        return String.valueOf(value);
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
        return String.valueOf(value);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeScene(String scene) {
        String normalized = trimToNull(scene);
        if (BIZ_SCENE_LOGISTICS.equals(normalized)
                || BIZ_SCENE_INSPECTION.equals(normalized)
                || BIZ_SCENE_TRACEABILITY.equals(normalized)) {
            return normalized;
        }
        throw BizException.badRequest("暂不支持的第三方同步场景");
    }

    private String resolveSceneSourceType(PurchaseOrder order, String scene) {
        if (BIZ_SCENE_LOGISTICS.equals(scene)) {
            return normalizeSourceType(order.getLogisticsSourceType());
        }
        if (BIZ_SCENE_INSPECTION.equals(scene)) {
            return normalizeSourceType(order.getInspectionSourceType());
        }
        return normalizeSourceType(order.getTraceSourceType());
    }

    private void markSceneSourceTypeAsThirdParty(PurchaseOrder order, String scene) {
        LambdaUpdateWrapper<PurchaseOrder> updateWrapper = new LambdaUpdateWrapper<PurchaseOrder>()
                .eq(PurchaseOrder::getId, order.getId());
        if (BIZ_SCENE_LOGISTICS.equals(scene)) {
            updateWrapper.set(PurchaseOrder::getLogisticsSourceType, SOURCE_TYPE_THIRD_PARTY);
            order.setLogisticsSourceType(SOURCE_TYPE_THIRD_PARTY);
        } else if (BIZ_SCENE_INSPECTION.equals(scene)) {
            updateWrapper.set(PurchaseOrder::getInspectionSourceType, SOURCE_TYPE_THIRD_PARTY);
            order.setInspectionSourceType(SOURCE_TYPE_THIRD_PARTY);
        } else {
            updateWrapper.set(PurchaseOrder::getTraceSourceType, SOURCE_TYPE_THIRD_PARTY);
            order.setTraceSourceType(SOURCE_TYPE_THIRD_PARTY);
        }
        purchaseOrderMapper.update(null, updateWrapper);
    }

    private void markSceneSourceTypeAsThirdPartyCommitted(PurchaseOrder order, String scene) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactionTemplate.executeWithoutResult(status -> markSceneSourceTypeAsThirdParty(order, scene));
    }

    private String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
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

    private BigDecimal scaleAmount(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleQuantity(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private PurchaseOrderPlanItemOptionVO selectRepresentativePlanItemOption(List<PurchaseOrderPlanItemOptionVO> items) {
        PurchaseOrderPlanItemOptionVO representative = items.get(0);
        for (PurchaseOrderPlanItemOptionVO item : items) {
            if (item.getId() != null && representative.getId() != null) {
                if (item.getId() < representative.getId()) {
                    representative = item;
                }
            } else if (representative.getId() == null && item.getId() != null) {
                representative = item;
            }
        }
        return representative;
    }

    private List<PurchaseOrderPlanItemOptionVO> sortPlanItemSources(List<PurchaseOrderPlanItemOptionVO> items) {
        List<PurchaseOrderPlanItemOptionVO> sorted = new ArrayList<>(items);
        sorted.sort((left, right) -> {
            long leftId = left.getId() == null ? Long.MAX_VALUE : left.getId();
            long rightId = right.getId() == null ? Long.MAX_VALUE : right.getId();
            return Long.compare(leftId, rightId);
        });
        return sorted;
    }

    private String buildMergedPlanNo(List<PurchaseOrderItemVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderItemVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanNo())) {
                values.add(item.getPlanNo());
            }
        }
        return summarizeMergedText(values, "条计划");
    }

    private String buildMergedPlanName(List<PurchaseOrderItemVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderItemVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanName())) {
                values.add(item.getPlanName());
            }
        }
        return summarizeMergedText(values, "条计划");
    }

    private String buildMergedPlanOrgName(List<PurchaseOrderItemVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderItemVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanOrgName())) {
                values.add(item.getPlanOrgName());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return "多组织";
    }

    private String buildMergedPlanNoFromOptions(List<PurchaseOrderPlanItemOptionVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderPlanItemOptionVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanNo())) {
                values.add(item.getPlanNo());
            }
        }
        return summarizeMergedText(values, "条计划");
    }

    private String buildMergedPlanNameFromOptions(List<PurchaseOrderPlanItemOptionVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderPlanItemOptionVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanName())) {
                values.add(item.getPlanName());
            }
        }
        return summarizeMergedText(values, "条计划");
    }

    private String buildMergedPlanOrgNameFromOptions(List<PurchaseOrderPlanItemOptionVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderPlanItemOptionVO item : items) {
            if (StrUtil.isNotBlank(item.getPlanOrgName())) {
                values.add(item.getPlanOrgName());
            }
        }
        if (values.isEmpty()) {
            return null;
        }
        if (values.size() == 1) {
            return values.iterator().next();
        }
        return "多组织";
    }

    private String summarizeMergedText(LinkedHashSet<String> values, String suffix) {
        if (values.isEmpty()) {
            return null;
        }
        String first = values.iterator().next();
        if (values.size() == 1) {
            return first;
        }
        return first + " 等" + values.size() + suffix;
    }

    private String resolveMergedRemark(List<PurchaseOrderItemVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderItemVO item : items) {
            if (StrUtil.isNotBlank(item.getRemark())) {
                values.add(item.getRemark());
            }
        }
        return values.isEmpty() ? null : String.join("；", values);
    }

    private String resolveMergedPlanItemRemark(List<PurchaseOrderPlanItemOptionVO> items) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (PurchaseOrderPlanItemOptionVO item : items) {
            if (StrUtil.isNotBlank(item.getRemark())) {
                values.add(item.getRemark());
            }
        }
        return values.isEmpty() ? null : String.join("；", values);
    }

    private BigDecimal sumOrderItemField(List<PurchaseOrderItemVO> items, Function<PurchaseOrderItemVO, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItemVO item : items) {
            BigDecimal value = extractor.apply(item);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }

    private BigDecimal sumPlanItemOptionField(List<PurchaseOrderPlanItemOptionVO> items, Function<PurchaseOrderPlanItemOptionVO, BigDecimal> extractor) {
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderPlanItemOptionVO item : items) {
            BigDecimal value = extractor.apply(item);
            if (value != null) {
                total = total.add(value);
            }
        }
        return total;
    }

    private static class BuildResult {
        private final List<PurchaseOrderItem> items;
        private final BigDecimal totalAmount;
        private final Long primaryPlanId;

        private BuildResult(List<PurchaseOrderItem> items, BigDecimal totalAmount, Long primaryPlanId) {
            this.items = items;
            this.totalAmount = totalAmount;
            this.primaryPlanId = primaryPlanId;
        }

        public List<PurchaseOrderItem> getItems() {
            return items;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }

        public Long getPrimaryPlanId() {
            return primaryPlanId;
        }
    }

    private static class PlanItemLoadResult {
        private final List<PurchaseOrderPlanItemOptionVO> options;
        private final Map<Long, List<PurchaseOrderPlanItemOptionVO>> sourceMap;

        private PlanItemLoadResult(
                List<PurchaseOrderPlanItemOptionVO> options,
                Map<Long, List<PurchaseOrderPlanItemOptionVO>> sourceMap
        ) {
            this.options = options;
            this.sourceMap = sourceMap;
        }

        public List<PurchaseOrderPlanItemOptionVO> getOptions() {
            return options;
        }

        public List<PurchaseOrderPlanItemOptionVO> getSourceItems(Long optionId) {
            return sourceMap.getOrDefault(optionId, Collections.emptyList());
        }
    }

    private static class ReleasePlanRelationResult {
        private final List<Long> planIds;
        private final int releasedItemCount;

        private ReleasePlanRelationResult(List<Long> planIds, int releasedItemCount) {
            this.planIds = planIds;
            this.releasedItemCount = releasedItemCount;
        }

        public List<Long> getPlanIds() {
            return planIds;
        }

        public int getReleasedItemCount() {
            return releasedItemCount;
        }
    }

    private static class ReleaseMergeLockedPlanResult {
        private final List<Long> planIds;
        private final int releasedPlanCount;

        private ReleaseMergeLockedPlanResult(List<Long> planIds, int releasedPlanCount) {
            this.planIds = planIds;
            this.releasedPlanCount = releasedPlanCount;
        }

        public List<Long> getPlanIds() {
            return planIds;
        }

        public int getReleasedPlanCount() {
            return releasedPlanCount;
        }
    }

    private record PurchaseOrderDeleteCheckResult(
            int linkedInboundOrderCount,
            int linkedReceiptRecordCount,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

    private record PurchaseOrderDeleteRecheckSnapshot(
            PurchaseOrder latestOrder,
            PurchaseOrderDeleteCheckResult checkResult
    ) {
    }

    private record PurchaseOrderReverseAuditCheckResult(
            int linkedPlanCount,
            List<Long> linkedPlanIds,
            int linkedInboundCount,
            int linkedReceiptRecordCount,
            int linkedInventoryRecordCount,
            List<LinkedInboundOrderState> linkedInbounds,
            List<LinkedInboundOrderState> blockedInbounds,
            SupplierReverseAuditState supplierState,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

    private record PurchaseOrderReverseAuditRecheckSnapshot(
            PurchaseOrder order,
            PurchaseOrderReverseAuditCheckResult checkResult
    ) {
    }

    private record LinkedInboundOrderState(
            Long inboundOrderId,
            String inboundNo,
            String status,
            String postStatus
    ) {
    }

    private record SupplierReverseAuditState(
            Long supplierId,
            String supplierName,
            String status,
            LocalDateTime licenseExpiresAt,
            LocalDateTime foodLicenseExpiresAt,
            String blockedReason
    ) {
        private boolean passed() {
            return StrUtil.isBlank(blockedReason);
        }
    }

}
