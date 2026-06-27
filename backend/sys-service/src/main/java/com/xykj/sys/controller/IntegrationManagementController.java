package com.xykj.sys.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xykj.common.result.PageResult;
import com.xykj.common.result.R;
import com.xykj.sys.dto.IntegrationCallbackLogQueryDTO;
import com.xykj.sys.dto.IntegrationFieldMappingDuplicateCheckDTO;
import com.xykj.sys.dto.IntegrationFieldMappingQueryDTO;
import com.xykj.sys.dto.IntegrationFieldMappingSaveDTO;
import com.xykj.sys.dto.IntegrationFileRecordQueryDTO;
import com.xykj.sys.dto.IntegrationHealthCheckQueryDTO;
import com.xykj.sys.dto.IntegrationInternalBizBindingQueryDTO;
import com.xykj.sys.dto.IntegrationInternalSwitchModeDTO;
import com.xykj.sys.dto.IntegrationInternalTriggerSyncDTO;
import com.xykj.sys.dto.IntegrationModuleConfigQueryDTO;
import com.xykj.sys.dto.IntegrationModuleConfigSaveDTO;
import com.xykj.sys.dto.IntegrationModuleConfigStatusDTO;
import com.xykj.sys.dto.IntegrationProviderTemplateQueryDTO;
import com.xykj.sys.dto.IntegrationProviderTemplateSaveDTO;
import com.xykj.sys.dto.IntegrationProviderTemplateStatusDTO;
import com.xykj.sys.dto.IntegrationStatusMappingDuplicateCheckDTO;
import com.xykj.sys.dto.IntegrationStatusMappingQueryDTO;
import com.xykj.sys.dto.IntegrationStatusMappingSaveDTO;
import com.xykj.sys.dto.IntegrationSyncLogHandleDTO;
import com.xykj.sys.dto.IntegrationSyncLogQueryDTO;
import com.xykj.sys.dto.IntegrationSyncTaskQueryDTO;
import com.xykj.sys.dto.IntegrationSyncTaskTriggerDTO;
import com.xykj.sys.service.IntegrationManagementService;
import com.xykj.sys.vo.IntegrationCallbackLogVO;
import com.xykj.sys.vo.IntegrationCallbackHandleResultVO;
import com.xykj.sys.vo.IntegrationExecutionResultVO;
import com.xykj.sys.vo.IntegrationFieldMappingVO;
import com.xykj.sys.vo.IntegrationFileRecordVO;
import com.xykj.sys.vo.IntegrationHealthCheckVO;
import com.xykj.sys.vo.IntegrationInternalBindingSummaryVO;
import com.xykj.sys.vo.IntegrationInternalLogsVO;
import com.xykj.sys.vo.IntegrationInternalSceneMetaVO;
import com.xykj.sys.vo.IntegrationInternalTriggerResultVO;
import com.xykj.sys.vo.IntegrationModuleConfigVO;
import com.xykj.sys.vo.IntegrationOverviewVO;
import com.xykj.sys.vo.IntegrationProviderTemplateVO;
import com.xykj.sys.vo.IntegrationStatusMappingVO;
import com.xykj.sys.vo.IntegrationSyncLogVO;
import com.xykj.sys.vo.IntegrationSyncLogProviderOptionVO;
import com.xykj.sys.vo.IntegrationSyncTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/v1/integration")
@RequiredArgsConstructor
public class IntegrationManagementController {

    private final IntegrationManagementService integrationManagementService;

    @GetMapping("/providers")
    public R<PageResult<IntegrationProviderTemplateVO>> pageProviders(IntegrationProviderTemplateQueryDTO query) {
        Page<IntegrationProviderTemplateVO> page = integrationManagementService.pageProviders(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/providers/{id}")
    public R<IntegrationProviderTemplateVO> getProvider(@PathVariable Long id) {
        return R.ok(integrationManagementService.getProvider(id));
    }

    @PostMapping("/providers")
    public R<Map<String, Object>> createProvider(@Valid @RequestBody IntegrationProviderTemplateSaveDTO dto) {
        return R.ok(Map.of("id", integrationManagementService.createProvider(dto)));
    }

    @PutMapping("/providers/{id}")
    public R<Void> updateProvider(@PathVariable Long id, @Valid @RequestBody IntegrationProviderTemplateSaveDTO dto) {
        integrationManagementService.updateProvider(id, dto);
        return R.ok();
    }

    @DeleteMapping("/providers/{id}")
    public R<Void> deleteProvider(@PathVariable Long id) {
        integrationManagementService.deleteProvider(id);
        return R.ok();
    }

    @PutMapping("/providers/{id}/status")
    public R<Void> changeProviderStatus(@PathVariable Long id, @Valid @RequestBody IntegrationProviderTemplateStatusDTO dto) {
        integrationManagementService.changeProviderStatus(id, dto);
        return R.ok();
    }

    @GetMapping("/module-configs")
    public R<PageResult<IntegrationModuleConfigVO>> pageModuleConfigs(IntegrationModuleConfigQueryDTO query) {
        Page<IntegrationModuleConfigVO> page = integrationManagementService.pageModuleConfigs(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/module-configs/{id}")
    public R<IntegrationModuleConfigVO> getModuleConfig(@PathVariable Long id) {
        return R.ok(integrationManagementService.getModuleConfig(id));
    }

    @PostMapping("/module-configs")
    public R<Map<String, Object>> createModuleConfig(@Valid @RequestBody IntegrationModuleConfigSaveDTO dto) {
        return R.ok(Map.of("id", integrationManagementService.createModuleConfig(dto)));
    }

    @PutMapping("/module-configs/{id}")
    public R<Void> updateModuleConfig(@PathVariable Long id, @Valid @RequestBody IntegrationModuleConfigSaveDTO dto) {
        integrationManagementService.updateModuleConfig(id, dto);
        return R.ok();
    }

    @DeleteMapping("/module-configs/{id}")
    public R<Void> deleteModuleConfig(@PathVariable Long id) {
        integrationManagementService.deleteModuleConfig(id);
        return R.ok();
    }

    @PutMapping("/module-configs/{id}/status")
    public R<Void> changeModuleConfigStatus(@PathVariable Long id, @Valid @RequestBody IntegrationModuleConfigStatusDTO dto) {
        integrationManagementService.changeModuleConfigStatus(id, dto);
        return R.ok();
    }

    @PostMapping("/module-configs/{id}/test")
    public R<IntegrationHealthCheckVO> testModuleConfig(@PathVariable Long id) {
        return R.ok(integrationManagementService.testModuleConfig(id));
    }

    @GetMapping("/field-mappings")
    public R<PageResult<IntegrationFieldMappingVO>> pageFieldMappings(IntegrationFieldMappingQueryDTO query) {
        Page<IntegrationFieldMappingVO> page = integrationManagementService.pageFieldMappings(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/field-mappings/{id}")
    public R<IntegrationFieldMappingVO> getFieldMapping(@PathVariable Long id) {
        return R.ok(integrationManagementService.getFieldMapping(id));
    }

    @PostMapping("/field-mappings/check-duplicate")
    public R<Void> checkFieldMappingDuplicate(@Valid @RequestBody IntegrationFieldMappingDuplicateCheckDTO dto) {
        integrationManagementService.checkFieldMappingDuplicate(dto);
        return R.ok();
    }

    @PostMapping("/field-mappings")
    public R<Map<String, Object>> createFieldMapping(@Valid @RequestBody IntegrationFieldMappingSaveDTO dto) {
        return R.ok(Map.of("id", integrationManagementService.createFieldMapping(dto)));
    }

    @PutMapping("/field-mappings/{id}")
    public R<Void> updateFieldMapping(@PathVariable Long id, @Valid @RequestBody IntegrationFieldMappingSaveDTO dto) {
        integrationManagementService.updateFieldMapping(id, dto);
        return R.ok();
    }

    @DeleteMapping("/field-mappings/{id}")
    public R<Void> deleteFieldMapping(@PathVariable Long id) {
        integrationManagementService.deleteFieldMapping(id);
        return R.ok();
    }

    @GetMapping("/status-mappings")
    public R<PageResult<IntegrationStatusMappingVO>> pageStatusMappings(IntegrationStatusMappingQueryDTO query) {
        Page<IntegrationStatusMappingVO> page = integrationManagementService.pageStatusMappings(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/status-mappings/{id}")
    public R<IntegrationStatusMappingVO> getStatusMapping(@PathVariable Long id) {
        return R.ok(integrationManagementService.getStatusMapping(id));
    }

    @PostMapping("/status-mappings/check-duplicate")
    public R<Void> checkStatusMappingDuplicate(@Valid @RequestBody IntegrationStatusMappingDuplicateCheckDTO dto) {
        integrationManagementService.checkStatusMappingDuplicate(dto);
        return R.ok();
    }

    @PostMapping("/status-mappings")
    public R<Map<String, Object>> createStatusMapping(@Valid @RequestBody IntegrationStatusMappingSaveDTO dto) {
        return R.ok(Map.of("id", integrationManagementService.createStatusMapping(dto)));
    }

    @PutMapping("/status-mappings/{id}")
    public R<Void> updateStatusMapping(@PathVariable Long id, @Valid @RequestBody IntegrationStatusMappingSaveDTO dto) {
        integrationManagementService.updateStatusMapping(id, dto);
        return R.ok();
    }

    @DeleteMapping("/status-mappings/{id}")
    public R<Void> deleteStatusMapping(@PathVariable Long id) {
        integrationManagementService.deleteStatusMapping(id);
        return R.ok();
    }

    @GetMapping("/overview")
    public R<IntegrationOverviewVO> getOverview(@RequestParam(required = false) Long orgId) {
        return R.ok(integrationManagementService.getOverview(orgId));
    }

    @GetMapping("/sync/tasks")
    public R<PageResult<IntegrationSyncTaskVO>> pageSyncTasks(IntegrationSyncTaskQueryDTO query) {
        Page<IntegrationSyncTaskVO> page = integrationManagementService.pageSyncTasks(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/sync/tasks/{id}")
    public R<IntegrationSyncTaskVO> getSyncTask(@PathVariable Long id) {
        return R.ok(integrationManagementService.getSyncTask(id));
    }

    @PostMapping("/sync/tasks/trigger")
    public R<IntegrationExecutionResultVO> triggerSync(@Valid @RequestBody IntegrationSyncTaskTriggerDTO dto) {
        return R.ok(integrationManagementService.triggerSync(dto));
    }

    @PostMapping("/sync/tasks/{id}/retry")
    public R<IntegrationExecutionResultVO> retrySyncTask(@PathVariable Long id) {
        return R.ok(integrationManagementService.retrySyncTask(id));
    }

    @GetMapping("/sync/logs")
    public R<PageResult<IntegrationSyncLogVO>> pageSyncLogs(@Valid IntegrationSyncLogQueryDTO query) {
        Page<IntegrationSyncLogVO> page = integrationManagementService.pageSyncLogs(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/sync/logs/{id}")
    public R<IntegrationSyncLogVO> getSyncLog(@PathVariable Long id) {
        return R.ok(integrationManagementService.getSyncLog(id));
    }

    @GetMapping("/sync/logs/options/providers")
    public R<List<IntegrationSyncLogProviderOptionVO>> getSyncLogProviderOptions() {
        return R.ok(integrationManagementService.getSyncLogProviderOptions());
    }

    @PutMapping("/sync/logs/{id}/handle-status")
    public R<Void> updateSyncLogHandleStatus(@PathVariable Long id, @Valid @RequestBody IntegrationSyncLogHandleDTO dto) {
        integrationManagementService.updateSyncLogHandleStatus(id, dto);
        return R.ok();
    }

    @GetMapping("/callback/logs")
    public R<PageResult<IntegrationCallbackLogVO>> pageCallbackLogs(IntegrationCallbackLogQueryDTO query) {
        Page<IntegrationCallbackLogVO> page = integrationManagementService.pageCallbackLogs(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/callback/logs/{id}")
    public R<IntegrationCallbackLogVO> getCallbackLog(@PathVariable Long id) {
        return R.ok(integrationManagementService.getCallbackLog(id));
    }

    @GetMapping("/callback/logs/options/providers")
    public R<List<IntegrationSyncLogProviderOptionVO>> getCallbackLogProviderOptions() {
        return R.ok(integrationManagementService.getCallbackLogProviderOptions());
    }

    @GetMapping("/files")
    public R<PageResult<IntegrationFileRecordVO>> pageFileRecords(IntegrationFileRecordQueryDTO query) {
        Page<IntegrationFileRecordVO> page = integrationManagementService.pageFileRecords(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/files/{id}")
    public R<IntegrationFileRecordVO> getFileRecord(@PathVariable Long id) {
        return R.ok(integrationManagementService.getFileRecord(id));
    }

    @GetMapping("/files/options/providers")
    public R<List<IntegrationSyncLogProviderOptionVO>> getFileRecordProviderOptions() {
        return R.ok(integrationManagementService.getFileRecordProviderOptions());
    }

    @GetMapping("/health-checks")
    public R<PageResult<IntegrationHealthCheckVO>> pageHealthChecks(IntegrationHealthCheckQueryDTO query) {
        Page<IntegrationHealthCheckVO> page = integrationManagementService.pageHealthChecks(query);
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/health-checks/{configId}")
    public R<IntegrationHealthCheckVO> getHealthCheck(@PathVariable Long configId) {
        return R.ok(integrationManagementService.getHealthCheck(configId));
    }

    @GetMapping("/health-checks/options/providers")
    public R<List<IntegrationSyncLogProviderOptionVO>> getHealthCheckProviderOptions() {
        return R.ok(integrationManagementService.getHealthCheckProviderOptions());
    }

    @GetMapping("/internal/bindings/by-biz")
    public R<IntegrationInternalSceneMetaVO> getInternalSceneMeta(@Valid IntegrationInternalBizBindingQueryDTO query) {
        return R.ok(integrationManagementService.getInternalSceneMeta(query));
    }

    @PostMapping("/internal/bindings/trigger")
    public R<IntegrationInternalTriggerResultVO> triggerInternalSync(@Valid @RequestBody IntegrationInternalTriggerSyncDTO dto) {
        return R.ok(integrationManagementService.triggerInternalSync(dto));
    }

    @GetMapping("/internal/logs/by-binding")
    public R<IntegrationInternalLogsVO> getInternalLogs(@Valid IntegrationInternalBizBindingQueryDTO query) {
        return R.ok(integrationManagementService.getInternalLogs(query));
    }

    @PostMapping("/internal/bindings/switch-mode")
    public R<IntegrationInternalBindingSummaryVO> switchInternalBindingMode(@Valid @RequestBody IntegrationInternalSwitchModeDTO dto) {
        return R.ok(integrationManagementService.switchInternalBindingMode(dto));
    }

    @PostMapping("/callback/{providerCode}")
    public R<IntegrationCallbackHandleResultVO> handleCallback(@PathVariable String providerCode,
                                                               @RequestBody(required = false) String payload,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) {
        IntegrationCallbackHandleResultVO result = integrationManagementService.handleCallback(providerCode, payload, request);
        HttpStatus httpStatus = resolveCallbackHttpStatus(result.getProcessStatus());
        response.setStatus(httpStatus.value());
        return buildCallbackResponse(result, httpStatus);
    }

    private HttpStatus resolveCallbackHttpStatus(String processStatus) {
        if ("success".equals(processStatus) || "no_data".equals(processStatus) || "mapping_missing".equals(processStatus)) {
            return HttpStatus.OK;
        }
        if ("duplicate".equals(processStatus)) {
            return HttpStatus.CONFLICT;
        }
        if ("ignored".equals(processStatus)) {
            return HttpStatus.ACCEPTED;
        }
        if ("security_failed".equals(processStatus)) {
            return HttpStatus.FORBIDDEN;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private R<IntegrationCallbackHandleResultVO> buildCallbackResponse(IntegrationCallbackHandleResultVO result, HttpStatus httpStatus) {
        if (httpStatus == HttpStatus.OK) {
            return R.ok(result.getMessage(), result);
        }
        if (httpStatus == HttpStatus.FORBIDDEN) {
            return R.<IntegrationCallbackHandleResultVO>forbidden().message(result.getMessage()).data(result);
        }
        if (httpStatus == HttpStatus.ACCEPTED) {
            return R.ok(result.getMessage(), result);
        }
        if (httpStatus == HttpStatus.CONFLICT) {
            return R.<IntegrationCallbackHandleResultVO>conflict(result.getMessage()).data(result);
        }
        return R.<IntegrationCallbackHandleResultVO>fail(result.getMessage()).data(result);
    }
}
