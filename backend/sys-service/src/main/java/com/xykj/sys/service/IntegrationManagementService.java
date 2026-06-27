package com.xykj.sys.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

import java.util.List;

public interface IntegrationManagementService {

    Page<IntegrationProviderTemplateVO> pageProviders(IntegrationProviderTemplateQueryDTO query);

    IntegrationProviderTemplateVO getProvider(Long id);

    Long createProvider(IntegrationProviderTemplateSaveDTO dto);

    void updateProvider(Long id, IntegrationProviderTemplateSaveDTO dto);

    void deleteProvider(Long id);

    void changeProviderStatus(Long id, IntegrationProviderTemplateStatusDTO dto);

    Page<IntegrationModuleConfigVO> pageModuleConfigs(IntegrationModuleConfigQueryDTO query);

    IntegrationModuleConfigVO getModuleConfig(Long id);

    Long createModuleConfig(IntegrationModuleConfigSaveDTO dto);

    void updateModuleConfig(Long id, IntegrationModuleConfigSaveDTO dto);

    void deleteModuleConfig(Long id);

    void changeModuleConfigStatus(Long id, IntegrationModuleConfigStatusDTO dto);

    IntegrationHealthCheckVO testModuleConfig(Long id);

    Page<IntegrationFieldMappingVO> pageFieldMappings(IntegrationFieldMappingQueryDTO query);

    IntegrationFieldMappingVO getFieldMapping(Long id);

    void checkFieldMappingDuplicate(IntegrationFieldMappingDuplicateCheckDTO dto);

    Long createFieldMapping(IntegrationFieldMappingSaveDTO dto);

    void updateFieldMapping(Long id, IntegrationFieldMappingSaveDTO dto);

    void deleteFieldMapping(Long id);

    Page<IntegrationStatusMappingVO> pageStatusMappings(IntegrationStatusMappingQueryDTO query);

    IntegrationStatusMappingVO getStatusMapping(Long id);

    void checkStatusMappingDuplicate(IntegrationStatusMappingDuplicateCheckDTO dto);

    Long createStatusMapping(IntegrationStatusMappingSaveDTO dto);

    void updateStatusMapping(Long id, IntegrationStatusMappingSaveDTO dto);

    void deleteStatusMapping(Long id);

    IntegrationOverviewVO getOverview(Long orgId);

    Page<IntegrationSyncTaskVO> pageSyncTasks(IntegrationSyncTaskQueryDTO query);

    IntegrationSyncTaskVO getSyncTask(Long id);

    IntegrationExecutionResultVO triggerSync(IntegrationSyncTaskTriggerDTO dto);

    IntegrationExecutionResultVO retrySyncTask(Long id);

    Page<IntegrationSyncLogVO> pageSyncLogs(IntegrationSyncLogQueryDTO query);

    IntegrationSyncLogVO getSyncLog(Long id);

    List<IntegrationSyncLogProviderOptionVO> getSyncLogProviderOptions();

    void updateSyncLogHandleStatus(Long id, IntegrationSyncLogHandleDTO dto);

    Page<IntegrationCallbackLogVO> pageCallbackLogs(IntegrationCallbackLogQueryDTO query);

    IntegrationCallbackLogVO getCallbackLog(Long id);

    List<IntegrationSyncLogProviderOptionVO> getCallbackLogProviderOptions();

    Page<IntegrationFileRecordVO> pageFileRecords(IntegrationFileRecordQueryDTO query);

    IntegrationFileRecordVO getFileRecord(Long id);

    List<IntegrationSyncLogProviderOptionVO> getFileRecordProviderOptions();

    Page<IntegrationHealthCheckVO> pageHealthChecks(IntegrationHealthCheckQueryDTO query);

    IntegrationHealthCheckVO getHealthCheck(Long configId);

    List<IntegrationSyncLogProviderOptionVO> getHealthCheckProviderOptions();

    IntegrationInternalSceneMetaVO getInternalSceneMeta(IntegrationInternalBizBindingQueryDTO dto);

    IntegrationInternalTriggerResultVO triggerInternalSync(IntegrationInternalTriggerSyncDTO dto);

    IntegrationInternalLogsVO getInternalLogs(IntegrationInternalBizBindingQueryDTO dto);

    IntegrationInternalBindingSummaryVO switchInternalBindingMode(IntegrationInternalSwitchModeDTO dto);

    IntegrationCallbackHandleResultVO handleCallback(String providerCode, String payload, HttpServletRequest request);
}
