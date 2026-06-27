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
import com.xykj.common.service.DataScopeService;
import com.xykj.sys.dto.DispatchQueryDTO;
import com.xykj.sys.dto.ProcessFormDTO;
import com.xykj.sys.entity.*;
import com.xykj.sys.mapper.*;
import com.xykj.sys.service.DispatchService;
import com.xykj.sys.vo.DispatchDetailVO;
import com.xykj.sys.vo.DispatchVO;
import com.xykj.sys.vo.WorkOrderRecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 派单服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final ComplaintDispatchMapper complaintDispatchMapper;
    private final ComplaintMapper complaintMapper;
    private final WorkOrderRecordMapper workOrderRecordMapper;
    private final OrganizationMapper organizationMapper;
    private final EmployeeMapper employeeMapper;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;

    @Override
    @DataScope
    public PageResult<DispatchVO> list(DispatchQueryDTO query) {
        Long tenantId = UserContext.getTenantId();
        Long currentUserId = UserContext.getUserId();

        // 获取当前账号对应的员工ID，用于匹配 handler_id
        Long currentEmployeeId = currentUserId != null
                ? employeeMapper.selectIdByUserId(currentUserId) : null;

        log.info("[DataScope调试] 派单列表查询 - userId: {}, employeeId: {}, orgId: {}, orgIds: {}",
                currentUserId, currentEmployeeId, query.getOrgId(), query.getOrgIds());

        // 构建查询条件
        LambdaQueryWrapper<ComplaintDispatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(tenantId != null, ComplaintDispatch::getTenantId, tenantId)
                .eq(StrUtil.isNotBlank(query.getDispatchType()), ComplaintDispatch::getDispatchType, query.getDispatchType())
                .eq(StrUtil.isNotBlank(query.getStatus()), ComplaintDispatch::getStatus, query.getStatus())
                .eq(query.getOrgId() != null, ComplaintDispatch::getOrgId, query.getOrgId())
                .like(StrUtil.isNotBlank(query.getHandlerName()), ComplaintDispatch::getHandlerName, query.getHandlerName())
                .orderByDesc(ComplaintDispatch::getCreatedAt);

        // 数据权限过滤（@DataScope 已注入 orgIds）
        if (query.getOrgId() == null && query.getOrgIds() != null) {
            if (query.getOrgIds().isEmpty()) {
                // 无任何组织权限：只能看到派给自己的
                if (currentEmployeeId != null) {
                    wrapper.eq(ComplaintDispatch::getHandlerId, currentEmployeeId);
                } else {
                    wrapper.isNull(ComplaintDispatch::getId);
                }
            } else if (currentEmployeeId != null) {
                // 有组织权限 + 也能看到派给自己的
                wrapper.and(w -> w.in(ComplaintDispatch::getOrgId, query.getOrgIds())
                        .or()
                        .eq(ComplaintDispatch::getHandlerId, currentEmployeeId));
            } else {
                wrapper.in(ComplaintDispatch::getOrgId, query.getOrgIds());
            }
        }

        // 时间范围筛选
        if (StrUtil.isNotBlank(query.getStartTime())) {
            wrapper.ge(ComplaintDispatch::getCreatedAt, query.getStartTime() + " 00:00:00");
        }
        if (StrUtil.isNotBlank(query.getEndTime())) {
            wrapper.le(ComplaintDispatch::getCreatedAt, query.getEndTime() + " 23:59:59");
        }

        // 分页查询
        IPage<ComplaintDispatch> page = complaintDispatchMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()),
                wrapper
        );

        // 转换为VO
        List<DispatchVO> voList = page.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充组织名称和优先级
        fillOrgNames(voList);
        fillPriorityInfo(voList);

        return PageResult.of(page, voList);
    }

    @Override
    public DispatchDetailVO getDetail(Long id) {
        ComplaintDispatch dispatch = getDispatchById(id);
        ensureDispatchReadable(dispatch);
        DispatchDetailVO vo = convertToDetailVO(dispatch);

        // 填充组织名称
        if (dispatch.getOrgId() != null) {
            Organization org = organizationMapper.selectById(dispatch.getOrgId());
            if (org != null) {
                vo.setOrgName(org.getOrgName());
            }
        }

        // 填充优先级
        if (dispatch.getComplaintId() != null) {
            Complaint complaint = complaintMapper.selectById(dispatch.getComplaintId());
            if (complaint != null) {
                vo.setPriority(complaint.getPriority());
                vo.setPriorityName(getPriorityName(complaint.getPriority()));
            }
        }

        // 填充处理记录
        List<WorkOrderRecord> records = workOrderRecordMapper.selectByDispatchId(id);
        List<WorkOrderRecordVO> recordVOs = records.stream()
                .map(this::convertToRecordVO)
                .collect(Collectors.toList());
        vo.setRecords(recordVOs);

        return vo;
    }

    @Override
    public List<WorkOrderRecordVO> getRecords(Long id) {
        ComplaintDispatch dispatch = getDispatchById(id);
        ensureDispatchReadable(dispatch);
        List<WorkOrderRecord> records = workOrderRecordMapper.selectByDispatchId(id);
        return records.stream()
                .map(this::convertToRecordVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> process(Long id, ProcessFormDTO dto) {
        ComplaintDispatch dispatch = getDispatchById(id);
        String action = dto.getAction();

        // 捕获处理前数据（仅关键标识和状态）
        Map<String, Object> beforeDataMap = new HashMap<>();
        beforeDataMap.put("dispatchNo", dispatch.getDispatchNo());
        beforeDataMap.put("dispatchStatus", dispatch.getStatus());
        if (dispatch.getComplaintId() != null) {
            Complaint complaintBefore = complaintMapper.selectById(dispatch.getComplaintId());
            if (complaintBefore != null) {
                beforeDataMap.put("complaintNo", complaintBefore.getComplaintNo());
                beforeDataMap.put("complaintStatus", complaintBefore.getStatus());
            }
        }

        // 校验状态
        switch (action) {
            case "process":
                // 标记处理中：当前状态必须是pending或processing
                if (!"pending".equals(dispatch.getStatus()) && !"processing".equals(dispatch.getStatus())) {
                    throw BizException.conflict("当前状态不允许标记处理中");
                }
                // 仅从pending变为processing时才更新状态
                if ("pending".equals(dispatch.getStatus())) {
                    dispatch.setStatus("processing");
                    // 同步更新投诉状态为处理中
                    Complaint complaintForProcess = complaintMapper.selectById(dispatch.getComplaintId());
                    if (complaintForProcess != null) {
                        complaintForProcess.setStatus("processing");
                        complaintMapper.updateById(complaintForProcess);
                    }
                }
                break;
            case "complete":
                // 完成处理：当前状态必须是pending或processing
                if (!"pending".equals(dispatch.getStatus()) && !"processing".equals(dispatch.getStatus())) {
                    throw BizException.conflict("当前状态不允许完成处理");
                }
                dispatch.setStatus("completed");
                dispatch.setCompletedAt(LocalDateTime.now());
                dispatch.setHandleResult(dto.getContent());
                dispatch.setHandleImages(dto.getImages() != null ? JSONUtil.toJsonStr(dto.getImages()) : null);

                // 同步更新投诉状态为closed
                Complaint complaint = complaintMapper.selectById(dispatch.getComplaintId());
                if (complaint != null) {
                    complaint.setStatus("closed");
                    complaintMapper.updateById(complaint);
                }
                break;
            case "cancel":
                // 取消工单
                if ("completed".equals(dispatch.getStatus()) || "cancelled".equals(dispatch.getStatus())) {
                    throw BizException.conflict("当前状态不允许取消");
                }
                dispatch.setStatus("cancelled");
                // 同步更新投诉状态为已闭环
                Complaint complaintForCancel = complaintMapper.selectById(dispatch.getComplaintId());
                if (complaintForCancel != null) {
                    complaintForCancel.setStatus("closed");
                    complaintMapper.updateById(complaintForCancel);
                }
                break;
            default:
                throw BizException.validationFailed("不支持的操作类型: " + action);
        }

        complaintDispatchMapper.updateById(dispatch);

        // 记录工单操作
        String actionName = getActionName(action);
        Long operatorId = UserContext.getUserId();
        String operatorName = UserContext.getRealName();
        createWorkOrderRecord(dispatch.getId(), dispatch.getComplaintId(), action, actionName,
                operatorId, operatorName, dto.getContent(),
                dto.getImages() != null ? JSONUtil.toJsonStr(dto.getImages()) : null,
                dispatch.getOrgId(), dispatch.getTenantId());

        log.info("处理工单成功: dispatchId={}, action={}", id, action);

        // 捕获处理后数据（仅关键标识、状态变化、处理内容、操作人）
        Map<String, Object> afterDataMap = new HashMap<>();
        afterDataMap.put("dispatchNo", dispatch.getDispatchNo());
        afterDataMap.put("dispatchStatus", dispatch.getStatus());
        afterDataMap.put("action", actionName);
        afterDataMap.put("operatorId", operatorId);
        afterDataMap.put("operatorName", operatorName);
        if (dto.getContent() != null) {
            afterDataMap.put("content", dto.getContent());
        }
        if ("complete".equals(action)) {
            afterDataMap.put("handleResult", dispatch.getHandleResult());
            if (dispatch.getCompletedAt() != null) {
                afterDataMap.put("completedAt", dispatch.getCompletedAt().toString());
            }
        }
        if (dispatch.getComplaintId() != null) {
            Complaint complaintAfter = complaintMapper.selectById(dispatch.getComplaintId());
            if (complaintAfter != null) {
                afterDataMap.put("complaintNo", complaintAfter.getComplaintNo());
                afterDataMap.put("complaintStatus", complaintAfter.getStatus());
            }
        }

        auditLogService.log(AuditModule.SYS_DISPATCH, AuditOperationType.PROCESS, id, dispatch.getDispatchNo(),
            "处理工单：" + dispatch.getDispatchNo() + "，动作：" + actionName,
            JSONUtil.toJsonStr(beforeDataMap), JSONUtil.toJsonStr(afterDataMap));

        Map<String, Object> result = new HashMap<>();
        result.put("dispatchId", id);
        result.put("dispatchNo", dispatch.getDispatchNo());
        result.put("status", dispatch.getStatus());
        result.put("complaintId", dispatch.getComplaintId());

        // 如果是完成操作，返回投诉状态和完成时间
        if ("complete".equals(action)) {
            result.put("complaintStatus", "closed");
            result.put("completedAt", dispatch.getCompletedAt());
        }

        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 根据ID获取派单
     */
    private ComplaintDispatch getDispatchById(Long id) {
        ComplaintDispatch dispatch = complaintDispatchMapper.selectById(id);
        if (dispatch == null) {
            throw BizException.notFound("派单记录不存在");
        }
        return dispatch;
    }

    private void ensureDispatchReadable(ComplaintDispatch dispatch) {
        if (dispatch == null) {
            throw BizException.notFound("派单记录不存在");
        }
        Long tenantId = UserContext.getTenantId();
        if (tenantId != null && dispatch.getTenantId() != null && !tenantId.equals(dispatch.getTenantId())) {
            throw BizException.forbidden("无权访问该派单数据");
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null && currentUserId.equals(dispatch.getHandlerId())) {
            return;
        }
        if (dataScopeService.isAdminUser()) {
            return;
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        if (!scope.isAllowed(dispatch.getOrgId())) {
            throw BizException.forbidden("无权访问该派单数据");
        }
    }

    /**
     * 转换为VO
     */
    private DispatchVO convertToVO(ComplaintDispatch dispatch) {
        DispatchVO vo = new DispatchVO();
        BeanUtil.copyProperties(dispatch, vo);
        vo.setDispatchTypeName(getDispatchTypeName(dispatch.getDispatchType()));
        vo.setStatusName(getDispatchStatusName(dispatch.getStatus()));
        return vo;
    }

    /**
     * 转换为详情VO
     */
    private DispatchDetailVO convertToDetailVO(ComplaintDispatch dispatch) {
        DispatchDetailVO vo = new DispatchDetailVO();
        BeanUtil.copyProperties(dispatch, vo);
        vo.setDispatchTypeName(getDispatchTypeName(dispatch.getDispatchType()));
        vo.setStatusName(getDispatchStatusName(dispatch.getStatus()));
        // 从 dispatch 自身获取优先级
        vo.setPriority(dispatch.getPriority());
        vo.setPriorityName(getPriorityName(dispatch.getPriority()));
        vo.setHandleImages(dispatch.getHandleImages() != null ?
                JSONUtil.toList(dispatch.getHandleImages(), String.class) : null);
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
    private void fillOrgNames(List<DispatchVO> voList) {
        List<Long> orgIds = voList.stream()
                .map(DispatchVO::getOrgId)
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
     * 填充优先级信息（从派单自身获取）
     */
    private void fillPriorityInfo(List<DispatchVO> voList) {
        voList.forEach(vo -> {
            vo.setPriorityName(getPriorityName(vo.getPriority()));
        });
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

    private String getPriorityName(String priority) {
        if (priority == null) return "";
        switch (priority) {
            case "high": return "高";
            case "medium": return "中";
            case "low": return "低";
            default: return priority;
        }
    }

    private String getActionName(String action) {
        if (action == null) return "";
        switch (action) {
            case "dispatch": return "派单";
            case "reassign": return "改派";
            case "process": return "处理";
            case "complete": return "完成";
            case "cancel": return "取消";
            default: return action;
        }
    }
}
