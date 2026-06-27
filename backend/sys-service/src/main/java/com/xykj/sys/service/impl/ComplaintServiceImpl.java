package com.xykj.sys.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.annotation.DataScope;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.result.PageResult;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.NotificationHelper;
import com.xykj.sys.dto.ComplaintCreateDTO;
import com.xykj.sys.dto.ComplaintQueryDTO;
import com.xykj.sys.dto.DispatchFormDTO;
import com.xykj.sys.dto.SatisfactionFormDTO;
import com.xykj.sys.entity.*;
import com.xykj.sys.mapper.*;
import com.xykj.sys.service.ComplaintService;
import com.xykj.sys.service.SensitiveWordService;
import com.xykj.sys.vo.ComplaintDetailVO;
import com.xykj.sys.vo.ComplaintVO;
import com.xykj.sys.vo.WorkOrderRecordVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
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
 * 投诉服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintServiceImpl implements ComplaintService {

    private final ComplaintMapper complaintMapper;
    private final ComplaintDispatchMapper complaintDispatchMapper;
    private final WorkOrderRecordMapper workOrderRecordMapper;
    private final EmployeeMapper employeeMapper;
    private final OrganizationMapper organizationMapper;
    private final AuditLogService auditLogService;
    private final NotificationHelper notificationHelper;
    private final JdbcTemplate jdbcTemplate;
    private final SensitiveWordService sensitiveWordService;

    @Override
    @DataScope
    public PageResult<ComplaintVO> list(ComplaintQueryDTO query) {
        log.info("[DataScope调试] 投诉列表查询 - userId: {}, orgId: {}, orgIds: {}",
                UserContext.getUserId(), query.getOrgId(), query.getOrgIds());

        // 构建查询条件
        LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(query.getComplaintType()), Complaint::getComplaintType, query.getComplaintType())
                .eq(StrUtil.isNotBlank(query.getSource()), Complaint::getSource, query.getSource())
                .eq(StrUtil.isNotBlank(query.getStatus()), Complaint::getStatus, query.getStatus())
                .eq(StrUtil.isNotBlank(query.getPriority()), Complaint::getPriority, query.getPriority())
                .eq(query.getOrgId() != null, Complaint::getOrgId, query.getOrgId())
                .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Complaint::getOrgId, query.getOrgIds())
                .like(StrUtil.isNotBlank(query.getSubmitterName()), Complaint::getSubmitterName, query.getSubmitterName())
                .orderByDesc(Complaint::getCreatedAt);

        if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
            wrapper.isNull(Complaint::getId);
        }

        // 时间范围筛选
        if (StrUtil.isNotBlank(query.getStartTime())) {
            wrapper.ge(Complaint::getCreatedAt, query.getStartTime() + " 00:00:00");
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(Complaint::getCreatedAt, query.getEndTime() + " 23:59:59");
        }

        // 分页查询
        IPage<Complaint> page = complaintMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 转换为VO
        List<ComplaintVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充组织名称和派单信息
        fillOrgNames(voList);
        fillDispatchInfo(voList);

        return PageResult.of(page, voList);
    }

    @Override
    public ComplaintDetailVO getDetail(Long id) {
        Complaint complaint = getComplaintById(id);
        ComplaintDetailVO vo = convertToDetailVO(complaint);

        // 填充组织名称
        if (complaint.getOrgId() != null) {
            Organization org = organizationMapper.selectById(complaint.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getOrgName());
            }
        }

        // 填充派单信息
        ComplaintDispatch dispatch = complaintDispatchMapper.selectByComplaintId(id);
        if (dispatch != null) {
            ComplaintDetailVO.DispatchInfo dispatchInfo = new ComplaintDetailVO.DispatchInfo();
            BeanUtil.copyProperties(dispatch, dispatchInfo);
            dispatchInfo.setDispatchId(dispatch.getId());
            dispatchInfo.setDispatchTypeName(getDispatchTypeName(dispatch.getDispatchType()));
            dispatchInfo.setStatusName(getDispatchStatusName(dispatch.getStatus()));
            dispatchInfo.setPriority(dispatch.getPriority());
            dispatchInfo.setPriorityName(getPriorityName(dispatch.getPriority()));
            dispatchInfo.setHandleImages(dispatch.getHandleImages() != null ?
                    JSONUtil.toList(dispatch.getHandleImages(), String.class) : null);
            vo.setDispatch(dispatchInfo);
            vo.setDispatchId(dispatch.getId());
        }

        // 填充处理记录
        List<WorkOrderRecord> records = workOrderRecordMapper.selectByComplaintId(id);
        List<WorkOrderRecordVO> recordVOs = records.stream()
                .map(this::convertToRecordVO)
                .collect(Collectors.toList());
        vo.setRecords(recordVOs);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> create(ComplaintCreateDTO dto) {
        // 敏感词校验
        if (StrUtil.isNotBlank(dto.getDescription())) {
            if (sensitiveWordService.containsSensitiveWord(dto.getDescription())) {
                auditLogService.log(AuditModule.SYS_COMPLAINT, AuditOperationType.CREATE, null, null,
                    "新增投诉失败：内容包含敏感信息", null,
                    JSONUtil.toJsonStr(Map.of("description", dto.getDescription(), "title", dto.getTitle() != null ? dto.getTitle() : "")),
                    "failure", "内容包含敏感信息");
                throw BizException.validationFailed("内容包含敏感信息，提交失败，请修改后重试");
            }
        }
        if (StrUtil.isNotBlank(dto.getTitle())) {
            if (sensitiveWordService.containsSensitiveWord(dto.getTitle())) {
                auditLogService.log(AuditModule.SYS_COMPLAINT, AuditOperationType.CREATE, null, null,
                    "新增投诉失败：标题包含敏感信息", null,
                    JSONUtil.toJsonStr(Map.of("title", dto.getTitle(), "description", dto.getDescription() != null ? dto.getDescription() : "")),
                    "failure", "标题包含敏感信息");
                throw BizException.validationFailed("内容包含敏感信息，提交失败，请修改后重试");
            }
        }

        // 创建投诉实体
        Complaint complaint = new Complaint();
        BeanUtil.copyProperties(dto, complaint);
        complaint.setTenantId(UserContext.getTenantId());
        complaint.setComplaintNo(complaintMapper.generateComplaintNo());
        complaint.setStatus("pending");
        complaint.setPriority(dto.getPriority() != null ? dto.getPriority() : "medium");
        complaint.setImages(dto.getImages() != null ? JSONUtil.toJsonStr(dto.getImages()) : null);

        complaintMapper.insert(complaint);

        log.info("创建投诉成功: id={}, complaintNo={}", complaint.getId(), complaint.getComplaintNo());

        auditLogService.log(AuditModule.SYS_COMPLAINT, AuditOperationType.CREATE, complaint.getId(), complaint.getComplaintNo(),
            "新增投诉：" + complaint.getTitle() + "（" + complaint.getComplaintNo() + "）",
            null, JSONUtil.toJsonStr(complaint));

        Map<String, Object> result = new HashMap<>();
        result.put("id", complaint.getId());
        result.put("complaintNo", complaint.getComplaintNo());
        result.put("status", complaint.getStatus());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> dispatch(Long id, DispatchFormDTO dto) {
        Complaint complaint = getComplaintById(id);

        // 捕获派单前数据（仅关键标识和状态）
        Map<String, Object> beforeDataMap = new HashMap<>();
        beforeDataMap.put("complaintNo", complaint.getComplaintNo());
        beforeDataMap.put("complaintTitle", complaint.getTitle());
        beforeDataMap.put("complaintStatus", complaint.getStatus());

        // 校验投诉状态
        if (!"pending".equals(complaint.getStatus())) {
            throw BizException.conflict("该投诉已派单，不能重复派单");
        }

        // 确定处理人
        Long handlerId;
        String handlerName;
        Long assignerId = 0L;
        String assignerName = "系统";

        if ("auto".equals(dto.getDispatchType())) {
            // 自动派单：查找可处理人（这里简单实现，实际可根据规则分配）
            handlerId = findAvailableHandler(complaint.getOrgId());
            if (handlerId == null) {
                throw BizException.conflict("没有可用的处理人");
            }
            Employee handler = employeeMapper.selectById(handlerId);
            handlerName = handler != null ? handler.getRealName() : "未知";
        } else {
            // 人工派单
            if (dto.getHandlerId() == null) {
                throw BizException.validationFailed("请选择处理人");
            }
            handlerId = dto.getHandlerId();
            Employee handler = employeeMapper.selectById(handlerId);
            if (handler == null) {
                throw BizException.notFound("处理人不存在");
            }
            handlerName = handler.getRealName();
            // 获取当前登录用户作为派单人
            assignerId = UserContext.getUserId();
            assignerName = UserContext.getRealName();
        }

        // 创建派单记录
        ComplaintDispatch dispatch = new ComplaintDispatch();
        dispatch.setDispatchNo(complaintDispatchMapper.generateDispatchNo());
        dispatch.setComplaintId(id);
        dispatch.setComplaintNo(complaint.getComplaintNo());
        dispatch.setComplaintTitle(complaint.getTitle());
        dispatch.setDispatchType(dto.getDispatchType());
        dispatch.setAssignerId(assignerId);
        dispatch.setAssignerName(assignerName);
        dispatch.setHandlerId(handlerId);
        dispatch.setHandlerName(handlerName);
        // 自动派单时设置默认截止时间（24小时后）
        if ("auto".equals(dto.getDispatchType()) && dto.getDeadline() == null) {
            dispatch.setDeadline(LocalDateTime.now().plusHours(24));
        } else {
            dispatch.setDeadline(dto.getDeadline());
        }
        dispatch.setRemark(dto.getRemark());
        dispatch.setStatus("pending");
        dispatch.setOrgId(complaint.getOrgId());
        dispatch.setTenantId(complaint.getTenantId());

        // 设置优先级
        if ("manual".equals(dto.getDispatchType())) {
            dispatch.setPriority(dto.getPriority());
        } else {
            dispatch.setPriority(complaint.getPriority());
        }

        complaintDispatchMapper.insert(dispatch);

        // 更新投诉状态
        complaint.setStatus("dispatched");
        complaintMapper.updateById(complaint);

        // 记录工单操作（自动派单操作人为"系统"）
        Long operatorId = "auto".equals(dto.getDispatchType()) ? 0L : assignerId;
        String operatorName = "auto".equals(dto.getDispatchType()) ? "系统" : assignerName;
        createWorkOrderRecord(dispatch.getId(), id, "dispatch", "派单",
                operatorId, operatorName, "派单给" + handlerName, null,
                complaint.getOrgId(), complaint.getTenantId());

        log.info("派单成功: complaintId={}, dispatchId={}, handlerId={}", id, dispatch.getId(), handlerId);

        String dispatchTypeDesc = "auto".equals(dto.getDispatchType()) ? "自动" : "人工";

        // 捕获派单后数据（仅关键标识、状态变化、处理人）
        Map<String, Object> afterDataMap = new HashMap<>();
        afterDataMap.put("complaintNo", complaint.getComplaintNo());
        afterDataMap.put("complaintStatus", complaint.getStatus());
        afterDataMap.put("dispatchNo", dispatch.getDispatchNo());
        afterDataMap.put("dispatchType", "auto".equals(dto.getDispatchType()) ? "自动派单" : "人工派单");
        afterDataMap.put("handlerId", handlerId);
        afterDataMap.put("handlerName", handlerName);
        afterDataMap.put("operatorId", "auto".equals(dto.getDispatchType()) ? 0L : assignerId);
        afterDataMap.put("operatorName", "auto".equals(dto.getDispatchType()) ? "系统" : assignerName);
        afterDataMap.put("priority", dispatch.getPriority());
        if (dispatch.getRemark() != null) {
            afterDataMap.put("remark", dispatch.getRemark());
        }
        if (dispatch.getDeadline() != null) {
            afterDataMap.put("deadline", dispatch.getDeadline().toString());
        }

        auditLogService.log(AuditModule.SYS_DISPATCH, AuditOperationType.DISPATCH, id, complaint.getComplaintNo(),
            dispatchTypeDesc + "派单：" + complaint.getComplaintNo() + " → " + handlerName,
            JSONUtil.toJsonStr(beforeDataMap), JSONUtil.toJsonStr(afterDataMap));

        // 发送通知给处理人
        try {
            Long authUserId = notificationHelper.employeeIdToAuthUserId(handlerId);
            if (authUserId != null) {
                String riskLevel = switch (dispatch.getPriority()) {
                    case "high" -> "high";
                    case "low" -> "normal";
                    default -> "attention";
                };
                notificationHelper.send(authUserId, "approval_todo", "complaint_dispatch",
                        "投诉派单通知", dispatchTypeDesc + "派单：" + complaint.getComplaintNo() + " → " + handlerName,
                        riskLevel, "投诉管理", dispatch.getId(), "complaint_dispatch",
                        complaint.getTenantId(), complaint.getOrgId(),
                        "[{\"label\":\"去处理\",\"route\":\"/evaluation?tab=dispatch\"}]");
            }
        } catch (Exception e) {
            log.warn("投诉派单通知发送失败: complaintId={}", id, e);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("dispatchId", dispatch.getId());
        result.put("dispatchNo", dispatch.getDispatchNo());
        result.put("complaintId", id);
        result.put("complaintNo", complaint.getComplaintNo());
        result.put("dispatchType", dto.getDispatchType());
        result.put("handlerId", handlerId);
        result.put("handlerName", handlerName);
        result.put("deadline", dispatch.getDeadline() != null
                ? dispatch.getDeadline().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                : null);
        result.put("status", "pending");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSatisfaction(Long id, SatisfactionFormDTO dto) {
        Complaint complaint = getComplaintById(id);

        // 校验投诉状态
        if (!"closed".equals(complaint.getStatus())) {
            throw BizException.conflict("只有已闭环的投诉才能进行满意度评价");
        }

        // 更新满意度
        complaint.setSatisfaction(dto.getSatisfaction());
        complaint.setSatisfactionRemark(dto.getSatisfactionRemark());
        complaintMapper.updateById(complaint);

        log.info("满意度评价成功: complaintId={}, satisfaction={}", id, dto.getSatisfaction());

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("complaintNo", complaint.getComplaintNo());
        result.put("satisfaction", dto.getSatisfaction());
        result.put("satisfactionName", getSatisfactionName(dto.getSatisfaction()));
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 根据ID获取投诉
     */
    private Complaint getComplaintById(Long id) {
        Complaint complaint = complaintMapper.selectById(id);
        if (complaint == null) {
            throw BizException.notFound("投诉不存在");
        }
        return complaint;
    }

    /**
     * 转换为VO
     */
    private ComplaintVO convertToVO(Complaint complaint) {
        ComplaintVO vo = new ComplaintVO();
        BeanUtil.copyProperties(complaint, vo);
        vo.setComplaintTypeName(getComplaintTypeName(complaint.getComplaintType()));
        vo.setSourceName(getSourceName(complaint.getSource()));
        vo.setStatusName(getComplaintStatusName(complaint.getStatus()));
        vo.setPriorityName(getPriorityName(complaint.getPriority()));
        return vo;
    }

    /**
     * 转换为详情VO
     */
    private ComplaintDetailVO convertToDetailVO(Complaint complaint) {
        ComplaintDetailVO vo = new ComplaintDetailVO();
        BeanUtil.copyProperties(complaint, vo);
        vo.setComplaintTypeName(getComplaintTypeName(complaint.getComplaintType()));
        vo.setSourceName(getSourceName(complaint.getSource()));
        vo.setStatusName(getComplaintStatusName(complaint.getStatus()));
        vo.setPriorityName(getPriorityName(complaint.getPriority()));
        vo.setSatisfactionName(getSatisfactionName(complaint.getSatisfaction()));
        vo.setImages(complaint.getImages() != null ? JSONUtil.toList(complaint.getImages(), String.class) : null);
        return vo;
    }

    /**
     * 转换处理记录为VO
     */
    private WorkOrderRecordVO convertToRecordVO(WorkOrderRecord record) {
        WorkOrderRecordVO vo = new WorkOrderRecordVO();
        BeanUtil.copyProperties(record, vo);
        vo.setImages(record.getImages() != null ? JSONUtil.toList(record.getImages(), String.class) : null);
        return vo;
    }

    /**
     * 填充组织名称
     */
    private void fillOrgNames(List<ComplaintVO> voList) {
        List<Long> orgIds = voList.stream()
                .map(ComplaintVO::getOrgId)
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
     * 填充派单信息
     */
    private void fillDispatchInfo(List<ComplaintVO> voList) {
        List<Long> complaintIds = voList.stream()
                .map(ComplaintVO::getId)
                .collect(Collectors.toList());

        if (complaintIds.isEmpty()) {
            return;
        }

        // 批量查询派单信息
        LambdaQueryWrapper<ComplaintDispatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ComplaintDispatch::getComplaintId, complaintIds)
                .orderByDesc(ComplaintDispatch::getCreatedAt);
        List<ComplaintDispatch> dispatches = complaintDispatchMapper.selectList(wrapper);

        Map<Long, ComplaintDispatch> dispatchMap = dispatches.stream()
                .collect(Collectors.toMap(
                        ComplaintDispatch::getComplaintId,
                        d -> d,
                        (existing, replacement) -> existing  // 保留第一个（最新的）
                ));

        voList.forEach(vo -> {
            ComplaintDispatch dispatch = dispatchMap.get(vo.getId());
            if (dispatch != null) {
                vo.setDispatchType(dispatch.getDispatchType());
                vo.setDispatchId(dispatch.getId());
                vo.setHandlerId(dispatch.getHandlerId());
                vo.setHandlerName(dispatch.getHandlerName());
                vo.setDeadline(dispatch.getDeadline());
            }
        });
    }

    /**
     * 查找可用的处理人（自动派单）
     * 规则：同组织 + 员工在职 + 账号启用 + 待处理工单最少
     */
    private Long findAvailableHandler(Long orgId) {
        return employeeMapper.selectLeastBusyHandler(orgId);
    }

    /**
     * 创建工单处理记录
     */
    private void createWorkOrderRecord(Long dispatchId, Long complaintId, String action,
                                        String actionName, Long operatorId, String operatorName,
                                        String content, String images, Long orgId, Long tenantId) {
        WorkOrderRecord record = new WorkOrderRecord();
        record.setDispatchId(dispatchId);
        record.setComplaintId(complaintId);
        record.setAction(action);
        record.setActionName(actionName);
        record.setOperatorId(operatorId);
        record.setOperatorName(operatorName);
        record.setContent(content);
        record.setImages(images);
        record.setOrgId(orgId);
        record.setTenantId(tenantId);
        workOrderRecordMapper.insert(record);
    }

    // ==================== 名称转换方法 ====================

    @Override
    @DataScope
    public void exportComplaints(ComplaintQueryDTO query, HttpServletResponse response) {
        try {
            // 复用 list() 的查询条件构建
            LambdaQueryWrapper<Complaint> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(StrUtil.isNotBlank(query.getComplaintType()), Complaint::getComplaintType, query.getComplaintType())
                    .eq(StrUtil.isNotBlank(query.getSource()), Complaint::getSource, query.getSource())
                    .eq(StrUtil.isNotBlank(query.getStatus()), Complaint::getStatus, query.getStatus())
                    .eq(StrUtil.isNotBlank(query.getPriority()), Complaint::getPriority, query.getPriority())
                    .eq(query.getOrgId() != null, Complaint::getOrgId, query.getOrgId())
                    .in(query.getOrgId() == null && query.getOrgIds() != null && !query.getOrgIds().isEmpty(), Complaint::getOrgId, query.getOrgIds())
                    .like(StrUtil.isNotBlank(query.getSubmitterName()), Complaint::getSubmitterName, query.getSubmitterName())
                    .orderByDesc(Complaint::getCreatedAt);

            if (query.getOrgId() == null && query.getOrgIds() != null && query.getOrgIds().isEmpty()) {
                wrapper.isNull(Complaint::getId);
            }

            if (StrUtil.isNotBlank(query.getStartTime())) {
                wrapper.ge(Complaint::getCreatedAt, query.getStartTime() + " 00:00:00");
            }
            if (StrUtil.isNotBlank(query.getEndTime())) {
                wrapper.le(Complaint::getCreatedAt, query.getEndTime() + " 23:59:59");
            }

            List<Complaint> complaints = complaintMapper.selectList(wrapper);

            // 转换为VO并填充组织名称、派单信息
            List<ComplaintVO> voList = complaints.stream().map(this::convertToVO).collect(Collectors.toList());
            fillOrgNames(voList);
            fillDispatchInfo(voList);

            // 构建 complaintId -> Complaint 映射，用于获取 description 等字段
            Map<Long, Complaint> complaintMap = complaints.stream()
                    .collect(Collectors.toMap(Complaint::getId, c -> c));

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = URLEncoder.encode("投诉数据导出_" + timestamp, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName + ".xlsx");

            String[] headers = {"序号", "投诉编号", "来源", "标题", "投诉人", "联系电话", "门店", "投诉类型", "关联菜品", "投诉描述", "处理状态", "派单方式", "处理人", "优先级", "满意度", "创建时间"};
            int[] colWidths = {6, 18, 10, 25, 10, 14, 15, 10, 15, 30, 10, 10, 10, 8, 8, 20};

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("投诉数据");

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
            for (ComplaintVO vo : voList) {
                Complaint c = complaintMap.get(vo.getId());
                Row dataRow = sheet.createRow(rowNum++);
                String[] values = {
                        String.valueOf(index++),
                        vo.getComplaintNo() != null ? vo.getComplaintNo() : "",
                        vo.getSourceName() != null ? vo.getSourceName() : "",
                        vo.getTitle() != null ? vo.getTitle() : "",
                        vo.getSubmitterName() != null ? vo.getSubmitterName() : "",
                        c != null && c.getSubmitterPhone() != null ? c.getSubmitterPhone() : "",
                        vo.getOrgName() != null ? vo.getOrgName() : "",
                        vo.getComplaintTypeName() != null ? vo.getComplaintTypeName() : "",
                        c != null && c.getRelatedMenuName() != null ? c.getRelatedMenuName() : "",
                        c != null && c.getDescription() != null ? c.getDescription() : "",
                        vo.getStatusName() != null ? vo.getStatusName() : "",
                        vo.getDispatchType() != null ? getDispatchTypeName(vo.getDispatchType()) : "",
                        vo.getHandlerName() != null ? vo.getHandlerName() : "",
                        vo.getPriorityName() != null ? vo.getPriorityName() : "",
                        vo.getSatisfaction() != null ? getSatisfactionName(vo.getSatisfaction()) : "",
                        vo.getCreatedAt() != null ? vo.getCreatedAt().format(dtFormatter) : ""
                };
                for (int i = 0; i < values.length; i++) {
                    Cell cell = dataRow.createCell(i);
                    cell.setCellValue(values[i]);
                    cell.setCellStyle(dataStyle);
                }
            }

            workbook.write(response.getOutputStream());
            workbook.close();

            auditLogService.log(AuditModule.SYS_COMPLAINT, AuditOperationType.EXPORT, null, null,
                    "导出投诉数据：" + voList.size() + "条",
                    null, JSONUtil.toJsonStr(Map.of("count", voList.size())), "success", null);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出投诉失败", e);
            try {
                auditLogService.log(AuditModule.SYS_COMPLAINT, AuditOperationType.EXPORT, null, null,
                        "导出投诉数据失败", null, null, "failure", e.getMessage());
            } catch (Exception ignored) {
            }
            throw new BizException("导出失败");
        }
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

    // ==================== 名称转换方法 ====================

    private String getComplaintTypeName(String type) {
        if (type == null) return "";
        switch (type) {
            case "food": return "食品问题";
            case "service": return "服务问题";
            case "hygiene": return "卫生问题";
            case "other": return "其他";
            default: return type;
        }
    }

    private String getSourceName(String source) {
        if (source == null) return "";
        switch (source) {
            case "meal": return "用餐评价";
            case "supervision": return "监管反馈";
            case "manual": return "人工录入";
            default: return source;
        }
    }

    private String getComplaintStatusName(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending": return "待处理";
            case "dispatched": return "已派单";
            case "processing": return "处理中";
            case "closed": return "已闭环";
            default: return status;
        }
    }

    private String getPriorityName(String priority) {
        if (priority == null) return "";
        switch (priority) {
            case "high": return "高";
            case "medium": return "中";
            case "low": return "低";
            default: return priority;
        }
    }

    private String getSatisfactionName(String satisfaction) {
        if (satisfaction == null) return "";
        switch (satisfaction) {
            case "satisfied": return "满意";
            case "neutral": return "一般";
            case "dissatisfied": return "不满意";
            default: return satisfaction;
        }
    }

    private String getDispatchTypeName(String type) {
        if (type == null) return "";
        switch (type) {
            case "auto": return "自动派单";
            case "manual": return "人工派单";
            default: return type;
        }
    }

    private String getDispatchStatusName(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending": return "待处理";
            case "processing": return "处理中";
            case "completed": return "已完成";
            case "cancelled": return "已取消";
            default: return status;
        }
    }
}
