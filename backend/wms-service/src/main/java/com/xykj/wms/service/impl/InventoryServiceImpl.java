package com.xykj.wms.service.impl;

import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.DataScopeService;
import com.xykj.wms.dto.InventoryMovementQueryDTO;
import com.xykj.wms.dto.InventoryOverviewQueryDTO;
import com.xykj.wms.mapper.InventoryMapper;
import com.xykj.wms.service.InventoryService;
import com.xykj.wms.vo.InventoryDistributionVO;
import com.xykj.wms.vo.InventoryMovementVO;
import com.xykj.wms.vo.InventoryOverviewVO;
import com.xykj.wms.vo.InventoryShelfLifeSummaryVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);

    private final InventoryMapper inventoryMapper;
    private final DataScopeService dataScopeService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public PageResult<InventoryOverviewVO> getOverview(InventoryOverviewQueryDTO query) {
        List<Long> orgIds = resolveOrgIds();
        Long tenantId = resolveTenantId();
        String keyword = trim(query.getKeyword());
        String categoryName = trim(query.getCategoryName());
        String materialStatus = normalizeMaterialStatus(query.getMaterialStatus());
        Long total = inventoryMapper.countOverviewRows(
                keyword,
                categoryName,
                materialStatus,
                query.getWarehouseId(),
                query.getLocationId(),
                orgIds,
                tenantId
        );
        long safeTotal = total == null ? 0L : total;
        int requestedPageNum = query.getPageNum();
        int resolvedPageNum = resolveOverviewPageNum(requestedPageNum, query.getPageSize(), safeTotal);
        long offset = (long) (resolvedPageNum - 1) * query.getPageSize();
        List<Map<String, Object>> rows = inventoryMapper.selectOverviewRows(
                keyword,
                categoryName,
                materialStatus,
                query.getWarehouseId(),
                query.getLocationId(),
                orgIds,
                tenantId,
                offset,
                query.getPageSize()
        );
        List<InventoryOverviewVO> all = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            InventoryOverviewVO vo = mapOverview(row);
            if (matchesOverviewFilters(vo, query)) {
                all.add(vo);
            }
        }
        return PageResult.of(all, Long.valueOf(resolvedPageNum), Long.valueOf(query.getPageSize()), safeTotal);
    }

    @Override
    public List<InventoryDistributionVO> getDistribution(Long materialId) {
        assertMaterialInScope(materialId);
        return loadDistribution(materialId);
    }

    @Override
    public InventoryShelfLifeSummaryVO getShelfLifeSummary(Long materialId) {
        assertMaterialInScope(materialId);
        return buildShelfLifeSummary(materialId);
    }

    @Override
    public PageResult<InventoryMovementVO> getMovements(Long materialId, InventoryMovementQueryDTO query) {
        assertMaterialInScope(materialId);
        List<InventoryMovementVO> rows = loadMovementRows(materialId, query);
        int fromIndex = Math.min((query.getPageNum() - 1) * query.getPageSize(), rows.size());
        int toIndex = Math.min(fromIndex + query.getPageSize(), rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), Long.valueOf(query.getPageNum()), Long.valueOf(query.getPageSize()), Long.valueOf(rows.size()));
    }

    @Override
    public void exportOverview(InventoryOverviewQueryDTO query, HttpServletResponse response) {
        ensureExportPermission("无权导出库存总览");
        List<InventoryOverviewVO> rows = getOverview(query).getList();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("库存总览");
            Row header = sheet.createRow(0);
            String[] titles = {"物料编码", "物料名称", "类别", "规格", "所在仓库", "所在仓位", "当前库存", "库存范围", "最新批次", "最小剩余天数", "库存状态", "最后更新时间"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                InventoryOverviewVO item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(nullToEmpty(item.getMaterialCode()));
                row.createCell(1).setCellValue(nullToEmpty(item.getMaterialName()));
                row.createCell(2).setCellValue(nullToEmpty(item.getCategoryName()));
                row.createCell(3).setCellValue(nullToEmpty(item.getMaterialSpec()));
                row.createCell(4).setCellValue(nullToEmpty(item.getWarehouseName()));
                row.createCell(5).setCellValue(nullToEmpty(item.getLocationName()));
                row.createCell(6).setCellValue(decimalToString(item.getCurrentStock()));
                row.createCell(7).setCellValue(nullToEmpty(item.getStockRange()));
                row.createCell(8).setCellValue(nullToEmpty(item.getLatestBatchNo()));
                row.createCell(9).setCellValue(item.getMinRemainingDays() == null ? "" : String.valueOf(item.getMinRemainingDays()));
                row.createCell(10).setCellValue(toStockStatusLabel(item.getStockStatus()));
                row.createCell(11).setCellValue(item.getUpdatedAt() == null ? "" : item.getUpdatedAt().toString().replace('T', ' '));
            }
            writeWorkbook(response, workbook, "库存总览.xlsx");
        } catch (IOException ex) {
            throw new IllegalStateException("导出库存总览失败", ex);
        }
    }

    @Override
    public void exportMovements(Long materialId, InventoryMovementQueryDTO query, HttpServletResponse response) {
        ensureExportPermission("无权导出库存明细");
        assertMaterialInScope(materialId);
        List<InventoryMovementVO> rows = loadMovementRows(materialId, query);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("出入库明细");
            Row header = sheet.createRow(0);
            String[] titles = {"单据类型", "单据编号", "操作类型", "物料名称", "规格", "数量", "单位", "操作后库存数量", "仓库", "仓位", "操作人", "操作时间"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                InventoryMovementVO item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(nullToEmpty(item.getBizType()));
                row.createCell(1).setCellValue(nullToEmpty(item.getDocumentNo()));
                row.createCell(2).setCellValue(nullToEmpty(item.getOperationType()));
                row.createCell(3).setCellValue(nullToEmpty(item.getMaterialName()));
                row.createCell(4).setCellValue(nullToEmpty(item.getSpec()));
                row.createCell(5).setCellValue(decimalToString(item.getQuantity()));
                row.createCell(6).setCellValue(nullToEmpty(item.getUnit()));
                row.createCell(7).setCellValue(decimalToString(item.getPostOperationStockQty()));
                row.createCell(8).setCellValue(nullToEmpty(item.getWarehouseName()));
                row.createCell(9).setCellValue(nullToEmpty(item.getLocationName()));
                row.createCell(10).setCellValue(nullToEmpty(item.getOperatorName()));
                row.createCell(11).setCellValue(item.getOperationTime() == null ? "" : item.getOperationTime().toString().replace('T', ' '));
            }
            writeWorkbook(response, workbook, "出入库明细.xlsx");
        } catch (IOException ex) {
            throw new IllegalStateException("导出出入库明细失败", ex);
        }
    }

    private int resolveOverviewPageNum(int requestedPageNum, int pageSize, long total) {
        if (pageSize <= 0) {
            return requestedPageNum;
        }
        long totalPages = total == 0 ? 1L : (total + pageSize - 1) / pageSize;
        return (int) Math.min(Math.max(requestedPageNum, 1), totalPages);
    }

    private String normalizeMaterialStatus(String materialStatus) {
        String normalized = trim(materialStatus);
        return normalized == null ? "active" : normalized;
    }

    private List<InventoryDistributionVO> loadDistribution(Long materialId) {
        List<Map<String, Object>> rows = inventoryMapper.selectDistributionRows(materialId, resolveOrgIds(), resolveTenantId());
        List<InventoryDistributionVO> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            InventoryDistributionVO vo = new InventoryDistributionVO();
            vo.setWarehouseId(toLong(row.get("warehouseId")));
            vo.setWarehouseName(toStringValue(row.get("warehouseName")));
            vo.setLocationId(toLong(row.get("locationId")));
            vo.setLocationName(toStringValue(row.get("locationName")));
            vo.setBatchNo(toStringValue(row.get("batchNo")));
            vo.setQuantity(toBigDecimal(row.get("quantity")));
            vo.setProductionDate(toLocalDate(row.get("productionDate")));
            vo.setExpiryDate(toLocalDate(row.get("expiryDate")));
            vo.setRemainingDays(calcRemainingDays(vo.getExpiryDate()));
            result.add(vo);
        }
        return result;
    }

    private InventoryShelfLifeSummaryVO buildShelfLifeSummary(Long materialId) {
        Map<String, Object> config = inventoryMapper.selectMaterialShelfLifeConfig(materialId);
        Integer nearExpiryDays = toInteger(config == null ? null : config.get("nearExpiryDays"), null);
        Integer warningDays = toInteger(config == null ? null : config.get("warningDays"), null);
        InventoryShelfLifeSummaryVO summary = new InventoryShelfLifeSummaryVO();
        summary.setMaterialId(materialId);
        summary.setNormalQty(BigDecimal.ZERO);
        summary.setWarningQty(BigDecimal.ZERO);
        summary.setNearExpiryQty(BigDecimal.ZERO);
        summary.setExpiredQty(BigDecimal.ZERO);
        if (nearExpiryDays == null || warningDays == null) {
            summary.setConfigStatus("missing");
            log.warn("保质期分层阈值缺失，materialId={}", materialId);
            return fillShelfLifeTotalOnly(summary);
        }
        summary.setConfigStatus("ok");
        for (InventoryDistributionVO distribution : loadDistribution(materialId)) {
            BigDecimal qty = distribution.getQuantity() == null ? BigDecimal.ZERO : distribution.getQuantity();
            String level = calculateShelfLifeLevel(distribution.getRemainingDays(), nearExpiryDays, warningDays);
            switch (level) {
                case "expired" -> summary.setExpiredQty(summary.getExpiredQty().add(qty));
                case "near_expiry" -> summary.setNearExpiryQty(summary.getNearExpiryQty().add(qty));
                case "warning" -> summary.setWarningQty(summary.getWarningQty().add(qty));
                default -> summary.setNormalQty(summary.getNormalQty().add(qty));
            }
        }
        summary.setTotalQty(summary.getNormalQty().add(summary.getWarningQty()).add(summary.getNearExpiryQty()).add(summary.getExpiredQty()));
        return summary;
    }

    private InventoryShelfLifeSummaryVO fillShelfLifeTotalOnly(InventoryShelfLifeSummaryVO summary) {
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryDistributionVO distribution : loadDistribution(summary.getMaterialId())) {
            BigDecimal qty = distribution.getQuantity() == null ? BigDecimal.ZERO : distribution.getQuantity();
            total = total.add(qty);
        }
        summary.setTotalQty(total);
        return summary;
    }

    private List<InventoryMovementVO> loadMovementRows(Long materialId, InventoryMovementQueryDTO query) {
        List<InventoryMovementVO> rows = new ArrayList<>();
        rows.addAll(queryInboundMovements(materialId, query));
        rows.addAll(queryOutboundMovements(materialId, query));
        rows.addAll(queryStocktakeMovements(materialId, query));
        rows.sort(Comparator.comparing(InventoryMovementVO::getOperationTime, Comparator.nullsLast(LocalDateTime::compareTo)).reversed());
        if (trim(query.getBizType()) != null) {
            rows = rows.stream()
                    .filter(item -> Objects.equals(item.getBizType(), trim(query.getBizType())))
                    .toList();
        }
        applyApproximatePostStock(rows, materialId);
        return rows;
    }

    private List<InventoryMovementVO> queryInboundMovements(Long materialId, InventoryMovementQueryDTO query) {
        StringBuilder sql = new StringBuilder("SELECT '入库单' AS bizType, o.inbound_no AS documentNo, '入库' AS operationType, i.material_id AS materialId, i.material_name AS materialName, i.spec AS spec, i.quantity AS quantity, i.unit AS unit, inv.quantity AS postOperationStockQty, w.warehouse_name AS warehouseName, l.location_name AS locationName, COALESCE(o.submitter_name, CAST(o.submitted_by AS CHAR)) AS operatorName, COALESCE(o.approved_at, o.updated_at, o.created_at) AS operationTime FROM wms_inbound_order_item i INNER JOIN wms_inbound_order o ON o.id = i.inbound_id LEFT JOIN wms_inventory inv ON inv.material_id = i.material_id AND inv.warehouse_id = i.warehouse_id AND inv.location_id = i.location_id AND ((inv.batch_no IS NULL AND i.batch_no IS NULL) OR inv.batch_no = i.batch_no) LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 WHERE i.material_id = ? AND o.deleted = 0 AND (o.status = 'completed' OR (o.status = 'approved' AND o.post_status = 'posted'))");
        List<Object> args = new ArrayList<>();
        args.add(materialId);
        appendScopeFilters(sql, args, "o");
        appendMovementFilters(sql, args, query, "o", "inbound_no", "updated_at");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapMovement(rs), args.toArray());
    }

    private List<InventoryMovementVO> queryOutboundMovements(Long materialId, InventoryMovementQueryDTO query) {
        StringBuilder sql = new StringBuilder("SELECT '出库单' AS bizType, o.outbound_no AS documentNo, '出库' AS operationType, i.material_id AS materialId, i.material_name AS materialName, i.spec AS spec, i.quantity AS quantity, i.unit AS unit, inv.quantity AS postOperationStockQty, w.warehouse_name AS warehouseName, l.location_name AS locationName, COALESCE(o.submitter_name, CAST(o.submitted_by AS CHAR)) AS operatorName, COALESCE(o.completed_at, o.updated_at, o.created_at) AS operationTime FROM wms_outbound_order_item i INNER JOIN wms_outbound_order o ON o.id = i.outbound_id LEFT JOIN wms_inventory inv ON inv.id = i.inventory_id LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 WHERE i.material_id = ? AND o.deleted = 0 AND o.status = 'completed'");
        List<Object> args = new ArrayList<>();
        args.add(materialId);
        appendScopeFilters(sql, args, "o");
        appendMovementFilters(sql, args, query, "o", "outbound_no", "updated_at");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapMovement(rs), args.toArray());
    }

    private List<InventoryMovementVO> queryStocktakeMovements(Long materialId, InventoryMovementQueryDTO query) {
        StringBuilder sql = new StringBuilder("SELECT '盘点单' AS bizType, o.stocktake_no AS documentNo, CASE WHEN i.diff_qty > 0 THEN '盘盈' ELSE '盘亏' END AS operationType, i.material_id AS materialId, i.material_name AS materialName, i.spec AS spec, ABS(i.diff_qty) AS quantity, i.unit AS unit, inv.quantity AS postOperationStockQty, w.warehouse_name AS warehouseName, l.location_name AS locationName, COALESCE(o.checker_name, CAST(o.created_by AS CHAR)) AS operatorName, COALESCE(o.approved_at, o.updated_at, o.created_at) AS operationTime FROM wms_stocktake_order_item i INNER JOIN wms_stocktake_order o ON o.id = i.stocktake_id LEFT JOIN wms_inventory inv ON inv.id = i.inventory_id LEFT JOIN wms_warehouse w ON w.id = i.warehouse_id AND w.deleted = 0 LEFT JOIN wms_location l ON l.id = i.location_id AND l.deleted = 0 WHERE i.material_id = ? AND o.deleted = 0 AND o.status = 'completed' AND i.diff_qty <> 0");
        List<Object> args = new ArrayList<>();
        args.add(materialId);
        appendScopeFilters(sql, args, "o");
        appendMovementFilters(sql, args, query, "o", "stocktake_no", "updated_at");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> mapMovement(rs), args.toArray());
    }

    private void appendMovementFilters(StringBuilder sql,
                                       List<Object> args,
                                       InventoryMovementQueryDTO query,
                                       String tableAlias,
                                       String documentColumn,
                                       String timeColumn) {
        if (query.getStartDate() != null) {
            sql.append(" AND DATE(").append(tableAlias).append(".").append(timeColumn).append(") >= ?");
            args.add(query.getStartDate());
        }
        if (query.getEndDate() != null) {
            sql.append(" AND DATE(").append(tableAlias).append(".").append(timeColumn).append(") <= ?");
            args.add(query.getEndDate());
        }
        String documentNo = trim(query.getDocumentNo());
        if (documentNo != null) {
            sql.append(" AND ").append(tableAlias).append(".").append(documentColumn).append(" LIKE ?");
            args.add("%" + documentNo + "%");
        }
    }

    private void appendScopeFilters(StringBuilder sql, List<Object> args, String tableAlias) {
        List<Long> orgIds = resolveOrgIds();
        if (orgIds != null) {
            if (orgIds.isEmpty()) {
                sql.append(" AND 1 = 0");
            } else {
                sql.append(" AND ").append(tableAlias).append(".org_id IN (");
                for (int i = 0; i < orgIds.size(); i++) {
                    if (i > 0) {
                        sql.append(", ");
                    }
                    sql.append("?");
                    args.add(orgIds.get(i));
                }
                sql.append(")");
            }
        }
        Long tenantId = resolveTenantId();
        if (tenantId != null) {
            sql.append(" AND ").append(tableAlias).append(".tenant_id = ?");
            args.add(tenantId);
        }
    }

    private void applyApproximatePostStock(List<InventoryMovementVO> rows, Long materialId) {
        BigDecimal runningStock = getCurrentStock(materialId);
        for (InventoryMovementVO row : rows) {
            row.setPostOperationStockQty(runningStock);
            BigDecimal quantity = row.getQuantity() == null ? BigDecimal.ZERO : row.getQuantity();
            String operationType = row.getOperationType();
            if ("入库".equals(operationType) || "盘盈".equals(operationType)) {
                runningStock = runningStock.subtract(quantity);
            } else if ("出库".equals(operationType) || "盘亏".equals(operationType)) {
                runningStock = runningStock.add(quantity);
            }
        }
    }

    private BigDecimal getCurrentStock(Long materialId) {
        List<InventoryDistributionVO> distributions = getDistribution(materialId);
        BigDecimal total = BigDecimal.ZERO;
        for (InventoryDistributionVO distribution : distributions) {
            BigDecimal quantity = distribution.getQuantity() == null ? BigDecimal.ZERO : distribution.getQuantity();
            total = total.add(quantity);
        }
        return total;
    }

    private InventoryMovementVO mapMovement(java.sql.ResultSet rs) throws java.sql.SQLException {
        InventoryMovementVO vo = new InventoryMovementVO();
        vo.setBizType(rs.getString("bizType"));
        vo.setDocumentNo(rs.getString("documentNo"));
        vo.setOperationType(rs.getString("operationType"));
        vo.setMaterialId(rs.getLong("materialId"));
        vo.setMaterialName(rs.getString("materialName"));
        vo.setSpec(rs.getString("spec"));
        vo.setQuantity(rs.getBigDecimal("quantity"));
        vo.setUnit(rs.getString("unit"));
        vo.setPostOperationStockQty(rs.getBigDecimal("postOperationStockQty"));
        vo.setWarehouseName(rs.getString("warehouseName"));
        vo.setLocationName(rs.getString("locationName"));
        vo.setOperatorName(rs.getString("operatorName"));
        java.sql.Timestamp ts = rs.getTimestamp("operationTime");
        vo.setOperationTime(ts == null ? null : ts.toLocalDateTime());
        return vo;
    }

    private InventoryOverviewVO mapOverview(Map<String, Object> row) {
        InventoryOverviewVO vo = new InventoryOverviewVO();
        vo.setMaterialId(toLong(row.get("materialId")));
        vo.setMaterialCode(toStringValue(row.get("materialCode")));
        vo.setMaterialName(toStringValue(row.get("materialName")));
        vo.setCategoryName(toStringValue(row.get("categoryName")));
        vo.setMaterialSpec(toStringValue(row.get("materialSpec")));
        vo.setUnit(toStringValue(row.get("unit")));
        vo.setImageUrl(toStringValue(row.get("imageUrl")));
        vo.setCurrentStock(toBigDecimal(row.get("currentStock")));
        vo.setMinStock(toBigDecimal(row.get("minStock")));
        vo.setMaxStock(toBigDecimal(row.get("maxStock")));
        vo.setShelfLifeDays(toInteger(row.get("shelfLifeDays"), 0));
        vo.setLatestBatchNo(toStringValue(row.get("latestBatchNo")));
        vo.setLatestProductionDate(toLocalDate(row.get("latestProductionDate")));
        vo.setMinRemainingDays(toInteger(row.get("minRemainingDays"), null));
        vo.setUpdatedAt(toLocalDateTime(row.get("updatedAt")));
        int warehouseCount = toInteger(row.get("warehouseCount"), 0);
        int locationCount = toInteger(row.get("locationCount"), 0);
        vo.setWarehouseName(warehouseCount > 1 ? "多仓库" : resolveSingleWarehouseName(vo.getMaterialId()));
        vo.setLocationName(locationCount > 1 ? "多仓位" : resolveSingleLocationName(vo.getMaterialId()));
        vo.setStockRange(decimalToString(vo.getMinStock()) + "-" + decimalToString(vo.getMaxStock()));
        BigDecimal expiredQty = toBigDecimal(row.get("expiredQty"));
        vo.setStockStatus(calculateStockStatus(vo.getCurrentStock(), vo.getMinStock(), vo.getMaxStock(), expiredQty));
        Map<String, Object> config = inventoryMapper.selectMaterialShelfLifeConfig(vo.getMaterialId());
        vo.setShelfLifeLevel(calculateShelfLifeLevel(vo.getMinRemainingDays(), toInteger(config == null ? null : config.get("nearExpiryDays"), 0), toInteger(config == null ? null : config.get("warningDays"), 0)));
        return vo;
    }

    private boolean matchesOverviewFilters(InventoryOverviewVO vo, InventoryOverviewQueryDTO query) {
        if (trim(query.getStockStatus()) != null && !Objects.equals(vo.getStockStatus(), trim(query.getStockStatus()))) {
            return false;
        }
        return trim(query.getShelfLifeLevel()) == null || Objects.equals(vo.getShelfLifeLevel(), trim(query.getShelfLifeLevel()));
    }

    private String resolveSingleWarehouseName(Long materialId) {
        List<InventoryDistributionVO> distributions = loadDistribution(materialId);
        if (distributions.isEmpty()) {
            return "—";
        }
        return distributions.size() > 1 ? "多仓库" : nullToEmpty(distributions.get(0).getWarehouseName());
    }

    private String resolveSingleLocationName(Long materialId) {
        List<InventoryDistributionVO> distributions = loadDistribution(materialId);
        if (distributions.isEmpty()) {
            return "—";
        }
        return distributions.size() > 1 ? "多仓位" : nullToEmpty(distributions.get(0).getLocationName());
    }

    private String calculateStockStatus(BigDecimal currentStock, BigDecimal minStock, BigDecimal maxStock, BigDecimal expiredQty) {
        if (expiredQty != null && expiredQty.compareTo(BigDecimal.ZERO) > 0) {
            return "expired";
        }
        BigDecimal safeStock = currentStock == null ? BigDecimal.ZERO : currentStock;
        if (minStock != null && safeStock.compareTo(minStock) <= 0) {
            return "low";
        }
        if (maxStock != null && safeStock.compareTo(maxStock) >= 0) {
            return "high";
        }
        return "normal";
    }

    private String calculateShelfLifeLevel(Integer remainingDays, int nearExpiryDays, int warningDays) {
        if (remainingDays == null) {
            return "normal";
        }
        if (remainingDays <= 0) {
            return "expired";
        }
        if (nearExpiryDays > 0 && remainingDays <= nearExpiryDays) {
            return "near_expiry";
        }
        if (warningDays > nearExpiryDays && remainingDays <= warningDays) {
            return "warning";
        }
        return "normal";
    }

    private Integer calcRemainingDays(LocalDate expiryDate) {
        if (expiryDate == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    private void writeWorkbook(HttpServletResponse response, Workbook workbook, String fileName) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        workbook.write(response.getOutputStream());
    }

    private void assertMaterialInScope(Long materialId) {
        if (!inventoryMapper.existsActiveMaterial(materialId)) {
            throw BizException.validationFailed("物料不可用");
        }
        List<Long> orgIds = resolveOrgIds();
        Long tenantId = resolveTenantId();
        boolean visible = inventoryMapper.existsMaterialVisibleInScope(materialId, orgIds, tenantId);
        if (!visible) {
            throw BizException.forbidden("无权限查看该物料库存详情");
        }
    }

    private void ensureExportPermission(String errorMessage) {
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
                "inventory:export"
        );
        if (count == null || count <= 0L) {
            throw BizException.forbidden(errorMessage);
        }
    }

    private List<Long> resolveOrgIds() {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isRestricted()) {
            return null;
        }
        return new ArrayList<>(scope.getOrgIds());
    }

    private Long resolveTenantId() {
        return UserContext.getTenantId() != null ? UserContext.getTenantId() : 1L;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime().toLocalDate();
        }
        return null;
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate().atStartOfDay();
        }
        return null;
    }

    private Long toLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer toInteger(Object value, Integer defaultValue) {
        return value instanceof Number number ? number.intValue() : defaultValue;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return BigDecimal.ZERO;
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String decimalToString(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String toStockStatusLabel(String status) {
        return switch (status) {
            case "expired" -> "已过期";
            case "low" -> "库存不足";
            case "high" -> "库存积压";
            default -> "正常";
        };
    }
}
