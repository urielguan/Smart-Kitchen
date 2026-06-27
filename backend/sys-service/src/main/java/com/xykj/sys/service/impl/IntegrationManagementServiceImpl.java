package com.xykj.sys.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.ai.service.AiConfigCryptoService;
import com.xykj.common.context.UserContext;
import com.xykj.common.enums.AuditModule;
import com.xykj.common.enums.AuditOperationType;
import com.xykj.common.exception.BizException;
import com.xykj.common.service.AuditLogService;
import com.xykj.common.service.DataScopeService;
import com.xykj.common.service.FileStorageService;
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
import com.xykj.sys.dto.IntegrationSecretInputDTO;
import com.xykj.sys.dto.IntegrationStatusMappingDuplicateCheckDTO;
import com.xykj.sys.dto.IntegrationStatusMappingQueryDTO;
import com.xykj.sys.dto.IntegrationStatusMappingSaveDTO;
import com.xykj.sys.dto.IntegrationSyncLogHandleDTO;
import com.xykj.sys.dto.IntegrationSyncLogQueryDTO;
import com.xykj.sys.dto.IntegrationSyncTaskQueryDTO;
import com.xykj.sys.dto.IntegrationSyncTaskTriggerDTO;
import com.xykj.sys.entity.IntegrationBinding;
import com.xykj.sys.entity.IntegrationCallbackLog;
import com.xykj.sys.entity.IntegrationFieldMapping;
import com.xykj.sys.entity.IntegrationFileRecord;
import com.xykj.sys.entity.IntegrationHealthCheckLog;
import com.xykj.sys.entity.IntegrationModuleConfig;
import com.xykj.sys.entity.IntegrationProviderTemplate;
import com.xykj.sys.entity.IntegrationSecretConfig;
import com.xykj.sys.entity.IntegrationStatusMapping;
import com.xykj.sys.entity.IntegrationSyncLog;
import com.xykj.sys.entity.IntegrationSyncTask;
import com.xykj.sys.mapper.IntegrationBindingMapper;
import com.xykj.sys.mapper.IntegrationCallbackLogMapper;
import com.xykj.sys.mapper.IntegrationFieldMappingMapper;
import com.xykj.sys.mapper.IntegrationFileRecordMapper;
import com.xykj.sys.mapper.IntegrationHealthCheckLogMapper;
import com.xykj.sys.mapper.IntegrationModuleConfigMapper;
import com.xykj.sys.mapper.IntegrationProviderTemplateMapper;
import com.xykj.sys.mapper.IntegrationSecretConfigMapper;
import com.xykj.sys.mapper.IntegrationStatusMappingMapper;
import com.xykj.sys.mapper.IntegrationSyncLogMapper;
import com.xykj.sys.mapper.IntegrationSyncTaskMapper;
import com.xykj.sys.service.IntegrationManagementService;
import com.xykj.sys.util.SimpleNamedMultipartFile;
import com.xykj.sys.vo.IntegrationCallbackHandleResultVO;
import com.xykj.sys.vo.IntegrationCallbackLogVO;
import com.xykj.sys.vo.IntegrationExecutionResultVO;
import com.xykj.sys.vo.IntegrationFieldMappingVO;
import com.xykj.sys.vo.IntegrationFileRecordVO;
import com.xykj.sys.vo.IntegrationHealthCheckLogVO;
import com.xykj.sys.vo.IntegrationHealthCheckVO;
import com.xykj.sys.vo.IntegrationInternalBindingSummaryVO;
import com.xykj.sys.vo.IntegrationInternalLogBriefVO;
import com.xykj.sys.vo.IntegrationInternalLogsVO;
import com.xykj.sys.vo.IntegrationInternalModuleOptionVO;
import com.xykj.sys.vo.IntegrationInternalSceneMetaVO;
import com.xykj.sys.vo.IntegrationInternalTriggerResultVO;
import com.xykj.sys.vo.IntegrationModuleConfigVO;
import com.xykj.sys.vo.IntegrationOverviewVO;
import com.xykj.sys.vo.IntegrationProviderTemplateVO;
import com.xykj.sys.vo.IntegrationSecretMaskedVO;
import com.xykj.sys.vo.IntegrationStatusMappingVO;
import com.xykj.sys.vo.IntegrationSyncLogProviderOptionVO;
import com.xykj.sys.vo.IntegrationSyncLogVO;
import com.xykj.sys.vo.IntegrationSyncTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntegrationManagementServiceImpl implements IntegrationManagementService {

    private static final DateTimeFormatter STANDARD_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String MODE_MANUAL = "manual";
    private static final String MODE_THIRD_PARTY = "third_party";
    private static final String TRIGGER_STRATEGY_SCHEDULER = "scheduler";
    private static final String TRIGGER_STRATEGY_CALLBACK = "callback";
    private static final String TASK_TYPE_SYNC = "sync";
    private static final String TASK_TYPE_QUERY_ONLY = "query_only";
    private static final String TASK_TYPE_RETRY = "retry";
    private static final String TASK_TYPE_CALLBACK_FOLLOWUP = "callback_followup";
    private static final String TRIGGER_TYPE_MANUAL = "manual";
    private static final String TRIGGER_TYPE_SCHEDULER = "scheduler";
    private static final String TRIGGER_TYPE_CALLBACK = "callback";
    private static final String TASK_STATUS_PENDING = "pending";
    private static final String TASK_STATUS_RUNNING = "running";
    private static final String TASK_STATUS_SUCCESS = "success";
    private static final String TASK_STATUS_FAILED = "failed";
    private static final String TASK_STATUS_NO_DATA = "no_data";
    private static final String TASK_STATUS_MAPPING_MISSING = "mapping_missing";
    private static final String LOG_TYPE_SYNC = "sync";
    private static final String LOG_TYPE_CALLBACK = "callback";
    private static final String ATTACHMENT_TYPE_LOGISTICS = "logistics";
    private static final String ATTACHMENT_TYPE_INSPECTION = "inspection";
    private static final String ATTACHMENT_TYPE_TRACEABILITY = "traceability";
    private static final String BIZ_MODULE_PURCHASE_ORDER = "purchase_order";
    private static final String BIZ_MODULE_SAMPLE_RETENTION = "sample_retention";
    private static final String BIZ_MODULE_MORNING_CHECK = "morning_check";
    private static final String BIZ_SCENE_LOGISTICS = "logistics";
    private static final String BIZ_SCENE_INSPECTION = "inspection";
    private static final String BIZ_SCENE_TRACEABILITY = "traceability";
    private static final String BIZ_SCENE_SAMPLE_TASK = "sample_task";
    private static final String BIZ_SCENE_SAMPLE_RECORD = "sample_record";
    private static final String BIZ_SCENE_SAMPLE_RESULT = "sample_result";
    private static final String BIZ_SCENE_SAMPLE_DEVICE = "sample_device";
    private static final String BIZ_SCENE_FACE_PROFILE = "face_profile";
    private static final String BIZ_SCENE_HEALTH_CERTIFICATE = "health_certificate";
    private static final String BIZ_SCENE_MORNING_CHECK_RESULT = "morning_check_result";
    private static final String BIZ_SCENE_DEVICE_MASTER = "device_master";
    private static final String ORDER_STATUS_APPROVED = "approved";
    private static final String ORDER_STATUS_DELIVERING = "delivering";
    private static final String ORDER_STATUS_PENDING_RECEIPT = "pending_receipt";
    private static final String ORDER_STATUS_COMPLETED = "completed";
    private static final String LOGISTICS_STATUS_SHIPPED = "shipped";
    private static final String LOGISTICS_STATUS_IN_TRANSIT = "in_transit";
    private static final String LOGISTICS_STATUS_ARRIVED = "arrived";
    private static final String INSPECTION_RESULT_QUALIFIED = "合格";
    private static final String INSPECTION_RESULT_UNQUALIFIED = "不合格";
    private static final String SAMPLE_STATUS_PENDING_SAMPLE = "pending_sample";
    private static final String SAMPLE_STATUS_SAMPLED = "sampled";
    private static final String SAMPLE_STATUS_EVALUATED = "evaluated";
    private static final String SAMPLE_STATUS_PENDING_DISPOSAL = "pending_disposal";
    private static final String SAMPLE_STATUS_DISPOSED = "disposed";
    private static final String SAMPLE_STATUS_OVERDUE = "overdue";
    private static final String SAMPLE_STATUS_VOIDED = "voided";
    private static final String SAMPLE_STATUS_ARCHIVED = "archived";
    private static final String HEALTH_CERT_STATUS_PENDING = "pending";
    private static final String HEALTH_CERT_STATUS_VALID = "valid";
    private static final String HEALTH_CERT_STATUS_EXPIRING = "expiring";
    private static final String HEALTH_CERT_STATUS_EXPIRED = "expired";
    private static final String HEALTH_CHECK_RESULT_PASS = "pass";
    private static final String HEALTH_CHECK_RESULT_FAIL = "fail";
    private static final String HEALTH_CHECK_STATUS_PENDING = "pending_check";
    private static final String HEALTH_CHECK_STATUS_COMPLETED_NORMAL = "completed_normal";
    private static final String HEALTH_CHECK_STATUS_COMPLETED_ABNORMAL = "completed_abnormal";
    private static final String DEVICE_TYPE_SAMPLE_TERMINAL = "sample_terminal";
    private static final String DEVICE_TYPE_HEALTH_TERMINAL = "health_terminal";
    private static final String REQUEST_TEMPLATE_JSON_ERROR = "请求模板JSON格式不正确，请检查括号、逗号、引号是否规范";
    private static final String RESPONSE_TEMPLATE_JSON_ERROR = "响应模板JSON格式不正确，请检查括号、逗号、引号是否规范";
    private static final String TEMPLATE_VARIABLE_ERROR = "模板内存在不合法动态变量表达式，请修正${xxx}格式";
    private static final String PROVIDER_CODE_FORMAT_ERROR = "平台编码仅支持小写字母、数字、下划线，且必须以字母开头";
    private static final String REQUEST_TEMPLATE_SEMANTIC_ERROR = "请求模板缺少可执行URL；如果该平台仅支持回调接入，请直接留空请求模板，不要保存空URL模板";
    private static final String REQUEST_TEMPLATE_METHOD_ERROR = "请求模板中的请求方法仅支持 GET、POST、PUT、PATCH、DELETE";
    private static final String REQUEST_TEMPLATE_URL_ERROR = "请求模板中的URL必须是 http/https 地址，或以 ${变量} 开头的地址模板";
    private static final String REQUEST_TEMPLATE_STRUCTURE_ERROR = "请求模板中的 headers、query 必须是 JSON 对象";
    private static final String PROVIDER_RUNTIME_DISABLED_ERROR = "当前第三方平台模板已停用，不能执行连接测试或同步";
    private static final String PROVIDER_RUNTIME_URL_MISSING_ERROR = "当前平台模板未配置可执行请求地址，不能执行真实连接测试或同步";
    private static final String TOKEN_RESPONSE_EMPTY_ERROR = "获取 AccessToken 失败：第三方未返回有效 token";
    private static final String CALLBACK_SIGNATURE_MISSING_ERROR = "缺少回调签名";
    private static final String CALLBACK_SIGNATURE_SECRET_MISSING_ERROR = "未配置回调签名密钥";
    private static final String CALLBACK_TIMESTAMP_MISSING_ERROR = "缺少回调时间戳";
    private static final String CALLBACK_TIMESTAMP_INVALID_ERROR = "回调时间戳已过期或格式不正确";
    private static final String CALLBACK_IP_WHITELIST_MISSING_ERROR = "未配置回调IP白名单";
    private static final String CALLBACK_IP_DENIED_ERROR = "回调来源IP不在白名单内";
    private static final String NO_DATA_MESSAGE = "第三方返回空数据，未找到可同步内容";
    private static final String MAPPING_MISSING_MESSAGE_PREFIX = "第三方返回状态值缺少映射：";
    private static final String EXECUTE_ERROR_CODE = "EXECUTE_ERROR";
    private static final String NO_DATA_ERROR_CODE = "NO_DATA";
    private static final String MAPPING_MISSING_ERROR_CODE = "MAPPING_MISSING";
    private static final String FIELD_MAPPING_ERROR_CODE = "FIELD_MAPPING_ERROR";
    private static final String BUILTIN_PROVIDER_STATUS_STOP_MESSAGE = "平台模板已停用，历史绑定已停止自动同步";
    private static final String PERMISSION_INTEGRATION_VIEW = "integration-management";
    private static final String PERMISSION_INTEGRATION_CREATE = "integration-management:create";
    private static final String PERMISSION_INTEGRATION_EDIT = "integration-management:edit";
    private static final String PERMISSION_INTEGRATION_DELETE = "integration-management:delete";
    private static final String PERMISSION_INTEGRATION_STATUS = "integration-management:status";
    private static final String PERMISSION_INTEGRATION_TEST = "integration-management:test";
    private static final String PERMISSION_INTEGRATION_SYNC = "integration-management:sync";
    private static final String PERMISSION_INTEGRATION_RETRY = "integration-management:retry";
    private static final String PERMISSION_INTEGRATION_LOG_VIEW = "integration-management:view-log";
    private static final String PERMISSION_INTEGRATION_CALLBACK_VIEW = "integration-management:view-callback";
    private static final String LOG_HANDLE_STATUS_PENDING_REVIEW = "pending_review";
    private static final String LOG_HANDLE_STATUS_CONFIRMED = "confirmed";
    private static final String LOG_HANDLE_STATUS_IGNORED = "ignored";
    private static final String LOG_HANDLE_STATUS_RECHECKED = "rechecked";
    private static final String SYNC_LOG_ERROR_CODE_AUTH = "AUTH_ERROR";
    private static final String SYNC_LOG_ERROR_CODE_RATE_LIMIT = "RATE_LIMITED";
    private static final String SYNC_LOG_ERROR_CODE_TIMEOUT = "TIMEOUT";
    private static final String SYNC_LOG_ERROR_CODE_NETWORK = "NETWORK_ERROR";
    private static final String SYNC_LOG_ERROR_CODE_HTTP_4XX = "HTTP_4XX";
    private static final String SYNC_LOG_ERROR_CODE_HTTP_5XX = "HTTP_5XX";
    private static final String SYNC_LOG_ERROR_CODE_TOKEN = "TOKEN_ERROR";
    private static final String SYNC_LOG_ERROR_CODE_CIRCUIT_OPEN = "CIRCUIT_OPEN";
    private static final String SYNC_LOG_ERROR_CODE_PROVIDER_DISABLED = "PROVIDER_DISABLED";
    private static final String SYNC_LOG_ERROR_CODE_RUNTIME_URL_MISSING = "RUNTIME_URL_MISSING";
    private static final String FILE_RECORD_STATUS_PENDING = "pending";
    private static final String FILE_RECORD_STATUS_SUCCESS = "success";
    private static final String FILE_RECORD_STATUS_FAILED = "failed";
    private static final String FILE_RECORD_STATUS_SKIPPED = "skipped";
    private static final String FILE_RECORD_STATUS_REUSED = "reused";
    private static final String FILE_RECORD_ERROR_PROVIDER_FILE_PULL_DISABLED = "PROVIDER_FILE_PULL_DISABLED";
    private static final String FILE_RECORD_ERROR_ATTACHMENTS_FIELD_MISSING = "ATTACHMENTS_FIELD_MISSING";
    private static final String FILE_RECORD_ERROR_ATTACHMENTS_EMPTY = "ATTACHMENTS_EMPTY";
    private static final String FILE_RECORD_ERROR_ATTACHMENTS_INVALID = "ATTACHMENTS_INVALID";
    private static final String FILE_RECORD_ERROR_URL_MISSING = "ATTACHMENT_URL_MISSING";
    private static final String FILE_RECORD_ERROR_FILE_EMPTY = "FILE_EMPTY";
    private static final String FILE_RECORD_ERROR_FILE_SIZE_EXCEEDED = "FILE_SIZE_EXCEEDED";
    private static final String FILE_RECORD_ERROR_FILE_TYPE_BLOCKED = "FILE_TYPE_BLOCKED";
    private static final String FILE_RECORD_ERROR_DOWNLOAD = "FILE_DOWNLOAD_ERROR";
    private static final String FILE_RECORD_ERROR_STORAGE = "FILE_STORAGE_ERROR";
    private static final String FILE_RECORD_ERROR_TRANSFER_TIMEOUT = "FILE_TRANSFER_TIMEOUT";
    private static final String HEALTH_CHECK_STATUS_WARNING = "warning";
    private static final String HEALTH_CHECK_STATUS_UNKNOWN = "unknown";
    private static final String HEALTH_CHECK_ERROR_INVALID_RESPONSE = "INVALID_RESPONSE";
    private static final String HEALTH_CHECK_ERROR_CALLBACK = "CALLBACK_UNREACHABLE";
    private static final String HEALTH_CHECK_ERROR_PLACEHOLDER = "TEST_PLACEHOLDER_REJECTED";
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{[^{}]*}");
    private static final Pattern LEGAL_TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*}");
    private static final Pattern PROVIDER_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final Pattern TEMPLATE_URL_PATTERN = Pattern.compile("^(https?://.+|\\$\\{[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*}.*)$");
    private static final Pattern ACTION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");
    private static final int AUTO_SORT_STEP = 10;
    private static final int AUTO_SORT_FALLBACK_BASE = 1000;
    private static final int CALLBACK_DEFAULT_TOLERANCE_SECONDS = 300;
    private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_BREAKER_OPEN_MILLIS = 120_000L;
    private static final long RATE_LIMIT_CIRCUIT_OPEN_MILLIS = 180_000L;
    private static final long RETRY_BASE_DELAY_MILLIS = 500L;
    private static final int MAX_TOTAL_RETRY_ATTEMPTS = 3;
    private static final int SCHEDULE_CLAIM_LEASE_SECONDS = 180;
    private static final int STUCK_TASK_TIMEOUT_MINUTES = 15;
    private static final int STUCK_CALLBACK_LOG_TIMEOUT_MINUTES = 15;
    private static final int STUCK_FILE_RECORD_TIMEOUT_MINUTES = 15;
    private static final int FILE_TRANSFER_MAX_ATTEMPTS = 2;
    private static final long FILE_TRANSFER_RETRY_DELAY_MILLIS = 300L;
    private static final long MAX_INTEGRATION_FILE_SIZE = 50L * 1024 * 1024;
    private static final int HEALTH_CHECK_PAGE_SIZE_MAX = 200;
    private static final int HEALTH_CHECK_PROVIDER_OPTION_LIMIT = 500;
    private static final int HEALTH_CHECK_LOG_DETAIL_LIMIT = 3;
    private static final String TASK_TIMEOUT_ERROR_CODE = "TASK_TIMEOUT";
    private static final Set<String> BUILTIN_PROVIDER_CODES = Set.of(
            "sf_express", "jd_logistics", "inspection_agency_a", "traceability_platform_b",
            "sample_device_platform", "face_check_device_platform"
    );
    private static final Set<String> ALLOWED_FILE_RECORD_STATUSES = Set.of(
            FILE_RECORD_STATUS_PENDING,
            FILE_RECORD_STATUS_SUCCESS,
            FILE_RECORD_STATUS_FAILED,
            FILE_RECORD_STATUS_SKIPPED,
            FILE_RECORD_STATUS_REUSED
    );
    private static final Set<String> BLOCKED_INTEGRATION_FILE_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "dll", "msi", "sh", "bash", "zsh", "ps1", "vbs", "jar", "js"
    );
    private static final Set<String> ALLOWED_PROVIDER_TYPES = Set.of("logistics", "inspection", "traceability", "device");
    private static final Set<String> ALLOWED_AUTH_TYPES = Set.of("bearer", "app_secret", "oauth2");
    private static final Set<String> ALLOWED_PROTOCOL_TYPES = Set.of("http", "https");
    private static final Set<String> ALLOWED_PROVIDER_STATUSES = Set.of("active", "inactive");
    private static final Set<String> ALLOWED_DEFAULT_MODES = Set.of(MODE_MANUAL, MODE_THIRD_PARTY);
    private static final Set<String> ALLOWED_TRIGGER_STRATEGIES = Set.of(MODE_MANUAL, TRIGGER_STRATEGY_SCHEDULER, TRIGGER_STRATEGY_CALLBACK);
    private static final Set<String> ALLOWED_TASK_TRIGGER_TYPES = Set.of(TRIGGER_TYPE_MANUAL);
    private static final Set<String> ALLOWED_TASK_MODE_SOURCES = Set.of("user_selected", "business_scene");
    private static final Set<String> ALLOWED_AUTO_COVER_STRATEGIES = Set.of("merge", "overwrite");
    private static final Set<String> ALLOWED_TOKEN_REQUEST_METHODS = Set.of("GET", "POST");
    private static final Set<String> ALLOWED_TEMPLATE_HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> ALLOWED_FIELD_TRANSFORM_TYPES = Set.of("direct", "dict", "date", "number", "json_path");
    private static final Set<String> ALLOWED_FIELD_ERROR_STRATEGIES = Set.of("fail", "skip", "log_only", "manual_review");
    private static final Set<String> ALLOWED_SYNC_LOG_HANDLE_STATUSES = Set.of(
            LOG_HANDLE_STATUS_PENDING_REVIEW, LOG_HANDLE_STATUS_CONFIRMED, LOG_HANDLE_STATUS_IGNORED, LOG_HANDLE_STATUS_RECHECKED
    );
    private static final Set<String> SENSITIVE_LOG_FIELD_KEYS = Set.of(
            "authorization", "access_token", "accesstoken", "refresh_token", "refreshtoken", "token",
            "clientsecret", "appsecret", "secret", "signsecret", "signaturesecret", "privatekey", "appkey",
            "signature", "sign"
    );
    private static final Set<String> TASK_PENDING_HANDLE_STATUSES = Set.of(TASK_STATUS_FAILED, TASK_STATUS_NO_DATA, TASK_STATUS_MAPPING_MISSING);
    private static final String FIELD_TYPE_STRING = "string";
    private static final String FIELD_TYPE_DATE = "date";
    private static final String FIELD_TYPE_DATETIME = "datetime";
    private static final String FIELD_TYPE_NUMBER = "number";
    private static final String FIELD_TYPE_LONG = "long";
    private static final String FIELD_TYPE_ARRAY = "array";
    private static final Set<String> ALLOWED_LOGISTICS_STATUS_VALUES = Set.of("pending", LOGISTICS_STATUS_SHIPPED, LOGISTICS_STATUS_IN_TRANSIT, LOGISTICS_STATUS_ARRIVED);
    private static final Set<String> ALLOWED_INSPECTION_RESULT_VALUES = Set.of("待出结果", INSPECTION_RESULT_QUALIFIED, INSPECTION_RESULT_UNQUALIFIED);
    private static final Set<String> ALLOWED_DEVICE_ONLINE_STATUS_VALUES = Set.of("online", "offline", "fault");
    private static final Set<String> ALLOWED_SUPPLIER_STATUS_VALUES = Set.of("pending", "active", "rejected", "disabled", "cancelled");
    private static final Set<String> ALLOWED_INBOUND_STATUS_VALUES = Set.of("draft", "pending", "approved", "completed", "rejected", "cancelled");
    private static final Set<String> ALLOWED_SCENE_CODES = Set.of(
            sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_LOGISTICS),
            sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_INSPECTION),
            sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_TRACEABILITY),
            sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_TASK),
            sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RECORD),
            sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RESULT),
            sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_DEVICE),
            sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_FACE_PROFILE),
            sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_HEALTH_CERTIFICATE),
            sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_MORNING_CHECK_RESULT),
            sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_DEVICE_MASTER),
            "supplier:qualification_verify",
            "inbound:arrival_receipt"
    );
    private static final Map<String, Map<String, StandardFieldMeta>> STANDARD_FIELD_META_MAP = Map.ofEntries(
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_LOGISTICS),
                    buildFieldMetaMap(
                            fieldMeta("trackingNo", FIELD_TYPE_STRING),
                            fieldMeta("company", FIELD_TYPE_STRING),
                            fieldMeta("status", FIELD_TYPE_STRING, ALLOWED_LOGISTICS_STATUS_VALUES),
                            fieldMeta("shippedAt", FIELD_TYPE_DATETIME),
                            fieldMeta("arrivedAt", FIELD_TYPE_DATETIME),
                            fieldMeta("remark", FIELD_TYPE_STRING),
                            fieldMeta("attachments", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_INSPECTION),
                    buildFieldMetaMap(
                            fieldMeta("reportNo", FIELD_TYPE_STRING),
                            fieldMeta("result", FIELD_TYPE_STRING, ALLOWED_INSPECTION_RESULT_VALUES),
                            fieldMeta("agency", FIELD_TYPE_STRING),
                            fieldMeta("inspectionAt", FIELD_TYPE_DATETIME),
                            fieldMeta("remark", FIELD_TYPE_STRING),
                            fieldMeta("attachments", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_TRACEABILITY),
                    buildFieldMetaMap(
                            fieldMeta("batchId", FIELD_TYPE_STRING),
                            fieldMeta("origin", FIELD_TYPE_STRING),
                            fieldMeta("remark", FIELD_TYPE_STRING),
                            fieldMeta("attachments", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_TASK),
                    buildFieldMetaMap(
                            fieldMeta("sampleTaskNo", FIELD_TYPE_STRING),
                            fieldMeta("cookingTaskId", FIELD_TYPE_LONG),
                            fieldMeta("recipeName", FIELD_TYPE_STRING),
                            fieldMeta("dishName", FIELD_TYPE_STRING),
                            fieldMeta("requiredSampleWeight", FIELD_TYPE_NUMBER),
                            fieldMeta("plannedSampleTime", FIELD_TYPE_DATETIME),
                            fieldMeta("kitchenAreaId", FIELD_TYPE_LONG)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RECORD),
                    buildFieldMetaMap(
                            fieldMeta("sampleRecordNo", FIELD_TYPE_STRING),
                            fieldMeta("sampleTaskNo", FIELD_TYPE_STRING),
                            fieldMeta("sampleWeight", FIELD_TYPE_NUMBER),
                            fieldMeta("samplerId", FIELD_TYPE_LONG),
                            fieldMeta("sampledAt", FIELD_TYPE_DATETIME),
                            fieldMeta("storageLocation", FIELD_TYPE_STRING),
                            fieldMeta("evidenceFiles", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RESULT),
                    buildFieldMetaMap(
                            fieldMeta("qualityScore", FIELD_TYPE_NUMBER),
                            fieldMeta("complianceStatus", FIELD_TYPE_STRING),
                            fieldMeta("anomalyTags", FIELD_TYPE_ARRAY),
                            fieldMeta("inspectionReportNo", FIELD_TYPE_STRING),
                            fieldMeta("destroyAt", FIELD_TYPE_DATETIME),
                            fieldMeta("destroyEvidenceFiles", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_DEVICE),
                    buildFieldMetaMap(
                            fieldMeta("deviceCode", FIELD_TYPE_STRING),
                            fieldMeta("deviceName", FIELD_TYPE_STRING),
                            fieldMeta("manufacturer", FIELD_TYPE_STRING),
                            fieldMeta("model", FIELD_TYPE_STRING),
                            fieldMeta("onlineStatus", FIELD_TYPE_STRING, ALLOWED_DEVICE_ONLINE_STATUS_VALUES),
                            fieldMeta("storageTemperature", FIELD_TYPE_NUMBER),
                            fieldMeta("weightPrecision", FIELD_TYPE_NUMBER)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_FACE_PROFILE),
                    buildFieldMetaMap(
                            fieldMeta("employeeCode", FIELD_TYPE_STRING),
                            fieldMeta("employeeId", FIELD_TYPE_LONG),
                            fieldMeta("employeeName", FIELD_TYPE_STRING),
                            fieldMeta("externalFaceId", FIELD_TYPE_STRING),
                            fieldMeta("faceEnrollStatus", FIELD_TYPE_STRING),
                            fieldMeta("faceImageUrl", FIELD_TYPE_STRING)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_HEALTH_CERTIFICATE),
                    buildFieldMetaMap(
                            fieldMeta("employeeId", FIELD_TYPE_LONG),
                            fieldMeta("employeeCode", FIELD_TYPE_STRING),
                            fieldMeta("employeeName", FIELD_TYPE_STRING),
                            fieldMeta("certificateNo", FIELD_TYPE_STRING),
                            fieldMeta("issueDate", FIELD_TYPE_DATE),
                            fieldMeta("expireDate", FIELD_TYPE_DATE),
                            fieldMeta("certificateStatus", FIELD_TYPE_STRING, Set.of(HEALTH_CERT_STATUS_PENDING, HEALTH_CERT_STATUS_VALID, HEALTH_CERT_STATUS_EXPIRING, HEALTH_CERT_STATUS_EXPIRED)),
                            fieldMeta("certificateImages", FIELD_TYPE_ARRAY)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_MORNING_CHECK_RESULT),
                    buildFieldMetaMap(
                            fieldMeta("checkNo", FIELD_TYPE_STRING),
                            fieldMeta("externalCheckId", FIELD_TYPE_STRING),
                            fieldMeta("employeeId", FIELD_TYPE_LONG),
                            fieldMeta("employeeCode", FIELD_TYPE_STRING),
                            fieldMeta("deviceCode", FIELD_TYPE_STRING),
                            fieldMeta("faceCheckResult", FIELD_TYPE_STRING),
                            fieldMeta("faceMatchScore", FIELD_TYPE_NUMBER),
                            fieldMeta("temperature", FIELD_TYPE_NUMBER),
                            fieldMeta("temperatureStatus", FIELD_TYPE_STRING),
                            fieldMeta("certificateCheckResult", FIELD_TYPE_STRING),
                            fieldMeta("certificateCheckMessage", FIELD_TYPE_STRING),
                            fieldMeta("handCheckResult", FIELD_TYPE_STRING),
                            fieldMeta("handCheckMessage", FIELD_TYPE_STRING),
                            fieldMeta("checkResult", FIELD_TYPE_STRING, Set.of(HEALTH_CHECK_RESULT_PASS, HEALTH_CHECK_RESULT_FAIL)),
                            fieldMeta("evidenceImageUrl", FIELD_TYPE_STRING),
                            fieldMeta("checkTime", FIELD_TYPE_DATETIME)
                    )),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_DEVICE_MASTER),
                    buildFieldMetaMap(
                            fieldMeta("deviceCode", FIELD_TYPE_STRING),
                            fieldMeta("uuid", FIELD_TYPE_STRING),
                            fieldMeta("deviceName", FIELD_TYPE_STRING),
                            fieldMeta("manufacturer", FIELD_TYPE_STRING),
                            fieldMeta("model", FIELD_TYPE_STRING),
                            fieldMeta("onlineStatus", FIELD_TYPE_STRING, ALLOWED_DEVICE_ONLINE_STATUS_VALUES),
                            fieldMeta("installLocation", FIELD_TYPE_STRING),
                            fieldMeta("faceThreshold", FIELD_TYPE_NUMBER),
                            fieldMeta("temperatureThreshold", FIELD_TYPE_NUMBER)
                    )),
            Map.entry("supplier:qualification_verify",
                    buildFieldMetaMap(
                            fieldMeta("supplierCode", FIELD_TYPE_STRING),
                            fieldMeta("supplierName", FIELD_TYPE_STRING),
                            fieldMeta("unifiedCreditCode", FIELD_TYPE_STRING),
                            fieldMeta("licenseNo", FIELD_TYPE_STRING),
                            fieldMeta("licenseExpireAt", FIELD_TYPE_DATETIME),
                            fieldMeta("foodLicenseNo", FIELD_TYPE_STRING),
                            fieldMeta("foodLicenseExpireAt", FIELD_TYPE_DATETIME),
                            fieldMeta("qualificationFiles", FIELD_TYPE_ARRAY),
                            fieldMeta("qualificationStatus", FIELD_TYPE_STRING, ALLOWED_SUPPLIER_STATUS_VALUES)
                    )),
            Map.entry("inbound:arrival_receipt",
                    buildFieldMetaMap(
                            fieldMeta("inboundNo", FIELD_TYPE_STRING),
                            fieldMeta("sourceOrderNo", FIELD_TYPE_STRING),
                            fieldMeta("supplierName", FIELD_TYPE_STRING),
                            fieldMeta("warehouseCode", FIELD_TYPE_STRING),
                            fieldMeta("warehouseName", FIELD_TYPE_STRING),
                            fieldMeta("receiptStatus", FIELD_TYPE_STRING, ALLOWED_INBOUND_STATUS_VALUES),
                            fieldMeta("approvedAt", FIELD_TYPE_DATETIME),
                            fieldMeta("attachments", FIELD_TYPE_ARRAY),
                            fieldMeta("remark", FIELD_TYPE_STRING)
                    ))
    );
    private static final Map<String, Map<String, Integer>> BUILTIN_FIELD_SORT_MAP = Map.ofEntries(
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_LOGISTICS),
                    buildSortOrderMap("trackingNo", "company", "status", "shippedAt", "arrivedAt", "remark", "attachments")),
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_INSPECTION),
                    buildSortOrderMap("reportNo", "result", "agency", "inspectionAt", "remark", "attachments")),
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_TRACEABILITY),
                    buildSortOrderMap("batchId", "origin", "remark", "attachments")),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_TASK),
                    buildSortOrderMap("sampleTaskNo", "cookingTaskId", "recipeName", "dishName", "requiredSampleWeight", "plannedSampleTime", "kitchenAreaId")),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RECORD),
                    buildSortOrderMap("sampleRecordNo", "sampleTaskNo", "sampleWeight", "samplerId", "sampledAt", "storageLocation", "evidenceFiles")),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RESULT),
                    buildSortOrderMap("qualityScore", "complianceStatus", "anomalyTags", "inspectionReportNo", "destroyAt", "destroyEvidenceFiles")),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_DEVICE),
                    buildSortOrderMap("deviceCode", "deviceName", "manufacturer", "model", "onlineStatus", "storageTemperature", "weightPrecision")),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_FACE_PROFILE),
                    buildSortOrderMap("employeeCode", "employeeId", "employeeName", "externalFaceId", "faceEnrollStatus", "faceImageUrl")),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_HEALTH_CERTIFICATE),
                    buildSortOrderMap("employeeId", "employeeCode", "employeeName", "certificateNo", "issueDate", "expireDate", "certificateStatus", "certificateImages")),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_MORNING_CHECK_RESULT),
                    buildSortOrderMap("checkNo", "externalCheckId", "employeeId", "employeeCode", "deviceCode", "faceCheckResult", "faceMatchScore",
                            "temperature", "temperatureStatus", "certificateCheckResult", "certificateCheckMessage", "handCheckResult",
                            "handCheckMessage", "checkResult", "evidenceImageUrl", "checkTime")),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_DEVICE_MASTER),
                    buildSortOrderMap("deviceCode", "uuid", "deviceName", "manufacturer", "model", "onlineStatus", "installLocation",
                            "faceThreshold", "temperatureThreshold")),
            Map.entry("supplier:qualification_verify",
                    buildSortOrderMap("supplierCode", "supplierName", "unifiedCreditCode", "licenseNo", "licenseExpireAt",
                            "foodLicenseNo", "foodLicenseExpireAt", "qualificationFiles", "qualificationStatus")),
            Map.entry("inbound:arrival_receipt",
                    buildSortOrderMap("inboundNo", "sourceOrderNo", "supplierName", "warehouseCode", "warehouseName",
                            "receiptStatus", "approvedAt", "attachments", "remark"))
    );
    private static final Map<String, Map<String, Integer>> BUILTIN_STATUS_SORT_MAP = Map.ofEntries(
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_LOGISTICS),
                    buildSortOrderMap("pending", "shipped", "in_transit", "arrived")),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_TASK),
                    buildSortOrderMap(SAMPLE_STATUS_PENDING_SAMPLE, SAMPLE_STATUS_SAMPLED, SAMPLE_STATUS_EVALUATED,
                            SAMPLE_STATUS_PENDING_DISPOSAL, SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_OVERDUE,
                            SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RECORD),
                    buildSortOrderMap(SAMPLE_STATUS_PENDING_SAMPLE, SAMPLE_STATUS_SAMPLED, SAMPLE_STATUS_EVALUATED,
                            SAMPLE_STATUS_PENDING_DISPOSAL, SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_OVERDUE,
                            SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RESULT),
                    buildSortOrderMap(SAMPLE_STATUS_PENDING_SAMPLE, SAMPLE_STATUS_SAMPLED, SAMPLE_STATUS_EVALUATED,
                            SAMPLE_STATUS_PENDING_DISPOSAL, SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_OVERDUE,
                            SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_HEALTH_CERTIFICATE),
                    buildSortOrderMap(HEALTH_CERT_STATUS_PENDING, HEALTH_CERT_STATUS_VALID, HEALTH_CERT_STATUS_EXPIRING, HEALTH_CERT_STATUS_EXPIRED)),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_MORNING_CHECK_RESULT),
                    buildSortOrderMap(HEALTH_CHECK_RESULT_PASS, HEALTH_CHECK_RESULT_FAIL))
    );
    private static final Map<String, Set<String>> TERMINAL_STATUS_CODE_MAP = Map.ofEntries(
            Map.entry(sceneKey(BIZ_MODULE_PURCHASE_ORDER, BIZ_SCENE_LOGISTICS),
                    buildStatusCodeSet(LOGISTICS_STATUS_ARRIVED)),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_TASK),
                    buildStatusCodeSet(SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RECORD),
                    buildStatusCodeSet(SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_SAMPLE_RETENTION, BIZ_SCENE_SAMPLE_RESULT),
                    buildStatusCodeSet(SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED)),
            Map.entry(sceneKey(BIZ_MODULE_MORNING_CHECK, BIZ_SCENE_MORNING_CHECK_RESULT),
                    buildStatusCodeSet(HEALTH_CHECK_RESULT_PASS, HEALTH_CHECK_RESULT_FAIL))
    );

    private final IntegrationProviderTemplateMapper providerTemplateMapper;
    private final IntegrationModuleConfigMapper moduleConfigMapper;
    private final IntegrationSecretConfigMapper secretConfigMapper;
    private final IntegrationFieldMappingMapper fieldMappingMapper;
    private final IntegrationStatusMappingMapper statusMappingMapper;
    private final IntegrationBindingMapper bindingMapper;
    private final IntegrationSyncTaskMapper syncTaskMapper;
    private final IntegrationSyncLogMapper syncLogMapper;
    private final IntegrationCallbackLogMapper callbackLogMapper;
    private final IntegrationFileRecordMapper fileRecordMapper;
    private final IntegrationHealthCheckLogMapper healthCheckLogMapper;
    private final AuditLogService auditLogService;
    private final DataScopeService dataScopeService;
    private final AiConfigCryptoService aiConfigCryptoService;
    private final FileStorageService fileStorageService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    @Qualifier("aiRestTemplate")
    private final RestTemplate aiRestTemplate;
    private final Map<String, ProviderCircuitState> providerCircuitStateMap = new ConcurrentHashMap<>();
    private volatile Boolean healthCheckLogTableAvailableCache;
    private volatile long healthCheckLogTableAvailableCheckedAt;

    @Override
    public Page<IntegrationProviderTemplateVO> pageProviders(IntegrationProviderTemplateQueryDTO query) {
        ensureBuiltinProviders();
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<IntegrationProviderTemplate> wrapper = new LambdaQueryWrapper<IntegrationProviderTemplate>()
                .eq(tenantId != null, IntegrationProviderTemplate::getTenantId, tenantId)
                .eq(StrUtil.isNotBlank(query.getProviderType()), IntegrationProviderTemplate::getProviderType, query.getProviderType())
                .eq(StrUtil.isNotBlank(query.getStatus()), IntegrationProviderTemplate::getStatus, query.getStatus())
                .orderByAsc(IntegrationProviderTemplate::getProviderType)
                .orderByAsc(IntegrationProviderTemplate::getProviderCode);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationProviderTemplate::getProviderName, query.getKeyword())
                    .or().like(IntegrationProviderTemplate::getProviderCode, query.getKeyword()));
        }
        Page<IntegrationProviderTemplate> page = providerTemplateMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<IntegrationProviderTemplateVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toProviderVO).toList());
        return result;
    }

    @Override
    public IntegrationProviderTemplateVO getProvider(Long id) {
        ensureBuiltinProviders();
        return toProviderVO(getProviderEntity(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProvider(IntegrationProviderTemplateSaveDTO dto) {
        normalizeProviderTemplateSaveDTO(dto);
        validateProviderTemplateSaveDTO(dto);
        ensureProviderCodeUnique(dto.getProviderCode(), null);
        IntegrationProviderTemplate entity = new IntegrationProviderTemplate();
        applyProviderTemplateSaveData(entity, dto);
        entity.setOrgId(0L);
        entity.setTenantId(currentTenantId());
        providerTemplateMapper.insert(entity);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.CREATE, entity.getId(), entity.getProviderCode(),
                "新增第三方平台模板：" + entity.getProviderName(), null, safeJson(entity));
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProvider(Long id, IntegrationProviderTemplateSaveDTO dto) {
        normalizeProviderTemplateSaveDTO(dto);
        validateProviderTemplateSaveDTO(dto);
        IntegrationProviderTemplate before = getProviderEntity(id);
        ensureBuiltinProviderChangeAllowed(before, AuditOperationType.UPDATE, dto,
                "编辑第三方平台模板失败：" + before.getProviderName(), "系统内置模板不允许编辑");
        ensureProviderCodeChangeAllowed(before, dto);
        ensureProviderCodeUnique(dto.getProviderCode(), id);
        IntegrationProviderTemplate update = new IntegrationProviderTemplate();
        applyProviderTemplateSaveData(update, dto);
        update.setId(id);
        providerTemplateMapper.updateById(update);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, id, before.getProviderCode(),
                "编辑第三方平台模板：" + dto.getProviderName(), safeJson(before), safeJson(getProviderEntity(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProvider(Long id) {
        IntegrationProviderTemplate before = getProviderEntity(id);
        if (isBuiltinProvider(before.getProviderCode())) {
            String message = "系统内置模板不允许删除";
            auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.DELETE, id, before.getProviderCode(),
                    "删除第三方平台模板失败：" + before.getProviderName(), safeJson(before), null, "failed", message);
            throw BizException.validationFailed(message);
        }
        Long refCount = moduleConfigMapper.selectCount(new LambdaQueryWrapper<IntegrationModuleConfig>()
                .eq(IntegrationModuleConfig::getProviderCode, before.getProviderCode())
                .eq(before.getTenantId() != null, IntegrationModuleConfig::getTenantId, before.getTenantId()));
        if (refCount != null && refCount > 0) {
            throw BizException.conflict("该平台模板已被模块接入配置引用，不能删除");
        }
        providerTemplateMapper.deleteById(id);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.DELETE, id, before.getProviderCode(),
                "删除第三方平台模板：" + before.getProviderName(), safeJson(before), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeProviderStatus(Long id, IntegrationProviderTemplateStatusDTO dto) {
        IntegrationProviderTemplate before = getProviderEntity(id);
        ensureBuiltinProviderChangeAllowed(before, AuditOperationType.STATUS_CHANGE, dto,
                "变更第三方平台模板状态失败：" + before.getProviderName(), "系统内置模板不允许启用或停用");
        validateProviderStatusValue(dto == null ? null : dto.getStatus());
        IntegrationProviderTemplate update = new IntegrationProviderTemplate();
        update.setId(id);
        update.setStatus(dto.getStatus());
        providerTemplateMapper.updateById(update);
        if ("inactive".equals(dto.getStatus())) {
            stopBindingsByProvider(before.getProviderCode(), before.getTenantId(), BUILTIN_PROVIDER_STATUS_STOP_MESSAGE);
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.STATUS_CHANGE, id, before.getProviderCode(),
                ("active".equals(dto.getStatus()) ? "启用" : "停用") + "第三方平台模板：" + before.getProviderName(),
                safeJson(before), safeJson(getProviderEntity(id)));
    }

    @Override
    public Page<IntegrationModuleConfigVO> pageModuleConfigs(IntegrationModuleConfigQueryDTO query) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        ensureBuiltinProviders();
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        Long tenantId = currentTenantId();
        LambdaQueryWrapper<IntegrationModuleConfig> wrapper = new LambdaQueryWrapper<IntegrationModuleConfig>()
                .eq(tenantId != null, IntegrationModuleConfig::getTenantId, tenantId)
                .eq(query.getOrgId() != null, IntegrationModuleConfig::getOrgId, query.getOrgId())
                .eq(StrUtil.isNotBlank(query.getBizModule()), IntegrationModuleConfig::getBizModule, query.getBizModule())
                .eq(StrUtil.isNotBlank(query.getBizScene()), IntegrationModuleConfig::getBizScene, query.getBizScene())
                .eq(StrUtil.isNotBlank(query.getProviderCode()), IntegrationModuleConfig::getProviderCode, query.getProviderCode())
                .eq(query.getEnabled() != null, IntegrationModuleConfig::getEnabled, query.getEnabled())
                .eq(StrUtil.isNotBlank(query.getDefaultMode()), IntegrationModuleConfig::getDefaultMode, query.getDefaultMode())
                .orderByDesc(IntegrationModuleConfig::getUpdatedAt)
                .orderByDesc(IntegrationModuleConfig::getId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationModuleConfig::getConfigName, query.getKeyword())
                    .or().like(IntegrationModuleConfig::getProviderCode, query.getKeyword())
                    .or().like(IntegrationModuleConfig::getBizScene, query.getKeyword()));
        }
        applyOrgScope(wrapper, query.getOrgId(), scope);
        Page<IntegrationModuleConfig> page = moduleConfigMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, String> orgNameMap = loadOrgNames(page.getRecords().stream().map(IntegrationModuleConfig::getOrgId).collect(Collectors.toSet()));
        Map<String, String> providerNameMap = loadProviderNameMap(page.getRecords().stream().map(IntegrationModuleConfig::getProviderCode).collect(Collectors.toSet()));
        Page<IntegrationModuleConfigVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(item -> toModuleConfigVO(item, orgNameMap, providerNameMap, false))
                .toList());
        return result;
    }

    @Override
    public IntegrationModuleConfigVO getModuleConfig(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        IntegrationModuleConfig entity = getModuleConfigEntity(id, true);
        return toModuleConfigVO(entity,
                loadOrgNames(Set.of(entity.getOrgId())),
                loadProviderNameMap(Set.of(entity.getProviderCode())),
                true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModuleConfig(IntegrationModuleConfigSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_CREATE, "当前用户无第三方接入配置新增权限");
        normalizeModuleConfigDto(dto);
        IntegrationProviderTemplate provider = getProviderByCode(dto.getProviderCode(), true);
        validateModuleConfigPayload(dto, null, provider);
        validateOrgWritable(dto.getOrgId());
        validateProviderForScene(provider, dto.getBizModule(), dto.getBizScene());
        ensureModuleConfigUnique(currentTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getProviderCode(), null);
        IntegrationModuleConfig entity = new IntegrationModuleConfig();
        BeanUtils.copyProperties(dto, entity);
        entity.setTenantId(currentTenantId());
        normalizeConfig(entity);
        try {
            moduleConfigMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw BizException.conflict("相同组织、模块、场景、平台的配置已存在");
        }
        replaceSecrets(entity.getId(), entity.getOrgId(), dto.getSecrets());
        seedDefaultStatusMappings(entity);
        refreshBindingsAfterConfigChange(entity);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.CREATE, entity.getId(), entity.getProviderCode(),
                "新增模块接入配置：" + entity.getConfigName(), null, safeJson(getModuleConfigEntity(entity.getId(), false)));
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModuleConfig(Long id, IntegrationModuleConfigSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_EDIT, "当前用户无第三方接入配置编辑权限");
        IntegrationModuleConfig before = getModuleConfigEntity(id, true);
        normalizeModuleConfigDto(dto);
        IntegrationProviderTemplate provider = getProviderByCode(dto.getProviderCode(), true);
        validateModuleConfigPayload(dto, before, provider);
        ensureModuleConfigIdentityChangeAllowed(before, dto);
        validateOrgWritable(dto.getOrgId());
        validateProviderForScene(provider, dto.getBizModule(), dto.getBizScene());
        ensureModuleConfigUnique(before.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getProviderCode(), id);
        IntegrationModuleConfig update = new IntegrationModuleConfig();
        BeanUtils.copyProperties(dto, update);
        update.setId(id);
        update.setTenantId(before.getTenantId());
        normalizeConfig(update);
        try {
            moduleConfigMapper.updateById(update);
        } catch (DuplicateKeyException ex) {
            throw BizException.conflict("相同组织、模块、场景、平台的配置已存在");
        }
        replaceSecrets(id, dto.getOrgId(), dto.getSecrets());
        refreshBindingsAfterConfigChange(getModuleConfigEntity(id, false));
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, id, before.getProviderCode(),
                "编辑模块接入配置：" + dto.getConfigName(), safeJson(before), safeJson(getModuleConfigEntity(id, false)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteModuleConfig(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_DELETE, "当前用户无第三方接入配置删除权限");
        IntegrationModuleConfig before = getModuleConfigEntity(id, true);
        Long bindingCount = bindingMapper.selectCount(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getConfigId, id));
        if (bindingCount != null && bindingCount > 0) {
            throw BizException.conflict("该配置已有单据绑定记录，不能删除");
        }
        moduleConfigMapper.deleteById(id);
        secretConfigMapper.delete(new LambdaQueryWrapper<IntegrationSecretConfig>().eq(IntegrationSecretConfig::getConfigId, id));
        fieldMappingMapper.delete(new LambdaQueryWrapper<IntegrationFieldMapping>().eq(IntegrationFieldMapping::getConfigId, id));
        statusMappingMapper.delete(new LambdaQueryWrapper<IntegrationStatusMapping>().eq(IntegrationStatusMapping::getConfigId, id));
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.DELETE, id, before.getProviderCode(),
                "删除模块接入配置：" + before.getConfigName(), safeJson(before), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeModuleConfigStatus(Long id, IntegrationModuleConfigStatusDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_STATUS, "当前用户无第三方接入配置启停权限");
        validateConfigBinaryFlag(dto.getEnabled(), "启用状态");
        IntegrationModuleConfig before = getModuleConfigEntity(id, true);
        IntegrationModuleConfig update = new IntegrationModuleConfig();
        update.setId(id);
        update.setEnabled(dto.getEnabled());
        moduleConfigMapper.updateById(update);
        refreshBindingsAfterConfigChange(getModuleConfigEntity(id, false));
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.STATUS_CHANGE, id, before.getProviderCode(),
                (dto.getEnabled() != null && dto.getEnabled() == 1 ? "启用" : "停用") + "模块接入配置：" + before.getConfigName(),
                safeJson(before), safeJson(getModuleConfigEntity(id, false)));
    }

    @Override
    public IntegrationHealthCheckVO testModuleConfig(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_TEST, "当前用户无第三方连接测试权限");
        IntegrationModuleConfig config = getModuleConfigEntity(id, true);
        IntegrationProviderTemplate provider = getProviderByCode(config.getProviderCode(), true);
        Map<String, String> secrets = loadSecretPlainMap(config.getId());
        HealthCheckResult testResult = performConnectionTest(config, provider, secrets);
        recordHealthCheckLog(config, provider, testResult);
        List<IntegrationHealthCheckLog> recentTestLogs = loadRecentHealthCheckLogsByConfig(config.getId(), HEALTH_CHECK_LOG_DETAIL_LIMIT);
        HealthCheckResult historical = buildHistoricalHealth(config, findProviderByCodeOptional(config.getProviderCode()), recentTestLogs);
        return buildHealthCheckVO(
                config,
                findProviderByCodeOptional(config.getProviderCode()),
                historical,
                loadRecentFailedLogsByConfig(config.getId(), 3),
                toHealthCheckLogVos(recentTestLogs)
        );
    }

    @Override
    public Page<IntegrationFieldMappingVO> pageFieldMappings(IntegrationFieldMappingQueryDTO query) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        Long configId = query.getConfigId();
        if (configId != null) {
            getModuleConfigEntity(configId, true);
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        LambdaQueryWrapper<IntegrationFieldMapping> wrapper = new LambdaQueryWrapper<IntegrationFieldMapping>()
                .eq(configId != null, IntegrationFieldMapping::getConfigId, configId)
                .eq(currentTenantId() != null, IntegrationFieldMapping::getTenantId, currentTenantId())
                .orderByAsc(IntegrationFieldMapping::getConfigId)
                .orderByAsc(IntegrationFieldMapping::getSortNo)
                .orderByAsc(IntegrationFieldMapping::getId);
        if (configId == null) {
            applyOrgScope(wrapper, null, scope);
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationFieldMapping::getSourceField, query.getKeyword())
                    .or().like(IntegrationFieldMapping::getTargetField, query.getKeyword()));
        }
        Page<IntegrationFieldMapping> page = fieldMappingMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(page.getRecords().stream().map(IntegrationFieldMapping::getConfigId).collect(Collectors.toSet()));
        Page<IntegrationFieldMappingVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<IntegrationFieldMapping> records = new ArrayList<>(page.getRecords());
        records.sort(buildFieldMappingComparator(configMap));
        result.setRecords(records.stream().map(item -> toFieldMappingVO(item, configMap)).toList());
        return result;
    }

    @Override
    public IntegrationFieldMappingVO getFieldMapping(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        IntegrationFieldMapping entity = getFieldMappingEntity(id);
        return toFieldMappingVO(entity, loadConfigMap(Set.of(entity.getConfigId())));
    }

    @Override
    public void checkFieldMappingDuplicate(IntegrationFieldMappingDuplicateCheckDTO dto) {
        ensureIntegrationPermission(dto.getId() == null ? PERMISSION_INTEGRATION_CREATE : PERMISSION_INTEGRATION_EDIT,
                dto.getId() == null ? "当前用户无字段映射新增权限" : "当前用户无字段映射编辑权限");
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        validateFieldMappingDuplicatePayload(config, dto);
        ensureFieldMappingUnique(config, dto.getId(), dto.getTargetField(), dto.getEnabled(), dto,
                resolveAuditOperationType(dto.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFieldMapping(IntegrationFieldMappingSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_CREATE, "当前用户无字段映射新增权限");
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        normalizeFieldMappingSaveDTO(dto);
        validateFieldMappingPayload(config, dto, null);
        ensureFieldMappingUnique(config, null, dto.getTargetField(), dto.getEnabled(), dto, AuditOperationType.CREATE);
        IntegrationFieldMapping entity = new IntegrationFieldMapping();
        BeanUtils.copyProperties(dto, entity);
        entity.setSortNo(resolveFieldMappingSortNo(config, dto, null));
        entity.setOrgId(config.getOrgId());
        entity.setTenantId(config.getTenantId());
        try {
            fieldMappingMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw BizException.validationFailed("当前接入配置下已存在该系统字段的启用映射，请勿重复配置。");
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.CREATE, entity.getId(), config.getProviderCode(),
                "新增字段映射：" + entity.getTargetField(), null, safeJson(entity));
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateFieldMapping(Long id, IntegrationFieldMappingSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_EDIT, "当前用户无字段映射编辑权限");
        IntegrationFieldMapping before = getFieldMappingEntity(id);
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        normalizeFieldMappingSaveDTO(dto);
        validateFieldMappingPayload(config, dto, before);
        ensureFieldMappingUnique(config, id, dto.getTargetField(), dto.getEnabled(), dto, AuditOperationType.UPDATE);
        IntegrationFieldMapping update = new IntegrationFieldMapping();
        BeanUtils.copyProperties(dto, update);
        update.setId(id);
        update.setSortNo(resolveFieldMappingSortNo(config, dto, before));
        update.setOrgId(config.getOrgId());
        update.setTenantId(config.getTenantId());
        try {
            fieldMappingMapper.updateById(update);
        } catch (DuplicateKeyException ex) {
            throw BizException.validationFailed("当前接入配置下已存在该系统字段的启用映射，请勿重复配置。");
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, id, config.getProviderCode(),
                "编辑字段映射：" + dto.getTargetField(), safeJson(before), safeJson(getFieldMappingEntity(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteFieldMapping(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_DELETE, "当前用户无字段映射删除权限");
        IntegrationFieldMapping before = getFieldMappingEntity(id);
        fieldMappingMapper.deleteById(id);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.DELETE, id, String.valueOf(before.getConfigId()),
                "删除字段映射：" + before.getTargetField(), safeJson(before), null);
    }

    @Override
    public Page<IntegrationStatusMappingVO> pageStatusMappings(IntegrationStatusMappingQueryDTO query) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        Long configId = query.getConfigId();
        if (configId != null) {
            getModuleConfigEntity(configId, true);
        }
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        LambdaQueryWrapper<IntegrationStatusMapping> wrapper = new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(configId != null, IntegrationStatusMapping::getConfigId, configId)
                .eq(currentTenantId() != null, IntegrationStatusMapping::getTenantId, currentTenantId())
                .orderByAsc(IntegrationStatusMapping::getConfigId)
                .orderByAsc(IntegrationStatusMapping::getSortNo)
                .orderByAsc(IntegrationStatusMapping::getId);
        if (configId == null) {
            applyOrgScope(wrapper, null, scope);
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationStatusMapping::getSourceStatusCode, query.getKeyword())
                    .or().like(IntegrationStatusMapping::getSourceStatusName, query.getKeyword())
                    .or().like(IntegrationStatusMapping::getTargetStatusCode, query.getKeyword()));
        }
        Page<IntegrationStatusMapping> page = statusMappingMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(page.getRecords().stream().map(IntegrationStatusMapping::getConfigId).collect(Collectors.toSet()));
        Page<IntegrationStatusMappingVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<IntegrationStatusMapping> records = new ArrayList<>(page.getRecords());
        records.sort(buildStatusMappingComparator(configMap));
        result.setRecords(records.stream().map(item -> toStatusMappingVO(item, configMap)).toList());
        return result;
    }

    @Override
    public IntegrationStatusMappingVO getStatusMapping(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        IntegrationStatusMapping entity = getStatusMappingEntity(id);
        return toStatusMappingVO(entity, loadConfigMap(Set.of(entity.getConfigId())));
    }

    @Override
    public void checkStatusMappingDuplicate(IntegrationStatusMappingDuplicateCheckDTO dto) {
        ensureIntegrationPermission(dto.getId() == null ? PERMISSION_INTEGRATION_CREATE : PERMISSION_INTEGRATION_EDIT,
                dto.getId() == null ? "当前用户无状态映射新增权限" : "当前用户无状态映射编辑权限");
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        validateStatusMappingDuplicatePayload(config, dto);
        ensureStatusMappingUnique(config, dto.getId(), dto.getSourceStatusCode(), dto, resolveAuditOperationType(dto.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStatusMapping(IntegrationStatusMappingSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_CREATE, "当前用户无状态映射新增权限");
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        normalizeStatusMappingSaveDTO(dto);
        validateStatusMappingPayload(config, dto, null);
        ensureStatusMappingUnique(config, null, dto.getSourceStatusCode(), dto, AuditOperationType.CREATE);
        IntegrationStatusMapping entity = new IntegrationStatusMapping();
        BeanUtils.copyProperties(dto, entity);
        entity.setSortNo(resolveStatusMappingSortNo(config, dto, null));
        entity.setOrgId(config.getOrgId());
        entity.setTenantId(config.getTenantId());
        try {
            statusMappingMapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw BizException.validationFailed("当前接入配置下已存在该第三方状态编码映射，请勿重复配置。");
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.CREATE, entity.getId(), config.getProviderCode(),
                "新增状态映射：" + entity.getSourceStatusCode(), null, safeJson(entity));
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatusMapping(Long id, IntegrationStatusMappingSaveDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_EDIT, "当前用户无状态映射编辑权限");
        IntegrationStatusMapping before = getStatusMappingEntity(id);
        IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
        normalizeStatusMappingSaveDTO(dto);
        validateStatusMappingPayload(config, dto, before);
        ensureStatusMappingUnique(config, id, dto.getSourceStatusCode(), dto, AuditOperationType.UPDATE);
        IntegrationStatusMapping update = new IntegrationStatusMapping();
        BeanUtils.copyProperties(dto, update);
        update.setId(id);
        update.setSortNo(resolveStatusMappingSortNo(config, dto, before));
        update.setOrgId(config.getOrgId());
        update.setTenantId(config.getTenantId());
        try {
            statusMappingMapper.updateById(update);
        } catch (DuplicateKeyException ex) {
            throw BizException.validationFailed("当前接入配置下已存在该第三方状态编码映射，请勿重复配置。");
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, id, config.getProviderCode(),
                "编辑状态映射：" + dto.getSourceStatusCode(), safeJson(before), safeJson(getStatusMappingEntity(id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStatusMapping(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_DELETE, "当前用户无状态映射删除权限");
        IntegrationStatusMapping before = getStatusMappingEntity(id);
        statusMappingMapper.deleteById(id);
        auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.DELETE, id, String.valueOf(before.getConfigId()),
                "删除状态映射：" + before.getSourceStatusCode(), safeJson(before), null);
    }

    @Override
    public IntegrationOverviewVO getOverview(Long orgId) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(orgId, scope);
        String orgSql = buildOrgFilterSql("org_id", orgId, scope);
        Object[] orgArgs = buildOrgFilterArgs(orgId, scope);
        IntegrationOverviewVO vo = new IntegrationOverviewVO();
        vo.setEnabledIntegrationCount(queryCount("SELECT COUNT(*) FROM sys_integration_module_config WHERE deleted = 0 AND enabled = 1" + orgSql, orgArgs));
        vo.setSyncSuccessCount(queryCount("SELECT COUNT(*) FROM sys_integration_sync_log WHERE deleted = 0 AND sync_status = 'success'" + orgSql, orgArgs));
        vo.setSyncFailureCount(queryCount("SELECT COUNT(*) FROM sys_integration_sync_log WHERE deleted = 0 AND sync_status = 'failed'" + orgSql, orgArgs));
        long callbackTotal = queryCount("SELECT COUNT(*) FROM sys_integration_callback_log WHERE deleted = 0" + orgSql, orgArgs);
        long callbackSuccess = queryCount("SELECT COUNT(*) FROM sys_integration_callback_log WHERE deleted = 0 AND process_status = 'success'" + orgSql, orgArgs);
        vo.setCallbackSuccessRate(callbackTotal == 0 ? BigDecimal.ZERO : percent(callbackSuccess, callbackTotal));
        vo.setAverageDurationMs(BigDecimal.valueOf(queryLong("SELECT COALESCE(AVG(duration_ms),0) FROM sys_integration_sync_log WHERE deleted = 0" + orgSql, orgArgs)));
        List<IntegrationOverviewVO.MetricItem> moduleDistribution = queryMetricList(
                "SELECT biz_module AS code, biz_module AS label, COUNT(*) AS value FROM sys_integration_module_config WHERE deleted = 0 AND enabled = 1" + orgSql + " GROUP BY biz_module ORDER BY value DESC",
                orgArgs
        );
        moduleDistribution.forEach(item -> item.setLabel(resolveBizModuleDisplayLabel(item.getCode(), item.getLabel())));
        vo.setModuleDistribution(moduleDistribution);
        List<IntegrationOverviewVO.MetricItem> providerFailureDistribution = queryMetricList(
                "SELECT provider_code AS code, provider_code AS label, COUNT(*) AS value FROM sys_integration_sync_log WHERE deleted = 0 AND sync_status = 'failed'" + orgSql + " GROUP BY provider_code ORDER BY value DESC",
                orgArgs
        );
        Map<String, String> providerNameMap = loadProviderNameMap(providerFailureDistribution.stream()
                .map(IntegrationOverviewVO.MetricItem::getCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet()));
        providerFailureDistribution.forEach(item -> item.setLabel(firstNonBlank(providerNameMap.get(item.getCode()), item.getLabel(), item.getCode())));
        vo.setProviderFailureDistribution(providerFailureDistribution);
        vo.setSyncTrend(queryMetricList(
                "SELECT DATE_FORMAT(created_at, '%m-%d') AS code, DATE_FORMAT(created_at, '%m-%d') AS label, COUNT(*) AS value FROM sys_integration_sync_log WHERE deleted = 0 AND created_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)" + orgSql + " GROUP BY DATE_FORMAT(created_at, '%m-%d') ORDER BY code",
                orgArgs
        ));
        vo.setRecentFailedRecords(loadRecentSyncLogs("failed", orgId, scope, 5));
        vo.setRecentTimeoutRecords(loadRecentTimeoutLogs(orgId, scope, 5));
        vo.setRecentSignFailedRecords(loadRecentSignFailedLogs(orgId, scope, 5));
        return vo;
    }

    @Override
    public Page<IntegrationSyncTaskVO> pageSyncTasks(IntegrationSyncTaskQueryDTO query) {
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(query.getOrgId(), scope);
        Set<Long> providerConfigIds = resolveConfigIdsByProvider(query.getProviderCode());
        LambdaQueryWrapper<IntegrationSyncTask> wrapper = new LambdaQueryWrapper<IntegrationSyncTask>()
                .eq(currentTenantId() != null, IntegrationSyncTask::getTenantId, currentTenantId())
                .eq(query.getOrgId() != null, IntegrationSyncTask::getOrgId, query.getOrgId())
                .eq(StrUtil.isNotBlank(query.getBizModule()), IntegrationSyncTask::getBizModule, query.getBizModule())
                .eq(StrUtil.isNotBlank(query.getBizScene()), IntegrationSyncTask::getBizScene, query.getBizScene())
                .eq(StrUtil.isNotBlank(query.getTriggerType()), IntegrationSyncTask::getTriggerType, query.getTriggerType())
                .orderByDesc(IntegrationSyncTask::getCreatedAt)
                .orderByDesc(IntegrationSyncTask::getId);
        if (Optional.ofNullable(query.getPendingHandleOnly()).orElse(0) == 1) {
            wrapper.in(IntegrationSyncTask::getTaskStatus, TASK_PENDING_HANDLE_STATUSES);
        } else {
            wrapper.eq(StrUtil.isNotBlank(query.getTaskStatus()), IntegrationSyncTask::getTaskStatus, query.getTaskStatus());
        }
        if (StrUtil.isNotBlank(query.getProviderCode())) {
            if (providerConfigIds.isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in(IntegrationSyncTask::getConfigId, providerConfigIds);
            }
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationSyncTask::getTaskNo, query.getKeyword())
                    .or().like(IntegrationSyncTask::getBizNo, query.getKeyword())
                    .or().like(IntegrationSyncTask::getExternalNo, query.getKeyword()));
        }
        applyOrgScope(wrapper, query.getOrgId(), scope);
        Page<IntegrationSyncTask> page = syncTaskMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(page.getRecords().stream().map(IntegrationSyncTask::getConfigId).collect(Collectors.toSet()));
        Map<Long, String> orgNameMap = loadOrgNames(page.getRecords().stream().map(IntegrationSyncTask::getOrgId).collect(Collectors.toSet()));
        Map<String, String> providerNameMap = loadProviderNameMap(configMap.values().stream().map(IntegrationModuleConfig::getProviderCode).collect(Collectors.toSet()));
        Map<Long, Long> latestTaskIdByBinding = loadLatestTaskIdsByBinding(page.getRecords().stream()
                .map(IntegrationSyncTask::getBindingId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toSet()));
        Page<IntegrationSyncTaskVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream()
                .map(item -> toSyncTaskVO(item, configMap, orgNameMap, providerNameMap, latestTaskIdByBinding))
                .toList());
        return result;
    }

    @Override
    public IntegrationSyncTaskVO getSyncTask(Long id) {
        IntegrationSyncTask entity = getTaskEntity(id, true);
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(Set.of(entity.getConfigId()));
        Map<Long, String> orgNameMap = loadOrgNames(Set.of(entity.getOrgId()));
        Map<String, String> providerNameMap = loadProviderNameMap(configMap.values().stream()
                .map(IntegrationModuleConfig::getProviderCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet()));
        Map<Long, Long> latestTaskIdByBinding = entity.getBindingId() != null && entity.getBindingId() > 0
                ? loadLatestTaskIdsByBinding(Set.of(entity.getBindingId()))
                : Collections.emptyMap();
        return toSyncTaskVO(entity, configMap, orgNameMap, providerNameMap, latestTaskIdByBinding);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationExecutionResultVO triggerSync(IntegrationSyncTaskTriggerDTO dto) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_SYNC, "当前用户无第三方同步触发权限");
        IntegrationExecutionResultVO result = null;
        IntegrationSyncTask task = null;
        try {
            ValidatedTaskTrigger trigger = validateTaskTriggerPayload(dto);
            IntegrationModuleConfig config = getModuleConfigEntity(dto.getConfigId(), true);
            if (!Objects.equals(config.getBizModule(), dto.getBizModule()) || !Objects.equals(config.getBizScene(), dto.getBizScene())) {
                throw BizException.validationFailed("业务模块或场景与配置不匹配");
            }
            if (config.getEnabled() == null || config.getEnabled() != 1) {
                throw BizException.conflict("当前接入配置未启用");
            }
            validateRequestedMaintenanceMode(config, dto.getMaintenanceMode());
            String maintenanceMode = normalizeMode(dto.getMaintenanceMode(), config);
            if (MODE_MANUAL.equals(maintenanceMode) && trigger.queryOnly() != 1) {
                throw BizException.validationFailed("手工模式仅支持只查询不覆盖");
            }
            ensureBusinessObjectExists(trigger.bizModule(), trigger.bizScene(), trigger.bizId(), trigger.bizNo(), config.getOrgId(), config.getTenantId());
            List<IntegrationBinding> bindings = loadBindingsByBiz(config.getTenantId(), config.getOrgId(), trigger.bizModule(), trigger.bizScene(), trigger.bizId());
            IntegrationBinding currentBinding = selectCurrentBinding(bindings, config.getId());
            ensureBindingSwitchAllowed(currentBinding, config, maintenanceMode, config.getTenantId(), config.getOrgId());

            IntegrationSyncTaskTriggerDTO normalizedDto = buildNormalizedTaskTriggerDTO(dto, trigger);
            if (trigger.queryOnly() == 1) {
                IntegrationBinding previewBinding = buildPreviewBinding(config, normalizedDto, maintenanceMode, currentBinding);
                task = createTask(config, previewBinding, TASK_TYPE_QUERY_ONLY, TRIGGER_TYPE_MANUAL, 0, UserContext.getUserId(), UserContext.getRealName());
                result = executeTask(task, config, previewBinding, null, null, TaskExecutionOptions.queryOnly());
            } else {
                IntegrationBinding binding = upsertBinding(config, normalizedDto, maintenanceMode);
                if (MODE_THIRD_PARTY.equals(maintenanceMode)) {
                    deactivateOtherBindings(binding);
                } else {
                    clearBindingNextSyncAt(binding.getId(), binding.getRemark());
                }
                task = createTask(config, binding, TASK_TYPE_SYNC, TRIGGER_TYPE_MANUAL, 0, UserContext.getUserId(), UserContext.getRealName());
                result = executeTask(task, config, binding, null, null, TaskExecutionOptions.standard());
            }
            Long auditLogId = auditSyncTaskOperation(AuditOperationType.CONTROL, task, "手动执行第三方同步任务", safeJson(trigger), result, null);
            attachAuditLogToLatestSyncLog(task, auditLogId);
            return result;
        } catch (BizException ex) {
            Long auditLogId = auditSyncTaskOperation(AuditOperationType.CONTROL, task, "手动执行第三方同步任务", safeJson(dto), result, ex.getMessage());
            attachAuditLogToLatestSyncLog(task, auditLogId);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationExecutionResultVO retrySyncTask(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_RETRY, "当前用户无第三方同步重试权限");
        IntegrationSyncTask sourceTask = getTaskEntity(id, true);
        IntegrationModuleConfig config = getModuleConfigEntity(sourceTask.getConfigId(), true);
        IntegrationExecutionResultVO result = null;
        IntegrationSyncTask retryTask = null;
        try {
            validateRetryTaskAllowed(sourceTask, config);
            IntegrationBinding binding = getBindingEntity(sourceTask.getBindingId(), true);
            retryTask = createTask(
                    config,
                    binding,
                    TASK_TYPE_RETRY,
                    TRIGGER_TYPE_MANUAL,
                    Optional.ofNullable(sourceTask.getRetryCount()).orElse(0) + 1,
                    UserContext.getUserId(),
                    UserContext.getRealName()
            );
            result = executeTask(retryTask, config, binding, null, null, TaskExecutionOptions.standard());
            Long auditLogId = auditSyncTaskOperation(AuditOperationType.CONTROL, retryTask, "手动重试第三方同步任务", safeJson(sourceTask), result, null);
            attachAuditLogToLatestSyncLog(retryTask, auditLogId);
            return result;
        } catch (BizException ex) {
            Long auditLogId = auditSyncTaskOperation(AuditOperationType.CONTROL, retryTask, "手动重试第三方同步任务", safeJson(sourceTask), result, ex.getMessage());
            attachAuditLogToLatestSyncLog(retryTask, auditLogId);
            throw ex;
        }
    }

    private ValidatedTaskTrigger validateTaskTriggerPayload(IntegrationSyncTaskTriggerDTO dto) {
        if (dto == null) {
            throw BizException.validationFailed("同步任务参数不能为空");
        }
        dto.setBizModule(StrUtil.trim(dto.getBizModule()));
        dto.setBizScene(StrUtil.trim(dto.getBizScene()));
        dto.setBizId(StrUtil.trim(dto.getBizId()));
        dto.setBizNo(StrUtil.trim(dto.getBizNo()));
        dto.setExternalNo(StrUtil.trim(dto.getExternalNo()));
        dto.setMaintenanceMode(StrUtil.trimToNull(dto.getMaintenanceMode()));
        dto.setModeSource(StrUtil.trimToNull(dto.getModeSource()));
        dto.setTriggerType(StrUtil.trimToNull(dto.getTriggerType()));
        if (StrUtil.isBlank(dto.getBizModule()) || StrUtil.isBlank(dto.getBizScene())
                || StrUtil.isBlank(dto.getBizId()) || StrUtil.isBlank(dto.getBizNo())
                || StrUtil.isBlank(dto.getExternalNo())) {
            throw BizException.validationFailed("请完整填写同步任务信息");
        }
        validateAllowedValue(dto.getBizModule(), Set.of(BIZ_MODULE_PURCHASE_ORDER, BIZ_MODULE_SAMPLE_RETENTION, BIZ_MODULE_MORNING_CHECK, "supplier", "inbound"),
                "业务模块不合法");
        validateSceneCode(dto.getBizModule(), dto.getBizScene());
        validateAllowedValue(firstNonBlank(dto.getTriggerType(), TRIGGER_TYPE_MANUAL), ALLOWED_TASK_TRIGGER_TYPES,
                "任务页触发方式仅支持 manual");
        validateAllowedValue(firstNonBlank(dto.getModeSource(), "user_selected"), ALLOWED_TASK_MODE_SOURCES,
                "方式来源仅支持 user_selected 或 business_scene");
        validateConfigBinaryFlag(firstNonNull(dto.getModeLocked(), 0), "锁定方式");
        validateConfigBinaryFlag(firstNonNull(dto.getQueryOnly(), 0), "只查询不覆盖");
        Long bizId = parsePositiveBizId(dto.getBizId());
        return new ValidatedTaskTrigger(
                dto.getBizModule(),
                dto.getBizScene(),
                bizId,
                dto.getBizNo(),
                dto.getExternalNo(),
                firstNonBlank(dto.getModeSource(), "user_selected"),
                firstNonNull(dto.getModeLocked(), 0),
                firstNonNull(dto.getQueryOnly(), 0)
        );
    }

    private IntegrationSyncTaskTriggerDTO buildNormalizedTaskTriggerDTO(IntegrationSyncTaskTriggerDTO source, ValidatedTaskTrigger trigger) {
        IntegrationSyncTaskTriggerDTO dto = new IntegrationSyncTaskTriggerDTO();
        dto.setConfigId(source.getConfigId());
        dto.setBizModule(trigger.bizModule());
        dto.setBizScene(trigger.bizScene());
        dto.setBizId(String.valueOf(trigger.bizId()));
        dto.setBizNo(trigger.bizNo());
        dto.setExternalNo(trigger.externalNo());
        dto.setMaintenanceMode(firstNonBlank(source.getMaintenanceMode(), MODE_THIRD_PARTY));
        dto.setModeSource(trigger.modeSource());
        dto.setModeLocked(trigger.modeLocked());
        dto.setTriggerType(TRIGGER_TYPE_MANUAL);
        dto.setQueryOnly(trigger.queryOnly());
        return dto;
    }

    private Long parsePositiveBizId(String rawBizId) {
        String normalized = StrUtil.trimToNull(rawBizId);
        if (normalized == null) {
            throw BizException.validationFailed("业务主键ID不能为空");
        }
        try {
            long bizId = Long.parseLong(normalized);
            if (bizId <= 0) {
                throw BizException.validationFailed("业务主键ID仅支持大于0的正整数");
            }
            return bizId;
        } catch (NumberFormatException ex) {
            throw BizException.validationFailed("业务主键ID仅支持大于0的正整数");
        }
    }

    private void ensureBusinessObjectExists(String bizModule, String bizScene, Long bizId, String bizNo, Long orgId, Long tenantId) {
        String expectedBizNo = StrUtil.trimToEmpty(bizNo);
        String actualBizNo = switch (sceneKey(bizModule, bizScene)) {
            case "purchase_order:logistics", "purchase_order:inspection", "purchase_order:traceability" ->
                    asString(queryBusinessRowById(
                            "SELECT id, order_no AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM scm_purchase_order WHERE id = ? AND deleted = 0 LIMIT 1",
                            bizId, orgId, tenantId, "采购订单不存在"
                    ).get("bizNo"));
            case "sample_retention:sample_task", "sample_retention:sample_record", "sample_retention:sample_result" ->
                    asString(queryBusinessRowById(
                            "SELECT id, sample_no AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM sample_record WHERE id = ? AND deleted = 0 LIMIT 1",
                            bizId, orgId, tenantId, "留样记录不存在"
                    ).get("bizNo"));
            case "sample_retention:sample_device", "morning_check:device_master" ->
                    asString(queryBusinessRowById(
                            "SELECT id, device_code AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM device_info WHERE id = ? AND deleted = 0 LIMIT 1",
                            bizId, orgId, tenantId, "设备信息不存在"
                    ).get("bizNo"));
            case "morning_check:face_profile" ->
                    asString(queryBusinessRowById(
                            "SELECT id, employee_no AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM sys_employee WHERE id = ? AND deleted = 0 LIMIT 1",
                            bizId, orgId, tenantId, "员工档案不存在"
                    ).get("bizNo"));
            case "morning_check:health_certificate" ->
                    asString(queryBusinessRowById(
                            "SELECT id, certificate_no AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM health_certificate WHERE id = ? AND deleted = 0 LIMIT 1",
                            bizId, orgId, tenantId, "健康证记录不存在"
                    ).get("bizNo"));
            case "morning_check:morning_check_result" ->
                    asString(queryBusinessRowById(
                            "SELECT id, check_no AS bizNo, org_id AS orgId, tenant_id AS tenantId FROM health_check_record WHERE id = ? LIMIT 1",
                            bizId, orgId, tenantId, "晨检记录不存在"
                    ).get("bizNo"));
            default -> null;
        };
        if (actualBizNo != null && !StrUtil.equals(expectedBizNo, StrUtil.trimToEmpty(actualBizNo))) {
            throw BizException.validationFailed("业务主键ID与业务编号不匹配，请检查后重试");
        }
    }

    private Map<String, Object> queryBusinessRowById(String sql, Long bizId, Long orgId, Long tenantId, String notFoundMessage) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, bizId);
        if (rows.isEmpty()) {
            throw BizException.notFound(notFoundMessage);
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, orgId == null ? asLong(row.get("orgId")) : orgId, tenantId);
        return row;
    }

    private IntegrationBinding buildPreviewBinding(IntegrationModuleConfig config, IntegrationSyncTaskTriggerDTO dto,
                                                   String maintenanceMode, IntegrationBinding currentBinding) {
        IntegrationBinding preview = new IntegrationBinding();
        if (currentBinding != null && Objects.equals(currentBinding.getConfigId(), config.getId())) {
            BeanUtils.copyProperties(currentBinding, preview);
        }
        preview.setId(firstNonNull(preview.getId(), 0L));
        preview.setConfigId(config.getId());
        preview.setProviderCode(config.getProviderCode());
        preview.setBizModule(dto.getBizModule());
        preview.setBizScene(dto.getBizScene());
        preview.setBizId(parsePositiveBizId(dto.getBizId()));
        preview.setBizNo(dto.getBizNo());
        preview.setExternalNo(dto.getExternalNo());
        preview.setMaintenanceMode(maintenanceMode);
        preview.setModeSource(config.getForceThirdParty() != null && config.getForceThirdParty() == 1
                ? "forced"
                : firstNonBlank(dto.getModeSource(), preview.getModeSource(), "user_selected"));
        preview.setModeLocked(config.getForceThirdParty() != null && config.getForceThirdParty() == 1
                ? 1
                : firstNonNull(dto.getModeLocked(), firstNonNull(preview.getModeLocked(), 0)));
        preview.setSyncStatus(firstNonBlank(preview.getSyncStatus(), TASK_STATUS_PENDING));
        preview.setOrgId(config.getOrgId());
        preview.setTenantId(config.getTenantId());
        preview.setFirstBindAt(firstNonNull(preview.getFirstBindAt(), LocalDateTime.now()));
        return preview;
    }

    private void validateRetryTaskAllowed(IntegrationSyncTask sourceTask, IntegrationModuleConfig config) {
        if (!TASK_STATUS_FAILED.equals(sourceTask.getTaskStatus())) {
            throw BizException.conflict("仅失败任务支持重试");
        }
        if (TASK_TYPE_QUERY_ONLY.equals(sourceTask.getTaskType())) {
            throw BizException.conflict("只查询不覆盖任务请重新执行，不支持直接重试");
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw BizException.conflict("当前接入配置已停用，不能继续重试");
        }
        if (sourceTask.getBindingId() == null || sourceTask.getBindingId() <= 0) {
            throw BizException.conflict("当前任务缺少有效绑定关系，不能直接重试");
        }
        Long latestTaskId = loadLatestTaskIdsByBinding(Set.of(sourceTask.getBindingId())).get(sourceTask.getBindingId());
        if (latestTaskId != null && !Objects.equals(latestTaskId, sourceTask.getId())) {
            throw BizException.conflict("仅最新一条失败任务支持重试");
        }
        int retryMaxCount = resolveManualRetryMaxCount(config);
        int currentRetryCount = Optional.ofNullable(sourceTask.getRetryCount()).orElse(0);
        if (currentRetryCount >= retryMaxCount) {
            if (retryMaxCount <= 0) {
                throw BizException.conflict("当前配置未开放手工重试");
            }
            throw BizException.conflict("已达到最大手工重试次数，不允许继续重试");
        }
    }

    private int resolveManualRetryMaxCount(IntegrationModuleConfig config) {
        return Math.max(Optional.ofNullable(config == null ? null : config.getRetryMaxCount()).orElse(0), 0);
    }

    private Map<Long, Long> loadLatestTaskIdsByBinding(Set<Long> bindingIds) {
        if (bindingIds == null || bindingIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = bindingIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT binding_id AS bindingId, MAX(id) AS latestId FROM sys_integration_sync_task " +
                        "WHERE deleted = 0 AND binding_id IN (" + placeholders + ") GROUP BY binding_id",
                bindingIds.toArray()
        );
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(asLong(row.get("bindingId")), asLong(row.get("latestId")));
        }
        return result;
    }

    private String resolveRetryDisabledReason(IntegrationSyncTask task, IntegrationModuleConfig config, Map<Long, Long> latestTaskIdByBinding) {
        if (task == null) {
            return "任务不存在";
        }
        if (!TASK_STATUS_FAILED.equals(task.getTaskStatus())) {
            return "仅失败任务支持重试";
        }
        if (TASK_TYPE_QUERY_ONLY.equals(task.getTaskType())) {
            return "只查询不覆盖任务请重新执行，不支持直接重试";
        }
        if (task.getBindingId() == null || task.getBindingId() <= 0) {
            return "当前任务缺少有效绑定关系，不能直接重试";
        }
        if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
            return "当前接入配置已停用，不能继续重试";
        }
        Long latestTaskId = latestTaskIdByBinding.get(task.getBindingId());
        if (latestTaskId != null && !Objects.equals(latestTaskId, task.getId())) {
            return "仅最新一条失败任务支持重试";
        }
        int retryMaxCount = resolveManualRetryMaxCount(config);
        int currentRetryCount = Optional.ofNullable(task.getRetryCount()).orElse(0);
        if (currentRetryCount >= retryMaxCount) {
            return retryMaxCount <= 0 ? "当前配置未开放手工重试" : "已达到最大手工重试次数";
        }
        return null;
    }

    private Long auditSyncTaskOperation(AuditOperationType operationType, IntegrationSyncTask task, String action,
                                        String requestPayload, IntegrationExecutionResultVO result, String errorMessage) {
        String resultCode = StrUtil.isNotBlank(errorMessage)
                ? "failed"
                : result == null ? "success" : TASK_STATUS_SUCCESS.equals(result.getSyncStatus()) ? "success" : "failed";
        String message = firstNonBlank(errorMessage, result == null ? null : result.getMessage());
        Map<String, Object> afterPayload = new LinkedHashMap<>();
        afterPayload.put("taskId", task == null ? null : task.getId());
        afterPayload.put("taskNo", task == null ? null : task.getTaskNo());
        afterPayload.put("taskType", task == null ? null : task.getTaskType());
        afterPayload.put("bindingId", task == null ? null : task.getBindingId());
        afterPayload.put("syncStatus", result == null ? null : result.getSyncStatus());
        afterPayload.put("message", message);
        return auditLogService.logAndReturnId(
                AuditModule.SYS_INTEGRATION,
                operationType,
                task == null ? null : task.getId(),
                task == null ? null : task.getTaskNo(),
                action,
                requestPayload,
                safeJson(afterPayload),
                resultCode,
                message
        );
    }

    @Override
    public Page<IntegrationSyncLogVO> pageSyncLogs(IntegrationSyncLogQueryDTO query) {
        ensureIntegrationLogViewPermission();
        validateSyncLogQuery(query);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(query.getOrgId(), scope);
        LambdaQueryWrapper<IntegrationSyncLog> wrapper = new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(currentTenantId() != null, IntegrationSyncLog::getTenantId, currentTenantId())
                .eq(query.getOrgId() != null, IntegrationSyncLog::getOrgId, query.getOrgId())
                .eq(StrUtil.isNotBlank(query.getBizModule()), IntegrationSyncLog::getBizModule, query.getBizModule())
                .eq(StrUtil.isNotBlank(query.getBizScene()), IntegrationSyncLog::getBizScene, query.getBizScene())
                .eq(StrUtil.isNotBlank(query.getProviderCode()), IntegrationSyncLog::getProviderCode, query.getProviderCode())
                .eq(StrUtil.isNotBlank(query.getSyncStatus()), IntegrationSyncLog::getSyncStatus, query.getSyncStatus())
                .eq(StrUtil.isNotBlank(query.getTriggerType()), IntegrationSyncLog::getTriggerType, query.getTriggerType())
                .eq(StrUtil.isNotBlank(query.getHandleStatus()), IntegrationSyncLog::getHandleStatus, query.getHandleStatus())
                .ge(StrUtil.isNotBlank(query.getStartTime()), IntegrationSyncLog::getCreatedAt, parseSyncLogQueryTime(query.getStartTime(), "开始时间"))
                .le(StrUtil.isNotBlank(query.getEndTime()), IntegrationSyncLog::getCreatedAt, parseSyncLogQueryTime(query.getEndTime(), "结束时间"))
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .orderByDesc(IntegrationSyncLog::getId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationSyncLog::getBizNo, query.getKeyword())
                    .or().like(IntegrationSyncLog::getExternalNo, query.getKeyword())
                    .or().like(IntegrationSyncLog::getErrorMessage, query.getKeyword())
                    .or().like(IntegrationSyncLog::getResultMessage, query.getKeyword()));
        }
        applyOrgScope(wrapper, query.getOrgId(), scope);
        Page<IntegrationSyncLog> page = syncLogMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<IntegrationSyncLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(loadSyncLogVos(page.getRecords()));
        return result;
    }

    @Override
    public IntegrationSyncLogVO getSyncLog(Long id) {
        ensureIntegrationLogViewPermission();
        IntegrationSyncLog entity = getSyncLogEntity(id, true);
        return loadSyncLogVos(List.of(entity)).stream().findFirst().orElseThrow(() -> BizException.notFound("同步日志不存在"));
    }

    @Override
    public List<IntegrationSyncLogProviderOptionVO> getSyncLogProviderOptions() {
        ensureIntegrationLogViewPermission();
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        StringBuilder sql = new StringBuilder(
                "SELECT l.provider_code AS providerCode, " +
                        "COALESCE(MAX(NULLIF(l.provider_name_snapshot, '')), MAX(NULLIF(p.provider_name, '')), l.provider_code) AS providerName " +
                        "FROM sys_integration_sync_log l " +
                        "LEFT JOIN sys_integration_provider_template p ON p.deleted = 0 AND p.provider_code = l.provider_code AND p.tenant_id = l.tenant_id " +
                        "WHERE l.deleted = 0"
        );
        List<Object> args = new ArrayList<>();
        if (currentTenantId() != null) {
            sql.append(" AND l.tenant_id = ?");
            args.add(currentTenantId());
        }
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND l.org_id IN (")
                    .append(scope.getOrgIds().stream().map(item -> "?").collect(Collectors.joining(",")))
                    .append(")");
            args.addAll(scope.getOrgIds());
        }
        sql.append(" GROUP BY l.provider_code ORDER BY providerName ASC, providerCode ASC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            IntegrationSyncLogProviderOptionVO vo = new IntegrationSyncLogProviderOptionVO();
            vo.setProviderCode(rs.getString("providerCode"));
            vo.setProviderName(firstNonBlank(rs.getString("providerName"), rs.getString("providerCode")));
            return vo;
        }, args.toArray());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSyncLogHandleStatus(Long id, IntegrationSyncLogHandleDTO dto) {
        ensureIntegrationLogViewPermission();
        IntegrationSyncLog entity = getSyncLogEntity(id, true);
        String handleStatus = StrUtil.trim(dto.getHandleStatus());
        if (!ALLOWED_SYNC_LOG_HANDLE_STATUSES.contains(handleStatus)) {
            throw BizException.validationFailed("处理状态仅支持待处理、已确认、已忽略、待复核");
        }
        String handleRemark = StrUtil.trimToNull(dto.getHandleRemark());
        IntegrationSyncLog before = new IntegrationSyncLog();
        BeanUtils.copyProperties(entity, before);
        LocalDateTime handledAt = LocalDateTime.now();
        String handledByName = firstNonBlank(UserContext.getRealName(), UserContext.getUsername(), "system");
        syncLogMapper.updateById(new IntegrationSyncLog() {{
            setId(id);
            setHandleStatus(handleStatus);
            setHandleRemark(handleRemark);
            setHandledBy(firstNonNull(UserContext.getUserId(), 0L));
            setHandledByName(handledByName);
            setHandledAt(handledAt);
        }});
        entity.setHandleStatus(handleStatus);
        entity.setHandleRemark(handleRemark);
        entity.setHandledBy(firstNonNull(UserContext.getUserId(), 0L));
        entity.setHandledByName(handledByName);
        entity.setHandledAt(handledAt);
        auditLogService.log(
                AuditModule.SYS_INTEGRATION,
                AuditOperationType.UPDATE,
                entity.getId(),
                String.valueOf(entity.getTaskId()),
                "更新第三方同步日志处理状态",
                safeJson(before),
                safeJson(entity)
        );
    }

    @Override
    public Page<IntegrationCallbackLogVO> pageCallbackLogs(IntegrationCallbackLogQueryDTO query) {
        ensureIntegrationCallbackLogViewPermission();
        recoverStuckCallbackLogs();
        validateCallbackLogQuery(query);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(query.getOrgId(), scope);
        LambdaQueryWrapper<IntegrationCallbackLog> wrapper = new LambdaQueryWrapper<IntegrationCallbackLog>()
                .eq(currentTenantId() != null, IntegrationCallbackLog::getTenantId, currentTenantId())
                .eq(query.getOrgId() != null, IntegrationCallbackLog::getOrgId, query.getOrgId())
                .eq(StrUtil.isNotBlank(query.getBizModule()), IntegrationCallbackLog::getBizModule, query.getBizModule())
                .eq(StrUtil.isNotBlank(query.getBizScene()), IntegrationCallbackLog::getBizScene, query.getBizScene())
                .eq(StrUtil.isNotBlank(query.getProviderCode()), IntegrationCallbackLog::getProviderCode, query.getProviderCode())
                .eq(StrUtil.isNotBlank(query.getSignResult()), IntegrationCallbackLog::getSignResult, query.getSignResult())
                .eq(StrUtil.isNotBlank(query.getProcessStatus()), IntegrationCallbackLog::getProcessStatus, query.getProcessStatus())
                .ge(StrUtil.isNotBlank(query.getStartTime()), IntegrationCallbackLog::getCreatedAt, parseSyncLogQueryTime(query.getStartTime(), "开始时间"))
                .le(StrUtil.isNotBlank(query.getEndTime()), IntegrationCallbackLog::getCreatedAt, parseSyncLogQueryTime(query.getEndTime(), "结束时间"))
                .orderByDesc(IntegrationCallbackLog::getCreatedAt)
                .orderByDesc(IntegrationCallbackLog::getId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationCallbackLog::getBizNo, query.getKeyword())
                    .or().like(IntegrationCallbackLog::getExternalNo, query.getKeyword())
                    .or().like(IntegrationCallbackLog::getErrorMessage, query.getKeyword())
                    .or().like(IntegrationCallbackLog::getProcessResult, query.getKeyword()));
        }
        applyOrgScope(wrapper, query.getOrgId(), scope);
        Page<IntegrationCallbackLog> page = callbackLogMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<IntegrationCallbackLogVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(loadCallbackLogVos(page.getRecords()));
        return result;
    }

    @Override
    public IntegrationCallbackLogVO getCallbackLog(Long id) {
        ensureIntegrationCallbackLogViewPermission();
        recoverStuckCallbackLogs();
        IntegrationCallbackLog entity = getCallbackLogEntity(id, true);
        return loadCallbackLogVos(List.of(entity)).stream().findFirst().orElseThrow(() -> BizException.notFound("回调日志不存在"));
    }

    @Override
    public List<IntegrationSyncLogProviderOptionVO> getCallbackLogProviderOptions() {
        ensureIntegrationCallbackLogViewPermission();
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        StringBuilder sql = new StringBuilder(
                "SELECT l.provider_code AS providerCode, " +
                        "COALESCE(MAX(NULLIF(l.provider_name_snapshot, '')), MAX(NULLIF(p.provider_name, '')), l.provider_code) AS providerName " +
                        "FROM sys_integration_callback_log l " +
                        "LEFT JOIN sys_integration_provider_template p ON p.deleted = 0 AND p.provider_code = l.provider_code AND p.tenant_id = l.tenant_id " +
                        "WHERE l.deleted = 0"
        );
        List<Object> args = new ArrayList<>();
        if (currentTenantId() != null) {
            sql.append(" AND l.tenant_id = ?");
            args.add(currentTenantId());
        }
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND l.org_id IN (")
                    .append(scope.getOrgIds().stream().map(item -> "?").collect(Collectors.joining(",")))
                    .append(")");
            args.addAll(scope.getOrgIds());
        }
        sql.append(" GROUP BY l.provider_code ORDER BY providerName ASC, providerCode ASC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            IntegrationSyncLogProviderOptionVO vo = new IntegrationSyncLogProviderOptionVO();
            vo.setProviderCode(rs.getString("providerCode"));
            vo.setProviderName(firstNonBlank(rs.getString("providerName"), rs.getString("providerCode")));
            return vo;
        }, args.toArray());
    }

    @Override
    public Page<IntegrationFileRecordVO> pageFileRecords(IntegrationFileRecordQueryDTO query) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        recoverStuckFileRecords();
        validateFileRecordQuery(query);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(query.getOrgId(), scope);
        LambdaQueryWrapper<IntegrationFileRecord> wrapper = new LambdaQueryWrapper<IntegrationFileRecord>()
                .eq(currentTenantId() != null, IntegrationFileRecord::getTenantId, currentTenantId())
                .eq(query.getOrgId() != null, IntegrationFileRecord::getOrgId, query.getOrgId())
                .eq(query.getBindingId() != null, IntegrationFileRecord::getBindingId, query.getBindingId())
                .eq(StrUtil.isNotBlank(query.getBizModule()), IntegrationFileRecord::getBizModule, query.getBizModule())
                .eq(StrUtil.isNotBlank(query.getBizScene()), IntegrationFileRecord::getBizScene, query.getBizScene())
                .eq(StrUtil.isNotBlank(query.getBizNo()), IntegrationFileRecord::getBizNo, query.getBizNo())
                .eq(StrUtil.isNotBlank(query.getProviderCode()), IntegrationFileRecord::getProviderCode, query.getProviderCode())
                .eq(StrUtil.isNotBlank(query.getDownloadStatus()), IntegrationFileRecord::getDownloadStatus, query.getDownloadStatus())
                .eq(StrUtil.isNotBlank(query.getStorageStatus()), IntegrationFileRecord::getStorageStatus, query.getStorageStatus())
                .ge(StrUtil.isNotBlank(query.getStartTime()), IntegrationFileRecord::getUpdatedAt, parseSyncLogQueryTime(query.getStartTime(), "开始时间"))
                .le(StrUtil.isNotBlank(query.getEndTime()), IntegrationFileRecord::getUpdatedAt, parseSyncLogQueryTime(query.getEndTime(), "结束时间"))
                .orderByDesc(IntegrationFileRecord::getUpdatedAt)
                .orderByDesc(IntegrationFileRecord::getId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(IntegrationFileRecord::getSourceFileName, query.getKeyword())
                    .or().like(IntegrationFileRecord::getSourceFileUrl, query.getKeyword())
                    .or().like(IntegrationFileRecord::getBizNo, query.getKeyword())
                    .or().like(IntegrationFileRecord::getErrorMessage, query.getKeyword())
                    .or().like(IntegrationFileRecord::getConfigNameSnapshot, query.getKeyword()));
        }
        applyOrgScope(wrapper, query.getOrgId(), scope);
        Page<IntegrationFileRecord> page = fileRecordMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        Page<IntegrationFileRecordVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(loadFileRecordVos(page.getRecords()));
        return result;
    }

    @Override
    public IntegrationFileRecordVO getFileRecord(Long id) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        recoverStuckFileRecords();
        IntegrationFileRecord entity = getFileRecordEntity(id, true);
        return loadFileRecordVos(List.of(entity)).stream().findFirst().orElseThrow(() -> BizException.notFound("附件转存记录不存在"));
    }

    @Override
    public List<IntegrationSyncLogProviderOptionVO> getFileRecordProviderOptions() {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        recoverStuckFileRecords();
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        StringBuilder sql = new StringBuilder(
                "SELECT l.provider_code AS providerCode, " +
                        "COALESCE(MAX(NULLIF(l.provider_name_snapshot, '')), MAX(NULLIF(p.provider_name, '')), l.provider_code) AS providerName " +
                        "FROM sys_integration_file_record l " +
                        "LEFT JOIN sys_integration_provider_template p ON p.deleted = 0 AND p.provider_code = l.provider_code AND p.tenant_id = l.tenant_id " +
                        "WHERE l.deleted = 0"
        );
        List<Object> args = new ArrayList<>();
        if (currentTenantId() != null) {
            sql.append(" AND l.tenant_id = ?");
            args.add(currentTenantId());
        }
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND l.org_id IN (")
                    .append(scope.getOrgIds().stream().map(item -> "?").collect(Collectors.joining(",")))
                    .append(")");
            args.addAll(scope.getOrgIds());
        }
        sql.append(" GROUP BY l.provider_code ORDER BY providerName ASC, providerCode ASC");
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            IntegrationSyncLogProviderOptionVO vo = new IntegrationSyncLogProviderOptionVO();
            vo.setProviderCode(rs.getString("providerCode"));
            vo.setProviderName(firstNonBlank(rs.getString("providerName"), rs.getString("providerCode")));
            return vo;
        }, args.toArray());
    }

    @Override
    public Page<IntegrationHealthCheckVO> pageHealthChecks(IntegrationHealthCheckQueryDTO query) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        validateHealthCheckQuery(query);
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        validateOrgReadable(query.getOrgId(), scope);
        if (query.getConfigId() != null) {
            IntegrationModuleConfig config = getModuleConfigEntity(query.getConfigId(), true);
            Page<IntegrationHealthCheckVO> result = new Page<>(query.getPageNum(), query.getPageSize(), 0);
            if (!matchesHealthCheckQuery(config, query)) {
                result.setRecords(Collections.emptyList());
                return result;
            }
            List<IntegrationHealthCheckLog> recentTestLogs = loadRecentHealthCheckLogsByConfig(config.getId(), 1);
            HealthCheckResult historical = buildHistoricalHealth(config, findProviderByCodeOptional(config.getProviderCode()), recentTestLogs);
            result.setTotal(1);
            result.setRecords(List.of(buildHealthCheckVO(
                    config,
                    findProviderByCodeOptional(config.getProviderCode()),
                    historical,
                    loadRecentFailedLogsByConfig(config.getId(), 3),
                    toHealthCheckLogVos(recentTestLogs)
            )));
            return result;
        }
        IntegrationModuleConfigQueryDTO configQuery = new IntegrationModuleConfigQueryDTO();
        configQuery.setKeyword(query.getKeyword());
        configQuery.setOrgId(query.getOrgId());
        configQuery.setBizModule(query.getBizModule());
        configQuery.setBizScene(query.getBizScene());
        configQuery.setProviderCode(query.getProviderCode());
        configQuery.setPageNum(query.getPageNum());
        configQuery.setPageSize(query.getPageSize());
        Page<IntegrationModuleConfigVO> configPage = pageModuleConfigs(configQuery);
        Set<Long> configIds = configPage.getRecords().stream().map(IntegrationModuleConfigVO::getId).collect(Collectors.toSet());
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(configIds);
        List<IntegrationHealthCheckVO> records = configMap.values().stream()
                .sorted(Comparator.comparing(IntegrationModuleConfig::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(config -> {
                    IntegrationProviderTemplate provider = findProviderByCodeOptional(config.getProviderCode());
                    List<IntegrationHealthCheckLog> recentTestLogs = loadRecentHealthCheckLogsByConfig(config.getId(), 1);
                    HealthCheckResult health = buildHistoricalHealth(config, provider, recentTestLogs);
                    return buildHealthCheckVO(config, provider, health, loadRecentFailedLogsByConfig(config.getId(), 3), toHealthCheckLogVos(recentTestLogs));
                }).toList();
        Page<IntegrationHealthCheckVO> result = new Page<>(configPage.getCurrent(), configPage.getSize(), configPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    public IntegrationHealthCheckVO getHealthCheck(Long configId) {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        IntegrationModuleConfig config = getModuleConfigEntity(configId, true);
        IntegrationProviderTemplate provider = findProviderByCodeOptional(config.getProviderCode());
        List<IntegrationHealthCheckLog> recentTestLogs = loadRecentHealthCheckLogsByConfig(config.getId(), HEALTH_CHECK_LOG_DETAIL_LIMIT);
        HealthCheckResult health = buildHistoricalHealth(config, provider, recentTestLogs);
        return buildHealthCheckVO(config, provider, health, loadRecentFailedLogsByConfig(config.getId(), 3), toHealthCheckLogVos(recentTestLogs));
    }

    @Override
    public List<IntegrationSyncLogProviderOptionVO> getHealthCheckProviderOptions() {
        ensureIntegrationPermission(PERMISSION_INTEGRATION_VIEW, "当前用户无第三方接入管理查看权限");
        DataScopeService.DataScopeResult scope = dataScopeService.resolveCurrentUserOrgScope();
        StringBuilder sql = new StringBuilder(
                "SELECT c.provider_code AS providerCode, " +
                        "COALESCE(MAX(NULLIF(p.provider_name, '')), c.provider_code) AS providerName " +
                        "FROM sys_integration_module_config c " +
                        "LEFT JOIN sys_integration_provider_template p ON p.deleted = 0 AND p.provider_code = c.provider_code AND p.tenant_id = c.tenant_id " +
                        "WHERE c.deleted = 0"
        );
        List<Object> args = new ArrayList<>();
        if (currentTenantId() != null) {
            sql.append(" AND c.tenant_id = ?");
            args.add(currentTenantId());
        }
        if (scope.isRestricted()) {
            if (scope.getOrgIds().isEmpty()) {
                return Collections.emptyList();
            }
            sql.append(" AND c.org_id IN (")
                    .append(scope.getOrgIds().stream().map(item -> "?").collect(Collectors.joining(",")))
                    .append(")");
            args.addAll(scope.getOrgIds());
        }
        sql.append(" GROUP BY c.provider_code ORDER BY providerName ASC, providerCode ASC LIMIT ").append(HEALTH_CHECK_PROVIDER_OPTION_LIMIT);
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            IntegrationSyncLogProviderOptionVO vo = new IntegrationSyncLogProviderOptionVO();
            vo.setProviderCode(rs.getString("providerCode"));
            vo.setProviderName(firstNonBlank(rs.getString("providerName"), rs.getString("providerCode")));
            return vo;
        }, args.toArray());
    }

    @Override
    public IntegrationInternalSceneMetaVO getInternalSceneMeta(IntegrationInternalBizBindingQueryDTO dto) {
        validateInternalReadable(dto.getTenantId(), dto.getOrgId());
        List<IntegrationModuleConfig> configs = loadInternalConfigs(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene());
        List<IntegrationBinding> bindings = loadBindingsByBiz(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getBizId());
        IntegrationBinding currentBinding = selectCurrentBinding(bindings, dto.getConfigId());
        IntegrationModuleConfig selectedConfig = resolveSelectedConfig(configs, currentBinding, dto.getConfigId());
        Set<String> providerCodes = configs.stream()
                .map(IntegrationModuleConfig::getProviderCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (currentBinding != null && StrUtil.isNotBlank(currentBinding.getProviderCode())) {
            providerCodes.add(currentBinding.getProviderCode());
        }
        Map<String, String> providerNameMap = loadProviderNameMap(providerCodes);
        Map<Long, IntegrationModuleConfig> configMap = configs.stream()
                .collect(Collectors.toMap(IntegrationModuleConfig::getId, item -> item, (a, b) -> a, LinkedHashMap::new));

        IntegrationInternalSceneMetaVO meta = new IntegrationInternalSceneMetaVO();
        meta.setBizModule(dto.getBizModule());
        meta.setBizScene(dto.getBizScene());
        meta.setBizId(dto.getBizId());
        meta.setOrgId(dto.getOrgId());
        meta.setTenantId(dto.getTenantId());
        meta.setSelectedConfigId(currentBinding != null
                ? currentBinding.getConfigId()
                : selectedConfig == null ? null : selectedConfig.getId());
        meta.setDefaultMode(firstNonBlank(selectedConfig == null ? null : selectedConfig.getDefaultMode(), MODE_MANUAL));
        meta.setAllowDocumentSwitch(selectedConfig == null ? 1 : Optional.ofNullable(selectedConfig.getAllowDocumentSwitch()).orElse(1));
        meta.setForceThirdParty(selectedConfig == null ? 0 : Optional.ofNullable(selectedConfig.getForceThirdParty()).orElse(0));
        meta.setAllowManualFallback(selectedConfig == null ? 0 : Optional.ofNullable(selectedConfig.getAllowManualFallback()).orElse(0));
        meta.setAutoCoverEnabled(selectedConfig == null ? 0 : Optional.ofNullable(selectedConfig.getAutoCoverEnabled()).orElse(0));
        meta.setExternalNoFieldRule(selectedConfig == null ? null : selectedConfig.getExternalNoFieldRule());
        meta.setConfigOptions(configs.stream()
                .map(item -> toInternalModuleOptionVO(item, providerNameMap.get(item.getProviderCode())))
                .toList());
        if (currentBinding != null) {
            meta.setCurrentBinding(toInternalBindingSummary(currentBinding, configMap, providerNameMap));
        }
        int logLimit = normalizeInternalLogLimit(dto.getLogLimit());
        meta.setRecentSyncLogs(loadInternalSyncLogs(dto, currentBinding, providerNameMap, logLimit));
        meta.setRecentCallbackLogs(loadInternalCallbackLogs(dto, currentBinding, providerNameMap, logLimit));
        return meta;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationInternalTriggerResultVO triggerInternalSync(IntegrationInternalTriggerSyncDTO dto) {
        validateInternalWritable(dto.getTenantId(), dto.getOrgId());
        IntegrationModuleConfig config = getInternalModuleConfigEntity(dto.getConfigId(), dto.getTenantId(), dto.getOrgId());
        if (!Objects.equals(config.getBizModule(), dto.getBizModule()) || !Objects.equals(config.getBizScene(), dto.getBizScene())) {
            throw BizException.validationFailed("业务模块或场景与配置不匹配");
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            throw BizException.conflict("当前接入配置未启用");
        }
        validateRequestedMaintenanceMode(config, dto.getMaintenanceMode());
        return withSyntheticUserContext(dto.getOperatorId(), dto.getOperatorName(), dto.getOperatorUsername(), dto.getOrgId(), dto.getTenantId(), () -> {
            List<IntegrationBinding> bindings = loadBindingsByBiz(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getBizId());
            IntegrationBinding currentBinding = selectCurrentBinding(bindings, dto.getConfigId());
            String maintenanceMode = normalizeMode(dto.getMaintenanceMode(), config);
            ensureBindingSwitchAllowed(currentBinding, config, maintenanceMode, dto.getTenantId(), dto.getOrgId());
            if (MODE_MANUAL.equals(maintenanceMode) && (dto.getQueryOnly() == null || dto.getQueryOnly() != 1)) {
                throw BizException.validationFailed("手工模式仅支持只查询不覆盖");
            }
            IntegrationSyncTaskTriggerDTO triggerDTO = new IntegrationSyncTaskTriggerDTO();
            triggerDTO.setConfigId(config.getId());
            triggerDTO.setBizModule(dto.getBizModule());
            triggerDTO.setBizScene(dto.getBizScene());
            triggerDTO.setBizId(String.valueOf(dto.getBizId()));
            triggerDTO.setBizNo(dto.getBizNo());
            triggerDTO.setExternalNo(dto.getExternalNo());
            triggerDTO.setMaintenanceMode(maintenanceMode);
            triggerDTO.setModeSource(firstNonBlank(dto.getModeSource(), "business_scene"));
            triggerDTO.setModeLocked(Optional.ofNullable(dto.getModeLocked()).orElse(0));
            triggerDTO.setTriggerType(firstNonBlank(dto.getTriggerType(), "manual"));
            triggerDTO.setQueryOnly(Optional.ofNullable(dto.getQueryOnly()).orElse(0));
            IntegrationBinding binding = upsertBinding(config, triggerDTO, maintenanceMode);
            if (MODE_THIRD_PARTY.equals(maintenanceMode)) {
                deactivateOtherBindings(binding);
            } else {
                clearBindingNextSyncAt(binding.getId(), binding.getRemark());
            }
            IntegrationSyncTask task = createTask(
                    config,
                    binding,
                    TASK_TYPE_SYNC,
                    triggerDTO.getTriggerType(),
                    0,
                    firstNonNull(dto.getOperatorId(), 0L),
                    firstNonBlank(dto.getOperatorName(), dto.getOperatorUsername(), "system")
            );
            IntegrationExecutionResultVO executionResult = executeTask(task, config, binding, null, null, TaskExecutionOptions.standard());
            IntegrationInternalTriggerResultVO result = new IntegrationInternalTriggerResultVO();
            result.setExecutionResult(executionResult);
            result.setBinding(loadInternalBindingSummary(binding.getId()));
            return result;
        });
    }

    @Override
    public IntegrationInternalLogsVO getInternalLogs(IntegrationInternalBizBindingQueryDTO dto) {
        validateInternalReadable(dto.getTenantId(), dto.getOrgId());
        List<IntegrationBinding> bindings = loadBindingsByBiz(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getBizId());
        IntegrationBinding currentBinding = selectCurrentBinding(bindings, dto.getConfigId());
        Set<String> providerCodes = bindings.stream()
                .map(IntegrationBinding::getProviderCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> providerNameMap = loadProviderNameMap(providerCodes);
        IntegrationInternalLogsVO result = new IntegrationInternalLogsVO();
        if (currentBinding != null) {
            Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(Set.of(currentBinding.getConfigId()));
            result.setBinding(toInternalBindingSummary(currentBinding, configMap, providerNameMap));
        }
        int logLimit = normalizeInternalLogLimit(dto.getLogLimit());
        result.setSyncLogs(loadInternalSyncLogs(dto, currentBinding, providerNameMap, logLimit));
        result.setCallbackLogs(loadInternalCallbackLogs(dto, currentBinding, providerNameMap, logLimit));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationInternalBindingSummaryVO switchInternalBindingMode(IntegrationInternalSwitchModeDTO dto) {
        validateInternalWritable(dto.getTenantId(), dto.getOrgId());
        return withSyntheticUserContext(dto.getOperatorId(), dto.getOperatorName(), dto.getOperatorUsername(), dto.getOrgId(), dto.getTenantId(), () -> {
            List<IntegrationBinding> bindings = loadBindingsByBiz(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getBizId());
            String beforeData = safeJson(bindings);
            String maintenanceMode = normalizeManualOrThirdParty(dto.getMaintenanceMode());
            IntegrationBinding currentBinding = selectCurrentBinding(bindings, dto.getConfigId());
            if (MODE_MANUAL.equals(maintenanceMode)) {
                if (bindings.isEmpty() || currentBinding == null) {
                    return null;
                }
                IntegrationModuleConfig currentConfig = getInternalModuleConfigEntity(currentBinding.getConfigId(), dto.getTenantId(), dto.getOrgId());
                ensureBindingSwitchAllowed(currentBinding, currentConfig, MODE_MANUAL, dto.getTenantId(), dto.getOrgId());
                LambdaQueryWrapper<IntegrationBinding> queryWrapper = new LambdaQueryWrapper<IntegrationBinding>()
                        .eq(IntegrationBinding::getTenantId, dto.getTenantId())
                        .eq(IntegrationBinding::getOrgId, dto.getOrgId())
                        .eq(IntegrationBinding::getBizModule, dto.getBizModule())
                        .eq(IntegrationBinding::getBizScene, dto.getBizScene())
                        .eq(IntegrationBinding::getBizId, dto.getBizId());
                List<IntegrationBinding> allBindings = bindingMapper.selectList(queryWrapper);
                for (IntegrationBinding binding : allBindings) {
                    binding.setMaintenanceMode(MODE_MANUAL);
                    binding.setModeSource(firstNonBlank(dto.getModeSource(), "business_scene"));
                    bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationBinding>()
                            .eq(IntegrationBinding::getId, binding.getId())
                            .set(IntegrationBinding::getMaintenanceMode, MODE_MANUAL)
                            .set(IntegrationBinding::getModeSource, firstNonBlank(dto.getModeSource(), "business_scene"))
                            .set(IntegrationBinding::getNextSyncAt, null));
                }
            } else {
                if (dto.getConfigId() == null) {
                    throw BizException.badRequest("请选择同步方案");
                }
                String externalNo = StrUtil.trimToNull(dto.getExternalNo());
                if (StrUtil.isBlank(externalNo)) {
                    throw BizException.badRequest("请填写第三方外部单号");
                }
                String bizNo = firstNonBlank(StrUtil.trimToNull(dto.getBizNo()), currentBinding == null ? null : currentBinding.getBizNo());
                if (StrUtil.isBlank(bizNo)) {
                    throw BizException.badRequest("业务编号不能为空");
                }
                IntegrationModuleConfig currentConfig = getInternalModuleConfigEntity(dto.getConfigId(), dto.getTenantId(), dto.getOrgId());
                ensureBindingSwitchAllowed(currentBinding, currentConfig, MODE_THIRD_PARTY, dto.getTenantId(), dto.getOrgId());
                IntegrationSyncTaskTriggerDTO triggerDTO = new IntegrationSyncTaskTriggerDTO();
                triggerDTO.setConfigId(currentConfig.getId());
                triggerDTO.setBizModule(dto.getBizModule());
                triggerDTO.setBizScene(dto.getBizScene());
                triggerDTO.setBizId(String.valueOf(dto.getBizId()));
                triggerDTO.setBizNo(bizNo);
                triggerDTO.setExternalNo(externalNo);
                triggerDTO.setModeSource(firstNonBlank(dto.getModeSource(), "business_scene"));
                currentBinding = upsertBinding(currentConfig, triggerDTO, MODE_THIRD_PARTY);
                deactivateOtherBindings(currentBinding);
            }
            List<IntegrationBinding> latestBindings = loadBindingsByBiz(dto.getTenantId(), dto.getOrgId(), dto.getBizModule(), dto.getBizScene(), dto.getBizId());
            auditLogService.log(
                    AuditModule.SYS_INTEGRATION,
                    AuditOperationType.UPDATE,
                    dto.getBizId(),
                    firstNonBlank(dto.getBizNo(), currentBinding.getBizNo()),
                    "采购订单场景第三方绑定切换为" + (MODE_THIRD_PARTY.equals(maintenanceMode) ? "第三方接口" : "手工录入"),
                    beforeData,
                    safeJson(latestBindings)
            );
            return loadInternalBindingSummary(currentBinding.getId());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IntegrationCallbackHandleResultVO handleCallback(String providerCode, String payload, HttpServletRequest request) {
        ensureBuiltinProviders();
        String normalizedPayload = StrUtil.blankToDefault(payload, writeJson(new LinkedHashMap<>(request.getParameterMap())));
        String logPayload = normalizeCallbackPayloadForStorage(normalizedPayload);
        String idempotentKey = firstNonBlank(
                request.getHeader("X-Idempotent-Key"),
                request.getHeader("X-Request-Id"),
                request.getParameter("requestId"),
                sha256(normalizedPayload)
        );
        JsonNode body = readTree(normalizedPayload);
        List<IntegrationModuleConfig> candidateConfigs = resolveCallbackCandidateConfigs(providerCode, body, request);
        Map<Long, IntegrationModuleConfig> candidateConfigMap = candidateConfigs.stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(IntegrationModuleConfig::getId, item -> item, (left, right) -> left, LinkedHashMap::new));
        String externalNo = firstNonBlank(
                findString(body, "externalNo"),
                findString(body, "mailNo"),
                findString(body, "trackingNo"),
                findString(body, "reportNo"),
                request.getParameter("externalNo")
        );
        CallbackBindingMatchResult bindingMatch = findBindingForCallback(candidateConfigs, providerCode, externalNo, body, request);
        IntegrationBinding binding = bindingMatch.binding();
        IntegrationModuleConfig bindingConfig = binding == null ? null : candidateConfigMap.get(binding.getConfigId());
        CallbackSecurityResult securityResult = verifyCallbackSecurity(binding, bindingConfig, candidateConfigs, normalizedPayload, request);
        IntegrationModuleConfig attributedConfig = resolveCallbackAttributionConfig(bindingConfig, securityResult.matchedConfig(), candidateConfigs);
        IntegrationCallbackLog callbackLog = new IntegrationCallbackLog();
        callbackLog.setProviderCode(providerCode);
        callbackLog.setProviderNameSnapshot(resolveProviderNameSnapshot(providerCode));
        callbackLog.setCallbackUri(request.getRequestURI());
        callbackLog.setCallbackHeaders(collectHeaders(request));
        callbackLog.setCallbackPayload(logPayload);
        callbackLog.setClientIp(extractClientIp(request));
        callbackLog.setIdempotentKey(idempotentKey);
        callbackLog.setClaimKey(buildCallbackClaimKey(binding, attributedConfig, providerCode, idempotentKey));
        callbackLog.setProcessStatus("pending");
        if (binding != null) {
            callbackLog.setBindingId(binding.getId());
            callbackLog.setConfigId(binding.getConfigId());
            callbackLog.setBizId(binding.getBizId());
            callbackLog.setBizNo(binding.getBizNo());
            callbackLog.setBizModule(binding.getBizModule());
            callbackLog.setBizScene(binding.getBizScene());
            callbackLog.setExternalNo(binding.getExternalNo());
            callbackLog.setOrgId(binding.getOrgId());
            callbackLog.setTenantId(binding.getTenantId());
        } else {
            callbackLog.setExternalNo(externalNo);
        }
        applyCallbackLogAttribution(callbackLog, attributedConfig);
        callbackLog.setSignResult(securityResult.passed() ? "pass" : "fail");
        if (!securityResult.passed()) {
            callbackLog.setProcessStatus("security_failed");
            callbackLog.setErrorMessage(securityResult.message());
            callbackLog.setProcessResult(securityResult.message());
            try {
                callbackLogMapper.insert(callbackLog);
                return buildCallbackHandleResult(callbackLog);
            } catch (DuplicateKeyException ex) {
                return insertDuplicateCallbackLog(callbackLog, "重复回调已忽略");
            }
        }
        if (callbackExists(callbackLog.getClaimKey())) {
            return insertDuplicateCallbackLog(callbackLog, "重复回调已忽略");
        }
        try {
            callbackLogMapper.insert(callbackLog);
        } catch (DuplicateKeyException ex) {
            return insertDuplicateCallbackLog(callbackLog, "重复回调已忽略");
        }
        if (binding == null) {
            callbackLog.setProcessStatus("ignored");
            callbackLog.setProcessResult(firstNonBlank(bindingMatch.reason(), "未匹配到绑定关系"));
            callbackLogMapper.updateById(callbackLog);
            return buildCallbackHandleResult(callbackLog);
        }
        IntegrationModuleConfig callbackConfig = firstNonNull(bindingConfig, firstNonNull(attributedConfig, getCallbackModuleConfigEntity(binding)));
        if (callbackConfig.getEnabled() == null || callbackConfig.getEnabled() != 1) {
            callbackLog.setProcessStatus("ignored");
            callbackLog.setProcessResult("当前模块接入配置已停用，回调已忽略");
            callbackLogMapper.updateById(callbackLog);
            return buildCallbackHandleResult(callbackLog);
        }
        if (callbackConfig.getCallbackEnabled() == null || callbackConfig.getCallbackEnabled() != 1) {
            callbackLog.setProcessStatus("ignored");
            callbackLog.setProcessResult("当前模块接入配置未启用回调，回调已忽略");
            callbackLogMapper.updateById(callbackLog);
            return buildCallbackHandleResult(callbackLog);
        }
        IntegrationExecutionResultVO execution = withSyntheticUserContext(0L, "callback", "callback", binding.getOrgId(), binding.getTenantId(), () -> {
            IntegrationSyncTask task = createTask(callbackConfig, binding, TASK_TYPE_CALLBACK_FOLLOWUP, "callback", 0, 0L, "callback");
            return executeTask(task, callbackConfig, binding, null, body, TaskExecutionOptions.standard());
        });
        callbackLog.setTaskId(execution.getTaskId());
        callbackLog.setSyncLogId(execution.getSyncLogId());
        callbackLog.setAuditLogId(execution.getAuditLogId());
        callbackLog.setProcessStatus(normalizeCallbackProcessStatus(execution.getSyncStatus()));
        callbackLog.setProcessResult(execution.getMessage());
        callbackLog.setErrorMessage(TASK_STATUS_SUCCESS.equals(execution.getSyncStatus()) ? null : execution.getMessage());
        callbackLogMapper.updateById(callbackLog);
        return buildCallbackHandleResult(callbackLog);
    }

    private IntegrationCallbackHandleResultVO insertDuplicateCallbackLog(IntegrationCallbackLog source, String message) {
        IntegrationCallbackLog duplicateLog = new IntegrationCallbackLog();
        BeanUtils.copyProperties(source, duplicateLog);
        duplicateLog.setId(null);
        duplicateLog.setClaimKey(null);
        duplicateLog.setProcessStatus("duplicate");
        duplicateLog.setProcessResult(message);
        duplicateLog.setErrorMessage(null);
        callbackLogMapper.insert(duplicateLog);
        return buildCallbackHandleResult(duplicateLog);
    }

    private IntegrationCallbackHandleResultVO buildCallbackHandleResult(IntegrationCallbackLog callbackLog) {
        IntegrationCallbackHandleResultVO result = new IntegrationCallbackHandleResultVO();
        result.setCallbackLogId(callbackLog.getId());
        result.setProcessStatus(callbackLog.getProcessStatus());
        result.setMessage(firstNonBlank(callbackLog.getProcessResult(), callbackLog.getErrorMessage(), callbackLog.getProcessStatus()));
        result.setTaskId(callbackLog.getTaskId());
        result.setSyncLogId(callbackLog.getSyncLogId());
        result.setAuditLogId(callbackLog.getAuditLogId());
        return result;
    }

    private void applyCallbackLogAttribution(IntegrationCallbackLog callbackLog, IntegrationModuleConfig config) {
        if (callbackLog == null || config == null) {
            return;
        }
        callbackLog.setConfigId(firstNonNull(callbackLog.getConfigId(), config.getId()));
        callbackLog.setConfigNameSnapshot(firstNonBlank(callbackLog.getConfigNameSnapshot(), config.getConfigName()));
        callbackLog.setProviderNameSnapshot(firstNonBlank(callbackLog.getProviderNameSnapshot(), resolveProviderNameSnapshot(config.getProviderCode())));
        callbackLog.setProviderCode(firstNonBlank(callbackLog.getProviderCode(), config.getProviderCode()));
        callbackLog.setOrgId(firstNonNull(callbackLog.getOrgId(), config.getOrgId()));
        callbackLog.setTenantId(firstNonNull(callbackLog.getTenantId(), config.getTenantId()));
        callbackLog.setOrgNameSnapshot(firstNonBlank(callbackLog.getOrgNameSnapshot(), resolveOrgNameSnapshot(config.getOrgId())));
    }

    private IntegrationModuleConfig resolveCallbackAttributionConfig(IntegrationModuleConfig bindingConfig,
                                                                     IntegrationModuleConfig matchedSecurityConfig,
                                                                     List<IntegrationModuleConfig> candidateConfigs) {
        if (bindingConfig != null) {
            return bindingConfig;
        }
        if (matchedSecurityConfig != null) {
            return matchedSecurityConfig;
        }
        if (candidateConfigs == null || candidateConfigs.size() != 1) {
            return null;
        }
        return candidateConfigs.get(0);
    }

    private String buildCallbackClaimKey(IntegrationBinding binding, IntegrationModuleConfig config,
                                         String providerCode, String idempotentKey) {
        String scope = binding != null && binding.getId() != null
                ? "binding:" + binding.getId()
                : config != null && config.getId() != null
                ? "config:" + config.getId()
                : "provider:" + firstNonBlank(providerCode, "unknown");
        return scope + "|idempotent:" + firstNonBlank(idempotentKey, "none");
    }

    private String normalizeCallbackProcessStatus(String syncStatus) {
        if (TASK_STATUS_SUCCESS.equals(syncStatus) || TASK_STATUS_NO_DATA.equals(syncStatus) || TASK_STATUS_MAPPING_MISSING.equals(syncStatus)) {
            return syncStatus;
        }
        return TASK_STATUS_FAILED;
    }

    private String normalizeCallbackPayloadForStorage(String payload) {
        Object sanitized = sanitizeLogValue(readTree(payload), null);
        return safeJson(sanitized);
    }

    private List<IntegrationModuleConfig> resolveCallbackCandidateConfigs(String providerCode, JsonNode body, HttpServletRequest request) {
        Long configIdHint = asLong(firstNonBlank(
                request.getHeader("X-Integration-Config-Id"),
                request.getHeader("X-Config-Id"),
                request.getParameter("configId"),
                request.getParameter("integrationConfigId"),
                findString(body, "configId"),
                findString(body, "integrationConfigId"),
                findString(body, "moduleConfigId")
        ));
        Long tenantIdHint = asLong(firstNonBlank(
                request.getHeader("X-Tenant-Id"),
                request.getParameter("tenantId"),
                findString(body, "tenantId")
        ));
        Long orgIdHint = asLong(firstNonBlank(
                request.getHeader("X-Org-Id"),
                request.getParameter("orgId"),
                request.getParameter("organizationId"),
                findString(body, "orgId"),
                findString(body, "organizationId")
        ));
        String bizModuleHint = firstNonBlank(
                request.getHeader("X-Biz-Module"),
                request.getParameter("bizModule"),
                findString(body, "bizModule")
        );
        String bizSceneHint = firstNonBlank(
                request.getHeader("X-Biz-Scene"),
                request.getParameter("bizScene"),
                findString(body, "bizScene"),
                findString(body, "scene")
        );
        String requestUri = request.getRequestURI();
        return moduleConfigMapper.selectList(new LambdaQueryWrapper<IntegrationModuleConfig>()
                        .eq(IntegrationModuleConfig::getProviderCode, providerCode)
                        .eq(IntegrationModuleConfig::getEnabled, 1)
                        .eq(IntegrationModuleConfig::getCallbackEnabled, 1)
                        .eq(configIdHint != null, IntegrationModuleConfig::getId, configIdHint)
                        .eq(tenantIdHint != null, IntegrationModuleConfig::getTenantId, tenantIdHint)
                        .eq(orgIdHint != null, IntegrationModuleConfig::getOrgId, orgIdHint)
                        .eq(StrUtil.isNotBlank(bizModuleHint), IntegrationModuleConfig::getBizModule, bizModuleHint)
                        .eq(StrUtil.isNotBlank(bizSceneHint), IntegrationModuleConfig::getBizScene, bizSceneHint))
                .stream()
                .filter(config -> StrUtil.isBlank(config.getCallbackUrl()) || callbackPathMatches(requestUri, config.getCallbackUrl()))
                .toList();
    }

    private CallbackBindingMatchResult findBindingForCallback(List<IntegrationModuleConfig> candidateConfigs, String providerCode,
                                                              String externalNo, JsonNode body, HttpServletRequest request) {
        Set<Long> candidateConfigIds = candidateConfigs.stream()
                .map(IntegrationModuleConfig::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (candidateConfigIds.isEmpty()) {
            return new CallbackBindingMatchResult(null, "未找到启用回调的模块接入配置");
        }
        String bizNo = resolveCallbackBizNo(body);
        Long configIdHint = asLong(firstNonBlank(
                request.getHeader("X-Integration-Config-Id"),
                request.getHeader("X-Config-Id"),
                request.getParameter("configId"),
                request.getParameter("integrationConfigId"),
                findString(body, "configId"),
                findString(body, "integrationConfigId"),
                findString(body, "moduleConfigId")
        ));
        Long tenantIdHint = asLong(firstNonBlank(
                request.getHeader("X-Tenant-Id"),
                request.getParameter("tenantId"),
                findString(body, "tenantId")
        ));
        Long orgIdHint = asLong(firstNonBlank(
                request.getHeader("X-Org-Id"),
                request.getParameter("orgId"),
                request.getParameter("organizationId"),
                findString(body, "orgId"),
                findString(body, "organizationId")
        ));
        String bizModuleHint = firstNonBlank(
                request.getHeader("X-Biz-Module"),
                request.getParameter("bizModule"),
                findString(body, "bizModule")
        );
        String bizSceneHint = firstNonBlank(
                request.getHeader("X-Biz-Scene"),
                request.getParameter("bizScene"),
                findString(body, "bizScene"),
                findString(body, "scene")
        );
        if (StrUtil.isBlank(externalNo) && StrUtil.isBlank(bizNo)) {
            return new CallbackBindingMatchResult(null, "回调缺少外部单号或业务编号，无法匹配绑定关系");
        }
        if (StrUtil.isNotBlank(externalNo)) {
            List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                    .in(IntegrationBinding::getConfigId, candidateConfigIds)
                    .eq(IntegrationBinding::getProviderCode, providerCode)
                    .eq(configIdHint != null, IntegrationBinding::getConfigId, configIdHint)
                    .eq(tenantIdHint != null, IntegrationBinding::getTenantId, tenantIdHint)
                    .eq(orgIdHint != null, IntegrationBinding::getOrgId, orgIdHint)
                    .eq(StrUtil.isNotBlank(bizModuleHint), IntegrationBinding::getBizModule, bizModuleHint)
                    .eq(StrUtil.isNotBlank(bizSceneHint), IntegrationBinding::getBizScene, bizSceneHint)
                    .eq(IntegrationBinding::getExternalNo, externalNo)
                    .orderByDesc(IntegrationBinding::getUpdatedAt)
                    .orderByDesc(IntegrationBinding::getId)
                    .last("LIMIT 2"));
            if (bindings.size() == 1) {
                return new CallbackBindingMatchResult(bindings.get(0), null);
            }
            if (bindings.size() > 1) {
                return new CallbackBindingMatchResult(null, "回调命中了多条绑定关系，请补充组织/配置唯一标识");
            }
        }
        if (StrUtil.isNotBlank(bizNo)) {
            List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                    .in(IntegrationBinding::getConfigId, candidateConfigIds)
                    .eq(IntegrationBinding::getProviderCode, providerCode)
                    .eq(configIdHint != null, IntegrationBinding::getConfigId, configIdHint)
                    .eq(tenantIdHint != null, IntegrationBinding::getTenantId, tenantIdHint)
                    .eq(orgIdHint != null, IntegrationBinding::getOrgId, orgIdHint)
                    .eq(StrUtil.isNotBlank(bizModuleHint), IntegrationBinding::getBizModule, bizModuleHint)
                    .eq(StrUtil.isNotBlank(bizSceneHint), IntegrationBinding::getBizScene, bizSceneHint)
                    .eq(IntegrationBinding::getBizNo, bizNo)
                    .orderByDesc(IntegrationBinding::getUpdatedAt)
                    .orderByDesc(IntegrationBinding::getId)
                    .last("LIMIT 2"));
            if (bindings.size() == 1) {
                return new CallbackBindingMatchResult(bindings.get(0), null);
            }
            if (bindings.size() > 1) {
                return new CallbackBindingMatchResult(null, "回调命中了多条绑定关系，请补充组织/配置唯一标识");
            }
        }
        return new CallbackBindingMatchResult(null, "未匹配到绑定关系");
    }

    @Scheduled(
            initialDelayString = "${integration.binding.scheduler.initial-delay-ms:30000}",
            fixedDelayString = "${integration.binding.scheduler.delay-ms:60000}"
    )
    public void executeDueBindingsOnSchedule() {
        try {
            executeDueBindings(20);
        } catch (Exception ex) {
            log.error("执行第三方绑定定时续同步失败", ex);
        }
    }

    private void executeDueBindings(int batchSize) {
        recoverStuckTasks();
        recoverStuckCallbackLogs();
        List<IntegrationBinding> dueBindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getMaintenanceMode, MODE_THIRD_PARTY)
                .isNotNull(IntegrationBinding::getNextSyncAt)
                .le(IntegrationBinding::getNextSyncAt, LocalDateTime.now())
                .orderByAsc(IntegrationBinding::getNextSyncAt)
                .orderByAsc(IntegrationBinding::getId)
                .last("LIMIT " + Math.max(1, Math.min(batchSize, 100))));
        if (dueBindings.isEmpty()) {
            return;
        }
        for (IntegrationBinding binding : dueBindings) {
            if (!tryClaimBindingForSchedule(binding)) {
                continue;
            }
            try {
                executeScheduledBinding(binding);
            } catch (Exception ex) {
                log.error("执行第三方绑定续同步失败, bindingId={}", binding.getId(), ex);
            }
        }
    }

    private void executeScheduledBinding(IntegrationBinding binding) {
        IntegrationModuleConfig config = getInternalModuleConfigEntity(binding.getConfigId(), binding.getTenantId(), binding.getOrgId());
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            stopBindingSchedule(binding, "模块配置已停用");
            return;
        }
        if (!TRIGGER_STRATEGY_SCHEDULER.equals(firstNonBlank(config.getTriggerStrategy(), MODE_MANUAL))) {
            stopBindingSchedule(binding, "当前配置未启用定时轮询");
            return;
        }
        IntegrationProviderTemplate provider = withSyntheticUserContext(0L, "integration-scheduler", "integration-scheduler", binding.getOrgId(), binding.getTenantId(),
                () -> getProviderByCode(config.getProviderCode()));
        if (!"active".equals(provider.getStatus())) {
            stopBindingSchedule(binding, BUILTIN_PROVIDER_STATUS_STOP_MESSAGE);
            updateConfigHealthStatus(config.getId(), TASK_STATUS_FAILED, PROVIDER_RUNTIME_DISABLED_ERROR);
            return;
        }
        if (!isBindingStillSchedulable(binding)) {
            stopBindingSchedule(binding, "业务对象当前已不再允许继续第三方同步");
            return;
        }
        withSyntheticUserContext(0L, "integration-scheduler", "integration-scheduler", binding.getOrgId(), binding.getTenantId(), () -> {
            IntegrationSyncTask task = createTask(config, binding, TASK_TYPE_SYNC, "scheduler", 0, 0L, "integration-scheduler");
            executeTask(task, config, binding, null, null, TaskExecutionOptions.standard());
            return null;
        });
    }

    private void stopBindingSchedule(IntegrationBinding binding, String reason) {
        clearBindingNextSyncAt(binding.getId(), reason);
    }

    private boolean tryClaimBindingForSchedule(IntegrationBinding binding) {
        if (binding == null || binding.getId() == null || binding.getNextSyncAt() == null) {
            return false;
        }
        LocalDateTime leaseUntil = LocalDateTime.now().plusSeconds(SCHEDULE_CLAIM_LEASE_SECONDS);
        int updated = bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getId, binding.getId())
                .eq(IntegrationBinding::getMaintenanceMode, MODE_THIRD_PARTY)
                .eq(IntegrationBinding::getNextSyncAt, binding.getNextSyncAt())
                .set(IntegrationBinding::getNextSyncAt, leaseUntil));
        if (updated > 0) {
            binding.setNextSyncAt(leaseUntil);
            return true;
        }
        return false;
    }

    private void recoverStuckTasks() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(STUCK_TASK_TIMEOUT_MINUTES);
        List<IntegrationSyncTask> stuckTasks = syncTaskMapper.selectList(new LambdaQueryWrapper<IntegrationSyncTask>()
                .eq(currentTenantId() != null, IntegrationSyncTask::getTenantId, currentTenantId())
                .in(IntegrationSyncTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_RUNNING)
                .and(wrapper -> wrapper
                        .le(IntegrationSyncTask::getPlanExecuteAt, cutoff)
                        .or()
                        .le(IntegrationSyncTask::getStartAt, cutoff))
                .orderByAsc(IntegrationSyncTask::getId)
                .last("LIMIT 50"));
        for (IntegrationSyncTask task : stuckTasks) {
            try {
                markStuckTaskFailed(task, now);
            } catch (Exception ex) {
                log.error("回收卡死同步任务失败, taskId={}", task.getId(), ex);
            }
        }
    }

    private void recoverStuckCallbackLogs() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(STUCK_CALLBACK_LOG_TIMEOUT_MINUTES);
        List<IntegrationCallbackLog> stuckLogs = callbackLogMapper.selectList(new LambdaQueryWrapper<IntegrationCallbackLog>()
                .eq(currentTenantId() != null, IntegrationCallbackLog::getTenantId, currentTenantId())
                .eq(IntegrationCallbackLog::getProcessStatus, "pending")
                .le(IntegrationCallbackLog::getCreatedAt, cutoff)
                .orderByAsc(IntegrationCallbackLog::getId)
                .last("LIMIT 50"));
        for (IntegrationCallbackLog logEntity : stuckLogs) {
            try {
                markStuckCallbackLogFailed(logEntity, now);
            } catch (Exception ex) {
                log.error("回收卡死回调日志失败, callbackLogId={}", logEntity.getId(), ex);
            }
        }
    }

    private void recoverStuckFileRecords() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusMinutes(STUCK_FILE_RECORD_TIMEOUT_MINUTES);
        List<IntegrationFileRecord> stuckRecords = fileRecordMapper.selectList(new LambdaQueryWrapper<IntegrationFileRecord>()
                .eq(currentTenantId() != null, IntegrationFileRecord::getTenantId, currentTenantId())
                .and(wrapper -> wrapper
                        .eq(IntegrationFileRecord::getDownloadStatus, FILE_RECORD_STATUS_PENDING)
                        .or()
                        .eq(IntegrationFileRecord::getStorageStatus, FILE_RECORD_STATUS_PENDING))
                .le(IntegrationFileRecord::getUpdatedAt, cutoff)
                .orderByAsc(IntegrationFileRecord::getId)
                .last("LIMIT 50"));
        for (IntegrationFileRecord record : stuckRecords) {
            try {
                markStuckFileRecordFailed(record, now);
            } catch (Exception ex) {
                log.error("回收卡死附件转存记录失败, fileRecordId={}", record.getId(), ex);
            }
        }
    }

    private void markStuckCallbackLogFailed(IntegrationCallbackLog logEntity, LocalDateTime now) {
        String message = "回调处理中断或服务异常退出，系统已自动收口，请人工复核本次回调";
        int updated = callbackLogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationCallbackLog>()
                .eq(IntegrationCallbackLog::getId, logEntity.getId())
                .eq(IntegrationCallbackLog::getProcessStatus, "pending")
                .set(IntegrationCallbackLog::getProcessStatus, TASK_STATUS_FAILED)
                .set(IntegrationCallbackLog::getProcessResult, message)
                .set(IntegrationCallbackLog::getErrorMessage, message)
                .set(IntegrationCallbackLog::getUpdatedAt, now));
        if (updated <= 0) {
            return;
        }
        logEntity.setProcessStatus(TASK_STATUS_FAILED);
        logEntity.setProcessResult(message);
        logEntity.setErrorMessage(message);
        logEntity.setUpdatedAt(now);
    }

    private void markStuckFileRecordFailed(IntegrationFileRecord record, LocalDateTime now) {
        String message = "附件转存处理中断或服务异常退出，系统已自动收口，请人工复核本次附件";
        int updated = fileRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationFileRecord>()
                .eq(IntegrationFileRecord::getId, record.getId())
                .and(wrapper -> wrapper
                        .eq(IntegrationFileRecord::getDownloadStatus, FILE_RECORD_STATUS_PENDING)
                        .or()
                        .eq(IntegrationFileRecord::getStorageStatus, FILE_RECORD_STATUS_PENDING))
                .set(IntegrationFileRecord::getDownloadStatus,
                        FILE_RECORD_STATUS_PENDING.equals(record.getDownloadStatus()) ? FILE_RECORD_STATUS_FAILED : record.getDownloadStatus())
                .set(IntegrationFileRecord::getStorageStatus, FILE_RECORD_STATUS_FAILED)
                .set(IntegrationFileRecord::getErrorCode, FILE_RECORD_ERROR_TRANSFER_TIMEOUT)
                .set(IntegrationFileRecord::getErrorMessage, message)
                .set(IntegrationFileRecord::getUpdatedAt, now));
        if (updated <= 0) {
            return;
        }
        if (FILE_RECORD_STATUS_PENDING.equals(record.getDownloadStatus())) {
            record.setDownloadStatus(FILE_RECORD_STATUS_FAILED);
        }
        record.setStorageStatus(FILE_RECORD_STATUS_FAILED);
        record.setErrorCode(FILE_RECORD_ERROR_TRANSFER_TIMEOUT);
        record.setErrorMessage(message);
        record.setUpdatedAt(now);
    }

    private void markStuckTaskFailed(IntegrationSyncTask task, LocalDateTime now) {
        String timeoutMessage = "任务执行超时或服务中断，系统已自动收口，请人工确认后再重试";
        int updated = syncTaskMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationSyncTask>()
                .eq(IntegrationSyncTask::getId, task.getId())
                .in(IntegrationSyncTask::getTaskStatus, TASK_STATUS_PENDING, TASK_STATUS_RUNNING)
                .set(IntegrationSyncTask::getTaskStatus, TASK_STATUS_FAILED)
                .set(IntegrationSyncTask::getFinishAt, now)
                .set(IntegrationSyncTask::getResultMessage, timeoutMessage));
        if (updated <= 0) {
            return;
        }
        task.setTaskStatus(TASK_STATUS_FAILED);
        task.setFinishAt(now);
        task.setResultMessage(timeoutMessage);
        IntegrationModuleConfig config = moduleConfigMapper.selectById(task.getConfigId());
        IntegrationBinding binding = task.getBindingId() == null || task.getBindingId() <= 0 ? null : bindingMapper.selectById(task.getBindingId());
        if (binding != null && isLatestTaskForBinding(task)) {
            binding.setSyncStatus(TASK_STATUS_FAILED);
            binding.setLastSyncAt(now);
            binding.setNextSyncAt(config == null ? null : determineNextSyncAt(config, binding, null));
            binding.setRemark(timeoutMessage);
            bindingMapper.updateById(binding);
        }
        if (config != null) {
            updateConfigHealthStatus(config.getId(), TASK_STATUS_FAILED, timeoutMessage);
        }
        if (config != null && binding != null) {
            LocalDateTime referenceStart = firstNonNull(task.getStartAt(), firstNonNull(task.getPlanExecuteAt(), now));
            createSyncLog(task, binding, config, new SyncLogCreatePayload(
                    null,
                    null,
                    null,
                    null,
                    null,
                    TASK_STATUS_FAILED,
                    TASK_TIMEOUT_ERROR_CODE,
                    timeoutMessage,
                    timeoutMessage,
                    null,
                    java.time.Duration.between(referenceStart, now).toMillis(),
                    null
            ));
        }
    }

    private boolean isLatestTaskForBinding(IntegrationSyncTask task) {
        if (task == null || task.getBindingId() == null || task.getBindingId() <= 0) {
            return false;
        }
        Long latestTaskId = loadLatestTaskIdsByBinding(Set.of(task.getBindingId())).get(task.getBindingId());
        return latestTaskId == null || Objects.equals(latestTaskId, task.getId());
    }

    private boolean isBindingStillSchedulable(IntegrationBinding binding) {
        if (!MODE_THIRD_PARTY.equals(binding.getMaintenanceMode())) {
            return false;
        }
        if (!BIZ_MODULE_PURCHASE_ORDER.equals(binding.getBizModule())) {
            return true;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT deleted, status, logistics_source_type AS logisticsSourceType, " +
                        "inspection_source_type AS inspectionSourceType, inspection_result AS inspectionResult, " +
                        "trace_source_type AS traceSourceType, trace_batch_id AS traceBatchId, trace_origin AS traceOrigin " +
                        "FROM scm_purchase_order WHERE id = ? LIMIT 1",
                binding.getBizId()
        );
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        if (Objects.equals(asLong(row.get("deleted")), 1L)) {
            return false;
        }
        String status = asString(row.get("status"));
        if (BIZ_SCENE_LOGISTICS.equals(binding.getBizScene())) {
            return MODE_THIRD_PARTY.equals(asString(row.get("logisticsSourceType")))
                    && (ORDER_STATUS_APPROVED.equals(status) || ORDER_STATUS_DELIVERING.equals(status) || ORDER_STATUS_PENDING_RECEIPT.equals(status));
        }
        if (BIZ_SCENE_INSPECTION.equals(binding.getBizScene())) {
            if (!MODE_THIRD_PARTY.equals(asString(row.get("inspectionSourceType")))) {
                return false;
            }
            if (ORDER_STATUS_COMPLETED.equals(status)) {
                String inspectionResult = asString(row.get("inspectionResult"));
                return !INSPECTION_RESULT_QUALIFIED.equals(inspectionResult) && !INSPECTION_RESULT_UNQUALIFIED.equals(inspectionResult);
            }
            return ORDER_STATUS_APPROVED.equals(status) || ORDER_STATUS_DELIVERING.equals(status)
                    || ORDER_STATUS_PENDING_RECEIPT.equals(status) || ORDER_STATUS_COMPLETED.equals(status);
        }
        if (BIZ_SCENE_TRACEABILITY.equals(binding.getBizScene())) {
            if (!MODE_THIRD_PARTY.equals(asString(row.get("traceSourceType")))) {
                return false;
            }
            if (ORDER_STATUS_COMPLETED.equals(status)) {
                return StrUtil.isBlank(asString(row.get("traceBatchId"))) && StrUtil.isBlank(asString(row.get("traceOrigin")));
            }
            return ORDER_STATUS_APPROVED.equals(status) || ORDER_STATUS_DELIVERING.equals(status)
                    || ORDER_STATUS_PENDING_RECEIPT.equals(status) || ORDER_STATUS_COMPLETED.equals(status);
        }
        return true;
    }

    private List<IntegrationModuleConfig> loadInternalConfigs(Long tenantId, Long orgId, String bizModule, String bizScene) {
        return moduleConfigMapper.selectList(new LambdaQueryWrapper<IntegrationModuleConfig>()
                .eq(IntegrationModuleConfig::getTenantId, tenantId)
                .eq(IntegrationModuleConfig::getOrgId, orgId)
                .eq(IntegrationModuleConfig::getBizModule, bizModule)
                .eq(IntegrationModuleConfig::getBizScene, bizScene)
                .eq(IntegrationModuleConfig::getEnabled, 1)
                .orderByDesc(IntegrationModuleConfig::getUpdatedAt)
                .orderByDesc(IntegrationModuleConfig::getId));
    }

    private List<IntegrationBinding> loadBindingsByBiz(Long tenantId, Long orgId, String bizModule, String bizScene, Long bizId) {
        return bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getTenantId, tenantId)
                .eq(IntegrationBinding::getOrgId, orgId)
                .eq(IntegrationBinding::getBizModule, bizModule)
                .eq(IntegrationBinding::getBizScene, bizScene)
                .eq(IntegrationBinding::getBizId, bizId)
                .orderByDesc(IntegrationBinding::getUpdatedAt)
                .orderByDesc(IntegrationBinding::getId));
    }

    private IntegrationBinding selectCurrentBinding(List<IntegrationBinding> bindings, Long preferredConfigId) {
        if (bindings == null || bindings.isEmpty()) {
            return null;
        }
        if (preferredConfigId != null) {
            for (IntegrationBinding binding : bindings) {
                if (Objects.equals(binding.getConfigId(), preferredConfigId)) {
                    return binding;
                }
            }
        }
        for (IntegrationBinding binding : bindings) {
            if (MODE_THIRD_PARTY.equals(binding.getMaintenanceMode())) {
                return binding;
            }
        }
        return bindings.get(0);
    }

    private IntegrationModuleConfig resolveSelectedConfig(List<IntegrationModuleConfig> configs, IntegrationBinding currentBinding, Long preferredConfigId) {
        if (configs == null || configs.isEmpty()) {
            return null;
        }
        Long selectedConfigId = currentBinding != null ? currentBinding.getConfigId() : preferredConfigId;
        if (selectedConfigId != null) {
            for (IntegrationModuleConfig config : configs) {
                if (Objects.equals(config.getId(), selectedConfigId)) {
                    return config;
                }
            }
        }
        return configs.get(0);
    }

    private IntegrationModuleConfig getInternalModuleConfigEntity(Long configId, Long tenantId, Long orgId) {
        IntegrationModuleConfig entity = moduleConfigMapper.selectById(configId);
        if (entity == null) {
            throw BizException.notFound("模块接入配置不存在");
        }
        if (!Objects.equals(entity.getTenantId(), tenantId)) {
            throw BizException.forbidden("无权访问该租户数据");
        }
        if (!Objects.equals(entity.getOrgId(), orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
        return entity;
    }

    private IntegrationInternalModuleOptionVO toInternalModuleOptionVO(IntegrationModuleConfig entity, String providerName) {
        IntegrationInternalModuleOptionVO vo = new IntegrationInternalModuleOptionVO();
        vo.setId(entity.getId());
        vo.setConfigName(entity.getConfigName());
        vo.setProviderCode(entity.getProviderCode());
        vo.setProviderName(providerName);
        vo.setDefaultMode(entity.getDefaultMode());
        vo.setAllowDocumentSwitch(entity.getAllowDocumentSwitch());
        vo.setForceThirdParty(entity.getForceThirdParty());
        vo.setAllowManualFallback(entity.getAllowManualFallback());
        vo.setAutoCoverEnabled(entity.getAutoCoverEnabled());
        vo.setCallbackEnabled(entity.getCallbackEnabled());
        vo.setSyncFrequencyMinutes(entity.getSyncFrequencyMinutes());
        vo.setExternalNoFieldRule(entity.getExternalNoFieldRule());
        vo.setLastSyncStatus(entity.getLastSyncStatus());
        vo.setLastErrorMessage(entity.getLastErrorMessage());
        vo.setLastSyncAt(entity.getLastSyncAt());
        return vo;
    }

    private IntegrationInternalBindingSummaryVO loadInternalBindingSummary(Long bindingId) {
        IntegrationBinding binding = bindingMapper.selectById(bindingId);
        if (binding == null) {
            return null;
        }
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(Set.of(binding.getConfigId()));
        Map<String, String> providerNameMap = loadProviderNameMap(Set.of(binding.getProviderCode()));
        return toInternalBindingSummary(binding, configMap, providerNameMap);
    }

    private IntegrationInternalBindingSummaryVO toInternalBindingSummary(IntegrationBinding binding,
                                                                        Map<Long, IntegrationModuleConfig> configMap,
                                                                        Map<String, String> providerNameMap) {
        if (binding == null) {
            return null;
        }
        IntegrationModuleConfig config = configMap.get(binding.getConfigId());
        IntegrationInternalBindingSummaryVO vo = new IntegrationInternalBindingSummaryVO();
        vo.setBindingId(binding.getId());
        vo.setConfigId(binding.getConfigId());
        vo.setConfigName(config == null ? null : config.getConfigName());
        vo.setProviderCode(binding.getProviderCode());
        vo.setProviderName(providerNameMap.get(binding.getProviderCode()));
        vo.setBizModule(binding.getBizModule());
        vo.setBizScene(binding.getBizScene());
        vo.setBizId(binding.getBizId());
        vo.setBizNo(binding.getBizNo());
        vo.setExternalNo(binding.getExternalNo());
        vo.setMaintenanceMode(binding.getMaintenanceMode());
        vo.setModeSource(binding.getModeSource());
        vo.setModeLocked(binding.getModeLocked());
        vo.setSyncStatus(binding.getSyncStatus());
        vo.setFirstBindAt(binding.getFirstBindAt());
        vo.setLastSyncAt(binding.getLastSyncAt());
        vo.setNextSyncAt(binding.getNextSyncAt());
        vo.setUpdatedAt(binding.getUpdatedAt());
        vo.setLastErrorMessage(loadLatestBindingErrorMessage(binding.getId()));
        return vo;
    }

    private String loadLatestBindingErrorMessage(Long bindingId) {
        IntegrationSyncLog failedLog = syncLogMapper.selectOne(new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(IntegrationSyncLog::getBindingId, bindingId)
                .eq(IntegrationSyncLog::getSyncStatus, TASK_STATUS_FAILED)
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .orderByDesc(IntegrationSyncLog::getId)
                .last("LIMIT 1"));
        return failedLog == null ? null : failedLog.getErrorMessage();
    }

    private List<IntegrationInternalLogBriefVO> loadInternalSyncLogs(IntegrationInternalBizBindingQueryDTO dto,
                                                                     IntegrationBinding currentBinding,
                                                                     Map<String, String> providerNameMap,
                                                                     int limit) {
        LambdaQueryWrapper<IntegrationSyncLog> wrapper = new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(IntegrationSyncLog::getTenantId, dto.getTenantId())
                .eq(IntegrationSyncLog::getOrgId, dto.getOrgId())
                .eq(IntegrationSyncLog::getBizModule, dto.getBizModule())
                .eq(IntegrationSyncLog::getBizScene, dto.getBizScene())
                .eq(IntegrationSyncLog::getBizId, dto.getBizId())
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .orderByDesc(IntegrationSyncLog::getId)
                .last("LIMIT " + limit);
        if (currentBinding != null) {
            wrapper.eq(IntegrationSyncLog::getBindingId, currentBinding.getId());
        }
        return syncLogMapper.selectList(wrapper).stream()
                .map(item -> toInternalLogBrief(item, providerNameMap.get(item.getProviderCode())))
                .toList();
    }

    private List<IntegrationInternalLogBriefVO> loadInternalCallbackLogs(IntegrationInternalBizBindingQueryDTO dto,
                                                                         IntegrationBinding currentBinding,
                                                                         Map<String, String> providerNameMap,
                                                                         int limit) {
        LambdaQueryWrapper<IntegrationCallbackLog> wrapper = new LambdaQueryWrapper<IntegrationCallbackLog>()
                .eq(IntegrationCallbackLog::getTenantId, dto.getTenantId())
                .eq(IntegrationCallbackLog::getOrgId, dto.getOrgId())
                .eq(IntegrationCallbackLog::getBizModule, dto.getBizModule())
                .eq(IntegrationCallbackLog::getBizScene, dto.getBizScene())
                .eq(IntegrationCallbackLog::getBizId, dto.getBizId())
                .orderByDesc(IntegrationCallbackLog::getCreatedAt)
                .orderByDesc(IntegrationCallbackLog::getId)
                .last("LIMIT " + limit);
        if (currentBinding != null) {
            wrapper.eq(IntegrationCallbackLog::getBindingId, currentBinding.getId());
        }
        return callbackLogMapper.selectList(wrapper).stream()
                .map(item -> toInternalLogBrief(item, providerNameMap.get(item.getProviderCode())))
                .toList();
    }

    private IntegrationInternalLogBriefVO toInternalLogBrief(IntegrationSyncLog entity, String providerName) {
        IntegrationInternalLogBriefVO vo = new IntegrationInternalLogBriefVO();
        vo.setId(entity.getId());
        vo.setLogType(LOG_TYPE_SYNC);
        vo.setBindingId(entity.getBindingId());
        vo.setConfigId(entity.getConfigId());
        vo.setProviderCode(entity.getProviderCode());
        vo.setProviderName(providerName);
        vo.setExternalNo(entity.getExternalNo());
        vo.setStatus(entity.getSyncStatus());
        vo.setTriggerType(entity.getTriggerType());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setMessage(firstNonBlank(entity.getErrorMessage(), entity.getSyncStatus()));
        vo.setCreatedAt(entity.getCreatedAt());
        IntegrationSyncTask task = entity.getTaskId() == null ? null : syncTaskMapper.selectById(entity.getTaskId());
        vo.setTaskNo(task == null ? null : task.getTaskNo());
        return vo;
    }

    private IntegrationInternalLogBriefVO toInternalLogBrief(IntegrationCallbackLog entity, String providerName) {
        IntegrationInternalLogBriefVO vo = new IntegrationInternalLogBriefVO();
        vo.setId(entity.getId());
        vo.setLogType(LOG_TYPE_CALLBACK);
        vo.setBindingId(entity.getBindingId());
        vo.setProviderCode(entity.getProviderCode());
        vo.setProviderName(providerName);
        vo.setExternalNo(entity.getExternalNo());
        vo.setStatus(entity.getProcessStatus());
        vo.setMessage(firstNonBlank(entity.getProcessResult(), entity.getErrorMessage(), entity.getProcessStatus()));
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private int normalizeInternalLogLimit(Integer rawLimit) {
        if (rawLimit == null || rawLimit < 1) {
            return 5;
        }
        return Math.min(rawLimit, 20);
    }

    private void validateInternalReadable(Long tenantId, Long orgId) {
        if (tenantId == null || orgId == null) {
            throw BizException.validationFailed("租户或组织信息不能为空");
        }
        Long currentTenantId = UserContext.getTenantId();
        if (currentTenantId != null && !Objects.equals(currentTenantId, tenantId)) {
            throw BizException.forbidden("无权访问该租户数据");
        }
        if (UserContext.getUserId() != null) {
            validateOrgReadable(orgId, dataScopeService.resolveCurrentUserOrgScope());
        }
    }

    private void validateInternalWritable(Long tenantId, Long orgId) {
        validateInternalReadable(tenantId, orgId);
        if (UserContext.getUserId() != null) {
            validateOrgWritable(orgId);
        }
    }

    private <T> T withSyntheticUserContext(Long operatorId, String operatorName, String operatorUsername,
                                           Long orgId, Long tenantId, Supplier<T> supplier) {
        UserContext existingContext = UserContext.get();
        boolean injected = existingContext == null;
        if (injected) {
            UserContext context = new UserContext();
            context.setUserId(firstNonNull(operatorId, 0L));
            context.setUsername(firstNonBlank(operatorUsername, operatorName, "system"));
            context.setRealName(firstNonBlank(operatorName, operatorUsername, "系统"));
            context.setOrgId(orgId);
            context.setTenantId(tenantId);
            UserContext.set(context);
        }
        try {
            return supplier.get();
        } finally {
            if (injected) {
                UserContext.clear();
            }
        }
    }

    private void deactivateOtherBindings(IntegrationBinding currentBinding) {
        List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getTenantId, currentBinding.getTenantId())
                .eq(IntegrationBinding::getOrgId, currentBinding.getOrgId())
                .eq(IntegrationBinding::getBizModule, currentBinding.getBizModule())
                .eq(IntegrationBinding::getBizScene, currentBinding.getBizScene())
                .eq(IntegrationBinding::getBizId, currentBinding.getBizId())
                .ne(IntegrationBinding::getId, currentBinding.getId()));
        for (IntegrationBinding binding : bindings) {
            binding.setMaintenanceMode(MODE_MANUAL);
            binding.setModeSource("superseded_by_new_binding");
            bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationBinding>()
                    .eq(IntegrationBinding::getId, binding.getId())
                    .set(IntegrationBinding::getMaintenanceMode, MODE_MANUAL)
                    .set(IntegrationBinding::getModeSource, "superseded_by_new_binding")
                    .set(IntegrationBinding::getNextSyncAt, null));
        }
    }

    private String normalizeManualOrThirdParty(String mode) {
        String normalized = firstNonBlank(mode, MODE_MANUAL);
        if (MODE_MANUAL.equals(normalized) || MODE_THIRD_PARTY.equals(normalized)) {
            return normalized;
        }
        throw BizException.validationFailed("维护模式仅支持 manual 或 third_party");
    }

    private boolean isPurchaseOrderSceneStillThirdParty(Map<String, Object> orderRow, String scene) {
        if (orderRow == null) {
            return false;
        }
        if (BIZ_SCENE_LOGISTICS.equals(scene)) {
            return MODE_THIRD_PARTY.equals(asString(orderRow.get("logisticsSourceType")));
        }
        if (BIZ_SCENE_INSPECTION.equals(scene)) {
            return MODE_THIRD_PARTY.equals(asString(orderRow.get("inspectionSourceType")));
        }
        if (BIZ_SCENE_TRACEABILITY.equals(scene)) {
            return MODE_THIRD_PARTY.equals(asString(orderRow.get("traceSourceType")));
        }
        return false;
    }

    private String resolveStatusAfterLogistics(String currentStatus, String logisticsStatus,
                                               LocalDateTime shippedAt, LocalDateTime arrivedAt) {
        if (arrivedAt != null || LOGISTICS_STATUS_ARRIVED.equals(logisticsStatus)) {
            return ORDER_STATUS_PENDING_RECEIPT;
        }
        if (ORDER_STATUS_DELIVERING.equals(currentStatus)) {
            return ORDER_STATUS_DELIVERING;
        }
        if (shippedAt != null
                || LOGISTICS_STATUS_SHIPPED.equals(logisticsStatus)
                || LOGISTICS_STATUS_IN_TRANSIT.equals(logisticsStatus)) {
            return ORDER_STATUS_DELIVERING;
        }
        return currentStatus;
    }

    private <T> T firstNonNull(T first, T fallback) {
        return first != null ? first : fallback;
    }

    private void ensureBuiltinProviders() {
        Long tenantId = currentTenantId();
        List<IntegrationProviderTemplate> existing = providerTemplateMapper.selectList(new LambdaQueryWrapper<IntegrationProviderTemplate>()
                .eq(tenantId != null, IntegrationProviderTemplate::getTenantId, tenantId)
                .in(IntegrationProviderTemplate::getProviderCode, BUILTIN_PROVIDER_CODES));
        Set<String> existingCodes = existing.stream()
                .map(IntegrationProviderTemplate::getProviderCode)
                .collect(Collectors.toSet());
        List<IntegrationProviderTemplate> templates = new ArrayList<>();
        if (!existingCodes.contains("sf_express")) {
            templates.add(buildProvider("sf_express", "顺丰物流", "logistics", "bearer", "http", 1, 1,
                    List.of("purchase_order:logistics"),
                    """
                            {"method":"GET","url":"","headers":{"Accept":"application/json"},"query":{"mailNo":"${externalNo}"},"testResponse":{"mailNo":"${externalNo}","company":"顺丰速运","status":"SIGNED","shippedAt":"2026-06-15 08:30:00","arrivedAt":"2026-06-16 14:20:00","remark":"模板模拟返回","attachments":[]}}
                            """,
                    """
                            {"statusMap":{"SIGNED":"arrived","IN_TRANSIT":"in_transit","COLLECTED":"shipped"}}
                            """));
        }
        if (!existingCodes.contains("jd_logistics")) {
            templates.add(buildProvider("jd_logistics", "京东物流", "logistics", "bearer", "http", 1, 1,
                    List.of("purchase_order:logistics"), null, null));
        }
        if (!existingCodes.contains("inspection_agency_a")) {
            templates.add(buildProvider("inspection_agency_a", "检测机构A", "inspection", "app_secret", "http", 1, 1,
                    List.of("purchase_order:inspection"),
                    """
                            {"method":"GET","url":"","headers":{"Accept":"application/json"},"query":{"reportNo":"${externalNo}"},"testResponse":{"reportNo":"${externalNo}","result":"PASS","agency":"检测机构A","inspectionAt":"2026-06-15 10:00:00","remark":"模板模拟返回","attachments":[]}}
                            """,
                    null));
        }
        if (!existingCodes.contains("traceability_platform_b")) {
            templates.add(buildProvider("traceability_platform_b", "溯源平台B", "traceability", "app_secret", "http", 1, 1,
                    List.of("purchase_order:traceability"),
                    """
                            {"method":"GET","url":"","headers":{"Accept":"application/json"},"query":{"batchId":"${externalNo}"},"testResponse":{"batchId":"${externalNo}","origin":"产地示例","remark":"模板模拟返回","attachments":[]}}
                            """,
                    null));
        }
        if (!existingCodes.contains("sample_device_platform")) {
            templates.add(buildProvider("sample_device_platform", "留样设备平台", "device", "oauth2", "http", 1, 1,
                    List.of("sample_retention:sample_task", "sample_retention:sample_record", "sample_retention:sample_result", "sample_retention:sample_device"), null, null));
        }
        if (!existingCodes.contains("face_check_device_platform")) {
            templates.add(buildProvider("face_check_device_platform", "晨检设备平台", "device", "oauth2", "http", 1, 1,
                    List.of("morning_check:face_profile", "morning_check:health_certificate", "morning_check:morning_check_result", "morning_check:device_master"), null, null));
        }
        for (IntegrationProviderTemplate template : templates) {
            providerTemplateMapper.insert(template);
        }
    }

    private IntegrationProviderTemplate buildProvider(String code, String name, String type, String authType,
                                                      String protocolType, int callbackSupported, int filePullSupported,
                                                      List<String> scenes, String requestTemplate, String responseTemplate) {
        IntegrationProviderTemplate template = new IntegrationProviderTemplate();
        template.setProviderCode(code);
        template.setProviderName(name);
        template.setProviderType(type);
        template.setAuthType(authType);
        template.setProtocolType(protocolType);
        template.setCallbackSupported(callbackSupported);
        template.setFilePullSupported(filePullSupported);
        template.setSceneCodes(writeJson(scenes));
        template.setRequestTemplate(requestTemplate);
        template.setResponseTemplate(responseTemplate);
        template.setStatus("active");
        template.setRemark("系统内置模板");
        template.setOrgId(0L);
        template.setTenantId(currentTenantId());
        return template;
    }

    private void ensureProviderCodeUnique(String providerCode, Long excludeId) {
        Long count = providerTemplateMapper.selectCount(new LambdaQueryWrapper<IntegrationProviderTemplate>()
                .eq(IntegrationProviderTemplate::getProviderCode, providerCode)
                .eq(currentTenantId() != null, IntegrationProviderTemplate::getTenantId, currentTenantId())
                .ne(excludeId != null, IntegrationProviderTemplate::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("平台编码已存在");
        }
    }

    private void applyProviderTemplateSaveData(IntegrationProviderTemplate target, IntegrationProviderTemplateSaveDTO dto) {
        BeanUtils.copyProperties(dto, target);
        target.setSceneCodes(writeJson(dto.getSceneCodes() == null ? Collections.emptyList() : dto.getSceneCodes()));
        target.setRequestTemplate(normalizeProviderTemplateJson(dto.getRequestTemplate(), true, dto.getProviderCode()));
        target.setResponseTemplate(normalizeProviderTemplateJson(dto.getResponseTemplate(), false, dto.getProviderCode()));
    }

    private void normalizeProviderTemplateSaveDTO(IntegrationProviderTemplateSaveDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setProviderCode(StrUtil.trim(dto.getProviderCode()));
        dto.setProviderName(StrUtil.trim(dto.getProviderName()));
        dto.setProviderType(StrUtil.trim(dto.getProviderType()));
        dto.setAuthType(StrUtil.trim(dto.getAuthType()));
        dto.setProtocolType(StrUtil.trim(dto.getProtocolType()));
        dto.setStatus(firstNonBlank(StrUtil.trim(dto.getStatus()), "active"));
        if (dto.getSceneCodes() != null) {
            dto.setSceneCodes(dto.getSceneCodes().stream()
                    .map(StrUtil::trim)
                    .filter(StrUtil::isNotBlank)
                    .distinct()
                    .toList());
        }
    }

    private void validateProviderTemplateSaveDTO(IntegrationProviderTemplateSaveDTO dto) {
        if (dto == null) {
            throw BizException.validationFailed("平台模板保存参数不能为空");
        }
        if (StrUtil.isBlank(dto.getProviderCode()) || !PROVIDER_CODE_PATTERN.matcher(dto.getProviderCode()).matches()) {
            throw BizException.validationFailed(PROVIDER_CODE_FORMAT_ERROR);
        }
        validateProviderEnumValue("平台类型", dto.getProviderType(), ALLOWED_PROVIDER_TYPES);
        validateProviderEnumValue("鉴权类型", dto.getAuthType(), ALLOWED_AUTH_TYPES);
        validateProviderEnumValue("协议类型", dto.getProtocolType(), ALLOWED_PROTOCOL_TYPES);
        validateProviderStatusValue(dto.getStatus());
        validateBinaryFlag("支持回调", dto.getCallbackSupported());
        validateBinaryFlag("支持文件拉取", dto.getFilePullSupported());
        if (dto.getSceneCodes() == null || dto.getSceneCodes().isEmpty()) {
            throw BizException.validationFailed("【支持场景】为必填项，请完成填写后再保存");
        }
        for (String sceneCode : dto.getSceneCodes()) {
            if (!ALLOWED_SCENE_CODES.contains(sceneCode)) {
                throw BizException.validationFailed("存在不受支持的业务场景编码：" + sceneCode);
            }
        }
    }

    private void validateProviderEnumValue(String fieldName, String value, Set<String> allowedValues) {
        if (StrUtil.isBlank(value) || allowedValues.contains(value)) {
            return;
        }
        throw BizException.validationFailed("【" + fieldName + "】取值不合法，请重新选择系统预设选项");
    }

    private void validateProviderStatusValue(String status) {
        validateProviderEnumValue("启用状态", status, ALLOWED_PROVIDER_STATUSES);
    }

    private void validateBinaryFlag(String fieldName, Integer value) {
        if (value == null || value == 0 || value == 1) {
            return;
        }
        throw BizException.validationFailed("【" + fieldName + "】仅支持 0 或 1");
    }

    private String normalizeProviderTemplateJson(String template, boolean requestTemplate, String providerCode) {
        String normalizedTemplate = StrUtil.trimToNull(template);
        if (normalizedTemplate == null) {
            return null;
        }
        validateTemplateVariables(normalizedTemplate, requestTemplate, providerCode);
        try {
            JsonNode jsonNode = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(normalizedTemplate);
            if (requestTemplate) {
                validateRequestTemplateSemantic(jsonNode);
            }
            return objectMapper.writeValueAsString(jsonNode);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("平台模板JSON解析失败, providerCode={}, templateType={}, content={}",
                    providerCode, requestTemplate ? "request" : "response", normalizedTemplate, ex);
            throw BizException.badRequest(requestTemplate ? REQUEST_TEMPLATE_JSON_ERROR : RESPONSE_TEMPLATE_JSON_ERROR);
        }
    }

    private void validateRequestTemplateSemantic(JsonNode templateNode) {
        if (templateNode == null || templateNode.isNull()) {
            return;
        }
        if (!templateNode.isObject()) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_STRUCTURE_ERROR);
        }
        JsonNode headers = templateNode.path("headers");
        JsonNode query = templateNode.path("query");
        if (!headers.isMissingNode() && !headers.isNull() && !headers.isObject()) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_STRUCTURE_ERROR);
        }
        if (!query.isMissingNode() && !query.isNull() && !query.isObject()) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_STRUCTURE_ERROR);
        }
        String method = StrUtil.trimToNull(templateNode.path("method").asText(null));
        if (method != null && !ALLOWED_TEMPLATE_HTTP_METHODS.contains(method.toUpperCase(Locale.ROOT))) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_METHOD_ERROR);
        }
        String url = StrUtil.trimToNull(templateNode.path("url").asText(null));
        if (url == null) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_SEMANTIC_ERROR);
        }
        if (!TEMPLATE_URL_PATTERN.matcher(url).matches()) {
            throw BizException.validationFailed(REQUEST_TEMPLATE_URL_ERROR);
        }
    }

    private void validateTemplateVariables(String template, boolean requestTemplate, String providerCode) {
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(template);
        StringBuffer remainder = new StringBuffer();
        while (matcher.find()) {
            String variableExpression = matcher.group();
            if (!LEGAL_TEMPLATE_VARIABLE_PATTERN.matcher(variableExpression).matches()) {
                log.warn("平台模板变量表达式非法, providerCode={}, templateType={}, expression={}",
                        providerCode, requestTemplate ? "request" : "response", variableExpression);
                throw BizException.validationFailed(TEMPLATE_VARIABLE_ERROR);
            }
            matcher.appendReplacement(remainder, "");
        }
        matcher.appendTail(remainder);
        if (remainder.indexOf("${") >= 0) {
            log.warn("平台模板存在未闭合变量表达式, providerCode={}, templateType={}, content={}",
                    providerCode, requestTemplate ? "request" : "response", template);
            throw BizException.validationFailed(TEMPLATE_VARIABLE_ERROR);
        }
    }

    private void normalizeModuleConfigDto(IntegrationModuleConfigSaveDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setBizModule(StrUtil.trim(dto.getBizModule()));
        dto.setBizScene(StrUtil.trim(dto.getBizScene()));
        dto.setProviderCode(StrUtil.trim(dto.getProviderCode()));
        dto.setConfigName(StrUtil.trim(dto.getConfigName()));
        dto.setDefaultMode(StrUtil.trim(dto.getDefaultMode()));
        dto.setTriggerStrategy(StrUtil.trim(dto.getTriggerStrategy()));
        dto.setAutoCoverStrategy(StrUtil.trim(dto.getAutoCoverStrategy()));
        dto.setCallbackUrl(StrUtil.trimToNull(dto.getCallbackUrl()));
        dto.setExternalNoFieldRule(StrUtil.trimToNull(dto.getExternalNoFieldRule()));
        dto.setAccessTokenUrl(StrUtil.trimToNull(dto.getAccessTokenUrl()));
        dto.setRefreshTokenUrl(StrUtil.trimToNull(dto.getRefreshTokenUrl()));
        dto.setTokenRequestMethod(StrUtil.trim(dto.getTokenRequestMethod()));
        dto.setScheduleCron(StrUtil.trimToNull(dto.getScheduleCron()));
        dto.setRemark(StrUtil.trimToNull(dto.getRemark()));
        if (dto.getSecrets() == null) {
            return;
        }
        for (IntegrationSecretInputDTO secret : dto.getSecrets()) {
            if (secret == null) {
                continue;
            }
            secret.setSecretKey(StrUtil.trim(secret.getSecretKey()));
            if (StrUtil.isBlank(secret.getSecretValue())) {
                secret.setSecretValue(null);
            }
        }
    }

    private void normalizeFieldMappingSaveDTO(IntegrationFieldMappingSaveDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setSourceField(StrUtil.trimToNull(dto.getSourceField()));
        dto.setSourcePath(StrUtil.trimToNull(dto.getSourcePath()));
        dto.setTargetField(StrUtil.trim(dto.getTargetField()));
        dto.setTransformType(StrUtil.trim(dto.getTransformType()));
        dto.setTransformRule(StrUtil.trimToNull(dto.getTransformRule()));
        dto.setDefaultValue(StrUtil.trimToNull(dto.getDefaultValue()));
        dto.setErrorStrategy(StrUtil.trimToNull(dto.getErrorStrategy()));
        dto.setRemark(StrUtil.trimToNull(dto.getRemark()));
    }

    private void validateFieldMappingDuplicatePayload(IntegrationModuleConfig config, IntegrationFieldMappingDuplicateCheckDTO dto) {
        if (dto == null) {
            throw BizException.validationFailed("字段映射校验参数不能为空");
        }
        dto.setTargetField(StrUtil.trim(dto.getTargetField()));
        dto.setEnabled(firstNonNull(dto.getEnabled(), 1));
        validateConfigBinaryFlag(dto.getEnabled(), "启用状态");
        IntegrationFieldMapping before = dto.getId() == null ? null : getFieldMappingEntity(dto.getId());
        validateFieldTargetField(config, dto.getTargetField(), before);
    }

    private void validateFieldMappingPayload(IntegrationModuleConfig config, IntegrationFieldMappingSaveDTO dto, IntegrationFieldMapping before) {
        if (dto == null) {
            throw BizException.validationFailed("字段映射保存参数不能为空");
        }
        dto.setRequiredFlag(firstNonNull(dto.getRequiredFlag(), 0));
        dto.setEnabled(firstNonNull(dto.getEnabled(), 1));
        dto.setTransformType(firstNonBlank(dto.getTransformType(), "direct"));
        dto.setErrorStrategy(firstNonBlank(dto.getErrorStrategy(), "fail"));
        validateConfigBinaryFlag(dto.getRequiredFlag(), "是否必填");
        validateConfigBinaryFlag(dto.getEnabled(), "启用状态");
        validateAllowedValue(dto.getTransformType(), ALLOWED_FIELD_TRANSFORM_TYPES,
                "转换规则仅支持 direct、dict、date、number、json_path");
        validateAllowedValue(dto.getErrorStrategy(), ALLOWED_FIELD_ERROR_STRATEGIES,
                "异常时处理方式仅支持 fail、skip、log_only、manual_review");
        if (StrUtil.isBlank(dto.getSourceField()) && StrUtil.isBlank(dto.getSourcePath()) && StrUtil.isBlank(dto.getDefaultValue())) {
            throw BizException.validationFailed("第三方字段、JSON路径、默认值不能同时为空，请至少填写一项");
        }
        StandardFieldMeta fieldMeta = validateFieldTargetField(config, dto.getTargetField(), before);
        validateSimpleJsonPath(dto.getSourcePath(), "JSON路径");
        validateFieldTransformRule(dto.getTransformType(), dto.getTransformRule(), dto.getTargetField());
        validateFieldDefaultValue(dto, fieldMeta);
    }

    private StandardFieldMeta validateFieldTargetField(IntegrationModuleConfig config, String targetField, IntegrationFieldMapping before) {
        StandardFieldMeta fieldMeta = lookupStandardFieldMeta(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene(), targetField);
        if (fieldMeta != null) {
            return fieldMeta;
        }
        if (before != null && Objects.equals(before.getTargetField(), targetField)) {
            return null;
        }
        throw BizException.validationFailed("系统字段必须选择当前场景预置的系统标准字段，不能填写数据库列名或自定义字符串");
    }

    private void validateFieldTransformRule(String transformType, String transformRule, String targetField) {
        String normalizedType = firstNonBlank(transformType, "direct");
        if ("direct".equals(normalizedType)) {
            if (StrUtil.isNotBlank(transformRule)) {
                throw BizException.validationFailed("字段【" + targetField + "】使用直接映射时，不需要填写转换表达式");
            }
            return;
        }
        if ("dict".equals(normalizedType)) {
            parseDictRuleStrict(transformRule, targetField);
            return;
        }
        if ("date".equals(normalizedType)) {
            if (StrUtil.isBlank(transformRule)) {
                return;
            }
            try {
                DateTimeFormatter.ofPattern(transformRule);
            } catch (IllegalArgumentException ex) {
                throw BizException.validationFailed("字段【" + targetField + "】的转换表达式不是合法的日期格式");
            }
            return;
        }
        if ("number".equals(normalizedType)) {
            if (StrUtil.isBlank(transformRule)) {
                return;
            }
            try {
                new BigDecimal(transformRule);
            } catch (NumberFormatException ex) {
                throw BizException.validationFailed("字段【" + targetField + "】的转换表达式必须是合法数字");
            }
            return;
        }
        if ("json_path".equals(normalizedType)) {
            if (StrUtil.isBlank(transformRule)) {
                throw BizException.validationFailed("字段【" + targetField + "】使用 JSON 路径提取时，必须填写转换表达式");
            }
            validateSimpleJsonPath(transformRule, "转换表达式");
        }
    }

    private void validateFieldDefaultValue(IntegrationFieldMappingSaveDTO dto, StandardFieldMeta fieldMeta) {
        if (dto == null || StrUtil.isBlank(dto.getDefaultValue())) {
            return;
        }
        IntegrationFieldMapping preview = new IntegrationFieldMapping();
        preview.setTargetField(dto.getTargetField());
        preview.setTransformType(dto.getTransformType());
        preview.setTransformRule(dto.getTransformRule());
        Object transformed = applyFieldTransform(dto.getDefaultValue(), preview, true);
        coerceFieldValueByMeta(fieldMeta, transformed, dto.getTargetField(), true);
    }

    private void normalizeStatusMappingSaveDTO(IntegrationStatusMappingSaveDTO dto) {
        if (dto == null) {
            return;
        }
        dto.setSourceStatusCode(StrUtil.trim(dto.getSourceStatusCode()));
        dto.setSourceStatusName(StrUtil.trim(dto.getSourceStatusName()));
        dto.setTargetStatusCode(StrUtil.trim(dto.getTargetStatusCode()));
        dto.setActionCode(StrUtil.trimToNull(dto.getActionCode()));
        dto.setRemark(StrUtil.trimToNull(dto.getRemark()));
    }

    private void validateStatusMappingDuplicatePayload(IntegrationModuleConfig config, IntegrationStatusMappingDuplicateCheckDTO dto) {
        if (dto == null) {
            throw BizException.validationFailed("状态映射校验参数不能为空");
        }
        dto.setSourceStatusCode(StrUtil.trim(dto.getSourceStatusCode()));
        if (StrUtil.isBlank(dto.getSourceStatusCode())) {
            throw BizException.validationFailed("第三方状态编码不能为空");
        }
        IntegrationStatusMapping before = dto.getId() == null ? null : getStatusMappingEntity(dto.getId());
        validateStatusTargetStatusForDuplicateCheck(config, before);
    }

    private void validateStatusTargetStatusForDuplicateCheck(IntegrationModuleConfig config, IntegrationStatusMapping before) {
        if (config == null || before == null) {
            return;
        }
        if (!Objects.equals(before.getConfigId(), config.getId())) {
            throw BizException.validationFailed("状态映射所属接入配置不一致，请刷新后重试");
        }
    }

    private void validateStatusMappingPayload(IntegrationModuleConfig config, IntegrationStatusMappingSaveDTO dto, IntegrationStatusMapping before) {
        if (dto == null) {
            throw BizException.validationFailed("状态映射保存参数不能为空");
        }
        dto.setFinishFlag(firstNonNull(dto.getFinishFlag(), 0));
        dto.setTriggerBusinessAction(firstNonNull(dto.getTriggerBusinessAction(), 0));
        dto.setWriteAttachmentFlag(firstNonNull(dto.getWriteAttachmentFlag(), 0));
        dto.setEnabled(firstNonNull(dto.getEnabled(), 1));
        validateConfigBinaryFlag(dto.getFinishFlag(), "结束同步");
        validateConfigBinaryFlag(dto.getTriggerBusinessAction(), "触发业务动作");
        validateConfigBinaryFlag(dto.getWriteAttachmentFlag(), "写入附件");
        validateConfigBinaryFlag(dto.getEnabled(), "启用状态");
        if (StrUtil.isBlank(dto.getSourceStatusCode())) {
            throw BizException.validationFailed("第三方状态编码不能为空");
        }
        if (StrUtil.isBlank(dto.getSourceStatusName())) {
            throw BizException.validationFailed("第三方状态名称不能为空");
        }
        if (StrUtil.isBlank(dto.getTargetStatusCode())) {
            throw BizException.validationFailed("系统标准状态不能为空");
        }
        validateStatusTargetStatus(config, dto.getTargetStatusCode(), dto.getEnabled(), before);
        validateStatusFinishFlag(config, dto.getTargetStatusCode(), dto.getFinishFlag(), dto.getEnabled());
        validateStatusReservedFlags(config, dto, before);
    }

    private void validateStatusTargetStatus(IntegrationModuleConfig config, String targetStatusCode, Integer enabled,
                                            IntegrationStatusMapping before) {
        boolean sceneSupported = hasBuiltinStatusScene(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene());
        boolean validStatus = lookupBuiltinStatusSort(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene(), targetStatusCode) != null;
        if (validStatus) {
            return;
        }
        boolean keepHistoricalDisabledValue = before != null
                && Objects.equals(before.getTargetStatusCode(), targetStatusCode)
                && enabled != null
                && enabled == 0;
        if (keepHistoricalDisabledValue) {
            return;
        }
        if (!sceneSupported) {
            throw BizException.validationFailed("当前场景未提供可用的系统标准状态，请优先使用字段映射；状态映射仅允许保留历史记录的停用/删除，不允许继续新增或启用");
        }
        throw BizException.validationFailed("系统标准状态必须选择当前场景预置的标准状态值；历史异常值仅允许停用后保留，不能继续启用保存");
    }

    private void validateStatusFinishFlag(IntegrationModuleConfig config, String targetStatusCode, Integer finishFlag, Integer enabled) {
        if (finishFlag == null || finishFlag != 1 || enabled == null || enabled != 1) {
            return;
        }
        Set<String> terminalCodes = TERMINAL_STATUS_CODE_MAP.get(sceneKey(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene()));
        if (terminalCodes == null || !terminalCodes.contains(targetStatusCode)) {
            throw BizException.validationFailed("当前系统标准状态不是该场景允许的终态，不能勾选结束同步");
        }
    }

    private void validateStatusReservedFlags(IntegrationModuleConfig config, IntegrationStatusMappingSaveDTO dto, IntegrationStatusMapping before) {
        if (dto.getTriggerBusinessAction() != null && dto.getTriggerBusinessAction() == 1) {
            if (StrUtil.isBlank(dto.getActionCode())) {
                throw BizException.validationFailed("触发业务动作开启时，动作编码不能为空");
            }
            if (!ACTION_CODE_PATTERN.matcher(dto.getActionCode()).matches()) {
                throw BizException.validationFailed("动作编码仅支持小写字母、数字、下划线，且必须以字母开头");
            }
        } else {
            dto.setActionCode(null);
        }
        if (dto.getWriteAttachmentFlag() != null && dto.getWriteAttachmentFlag() == 1) {
            throw BizException.validationFailed("当前版本附件写入由字段映射和附件下载配置控制，状态映射暂不支持单独开启写入附件");
        }
        if (before != null && before.getWriteAttachmentFlag() != null && before.getWriteAttachmentFlag() == 1
                && dto.getWriteAttachmentFlag() != null && dto.getWriteAttachmentFlag() == 0) {
            log.info("状态映射历史写入附件标记已自动收口为关闭, id={}", before.getId());
        }
        if (dto.getTriggerBusinessAction() != null && dto.getTriggerBusinessAction() == 1
                && config != null
                && config.getAttachmentPullEnabled() != null
                && config.getAttachmentPullEnabled() != 1
                && StrUtil.equalsAny(dto.getActionCode(), "write_attachment", "update_attachment")) {
            throw BizException.validationFailed("当前模块接入配置未开启附件下载，不能配置依赖附件写入的动作编码");
        }
    }

    private StandardFieldMeta lookupStandardFieldMeta(String bizModule, String bizScene, String targetField) {
        if (StrUtil.isBlank(targetField)) {
            return null;
        }
        Map<String, StandardFieldMeta> sceneFieldMetaMap = STANDARD_FIELD_META_MAP.get(sceneKey(bizModule, bizScene));
        return sceneFieldMetaMap == null ? null : sceneFieldMetaMap.get(targetField);
    }

    private void validateSimpleJsonPath(String path, String fieldLabel) {
        String normalized = StrUtil.trimToNull(path);
        if (normalized == null) {
            return;
        }
        String body = normalized.startsWith("$.") ? normalized.substring(2) : normalized.startsWith("$") ? normalized.substring(1) : normalized;
        if (StrUtil.isBlank(body)) {
            throw BizException.validationFailed(fieldLabel + "不能为空");
        }
        String[] parts = body.split("\\.");
        for (String part : parts) {
            if (StrUtil.isBlank(part)) {
                throw BizException.validationFailed(fieldLabel + "格式不正确，仅支持简单对象路径和数组下标，如 $.data.items[0].value");
            }
            int firstBracket = part.indexOf('[');
            if (firstBracket < 0) {
                if (part.contains("]")) {
                    throw BizException.validationFailed(fieldLabel + "格式不正确，仅支持简单对象路径和数组下标，如 $.data.items[0].value");
                }
                continue;
            }
            int lastBracket = part.lastIndexOf(']');
            if (lastBracket != part.length() - 1 || part.indexOf('[', firstBracket + 1) >= 0 || firstBracket > lastBracket) {
                throw BizException.validationFailed(fieldLabel + "格式不正确，仅支持简单对象路径和数组下标，如 $.data.items[0].value");
            }
            String indexText = part.substring(firstBracket + 1, lastBracket);
            if (StrUtil.isBlank(indexText) || !indexText.chars().allMatch(Character::isDigit)) {
                throw BizException.validationFailed(fieldLabel + "中的数组下标必须是非负整数");
            }
            String fieldName = part.substring(0, firstBracket);
            if (fieldName.contains("[") || fieldName.contains("]")) {
                throw BizException.validationFailed(fieldLabel + "格式不正确，仅支持简单对象路径和数组下标，如 $.data.items[0].value");
            }
        }
    }

    private void validateModuleConfigPayload(IntegrationModuleConfigSaveDTO dto, IntegrationModuleConfig before,
                                             IntegrationProviderTemplate provider) {
        validateConfigBinaryFlag(dto.getEnabled(), "启用状态");
        validateAllowedValue(dto.getBizModule(), Set.of(BIZ_MODULE_PURCHASE_ORDER, BIZ_MODULE_SAMPLE_RETENTION, BIZ_MODULE_MORNING_CHECK, "supplier", "inbound"),
                "业务模块不合法");
        validateSceneCode(dto.getBizModule(), dto.getBizScene());
        validateAllowedValue(firstNonBlank(dto.getDefaultMode(), MODE_MANUAL), ALLOWED_DEFAULT_MODES, "默认维护方式仅支持 manual 或 third_party");
        validateConfigBinaryFlag(dto.getAllowDocumentSwitch(), "允许单据切换");
        validateConfigBinaryFlag(dto.getForceThirdParty(), "强制第三方");
        validateAllowedValue(firstNonBlank(dto.getTriggerStrategy(), MODE_MANUAL), ALLOWED_TRIGGER_STRATEGIES, "触发策略仅支持 manual、scheduler、callback");
        validateConfigBinaryFlag(dto.getAllowManualFallback(), "允许手工兜底");
        validateConfigBinaryFlag(dto.getAutoCoverEnabled(), "自动覆盖");
        validateAllowedValue(firstNonBlank(dto.getAutoCoverStrategy(), "merge"), ALLOWED_AUTO_COVER_STRATEGIES, "自动覆盖策略仅支持 merge 或 overwrite");
        validateConfigBinaryFlag(dto.getAllowManualConfirmCover(), "允许人工确认覆盖");
        validateConfigBinaryFlag(dto.getAttachmentPullEnabled(), "下载附件");
        validateConfigBinaryFlag(dto.getCallbackEnabled(), "启用回调");
        validateAllowedValue(firstNonBlank(dto.getTokenRequestMethod(), "POST"), ALLOWED_TOKEN_REQUEST_METHODS, "Token请求方式仅支持 GET 或 POST");
        validatePositiveLong(dto.getTimeoutMs(), "超时时间");
        validateNonNegativeInt(dto.getRetryMaxCount(), "最大重试次数");
        validatePositiveInt(dto.getSyncFrequencyMinutes(), "同步频率");
        validateHttpUrl(dto.getCallbackUrl(), "回调地址", false);
        validateHttpUrl(dto.getAccessTokenUrl(), "AccessToken地址", false);
        validateHttpUrl(dto.getRefreshTokenUrl(), "RefreshToken地址", false);
        if (StrUtil.isNotBlank(dto.getCallbackUrl())) {
            validateCallbackUrlTail(dto.getCallbackUrl(), dto.getProviderCode());
        }
        if (StrUtil.isNotBlank(dto.getRefreshTokenUrl()) && StrUtil.isBlank(dto.getAccessTokenUrl())) {
            throw BizException.validationFailed("配置 RefreshToken地址 前必须先填写 AccessToken地址");
        }
        if (StrUtil.isNotBlank(dto.getScheduleCron())) {
            if (!TRIGGER_STRATEGY_SCHEDULER.equals(dto.getTriggerStrategy())) {
                throw BizException.validationFailed("仅当触发策略为定时轮询时才允许填写计划Cron");
            }
            validateCronExpression(dto.getScheduleCron());
        }
        if (TRIGGER_STRATEGY_CALLBACK.equals(dto.getTriggerStrategy()) && (dto.getCallbackEnabled() == null || dto.getCallbackEnabled() != 1)) {
            throw BizException.validationFailed("触发策略为回调驱动时，必须同时启用回调");
        }
        if (dto.getCallbackEnabled() != null && dto.getCallbackEnabled() == 1) {
            if (provider.getCallbackSupported() == null || provider.getCallbackSupported() != 1) {
                throw BizException.validationFailed("当前平台模板未开启回调支持，不能启用回调");
            }
            if (StrUtil.isBlank(dto.getCallbackUrl())) {
                throw BizException.validationFailed("启用回调时必须填写回调地址");
            }
        }
        if (dto.getForceThirdParty() != null && dto.getForceThirdParty() == 1 && MODE_MANUAL.equals(dto.getDefaultMode())) {
            throw BizException.validationFailed("强制第三方开启时，默认维护方式必须选择第三方接口");
        }
        validateModuleConfigSecrets(provider, before == null ? null : before.getId(), dto.getSecrets(), dto);
    }

    private void validateModuleConfigSecrets(IntegrationProviderTemplate provider, Long configId,
                                             List<IntegrationSecretInputDTO> secrets, IntegrationModuleConfigSaveDTO dto) {
        Map<String, String> effectiveSecrets = buildEffectiveSecretMap(configId, secrets);
        Set<String> duplicateKeys = new LinkedHashSet<>();
        Set<String> usedKeys = new LinkedHashSet<>();
        if (secrets != null) {
            for (IntegrationSecretInputDTO secret : secrets) {
                if (secret == null) {
                    continue;
                }
                String secretKey = StrUtil.trimToEmpty(secret.getSecretKey());
                String normalizedKey = secretKey.toLowerCase(Locale.ROOT);
                boolean hasValue = StrUtil.isNotBlank(secret.getSecretValue());
                if (StrUtil.isBlank(secretKey) && hasValue) {
                    throw BizException.validationFailed("存在仅填写了密钥值但未填写参数名的密钥配置");
                }
                if (StrUtil.isBlank(secretKey)) {
                    continue;
                }
                if (!usedKeys.add(normalizedKey)) {
                    duplicateKeys.add(secretKey);
                }
            }
        }
        if (!duplicateKeys.isEmpty()) {
            throw BizException.validationFailed("接入密钥参数名不能重复：" + String.join("、", duplicateKeys));
        }
        String authType = firstNonBlank(provider == null ? null : provider.getAuthType(), "bearer");
        if ("bearer".equals(authType)) {
            if (StrUtil.isBlank(dto.getAccessTokenUrl()) && !hasAnySecret(effectiveSecrets, "accessToken", "ACCESS_TOKEN", "token")) {
                throw BizException.validationFailed("Bearer鉴权至少需要配置固定令牌，或填写 AccessToken地址");
            }
        } else if ("app_secret".equals(authType)) {
            assertAnySecretPresent(effectiveSecrets, "鉴权参数 clientId/appKey", "clientId", "appKey", "ClientId");
            assertAnySecretPresent(effectiveSecrets, "鉴权参数 clientSecret/appSecret", "clientSecret", "appSecret", "ClientSecret");
        } else if ("oauth2".equals(authType)) {
            assertAnySecretPresent(effectiveSecrets, "鉴权参数 clientId/appKey", "clientId", "appKey", "ClientId");
            assertAnySecretPresent(effectiveSecrets, "鉴权参数 clientSecret/appSecret", "clientSecret", "appSecret", "ClientSecret");
            if (StrUtil.isBlank(dto.getAccessTokenUrl())) {
                throw BizException.validationFailed("OAuth2鉴权必须填写 AccessToken地址");
            }
        }
        if (dto.getCallbackEnabled() != null && dto.getCallbackEnabled() == 1) {
            assertAnySecretPresent(effectiveSecrets, "回调IP白名单", "callbackIpWhitelist", "ipWhitelist", "callbackSourceIps");
            assertAnySecretPresent(effectiveSecrets, "回调签名密钥", "callbackSignSecret", "signatureSecret", "appSecret", "clientSecret");
        }
    }

    private Map<String, String> buildEffectiveSecretMap(Long configId, List<IntegrationSecretInputDTO> secrets) {
        Map<String, String> effective = configId == null ? new LinkedHashMap<>() : loadSecretPlainMap(configId);
        if (secrets == null) {
            return effective;
        }
        for (IntegrationSecretInputDTO secret : secrets) {
            if (secret == null || StrUtil.isBlank(secret.getSecretKey())) {
                continue;
            }
            if (StrUtil.isNotBlank(secret.getSecretValue())) {
                effective.put(secret.getSecretKey(), secret.getSecretValue());
            } else if (!effective.containsKey(secret.getSecretKey())) {
                effective.put(secret.getSecretKey(), null);
            }
        }
        return effective;
    }

    private void validateConfigBinaryFlag(Integer value, String fieldName) {
        if (value == null || (value != 0 && value != 1)) {
            throw BizException.validationFailed(fieldName + "仅支持 0 或 1");
        }
    }

    private void validateAllowedValue(String value, Set<String> allowedValues, String message) {
        if (StrUtil.isBlank(value) || allowedValues == null || !allowedValues.contains(value)) {
            throw BizException.validationFailed(message);
        }
    }

    private void validateSceneCode(String bizModule, String bizScene) {
        if (!ALLOWED_SCENE_CODES.contains(sceneKey(bizModule, bizScene))) {
            throw BizException.validationFailed("业务模块与业务场景组合不合法");
        }
    }

    private void validatePositiveLong(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw BizException.validationFailed(fieldName + "必须大于0");
        }
    }

    private void validatePositiveInt(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw BizException.validationFailed(fieldName + "必须大于0");
        }
    }

    private void validateNonNegativeInt(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw BizException.validationFailed(fieldName + "不能小于0");
        }
    }

    private void validateHttpUrl(String rawUrl, String fieldName, boolean required) {
        if (StrUtil.isBlank(rawUrl)) {
            if (required) {
                throw BizException.validationFailed(fieldName + "不能为空");
            }
            return;
        }
        try {
            URI uri = URI.create(rawUrl);
            String scheme = StrUtil.blankToDefault(uri.getScheme(), "").toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                throw BizException.validationFailed(fieldName + "必须是 http/https 地址");
            }
            if (StrUtil.isBlank(uri.getHost())) {
                throw BizException.validationFailed(fieldName + "必须填写完整域名");
            }
        } catch (IllegalArgumentException ex) {
            throw BizException.validationFailed(fieldName + "格式不正确，请填写有效的 http/https 地址");
        }
    }

    private void validateCronExpression(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException ex) {
            throw BizException.validationFailed("计划Cron格式不正确，请检查后重试");
        }
    }

    private void validateCallbackUrlTail(String callbackUrl, String providerCode) {
        URI uri = URI.create(callbackUrl);
        String path = StrUtil.blankToDefault(uri.getPath(), "");
        List<String> segments = Arrays.stream(path.split("/"))
                .filter(StrUtil::isNotBlank)
                .toList();
        String tail = segments.isEmpty() ? null : segments.get(segments.size() - 1);
        if (!Objects.equals(tail, providerCode)) {
            throw BizException.validationFailed("回调地址最后一段必须与平台编码保持一致");
        }
    }

    private boolean hasAnySecret(Map<String, String> secrets, String... candidateKeys) {
        if (secrets == null || candidateKeys == null) {
            return false;
        }
        for (String key : candidateKeys) {
            if (StrUtil.isNotBlank(secrets.get(key))) {
                return true;
            }
        }
        return false;
    }

    private void assertAnySecretPresent(Map<String, String> secrets, String displayName, String... candidateKeys) {
        if (!hasAnySecret(secrets, candidateKeys)) {
            throw BizException.validationFailed("缺少必填密钥：" + displayName);
        }
    }

    private void ensureModuleConfigUnique(Long tenantId, Long orgId, String bizModule, String bizScene, String providerCode, Long excludeId) {
        Long count = moduleConfigMapper.selectCount(new LambdaQueryWrapper<IntegrationModuleConfig>()
                .eq(tenantId != null, IntegrationModuleConfig::getTenantId, tenantId)
                .eq(IntegrationModuleConfig::getOrgId, orgId)
                .eq(IntegrationModuleConfig::getBizModule, bizModule)
                .eq(IntegrationModuleConfig::getBizScene, bizScene)
                .eq(IntegrationModuleConfig::getProviderCode, providerCode)
                .ne(excludeId != null, IntegrationModuleConfig::getId, excludeId));
        if (count != null && count > 0) {
            throw BizException.conflict("相同组织、模块、场景、平台的配置已存在");
        }
    }

    private void validateProviderForScene(IntegrationProviderTemplate provider, String bizModule, String bizScene) {
        List<String> scenes = parseStringList(provider.getSceneCodes());
        String sceneCode = bizModule + ":" + bizScene;
        if (!scenes.isEmpty() && !scenes.contains(sceneCode)) {
            throw BizException.validationFailed("当前平台模板不支持该业务场景");
        }
    }

    private void normalizeConfig(IntegrationModuleConfig config) {
        if (config.getForceThirdParty() != null && config.getForceThirdParty() == 1) {
            config.setDefaultMode(MODE_THIRD_PARTY);
            config.setAllowDocumentSwitch(0);
        }
        if (StrUtil.isBlank(config.getTokenRequestMethod())) {
            config.setTokenRequestMethod("POST");
        }
        if (config.getTimeoutMs() == null) {
            config.setTimeoutMs(10_000L);
        }
        if (config.getRetryMaxCount() == null) {
            config.setRetryMaxCount(3);
        }
        if (config.getSyncFrequencyMinutes() == null) {
            config.setSyncFrequencyMinutes(60);
        }
    }

    private void replaceSecrets(Long configId, Long orgId, List<IntegrationSecretInputDTO> secrets) {
        List<IntegrationSecretConfig> existing = secretConfigMapper.selectList(new LambdaQueryWrapper<IntegrationSecretConfig>()
                .eq(IntegrationSecretConfig::getConfigId, configId));
        Map<String, IntegrationSecretConfig> existingMap = existing.stream()
                .collect(Collectors.toMap(IntegrationSecretConfig::getSecretKey, item -> item, (a, b) -> a, LinkedHashMap::new));
        secretConfigMapper.delete(new LambdaQueryWrapper<IntegrationSecretConfig>().eq(IntegrationSecretConfig::getConfigId, configId));
        if (secrets == null) {
            for (IntegrationSecretConfig item : existing) {
                item.setId(null);
                secretConfigMapper.insert(item);
            }
            return;
        }
        Set<String> usedKeys = new LinkedHashSet<>();
        for (IntegrationSecretInputDTO secret : secrets) {
            if (StrUtil.isBlank(secret.getSecretKey())) {
                continue;
            }
            usedKeys.add(secret.getSecretKey());
            IntegrationSecretConfig entity = new IntegrationSecretConfig();
            entity.setConfigId(configId);
            entity.setSecretKey(secret.getSecretKey());
            if (StrUtil.isNotBlank(secret.getSecretValue())) {
                entity.setSecretValue(aiConfigCryptoService.encrypt(secret.getSecretValue()));
                entity.setSecretMask(aiConfigCryptoService.mask(secret.getSecretValue()));
                entity.setEncryptedFlag(1);
            } else {
                IntegrationSecretConfig old = existingMap.get(secret.getSecretKey());
                if (old == null) {
                    continue;
                }
                entity.setSecretValue(old.getSecretValue());
                entity.setSecretMask(old.getSecretMask());
                entity.setEncryptedFlag(old.getEncryptedFlag());
            }
            entity.setOrgId(orgId);
            entity.setTenantId(currentTenantId());
            secretConfigMapper.insert(entity);
        }
        for (IntegrationSecretConfig old : existing) {
            if (usedKeys.contains(old.getSecretKey())) {
                continue;
            }
            old.setId(null);
            secretConfigMapper.insert(old);
        }
    }

    private void seedDefaultStatusMappings(IntegrationModuleConfig config) {
        Long existing = statusMappingMapper.selectCount(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getConfigId, config.getId()));
        if (existing != null && existing > 0) {
            return;
        }
        List<IntegrationStatusMapping> defaults = new ArrayList<>();
        if ("purchase_order".equals(config.getBizModule()) && "logistics".equals(config.getBizScene()) && "sf_express".equals(config.getProviderCode())) {
            defaults.add(buildStatusMapping(config, "COLLECTED", "已揽收", "shipped", 0, 1, "update_shipped_at", 10));
            defaults.add(buildStatusMapping(config, "IN_TRANSIT", "运输中", "in_transit", 0, 0, null, 20));
            defaults.add(buildStatusMapping(config, "SIGNED", "已签收", "arrived", 1, 1, "update_arrived_at", 30));
        }
        for (IntegrationStatusMapping item : defaults) {
            statusMappingMapper.insert(item);
        }
    }

    private IntegrationStatusMapping buildStatusMapping(IntegrationModuleConfig config, String sourceCode, String sourceName,
                                                        String targetCode, int finishFlag, int triggerAction,
                                                        String actionCode, int sortNo) {
        IntegrationStatusMapping mapping = new IntegrationStatusMapping();
        mapping.setConfigId(config.getId());
        mapping.setSourceStatusCode(sourceCode);
        mapping.setSourceStatusName(sourceName);
        mapping.setTargetStatusCode(targetCode);
        mapping.setFinishFlag(finishFlag);
        mapping.setTriggerBusinessAction(triggerAction);
        mapping.setActionCode(actionCode);
        mapping.setWriteAttachmentFlag(0);
        mapping.setSortNo(sortNo);
        mapping.setEnabled(1);
        mapping.setOrgId(config.getOrgId());
        mapping.setTenantId(config.getTenantId());
        return mapping;
    }

    private IntegrationBinding upsertBinding(IntegrationModuleConfig config, IntegrationSyncTaskTriggerDTO dto, String maintenanceMode) {
        Long bizId = parsePositiveBizId(dto.getBizId());
        IntegrationBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getConfigId, config.getId())
                .eq(IntegrationBinding::getBizId, bizId)
                .eq(IntegrationBinding::getBizScene, dto.getBizScene()));
        if (binding == null) {
            binding = new IntegrationBinding();
            binding.setConfigId(config.getId());
            binding.setProviderCode(config.getProviderCode());
            binding.setBizModule(dto.getBizModule());
            binding.setBizScene(dto.getBizScene());
            binding.setBizId(bizId);
            binding.setBizNo(dto.getBizNo());
            binding.setExternalNo(dto.getExternalNo());
            binding.setMaintenanceMode(maintenanceMode);
            binding.setModeSource(config.getForceThirdParty() != null && config.getForceThirdParty() == 1 ? "forced" : firstNonBlank(dto.getModeSource(), "user_selected"));
            binding.setModeLocked(config.getForceThirdParty() != null && config.getForceThirdParty() == 1 ? 1 : Optional.ofNullable(dto.getModeLocked()).orElse(0));
            binding.setSyncStatus(TASK_STATUS_PENDING);
            binding.setFirstBindAt(LocalDateTime.now());
            binding.setOrgId(config.getOrgId());
            binding.setTenantId(config.getTenantId());
            bindingMapper.insert(binding);
        } else {
            binding.setExternalNo(dto.getExternalNo());
            binding.setMaintenanceMode(maintenanceMode);
            binding.setModeSource(config.getForceThirdParty() != null && config.getForceThirdParty() == 1 ? "forced" : firstNonBlank(dto.getModeSource(), binding.getModeSource(), "user_selected"));
            binding.setModeLocked(config.getForceThirdParty() != null && config.getForceThirdParty() == 1 ? 1 : Optional.ofNullable(dto.getModeLocked()).orElse(binding.getModeLocked()));
            bindingMapper.updateById(binding);
        }
        return binding;
    }

    private IntegrationSyncTask createTask(IntegrationModuleConfig config, IntegrationBinding binding, String taskType,
                                           String triggerType, Integer retryCount, Long operatorId, String operatorName) {
        IntegrationSyncTask task = new IntegrationSyncTask();
        task.setTaskNo(generateTaskNo());
        task.setBindingId(firstNonNull(binding.getId(), 0L));
        task.setConfigId(config.getId());
        task.setTaskType(taskType);
        task.setTriggerType(firstNonBlank(triggerType, TRIGGER_TYPE_MANUAL));
        task.setTaskStatus(TASK_STATUS_PENDING);
        task.setPlanExecuteAt(LocalDateTime.now());
        task.setRetryCount(retryCount);
        task.setOperatorId(operatorId);
        task.setOperatorName(operatorName);
        task.setBizId(binding.getBizId());
        task.setBizNo(binding.getBizNo());
        task.setBizModule(binding.getBizModule());
        task.setBizScene(binding.getBizScene());
        task.setExternalNo(binding.getExternalNo());
        task.setOrgId(binding.getOrgId());
        task.setTenantId(binding.getTenantId());
        syncTaskMapper.insert(task);
        return task;
    }

    private IntegrationExecutionResultVO executeTask(IntegrationSyncTask task, IntegrationModuleConfig config, IntegrationBinding binding,
                                                     RequestSpec requestOverride, JsonNode callbackPayload,
                                                     TaskExecutionOptions options) {
        LocalDateTime start = LocalDateTime.now();
        markTaskRunning(task, start);
        IntegrationExecutionResultVO result = new IntegrationExecutionResultVO();
        result.setTaskId(task.getId());
        result.setTaskNo(task.getTaskNo());
        result.setBindingId(binding.getId());
        Map<String, String> secrets = loadSecretPlainMap(config.getId());
        IntegrationProviderTemplate provider = getProviderByCode(config.getProviderCode(), true);
        RequestSpec requestSpec = requestOverride != null ? requestOverride : buildRequestSpec(config, provider, binding, secrets);
        RequestLogSnapshot requestSnapshot = callbackPayload == null ? buildRequestLogSnapshot(requestSpec) : null;
        String requestPayload = requestSnapshot == null ? null : requestSnapshot.requestPayload();
        String requestHeaders = requestSnapshot == null ? null : requestSnapshot.requestHeaders();
        String requestBody = requestSnapshot == null ? null : requestSnapshot.requestBody();
        String responsePayload = callbackPayload == null ? null : safeJson(callbackPayload);
        String normalizedPayload = null;
        String writeBackResult = null;
        Long auditLogId = null;
        boolean writeBackAttempted = false;
        FileTransferBatchResult fileTransferBatch = FileTransferBatchResult.empty();
        try {
            ThirdPartyResponseResult responseResult = callbackPayload != null
                    ? new ThirdPartyResponseResult(callbackPayload, requestSnapshot, responsePayload)
                    : requestThirdParty(provider, config, secrets, requestSpec);
            requestSnapshot = responseResult.requestSnapshot();
            requestPayload = requestSnapshot == null ? null : requestSnapshot.requestPayload();
            requestHeaders = requestSnapshot == null ? null : requestSnapshot.requestHeaders();
            requestBody = requestSnapshot == null ? null : requestSnapshot.requestBody();
            JsonNode responseNode = responseResult.responseNode();
            responsePayload = firstNonBlank(responseResult.responsePayload(), safeJson(responseNode));
            NormalizedResult normalized = normalizeResponse(config, provider, responseNode, binding.getExternalNo());
            normalizedPayload = safeJson(normalized.data());
            if (!TASK_STATUS_SUCCESS.equals(normalized.processStatus())) {
                long durationMs = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
                if (TASK_STATUS_NO_DATA.equals(normalized.processStatus()) || TASK_STATUS_MAPPING_MISSING.equals(normalized.processStatus())) {
                    updateTaskAndBindingAfterSpecialStatus(task, binding, config, normalized.processStatus(), normalized.processMessage(), normalized.statusMatch(), options);
                } else {
                    updateTaskAndBindingAfterFailure(task, binding, config, normalized.processMessage(), options);
                }
                Long syncLogId = createSyncLog(task, binding, config, new SyncLogCreatePayload(
                        requestPayload,
                        requestHeaders,
                        requestBody,
                        responsePayload,
                        normalizedPayload,
                        normalized.processStatus(),
                        resolveNormalizedErrorCode(normalized.processStatus()),
                        normalized.processMessage(),
                        normalized.processMessage(),
                        null,
                        durationMs,
                        null
                ));
                result.setSyncStatus(normalized.processStatus());
                result.setMessage(normalized.processMessage());
                result.setNormalizedPayload(normalizedPayload);
                result.setDownloadedFileCount(0);
                result.setFailedFileCount(0);
                result.setSkippedFileCount(0);
                result.setReusedFileCount(0);
                result.setSyncLogId(syncLogId);
                return result;
            }
            fileTransferBatch = maybeTransferFiles(config, provider, binding, task, normalized);
            List<FileTransferResult> fileResults = fileTransferBatch.availableFiles();
            boolean writeBack = options.writeBackAllowed() && shouldWriteBack(task, config, binding);
            String resultMessage = writeBack ? "同步成功并已回写业务数据" : "同步成功，当前任务仅查询记录结果，不覆盖业务数据";
            resultMessage = appendFileTransferSummary(resultMessage, summarizeFileTransferBatch(fileTransferBatch, false));
            if (writeBack) {
                writeBackAttempted = true;
                auditLogId = writeBackToBusiness(config, binding, normalized, fileResults);
                writeBackResult = appendFileTransferSummary("已回写业务数据", summarizeFileTransferBatch(fileTransferBatch, true));
            } else {
                writeBackResult = appendFileTransferSummary("本次任务为只查询不覆盖，未执行业务回写", summarizeFileTransferBatch(fileTransferBatch, true));
            }
            long durationMs = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
            updateTaskAndBindingAfterSuccess(task, binding, config, normalized, durationMs, options);
            Long syncLogId = createSyncLog(task, binding, config, new SyncLogCreatePayload(
                    requestPayload,
                    requestHeaders,
                    requestBody,
                    responsePayload,
                    normalizedPayload,
                    TASK_STATUS_SUCCESS,
                    null,
                    null,
                    resultMessage,
                    writeBackResult,
                    durationMs,
                    auditLogId
            ));
            attachFileRecordsToSyncContext(task.getId(), syncLogId, auditLogId);
            result.setSyncStatus(TASK_STATUS_SUCCESS);
            result.setMessage(resultMessage);
            result.setNormalizedPayload(normalizedPayload);
            populateExecutionResultFileTransfer(result, fileTransferBatch);
            result.setSyncLogId(syncLogId);
            result.setAuditLogId(auditLogId);
            result.setWriteBackResult(writeBackResult);
            return result;
        } catch (ThirdPartyRequestException ex) {
            log.error("执行第三方同步失败, taskId={}", task.getId(), ex);
            long durationMs = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
            RequestLogSnapshot failedSnapshot = firstNonNull(ex.requestSnapshot(), requestSnapshot);
            updateTaskAndBindingAfterFailure(task, binding, config, ex.getMessage(), options);
            Long syncLogId = createSyncLog(task, binding, config, new SyncLogCreatePayload(
                    failedSnapshot == null ? requestPayload : failedSnapshot.requestPayload(),
                    failedSnapshot == null ? requestHeaders : failedSnapshot.requestHeaders(),
                    failedSnapshot == null ? requestBody : failedSnapshot.requestBody(),
                    firstNonBlank(ex.responsePayload(), responsePayload),
                    normalizedPayload,
                    TASK_STATUS_FAILED,
                    ex.errorCode(),
                    ex.getMessage(),
                    ex.getMessage(),
                    writeBackAttempted && StrUtil.isBlank(writeBackResult) ? "业务回写失败：" + ex.getMessage() : writeBackResult,
                    durationMs,
                    auditLogId
            ));
            attachFileRecordsToSyncContext(task.getId(), syncLogId, auditLogId);
            result.setSyncStatus(TASK_STATUS_FAILED);
            result.setMessage(firstNonBlank(ex.getMessage(), "同步失败"));
            result.setNormalizedPayload(normalizedPayload);
            populateExecutionResultFileTransfer(result, fileTransferBatch);
            result.setSyncLogId(syncLogId);
            result.setAuditLogId(auditLogId);
            result.setWriteBackResult(writeBackAttempted && StrUtil.isBlank(writeBackResult) ? "业务回写失败：" + ex.getMessage() : writeBackResult);
            return result;
        } catch (Exception ex) {
            log.error("执行第三方同步失败, taskId={}", task.getId(), ex);
            long durationMs = java.time.Duration.between(start, LocalDateTime.now()).toMillis();
            updateTaskAndBindingAfterFailure(task, binding, config, ex.getMessage(), options);
            if (writeBackAttempted && StrUtil.isBlank(writeBackResult)) {
                writeBackResult = "业务回写失败：" + firstNonBlank(ex.getMessage(), "未知异常");
            }
            Long syncLogId = createSyncLog(task, binding, config, new SyncLogCreatePayload(
                    requestPayload,
                    requestHeaders,
                    requestBody,
                    responsePayload,
                    normalizedPayload,
                    TASK_STATUS_FAILED,
                    resolveSyncLogErrorCode(ex),
                    ex.getMessage(),
                    ex.getMessage(),
                    writeBackResult,
                    durationMs,
                    auditLogId
            ));
            attachFileRecordsToSyncContext(task.getId(), syncLogId, auditLogId);
            result.setSyncStatus(TASK_STATUS_FAILED);
            result.setMessage(firstNonBlank(ex.getMessage(), "同步失败"));
            result.setNormalizedPayload(normalizedPayload);
            populateExecutionResultFileTransfer(result, fileTransferBatch);
            result.setSyncLogId(syncLogId);
            result.setAuditLogId(auditLogId);
            result.setWriteBackResult(writeBackResult);
            return result;
        }
    }

    private RequestSpec buildRequestSpec(IntegrationModuleConfig config, IntegrationProviderTemplate provider,
                                         IntegrationBinding binding, Map<String, String> secrets) {
        JsonNode template = parseStoredTemplate(provider.getRequestTemplate(), true);
        Map<String, String> variables = buildTemplateVariables(config, binding, secrets);
        if (template == null || template.isNull() || template.isMissingNode()) {
            return new RequestSpec(null, "GET", Collections.emptyMap(), Collections.emptyMap(), null, null);
        }
        String url = interpolate(findString(template, "url"), variables);
        String method = firstNonBlank(interpolate(findString(template, "method"), variables), "GET");
        Map<String, String> headers = interpolateStringMap(readStringMap(template.path("headers")), variables);
        Map<String, Object> query = interpolateObjectMap(readObjectMap(template.path("query")), variables);
        Object body = interpolateObject(readObject(template.path("body")), variables);
        JsonNode testResponse = template.path("testResponse");
        if (testResponse == null || testResponse.isMissingNode() || testResponse.isNull()) {
            testResponse = parseStoredTemplate(provider.getResponseTemplate(), false);
        }
        return new RequestSpec(url, method, headers, query, body, testResponse);
    }

    private ThirdPartyResponseResult requestThirdParty(IntegrationProviderTemplate provider, IntegrationModuleConfig config,
                                                       Map<String, String> secrets, RequestSpec requestSpec) {
        if (StrUtil.isBlank(requestSpec.url())) {
            throw BizException.validationFailed(PROVIDER_RUNTIME_URL_MISSING_ERROR);
        }
        String circuitKey = buildProviderCircuitKey(provider, config);
        assertProviderCircuitClosed(circuitKey);
        int totalAttempts = resolveTotalAttempts(config);
        boolean refreshedToken = false;
        Throwable lastError = null;
        RequestAttemptContext attemptContext = new RequestAttemptContext();
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                ThirdPartyResponseResult response = doRequestThirdParty(provider, config, secrets, requestSpec, refreshedToken, attemptContext);
                recordProviderRequestSuccess(circuitKey);
                return response;
            } catch (ThirdPartyRequestException ex) {
                lastError = ex;
                if (!refreshedToken && ex.refreshable()) {
                    refreshedToken = true;
                    continue;
                }
                if (ex.retryable() && attempt < totalAttempts) {
                    applyRetryBackoff(attempt, ex.rateLimited());
                    continue;
                }
                recordProviderRequestFailure(circuitKey, ex.rateLimited());
                throw ex;
            }
        }
        boolean rateLimited = lastError instanceof ThirdPartyRequestException requestException && requestException.rateLimited();
        recordProviderRequestFailure(circuitKey, rateLimited);
        if (lastError instanceof ThirdPartyRequestException requestException) {
            throw requestException;
        }
        throw new ThirdPartyRequestException(
                SYNC_LOG_ERROR_CODE_NETWORK,
                "第三方请求失败：" + (lastError == null ? "未知异常" : normalizeRestClientMessage(lastError)),
                attemptContext.requestSnapshot,
                null,
                null,
                false,
                false
        );
    }

    private ThirdPartyResponseResult doRequestThirdParty(IntegrationProviderTemplate provider, IntegrationModuleConfig config,
                                                         Map<String, String> secrets, RequestSpec requestSpec,
                                                         boolean forceRefreshToken, RequestAttemptContext attemptContext) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        requestSpec.headers().forEach(headers::set);
        String accessToken = resolveAccessToken(config, secrets, forceRefreshToken);
        if (StrUtil.isNotBlank(accessToken)) {
            headers.setBearerAuth(accessToken);
        }
        URI uri = buildUri(requestSpec.url(), requestSpec.query());
        HttpEntity<Object> entity = new HttpEntity<>(requestSpec.body(), headers);
        RequestLogSnapshot snapshot = buildRequestLogSnapshot(requestSpec, uri, headers, entity.getBody());
        if (attemptContext != null) {
            attemptContext.requestSnapshot = snapshot;
        }
        try {
            ResponseEntity<String> response = aiRestTemplate.exchange(uri, HttpMethod.valueOf(requestSpec.method().toUpperCase(Locale.ROOT)), entity, String.class);
            return new ThirdPartyResponseResult(readTree(response.getBody()), snapshot, response.getBody());
        } catch (RestClientResponseException ex) {
            throw new ThirdPartyRequestException(
                    resolveSyncLogHttpErrorCode(ex),
                    buildThirdPartyRequestError(ex),
                    snapshot,
                    ex.getResponseBodyAsString(),
                    ex.getRawStatusCode(),
                    shouldRefreshToken(ex, config),
                    ex.getRawStatusCode() == 429
            );
        } catch (ResourceAccessException ex) {
            throw new ThirdPartyRequestException(
                    resolveSyncLogTransportErrorCode(ex),
                    "第三方请求失败：" + normalizeRestClientMessage(ex),
                    snapshot,
                    null,
                    null,
                    false,
                    false
            );
        } catch (RestClientException ex) {
            throw new ThirdPartyRequestException(
                    SYNC_LOG_ERROR_CODE_NETWORK,
                    "第三方请求失败：" + normalizeRestClientMessage(ex),
                    snapshot,
                    null,
                    null,
                    false,
                    false
            );
        }
    }

    private String resolveAccessToken(IntegrationModuleConfig config, Map<String, String> secrets, boolean forceRefresh) {
        String existing = firstNonBlank(
                secrets.get("accessToken"),
                secrets.get("ACCESS_TOKEN"),
                secrets.get("token")
        );
        if (!forceRefresh && (StrUtil.isNotBlank(existing) || StrUtil.isBlank(config.getAccessTokenUrl()))) {
            return existing;
        }
        String tokenUrl = forceRefresh && StrUtil.isNotBlank(config.getRefreshTokenUrl())
                ? config.getRefreshTokenUrl()
                : config.getAccessTokenUrl();
        if (StrUtil.isBlank(tokenUrl)) {
            return existing;
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("clientId", firstNonBlank(secrets.get("clientId"), secrets.get("appKey"), secrets.get("ClientId")));
        body.put("clientSecret", firstNonBlank(secrets.get("clientSecret"), secrets.get("appSecret"), secrets.get("ClientSecret")));
        if (forceRefresh && StrUtil.isNotBlank(config.getRefreshTokenUrl())) {
            body.put("grantType", "refresh_token");
            body.put("refreshToken", firstNonBlank(secrets.get("refreshToken"), secrets.get("REFRESH_TOKEN")));
            body.put("refresh_token", firstNonBlank(secrets.get("refreshToken"), secrets.get("REFRESH_TOKEN")));
        } else {
            body.put("grantType", "client_credentials");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = aiRestTemplate.exchange(tokenUrl, HttpMethod.valueOf(firstNonBlank(config.getTokenRequestMethod(), "POST")), entity, String.class);
            JsonNode tokenNode = readTree(response.getBody());
            String accessToken = firstNonBlank(findString(tokenNode, "accessToken"), findString(tokenNode, "access_token"), findString(tokenNode, "token"));
            if (StrUtil.isBlank(accessToken)) {
                throw BizException.of(TOKEN_RESPONSE_EMPTY_ERROR);
            }
            secrets.put("accessToken", accessToken);
            String refreshToken = firstNonBlank(findString(tokenNode, "refreshToken"), findString(tokenNode, "refresh_token"));
            if (StrUtil.isNotBlank(refreshToken)) {
                secrets.put("refreshToken", refreshToken);
            }
            persistTokenSecret(config, secrets, accessToken, refreshToken);
            return accessToken;
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw BizException.of("获取 AccessToken 失败：" + normalizeRestClientMessage(ex));
        }
    }

    private int resolveFieldMappingSortNo(IntegrationModuleConfig config, IntegrationFieldMappingSaveDTO dto, IntegrationFieldMapping before) {
        Integer builtinSort = lookupBuiltinFieldSort(config.getBizModule(), config.getBizScene(), dto.getTargetField());
        if (builtinSort != null) {
            return builtinSort;
        }
        if (before != null && before.getSortNo() != null && before.getSortNo() > 0) {
            return before.getSortNo();
        }
        return nextFieldSortNo(config.getId(), before == null ? null : before.getId());
    }

    private int resolveStatusMappingSortNo(IntegrationModuleConfig config, IntegrationStatusMappingSaveDTO dto, IntegrationStatusMapping before) {
        Integer builtinSort = lookupBuiltinStatusSort(config.getBizModule(), config.getBizScene(), dto.getTargetStatusCode());
        if (builtinSort != null) {
            return builtinSort;
        }
        if (before != null && before.getSortNo() != null && before.getSortNo() > 0) {
            return before.getSortNo();
        }
        return nextStatusSortNo(config.getId(), before == null ? null : before.getId());
    }

    private Comparator<IntegrationFieldMapping> buildFieldMappingComparator(Map<Long, IntegrationModuleConfig> configMap) {
        return Comparator
                .comparing((IntegrationFieldMapping item) -> item.getConfigId(), Comparator.nullsLast(Long::compareTo))
                .thenComparingInt(item -> resolveEffectiveFieldSortNo(configMap.get(item.getConfigId()), item))
                .thenComparing(item -> item.getId(), Comparator.nullsLast(Long::compareTo));
    }

    private Comparator<IntegrationStatusMapping> buildStatusMappingComparator(Map<Long, IntegrationModuleConfig> configMap) {
        return Comparator
                .comparing((IntegrationStatusMapping item) -> item.getConfigId(), Comparator.nullsLast(Long::compareTo))
                .thenComparingInt(item -> resolveEffectiveStatusSortNo(configMap.get(item.getConfigId()), item))
                .thenComparing(item -> item.getId(), Comparator.nullsLast(Long::compareTo));
    }

    private int resolveEffectiveFieldSortNo(IntegrationModuleConfig config, IntegrationFieldMapping entity) {
        if (entity.getSortNo() != null && entity.getSortNo() > 0) {
            return entity.getSortNo();
        }
        Integer builtinSort = lookupBuiltinFieldSort(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene(), entity.getTargetField());
        return builtinSort != null ? builtinSort : AUTO_SORT_FALLBACK_BASE;
    }

    private int resolveEffectiveStatusSortNo(IntegrationModuleConfig config, IntegrationStatusMapping entity) {
        if (entity.getSortNo() != null && entity.getSortNo() > 0) {
            return entity.getSortNo();
        }
        Integer builtinSort = lookupBuiltinStatusSort(config == null ? null : config.getBizModule(),
                config == null ? null : config.getBizScene(), entity.getTargetStatusCode());
        return builtinSort != null ? builtinSort : AUTO_SORT_FALLBACK_BASE;
    }

    private int nextFieldSortNo(Long configId, Long excludeId) {
        List<IntegrationFieldMapping> records = fieldMappingMapper.selectList(new LambdaQueryWrapper<IntegrationFieldMapping>()
                .eq(IntegrationFieldMapping::getConfigId, configId)
                .ne(excludeId != null, IntegrationFieldMapping::getId, excludeId));
        return nextAutoSortNo(records.stream().map(IntegrationFieldMapping::getSortNo).toList());
    }

    private int nextStatusSortNo(Long configId, Long excludeId) {
        List<IntegrationStatusMapping> records = statusMappingMapper.selectList(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getConfigId, configId)
                .ne(excludeId != null, IntegrationStatusMapping::getId, excludeId));
        return nextAutoSortNo(records.stream().map(IntegrationStatusMapping::getSortNo).toList());
    }

    private int nextAutoSortNo(List<Integer> sortValues) {
        int maxSort = sortValues.stream()
                .filter(Objects::nonNull)
                .filter(item -> item > 0)
                .max(Integer::compareTo)
                .orElse(0);
        if (maxSort <= 0) {
            return AUTO_SORT_STEP;
        }
        return ((maxSort / AUTO_SORT_STEP) + 1) * AUTO_SORT_STEP;
    }

    private Integer lookupBuiltinFieldSort(String bizModule, String bizScene, String targetField) {
        if (StrUtil.isBlank(targetField)) {
            return null;
        }
        Map<String, Integer> sceneSortMap = BUILTIN_FIELD_SORT_MAP.get(sceneKey(bizModule, bizScene));
        return sceneSortMap == null ? null : sceneSortMap.get(targetField);
    }

    private Integer lookupBuiltinStatusSort(String bizModule, String bizScene, String targetStatusCode) {
        if (StrUtil.isBlank(targetStatusCode)) {
            return null;
        }
        Map<String, Integer> sceneSortMap = BUILTIN_STATUS_SORT_MAP.get(sceneKey(bizModule, bizScene));
        return sceneSortMap == null ? null : sceneSortMap.get(targetStatusCode);
    }

    private boolean hasBuiltinFieldScene(String bizModule, String bizScene) {
        return BUILTIN_FIELD_SORT_MAP.containsKey(sceneKey(bizModule, bizScene));
    }

    private boolean hasBuiltinStatusScene(String bizModule, String bizScene) {
        return BUILTIN_STATUS_SORT_MAP.containsKey(sceneKey(bizModule, bizScene));
    }

    private static Map<String, Integer> buildSortOrderMap(String... codes) {
        Map<String, Integer> map = new LinkedHashMap<>();
        int sortNo = AUTO_SORT_STEP;
        for (String code : codes) {
            map.put(code, sortNo);
            sortNo += AUTO_SORT_STEP;
        }
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> buildStatusCodeSet(String... codes) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(codes)));
    }

    @SafeVarargs
    private static Map<String, StandardFieldMeta> buildFieldMetaMap(Map.Entry<String, StandardFieldMeta>... entries) {
        Map<String, StandardFieldMeta> map = new LinkedHashMap<>();
        for (Map.Entry<String, StandardFieldMeta> entry : entries) {
            map.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map.Entry<String, StandardFieldMeta> fieldMeta(String fieldCode, String valueType) {
        return fieldMeta(fieldCode, valueType, null);
    }

    private static Map.Entry<String, StandardFieldMeta> fieldMeta(String fieldCode, String valueType, Set<String> allowedValues) {
        return Map.entry(fieldCode, new StandardFieldMeta(valueType, allowedValues == null ? Collections.emptySet() : Set.copyOf(allowedValues)));
    }

    private static String sceneKey(String bizModule, String bizScene) {
        return StrUtil.blankToDefault(bizModule, "") + ":" + StrUtil.blankToDefault(bizScene, "");
    }

    private NormalizedResult normalizeResponse(IntegrationModuleConfig config, IntegrationProviderTemplate provider, JsonNode responseNode, String externalNo) {
        if (isResponseNodeEmpty(responseNode)) {
            Map<String, Object> emptyData = new LinkedHashMap<>();
            emptyData.put("externalNo", externalNo);
            return new NormalizedResult(emptyData, null, TASK_STATUS_NO_DATA, NO_DATA_MESSAGE);
        }
        List<IntegrationFieldMapping> mappings = fieldMappingMapper.selectList(new LambdaQueryWrapper<IntegrationFieldMapping>()
                .eq(IntegrationFieldMapping::getConfigId, config.getId())
                .eq(IntegrationFieldMapping::getEnabled, 1));
        mappings.sort(buildFieldMappingComparator(Map.of(config.getId(), config)));
        Map<String, Object> normalized = new LinkedHashMap<>(defaultNormalize(config.getBizScene(), responseNode, externalNo));
        if (!mappings.isEmpty()) {
            Set<String> mappedTargets = mappings.stream()
                    .map(IntegrationFieldMapping::getTargetField)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            mappedTargets.forEach(normalized::remove);
            for (IntegrationFieldMapping mapping : mappings) {
                Object value;
                try {
                    value = extractMappedValue(config, responseNode, mapping);
                } catch (BizException ex) {
                    FieldMappingFailureDecision decision = handleFieldMappingFailure(mapping, ex.getMessage(), ex);
                    if (!decision.continueProcessing()) {
                        return new NormalizedResult(normalized, null, decision.processStatus(), decision.processMessage());
                    }
                    continue;
                }
                if (value == null && mapping.getRequiredFlag() != null && mapping.getRequiredFlag() == 1) {
                    FieldMappingFailureDecision decision = handleFieldMappingFailure(mapping, "未取到有效值", null);
                    if (!decision.continueProcessing()) {
                        return new NormalizedResult(normalized, null, decision.processStatus(), decision.processMessage());
                    }
                    continue;
                }
                if (value != null) {
                    normalized.put(mapping.getTargetField(), value);
                }
            }
        }
        normalized.putIfAbsent("externalNo", externalNo);
        if (isNormalizedDataEmpty(normalized)) {
            return new NormalizedResult(normalized, null, TASK_STATUS_NO_DATA, NO_DATA_MESSAGE);
        }
        String sourceStatus = resolveSourceStatus(normalized);
        if (StrUtil.isNotBlank(sourceStatus)) {
            normalized.put("sourceStatus", sourceStatus);
        }
        StatusMatch statusMatch = matchStatus(config.getId(), normalized, sourceStatus);
        if (statusMatch != null) {
            normalized.put("status", statusMatch.targetStatus());
        } else if (StrUtil.isNotBlank(sourceStatus) && shouldMarkMappingMissing(config, sourceStatus)) {
            normalized.remove("status");
            return new NormalizedResult(normalized, null, TASK_STATUS_MAPPING_MISSING, buildMappingMissingMessage(config, sourceStatus));
        }
        return new NormalizedResult(normalized, statusMatch, TASK_STATUS_SUCCESS, null);
    }

    private Object extractMappedValue(IntegrationModuleConfig config, JsonNode responseNode, IntegrationFieldMapping mapping) {
        JsonNode sourceNode = StrUtil.isNotBlank(mapping.getSourcePath()) ? resolveJsonPath(responseNode, mapping.getSourcePath()) : findNode(responseNode, mapping.getSourceField());
        Object raw = nodeToValue(sourceNode);
        if (raw == null && StrUtil.isNotBlank(mapping.getDefaultValue())) {
            raw = mapping.getDefaultValue();
        }
        if (raw == null) {
            return null;
        }
        StandardFieldMeta fieldMeta = lookupStandardFieldMeta(config.getBizModule(), config.getBizScene(), mapping.getTargetField());
        Object transformed = applyFieldTransform(raw, mapping, true);
        return coerceFieldValueByMeta(fieldMeta, transformed, mapping.getTargetField(), true);
    }

    private Map<String, Object> defaultNormalize(String bizScene, JsonNode responseNode, String externalNo) {
        Map<String, Object> map = new LinkedHashMap<>();
        if ("logistics".equals(bizScene)) {
            map.put("trackingNo", firstNonBlank(findString(responseNode, "trackingNo"), findString(responseNode, "mailNo"), externalNo));
            map.put("company", firstNonBlank(findString(responseNode, "company"), findString(responseNode, "logisticsCompany"), "第三方物流"));
            map.put("status", firstNonBlank(findString(responseNode, "status"), findString(responseNode, "logisticsStatus")));
            map.put("shippedAt", firstNonBlank(findString(responseNode, "shippedAt"), findString(responseNode, "acceptTime")));
            map.put("arrivedAt", firstNonBlank(findString(responseNode, "arrivedAt"), findString(responseNode, "signedTime")));
            map.put("remark", firstNonBlank(findString(responseNode, "remark"), findString(responseNode, "routeDesc")));
        } else if ("inspection".equals(bizScene)) {
            map.put("reportNo", firstNonBlank(findString(responseNode, "reportNo"), findString(responseNode, "labReportNo"), externalNo));
            map.put("result", firstNonBlank(findString(responseNode, "result"), findString(responseNode, "inspectionResult")));
            map.put("agency", firstNonBlank(findString(responseNode, "agency"), findString(responseNode, "inspectionAgency")));
            map.put("inspectionAt", firstNonBlank(findString(responseNode, "inspectionAt"), findString(responseNode, "reportTime")));
            map.put("remark", firstNonBlank(findString(responseNode, "remark"), findString(responseNode, "summary")));
        } else if ("traceability".equals(bizScene)) {
            map.put("batchId", firstNonBlank(findString(responseNode, "batchId"), externalNo));
            map.put("origin", firstNonBlank(findString(responseNode, "origin"), findString(responseNode, "traceOrigin")));
            map.put("remark", firstNonBlank(findString(responseNode, "remark"), findString(responseNode, "summary")));
        } else if (BIZ_SCENE_SAMPLE_TASK.equals(bizScene)) {
            map.put("sampleTaskNo", firstNonBlank(findString(responseNode, "sampleTaskNo"), findString(responseNode, "taskNo"), externalNo));
            map.put("cookingTaskId", firstNonBlank(findString(responseNode, "cookingTaskId"), findString(responseNode, "taskId")));
            map.put("recipeName", firstNonBlank(findString(responseNode, "recipeName"), findString(responseNode, "menuName")));
            map.put("dishName", firstNonBlank(findString(responseNode, "dishName"), findString(responseNode, "dish")));
            map.put("requiredSampleWeight", firstNonBlank(findString(responseNode, "requiredSampleWeight"), findString(responseNode, "plannedWeight")));
            map.put("plannedSampleTime", firstNonBlank(findString(responseNode, "plannedSampleTime"), findString(responseNode, "samplePlanTime")));
            map.put("kitchenAreaId", firstNonBlank(findString(responseNode, "kitchenAreaId"), findString(responseNode, "areaId")));
            map.put("status", firstNonBlank(findString(responseNode, "status"), findString(responseNode, "taskStatus")));
        } else if (BIZ_SCENE_SAMPLE_RECORD.equals(bizScene)) {
            map.put("sampleRecordNo", firstNonBlank(findString(responseNode, "sampleRecordNo"), findString(responseNode, "recordNo"), externalNo));
            map.put("sampleTaskNo", firstNonBlank(findString(responseNode, "sampleTaskNo"), findString(responseNode, "taskNo")));
            map.put("sampleWeight", firstNonBlank(findString(responseNode, "sampleWeight"), findString(responseNode, "actualWeight")));
            map.put("samplerId", firstNonBlank(findString(responseNode, "samplerId"), findString(responseNode, "sampledBy")));
            map.put("sampledAt", firstNonBlank(findString(responseNode, "sampledAt"), findString(responseNode, "sampleTime")));
            map.put("storageLocation", firstNonBlank(findString(responseNode, "storageLocation"), findString(responseNode, "cabinetNo")));
            map.put("status", firstNonBlank(findString(responseNode, "status"), findString(responseNode, "recordStatus")));
        } else if (BIZ_SCENE_SAMPLE_RESULT.equals(bizScene)) {
            map.put("qualityScore", firstNonBlank(findString(responseNode, "qualityScore"), findString(responseNode, "score")));
            map.put("complianceStatus", firstNonBlank(findString(responseNode, "complianceStatus"), findString(responseNode, "compliance")));
            map.put("anomalyTags", nodeToValue(findNode(responseNode, "anomalyTags")));
            map.put("inspectionReportNo", firstNonBlank(findString(responseNode, "inspectionReportNo"), findString(responseNode, "reportNo")));
            map.put("destroyAt", firstNonBlank(findString(responseNode, "destroyAt"), findString(responseNode, "disposedAt")));
            map.put("status", firstNonBlank(findString(responseNode, "status"), findString(responseNode, "resultStatus")));
        } else if (BIZ_SCENE_SAMPLE_DEVICE.equals(bizScene)) {
            map.put("deviceCode", firstNonBlank(findString(responseNode, "deviceCode"), findString(responseNode, "code"), externalNo));
            map.put("deviceName", firstNonBlank(findString(responseNode, "deviceName"), findString(responseNode, "name")));
            map.put("manufacturer", firstNonBlank(findString(responseNode, "manufacturer"), findString(responseNode, "vendor")));
            map.put("model", firstNonBlank(findString(responseNode, "model"), findString(responseNode, "deviceModel")));
            map.put("onlineStatus", firstNonBlank(findString(responseNode, "onlineStatus"), findString(responseNode, "status")));
            map.put("storageTemperature", firstNonBlank(findString(responseNode, "storageTemperature"), findString(responseNode, "temperature")));
            map.put("weightPrecision", firstNonBlank(findString(responseNode, "weightPrecision"), findString(responseNode, "precision")));
        } else if (BIZ_SCENE_FACE_PROFILE.equals(bizScene)) {
            map.put("employeeCode", firstNonBlank(findString(responseNode, "employeeCode"), findString(responseNode, "staffCode")));
            map.put("employeeId", firstNonBlank(findString(responseNode, "employeeId"), findString(responseNode, "staffId")));
            map.put("employeeName", firstNonBlank(findString(responseNode, "employeeName"), findString(responseNode, "staffName")));
            map.put("externalFaceId", firstNonBlank(findString(responseNode, "externalFaceId"), findString(responseNode, "faceId"), externalNo));
            map.put("faceEnrollStatus", firstNonBlank(findString(responseNode, "faceEnrollStatus"), findString(responseNode, "status")));
            map.put("faceImageUrl", firstNonBlank(findString(responseNode, "faceImageUrl"), findString(responseNode, "faceImage")));
        } else if (BIZ_SCENE_HEALTH_CERTIFICATE.equals(bizScene)) {
            map.put("certificateNo", firstNonBlank(findString(responseNode, "certificateNo"), findString(responseNode, "certNo"), externalNo));
            map.put("issueDate", firstNonBlank(findString(responseNode, "issueDate"), findString(responseNode, "issuedAt")));
            map.put("expireDate", firstNonBlank(findString(responseNode, "expireDate"), findString(responseNode, "expiryDate")));
            map.put("certificateStatus", firstNonBlank(findString(responseNode, "certificateStatus"), findString(responseNode, "status")));
            map.put("employeeId", firstNonBlank(findString(responseNode, "employeeId"), findString(responseNode, "staffId")));
            map.put("employeeCode", firstNonBlank(findString(responseNode, "employeeCode"), findString(responseNode, "staffCode")));
            map.put("employeeName", firstNonBlank(findString(responseNode, "employeeName"), findString(responseNode, "staffName")));
            map.put("status", firstNonBlank(findString(responseNode, "certificateStatus"), findString(responseNode, "status")));
        } else if (BIZ_SCENE_MORNING_CHECK_RESULT.equals(bizScene)) {
            map.put("checkNo", firstNonBlank(findString(responseNode, "checkNo"), findString(responseNode, "morningCheckNo"), externalNo));
            map.put("externalCheckId", firstNonBlank(findString(responseNode, "externalCheckId"), findString(responseNode, "checkId"), externalNo));
            map.put("employeeId", firstNonBlank(findString(responseNode, "employeeId"), findString(responseNode, "staffId")));
            map.put("employeeCode", firstNonBlank(findString(responseNode, "employeeCode"), findString(responseNode, "staffCode")));
            map.put("employeeName", firstNonBlank(findString(responseNode, "employeeName"), findString(responseNode, "staffName")));
            map.put("deviceCode", firstNonBlank(findString(responseNode, "deviceCode"), findString(responseNode, "terminalCode")));
            map.put("faceCheckResult", firstNonBlank(findString(responseNode, "faceCheckResult"), findString(responseNode, "faceResult")));
            map.put("faceMatchScore", firstNonBlank(findString(responseNode, "faceMatchScore"), findString(responseNode, "matchScore")));
            map.put("temperature", firstNonBlank(findString(responseNode, "temperature"), findString(responseNode, "bodyTemperature")));
            map.put("temperatureStatus", firstNonBlank(findString(responseNode, "temperatureStatus"), findString(responseNode, "tempStatus")));
            map.put("certificateCheckResult", firstNonBlank(findString(responseNode, "certificateCheckResult"), findString(responseNode, "certResult")));
            map.put("certificateCheckMessage", firstNonBlank(findString(responseNode, "certificateCheckMessage"), findString(responseNode, "certMessage")));
            map.put("handCheckResult", firstNonBlank(findString(responseNode, "handCheckResult"), findString(responseNode, "handResult")));
            map.put("handCheckMessage", firstNonBlank(findString(responseNode, "handCheckMessage"), findString(responseNode, "handMessage")));
            map.put("checkResult", firstNonBlank(findString(responseNode, "checkResult"), findString(responseNode, "result")));
            map.put("evidenceImageUrl", firstNonBlank(findString(responseNode, "evidenceImageUrl"), findString(responseNode, "snapshotUrl")));
            map.put("checkTime", firstNonBlank(findString(responseNode, "checkTime"), findString(responseNode, "checkedAt")));
            map.put("status", firstNonBlank(findString(responseNode, "checkResult"), findString(responseNode, "status")));
        } else if (BIZ_SCENE_DEVICE_MASTER.equals(bizScene)) {
            map.put("deviceCode", firstNonBlank(findString(responseNode, "deviceCode"), findString(responseNode, "code"), externalNo));
            map.put("uuid", firstNonBlank(findString(responseNode, "uuid"), findString(responseNode, "deviceUuid")));
            map.put("deviceName", firstNonBlank(findString(responseNode, "deviceName"), findString(responseNode, "name")));
            map.put("manufacturer", firstNonBlank(findString(responseNode, "manufacturer"), findString(responseNode, "vendor")));
            map.put("model", firstNonBlank(findString(responseNode, "model"), findString(responseNode, "deviceModel")));
            map.put("onlineStatus", firstNonBlank(findString(responseNode, "onlineStatus"), findString(responseNode, "status")));
            map.put("installLocation", firstNonBlank(findString(responseNode, "installLocation"), findString(responseNode, "location")));
            map.put("faceThreshold", firstNonBlank(findString(responseNode, "faceThreshold"), findString(responseNode, "matchThreshold")));
            map.put("temperatureThreshold", firstNonBlank(findString(responseNode, "temperatureThreshold"), findString(responseNode, "tempThreshold")));
        }
        JsonNode attachments = findNode(responseNode, "attachments");
        if (attachments != null && !attachments.isMissingNode() && !attachments.isNull()) {
            map.put("attachments", nodeToValue(attachments));
        }
        return map;
    }

    private StatusMatch matchStatus(Long configId, Map<String, Object> normalized, String sourceStatus) {
        String normalizedSourceStatus = StrUtil.trimToNull(sourceStatus);
        if (normalizedSourceStatus == null) {
            return null;
        }
        IntegrationModuleConfig config = moduleConfigMapper.selectById(configId);
        List<IntegrationStatusMapping> mappings = statusMappingMapper.selectList(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getConfigId, configId)
                .eq(IntegrationStatusMapping::getEnabled, 1));
        if (config != null) {
            mappings.sort(buildStatusMappingComparator(Map.of(configId, config)));
        }
        for (IntegrationStatusMapping mapping : mappings) {
            String mappingSourceCode = StrUtil.trimToNull(mapping.getSourceStatusCode());
            String mappingSourceName = StrUtil.trimToNull(mapping.getSourceStatusName());
            if (normalizedSourceStatus.equalsIgnoreCase(firstNonBlank(mappingSourceCode, mappingSourceName))) {
                return new StatusMatch(mapping.getTargetStatusCode(), mapping.getFinishFlag() != null && mapping.getFinishFlag() == 1,
                        mapping.getTriggerBusinessAction() != null && mapping.getTriggerBusinessAction() == 1, mapping.getActionCode());
            }
            if (mappingSourceCode != null && normalizedSourceStatus.equalsIgnoreCase(mappingSourceCode)) {
                return new StatusMatch(mapping.getTargetStatusCode(), mapping.getFinishFlag() != null && mapping.getFinishFlag() == 1,
                        mapping.getTriggerBusinessAction() != null && mapping.getTriggerBusinessAction() == 1, mapping.getActionCode());
            }
        }
        return null;
    }

    private FileTransferBatchResult maybeTransferFiles(IntegrationModuleConfig config, IntegrationProviderTemplate provider,
                                                       IntegrationBinding binding, IntegrationSyncTask task,
                                                       NormalizedResult normalized) {
        if (config.getAttachmentPullEnabled() == null || config.getAttachmentPullEnabled() != 1) {
            return FileTransferBatchResult.empty();
        }
        if (provider.getFilePullSupported() == null || provider.getFilePullSupported() != 1) {
            String message = "平台模板未开启“支持文件拉取”，本次已跳过附件转存";
            upsertDiagnosticFileRecord(config, binding, task, FILE_RECORD_ERROR_PROVIDER_FILE_PULL_DISABLED, message);
            return FileTransferBatchResult.of(Collections.emptyList(), 0, 0, 1, 0, List.of(message));
        }
        AttachmentExtractionResult extraction = extractFiles(normalized.data());
        List<String> warnings = new ArrayList<>();
        List<FileTransferResult> availableFiles = new ArrayList<>();
        int transferredCount = 0;
        int failedCount = 0;
        int skippedCount = 0;
        int reusedCount = 0;
        if (StrUtil.isNotBlank(extraction.diagnosticCode()) && extraction.files().isEmpty()) {
            upsertDiagnosticFileRecord(config, binding, task, extraction.diagnosticCode(), extraction.diagnosticMessage());
            warnings.add(extraction.diagnosticMessage());
            return FileTransferBatchResult.of(availableFiles, 0, 0, 1, 0, warnings);
        }
        if (StrUtil.isNotBlank(extraction.warningMessage())) {
            upsertDiagnosticFileRecord(config, binding, task, FILE_RECORD_ERROR_ATTACHMENTS_INVALID, extraction.warningMessage());
            warnings.add(extraction.warningMessage());
            skippedCount++;
        }
        for (FileCandidate candidate : extraction.files()) {
            IntegrationFileRecord record = resolveTransferFileRecord(config, binding, task, candidate.fileName(), candidate.sanitizedUrl(), candidate.signature());
            if (isReusableTransferredFile(record)) {
                markFileRecordReused(record, config, binding, task, candidate.fileName(), candidate.sanitizedUrl());
                reusedCount++;
                availableFiles.add(new FileTransferResult(candidate.fileName(), candidate.sanitizedUrl(), record.getMinioFileUrl()));
                continue;
            }
            try {
                DownloadedFilePayload downloaded = downloadFileWithRetry(candidate.rawUrl());
                String mimeType = resolveTransferMimeType(candidate.fileName(), downloaded.contentType());
                validateTransferFileConstraints(candidate.fileName(), mimeType, downloaded.bytes().length);
                record.setDownloadStatus(FILE_RECORD_STATUS_SUCCESS);
                record.setStorageStatus(FILE_RECORD_STATUS_PENDING);
                record.setFileHash(sha256(downloaded.bytes()));
                record.setFileSize(String.valueOf(downloaded.bytes().length));
                record.setMimeType(mimeType);
                record.setErrorCode(null);
                record.setErrorMessage(null);
                fileRecordMapper.updateById(record);
                try {
                    String minioUrl = uploadFileWithRetry(candidate.fileName(), mimeType, downloaded.bytes());
                    record.setMinioFileUrl(minioUrl);
                    record.setStorageStatus(FILE_RECORD_STATUS_SUCCESS);
                    record.setErrorCode(null);
                    record.setErrorMessage(null);
                    fileRecordMapper.updateById(record);
                    transferredCount++;
                    availableFiles.add(new FileTransferResult(candidate.fileName(), candidate.sanitizedUrl(), minioUrl));
                } catch (Exception ex) {
                    FileTransferFailure failure = resolveFileTransferFailure(ex, false);
                    record.setStorageStatus(FILE_RECORD_STATUS_FAILED);
                    record.setErrorCode(failure.errorCode());
                    record.setErrorMessage(failure.errorMessage());
                    record.setMinioFileUrl(null);
                    fileRecordMapper.updateById(record);
                    failedCount++;
                    warnings.add(candidate.fileName() + "：" + failure.errorMessage());
                    log.warn("第三方附件转存上传失败, sourceUrl={}", candidate.rawUrl(), ex);
                }
            } catch (Exception ex) {
                FileTransferFailure failure = resolveFileTransferFailure(ex, true);
                record.setDownloadStatus(FILE_RECORD_STATUS_FAILED);
                record.setStorageStatus(FILE_RECORD_STATUS_SKIPPED);
                record.setErrorCode(failure.errorCode());
                record.setErrorMessage(failure.errorMessage());
                record.setFileSize("0");
                record.setMimeType(null);
                record.setMinioFileUrl(null);
                fileRecordMapper.updateById(record);
                failedCount++;
                warnings.add(candidate.fileName() + "：" + failure.errorMessage());
                log.warn("第三方附件转存下载失败, sourceUrl={}", candidate.rawUrl(), ex);
            }
        }
        return FileTransferBatchResult.of(availableFiles, transferredCount, failedCount, skippedCount, reusedCount, warnings);
    }

    private String summarizeFileTransferBatch(FileTransferBatchResult batch, boolean includeWarnings) {
        if (batch == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (batch.transferredCount() > 0) {
            parts.add("新增转存" + batch.transferredCount() + "个");
        }
        if (batch.reusedCount() > 0) {
            parts.add("复用历史" + batch.reusedCount() + "个");
        }
        if (batch.failedCount() > 0) {
            parts.add("失败" + batch.failedCount() + "个");
        }
        if (batch.skippedCount() > 0) {
            parts.add("跳过" + batch.skippedCount() + "个");
        }
        if (parts.isEmpty() && batch.warnings().isEmpty()) {
            return null;
        }
        String summary = parts.isEmpty() ? "附件转存无可执行项目" : "附件处理：" + String.join("，", parts);
        if (!includeWarnings || batch.warnings().isEmpty()) {
            return summary;
        }
        String warningSummary = batch.warnings().stream().limit(3).collect(Collectors.joining("；"));
        return StrUtil.isBlank(summary) ? warningSummary : summary + "；" + warningSummary;
    }

    private String appendFileTransferSummary(String baseMessage, String fileSummary) {
        if (StrUtil.isBlank(fileSummary)) {
            return baseMessage;
        }
        return StrUtil.isBlank(baseMessage) ? fileSummary : baseMessage + "；" + fileSummary;
    }

    private void populateExecutionResultFileTransfer(IntegrationExecutionResultVO result, FileTransferBatchResult batch) {
        FileTransferBatchResult safeBatch = batch == null ? FileTransferBatchResult.empty() : batch;
        result.setDownloadedFileCount(safeBatch.availableFiles().size());
        result.setFailedFileCount(safeBatch.failedCount());
        result.setSkippedFileCount(safeBatch.skippedCount());
        result.setReusedFileCount(safeBatch.reusedCount());
        result.setFileTransferSummary(summarizeFileTransferBatch(safeBatch, true));
    }

    private void attachFileRecordsToSyncContext(Long taskId, Long syncLogId, Long auditLogId) {
        if (taskId == null || taskId <= 0) {
            return;
        }
        fileRecordMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationFileRecord>()
                .eq(IntegrationFileRecord::getTaskId, taskId)
                .set(syncLogId != null, IntegrationFileRecord::getSyncLogId, syncLogId)
                .set(auditLogId != null, IntegrationFileRecord::getAuditLogId, auditLogId));
    }

    private void upsertDiagnosticFileRecord(IntegrationModuleConfig config, IntegrationBinding binding, IntegrationSyncTask task,
                                            String diagnosticCode, String diagnosticMessage) {
        if (binding == null || binding.getId() == null) {
            return;
        }
        IntegrationFileRecord record = resolveTransferFileRecord(
                config,
                binding,
                task,
                "系统诊断",
                null,
                buildFileSourceSignature("diagnostic:" + diagnosticCode)
        );
        record.setDownloadStatus(FILE_RECORD_STATUS_SKIPPED);
        record.setStorageStatus(FILE_RECORD_STATUS_SKIPPED);
        record.setErrorCode(diagnosticCode);
        record.setErrorMessage(diagnosticMessage);
        record.setFileHash(null);
        record.setFileSize(null);
        record.setMimeType(null);
        record.setMinioFileUrl(null);
        fileRecordMapper.updateById(record);
    }

    private IntegrationFileRecord resolveTransferFileRecord(IntegrationModuleConfig config, IntegrationBinding binding, IntegrationSyncTask task,
                                                            String fileName, String sanitizedSourceUrl, String sourceSignature) {
        IntegrationFileRecord record = findExistingFileRecord(binding.getId(), binding.getTenantId(), sourceSignature);
        if (record == null) {
            record = new IntegrationFileRecord();
            fillFileRecordRuntimeContext(record, config, binding, task);
            record.setSourceFileName(fileName);
            record.setSourceFileUrl(sanitizedSourceUrl);
            record.setSourceUrlSignature(sourceSignature);
            record.setDownloadStatus(FILE_RECORD_STATUS_PENDING);
            record.setStorageStatus(FILE_RECORD_STATUS_PENDING);
            record.setErrorCode(null);
            record.setErrorMessage(null);
            try {
                fileRecordMapper.insert(record);
                return record;
            } catch (DuplicateKeyException ex) {
                record = findExistingFileRecord(binding.getId(), binding.getTenantId(), sourceSignature);
                if (record == null) {
                    throw ex;
                }
            }
        }
        fillFileRecordRuntimeContext(record, config, binding, task);
        record.setSourceFileName(fileName);
        record.setSourceFileUrl(sanitizedSourceUrl);
        record.setSourceUrlSignature(sourceSignature);
        if (!isReusableTransferredFile(record)) {
            record.setDownloadStatus(FILE_RECORD_STATUS_PENDING);
            record.setStorageStatus(FILE_RECORD_STATUS_PENDING);
            record.setFileHash(null);
            record.setFileSize(null);
            record.setMimeType(null);
            record.setMinioFileUrl(null);
        }
        record.setErrorCode(null);
        record.setErrorMessage(null);
        fileRecordMapper.updateById(record);
        return record;
    }

    private IntegrationFileRecord findExistingFileRecord(Long bindingId, Long tenantId, String sourceSignature) {
        if (bindingId == null || StrUtil.isBlank(sourceSignature)) {
            return null;
        }
        return fileRecordMapper.selectOne(new LambdaQueryWrapper<IntegrationFileRecord>()
                .eq(IntegrationFileRecord::getBindingId, bindingId)
                .eq(tenantId != null, IntegrationFileRecord::getTenantId, tenantId)
                .eq(IntegrationFileRecord::getSourceUrlSignature, sourceSignature)
                .last("LIMIT 1"));
    }

    private void fillFileRecordRuntimeContext(IntegrationFileRecord record, IntegrationModuleConfig config,
                                              IntegrationBinding binding, IntegrationSyncTask task) {
        record.setBindingId(binding.getId());
        record.setConfigId(config.getId());
        record.setConfigNameSnapshot(resolveConfigNameSnapshot(config.getId()));
        record.setBizModule(binding.getBizModule());
        record.setBizScene(binding.getBizScene());
        record.setBizId(binding.getBizId());
        record.setBizNo(binding.getBizNo());
        record.setProviderCode(binding.getProviderCode());
        record.setProviderNameSnapshot(resolveProviderNameSnapshot(binding.getProviderCode()));
        record.setTaskId(task == null ? null : task.getId());
        record.setOrgId(binding.getOrgId());
        record.setOrgNameSnapshot(resolveOrgNameSnapshot(binding.getOrgId()));
        record.setTenantId(binding.getTenantId());
    }

    private boolean isReusableTransferredFile(IntegrationFileRecord record) {
        if (record == null || StrUtil.isBlank(record.getMinioFileUrl())) {
            return false;
        }
        return FILE_RECORD_STATUS_SUCCESS.equals(record.getStorageStatus()) || FILE_RECORD_STATUS_REUSED.equals(record.getStorageStatus());
    }

    private void markFileRecordReused(IntegrationFileRecord record, IntegrationModuleConfig config,
                                      IntegrationBinding binding, IntegrationSyncTask task,
                                      String fileName, String sanitizedSourceUrl) {
        fillFileRecordRuntimeContext(record, config, binding, task);
        record.setSourceFileName(fileName);
        record.setSourceFileUrl(sanitizedSourceUrl);
        record.setDownloadStatus(FILE_RECORD_STATUS_REUSED);
        record.setStorageStatus(FILE_RECORD_STATUS_REUSED);
        record.setErrorCode(null);
        record.setErrorMessage(null);
        fileRecordMapper.updateById(record);
    }

    private boolean shouldWriteBack(IntegrationSyncTask task, IntegrationModuleConfig config, IntegrationBinding binding) {
        if (!MODE_THIRD_PARTY.equals(binding.getMaintenanceMode())) {
            return false;
        }
        if (TRIGGER_TYPE_MANUAL.equals(task.getTriggerType())) {
            return true;
        }
        return config.getAutoCoverEnabled() != null && config.getAutoCoverEnabled() == 1;
    }

    private Long writeBackToBusiness(IntegrationModuleConfig config, IntegrationBinding binding,
                                     NormalizedResult normalized, List<FileTransferResult> fileResults) {
        if (BIZ_MODULE_PURCHASE_ORDER.equals(binding.getBizModule())) {
            return writeBackPurchaseOrder(config, binding, normalized, fileResults);
        }
        if (BIZ_MODULE_SAMPLE_RETENTION.equals(binding.getBizModule())) {
            return writeBackSampleRetention(config, binding, normalized, fileResults);
        }
        if (BIZ_MODULE_MORNING_CHECK.equals(binding.getBizModule())) {
            return writeBackMorningCheck(config, binding, normalized, fileResults);
        }
        return auditLogService.logAndReturnId(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, binding.getBizId(), binding.getBizNo(),
                "第三方同步结果已回写到业务模块：" + binding.getBizModule() + "/" + binding.getBizScene(),
                null, safeJson(normalized.data()));
    }

    private Long writeBackPurchaseOrder(IntegrationModuleConfig config, IntegrationBinding binding,
                                        NormalizedResult normalized, List<FileTransferResult> fileResults) {
        Map<String, Object> orderRow = queryOrder(binding.getBizId(), binding.getOrgId());
        String scene = binding.getBizScene();
        if (!isPurchaseOrderSceneStillThirdParty(orderRow, scene)) {
            log.info("采购订单当前场景已切回手工维护，忽略第三方回写: bizId={}, scene={}", binding.getBizId(), scene);
            return null;
        }
        String payload = safeJson(normalized.data());
        if (BIZ_SCENE_LOGISTICS.equals(scene)) {
            LocalDateTime shippedAt = parseDateTime(normalized.data().get("shippedAt"));
            LocalDateTime arrivedAt = parseDateTime(normalized.data().get("arrivedAt"));
            String logisticsStatus = asString(normalized.data().get("status"));
            String nextStatus = resolveStatusAfterLogistics(asString(orderRow.get("status")), logisticsStatus, shippedAt, arrivedAt);
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order SET logistics_no=?, logistics_company=?, logistics_status=?, logistics_remark=?, logistics_source_type='third_party', logistics_sync_payload=?, shipped_at=?, arrived_at=?, actual_delivery_at=?, logistics_attachment_name=?, logistics_attachment_url=?, status=?, updated_at=NOW() WHERE id=? AND deleted=0",
                    asString(normalized.data().get("trackingNo")),
                    asString(normalized.data().get("company")),
                    logisticsStatus,
                    asString(normalized.data().get("remark")),
                    payload,
                    shippedAt,
                    arrivedAt,
                    arrivedAt,
                    fileResults.isEmpty() ? null : fileResults.get(0).fileName(),
                    fileResults.isEmpty() ? null : fileResults.get(0).minioUrl(),
                    nextStatus,
                    binding.getBizId()
            );
            replacePurchaseOrderAttachments(binding, ATTACHMENT_TYPE_LOGISTICS, orderRow, fileResults);
        } else if (BIZ_SCENE_INSPECTION.equals(scene)) {
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order SET inspection_report_no=?, inspection_result=?, inspection_agency=?, inspection_at=?, inspection_remark=?, inspection_source_type='third_party', inspection_sync_payload=?, inspection_attachment_name=?, inspection_attachment_url=?, updated_at=NOW() WHERE id=? AND deleted=0",
                    asString(normalized.data().get("reportNo")),
                    asString(normalized.data().get("result")),
                    asString(normalized.data().get("agency")),
                    parseDateTime(normalized.data().get("inspectionAt")),
                    asString(normalized.data().get("remark")),
                    payload,
                    fileResults.isEmpty() ? null : fileResults.get(0).fileName(),
                    fileResults.isEmpty() ? null : fileResults.get(0).minioUrl(),
                    binding.getBizId()
            );
            replacePurchaseOrderAttachments(binding, ATTACHMENT_TYPE_INSPECTION, orderRow, fileResults);
        } else if (BIZ_SCENE_TRACEABILITY.equals(scene)) {
            jdbcTemplate.update(
                    "UPDATE scm_purchase_order SET trace_batch_id=?, trace_origin=?, trace_remark=?, trace_source_type='third_party', trace_sync_payload=?, trace_attachment_name=?, trace_attachment_url=?, updated_at=NOW() WHERE id=? AND deleted=0",
                    asString(normalized.data().get("batchId")),
                    asString(normalized.data().get("origin")),
                    asString(normalized.data().get("remark")),
                    payload,
                    fileResults.isEmpty() ? null : fileResults.get(0).fileName(),
                    fileResults.isEmpty() ? null : fileResults.get(0).minioUrl(),
                    binding.getBizId()
            );
            replacePurchaseOrderAttachments(binding, ATTACHMENT_TYPE_TRACEABILITY, orderRow, fileResults);
        }
        return auditLogService.logAndReturnId(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, binding.getBizId(), binding.getBizNo(),
                "第三方同步结果回写采购订单：" + binding.getBizNo() + " / " + scene,
                null, payload);
    }

    private void replacePurchaseOrderAttachments(IntegrationBinding binding, String attachmentType,
                                                 Map<String, Object> orderRow, List<FileTransferResult> fileResults) {
        jdbcTemplate.update("DELETE FROM scm_purchase_order_attachment WHERE order_id=? AND attachment_type=?", binding.getBizId(), attachmentType);
        int sort = 1;
        for (FileTransferResult file : fileResults) {
            jdbcTemplate.update(
                    "INSERT INTO scm_purchase_order_attachment(order_id, attachment_type, file_name, file_size, file_url, sort_order, org_id, tenant_id, created_by, updated_by) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    binding.getBizId(), attachmentType, file.fileName(), null, file.minioUrl(), sort++,
                    orderRow.get("orgId"), orderRow.get("tenantId"), UserContext.getUserId(), UserContext.getUserId()
            );
        }
    }

    private Map<String, Object> queryOrder(Long orderId, Long orgId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, org_id AS orgId, tenant_id AS tenantId, status, " +
                        "logistics_source_type AS logisticsSourceType, inspection_source_type AS inspectionSourceType, trace_source_type AS traceSourceType " +
                        "FROM scm_purchase_order WHERE id = ? AND deleted = 0 LIMIT 1",
                orderId
        );
        if (rows.isEmpty()) {
            throw BizException.notFound("采购订单不存在");
        }
        Map<String, Object> row = rows.get(0);
        Long rowOrgId = asLong(row.get("orgId"));
        if (!Objects.equals(rowOrgId, orgId)) {
            validateOrgWritable(rowOrgId);
        }
        return row;
    }

    private Long writeBackSampleRetention(IntegrationModuleConfig config, IntegrationBinding binding,
                                          NormalizedResult normalized, List<FileTransferResult> fileResults) {
        String scene = binding.getBizScene();
        if (BIZ_SCENE_SAMPLE_DEVICE.equals(scene)) {
            writeBackSampleDevice(binding, normalized, fileResults);
        } else {
            Map<String, Object> sampleRow = querySampleRecord(binding.getBizId(), binding.getBizNo(), binding.getOrgId(), binding.getTenantId());
            String payload = safeJson(normalized.data());
            if (BIZ_SCENE_SAMPLE_TASK.equals(scene)) {
                writeBackSampleTask(binding, sampleRow, normalized, payload);
            } else if (BIZ_SCENE_SAMPLE_RECORD.equals(scene)) {
                writeBackSampleRecord(binding, sampleRow, normalized, fileResults, payload);
            } else if (BIZ_SCENE_SAMPLE_RESULT.equals(scene)) {
                writeBackSampleResult(binding, sampleRow, normalized, fileResults, payload);
            }
        }
        String payload = safeJson(normalized.data());
        return auditLogService.logAndReturnId(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, binding.getBizId(), binding.getBizNo(),
                "第三方同步结果回写留样管理：" + binding.getBizNo() + " / " + scene,
                null, payload);
    }

    private Long writeBackMorningCheck(IntegrationModuleConfig config, IntegrationBinding binding,
                                       NormalizedResult normalized, List<FileTransferResult> fileResults) {
        String scene = binding.getBizScene();
        if (BIZ_SCENE_FACE_PROFILE.equals(scene)) {
            writeBackFaceProfile(binding, normalized, fileResults);
        } else if (BIZ_SCENE_HEALTH_CERTIFICATE.equals(scene)) {
            writeBackHealthCertificate(binding, normalized, fileResults);
        } else if (BIZ_SCENE_MORNING_CHECK_RESULT.equals(scene)) {
            writeBackMorningCheckResult(binding, normalized, fileResults);
        } else if (BIZ_SCENE_DEVICE_MASTER.equals(scene)) {
            writeBackMorningDevice(binding, normalized, fileResults);
        }
        return auditLogService.logAndReturnId(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, binding.getBizId(), binding.getBizNo(),
                "第三方同步结果回写晨检模块：" + binding.getBizNo() + " / " + scene,
                null, safeJson(normalized.data()));
    }

    private void writeBackSampleTask(IntegrationBinding binding, Map<String, Object> sampleRow,
                                     NormalizedResult normalized, String payload) {
        Map<String, Object> analysisPatch = new LinkedHashMap<>();
        analysisPatch.put("thirdPartySampleTaskNo", firstNonBlank(asString(normalized.data().get("sampleTaskNo")), binding.getExternalNo()));
        analysisPatch.put("plannedSampleWeight", normalized.data().get("requiredSampleWeight"));
        analysisPatch.put("plannedSampleTime", normalizeDateTimeForJson(normalized.data().get("plannedSampleTime")));
        analysisPatch.put("kitchenAreaId", normalized.data().get("kitchenAreaId"));
        analysisPatch.put("taskSyncPayload", normalized.data());
        Map<String, Object> analysis = mergeJsonMap(asString(sampleRow.get("aiAnalysisResult")), analysisPatch);
        String nextStatus = normalizeSampleStatus(asString(normalized.data().get("status")), asString(sampleRow.get("status")));
        jdbcTemplate.update(
                "UPDATE sample_record SET task_id=?, menu_name=?, ai_analysis_result=?, status=?, updated_at=NOW() WHERE id=? AND deleted=0",
                firstNonNull(asLong(normalized.data().get("cookingTaskId")), asLong(sampleRow.get("taskId"))),
                firstNonBlank(asString(normalized.data().get("recipeName")), asString(normalized.data().get("dishName")), asString(sampleRow.get("menuName"))),
                writeJson(analysis),
                nextStatus,
                sampleRow.get("id")
        );
        insertSampleOperationLog(asLong(sampleRow.get("id")), "third_party_sync", "第三方同步", "第三方同步留样任务信息");
    }

    private void writeBackSampleRecord(IntegrationBinding binding, Map<String, Object> sampleRow,
                                       NormalizedResult normalized, List<FileTransferResult> fileResults, String payload) {
        LocalDateTime sampledAt = firstNonNull(parseDateTime(normalized.data().get("sampledAt")), parseDateTime(sampleRow.get("sampledAt")));
        BigDecimal sampleWeight = firstNonNull(asDecimal(normalized.data().get("sampleWeight")), asDecimal(sampleRow.get("sampleWeight")));
        List<String> evidenceUrls = resolveAttachmentUrls(fileResults, normalized.data().get("evidenceFiles"));
        String nextStatus = resolveSampleRecordStatus(asString(sampleRow.get("status")), asString(normalized.data().get("status")), sampledAt, sampleWeight);
        Map<String, Object> analysisPatch = new LinkedHashMap<>();
        analysisPatch.put("thirdPartySampleRecordNo", normalized.data().get("sampleRecordNo"));
        analysisPatch.put("linkedSampleTaskNo", normalized.data().get("sampleTaskNo"));
        analysisPatch.put("recordSyncPayload", normalized.data());
        Map<String, Object> analysis = mergeJsonMap(asString(sampleRow.get("aiAnalysisResult")), analysisPatch);
        jdbcTemplate.update(
                "UPDATE sample_record SET sample_weight=?, sampled_by=?, sampled_at=?, disposal_due_at=?, storage_location=?, sample_images=?, status=?, ai_analysis_result=?, updated_at=NOW() WHERE id=? AND deleted=0",
                sampleWeight,
                firstNonNull(asLong(normalized.data().get("samplerId")), asLong(sampleRow.get("sampledBy"))),
                sampledAt,
                sampledAt == null ? sampleRow.get("disposalDueAt") : sampledAt.plusHours(48),
                firstNonBlank(asString(normalized.data().get("storageLocation")), asString(sampleRow.get("storageLocation"))),
                evidenceUrls.isEmpty() ? asString(sampleRow.get("sampleImages")) : resolveJsonArrayColumn(evidenceUrls),
                nextStatus,
                writeJson(analysis),
                sampleRow.get("id")
        );
        insertSampleOperationLog(asLong(sampleRow.get("id")), "third_party_sync", "第三方同步", "第三方同步留样执行记录");
    }

    private void writeBackSampleResult(IntegrationBinding binding, Map<String, Object> sampleRow,
                                       NormalizedResult normalized, List<FileTransferResult> fileResults, String payload) {
        List<String> destroyUrls = resolveAttachmentUrls(fileResults, normalized.data().get("destroyEvidenceFiles"));
        LocalDateTime destroyAt = parseDateTime(normalized.data().get("destroyAt"));
        String nextStatus = resolveSampleResultStatus(asString(sampleRow.get("status")), asString(normalized.data().get("status")), destroyAt);
        Map<String, Object> analysisPatch = new LinkedHashMap<>();
        analysisPatch.put("complianceStatus", normalized.data().get("complianceStatus"));
        analysisPatch.put("anomalyTags", normalized.data().get("anomalyTags"));
        analysisPatch.put("inspectionReportNo", normalized.data().get("inspectionReportNo"));
        analysisPatch.put("resultSyncPayload", normalized.data());
        Map<String, Object> analysis = mergeJsonMap(asString(sampleRow.get("aiAnalysisResult")), analysisPatch);
        boolean evaluated = normalized.data().get("qualityScore") != null
                || normalized.data().get("complianceStatus") != null
                || normalized.data().get("anomalyTags") != null;
        jdbcTemplate.update(
                "UPDATE sample_record SET ai_quality_score=?, ai_analysis_result=?, evaluated_at=?, disposal_at=?, disposal_by=?, disposal_images=?, disposal_remark=?, status=?, updated_at=NOW() WHERE id=? AND deleted=0",
                asDecimal(normalized.data().get("qualityScore")),
                writeJson(analysis),
                evaluated ? LocalDateTime.now() : sampleRow.get("evaluatedAt"),
                destroyAt,
                destroyAt == null ? sampleRow.get("disposalBy") : firstNonNull(UserContext.getUserId(), 0L),
                destroyUrls.isEmpty() ? asString(sampleRow.get("disposalImages")) : resolveJsonArrayColumn(destroyUrls),
                destroyAt == null ? asString(sampleRow.get("disposalRemark")) : firstNonBlank(asString(sampleRow.get("disposalRemark")), "第三方同步销样结果"),
                nextStatus,
                sampleRow.get("id")
        );
        insertSampleOperationLog(asLong(sampleRow.get("id")), "third_party_sync", "第三方同步", "第三方同步留样结果/销样结果");
    }

    private void writeBackSampleDevice(IntegrationBinding binding, NormalizedResult normalized, List<FileTransferResult> fileResults) {
        String deviceCode = firstNonBlank(asString(normalized.data().get("deviceCode")), binding.getBizNo(), binding.getExternalNo());
        if (StrUtil.isBlank(deviceCode)) {
            throw BizException.validationFailed("留样设备同步缺少设备编码");
        }
        Map<String, Object> deviceRow = queryDeviceInfo(binding.getBizId(), binding.getBizNo(), deviceCode, binding.getOrgId(), binding.getTenantId());
        Map<String, Object> configPatch = new LinkedHashMap<>();
        configPatch.put("storageTemperature", normalized.data().get("storageTemperature"));
        configPatch.put("weightPrecision", normalized.data().get("weightPrecision"));
        Map<String, Object> configParams = mergeJsonMap(deviceRow == null ? null : asString(deviceRow.get("configParams")), configPatch);
        String deviceName = firstNonBlank(asString(normalized.data().get("deviceName")), deviceRow == null ? null : asString(deviceRow.get("deviceName")), deviceCode);
        String onlineStatus = normalizeOnlineStatus(asString(normalized.data().get("onlineStatus")));
        if (deviceRow == null) {
            jdbcTemplate.update(
                    "INSERT INTO device_info(device_code, device_name, device_type, device_model, manufacturer, online_status, config_params, status, org_id, tenant_id, created_by, updated_by, created_at, updated_at, deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),0)",
                    deviceCode,
                    deviceName,
                    DEVICE_TYPE_SAMPLE_TERMINAL,
                    asString(normalized.data().get("model")),
                    asString(normalized.data().get("manufacturer")),
                    onlineStatus,
                    writeJson(configParams),
                    "active",
                    binding.getOrgId(),
                    binding.getTenantId(),
                    UserContext.getUserId(),
                    UserContext.getUserId()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE device_info SET device_name=?, device_type=?, device_model=?, manufacturer=?, online_status=?, config_params=?, status='active', updated_at=NOW() WHERE id=? AND deleted=0",
                    deviceName,
                    firstNonBlank(asString(deviceRow.get("deviceType")), DEVICE_TYPE_SAMPLE_TERMINAL),
                    firstNonBlank(asString(normalized.data().get("model")), asString(deviceRow.get("deviceModel"))),
                    firstNonBlank(asString(normalized.data().get("manufacturer")), asString(deviceRow.get("manufacturer"))),
                    onlineStatus,
                    writeJson(configParams),
                    deviceRow.get("id")
            );
        }
    }

    private void writeBackFaceProfile(IntegrationBinding binding, NormalizedResult normalized, List<FileTransferResult> fileResults) {
        Map<String, Object> employeeRow = resolveEmployeeRow(binding, normalized.data(), true);
        Long employeeId = asLong(employeeRow.get("id"));
        String faceImageUrl = firstNonBlank(firstAttachmentUrl(fileResults, normalized.data().get("faceImageUrl")), asString(normalized.data().get("faceImageUrl")));
        Integer faceEnrolled = resolveFaceEnrolledFlag(asString(normalized.data().get("faceEnrollStatus")));
        jdbcTemplate.update(
                "UPDATE sys_employee SET real_name=?, avatar_url=?, face_enrolled=?, updated_at=NOW() WHERE id=?",
                firstNonBlank(asString(normalized.data().get("employeeName")), asString(employeeRow.get("realName"))),
                firstNonBlank(faceImageUrl, asString(employeeRow.get("avatarUrl"))),
                faceEnrolled,
                employeeId
        );

        Map<String, Object> faceRow = queryHealthFaceFeature(employeeId, binding.getTenantId());
        String storedVector = faceRow == null ? null : asString(faceRow.get("faceFeatureVector"));
        String externalFaceId = asString(normalized.data().get("externalFaceId"));
        if (faceRow == null) {
            jdbcTemplate.update(
                    "INSERT INTO health_face_feature(employee_id, face_image_url, face_feature_vector, feature_version, quality_score, is_active, enrolled_at, org_id, tenant_id, created_by, updated_by, created_at, updated_at, deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),0)",
                    employeeId,
                    faceImageUrl,
                    firstNonBlank(storedVector, externalFaceId),
                    "external_sync",
                    null,
                    faceEnrolled,
                    faceEnrolled == 1 ? LocalDateTime.now() : null,
                    employeeRow.get("orgId"),
                    employeeRow.get("tenantId"),
                    UserContext.getUserId(),
                    UserContext.getUserId()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE health_face_feature SET face_image_url=?, face_feature_vector=?, feature_version=?, is_active=?, enrolled_at=?, updated_at=NOW() WHERE id=? AND deleted=0",
                    firstNonBlank(faceImageUrl, asString(faceRow.get("faceImageUrl"))),
                    firstNonBlank(storedVector, externalFaceId),
                    firstNonBlank(asString(faceRow.get("featureVersion")), "external_sync"),
                    faceEnrolled,
                    faceEnrolled == 1 ? firstNonNull(parseDateTime(faceRow.get("enrolledAt")), LocalDateTime.now()) : null,
                    faceRow.get("id")
            );
        }
    }

    private void writeBackHealthCertificate(IntegrationBinding binding, NormalizedResult normalized, List<FileTransferResult> fileResults) {
        Map<String, Object> certificateRow = queryHealthCertificate(binding.getBizId(), binding.getBizNo(), null, binding.getOrgId(), binding.getTenantId());
        Map<String, Object> employeeRow = resolveEmployeeRow(binding, normalized.data(), certificateRow == null);
        Long employeeId = employeeRow == null ? null : asLong(employeeRow.get("id"));
        if (certificateRow == null) {
            certificateRow = queryHealthCertificate(null, binding.getBizNo(), employeeId, binding.getOrgId(), binding.getTenantId());
        }
        if (employeeId == null && certificateRow != null) {
            employeeId = asLong(certificateRow.get("employeeId"));
            employeeRow = queryEmployeeById(employeeId, binding.getTenantId());
        }
        if (employeeId == null) {
            throw BizException.validationFailed("健康证同步缺少员工信息，无法定位业务对象");
        }
        LocalDate issueDate = parseDate(normalized.data().get("issueDate"));
        LocalDate expireDate = parseDate(firstNonBlank(asString(normalized.data().get("expireDate")), asString(normalized.data().get("expiryDate"))));
        String status = resolveHealthCertificateStatus(firstNonBlank(asString(normalized.data().get("certificateStatus")), asString(normalized.data().get("status"))), expireDate);
        String certificateImage = firstAttachmentUrl(fileResults, normalized.data().get("certificateImages"));
        if (certificateRow == null) {
            jdbcTemplate.update(
                    "INSERT INTO health_certificate(employee_id, employee_name, certificate_no, issue_date, expiry_date, certificate_images, status, warning_days, remark, org_id, tenant_id, created_by, updated_by, created_at, updated_at, deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),0)",
                    employeeId,
                    firstNonBlank(asString(normalized.data().get("employeeName")), employeeRow == null ? null : asString(employeeRow.get("realName")), "未知员工"),
                    firstNonBlank(asString(normalized.data().get("certificateNo")), binding.getExternalNo()),
                    issueDate,
                    expireDate,
                    certificateImage,
                    status,
                    30,
                    "第三方同步健康证档案",
                    employeeRow == null ? binding.getOrgId() : employeeRow.get("orgId"),
                    employeeRow == null ? binding.getTenantId() : employeeRow.get("tenantId"),
                    UserContext.getUserId(),
                    UserContext.getUserId()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE health_certificate SET employee_name=?, certificate_no=?, issue_date=?, expiry_date=?, certificate_images=?, status=?, updated_at=NOW() WHERE id=? AND deleted=0",
                    firstNonBlank(asString(normalized.data().get("employeeName")), asString(certificateRow.get("employeeName")), employeeRow == null ? null : asString(employeeRow.get("realName"))),
                    firstNonBlank(asString(normalized.data().get("certificateNo")), asString(certificateRow.get("certificateNo")), binding.getExternalNo()),
                    firstNonNull(issueDate, parseDate(certificateRow.get("issueDate"))),
                    firstNonNull(expireDate, parseDate(certificateRow.get("expiryDate"))),
                    firstNonBlank(certificateImage, asString(certificateRow.get("certificateImages"))),
                    status,
                    certificateRow.get("id")
            );
        }
        jdbcTemplate.update("UPDATE sys_employee SET health_cert_status=?, updated_at=NOW() WHERE id=?", status, employeeId);
    }

    private void writeBackMorningCheckResult(IntegrationBinding binding, NormalizedResult normalized, List<FileTransferResult> fileResults) {
        Map<String, Object> existingRecord = queryHealthCheckRecord(binding.getBizId(), binding.getBizNo(), binding.getOrgId(), binding.getTenantId());
        Map<String, Object> employeeRow = resolveEmployeeRow(binding, normalized.data(), existingRecord == null);
        Long employeeId = employeeRow == null ? (existingRecord == null ? null : asLong(existingRecord.get("employeeId"))) : asLong(employeeRow.get("id"));
        if (employeeId == null) {
            throw BizException.validationFailed("晨检结果同步缺少员工信息，无法定位业务对象");
        }
        String employeeName = firstNonBlank(asString(normalized.data().get("employeeName")),
                employeeRow == null ? null : asString(employeeRow.get("realName")),
                existingRecord == null ? null : asString(existingRecord.get("employeeName")),
                "未知员工");
        LocalDateTime checkTime = firstNonNull(parseDateTime(normalized.data().get("checkTime")), LocalDateTime.now());
        LocalDate checkDate = checkTime.toLocalDate();
        BigDecimal temperature = asDecimal(normalized.data().get("temperature"));
        String healthStatus = resolveMorningHealthStatus(asString(normalized.data().get("temperatureStatus")), temperature);
        String certificateStatus = resolveCertificateStatusFromCheckResult(firstNonBlank(asString(normalized.data().get("certificateCheckResult")), asString(normalized.data().get("status"))));
        String handHygiene = normalizePassFailValue(asString(normalized.data().get("handCheckResult")));
        String checkResult = resolveMorningCheckResultCode(normalized.data(), healthStatus, handHygiene, certificateStatus);
        String recordStatus = HEALTH_CHECK_RESULT_PASS.equals(checkResult) ? HEALTH_CHECK_STATUS_COMPLETED_NORMAL : HEALTH_CHECK_STATUS_COMPLETED_ABNORMAL;
        String faceImageUrl = firstNonBlank(firstAttachmentUrl(fileResults, normalized.data().get("evidenceImageUrl")), asString(normalized.data().get("evidenceImageUrl")));
        String failReason = buildMorningFailReason(normalized.data(), healthStatus, handHygiene, certificateStatus, checkResult);
        String remark = buildMorningRemark(existingRecord == null ? null : asString(existingRecord.get("remark")), normalized.data());

        if (existingRecord == null) {
            String checkNo = firstNonBlank(asString(normalized.data().get("checkNo")), binding.getBizNo(), "HC-SYNC-" + System.currentTimeMillis());
            jdbcTemplate.update(
                    "INSERT INTO health_check_record(check_no, employee_id, employee_name, check_date, check_time, temperature, face_image_url, face_match_score, certificate_status, hand_hygiene, health_status, check_result, fail_reason, checker_id, status, remark, org_id, tenant_id, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW())",
                    checkNo,
                    employeeId,
                    employeeName,
                    checkDate,
                    checkTime,
                    temperature,
                    faceImageUrl,
                    asDecimal(normalized.data().get("faceMatchScore")),
                    certificateStatus,
                    handHygiene,
                    healthStatus,
                    checkResult,
                    failReason,
                    UserContext.getUserId(),
                    recordStatus,
                    remark,
                    employeeRow == null ? binding.getOrgId() : employeeRow.get("orgId"),
                    employeeRow == null ? binding.getTenantId() : employeeRow.get("tenantId")
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE health_check_record SET employee_name=?, check_date=?, check_time=?, temperature=?, face_image_url=?, face_match_score=?, certificate_status=?, hand_hygiene=?, health_status=?, check_result=?, fail_reason=?, status=?, remark=?, updated_at=NOW() WHERE id=?",
                    employeeName,
                    checkDate,
                    checkTime,
                    firstNonNull(temperature, asDecimal(existingRecord.get("temperature"))),
                    firstNonBlank(faceImageUrl, asString(existingRecord.get("faceImageUrl"))),
                    firstNonNull(asDecimal(normalized.data().get("faceMatchScore")), asDecimal(existingRecord.get("faceMatchScore"))),
                    firstNonBlank(certificateStatus, asString(existingRecord.get("certificateStatus"))),
                    firstNonBlank(handHygiene, asString(existingRecord.get("handHygiene"))),
                    firstNonBlank(healthStatus, asString(existingRecord.get("healthStatus"))),
                    checkResult,
                    failReason,
                    recordStatus,
                    remark,
                    existingRecord.get("id")
            );
        }
    }

    private void writeBackMorningDevice(IntegrationBinding binding, NormalizedResult normalized, List<FileTransferResult> fileResults) {
        String deviceCode = firstNonBlank(asString(normalized.data().get("deviceCode")), binding.getBizNo(), binding.getExternalNo());
        if (StrUtil.isBlank(deviceCode)) {
            throw BizException.validationFailed("晨检设备同步缺少设备编码");
        }
        Map<String, Object> deviceRow = queryDeviceInfo(binding.getBizId(), binding.getBizNo(), deviceCode, binding.getOrgId(), binding.getTenantId());
        Map<String, Object> configPatch = new LinkedHashMap<>();
        configPatch.put("uuid", normalized.data().get("uuid"));
        configPatch.put("faceThreshold", normalized.data().get("faceThreshold"));
        configPatch.put("temperatureThreshold", normalized.data().get("temperatureThreshold"));
        Map<String, Object> configParams = mergeJsonMap(deviceRow == null ? null : asString(deviceRow.get("configParams")), configPatch);
        String deviceName = firstNonBlank(asString(normalized.data().get("deviceName")), deviceRow == null ? null : asString(deviceRow.get("deviceName")), deviceCode);
        String onlineStatus = normalizeOnlineStatus(asString(normalized.data().get("onlineStatus")));
        String installLocation = firstNonBlank(asString(normalized.data().get("installLocation")), deviceRow == null ? null : asString(deviceRow.get("locationDesc")));
        if (deviceRow == null) {
            jdbcTemplate.update(
                    "INSERT INTO device_info(device_code, device_name, device_type, device_model, manufacturer, location_desc, online_status, config_params, status, org_id, tenant_id, created_by, updated_by, created_at, updated_at, deleted) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,NOW(),NOW(),0)",
                    deviceCode,
                    deviceName,
                    DEVICE_TYPE_HEALTH_TERMINAL,
                    asString(normalized.data().get("model")),
                    asString(normalized.data().get("manufacturer")),
                    installLocation,
                    onlineStatus,
                    writeJson(configParams),
                    "active",
                    binding.getOrgId(),
                    binding.getTenantId(),
                    UserContext.getUserId(),
                    UserContext.getUserId()
            );
        } else {
            jdbcTemplate.update(
                    "UPDATE device_info SET device_name=?, device_type=?, device_model=?, manufacturer=?, location_desc=?, online_status=?, config_params=?, status='active', updated_at=NOW() WHERE id=? AND deleted=0",
                    deviceName,
                    firstNonBlank(asString(deviceRow.get("deviceType")), DEVICE_TYPE_HEALTH_TERMINAL),
                    firstNonBlank(asString(normalized.data().get("model")), asString(deviceRow.get("deviceModel"))),
                    firstNonBlank(asString(normalized.data().get("manufacturer")), asString(deviceRow.get("manufacturer"))),
                    installLocation,
                    onlineStatus,
                    writeJson(configParams),
                    deviceRow.get("id")
            );
        }
    }

    private Map<String, Object> querySampleRecord(Long bizId, String bizNo, Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, sample_no AS sampleNo, task_id AS taskId, menu_id AS menuId, menu_name AS menuName, sample_weight AS sampleWeight, " +
                        "sample_images AS sampleImages, ai_quality_score AS aiQualityScore, ai_analysis_result AS aiAnalysisResult, storage_location AS storageLocation, " +
                        "sampled_by AS sampledBy, sampled_at AS sampledAt, disposal_due_at AS disposalDueAt, disposal_by AS disposalBy, disposal_at AS disposalAt, " +
                        "disposal_images AS disposalImages, disposal_remark AS disposalRemark, evaluated_at AS evaluatedAt, status, org_id AS orgId, tenant_id AS tenantId " +
                        "FROM sample_record WHERE deleted = 0 AND (id = ? OR sample_no = ?) ORDER BY id = ? DESC LIMIT 1",
                bizId, bizNo, bizId
        );
        if (rows.isEmpty()) {
            throw BizException.notFound("留样记录不存在");
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, orgId, tenantId);
        return row;
    }

    private Map<String, Object> queryEmployeeById(Long employeeId, Long tenantId) {
        if (employeeId == null) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, employee_no AS employeeNo, real_name AS realName, avatar_url AS avatarUrl, face_enrolled AS faceEnrolled, health_cert_status AS healthCertStatus, org_id AS orgId, tenant_id AS tenantId " +
                        "FROM sys_employee WHERE id = ? AND deleted = 0 LIMIT 1",
                employeeId
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, asLong(row.get("orgId")), tenantId);
        return row;
    }

    private Map<String, Object> queryEmployeeByCode(String employeeCode, Long tenantId) {
        if (StrUtil.isBlank(employeeCode)) {
            return null;
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, employee_no AS employeeNo, real_name AS realName, avatar_url AS avatarUrl, face_enrolled AS faceEnrolled, health_cert_status AS healthCertStatus, org_id AS orgId, tenant_id AS tenantId " +
                        "FROM sys_employee WHERE employee_no = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                employeeCode
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, asLong(row.get("orgId")), tenantId);
        return row;
    }

    private Map<String, Object> queryHealthFaceFeature(Long employeeId, Long tenantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, employee_id AS employeeId, face_image_url AS faceImageUrl, face_feature_vector AS faceFeatureVector, feature_version AS featureVersion, enrolled_at AS enrolledAt, org_id AS orgId, tenant_id AS tenantId " +
                        "FROM health_face_feature WHERE employee_id = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                employeeId
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, asLong(row.get("orgId")), tenantId);
        return row;
    }

    private Map<String, Object> queryHealthCertificate(Long bizId, String bizNo, Long employeeId, Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (bizId != null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, employee_id AS employeeId, employee_name AS employeeName, certificate_no AS certificateNo, issue_date AS issueDate, expiry_date AS expiryDate, certificate_images AS certificateImages, status, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM health_certificate WHERE id = ? AND deleted = 0 LIMIT 1",
                    bizId
            );
        }
        if (rows.isEmpty() && employeeId != null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, employee_id AS employeeId, employee_name AS employeeName, certificate_no AS certificateNo, issue_date AS issueDate, expiry_date AS expiryDate, certificate_images AS certificateImages, status, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM health_certificate WHERE employee_id = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    employeeId
            );
        }
        if (rows.isEmpty() && StrUtil.isNotBlank(bizNo)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, employee_id AS employeeId, employee_name AS employeeName, certificate_no AS certificateNo, issue_date AS issueDate, expiry_date AS expiryDate, certificate_images AS certificateImages, status, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM health_certificate WHERE certificate_no = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    bizNo
            );
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, orgId == null ? asLong(row.get("orgId")) : orgId, tenantId);
        return row;
    }

    private Map<String, Object> queryHealthCheckRecord(Long bizId, String bizNo, Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (bizId != null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, check_no AS checkNo, employee_id AS employeeId, employee_name AS employeeName, check_date AS checkDate, check_time AS checkTime, " +
                            "temperature, face_image_url AS faceImageUrl, face_match_score AS faceMatchScore, certificate_status AS certificateStatus, hand_hygiene AS handHygiene, " +
                            "health_status AS healthStatus, check_result AS checkResult, fail_reason AS failReason, remark, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM health_check_record WHERE id = ? LIMIT 1",
                    bizId
            );
        }
        if (rows.isEmpty() && StrUtil.isNotBlank(bizNo)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, check_no AS checkNo, employee_id AS employeeId, employee_name AS employeeName, check_date AS checkDate, check_time AS checkTime, " +
                            "temperature, face_image_url AS faceImageUrl, face_match_score AS faceMatchScore, certificate_status AS certificateStatus, hand_hygiene AS handHygiene, " +
                            "health_status AS healthStatus, check_result AS checkResult, fail_reason AS failReason, remark, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM health_check_record WHERE check_no = ? ORDER BY id DESC LIMIT 1",
                    bizNo
            );
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, orgId == null ? asLong(row.get("orgId")) : orgId, tenantId);
        return row;
    }

    private Map<String, Object> queryDeviceInfo(Long bizId, String bizNo, String deviceCode, Long orgId, Long tenantId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (bizId != null) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, device_code AS deviceCode, device_name AS deviceName, device_type AS deviceType, device_model AS deviceModel, manufacturer, location_desc AS locationDesc, online_status AS onlineStatus, config_params AS configParams, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM device_info WHERE id = ? AND deleted = 0 LIMIT 1",
                    bizId
            );
        }
        if (rows.isEmpty() && StrUtil.isNotBlank(deviceCode)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, device_code AS deviceCode, device_name AS deviceName, device_type AS deviceType, device_model AS deviceModel, manufacturer, location_desc AS locationDesc, online_status AS onlineStatus, config_params AS configParams, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM device_info WHERE device_code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    deviceCode
            );
        }
        if (rows.isEmpty() && StrUtil.isNotBlank(bizNo)) {
            rows = jdbcTemplate.queryForList(
                    "SELECT id, device_code AS deviceCode, device_name AS deviceName, device_type AS deviceType, device_model AS deviceModel, manufacturer, location_desc AS locationDesc, online_status AS onlineStatus, config_params AS configParams, org_id AS orgId, tenant_id AS tenantId " +
                            "FROM device_info WHERE device_code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                    bizNo
            );
        }
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        ensureBusinessRowAccessible(row, orgId == null ? asLong(row.get("orgId")) : orgId, tenantId);
        return row;
    }

    private void ensureBusinessRowAccessible(Map<String, Object> row, Long orgId, Long tenantId) {
        if (row == null) {
            return;
        }
        Long rowTenantId = asLong(row.get("tenantId"));
        if (tenantId != null && rowTenantId != null && !Objects.equals(rowTenantId, tenantId)) {
            validateTenantReadable(rowTenantId);
        }
        Long rowOrgId = asLong(row.get("orgId"));
        if (orgId != null && rowOrgId != null && !Objects.equals(rowOrgId, orgId)) {
            validateOrgWritable(rowOrgId);
        }
    }

    private Map<String, Object> resolveEmployeeRow(IntegrationBinding binding, Map<String, Object> normalized, boolean allowBizIdAsEmployee) {
        Map<String, Object> employeeRow = queryEmployeeById(asLong(normalized.get("employeeId")), binding.getTenantId());
        if (employeeRow != null) {
            return employeeRow;
        }
        employeeRow = queryEmployeeByCode(asString(normalized.get("employeeCode")), binding.getTenantId());
        if (employeeRow != null) {
            return employeeRow;
        }
        if (allowBizIdAsEmployee) {
            employeeRow = queryEmployeeById(binding.getBizId(), binding.getTenantId());
            if (employeeRow != null) {
                return employeeRow;
            }
        }
        if (StrUtil.isNotBlank(binding.getBizNo())) {
            employeeRow = queryEmployeeByCode(binding.getBizNo(), binding.getTenantId());
        }
        return employeeRow;
    }

    private void insertSampleOperationLog(Long recordId, String action, String actionName, String content) {
        jdbcTemplate.update(
                "INSERT INTO sample_operation_log(record_id, action, action_name, operator_id, operator_name, content, terminal, created_at) VALUES(?,?,?,?,?,?,?,NOW())",
                recordId,
                action,
                actionName,
                firstNonNull(UserContext.getUserId(), 0L),
                firstNonBlank(UserContext.getRealName(), UserContext.getUsername(), "system"),
                content,
                "integration"
        );
    }

    private String resolveSampleRecordStatus(String currentStatus, String mappedStatus, LocalDateTime sampledAt, BigDecimal sampleWeight) {
        String normalizedStatus = normalizeSampleStatus(mappedStatus, null);
        if (normalizedStatus != null) {
            return normalizedStatus;
        }
        if (sampledAt != null || sampleWeight != null) {
            return SAMPLE_STATUS_SAMPLED;
        }
        return firstNonBlank(currentStatus, SAMPLE_STATUS_PENDING_SAMPLE);
    }

    private String resolveSampleResultStatus(String currentStatus, String mappedStatus, LocalDateTime destroyAt) {
        String normalizedStatus = normalizeSampleStatus(mappedStatus, null);
        if (normalizedStatus != null) {
            return normalizedStatus;
        }
        if (destroyAt != null) {
            return SAMPLE_STATUS_DISPOSED;
        }
        if (SAMPLE_STATUS_PENDING_SAMPLE.equals(currentStatus)) {
            return SAMPLE_STATUS_EVALUATED;
        }
        return SAMPLE_STATUS_EVALUATED.equals(currentStatus) ? SAMPLE_STATUS_EVALUATED : SAMPLE_STATUS_EVALUATED;
    }

    private String normalizeSampleStatus(String rawStatus, String fallback) {
        String status = StrUtil.trimToNull(asString(rawStatus));
        if (status == null) {
            return fallback;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case SAMPLE_STATUS_PENDING_SAMPLE, SAMPLE_STATUS_SAMPLED, SAMPLE_STATUS_EVALUATED, SAMPLE_STATUS_PENDING_DISPOSAL,
                    SAMPLE_STATUS_DISPOSED, SAMPLE_STATUS_OVERDUE, SAMPLE_STATUS_VOIDED, SAMPLE_STATUS_ARCHIVED -> status.toLowerCase(Locale.ROOT);
            default -> fallback;
        };
    }

    private String resolveHealthCertificateStatus(String rawStatus, LocalDate expireDate) {
        String normalized = StrUtil.trimToNull(asString(rawStatus));
        if (normalized != null) {
            String lower = normalized.toLowerCase(Locale.ROOT);
            if (Set.of(HEALTH_CERT_STATUS_PENDING, HEALTH_CERT_STATUS_VALID, HEALTH_CERT_STATUS_EXPIRING, HEALTH_CERT_STATUS_EXPIRED).contains(lower)) {
                return lower;
            }
            if (Set.of("pass", "matched", "ok", "success").contains(lower)) {
                return HEALTH_CERT_STATUS_VALID;
            }
            if (Set.of("expiring", "warning").contains(lower)) {
                return HEALTH_CERT_STATUS_EXPIRING;
            }
            if (Set.of("fail", "invalid", "expired", "mismatch").contains(lower)) {
                return HEALTH_CERT_STATUS_EXPIRED;
            }
        }
        if (expireDate == null) {
            return HEALTH_CERT_STATUS_PENDING;
        }
        if (expireDate.isBefore(LocalDate.now())) {
            return HEALTH_CERT_STATUS_EXPIRED;
        }
        if (!expireDate.isAfter(LocalDate.now().plusDays(30))) {
            return HEALTH_CERT_STATUS_EXPIRING;
        }
        return HEALTH_CERT_STATUS_VALID;
    }

    private String resolveCertificateStatusFromCheckResult(String raw) {
        String normalized = StrUtil.trimToNull(asString(raw));
        if (normalized == null) {
            return HEALTH_CERT_STATUS_PENDING;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (Set.of(HEALTH_CERT_STATUS_VALID, "pass", "matched", "success", "normal").contains(lower)) {
            return HEALTH_CERT_STATUS_VALID;
        }
        if (Set.of(HEALTH_CERT_STATUS_EXPIRING, "warning", "expiring").contains(lower)) {
            return HEALTH_CERT_STATUS_EXPIRING;
        }
        if (Set.of(HEALTH_CERT_STATUS_EXPIRED, "fail", "invalid", "mismatch", "expired").contains(lower)) {
            return HEALTH_CERT_STATUS_EXPIRED;
        }
        return HEALTH_CERT_STATUS_PENDING;
    }

    private String resolveMorningHealthStatus(String rawTemperatureStatus, BigDecimal temperature) {
        String status = StrUtil.trimToNull(asString(rawTemperatureStatus));
        if (status != null) {
            String lower = status.toLowerCase(Locale.ROOT);
            if (Set.of("high", "abnormal", "fever").contains(lower)) {
                return "abnormal";
            }
            if (Set.of("normal", "low", "ok").contains(lower)) {
                return "normal";
            }
        }
        if (temperature != null && temperature.compareTo(new BigDecimal("37.3")) >= 0) {
            return "abnormal";
        }
        return "normal";
    }

    private String resolveMorningCheckResultCode(Map<String, Object> normalized, String healthStatus, String handHygiene, String certificateStatus) {
        String explicit = normalizePassFailValue(firstNonBlank(asString(normalized.get("checkResult")), asString(normalized.get("status"))));
        if (explicit != null) {
            return explicit;
        }
        String faceResult = normalizePassFailValue(asString(normalized.get("faceCheckResult")));
        if (HEALTH_CHECK_RESULT_FAIL.equals(faceResult) || HEALTH_CHECK_RESULT_FAIL.equals(handHygiene) || "abnormal".equals(healthStatus)
                || HEALTH_CERT_STATUS_EXPIRED.equals(certificateStatus)) {
            return HEALTH_CHECK_RESULT_FAIL;
        }
        return HEALTH_CHECK_RESULT_PASS;
    }

    private String normalizePassFailValue(String raw) {
        String normalized = StrUtil.trimToNull(raw);
        if (normalized == null) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (Set.of("pass", "passed", "ok", "success", "normal", "matched").contains(lower)) {
            return HEALTH_CHECK_RESULT_PASS;
        }
        if (Set.of("fail", "failed", "abnormal", "error", "invalid", "mismatch").contains(lower)) {
            return HEALTH_CHECK_RESULT_FAIL;
        }
        return null;
    }

    private String buildMorningFailReason(Map<String, Object> normalized, String healthStatus,
                                          String handHygiene, String certificateStatus, String checkResult) {
        if (!HEALTH_CHECK_RESULT_FAIL.equals(checkResult)) {
            return null;
        }
        List<String> reasons = new ArrayList<>();
        if (HEALTH_CHECK_RESULT_FAIL.equals(normalizePassFailValue(asString(normalized.get("faceCheckResult"))))) {
            reasons.add("身份识别失败");
        }
        if ("abnormal".equals(healthStatus)) {
            reasons.add("体温异常");
        }
        if (HEALTH_CHECK_RESULT_FAIL.equals(handHygiene)) {
            reasons.add("手部检测异常");
        }
        if (HEALTH_CERT_STATUS_EXPIRED.equals(certificateStatus)) {
            reasons.add("健康证异常");
        }
        if (StrUtil.isNotBlank(asString(normalized.get("certificateCheckMessage")))) {
            reasons.add(asString(normalized.get("certificateCheckMessage")));
        }
        if (StrUtil.isNotBlank(asString(normalized.get("handCheckMessage")))) {
            reasons.add(asString(normalized.get("handCheckMessage")));
        }
        if (reasons.isEmpty()) {
            reasons.add("第三方晨检结果判定为不通过");
        }
        return joinAndTrim(reasons, "；", 300);
    }

    private String buildMorningRemark(String existingRemark, Map<String, Object> normalized) {
        List<String> parts = new ArrayList<>();
        if (StrUtil.isNotBlank(existingRemark)) {
            parts.add(existingRemark);
        }
        if (StrUtil.isNotBlank(asString(normalized.get("externalCheckId")))) {
            parts.add("第三方晨检ID=" + normalized.get("externalCheckId"));
        }
        if (StrUtil.isNotBlank(asString(normalized.get("deviceCode")))) {
            parts.add("设备编码=" + normalized.get("deviceCode"));
        }
        if (StrUtil.isNotBlank(asString(normalized.get("faceCheckResult")))) {
            parts.add("身份识别=" + normalized.get("faceCheckResult"));
        }
        if (StrUtil.isNotBlank(asString(normalized.get("certificateCheckMessage")))) {
            parts.add("健康证说明=" + normalized.get("certificateCheckMessage"));
        }
        if (StrUtil.isNotBlank(asString(normalized.get("handCheckMessage")))) {
            parts.add("手部说明=" + normalized.get("handCheckMessage"));
        }
        return joinAndTrim(parts, "；", 300);
    }

    private Integer resolveFaceEnrolledFlag(String rawStatus) {
        String normalized = StrUtil.trimToNull(rawStatus);
        if (normalized == null) {
            return 1;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (Set.of("inactive", "disabled", "deleted", "unenrolled", "0", "false").contains(lower)) {
            return 0;
        }
        return 1;
    }

    private String normalizeOnlineStatus(String rawStatus) {
        String normalized = StrUtil.trimToNull(rawStatus);
        if (normalized == null) {
            return "offline";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (Set.of("online", "offline", "fault").contains(lower)) {
            return lower;
        }
        if (Set.of("active", "enabled", "connected").contains(lower)) {
            return "online";
        }
        if (Set.of("error", "abnormal").contains(lower)) {
            return "fault";
        }
        return "offline";
    }

    private Map<String, Object> mergeJsonMap(String existingJson, Map<String, Object> additions) {
        Map<String, Object> merged = new LinkedHashMap<>(readObjectMap(readTree(existingJson)));
        additions.forEach((key, value) -> {
            if (value != null && !(value instanceof String text && StrUtil.isBlank(text))) {
                merged.put(key, value);
            }
        });
        return merged;
    }

    private String resolveJsonArrayColumn(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return null;
        }
        return writeJson(urls);
    }

    private List<String> resolveAttachmentUrls(List<FileTransferResult> fileResults, Object fallback) {
        List<String> urls = new ArrayList<>();
        if (fileResults != null) {
            fileResults.stream()
                    .map(FileTransferResult::minioUrl)
                    .filter(StrUtil::isNotBlank)
                    .forEach(urls::add);
        }
        if (!urls.isEmpty()) {
            return urls;
        }
        collectAttachmentUrls(fallback, urls);
        return urls;
    }

    private String firstAttachmentUrl(List<FileTransferResult> fileResults, Object fallback) {
        List<String> urls = resolveAttachmentUrls(fileResults, fallback);
        return urls.isEmpty() ? null : urls.get(0);
    }

    private void collectAttachmentUrls(Object value, List<String> urls) {
        if (value == null) {
            return;
        }
        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectAttachmentUrls(item, urls);
            }
            return;
        }
        if (value instanceof Map<?, ?> map) {
            String url = firstNonBlank(asString(map.get("url")), asString(map.get("fileUrl")), asString(map.get("link")));
            if (StrUtil.isNotBlank(url)) {
                urls.add(url);
            }
            return;
        }
        String text = asString(value);
        if (StrUtil.isBlank(text)) {
            return;
        }
        if (text.startsWith("[")) {
            JsonNode node = readTree(text);
            Object parsed = nodeToValue(node);
            if (!Objects.equals(parsed, text)) {
                collectAttachmentUrls(parsed, urls);
                return;
            }
        }
        if (text.startsWith("http")) {
            urls.add(text);
        }
    }

    private String normalizeDateTimeForJson(Object raw) {
        LocalDateTime value = parseDateTime(raw);
        return value == null ? asString(raw) : value.format(STANDARD_TIME);
    }

    private String joinAndTrim(List<String> parts, String delimiter, int maxLength) {
        String result = parts.stream()
                .filter(StrUtil::isNotBlank)
                .distinct()
                .collect(Collectors.joining(delimiter));
        if (result.length() <= maxLength) {
            return result;
        }
        return result.substring(0, maxLength);
    }

    private void markTaskRunning(IntegrationSyncTask task, LocalDateTime start) {
        task.setTaskStatus(TASK_STATUS_RUNNING);
        task.setStartAt(start);
        syncTaskMapper.updateById(task);
    }

    private void updateTaskAndBindingAfterSuccess(IntegrationSyncTask task, IntegrationBinding binding,
                                                  IntegrationModuleConfig config, NormalizedResult normalized,
                                                  long durationMs, TaskExecutionOptions options) {
        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(TASK_STATUS_SUCCESS);
        task.setFinishAt(now);
        task.setResultMessage("success");
        syncTaskMapper.updateById(task);

        if (options.persistBindingState() && binding.getId() != null && binding.getId() > 0) {
            binding.setSyncStatus(TASK_STATUS_SUCCESS);
            binding.setLastSyncAt(now);
            binding.setNextSyncAt(determineNextSyncAt(config, binding, normalized.statusMatch()));
            bindingMapper.updateById(binding);
        }
        updateConfigHealthStatus(config.getId(), TASK_STATUS_SUCCESS, "最近一次同步成功，耗时" + durationMs + "ms");
    }

    private void updateTaskAndBindingAfterFailure(IntegrationSyncTask task, IntegrationBinding binding,
                                                  IntegrationModuleConfig config, String errorMessage,
                                                  TaskExecutionOptions options) {
        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(TASK_STATUS_FAILED);
        task.setFinishAt(now);
        task.setResultMessage(errorMessage);
        syncTaskMapper.updateById(task);

        if (options.persistBindingState() && binding.getId() != null && binding.getId() > 0) {
            binding.setSyncStatus(TASK_STATUS_FAILED);
            binding.setLastSyncAt(now);
            binding.setNextSyncAt(determineNextSyncAt(config, binding, null));
            bindingMapper.updateById(binding);
        }
        updateConfigHealthStatus(config.getId(), TASK_STATUS_FAILED, errorMessage);
    }

    private void updateTaskAndBindingAfterSpecialStatus(IntegrationSyncTask task, IntegrationBinding binding,
                                                        IntegrationModuleConfig config, String status,
                                                        String message, StatusMatch statusMatch,
                                                        TaskExecutionOptions options) {
        LocalDateTime now = LocalDateTime.now();
        task.setTaskStatus(status);
        task.setFinishAt(now);
        task.setResultMessage(message);
        syncTaskMapper.updateById(task);

        if (options.persistBindingState() && binding.getId() != null && binding.getId() > 0) {
            binding.setSyncStatus(status);
            binding.setLastSyncAt(now);
            binding.setNextSyncAt(null);
            binding.setRemark(buildTaskPendingHandleRemark(status, message));
            bindingMapper.updateById(binding);
        }
        updateConfigHealthStatus(config.getId(), status, message);
    }

    private String buildTaskPendingHandleRemark(String status, String message) {
        if (TASK_STATUS_NO_DATA.equals(status)) {
            return "同步结果为无数据，系统已停止自动轮询，请到【同步任务管理】切换“失败待处理”查看并人工确认";
        }
        if (TASK_STATUS_MAPPING_MISSING.equals(status)) {
            return firstNonBlank(message, "同步结果缺少状态映射，系统已停止自动轮询，请到【同步任务管理】切换“失败待处理”查看并人工处理");
        }
        return firstNonBlank(message, "同步失败");
    }

    private void updateConfigHealthStatus(Long configId, String status, String message) {
        IntegrationModuleConfig update = new IntegrationModuleConfig();
        update.setId(configId);
        update.setLastSyncAt(LocalDateTime.now());
        update.setLastSyncStatus(status);
        update.setLastErrorMessage(TASK_STATUS_SUCCESS.equals(status) ? null : message);
        moduleConfigMapper.updateById(update);
    }

    private LocalDateTime determineNextSyncAt(IntegrationModuleConfig config, IntegrationBinding binding, StatusMatch statusMatch) {
        if (!MODE_THIRD_PARTY.equals(binding.getMaintenanceMode())) {
            return null;
        }
        if (statusMatch != null && statusMatch.finishFlag()) {
            return null;
        }
        String triggerStrategy = firstNonBlank(config.getTriggerStrategy(), MODE_MANUAL);
        if (!TRIGGER_STRATEGY_SCHEDULER.equals(triggerStrategy)) {
            return null;
        }
        if (StrUtil.isNotBlank(config.getScheduleCron())) {
            LocalDateTime next = CronExpression.parse(config.getScheduleCron()).next(LocalDateTime.now());
            if (next == null) {
                throw BizException.validationFailed("计划Cron未生成下一次执行时间，请检查配置");
            }
            return next;
        }
        if (config.getSyncFrequencyMinutes() == null || config.getSyncFrequencyMinutes() <= 0) {
            return null;
        }
        return LocalDateTime.now().plusMinutes(config.getSyncFrequencyMinutes());
    }

    private Long createSyncLog(IntegrationSyncTask task, IntegrationBinding binding, IntegrationModuleConfig config,
                               SyncLogCreatePayload payload) {
        IntegrationSyncLog logEntity = new IntegrationSyncLog();
        logEntity.setTaskId(task.getId());
        logEntity.setBindingId(binding.getId());
        logEntity.setConfigId(config.getId());
        logEntity.setConfigNameSnapshot(config.getConfigName());
        logEntity.setProviderCode(config.getProviderCode());
        logEntity.setProviderNameSnapshot(resolveProviderNameSnapshot(config.getProviderCode()));
        logEntity.setBizId(binding.getBizId());
        logEntity.setBizNo(binding.getBizNo());
        logEntity.setBizModule(binding.getBizModule());
        logEntity.setBizScene(binding.getBizScene());
        logEntity.setExternalNo(binding.getExternalNo());
        logEntity.setTaskType(task.getTaskType());
        logEntity.setRequestPayload(payload.requestPayload());
        logEntity.setRequestHeaders(payload.requestHeaders());
        logEntity.setRequestBody(payload.requestBody());
        logEntity.setResponsePayload(payload.responsePayload());
        logEntity.setNormalizedPayload(payload.normalizedPayload());
        logEntity.setSyncStatus(payload.syncStatus());
        logEntity.setErrorCode(payload.errorCode());
        logEntity.setErrorMessage(payload.errorMessage());
        logEntity.setDurationMs(payload.durationMs());
        logEntity.setAuditLogId(payload.auditLogId());
        logEntity.setResultMessage(payload.resultMessage());
        logEntity.setWriteBackResult(payload.writeBackResult());
        logEntity.setTriggerType(firstNonBlank(task.getTriggerType(), TRIGGER_TYPE_MANUAL));
        logEntity.setOperatorId(firstNonNull(task.getOperatorId(), 0L));
        logEntity.setOperatorName(firstNonBlank(task.getOperatorName(), "system"));
        logEntity.setOrgId(binding.getOrgId());
        logEntity.setOrgNameSnapshot(resolveOrgNameSnapshot(binding.getOrgId()));
        logEntity.setTenantId(binding.getTenantId());
        logEntity.setHandleStatus(resolveDefaultSyncLogHandleStatus(payload.syncStatus()));
        syncLogMapper.insert(logEntity);
        return logEntity.getId();
    }

    private void attachAuditLogToLatestSyncLog(IntegrationSyncTask task, Long auditLogId) {
        if (task == null || task.getId() == null || auditLogId == null) {
            return;
        }
        IntegrationSyncLog logEntity = syncLogMapper.selectOne(new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(IntegrationSyncLog::getTaskId, task.getId())
                .orderByDesc(IntegrationSyncLog::getId)
                .last("LIMIT 1"));
        if (logEntity == null || logEntity.getAuditLogId() != null) {
            return;
        }
        IntegrationSyncLog updateEntity = new IntegrationSyncLog();
        updateEntity.setId(logEntity.getId());
        updateEntity.setAuditLogId(auditLogId);
        syncLogMapper.updateById(updateEntity);
    }

    private String resolveProviderNameSnapshot(String providerCode) {
        if (StrUtil.isBlank(providerCode)) {
            return null;
        }
        return firstNonBlank(loadProviderNameMap(Set.of(providerCode)).get(providerCode), providerCode);
    }

    private String resolveConfigNameSnapshot(Long configId) {
        if (configId == null) {
            return null;
        }
        IntegrationModuleConfig config = moduleConfigMapper.selectById(configId);
        return config == null ? null : config.getConfigName();
    }

    private String resolveOrgNameSnapshot(Long orgId) {
        if (orgId == null) {
            return null;
        }
        return loadOrgNames(Set.of(orgId)).get(orgId);
    }

    private String resolveDefaultSyncLogHandleStatus(String syncStatus) {
        if (TASK_PENDING_HANDLE_STATUSES.contains(syncStatus)) {
            return LOG_HANDLE_STATUS_PENDING_REVIEW;
        }
        return LOG_HANDLE_STATUS_CONFIRMED;
    }

    private void validateSyncLogQuery(IntegrationSyncLogQueryDTO query) {
        if (query == null) {
            throw BizException.validationFailed("同步日志查询参数不能为空");
        }
        query.setPageNum(firstNonNull(query.getPageNum(), 1L));
        query.setPageSize(firstNonNull(query.getPageSize(), 20L));
        if (query.getPageNum() < 1) {
            throw BizException.validationFailed("页码必须大于等于1");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > 200) {
            throw BizException.validationFailed("每页条数必须在1到200之间");
        }
        query.setKeyword(StrUtil.trimToNull(query.getKeyword()));
        query.setBizModule(StrUtil.trimToNull(query.getBizModule()));
        query.setBizScene(StrUtil.trimToNull(query.getBizScene()));
        query.setProviderCode(StrUtil.trimToNull(query.getProviderCode()));
        query.setSyncStatus(StrUtil.trimToNull(query.getSyncStatus()));
        query.setTriggerType(StrUtil.trimToNull(query.getTriggerType()));
        query.setHandleStatus(StrUtil.trimToNull(query.getHandleStatus()));
        query.setStartTime(StrUtil.trimToNull(query.getStartTime()));
        query.setEndTime(StrUtil.trimToNull(query.getEndTime()));
        LocalDateTime startTime = parseSyncLogQueryTime(query.getStartTime(), "开始时间");
        LocalDateTime endTime = parseSyncLogQueryTime(query.getEndTime(), "结束时间");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw BizException.validationFailed("开始时间不能晚于结束时间");
        }
    }

    private void validateCallbackLogQuery(IntegrationCallbackLogQueryDTO query) {
        if (query == null) {
            throw BizException.validationFailed("回调日志查询参数不能为空");
        }
        query.setPageNum(firstNonNull(query.getPageNum(), 1L));
        query.setPageSize(firstNonNull(query.getPageSize(), 20L));
        if (query.getPageNum() < 1) {
            throw BizException.validationFailed("页码必须大于等于1");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > 200) {
            throw BizException.validationFailed("每页条数必须在1到200之间");
        }
        query.setKeyword(StrUtil.trimToNull(query.getKeyword()));
        query.setBizModule(StrUtil.trimToNull(query.getBizModule()));
        query.setBizScene(StrUtil.trimToNull(query.getBizScene()));
        query.setProviderCode(StrUtil.trimToNull(query.getProviderCode()));
        query.setSignResult(StrUtil.trimToNull(query.getSignResult()));
        query.setProcessStatus(StrUtil.trimToNull(query.getProcessStatus()));
        query.setStartTime(StrUtil.trimToNull(query.getStartTime()));
        query.setEndTime(StrUtil.trimToNull(query.getEndTime()));
        LocalDateTime startTime = parseSyncLogQueryTime(query.getStartTime(), "开始时间");
        LocalDateTime endTime = parseSyncLogQueryTime(query.getEndTime(), "结束时间");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw BizException.validationFailed("开始时间不能晚于结束时间");
        }
    }

    private void validateFileRecordQuery(IntegrationFileRecordQueryDTO query) {
        if (query == null) {
            throw BizException.validationFailed("附件转存记录查询参数不能为空");
        }
        query.setPageNum(firstNonNull(query.getPageNum(), 1L));
        query.setPageSize(firstNonNull(query.getPageSize(), 20L));
        if (query.getPageNum() < 1) {
            throw BizException.validationFailed("页码必须大于等于1");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > 200) {
            throw BizException.validationFailed("每页条数必须在1到200之间");
        }
        if (query.getBindingId() != null && query.getBindingId() < 1) {
            throw BizException.validationFailed("绑定记录ID必须大于等于1");
        }
        query.setKeyword(StrUtil.trimToNull(query.getKeyword()));
        query.setBizModule(StrUtil.trimToNull(query.getBizModule()));
        query.setBizScene(StrUtil.trimToNull(query.getBizScene()));
        query.setBizNo(StrUtil.trimToNull(query.getBizNo()));
        query.setProviderCode(StrUtil.trimToNull(query.getProviderCode()));
        query.setDownloadStatus(StrUtil.trimToNull(query.getDownloadStatus()));
        query.setStorageStatus(StrUtil.trimToNull(query.getStorageStatus()));
        query.setStartTime(StrUtil.trimToNull(query.getStartTime()));
        query.setEndTime(StrUtil.trimToNull(query.getEndTime()));
        if (StrUtil.isNotBlank(query.getDownloadStatus()) && !ALLOWED_FILE_RECORD_STATUSES.contains(query.getDownloadStatus())) {
            throw BizException.validationFailed("下载状态仅支持 pending、success、failed、skipped、reused");
        }
        if (StrUtil.isNotBlank(query.getStorageStatus()) && !ALLOWED_FILE_RECORD_STATUSES.contains(query.getStorageStatus())) {
            throw BizException.validationFailed("转存状态仅支持 pending、success、failed、skipped、reused");
        }
        LocalDateTime startTime = parseSyncLogQueryTime(query.getStartTime(), "开始时间");
        LocalDateTime endTime = parseSyncLogQueryTime(query.getEndTime(), "结束时间");
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw BizException.validationFailed("开始时间不能晚于结束时间");
        }
    }

    private void validateHealthCheckQuery(IntegrationHealthCheckQueryDTO query) {
        if (query == null) {
            throw BizException.validationFailed("健康检查查询参数不能为空");
        }
        query.setPageNum(firstNonNull(query.getPageNum(), 1L));
        query.setPageSize(firstNonNull(query.getPageSize(), 20L));
        if (query.getPageNum() < 1) {
            throw BizException.validationFailed("页码必须大于等于1");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > HEALTH_CHECK_PAGE_SIZE_MAX) {
            throw BizException.validationFailed("每页条数必须在1到" + HEALTH_CHECK_PAGE_SIZE_MAX + "之间");
        }
        if (query.getConfigId() != null && query.getConfigId() < 1) {
            throw BizException.validationFailed("接入配置ID必须大于等于1");
        }
        query.setKeyword(StrUtil.trimToNull(query.getKeyword()));
        query.setBizModule(StrUtil.trimToNull(query.getBizModule()));
        query.setBizScene(StrUtil.trimToNull(query.getBizScene()));
        query.setProviderCode(StrUtil.trimToNull(query.getProviderCode()));
    }

    private LocalDateTime parseSyncLogQueryTime(String value, String fieldLabel) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), STANDARD_TIME);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (DateTimeParseException ignored) {
                throw BizException.validationFailed(fieldLabel + "格式不正确，请使用 yyyy-MM-dd HH:mm:ss");
            }
        }
    }

    private RequestLogSnapshot buildRequestLogSnapshot(RequestSpec requestSpec) {
        if (requestSpec == null) {
            return null;
        }
        HttpHeaders headers = new HttpHeaders();
        requestSpec.headers().forEach(headers::set);
        URI uri = buildUri(StrUtil.blankToDefault(requestSpec.url(), ""), requestSpec.query());
        return buildRequestLogSnapshot(requestSpec, uri, headers, requestSpec.body());
    }

    private RequestLogSnapshot buildRequestLogSnapshot(RequestSpec requestSpec, URI uri, HttpHeaders headers, Object body) {
        if (requestSpec == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("method", firstNonBlank(requestSpec.method(), "GET"));
        payload.put("url", sanitizeRequestUrl(uri == null ? requestSpec.url() : uri.toString(), requestSpec.query()));
        Object sanitizedQuery = sanitizeLogValue(requestSpec.query(), null);
        if (sanitizedQuery instanceof Map<?, ?> queryMap && !queryMap.isEmpty()) {
            payload.put("query", sanitizedQuery);
        }
        Object sanitizedHeaders = sanitizeLogValue(convertHeadersToMap(headers), null);
        if (sanitizedHeaders instanceof Map<?, ?> headerMap && !headerMap.isEmpty()) {
            payload.put("headers", sanitizedHeaders);
        }
        Object sanitizedBody = sanitizeLogValue(body, null);
        if (sanitizedBody != null) {
            payload.put("body", sanitizedBody);
        }
        return new RequestLogSnapshot(
                safeJson(payload),
                headerMapToJson(sanitizedHeaders),
                safeJson(sanitizedBody)
        );
    }

    private String headerMapToJson(Object sanitizedHeaders) {
        if (!(sanitizedHeaders instanceof Map<?, ?> headerMap) || headerMap.isEmpty()) {
            return null;
        }
        return safeJson(headerMap);
    }

    private Map<String, Object> convertHeadersToMap(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (value == null || value.isEmpty()) {
                result.put(key, null);
            } else if (value.size() == 1) {
                result.put(key, value.get(0));
            } else {
                result.put(key, new ArrayList<>(value));
            }
        });
        return result;
    }

    private String sanitizeRequestUrl(String url, Map<String, Object> query) {
        if (StrUtil.isBlank(url)) {
            return url;
        }
        Object sanitizedQuery = sanitizeLogValue(query, null);
        if (!(sanitizedQuery instanceof Map<?, ?> queryMap) || queryMap.isEmpty()) {
            return url;
        }
        String baseUrl = url;
        int queryIndex = url.indexOf('?');
        if (queryIndex >= 0) {
            baseUrl = url.substring(0, queryIndex);
        }
        String joinedQuery = queryMap.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
        return StrUtil.isBlank(joinedQuery) ? baseUrl : baseUrl + "?" + joinedQuery;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeLogValue(Object value, String fieldKey) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((key, itemValue) -> {
                String childKey = key == null ? null : String.valueOf(key);
                sanitized.put(childKey, sanitizeLogValue(itemValue, childKey));
            });
            return sanitized;
        }
        if (value instanceof HttpHeaders httpHeaders) {
            return sanitizeLogValue(convertHeadersToMap(httpHeaders), fieldKey);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(item -> sanitizeLogValue(item, fieldKey))
                    .collect(Collectors.toList());
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                result.add(sanitizeLogValue(java.lang.reflect.Array.get(value, i), fieldKey));
            }
            return result;
        }
        if (value instanceof JsonNode node) {
            return sanitizeLogValue(nodeToValue(node), fieldKey);
        }
        if (value instanceof String stringValue) {
            if (isSensitiveLogFieldKey(fieldKey)) {
                return maskSecretValue(stringValue);
            }
            if ("authorization".equalsIgnoreCase(fieldKey) || stringValue.startsWith("Bearer ")) {
                return maskAuthorizationValue(stringValue);
            }
            return stringValue;
        }
        return isSensitiveLogFieldKey(fieldKey) ? maskSecretValue(String.valueOf(value)) : value;
    }

    private boolean isSensitiveLogFieldKey(String fieldKey) {
        if (StrUtil.isBlank(fieldKey)) {
            return false;
        }
        String normalized = fieldKey.replace("-", "").replace("_", "").toLowerCase(Locale.ROOT);
        if (SENSITIVE_LOG_FIELD_KEYS.contains(normalized)) {
            return true;
        }
        return normalized.contains("secret") || normalized.contains("token") || normalized.contains("authorization");
    }

    private String maskAuthorizationValue(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("Bearer ")) {
            return "Bearer " + maskSecretValue(trimmed.substring(7));
        }
        return maskSecretValue(trimmed);
    }

    private String maskSecretValue(String value) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 4) {
            return "****";
        }
        return trimmed.substring(0, 2) + "****" + trimmed.substring(trimmed.length() - 2);
    }

    private HealthCheckResult performConnectionTest(IntegrationModuleConfig config, IntegrationProviderTemplate provider, Map<String, String> secrets) {
        RequestSpec spec = buildRequestSpec(config, provider, buildHealthCheckProbeBinding(config), secrets);
        Boolean authSuccess = null;
        Boolean reachable = null;
        boolean warning = false;
        boolean failed = false;
        String authMessage;
        String reachabilityMessage = null;
        String errorCode = null;
        String errorMessage = null;
        RequestLogSnapshot requestSnapshot = null;
        String responsePayload = null;
        CallbackReachabilityResult callbackResult = testCallbackReachability(config, provider);
        String callbackMessage = callbackResult.message();
        Boolean callbackReachable = callbackResult.reachable();
        if (callbackResult.applicable() && Boolean.FALSE.equals(callbackReachable)) {
            failed = true;
            errorCode = HEALTH_CHECK_ERROR_CALLBACK;
            errorMessage = callbackMessage;
        }
        if (StrUtil.isBlank(config.getAccessTokenUrl())) {
            authMessage = "当前配置未配置独立 Token 地址，将通过业务接口响应结果校验鉴权";
        } else {
            try {
                String accessToken = resolveAccessToken(config, secrets, false);
                authSuccess = StrUtil.isNotBlank(accessToken);
                authMessage = authSuccess ? "AccessToken 获取成功" : TOKEN_RESPONSE_EMPTY_ERROR;
                if (!authSuccess) {
                    failed = true;
                    errorCode = SYNC_LOG_ERROR_CODE_TOKEN;
                    errorMessage = authMessage;
                }
            } catch (Exception ex) {
                authSuccess = false;
                authMessage = firstNonBlank(ex.getMessage(), "鉴权失败");
                failed = true;
                errorCode = SYNC_LOG_ERROR_CODE_TOKEN;
                errorMessage = authMessage;
            }
        }
        if (StrUtil.isBlank(spec.url())) {
            reachable = false;
            reachabilityMessage = PROVIDER_RUNTIME_URL_MISSING_ERROR;
            failed = true;
            errorCode = firstNonBlank(errorCode, SYNC_LOG_ERROR_CODE_RUNTIME_URL_MISSING);
            errorMessage = firstNonBlank(errorMessage, reachabilityMessage);
            return new HealthCheckResult(
                    TASK_STATUS_FAILED,
                    authSuccess,
                    reachable,
                    callbackReachable,
                    buildHealthCheckSummary(TASK_STATUS_FAILED, authMessage, reachabilityMessage, callbackMessage, errorMessage),
                    authMessage,
                    reachabilityMessage,
                    callbackMessage,
                    buildRequestLogSnapshot(spec),
                    null,
                    errorCode,
                    errorMessage
            );
        }
        try {
            ThirdPartyResponseResult responseResult = requestThirdParty(provider, config, secrets, spec);
            requestSnapshot = responseResult.requestSnapshot();
            responsePayload = responseResult.responsePayload();
            reachable = true;
            if (authSuccess == null) {
                authSuccess = true;
                authMessage = "通过业务接口验证鉴权成功";
            }
            try {
                JsonNode strictResponse = parseStrictHealthResponse(responseResult.responsePayload());
                HealthResponseInspection inspection = inspectHealthResponse(strictResponse);
                reachabilityMessage = inspection.message();
                if (inspection.warning()) {
                    if (HEALTH_CHECK_ERROR_INVALID_RESPONSE.equals(inspection.errorCode())) {
                        failed = true;
                    } else {
                        warning = true;
                    }
                    errorCode = firstNonBlank(errorCode, inspection.errorCode());
                    errorMessage = firstNonBlank(errorMessage, inspection.message());
                }
            } catch (BizException ex) {
                reachabilityMessage = firstNonBlank(ex.getMessage(), "第三方返回内容不符合预期");
                failed = true;
                errorCode = HEALTH_CHECK_ERROR_INVALID_RESPONSE;
                errorMessage = firstNonBlank(errorMessage, reachabilityMessage);
            }
        } catch (ThirdPartyRequestException ex) {
            requestSnapshot = firstNonNull(ex.requestSnapshot(), buildRequestLogSnapshot(spec));
            responsePayload = ex.responsePayload();
            Integer httpStatus = ex.httpStatus();
            if (httpStatus != null) {
                reachable = true;
                if (httpStatus == 401 || httpStatus == 403) {
                    authSuccess = false;
                    authMessage = "第三方接口已响应，但鉴权失败（HTTP " + httpStatus + "）";
                    reachabilityMessage = "第三方接口可达，但返回鉴权拒绝";
                    failed = true;
                    errorCode = "HTTP_" + httpStatus;
                    errorMessage = firstNonBlank(ex.getMessage(), authMessage);
                } else if (isLikelyTestPlaceholderFailure(ex, responsePayload)) {
                    if (authSuccess == null) {
                        authSuccess = true;
                        authMessage = "第三方接口已响应，鉴权前置校验通过";
                    }
                    reachabilityMessage = "第三方接口可达，但测试占位参数未通过业务校验；如需验证真实业务，请改用真实单号联调";
                    warning = true;
                    errorCode = HEALTH_CHECK_ERROR_PLACEHOLDER;
                    errorMessage = firstNonBlank(ex.getMessage(), reachabilityMessage);
                } else if (httpStatus == 429) {
                    if (authSuccess == null) {
                        authSuccess = true;
                        authMessage = "第三方接口已响应，鉴权前置校验通过";
                    }
                    reachabilityMessage = "第三方接口可达，但当前被限流（HTTP 429）";
                    warning = true;
                    errorCode = "HTTP_429";
                    errorMessage = firstNonBlank(ex.getMessage(), reachabilityMessage);
                } else if (httpStatus >= 400 && httpStatus < 500) {
                    if (authSuccess == null) {
                        authSuccess = true;
                        authMessage = "第三方接口已响应，鉴权前置校验通过";
                    }
                    reachabilityMessage = "第三方接口可达，但请求参数或模板未通过第三方校验（HTTP " + httpStatus + "）";
                    warning = true;
                    errorCode = "HTTP_" + httpStatus;
                    errorMessage = firstNonBlank(ex.getMessage(), reachabilityMessage);
                } else {
                    if (authSuccess == null) {
                        authSuccess = true;
                        authMessage = "第三方接口已响应，鉴权前置校验通过";
                    }
                    reachabilityMessage = "第三方接口可达，但服务端返回异常（HTTP " + httpStatus + "）";
                    failed = true;
                    errorCode = "HTTP_" + httpStatus;
                    errorMessage = firstNonBlank(ex.getMessage(), reachabilityMessage);
                }
            } else {
                reachable = false;
                if (authSuccess == null && StrUtil.isBlank(config.getAccessTokenUrl())) {
                    authMessage = "尚未通过第三方响应确认鉴权状态";
                }
                reachabilityMessage = firstNonBlank(ex.getMessage(), "第三方接口不可达");
                failed = true;
                errorCode = firstNonBlank(ex.errorCode(), SYNC_LOG_ERROR_CODE_NETWORK);
                errorMessage = firstNonBlank(ex.getMessage(), reachabilityMessage);
            }
        } catch (Exception ex) {
            requestSnapshot = buildRequestLogSnapshot(spec);
            reachable = false;
            if (authSuccess == null && StrUtil.isBlank(config.getAccessTokenUrl())) {
                authMessage = "尚未通过第三方响应确认鉴权状态";
            }
            reachabilityMessage = firstNonBlank(ex.getMessage(), "连接测试失败");
            failed = true;
            errorCode = firstNonBlank(errorCode, EXECUTE_ERROR_CODE);
            errorMessage = firstNonBlank(errorMessage, reachabilityMessage);
        }
        String status = failed ? TASK_STATUS_FAILED : warning ? HEALTH_CHECK_STATUS_WARNING : TASK_STATUS_SUCCESS;
        return new HealthCheckResult(
                status,
                authSuccess,
                reachable,
                callbackReachable,
                buildHealthCheckSummary(status, authMessage, reachabilityMessage, callbackMessage, errorMessage),
                authMessage,
                reachabilityMessage,
                callbackMessage,
                firstNonNull(requestSnapshot, buildRequestLogSnapshot(spec)),
                responsePayload,
                errorCode,
                errorMessage
        );
    }

    private HealthCheckResult buildHistoricalHealth(IntegrationModuleConfig config, IntegrationProviderTemplate provider,
                                                    List<IntegrationHealthCheckLog> recentTestLogs) {
        if (provider == null) {
            return new HealthCheckResult(
                    TASK_STATUS_FAILED,
                    null,
                    null,
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? Boolean.FALSE : null,
                    "当前平台模板不存在，无法确认历史健康状态，请检查平台模板与配置数据",
                    "平台模板不存在，无法确认鉴权状态",
                    "平台模板不存在，无法确认接口连通性",
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? "平台模板不存在，无法确认回调连通性" : "当前配置未启用回调",
                    null,
                    null,
                    "PROVIDER_NOT_FOUND",
                    "第三方平台模板不存在"
            );
        }
        if (!"active".equals(provider.getStatus())) {
            return new HealthCheckResult(
                    TASK_STATUS_FAILED,
                    null,
                    null,
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? Boolean.FALSE : null,
                    BUILTIN_PROVIDER_STATUS_STOP_MESSAGE,
                    "平台模板已停用，未再进行鉴权检测",
                    "平台模板已停用，未再进行接口连通性检测",
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? "平台模板已停用，未再进行回调连通性检测" : "当前配置未启用回调",
                    null,
                    null,
                    SYNC_LOG_ERROR_CODE_PROVIDER_DISABLED,
                    BUILTIN_PROVIDER_STATUS_STOP_MESSAGE
            );
        }
        IntegrationHealthCheckLog latest = recentTestLogs == null || recentTestLogs.isEmpty() ? null : recentTestLogs.get(0);
        if (!isHealthCheckLogTableAvailable()) {
            return new HealthCheckResult(
                    HEALTH_CHECK_STATUS_UNKNOWN,
                    null,
                    null,
                    null,
                    "当前环境未初始化健康检查日志表，暂无法展示测试历史；页面已降级展示基础健康概览",
                    "健康检查日志表未初始化，暂无鉴权历史",
                    "健康检查日志表未初始化，暂无接口连通性历史",
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? "健康检查日志表未初始化，暂无回调连通性历史" : "当前配置未启用回调",
                    null,
                    null,
                    null,
                    null
            );
        }
        if (latest == null) {
            return new HealthCheckResult(
                    HEALTH_CHECK_STATUS_UNKNOWN,
                    null,
                    null,
                    null,
                    "暂无健康检查记录，请先执行“测试连接”验证鉴权、接口和回调地址",
                    "暂无鉴权检测记录",
                    "暂无接口连通性检测记录",
                    config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1 ? "暂无回调连通性检测记录" : "当前配置未启用回调",
                    null,
                    null,
                    null,
                    null
            );
        }
        return new HealthCheckResult(
                firstNonBlank(latest.getTestStatus(), HEALTH_CHECK_STATUS_UNKNOWN),
                toNullableBoolean(latest.getAuthSuccess()),
                toNullableBoolean(latest.getReachable()),
                toNullableBoolean(latest.getCallbackReachable()),
                firstNonBlank(latest.getTestMessage(), "最近一次健康检查已完成"),
                latest.getAuthMessage(),
                latest.getReachabilityMessage(),
                latest.getCallbackMessage(),
                null,
                latest.getResponsePayload(),
                latest.getErrorCode(),
                latest.getErrorMessage()
        );
    }

    private IntegrationHealthCheckVO buildHealthCheckVO(IntegrationModuleConfig config, IntegrationProviderTemplate provider,
                                                        HealthCheckResult health, List<IntegrationSyncLogVO> recentFailedLogs,
                                                        List<IntegrationHealthCheckLogVO> recentTestLogs) {
        IntegrationHealthCheckVO vo = new IntegrationHealthCheckVO();
        vo.setConfigId(config.getId());
        vo.setConfigName(config.getConfigName());
        vo.setOrgId(config.getOrgId());
        vo.setOrgName(loadOrgNames(Set.of(config.getOrgId())).get(config.getOrgId()));
        vo.setBizModule(config.getBizModule());
        vo.setBizScene(config.getBizScene());
        vo.setProviderCode(config.getProviderCode());
        vo.setProviderName(provider == null ? config.getProviderCode() : firstNonBlank(provider.getProviderName(), config.getProviderCode()));
        vo.setEnabled(config.getEnabled());
        vo.setCallbackEnabled(config.getCallbackEnabled());
        vo.setAuthSuccess(health.authSuccess());
        vo.setReachable(health.reachable());
        vo.setCallbackReachable(health.callbackReachable());
        vo.setAuthMessage(health.authMessage());
        vo.setReachableMessage(health.reachabilityMessage());
        vo.setCallbackMessage(health.callbackMessage());
        vo.setTestMessage(health.message());
        if (recentTestLogs != null && !recentTestLogs.isEmpty()) {
            IntegrationHealthCheckLogVO latest = recentTestLogs.get(0);
            vo.setLastTestAt(latest.getCreatedAt());
            vo.setLastTestStatus(firstNonBlank(latest.getTestStatus(), HEALTH_CHECK_STATUS_UNKNOWN));
            vo.setLastTestMessage(firstNonBlank(latest.getTestMessage(), health.message()));
        } else {
            vo.setLastTestStatus(HEALTH_CHECK_STATUS_UNKNOWN);
            vo.setLastTestMessage(health.message());
        }
        vo.setSuccessRate24h(percent(queryCount(
                "SELECT COUNT(*) FROM sys_integration_sync_log WHERE deleted = 0 AND config_id = ? AND sync_status = 'success' AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                config.getId()
        ), Math.max(queryCount(
                "SELECT COUNT(*) FROM sys_integration_sync_log WHERE deleted = 0 AND config_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                config.getId()
        ), 1)));
        vo.setCallbackSuccessRate24h(percent(queryCount(
                "SELECT COUNT(*) FROM sys_integration_callback_log WHERE deleted = 0 AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
                        "AND tenant_id = ? AND org_id = ? AND (config_id = ? OR (config_id IS NULL AND provider_code = ? AND org_id = ? AND tenant_id = ?)) " +
                        "AND process_status = 'success'",
                config.getTenantId(), config.getOrgId(), config.getId(), config.getProviderCode(), config.getOrgId(), config.getTenantId()
        ), Math.max(queryCount(
                "SELECT COUNT(*) FROM sys_integration_callback_log WHERE deleted = 0 AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
                        "AND tenant_id = ? AND org_id = ? AND (config_id = ? OR (config_id IS NULL AND provider_code = ? AND org_id = ? AND tenant_id = ?))",
                config.getTenantId(), config.getOrgId(), config.getId(), config.getProviderCode(), config.getOrgId(), config.getTenantId()
        ), 1)));
        vo.setAverageDurationMs24h(queryLong(
                "SELECT COALESCE(AVG(duration_ms),0) FROM sys_integration_sync_log WHERE deleted = 0 AND config_id = ? AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                config.getId()
        ));
        vo.setLastSyncAt(config.getLastSyncAt());
        vo.setLastSyncStatus(config.getLastSyncStatus());
        vo.setLastErrorMessage(config.getLastErrorMessage());
        vo.setRecentFailedLogs(recentFailedLogs);
        vo.setRecentTestLogs(firstNonNull(recentTestLogs, Collections.emptyList()));
        return vo;
    }

    private List<IntegrationSyncLogVO> loadRecentFailedLogsByConfig(Long configId, int limit) {
        List<IntegrationSyncLog> logs = syncLogMapper.selectList(new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(IntegrationSyncLog::getConfigId, configId)
                .in(IntegrationSyncLog::getSyncStatus, TASK_STATUS_FAILED, TASK_STATUS_NO_DATA, TASK_STATUS_MAPPING_MISSING)
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .last("LIMIT " + limit));
        return loadSyncLogVos(logs);
    }

    private List<IntegrationHealthCheckLog> loadRecentHealthCheckLogsByConfig(Long configId, int limit) {
        if (configId == null || limit <= 0) {
            return Collections.emptyList();
        }
        if (!isHealthCheckLogTableAvailable()) {
            return Collections.emptyList();
        }
        return healthCheckLogMapper.selectList(new LambdaQueryWrapper<IntegrationHealthCheckLog>()
                .eq(IntegrationHealthCheckLog::getConfigId, configId)
                .orderByDesc(IntegrationHealthCheckLog::getCreatedAt)
                .orderByDesc(IntegrationHealthCheckLog::getId)
                .last("LIMIT " + limit));
    }

    private List<IntegrationHealthCheckLogVO> toHealthCheckLogVos(List<IntegrationHealthCheckLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream().map(log -> {
            IntegrationHealthCheckLogVO vo = new IntegrationHealthCheckLogVO();
            vo.setId(log.getId());
            vo.setTestStatus(log.getTestStatus());
            vo.setAuthSuccess(toNullableBoolean(log.getAuthSuccess()));
            vo.setReachable(toNullableBoolean(log.getReachable()));
            vo.setCallbackReachable(toNullableBoolean(log.getCallbackReachable()));
            vo.setAuthMessage(log.getAuthMessage());
            vo.setReachabilityMessage(log.getReachabilityMessage());
            vo.setCallbackMessage(log.getCallbackMessage());
            vo.setTestMessage(log.getTestMessage());
            vo.setErrorCode(log.getErrorCode());
            vo.setErrorMessage(log.getErrorMessage());
            vo.setRequestPayload(log.getRequestPayload());
            vo.setRequestHeaders(log.getRequestHeaders());
            vo.setRequestBody(log.getRequestBody());
            vo.setResponsePayload(log.getResponsePayload());
            vo.setOperatorId(log.getOperatorId());
            vo.setOperatorName(log.getOperatorName());
            vo.setCreatedAt(log.getCreatedAt());
            return vo;
        }).toList();
    }

    private void recordHealthCheckLog(IntegrationModuleConfig config, IntegrationProviderTemplate provider, HealthCheckResult result) {
        if (!isHealthCheckLogTableAvailable()) {
            log.warn("skip record health check log because table sys_integration_health_check_log is unavailable, configId={}", config == null ? null : config.getId());
            return;
        }
        IntegrationHealthCheckLog logEntity = new IntegrationHealthCheckLog();
        logEntity.setConfigId(config.getId());
        logEntity.setConfigNameSnapshot(config.getConfigName());
        logEntity.setBizModule(config.getBizModule());
        logEntity.setBizScene(config.getBizScene());
        logEntity.setProviderCode(config.getProviderCode());
        logEntity.setProviderNameSnapshot(provider == null ? config.getProviderCode() : firstNonBlank(provider.getProviderName(), config.getProviderCode()));
        logEntity.setTestStatus(result.status());
        logEntity.setAuthSuccess(toNullableInteger(result.authSuccess()));
        logEntity.setReachable(toNullableInteger(result.reachable()));
        logEntity.setCallbackReachable(toNullableInteger(result.callbackReachable()));
        logEntity.setAuthMessage(result.authMessage());
        logEntity.setReachabilityMessage(result.reachabilityMessage());
        logEntity.setCallbackMessage(result.callbackMessage());
        logEntity.setTestMessage(result.message());
        logEntity.setErrorCode(result.errorCode());
        logEntity.setErrorMessage(result.errorMessage());
        logEntity.setRequestPayload(result.requestSnapshot() == null ? null : result.requestSnapshot().requestPayload());
        logEntity.setRequestHeaders(result.requestSnapshot() == null ? null : result.requestSnapshot().requestHeaders());
        logEntity.setRequestBody(result.requestSnapshot() == null ? null : result.requestSnapshot().requestBody());
        logEntity.setResponsePayload(result.responsePayload());
        logEntity.setOperatorId(firstNonNull(UserContext.getUserId(), 0L));
        logEntity.setOperatorName(firstNonBlank(UserContext.getRealName(), UserContext.getUsername(), "system"));
        logEntity.setOrgId(config.getOrgId());
        logEntity.setOrgNameSnapshot(resolveOrgNameSnapshot(config.getOrgId()));
        logEntity.setTenantId(config.getTenantId());
        healthCheckLogMapper.insert(logEntity);
    }

    private boolean isHealthCheckLogTableAvailable() {
        long now = System.currentTimeMillis();
        Boolean cached = healthCheckLogTableAvailableCache;
        if (cached != null && now - healthCheckLogTableAvailableCheckedAt < TimeUnit.MINUTES.toMillis(1)) {
            return cached;
        }
        synchronized (this) {
            long refreshedNow = System.currentTimeMillis();
            Boolean refreshedCached = healthCheckLogTableAvailableCache;
            if (refreshedCached != null && refreshedNow - healthCheckLogTableAvailableCheckedAt < TimeUnit.MINUTES.toMillis(1)) {
                return refreshedCached;
            }
            boolean available = queryCount(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_integration_health_check_log'"
            ) > 0;
            healthCheckLogTableAvailableCache = available;
            healthCheckLogTableAvailableCheckedAt = refreshedNow;
            return available;
        }
    }

    private IntegrationBinding buildHealthCheckProbeBinding(IntegrationModuleConfig config) {
        IntegrationBinding binding = new IntegrationBinding();
        binding.setBizId(0L);
        binding.setBizNo("HEALTH-CHECK");
        binding.setExternalNo("TEST-EXTERNAL");
        binding.setBizModule(config.getBizModule());
        binding.setBizScene(config.getBizScene());
        return binding;
    }

    private CallbackReachabilityResult testCallbackReachability(IntegrationModuleConfig config, IntegrationProviderTemplate provider) {
        boolean enabled = config.getCallbackEnabled() != null && config.getCallbackEnabled() == 1;
        boolean providerSupported = provider != null && provider.getCallbackSupported() != null && provider.getCallbackSupported() == 1;
        if (!enabled || !providerSupported) {
            return new CallbackReachabilityResult(false, null, "当前配置未启用回调或平台模板未声明回调能力");
        }
        if (StrUtil.isBlank(config.getCallbackUrl())) {
            return new CallbackReachabilityResult(true, false, "回调地址为空，无法验证回调连通性");
        }
        try {
            URI callbackUri = URI.create(config.getCallbackUrl().trim());
            String scheme = StrUtil.blankToDefault(callbackUri.getScheme(), "").toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return new CallbackReachabilityResult(true, false, "回调地址必须是 http/https 地址");
            }
            String expectedPath = "/v1/integration/callback/" + provider.getProviderCode();
            String actualPath = StrUtil.blankToDefault(callbackUri.getPath(), "");
            if (!Objects.equals(actualPath, expectedPath) && !actualPath.endsWith(expectedPath)) {
                return new CallbackReachabilityResult(true, false, "回调地址路径与系统回调入口不一致，请检查配置");
            }
            String host = callbackUri.getHost();
            if (StrUtil.isBlank(host)) {
                return new CallbackReachabilityResult(true, false, "回调地址缺少可访问主机名");
            }
            int port = callbackUri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }
            int timeout = (int) Math.max(Math.min(firstNonNull(config.getTimeoutMs(), 3000L), 10_000L), 1000L);
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), timeout);
            }
            return new CallbackReachabilityResult(true, true, "回调地址主机与端口可连通");
        } catch (Exception ex) {
            return new CallbackReachabilityResult(true, false, "回调地址连通性校验失败：" + normalizeRestClientMessage(ex));
        }
    }

    private String buildHealthCheckSummary(String status, String authMessage, String reachabilityMessage,
                                           String callbackMessage, String errorMessage) {
        if (TASK_STATUS_SUCCESS.equals(status)) {
            return firstNonBlank(reachabilityMessage, authMessage, callbackMessage, "连接测试成功");
        }
        if (HEALTH_CHECK_STATUS_WARNING.equals(status)) {
            return firstNonBlank(errorMessage, reachabilityMessage, authMessage, callbackMessage, "连接测试存在待确认项");
        }
        return firstNonBlank(errorMessage, reachabilityMessage, authMessage, callbackMessage, "连接测试失败");
    }

    private JsonNode parseStrictHealthResponse(String payload) {
        if (StrUtil.isBlank(payload)) {
            throw BizException.validationFailed("第三方返回空响应，无法判定连接结果");
        }
        try {
            return objectMapper.readTree(payload.trim());
        } catch (Exception ex) {
            String trimmed = payload.trim();
            if (trimmed.startsWith("<")) {
                throw BizException.validationFailed("第三方返回了 HTML/文本页，不是可解析的 JSON 结果");
            }
            throw BizException.validationFailed("第三方返回内容不是有效 JSON，无法作为连接测试成功依据");
        }
    }

    private HealthResponseInspection inspectHealthResponse(JsonNode responseNode) {
        if (responseNode == null || responseNode.isNull() || responseNode.isMissingNode()) {
            return new HealthResponseInspection(false, HEALTH_CHECK_ERROR_INVALID_RESPONSE, "第三方返回空 JSON，无法确认连接测试结果");
        }
        boolean explicitFailure = responseNode.has("success") && responseNode.get("success").isBoolean() && !responseNode.get("success").asBoolean();
        String code = firstNonBlank(
                findString(responseNode, "code"),
                findString(responseNode, "status"),
                findString(responseNode, "resultCode"),
                findString(responseNode, "errorCode"),
                findString(responseNode, "errCode")
        );
        String message = firstNonBlank(
                findString(responseNode, "message"),
                findString(responseNode, "msg"),
                findString(responseNode, "errorMessage"),
                findString(responseNode, "error"),
                findString(responseNode, "detail")
        );
        if (explicitFailure || looksLikeFailedHealthResponse(code, message)) {
            return new HealthResponseInspection(true, HEALTH_CHECK_ERROR_INVALID_RESPONSE,
                    "第三方接口已响应，但返回了业务失败结果：" + firstNonBlank(message, code, "请检查第三方返回"));
        }
        return new HealthResponseInspection(false, null, "第三方接口请求成功，返回报文格式校验通过");
    }

    private boolean looksLikeFailedHealthResponse(String code, String message) {
        if (StrUtil.isNotBlank(code)) {
            String normalizedCode = code.trim().toLowerCase(Locale.ROOT);
            if (normalizedCode.matches("\\d+")) {
                try {
                    int numericCode = Integer.parseInt(normalizedCode);
                    if (numericCode >= 400) {
                        return true;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            if (!Set.of("0", "200", "ok", "success", "succeeded", "true").contains(normalizedCode)
                    && (normalizedCode.contains("error")
                    || normalizedCode.contains("fail")
                    || normalizedCode.contains("invalid")
                    || normalizedCode.contains("forbidden")
                    || normalizedCode.contains("expired")
                    || normalizedCode.contains("denied"))) {
                return true;
            }
        }
        if (StrUtil.isNotBlank(message)) {
            String normalizedMessage = message.trim().toLowerCase(Locale.ROOT);
            return normalizedMessage.contains("error")
                    || normalizedMessage.contains("fail")
                    || normalizedMessage.contains("invalid")
                    || normalizedMessage.contains("denied")
                    || normalizedMessage.contains("forbidden")
                    || normalizedMessage.contains("expired")
                    || normalizedMessage.contains("not found")
                    || normalizedMessage.contains("missing")
                    || normalizedMessage.contains("错误")
                    || normalizedMessage.contains("失败")
                    || normalizedMessage.contains("无效")
                    || normalizedMessage.contains("拒绝")
                    || normalizedMessage.contains("不存在")
                    || normalizedMessage.contains("缺失");
        }
        return false;
    }

    private boolean isLikelyTestPlaceholderFailure(ThirdPartyRequestException ex, String responsePayload) {
        Integer httpStatus = ex.httpStatus();
        if (httpStatus == null || (httpStatus != 400 && httpStatus != 404 && httpStatus != 422)) {
            return false;
        }
        String text = (firstNonBlank(ex.getMessage(), "") + " " + firstNonBlank(responsePayload, "")).toLowerCase(Locale.ROOT);
        return text.contains("test-external")
                || text.contains("health-check")
                || text.contains("not found")
                || text.contains("不存在")
                || text.contains("无效")
                || text.contains("参数")
                || text.contains("missing")
                || text.contains("externalno");
    }

    private Boolean toNullableBoolean(Integer value) {
        return value == null ? null : value == 1;
    }

    private Integer toNullableInteger(Boolean value) {
        return value == null ? null : value ? 1 : 0;
    }

    private boolean matchesHealthCheckQuery(IntegrationModuleConfig config, IntegrationHealthCheckQueryDTO query) {
        if (config == null || query == null) {
            return false;
        }
        if (query.getOrgId() != null && !Objects.equals(query.getOrgId(), config.getOrgId())) {
            return false;
        }
        if (StrUtil.isNotBlank(query.getProviderCode()) && !StrUtil.equals(query.getProviderCode(), config.getProviderCode())) {
            return false;
        }
        if (StrUtil.isNotBlank(query.getBizModule()) && !StrUtil.equals(query.getBizModule(), config.getBizModule())) {
            return false;
        }
        if (StrUtil.isNotBlank(query.getBizScene()) && !StrUtil.equals(query.getBizScene(), config.getBizScene())) {
            return false;
        }
        if (StrUtil.isNotBlank(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            return StrUtil.containsIgnoreCase(firstNonBlank(config.getConfigName(), ""), keyword)
                    || StrUtil.containsIgnoreCase(firstNonBlank(config.getProviderCode(), ""), keyword)
                    || StrUtil.containsIgnoreCase(firstNonBlank(config.getBizScene(), ""), keyword);
        }
        return true;
    }

    private List<IntegrationSyncLogVO> loadRecentSyncLogs(String status, Long orgId, DataScopeService.DataScopeResult scope, int limit) {
        LambdaQueryWrapper<IntegrationSyncLog> wrapper = new LambdaQueryWrapper<IntegrationSyncLog>()
                .eq(IntegrationSyncLog::getSyncStatus, status)
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .last("LIMIT " + limit);
        applyOrgScope(wrapper, orgId, scope);
        return loadSyncLogVos(syncLogMapper.selectList(wrapper));
    }

    private List<IntegrationSyncLogVO> loadRecentTimeoutLogs(Long orgId, DataScopeService.DataScopeResult scope, int limit) {
        LambdaQueryWrapper<IntegrationSyncLog> wrapper = new LambdaQueryWrapper<IntegrationSyncLog>()
                .like(IntegrationSyncLog::getErrorMessage, "超时")
                .orderByDesc(IntegrationSyncLog::getCreatedAt)
                .last("LIMIT " + limit);
        applyOrgScope(wrapper, orgId, scope);
        return loadSyncLogVos(syncLogMapper.selectList(wrapper));
    }

    private List<IntegrationCallbackLogVO> loadRecentSignFailedLogs(Long orgId, DataScopeService.DataScopeResult scope, int limit) {
        LambdaQueryWrapper<IntegrationCallbackLog> wrapper = new LambdaQueryWrapper<IntegrationCallbackLog>()
                .eq(IntegrationCallbackLog::getSignResult, "fail")
                .orderByDesc(IntegrationCallbackLog::getCreatedAt)
                .last("LIMIT " + limit);
        applyOrgScope(wrapper, orgId, scope);
        List<IntegrationCallbackLogVO> records = loadCallbackLogVos(callbackLogMapper.selectList(wrapper));
        if (records.isEmpty()) {
            return records;
        }
        Map<String, String> providerNameMap = loadProviderNameMap(records.stream()
                .map(IntegrationCallbackLogVO::getProviderCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet()));
        records.forEach(item -> item.setProviderName(firstNonBlank(providerNameMap.get(item.getProviderCode()), item.getProviderCode())));
        return records;
    }

    private List<IntegrationSyncLogVO> loadSyncLogVos(List<IntegrationSyncLog> logs) {
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(logs.stream().map(IntegrationSyncLog::getConfigId).collect(Collectors.toSet()));
        Map<Long, String> orgNameMap = loadOrgNames(logs.stream().map(IntegrationSyncLog::getOrgId).collect(Collectors.toSet()));
        Map<String, String> providerNameMap = loadProviderNameMap(logs.stream().map(IntegrationSyncLog::getProviderCode).collect(Collectors.toSet()));
        Map<Long, IntegrationSyncTask> taskMap = loadTaskMap(logs.stream().map(IntegrationSyncLog::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet()));
        return logs.stream().map(item -> {
            IntegrationSyncLogVO vo = new IntegrationSyncLogVO();
            BeanUtils.copyProperties(item, vo);
            IntegrationModuleConfig config = configMap.get(item.getConfigId());
            IntegrationSyncTask task = item.getTaskId() == null ? null : taskMap.get(item.getTaskId());
            vo.setTaskNo(task == null ? null : task.getTaskNo());
            vo.setConfigName(firstNonBlank(item.getConfigNameSnapshot(), config == null ? null : config.getConfigName()));
            vo.setProviderName(firstNonBlank(item.getProviderNameSnapshot(), providerNameMap.get(item.getProviderCode()), item.getProviderCode()));
            vo.setOrgName(firstNonBlank(item.getOrgNameSnapshot(), orgNameMap.get(item.getOrgId())));
            vo.setTaskType(firstNonBlank(item.getTaskType(), task == null ? null : task.getTaskType()));
            vo.setResultMessage(firstNonBlank(item.getResultMessage(), task == null ? null : task.getResultMessage(), item.getErrorMessage()));
            return vo;
        }).toList();
    }

    private List<IntegrationCallbackLogVO> loadCallbackLogVos(List<IntegrationCallbackLog> logs) {
        if (logs.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(logs.stream().map(IntegrationCallbackLog::getConfigId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, String> orgNameMap = loadOrgNames(logs.stream().map(IntegrationCallbackLog::getOrgId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<String, String> providerNameMap = loadProviderNameMap(logs.stream().map(IntegrationCallbackLog::getProviderCode).filter(StrUtil::isNotBlank).collect(Collectors.toSet()));
        Map<Long, IntegrationSyncTask> taskMap = loadTaskMap(logs.stream()
                .map(IntegrationCallbackLog::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return logs.stream().map(item -> {
            IntegrationCallbackLogVO vo = new IntegrationCallbackLogVO();
            BeanUtils.copyProperties(item, vo);
            IntegrationModuleConfig config = item.getConfigId() == null ? null : configMap.get(item.getConfigId());
            IntegrationSyncTask task = item.getTaskId() == null ? null : taskMap.get(item.getTaskId());
            vo.setConfigName(firstNonBlank(item.getConfigNameSnapshot(), config == null ? null : config.getConfigName()));
            vo.setProviderName(firstNonBlank(item.getProviderNameSnapshot(), providerNameMap.get(item.getProviderCode()), item.getProviderCode()));
            vo.setOrgName(firstNonBlank(item.getOrgNameSnapshot(), orgNameMap.get(item.getOrgId())));
            vo.setTaskNo(task == null ? null : task.getTaskNo());
            return vo;
        }).toList();
    }

    private List<IntegrationFileRecordVO> loadFileRecordVos(List<IntegrationFileRecord> records) {
        if (records.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, IntegrationModuleConfig> configMap = loadConfigMap(records.stream()
                .map(IntegrationFileRecord::getConfigId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<String, String> providerNameMap = loadProviderNameMap(records.stream()
                .map(IntegrationFileRecord::getProviderCode)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet()));
        Map<Long, String> orgNameMap = loadOrgNames(records.stream()
                .map(IntegrationFileRecord::getOrgId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, IntegrationSyncTask> taskMap = loadTaskMap(records.stream()
                .map(IntegrationFileRecord::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        Map<Long, IntegrationSyncLog> syncLogMap = loadSyncLogMap(records.stream()
                .map(IntegrationFileRecord::getSyncLogId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        return records.stream().map(item -> {
            IntegrationFileRecordVO vo = new IntegrationFileRecordVO();
            BeanUtils.copyProperties(item, vo);
            IntegrationModuleConfig config = item.getConfigId() == null ? null : configMap.get(item.getConfigId());
            IntegrationSyncTask task = item.getTaskId() == null ? null : taskMap.get(item.getTaskId());
            IntegrationSyncLog syncLog = item.getSyncLogId() == null ? null : syncLogMap.get(item.getSyncLogId());
            vo.setConfigName(firstNonBlank(item.getConfigNameSnapshot(), config == null ? null : config.getConfigName()));
            vo.setProviderName(firstNonBlank(item.getProviderNameSnapshot(), providerNameMap.get(item.getProviderCode()), item.getProviderCode()));
            vo.setOrgName(firstNonBlank(item.getOrgNameSnapshot(), orgNameMap.get(item.getOrgId())));
            vo.setTaskNo(task == null ? null : task.getTaskNo());
            vo.setSyncLogCreatedAt(syncLog == null ? null : syncLog.getCreatedAt());
            vo.setSyncLogStatus(syncLog == null ? null : syncLog.getSyncStatus());
            vo.setSyncLogResultMessage(syncLog == null ? null : syncLog.getResultMessage());
            vo.setSyncLogErrorMessage(syncLog == null ? null : syncLog.getErrorMessage());
            return vo;
        }).toList();
    }

    private IntegrationProviderTemplateVO toProviderVO(IntegrationProviderTemplate entity) {
        IntegrationProviderTemplateVO vo = new IntegrationProviderTemplateVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setBuiltinFlag(isBuiltinProvider(entity.getProviderCode()) ? 1 : 0);
        vo.setSceneCodeList(parseStringList(entity.getSceneCodes()));
        return vo;
    }

    private IntegrationModuleConfigVO toModuleConfigVO(IntegrationModuleConfig entity, Map<Long, String> orgNameMap,
                                                       Map<String, String> providerNameMap, boolean includeSecrets) {
        IntegrationModuleConfigVO vo = new IntegrationModuleConfigVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setOrgName(orgNameMap.get(entity.getOrgId()));
        vo.setProviderName(providerNameMap.get(entity.getProviderCode()));
        if (includeSecrets) {
            List<IntegrationSecretMaskedVO> secrets = secretConfigMapper.selectList(new LambdaQueryWrapper<IntegrationSecretConfig>()
                            .eq(IntegrationSecretConfig::getConfigId, entity.getId())
                            .orderByAsc(IntegrationSecretConfig::getId))
                    .stream()
                    .map(item -> {
                        IntegrationSecretMaskedVO masked = new IntegrationSecretMaskedVO();
                        masked.setSecretKey(item.getSecretKey());
                        masked.setSecretMask(item.getSecretMask());
                        masked.setEncryptedFlag(item.getEncryptedFlag());
                        return masked;
                    }).toList();
            vo.setSecrets(secrets);
        }
        return vo;
    }

    private IntegrationFieldMappingVO toFieldMappingVO(IntegrationFieldMapping entity, Map<Long, IntegrationModuleConfig> configMap) {
        IntegrationFieldMappingVO vo = new IntegrationFieldMappingVO();
        BeanUtils.copyProperties(entity, vo);
        IntegrationModuleConfig config = configMap.get(entity.getConfigId());
        if (config != null) {
            vo.setConfigName(config.getConfigName());
            vo.setProviderCode(config.getProviderCode());
        }
        vo.setSortNo(resolveEffectiveFieldSortNo(config, entity));
        return vo;
    }

    private IntegrationStatusMappingVO toStatusMappingVO(IntegrationStatusMapping entity, Map<Long, IntegrationModuleConfig> configMap) {
        IntegrationStatusMappingVO vo = new IntegrationStatusMappingVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setStatus(entity.getEnabled());
        IntegrationModuleConfig config = configMap.get(entity.getConfigId());
        if (config != null) {
            vo.setConfigName(config.getConfigName());
            vo.setProviderCode(config.getProviderCode());
        }
        vo.setSortNo(resolveEffectiveStatusSortNo(config, entity));
        return vo;
    }

    private IntegrationSyncTaskVO toSyncTaskVO(IntegrationSyncTask entity, Map<Long, IntegrationModuleConfig> configMap,
                                               Map<Long, String> orgNameMap, Map<String, String> providerNameMap,
                                               Map<Long, Long> latestTaskIdByBinding) {
        IntegrationSyncTaskVO vo = new IntegrationSyncTaskVO();
        BeanUtils.copyProperties(entity, vo);
        IntegrationModuleConfig config = configMap.get(entity.getConfigId());
        vo.setConfigName(config == null ? null : config.getConfigName());
        vo.setProviderCode(config == null ? null : config.getProviderCode());
        vo.setProviderName(config == null ? null : providerNameMap.get(config.getProviderCode()));
        vo.setOrgName(orgNameMap.get(entity.getOrgId()));
        vo.setRetryMaxCount(config == null ? 0 : resolveManualRetryMaxCount(config));
        String retryDisabledReason = resolveRetryDisabledReason(entity, config, latestTaskIdByBinding == null ? Collections.emptyMap() : latestTaskIdByBinding);
        vo.setRetryDisabledReason(retryDisabledReason);
        vo.setRetryAvailable(StrUtil.isBlank(retryDisabledReason));
        return vo;
    }

    private Map<String, String> loadProviderNameMap(Set<String> providerCodes) {
        if (providerCodes == null || providerCodes.isEmpty()) {
            return Collections.emptyMap();
        }
        return providerTemplateMapper.selectList(new LambdaQueryWrapper<IntegrationProviderTemplate>()
                        .eq(currentTenantId() != null, IntegrationProviderTemplate::getTenantId, currentTenantId())
                        .in(IntegrationProviderTemplate::getProviderCode, providerCodes))
                .stream()
                .collect(Collectors.toMap(IntegrationProviderTemplate::getProviderCode, IntegrationProviderTemplate::getProviderName, (a, b) -> a));
    }

    private Map<Long, String> loadOrgNames(Set<Long> orgIds) {
        if (orgIds == null || orgIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = orgIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, org_name FROM sys_organization WHERE deleted = 0 AND id IN (" + placeholders + ")",
                orgIds.toArray()
        );
        Map<Long, String> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            map.put(asLong(row.get("id")), asString(row.get("org_name")));
        }
        return map;
    }

    private Map<Long, IntegrationModuleConfig> loadConfigMap(Set<Long> configIds) {
        if (configIds == null || configIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return moduleConfigMapper.selectList(new LambdaQueryWrapper<IntegrationModuleConfig>()
                        .in(IntegrationModuleConfig::getId, configIds))
                .stream()
                .collect(Collectors.toMap(IntegrationModuleConfig::getId, item -> item));
    }

    private Map<Long, IntegrationSyncTask> loadTaskMap(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return syncTaskMapper.selectList(new LambdaQueryWrapper<IntegrationSyncTask>()
                        .in(IntegrationSyncTask::getId, taskIds))
                .stream()
                .collect(Collectors.toMap(IntegrationSyncTask::getId, item -> item));
    }

    private Map<Long, IntegrationSyncLog> loadSyncLogMap(Set<Long> syncLogIds) {
        if (syncLogIds == null || syncLogIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return syncLogMapper.selectList(new LambdaQueryWrapper<IntegrationSyncLog>()
                        .in(IntegrationSyncLog::getId, syncLogIds))
                .stream()
                .collect(Collectors.toMap(IntegrationSyncLog::getId, item -> item));
    }

    private IntegrationProviderTemplate getProviderEntity(Long id) {
        IntegrationProviderTemplate entity = providerTemplateMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("第三方平台模板不存在");
        }
        validateTenantReadable(entity.getTenantId());
        return entity;
    }

    private IntegrationProviderTemplate getProviderByCode(String providerCode) {
        return getProviderByCode(providerCode, false);
    }

    private IntegrationProviderTemplate findProviderByCodeOptional(String providerCode) {
        if (StrUtil.isBlank(providerCode)) {
            return null;
        }
        IntegrationProviderTemplate entity = providerTemplateMapper.selectOne(new LambdaQueryWrapper<IntegrationProviderTemplate>()
                .eq(IntegrationProviderTemplate::getProviderCode, providerCode)
                .eq(currentTenantId() != null, IntegrationProviderTemplate::getTenantId, currentTenantId())
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        validateTenantReadable(entity.getTenantId());
        return entity;
    }

    private IntegrationProviderTemplate getProviderByCode(String providerCode, boolean requireActive) {
        IntegrationProviderTemplate entity = findProviderByCodeOptional(providerCode);
        if (entity == null) {
            throw BizException.notFound("第三方平台模板不存在");
        }
        if (requireActive && !"active".equals(entity.getStatus())) {
            throw BizException.conflict(PROVIDER_RUNTIME_DISABLED_ERROR);
        }
        return entity;
    }

    private IntegrationModuleConfig getModuleConfigEntity(Long id, boolean validateScope) {
        IntegrationModuleConfig entity = moduleConfigMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("模块接入配置不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private IntegrationModuleConfig getCallbackModuleConfigEntity(IntegrationBinding binding) {
        IntegrationModuleConfig entity = moduleConfigMapper.selectById(binding.getConfigId());
        if (entity == null) {
            throw BizException.notFound("模块接入配置不存在");
        }
        if (!Objects.equals(entity.getTenantId(), binding.getTenantId())) {
            throw BizException.forbidden("第三方回调绑定的租户信息不匹配");
        }
        if (!Objects.equals(entity.getOrgId(), binding.getOrgId())) {
            throw BizException.forbidden("第三方回调绑定的组织信息不匹配");
        }
        return entity;
    }

    private void validateRequestedMaintenanceMode(IntegrationModuleConfig config, String requestedMode) {
        String normalized = StrUtil.trimToNull(requestedMode);
        if (normalized == null) {
            return;
        }
        if (!MODE_MANUAL.equals(normalized) && !MODE_THIRD_PARTY.equals(normalized)) {
            throw BizException.validationFailed("维护模式仅支持 manual 或 third_party");
        }
        if (MODE_MANUAL.equals(normalized) && config.getForceThirdParty() != null && config.getForceThirdParty() == 1) {
            throw BizException.conflict("当前配置已强制第三方维护，不允许切换为手工模式");
        }
    }

    private void ensureBindingSwitchAllowed(IntegrationBinding currentBinding, IntegrationModuleConfig targetConfig,
                                            String maintenanceMode, Long tenantId, Long orgId) {
        if (currentBinding == null) {
            if (MODE_MANUAL.equals(maintenanceMode) && targetConfig.getForceThirdParty() != null && targetConfig.getForceThirdParty() == 1) {
                throw BizException.conflict("当前配置已强制第三方维护，不允许切换为手工模式");
            }
            return;
        }
        IntegrationModuleConfig currentConfig = getInternalModuleConfigEntity(currentBinding.getConfigId(), tenantId, orgId);
        if (MODE_MANUAL.equals(maintenanceMode)) {
            if (currentConfig.getForceThirdParty() != null && currentConfig.getForceThirdParty() == 1) {
                throw BizException.conflict("当前配置已强制第三方维护，不允许切换为手工模式");
            }
            if (Optional.ofNullable(currentConfig.getAllowManualFallback()).orElse(0) == 1) {
                return;
            }
            if (Optional.ofNullable(currentConfig.getAllowDocumentSwitch()).orElse(1) != 1) {
                throw BizException.conflict("当前配置未开启手工兜底，也不允许切换维护方式");
            }
            return;
        }
        boolean sameConfig = Objects.equals(currentBinding.getConfigId(), targetConfig.getId());
        boolean sameMode = MODE_THIRD_PARTY.equals(currentBinding.getMaintenanceMode());
        if (!sameConfig || !sameMode) {
            if (Optional.ofNullable(currentConfig.getAllowDocumentSwitch()).orElse(1) != 1) {
                throw BizException.conflict("当前配置不允许单据级切换同步方案");
            }
        }
    }

    private void ensureModuleConfigIdentityChangeAllowed(IntegrationModuleConfig before, IntegrationModuleConfigSaveDTO dto) {
        if (before == null || dto == null || !hasBindingsForConfig(before.getId())) {
            return;
        }
        boolean changed = !Objects.equals(before.getOrgId(), dto.getOrgId())
                || !Objects.equals(before.getBizModule(), dto.getBizModule())
                || !Objects.equals(before.getBizScene(), dto.getBizScene())
                || !Objects.equals(before.getProviderCode(), dto.getProviderCode());
        if (changed) {
            throw BizException.validationFailed("该模块接入配置已被业务单据引用，不能修改所属组织、业务模块、业务场景或第三方平台");
        }
    }

    private boolean hasBindingsForConfig(Long configId) {
        if (configId == null) {
            return false;
        }
        Long count = bindingMapper.selectCount(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getConfigId, configId));
        return count != null && count > 0;
    }

    private IntegrationFieldMapping getFieldMappingEntity(Long id) {
        IntegrationFieldMapping entity = fieldMappingMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("字段映射不存在");
        }
        getModuleConfigEntity(entity.getConfigId(), true);
        return entity;
    }

    private IntegrationStatusMapping getStatusMappingEntity(Long id) {
        IntegrationStatusMapping entity = statusMappingMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("状态映射不存在");
        }
        getModuleConfigEntity(entity.getConfigId(), true);
        return entity;
    }

    private boolean isBuiltinProvider(String providerCode) {
        return BUILTIN_PROVIDER_CODES.contains(providerCode);
    }

    private void ensureProviderCodeChangeAllowed(IntegrationProviderTemplate entity, IntegrationProviderTemplateSaveDTO dto) {
        if (entity == null || dto == null || Objects.equals(entity.getProviderCode(), dto.getProviderCode())) {
            return;
        }
        Long moduleRefCount = moduleConfigMapper.selectCount(new LambdaQueryWrapper<IntegrationModuleConfig>()
                .eq(IntegrationModuleConfig::getProviderCode, entity.getProviderCode())
                .eq(entity.getTenantId() != null, IntegrationModuleConfig::getTenantId, entity.getTenantId()));
        Long bindingRefCount = bindingMapper.selectCount(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getProviderCode, entity.getProviderCode())
                .eq(entity.getTenantId() != null, IntegrationBinding::getTenantId, entity.getTenantId()));
        if ((moduleRefCount != null && moduleRefCount > 0) || (bindingRefCount != null && bindingRefCount > 0)) {
            String message = "该平台模板已被模块配置或单据绑定引用，不能修改平台编码";
            auditLogService.log(AuditModule.SYS_INTEGRATION, AuditOperationType.UPDATE, entity.getId(), entity.getProviderCode(),
                    "编辑第三方平台模板失败：" + entity.getProviderName(), safeJson(entity), safeJson(dto), "failed", message);
            throw BizException.validationFailed(message);
        }
    }

    private void ensureBuiltinProviderChangeAllowed(IntegrationProviderTemplate entity, AuditOperationType operationType,
                                                    Object requestPayload, String action, String message) {
        if (entity == null || !isBuiltinProvider(entity.getProviderCode())) {
            return;
        }
        auditLogService.log(AuditModule.SYS_INTEGRATION, operationType, entity.getId(), entity.getProviderCode(),
                action, safeJson(entity), safeJson(requestPayload), "failed", message);
        throw BizException.validationFailed(message);
    }

    private void stopBindingsByProvider(String providerCode, Long tenantId, String reason) {
        List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getProviderCode, providerCode)
                .eq(tenantId != null, IntegrationBinding::getTenantId, tenantId)
                .isNotNull(IntegrationBinding::getNextSyncAt));
        for (IntegrationBinding binding : bindings) {
            clearBindingNextSyncAt(binding.getId(), reason);
        }
    }

    private void stopBindingsByConfig(Long configId, String reason) {
        List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getConfigId, configId));
        for (IntegrationBinding binding : bindings) {
            bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationBinding>()
                    .eq(IntegrationBinding::getId, binding.getId())
                    .set(IntegrationBinding::getNextSyncAt, null)
                    .set(StrUtil.isNotBlank(reason), IntegrationBinding::getRemark, reason));
        }
    }

    private void clearBindingNextSyncAt(Long bindingId, String remark) {
        bindingMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getId, bindingId)
                .set(IntegrationBinding::getNextSyncAt, null)
                .set(StrUtil.isNotBlank(remark), IntegrationBinding::getRemark, remark));
    }

    private void refreshBindingsAfterConfigChange(IntegrationModuleConfig config) {
        if (config == null || config.getId() == null) {
            return;
        }
        if (config.getEnabled() == null || config.getEnabled() != 1) {
            stopBindingsByConfig(config.getId(), "模块配置已停用");
            return;
        }
        List<IntegrationBinding> bindings = bindingMapper.selectList(new LambdaQueryWrapper<IntegrationBinding>()
                .eq(IntegrationBinding::getConfigId, config.getId()));
        for (IntegrationBinding binding : bindings) {
            if (config.getForceThirdParty() != null && config.getForceThirdParty() == 1) {
                binding.setMaintenanceMode(MODE_THIRD_PARTY);
                binding.setModeSource("forced_by_config");
                binding.setModeLocked(1);
                binding.setNextSyncAt(determineNextSyncAt(config, binding, null));
                bindingMapper.updateById(binding);
                updateBusinessSourceTypeForBinding(binding);
                deactivateOtherBindings(binding);
                continue;
            }
            binding.setNextSyncAt(determineNextSyncAt(config, binding, null));
            if (!MODE_THIRD_PARTY.equals(binding.getMaintenanceMode())) {
                binding.setNextSyncAt(null);
            }
            bindingMapper.updateById(binding);
        }
    }

    private void updateBusinessSourceTypeForBinding(IntegrationBinding binding) {
        if (binding == null || !BIZ_MODULE_PURCHASE_ORDER.equals(binding.getBizModule()) || binding.getBizId() == null) {
            return;
        }
        if (BIZ_SCENE_LOGISTICS.equals(binding.getBizScene())) {
            jdbcTemplate.update("UPDATE scm_purchase_order SET logistics_source_type='third_party', updated_at=NOW() WHERE id=? AND deleted=0",
                    binding.getBizId());
        } else if (BIZ_SCENE_INSPECTION.equals(binding.getBizScene())) {
            jdbcTemplate.update("UPDATE scm_purchase_order SET inspection_source_type='third_party', updated_at=NOW() WHERE id=? AND deleted=0",
                    binding.getBizId());
        } else if (BIZ_SCENE_TRACEABILITY.equals(binding.getBizScene())) {
            jdbcTemplate.update("UPDATE scm_purchase_order SET trace_source_type='third_party', updated_at=NOW() WHERE id=? AND deleted=0",
                    binding.getBizId());
        }
    }

    private void ensureIntegrationPermission(String permissionCode, String errorMessage) {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden(errorMessage);
        }
        if (!hasPermission(permissionCode)) {
            throw BizException.forbidden(errorMessage);
        }
    }

    private void ensureIntegrationLogViewPermission() {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden("当前用户无第三方同步日志查看权限");
        }
        if (!hasAnyPermission(PERMISSION_INTEGRATION_VIEW, PERMISSION_INTEGRATION_LOG_VIEW)) {
            throw BizException.forbidden("当前用户无第三方同步日志查看权限");
        }
    }

    private void ensureIntegrationCallbackLogViewPermission() {
        if (dataScopeService.isAdminUser()) {
            return;
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw BizException.forbidden("当前用户无第三方回调日志查看权限");
        }
        if (!hasAnyPermission(PERMISSION_INTEGRATION_VIEW, PERMISSION_INTEGRATION_CALLBACK_VIEW)) {
            throw BizException.forbidden("当前用户无第三方回调日志查看权限");
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

    private boolean hasAnyPermission(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        for (String permissionCode : permissionCodes) {
            if (hasPermission(permissionCode)) {
                return true;
            }
        }
        return false;
    }

    private AuditOperationType resolveAuditOperationType(Long id) {
        return id == null ? AuditOperationType.CREATE : AuditOperationType.UPDATE;
    }

    private void ensureFieldMappingUnique(IntegrationModuleConfig config, Long excludeId, String targetField, Integer enabled,
                                          Object requestPayload, AuditOperationType operationType) {
        if (config == null || enabled == null || enabled != 1) {
            return;
        }
        IntegrationFieldMapping conflict = fieldMappingMapper.selectOne(new LambdaQueryWrapper<IntegrationFieldMapping>()
                .eq(IntegrationFieldMapping::getTenantId, config.getTenantId())
                .eq(IntegrationFieldMapping::getConfigId, config.getId())
                .eq(IntegrationFieldMapping::getTargetField, targetField)
                .eq(IntegrationFieldMapping::getEnabled, 1)
                .ne(excludeId != null, IntegrationFieldMapping::getId, excludeId)
                .last("LIMIT 1"));
        if (conflict == null) {
            return;
        }
        String message = "当前接入配置下已存在该系统字段的启用映射，请勿重复配置。";
        auditLogService.log(AuditModule.SYS_INTEGRATION, operationType, excludeId, config.getProviderCode(),
                "字段映射保存校验失败：" + targetField, safeJson(conflict), safeJson(requestPayload), "failed", message);
        throw BizException.validationFailed(message);
    }

    private void ensureStatusMappingUnique(IntegrationModuleConfig config, Long excludeId, String sourceStatusCode,
                                           Object requestPayload, AuditOperationType operationType) {
        if (config == null) {
            return;
        }
        IntegrationStatusMapping conflict = statusMappingMapper.selectOne(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getTenantId, config.getTenantId())
                .eq(IntegrationStatusMapping::getConfigId, config.getId())
                .eq(IntegrationStatusMapping::getSourceStatusCode, sourceStatusCode)
                .ne(excludeId != null, IntegrationStatusMapping::getId, excludeId)
                .last("LIMIT 1"));
        if (conflict == null) {
            return;
        }
        String message = "当前接入配置下已存在该第三方状态编码映射，请勿重复配置。";
        auditLogService.log(AuditModule.SYS_INTEGRATION, operationType, excludeId, config.getProviderCode(),
                "状态映射保存校验失败：" + sourceStatusCode, safeJson(conflict), safeJson(requestPayload), "failed", message);
        throw BizException.validationFailed(message);
    }

    private IntegrationBinding getBindingEntity(Long id, boolean validateScope) {
        IntegrationBinding entity = bindingMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("第三方绑定记录不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private IntegrationSyncTask getTaskEntity(Long id, boolean validateScope) {
        IntegrationSyncTask entity = syncTaskMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("同步任务不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private IntegrationSyncLog getSyncLogEntity(Long id, boolean validateScope) {
        IntegrationSyncLog entity = syncLogMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("同步日志不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private IntegrationCallbackLog getCallbackLogEntity(Long id, boolean validateScope) {
        IntegrationCallbackLog entity = callbackLogMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("回调日志不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private IntegrationFileRecord getFileRecordEntity(Long id, boolean validateScope) {
        IntegrationFileRecord entity = fileRecordMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("附件转存记录不存在");
        }
        validateTenantReadable(entity.getTenantId());
        if (validateScope) {
            validateOrgReadable(entity.getOrgId(), dataScopeService.resolveCurrentUserOrgScope());
        }
        return entity;
    }

    private Map<String, String> loadSecretPlainMap(Long configId) {
        List<IntegrationSecretConfig> secrets = secretConfigMapper.selectList(new LambdaQueryWrapper<IntegrationSecretConfig>()
                .eq(IntegrationSecretConfig::getConfigId, configId));
        Map<String, String> result = new LinkedHashMap<>();
        for (IntegrationSecretConfig secret : secrets) {
            result.put(secret.getSecretKey(), secret.getEncryptedFlag() != null && secret.getEncryptedFlag() == 1
                    ? aiConfigCryptoService.decrypt(secret.getSecretValue())
                    : secret.getSecretValue());
        }
        return result;
    }

    private JsonNode parseStoredTemplate(String template, boolean requestTemplate) {
        String normalized = StrUtil.trimToNull(template);
        if (normalized == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(normalized);
            if (requestTemplate) {
                validateRequestTemplateSemantic(node);
            }
            return node;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.badRequest(requestTemplate ? REQUEST_TEMPLATE_JSON_ERROR : RESPONSE_TEMPLATE_JSON_ERROR);
        }
    }

    private boolean isResponseNodeEmpty(JsonNode responseNode) {
        if (responseNode == null || responseNode.isNull() || responseNode.isMissingNode()) {
            return true;
        }
        if (responseNode.isObject()) {
            return !responseNode.fieldNames().hasNext();
        }
        return responseNode.isArray() && responseNode.isEmpty();
    }

    private boolean isNormalizedDataEmpty(Map<String, Object> normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return true;
        }
        return normalized.entrySet().stream()
                .filter(entry -> !"externalNo".equals(entry.getKey()))
                .allMatch(entry -> entry.getValue() == null || (entry.getValue() instanceof String text && StrUtil.isBlank(text)));
    }

    private String resolveSourceStatus(Map<String, Object> normalized) {
        return firstNonBlank(asString(normalized.get("sourceStatus")), asString(normalized.get("status")), asString(normalized.get("logisticsStatus")));
    }

    private boolean shouldMarkMappingMissing(IntegrationModuleConfig config, String sourceStatus) {
        if (config == null || StrUtil.isBlank(sourceStatus)) {
            return false;
        }
        if (hasBuiltinStatusScene(config.getBizModule(), config.getBizScene())) {
            return true;
        }
        Long mappingCount = statusMappingMapper.selectCount(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getConfigId, config.getId())
                .eq(IntegrationStatusMapping::getEnabled, 1));
        return mappingCount != null && mappingCount > 0;
    }

    private String buildMappingMissingMessage(IntegrationModuleConfig config, String sourceStatus) {
        String normalizedSourceStatus = StrUtil.trim(sourceStatus);
        if (config == null || StrUtil.isBlank(normalizedSourceStatus)) {
            return MAPPING_MISSING_MESSAGE_PREFIX + sourceStatus;
        }
        List<IntegrationStatusMapping> allMappings = statusMappingMapper.selectList(new LambdaQueryWrapper<IntegrationStatusMapping>()
                .eq(IntegrationStatusMapping::getConfigId, config.getId()));
        boolean sameCodeDisabled = allMappings.stream()
                .filter(Objects::nonNull)
                .anyMatch(mapping -> StrUtil.equalsIgnoreCase(StrUtil.trim(mapping.getSourceStatusCode()), normalizedSourceStatus)
                        && (mapping.getEnabled() == null || mapping.getEnabled() != 1));
        if (sameCodeDisabled) {
            return MAPPING_MISSING_MESSAGE_PREFIX + normalizedSourceStatus + "（已存在同编码状态映射，但当前为禁用，请到【状态映射配置】检查启用状态，并到【同步日志中心】筛选“状态映射缺失”人工处理）";
        }
        if (!hasBuiltinStatusScene(config.getBizModule(), config.getBizScene())) {
            return MAPPING_MISSING_MESSAGE_PREFIX + normalizedSourceStatus + "（当前场景更推荐使用字段映射回写业务字段；如历史状态映射仍在使用，请尽快清理，并到【同步日志中心】人工处理）";
        }
        return MAPPING_MISSING_MESSAGE_PREFIX + normalizedSourceStatus + "（请到【状态映射配置】补齐该状态映射，并到【同步日志中心】筛选“状态映射缺失”人工处理）";
    }

    private String resolveNormalizedErrorCode(String processStatus) {
        if (TASK_STATUS_NO_DATA.equals(processStatus)) {
            return NO_DATA_ERROR_CODE;
        }
        if (TASK_STATUS_MAPPING_MISSING.equals(processStatus)) {
            return MAPPING_MISSING_ERROR_CODE;
        }
        return FIELD_MAPPING_ERROR_CODE;
    }

    private String buildProviderCircuitKey(IntegrationProviderTemplate provider, IntegrationModuleConfig config) {
        return String.join(":",
                StrUtil.blankToDefault(String.valueOf(currentTenantId()), "0"),
                provider == null ? "" : StrUtil.blankToDefault(provider.getProviderCode(), ""),
                config == null || config.getId() == null ? "" : String.valueOf(config.getId()));
    }

    private void assertProviderCircuitClosed(String circuitKey) {
        ProviderCircuitState state = providerCircuitStateMap.get(circuitKey);
        if (state == null) {
            return;
        }
        if (state.openUntilEpochMillis() > System.currentTimeMillis()) {
            throw BizException.conflict("第三方平台当前触发熔断保护，请稍后再试");
        }
    }

    private void recordProviderRequestSuccess(String circuitKey) {
        providerCircuitStateMap.remove(circuitKey);
    }

    private void recordProviderRequestFailure(String circuitKey, boolean rateLimited) {
        ProviderCircuitState state = providerCircuitStateMap.getOrDefault(circuitKey, new ProviderCircuitState(0, 0L));
        int failureCount = state.failureCount() + 1;
        long openUntil = failureCount >= CIRCUIT_BREAKER_FAILURE_THRESHOLD
                ? System.currentTimeMillis() + (rateLimited ? RATE_LIMIT_CIRCUIT_OPEN_MILLIS : CIRCUIT_BREAKER_OPEN_MILLIS)
                : 0L;
        providerCircuitStateMap.put(circuitKey, new ProviderCircuitState(failureCount, openUntil));
    }

    private int resolveTotalAttempts(IntegrationModuleConfig config) {
        int retryCount = config == null || config.getRetryMaxCount() == null ? 0 : Math.max(config.getRetryMaxCount(), 0);
        return Math.max(1, Math.min(retryCount + 1, MAX_TOTAL_RETRY_ATTEMPTS));
    }

    private boolean shouldRetryThirdPartyRequest(Throwable throwable, int attempt, int totalAttempts) {
        if (attempt >= totalAttempts) {
            return false;
        }
        if (throwable instanceof ResourceAccessException) {
            return true;
        }
        if (throwable instanceof RestClientResponseException responseException) {
            int statusCode = responseException.getRawStatusCode();
            return statusCode == 429 || statusCode >= 500;
        }
        return throwable instanceof RestClientException;
    }

    private boolean shouldRefreshToken(RestClientResponseException exception, IntegrationModuleConfig config) {
        if (exception == null || config == null) {
            return false;
        }
        int statusCode = exception.getRawStatusCode();
        if (statusCode != 401 && statusCode != 403) {
            return false;
        }
        return StrUtil.isNotBlank(config.getAccessTokenUrl()) || StrUtil.isNotBlank(config.getRefreshTokenUrl());
    }

    private void applyRetryBackoff(int attempt, boolean rateLimited) {
        long delay = rateLimited
                ? RETRY_BASE_DELAY_MILLIS * (1L << Math.max(1, attempt))
                : RETRY_BASE_DELAY_MILLIS * (1L << Math.max(0, attempt - 1));
        try {
            TimeUnit.MILLISECONDS.sleep(Math.min(delay, 4_000L));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String buildThirdPartyRequestError(RestClientResponseException exception) {
        String responseBody = StrUtil.trimToNull(exception.getResponseBodyAsString());
        if (responseBody == null) {
            return "第三方请求失败：" + exception.getMessage();
        }
        return "第三方请求失败：" + exception.getStatusCode() + " " + responseBody;
    }

    private String resolveSyncLogHttpErrorCode(RestClientResponseException exception) {
        if (exception == null) {
            return EXECUTE_ERROR_CODE;
        }
        int statusCode = exception.getRawStatusCode();
        if (statusCode == 401 || statusCode == 403) {
            return SYNC_LOG_ERROR_CODE_AUTH;
        }
        if (statusCode == 429) {
            return SYNC_LOG_ERROR_CODE_RATE_LIMIT;
        }
        if (statusCode >= 500) {
            return SYNC_LOG_ERROR_CODE_HTTP_5XX;
        }
        if (statusCode >= 400) {
            return SYNC_LOG_ERROR_CODE_HTTP_4XX;
        }
        return EXECUTE_ERROR_CODE;
    }

    private String resolveSyncLogTransportErrorCode(ResourceAccessException exception) {
        String message = normalizeRestClientMessage(exception);
        if (StrUtil.containsIgnoreCase(message, "timed out")
                || StrUtil.containsIgnoreCase(message, "timeout")
                || StrUtil.containsIgnoreCase(message, "Read timed out")
                || StrUtil.containsIgnoreCase(message, "connect timed out")) {
            return SYNC_LOG_ERROR_CODE_TIMEOUT;
        }
        return SYNC_LOG_ERROR_CODE_NETWORK;
    }

    private String resolveSyncLogErrorCode(Throwable throwable) {
        if (throwable instanceof ThirdPartyRequestException requestException) {
            return requestException.errorCode();
        }
        if (throwable instanceof RestClientResponseException responseException) {
            return resolveSyncLogHttpErrorCode(responseException);
        }
        if (throwable instanceof ResourceAccessException resourceAccessException) {
            return resolveSyncLogTransportErrorCode(resourceAccessException);
        }
        String message = firstNonBlank(throwable == null ? null : throwable.getMessage(), "");
        if (StrUtil.equals(message, PROVIDER_RUNTIME_DISABLED_ERROR)) {
            return SYNC_LOG_ERROR_CODE_PROVIDER_DISABLED;
        }
        if (StrUtil.equals(message, PROVIDER_RUNTIME_URL_MISSING_ERROR)) {
            return SYNC_LOG_ERROR_CODE_RUNTIME_URL_MISSING;
        }
        if (StrUtil.contains(message, "熔断保护")) {
            return SYNC_LOG_ERROR_CODE_CIRCUIT_OPEN;
        }
        if (StrUtil.containsIgnoreCase(message, "accessToken") || StrUtil.containsIgnoreCase(message, "token")) {
            return SYNC_LOG_ERROR_CODE_TOKEN;
        }
        if (StrUtil.containsIgnoreCase(message, "429")) {
            return SYNC_LOG_ERROR_CODE_RATE_LIMIT;
        }
        if (StrUtil.containsIgnoreCase(message, "超时") || StrUtil.containsIgnoreCase(message, "timeout")) {
            return SYNC_LOG_ERROR_CODE_TIMEOUT;
        }
        return EXECUTE_ERROR_CODE;
    }

    private String normalizeRestClientMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知异常";
        }
        if (throwable instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode() + " " + StrUtil.blankToDefault(responseException.getResponseBodyAsString(), responseException.getMessage());
        }
        return StrUtil.blankToDefault(throwable.getMessage(), throwable.getClass().getSimpleName());
    }

    private void persistTokenSecret(IntegrationModuleConfig config, Map<String, String> secrets, String accessToken, String refreshToken) {
        if (config == null || config.getId() == null || StrUtil.isBlank(accessToken)) {
            return;
        }
        upsertSecretValue(config, resolveExistingSecretKey(secrets, "accessToken", "ACCESS_TOKEN", "token"), accessToken);
        if (StrUtil.isNotBlank(refreshToken)) {
            upsertSecretValue(config, resolveExistingSecretKey(secrets, "refreshToken", "REFRESH_TOKEN", "refresh_token"), refreshToken);
        }
    }

    private String resolveExistingSecretKey(Map<String, String> secrets, String defaultKey, String... candidates) {
        if (secrets != null) {
            if (secrets.containsKey(defaultKey)) {
                return defaultKey;
            }
            for (String candidate : candidates) {
                if (secrets.containsKey(candidate)) {
                    return candidate;
                }
            }
        }
        return defaultKey;
    }

    private void upsertSecretValue(IntegrationModuleConfig config, String secretKey, String secretValue) {
        IntegrationSecretConfig existing = secretConfigMapper.selectOne(new LambdaQueryWrapper<IntegrationSecretConfig>()
                .eq(IntegrationSecretConfig::getConfigId, config.getId())
                .eq(IntegrationSecretConfig::getSecretKey, secretKey)
                .last("LIMIT 1"));
        String storedValue = aiConfigCryptoService.encrypt(secretValue);
        String secretMask = maskSecret(secretValue);
        if (existing == null) {
            IntegrationSecretConfig entity = new IntegrationSecretConfig();
            entity.setConfigId(config.getId());
            entity.setSecretKey(secretKey);
            entity.setSecretValue(storedValue);
            entity.setSecretMask(secretMask);
            entity.setEncryptedFlag(1);
            entity.setOrgId(config.getOrgId());
            entity.setTenantId(config.getTenantId());
            secretConfigMapper.insert(entity);
            return;
        }
        existing.setSecretValue(storedValue);
        existing.setSecretMask(secretMask);
        existing.setEncryptedFlag(1);
        secretConfigMapper.updateById(existing);
    }

    private String maskSecret(String value) {
        if (StrUtil.isBlank(value)) {
            return "******";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "******" + value.substring(value.length() - 2);
    }

    private List<String> parseStringList(String value) {
        if (StrUtil.isBlank(value)) {
            return Collections.emptyList();
        }
        if ("null".equalsIgnoreCase(value.trim())) {
            return Collections.emptyList();
        }
        try {
            if (value.trim().startsWith("[")) {
                return objectMapper.readValue(value, new TypeReference<List<String>>() {});
            }
        } catch (Exception ignored) {
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
    }

    private JsonNode readTree(String json) {
        if (StrUtil.isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.valueToTree(Map.of("raw", json));
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw BizException.of("JSON 序列化失败");
        }
    }

    private String safeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private JsonNode findNode(JsonNode root, String key) {
        if (root == null || root.isMissingNode() || root.isNull() || StrUtil.isBlank(key)) {
            return null;
        }
        if (root.has(key)) {
            return root.get(key);
        }
        if (root.isObject()) {
            var fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode found = findNode(entry.getValue(), key);
                if (found != null && !found.isMissingNode() && !found.isNull()) {
                    return found;
                }
            }
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                JsonNode found = findNode(item, key);
                if (found != null && !found.isMissingNode() && !found.isNull()) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode resolveJsonPath(JsonNode root, String path) {
        if (root == null || StrUtil.isBlank(path)) {
            return null;
        }
        String normalized = path.startsWith("$.") ? path.substring(2) : path.startsWith("$") ? path.substring(1) : path;
        JsonNode current = root;
        for (String part : normalized.split("\\.")) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            if (part.contains("[") && part.endsWith("]")) {
                String field = part.substring(0, part.indexOf('['));
                int index = Integer.parseInt(part.substring(part.indexOf('[') + 1, part.length() - 1));
                current = StrUtil.isBlank(field) ? current : current.get(field);
                if (current == null || !current.isArray() || current.size() <= index) {
                    return null;
                }
                current = current.get(index);
            } else {
                current = current.get(part);
            }
        }
        return current;
    }

    private Object nodeToValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        try {
            return objectMapper.convertValue(node, Object.class);
        } catch (IllegalArgumentException ex) {
            return node.toString();
        }
    }

    private Map<String, String> readStringMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
            Map<String, String> result = new LinkedHashMap<>();
            raw.forEach((key, value) -> result.put(key, value == null ? null : String.valueOf(value)));
            return result;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> readObjectMap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private Object readObject(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return objectMapper.convertValue(node, Object.class);
    }

    private Map<String, String> buildTemplateVariables(IntegrationModuleConfig config, IntegrationBinding binding, Map<String, String> secrets) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("configId", String.valueOf(config.getId()));
        vars.put("providerCode", config.getProviderCode());
        vars.put("bizModule", binding.getBizModule());
        vars.put("bizScene", binding.getBizScene());
        vars.put("bizId", String.valueOf(binding.getBizId()));
        vars.put("bizNo", binding.getBizNo());
        vars.put("externalNo", binding.getExternalNo());
        vars.put("orgId", String.valueOf(binding.getOrgId()));
        vars.put("callbackUrl", firstNonBlank(config.getCallbackUrl(), ""));
        secrets.forEach(vars::put);
        return vars;
    }

    private String interpolate(String source, Map<String, String> vars) {
        if (source == null) {
            return null;
        }
        String result = source;
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", StrUtil.blankToDefault(entry.getValue(), ""));
        }
        return result;
    }

    private Map<String, String> interpolateStringMap(Map<String, String> source, Map<String, String> vars) {
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, interpolate(value, vars)));
        return result;
    }

    private Map<String, Object> interpolateObjectMap(Map<String, Object> source, Map<String, String> vars) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, interpolateObject(value, vars)));
        return result;
    }

    private Object interpolateObject(Object value, Map<String, String> vars) {
        if (value instanceof String text) {
            return interpolate(text, vars);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), interpolateObject(item, vars)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>();
            for (Object item : collection) {
                list.add(interpolateObject(item, vars));
            }
            return list;
        }
        return value;
    }

    private FieldMappingFailureDecision handleFieldMappingFailure(IntegrationFieldMapping mapping, String message, Throwable ex) {
        String fieldName = mapping == null ? "未知字段" : firstNonBlank(mapping.getTargetField(), "未知字段");
        String finalMessage = "字段映射【" + fieldName + "】" + (StrUtil.isBlank(message) ? "处理失败" : "处理失败：" + message);
        String strategy = firstNonBlank(mapping == null ? null : mapping.getErrorStrategy(), "fail");
        if ("skip".equals(strategy)) {
            return new FieldMappingFailureDecision(true, null, null);
        }
        if ("log_only".equals(strategy)) {
            log.warn("字段映射按 log_only 跳过, field={}, message={}", fieldName, finalMessage, ex);
            return new FieldMappingFailureDecision(true, null, null);
        }
        if ("manual_review".equals(strategy)) {
            log.warn("字段映射按 manual_review 标记人工处理, field={}, message={}", fieldName, finalMessage, ex);
            return new FieldMappingFailureDecision(false, TASK_STATUS_FAILED, finalMessage + "，请到同步日志中人工处理");
        }
        throw BizException.validationFailed(finalMessage);
    }

    private Object applyFieldTransform(Object raw, IntegrationFieldMapping mapping, boolean strict) {
        if (raw == null) {
            return null;
        }
        String transformType = firstNonBlank(mapping == null ? null : mapping.getTransformType(), "direct");
        String targetField = mapping == null ? "未知字段" : firstNonBlank(mapping.getTargetField(), "未知字段");
        return switch (transformType) {
            case "dict" -> applyDictRule(raw, mapping == null ? null : mapping.getTransformRule(), targetField, strict);
            case "date" -> formatDateTime(raw, mapping == null ? null : mapping.getTransformRule(), targetField, strict);
            case "number" -> normalizeNumber(raw, mapping == null ? null : mapping.getTransformRule(), targetField, strict);
            case "json_path" -> {
                JsonNode node = readStrictJsonTree(String.valueOf(raw), "字段【" + targetField + "】的待提取值不是合法JSON");
                Object childValue = nodeToValue(resolveJsonPath(node, mapping == null ? null : mapping.getTransformRule()));
                if (childValue == null && strict) {
                    throw BizException.validationFailed("字段【" + targetField + "】按转换表达式未提取到任何值");
                }
                yield childValue;
            }
            default -> raw;
        };
    }

    private Object coerceFieldValueByMeta(StandardFieldMeta fieldMeta, Object value, String targetField, boolean strict) {
        if (fieldMeta == null || value == null) {
            return value;
        }
        return switch (fieldMeta.valueType()) {
            case FIELD_TYPE_ARRAY -> normalizeArrayValue(value, targetField, strict);
            case FIELD_TYPE_LONG -> {
                Long longValue = asLong(value);
                if (longValue == null && strict) {
                    throw BizException.validationFailed("字段【" + targetField + "】要求整数值");
                }
                yield longValue == null ? value : longValue;
            }
            case FIELD_TYPE_NUMBER -> {
                String normalized = normalizeNumber(value, null, targetField, strict);
                yield normalized == null ? value : normalized;
            }
            case FIELD_TYPE_DATE -> {
                LocalDate date = parseDate(value);
                if (date == null && strict) {
                    throw BizException.validationFailed("字段【" + targetField + "】要求日期值");
                }
                yield date == null ? value : date.toString();
            }
            case FIELD_TYPE_DATETIME -> {
                String normalized = formatDateTime(value, null, targetField, strict);
                yield normalized == null ? value : normalized;
            }
            default -> value;
        };
    }

    private Object normalizeArrayValue(Object value, String targetField, boolean strict) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof Map<?, ?> map) {
            return List.of(new LinkedHashMap<>(map));
        }
        if (value instanceof String text) {
            String trimmed = StrUtil.trimToEmpty(text);
            if (trimmed.isEmpty()) {
                return Collections.emptyList();
            }
            if (trimmed.startsWith("[")) {
                try {
                    return objectMapper.readValue(trimmed, new TypeReference<List<Object>>() {});
                } catch (Exception ex) {
                    if (strict) {
                        throw BizException.validationFailed("字段【" + targetField + "】要求数组值，默认值/转换结果不是合法JSON数组");
                    }
                    return value;
                }
            }
            return Arrays.stream(trimmed.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }
        if (strict) {
            throw BizException.validationFailed("字段【" + targetField + "】要求数组值");
        }
        return value;
    }

    private JsonNode readStrictJsonTree(String json, String errorMessage) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw BizException.validationFailed(errorMessage);
        }
    }

    private Map<String, String> parseDictRuleStrict(String transformRule, String targetField) {
        if (StrUtil.isBlank(transformRule)) {
            throw BizException.validationFailed("字段【" + targetField + "】使用字典映射时，必须填写转换表达式");
        }
        Map<String, String> map = new LinkedHashMap<>();
        String trimmedRule = transformRule.trim();
        if (trimmedRule.startsWith("{")) {
            try {
                Map<String, Object> rawMap = objectMapper.readValue(trimmedRule, new TypeReference<LinkedHashMap<String, Object>>() {});
                rawMap.forEach((key, value) -> map.put(key, value == null ? null : String.valueOf(value)));
            } catch (Exception ex) {
                throw BizException.validationFailed("字段【" + targetField + "】的字典映射表达式不是合法JSON对象");
            }
        } else {
            for (String item : transformRule.split(",")) {
                String ruleItem = item.trim();
                if (ruleItem.isEmpty()) {
                    continue;
                }
                String[] pair = ruleItem.split("=", 2);
                if (pair.length != 2 || StrUtil.isBlank(pair[0].trim())) {
                    throw BizException.validationFailed("字段【" + targetField + "】的字典映射表达式格式不正确，应为 key=value 或 JSON 对象");
                }
                map.put(pair[0].trim(), pair[1].trim());
            }
        }
        if (map.isEmpty()) {
            throw BizException.validationFailed("字段【" + targetField + "】的字典映射表达式不能为空");
        }
        return map;
    }

    private Object applyDictRule(Object raw, String transformRule, String targetField, boolean strict) {
        Map<String, String> map = parseDictRuleStrict(transformRule, targetField);
        String rawKey = raw == null ? null : String.valueOf(raw);
        if (rawKey != null && map.containsKey(rawKey)) {
            return map.get(rawKey);
        }
        if (strict) {
            throw BizException.validationFailed("字段【" + targetField + "】未命中字典映射规则");
        }
        return raw;
    }

    private String normalizeNumber(Object raw, String rule, String targetField, boolean strict) {
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw).trim());
            if (StrUtil.isNotBlank(rule)) {
                value = value.multiply(new BigDecimal(rule.trim()));
            }
            return value.stripTrailingZeros().toPlainString();
        } catch (Exception ex) {
            if (strict) {
                throw BizException.validationFailed("字段【" + targetField + "】的数值换算失败");
            }
            return raw == null ? null : String.valueOf(raw);
        }
    }

    private String formatDateTime(Object raw, String rule, String targetField, boolean strict) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime time) {
            return time.format(STANDARD_TIME);
        }
        if (raw instanceof LocalDate date) {
            return date.atStartOfDay().format(STANDARD_TIME);
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (StrUtil.isNotBlank(rule)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(rule);
            try {
                return LocalDateTime.parse(text, formatter).format(STANDARD_TIME);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(text, formatter).atStartOfDay().format(STANDARD_TIME);
            } catch (DateTimeParseException ignored) {
            }
        }
        try {
            return LocalDateTime.parse(text).format(STANDARD_TIME);
        } catch (DateTimeParseException ignored) {
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ISO_DATE_TIME,
                DateTimeFormatter.ISO_DATE
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(text, formatter).format(STANDARD_TIME);
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(text, formatter).atStartOfDay().format(STANDARD_TIME);
            } catch (DateTimeParseException ignored) {
            }
        }
        if (strict) {
            throw BizException.validationFailed("字段【" + targetField + "】的日期格式转换失败");
        }
        return text;
    }

    private LocalDateTime parseDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime time) {
            return time;
        }
        if (raw instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (raw instanceof java.util.Date date) {
            return LocalDateTime.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        }
        String text = String.valueOf(raw);
        if (StrUtil.isBlank(text)) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SS"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
                DateTimeFormatter.ISO_DATE_TIME
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private LocalDate parseDate(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDate date) {
            return date;
        }
        if (raw instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (raw instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        String text = String.valueOf(raw);
        if (StrUtil.isBlank(text)) {
            return null;
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ISO_DATE,
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd")
        );
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        LocalDateTime dateTime = parseDateTime(text);
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private AttachmentExtractionResult extractFiles(Map<String, Object> normalized) {
        List<FileCandidate> files = new ArrayList<>();
        int invalidCount = 0;
        Object attachmentObj = normalized.get("attachments");
        if (attachmentObj != null) {
            if (attachmentObj instanceof Collection<?> collection) {
                if (collection.isEmpty()) {
                    return AttachmentExtractionResult.diagnostic(FILE_RECORD_ERROR_ATTACHMENTS_EMPTY, "第三方本次未返回可转存附件");
                }
                invalidCount += collectFileCandidates(collection, files);
                if (files.isEmpty()) {
                    return AttachmentExtractionResult.diagnostic(FILE_RECORD_ERROR_ATTACHMENTS_INVALID, "第三方返回了附件字段，但未解析出有效的 http/https 附件地址");
                }
            } else if (appendFileCandidate(attachmentObj, files)) {
                // 单值附件直接视为有效
            } else {
                return AttachmentExtractionResult.diagnostic(FILE_RECORD_ERROR_ATTACHMENTS_INVALID, "第三方返回了附件字段，但附件格式不是系统支持的地址/数组结构");
            }
            String warning = invalidCount > 0 ? "附件列表中有" + invalidCount + "项缺少有效地址或格式不支持，系统已自动跳过" : null;
            return AttachmentExtractionResult.of(files, null, null, warning);
        }

        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            if (!isLikelyAttachmentField(key)) {
                continue;
            }
            Object valueObject = entry.getValue();
            if (valueObject instanceof Collection<?> collection) {
                invalidCount += collectFileCandidates(collection, files);
            } else if (!appendFileCandidate(valueObject, files)) {
                invalidCount++;
            }
        }
        if (!files.isEmpty()) {
            String warning = invalidCount > 0 ? "附件候选字段中有" + invalidCount + "项未解析出有效地址，系统已自动跳过" : null;
            return AttachmentExtractionResult.of(files, null, null, warning);
        }
        return AttachmentExtractionResult.diagnostic(
                FILE_RECORD_ERROR_ATTACHMENTS_FIELD_MISSING,
                "标准化结果未包含 attachments 字段，未触发附件转存；请检查第三方返回内容或字段映射是否覆盖 attachments"
        );
    }

    private int collectFileCandidates(Collection<?> collection, List<FileCandidate> files) {
        int invalidCount = 0;
        for (Object item : collection) {
            if (!appendFileCandidate(item, files)) {
                invalidCount++;
            }
        }
        return invalidCount;
    }

    private boolean appendFileCandidate(Object item, List<FileCandidate> files) {
        if (item == null) {
            return false;
        }
        String url = null;
        String fileName = null;
        if (item instanceof Map<?, ?> map) {
            url = firstNonBlank(asString(map.get("url")), asString(map.get("fileUrl")), asString(map.get("link")));
            fileName = firstNonBlank(asString(map.get("name")), asString(map.get("fileName")));
        } else {
            url = String.valueOf(item);
        }
        url = StrUtil.trimToNull(url);
        if (url == null || !(StrUtil.startWithIgnoreCase(url, "http://") || StrUtil.startWithIgnoreCase(url, "https://"))) {
            return false;
        }
        String normalizedUrl = sanitizeFileSourceUrl(url);
        files.add(new FileCandidate(
                firstNonBlank(fileName, guessFileName(normalizedUrl), "integration-file"),
                url,
                normalizedUrl,
                buildFileSourceSignature(normalizedUrl)
        ));
        return true;
    }

    private boolean isLikelyAttachmentField(String key) {
        return key.contains("file") || key.contains("attachment") || key.contains("image") || key.contains("evidence");
    }

    private DownloadedFilePayload downloadFileWithRetry(String sourceUrl) {
        Exception last = null;
        for (int attempt = 1; attempt <= FILE_TRANSFER_MAX_ATTEMPTS; attempt++) {
            try {
                return downloadFileOnce(sourceUrl);
            } catch (Exception ex) {
                last = ex;
                if (attempt >= FILE_TRANSFER_MAX_ATTEMPTS || !isRetryableFileTransferException(ex)) {
                    break;
                }
                sleepQuietly(FILE_TRANSFER_RETRY_DELAY_MILLIS);
            }
        }
        if (last instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw BizException.of("下载第三方附件失败");
    }

    private DownloadedFilePayload downloadFileOnce(String sourceUrl) {
        try {
            ResponseEntity<byte[]> response = aiRestTemplate.exchange(sourceUrl, HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw BizException.of("第三方附件为空");
            }
            MediaType mediaType = response.getHeaders() == null ? null : response.getHeaders().getContentType();
            return new DownloadedFilePayload(body, mediaType == null ? null : mediaType.toString());
        } catch (RestClientResponseException ex) {
            throw BizException.of("下载第三方附件失败：HTTP " + ex.getRawStatusCode());
        } catch (RestClientException ex) {
            throw BizException.of("下载第三方附件失败：" + normalizeFileTransferExceptionMessage(ex));
        }
    }

    private String uploadFileWithRetry(String fileName, String contentType, byte[] bytes) {
        Exception last = null;
        for (int attempt = 1; attempt <= FILE_TRANSFER_MAX_ATTEMPTS; attempt++) {
            try {
                return fileStorageService.upload(new SimpleNamedMultipartFile("file", fileName, contentType, bytes), "integration");
            } catch (Exception ex) {
                last = ex;
                if (attempt >= FILE_TRANSFER_MAX_ATTEMPTS || !isRetryableFileTransferException(ex)) {
                    break;
                }
                sleepQuietly(FILE_TRANSFER_RETRY_DELAY_MILLIS);
            }
        }
        if (last instanceof RestClientResponseException ex) {
            throw BizException.of("上传 MinIO 失败：HTTP " + ex.getRawStatusCode());
        }
        throw BizException.of("上传 MinIO 失败：" + normalizeFileTransferExceptionMessage(last));
    }

    private boolean isRetryableFileTransferException(Exception ex) {
        if (ex instanceof BizException) {
            return false;
        }
        if (ex instanceof ResourceAccessException) {
            return true;
        }
        if (ex instanceof RestClientResponseException responseException) {
            return responseException.getStatusCode().is5xxServerError() || responseException.getRawStatusCode() == 429;
        }
        String message = normalizeFileTransferExceptionMessage(ex).toLowerCase(Locale.ROOT);
        return message.contains("timeout")
                || message.contains("timed out")
                || message.contains("connection reset")
                || message.contains("connection refused")
                || message.contains("temporarily unavailable");
    }

    private String normalizeFileTransferExceptionMessage(Exception ex) {
        if (ex == null) {
            return "未知异常";
        }
        String message = firstNonBlank(ex.getMessage(), ex.getClass().getSimpleName());
        return StrUtil.sub(message.replaceAll("\\s+", " ").trim(), 0, 300);
    }

    private void validateTransferFileConstraints(String fileName, String mimeType, int byteLength) {
        if (byteLength <= 0) {
            throw BizException.of("第三方附件为空");
        }
        if (byteLength > MAX_INTEGRATION_FILE_SIZE) {
            throw BizException.badRequest("第三方附件大小超过50MB，已拒绝转存");
        }
        String extension = getFileExtension(fileName);
        if (BLOCKED_INTEGRATION_FILE_EXTENSIONS.contains(extension)) {
            throw BizException.badRequest("第三方附件类型不受支持，已拒绝转存");
        }
        String normalizedMimeType = StrUtil.blankToDefault(StrUtil.trimToNull(mimeType), "application/octet-stream").toLowerCase(Locale.ROOT);
        if (normalizedMimeType.contains("javascript")
                || normalizedMimeType.contains("x-msdownload")
                || normalizedMimeType.contains("x-sh")) {
            throw BizException.badRequest("第三方附件类型不受支持，已拒绝转存");
        }
    }

    private String resolveTransferMimeType(String fileName, String responseContentType) {
        String contentType = StrUtil.trimToNull(responseContentType);
        if (contentType != null) {
            int separatorIndex = contentType.indexOf(';');
            if (separatorIndex >= 0) {
                contentType = contentType.substring(0, separatorIndex).trim();
            }
        }
        if (StrUtil.isNotBlank(contentType) && !"application/octet-stream".equalsIgnoreCase(contentType)) {
            return contentType;
        }
        return guessContentType(fileName);
    }

    private String sanitizeFileSourceUrl(String sourceUrl) {
        String trimmed = StrUtil.trimToEmpty(sourceUrl);
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        try {
            URI uri = URI.create(trimmed);
            StringBuilder builder = new StringBuilder();
            if (StrUtil.isNotBlank(uri.getScheme())) {
                builder.append(uri.getScheme()).append("://");
            }
            if (StrUtil.isNotBlank(uri.getHost())) {
                builder.append(uri.getHost());
                if (uri.getPort() > 0) {
                    builder.append(':').append(uri.getPort());
                }
            }
            builder.append(firstNonBlank(uri.getRawPath(), ""));
            return builder.length() == 0 ? stripUrlQuery(trimmed) : builder.toString();
        } catch (Exception ignored) {
            return stripUrlQuery(trimmed);
        }
    }

    private String stripUrlQuery(String sourceUrl) {
        String path = sourceUrl;
        int fragmentIndex = path.indexOf('#');
        if (fragmentIndex >= 0) {
            path = path.substring(0, fragmentIndex);
        }
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        return path;
    }

    private String buildFileSourceSignature(String sanitizedSourceUrl) {
        return sha256(StrUtil.blankToDefault(StrUtil.trimToNull(sanitizedSourceUrl), "_blank_"));
    }

    private String getFileExtension(String fileName) {
        String normalized = firstNonBlank(fileName, "").trim();
        int dotIndex = normalized.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private FileTransferFailure resolveFileTransferFailure(Exception ex, boolean duringDownload) {
        String message = firstNonBlank(ex == null ? null : ex.getMessage(), duringDownload ? "下载第三方附件失败" : "上传 MinIO 失败");
        if (StrUtil.contains(message, "第三方附件为空")) {
            return new FileTransferFailure(FILE_RECORD_ERROR_FILE_EMPTY, message);
        }
        if (StrUtil.contains(message, "大小超过50MB")) {
            return new FileTransferFailure(FILE_RECORD_ERROR_FILE_SIZE_EXCEEDED, message);
        }
        if (StrUtil.contains(message, "类型不受支持")) {
            return new FileTransferFailure(FILE_RECORD_ERROR_FILE_TYPE_BLOCKED, message);
        }
        return new FileTransferFailure(duringDownload ? FILE_RECORD_ERROR_DOWNLOAD : FILE_RECORD_ERROR_STORAGE, message);
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String guessFileName(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl)) {
            return "integration-file";
        }
        String path = sourceUrl;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        int slashIndex = path.lastIndexOf('/');
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }

    private String guessContentType(String fileName) {
        return Optional.ofNullable(URLConnection.guessContentTypeFromName(fileName)).orElse("application/octet-stream");
    }

    private CallbackSecurityResult verifyCallbackSecurity(IntegrationBinding binding,
                                                          IntegrationModuleConfig bindingConfig,
                                                          List<IntegrationModuleConfig> candidateConfigs,
                                                          String payload,
                                                          HttpServletRequest request) {
        if (binding != null) {
            IntegrationModuleConfig config = firstNonNull(bindingConfig, getCallbackModuleConfigEntity(binding));
            return verifyCallbackSecurityForConfig(config, payload, request);
        }
        if (candidateConfigs == null || candidateConfigs.isEmpty()) {
            return new CallbackSecurityResult(false, "未找到启用回调的模块接入配置", null);
        }
        List<CallbackSecurityResult> passedResults = new ArrayList<>();
        CallbackSecurityResult firstFailure = null;
        for (IntegrationModuleConfig config : candidateConfigs) {
            CallbackSecurityResult result = verifyCallbackSecurityForConfig(config, payload, request);
            if (result.passed()) {
                passedResults.add(result);
            } else if (firstFailure == null) {
                firstFailure = result;
            }
        }
        if (passedResults.size() == 1) {
            return passedResults.get(0);
        }
        if (passedResults.size() > 1) {
            return new CallbackSecurityResult(false, "回调命中了多个启用配置且安全校验均通过，请补充配置唯一标识", null);
        }
        return firstNonNull(firstFailure, new CallbackSecurityResult(false, "回调安全校验失败", null));
    }

    private CallbackSecurityResult verifyCallbackSecurityForConfig(IntegrationModuleConfig config, String payload, HttpServletRequest request) {
        if (config == null || config.getId() == null) {
            return new CallbackSecurityResult(false, "未找到可校验安全规则的模块接入配置", null);
        }
        Map<String, String> secrets = loadSecretPlainMap(config.getId());
        String ipWhitelist = firstNonBlank(secrets.get("callbackIpWhitelist"), secrets.get("ipWhitelist"), secrets.get("callbackSourceIps"));
        if (StrUtil.isBlank(ipWhitelist)) {
            return new CallbackSecurityResult(false, CALLBACK_IP_WHITELIST_MISSING_ERROR, config);
        }
        if (!isClientIpAllowed(extractClientIp(request), ipWhitelist)) {
            return new CallbackSecurityResult(false, CALLBACK_IP_DENIED_ERROR, config);
        }
        String timestamp = firstNonBlank(
                request.getHeader("X-Timestamp"),
                request.getHeader("timestamp"),
                request.getParameter("timestamp"),
                request.getParameter("ts")
        );
        if (StrUtil.isBlank(timestamp)) {
            return new CallbackSecurityResult(false, CALLBACK_TIMESTAMP_MISSING_ERROR, config);
        }
        if (!isCallbackTimestampValid(timestamp, resolveCallbackToleranceSeconds(secrets))) {
            return new CallbackSecurityResult(false, CALLBACK_TIMESTAMP_INVALID_ERROR, config);
        }
        String signature = firstNonBlank(request.getHeader("X-Signature"), request.getHeader("signature"), request.getParameter("signature"));
        if (StrUtil.isBlank(signature)) {
            return new CallbackSecurityResult(false, CALLBACK_SIGNATURE_MISSING_ERROR, config);
        }
        String secret = firstNonBlank(secrets.get("callbackSignSecret"), secrets.get("signatureSecret"), secrets.get("appSecret"), secrets.get("clientSecret"));
        if (StrUtil.isBlank(secret)) {
            return new CallbackSecurityResult(false, CALLBACK_SIGNATURE_SECRET_MISSING_ERROR, config);
        }
        String hex = hmacSha256Hex(payload, secret);
        String base64 = hmacSha256Base64(payload, secret);
        String payloadWithTimestamp = payload + "|" + timestamp;
        String timestampFirstPayload = timestamp + "|" + payload;
        boolean passed = signature.equalsIgnoreCase(hex)
                || signature.equals(base64)
                || signature.equalsIgnoreCase(hmacSha256Hex(payloadWithTimestamp, secret))
                || signature.equals(hmacSha256Base64(payloadWithTimestamp, secret))
                || signature.equalsIgnoreCase(hmacSha256Hex(timestampFirstPayload, secret))
                || signature.equals(hmacSha256Base64(timestampFirstPayload, secret));
        return new CallbackSecurityResult(passed, passed ? null : "回调签名校验失败", config);
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwardedFor = firstNonBlank(request.getHeader("X-Forwarded-For"), request.getHeader("x-forwarded-for"));
        if (StrUtil.isNotBlank(forwardedFor)) {
            return StrUtil.trim(forwardedFor.split(",")[0]);
        }
        return firstNonBlank(request.getRemoteAddr(), "unknown");
    }

    private boolean isClientIpAllowed(String clientIp, String whitelist) {
        if (StrUtil.isBlank(clientIp) || StrUtil.isBlank(whitelist)) {
            return false;
        }
        String normalizedClientIp = normalizeIpLiteral(clientIp);
        List<String> rules = Arrays.stream(whitelist.split("[,;\\n]"))
                .map(StrUtil::trim)
                .filter(StrUtil::isNotBlank)
                .toList();
        for (String rule : rules) {
            String normalizedRule = normalizeIpLiteral(rule);
            if ("*".equals(rule) || Objects.equals(normalizedRule, normalizedClientIp)) {
                return true;
            }
            if (normalizedRule.contains("*")) {
                String prefix = normalizedRule.substring(0, normalizedRule.indexOf('*'));
                if (normalizedClientIp.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeIpLiteral(String ip) {
        String normalized = StrUtil.trimToEmpty(ip).toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalized)) {
            return "127.0.0.1";
        }
        if ("::1".equals(normalized) || "0:0:0:0:0:0:0:1".equals(normalized) || "::ffff:127.0.0.1".equals(normalized)) {
            return "127.0.0.1";
        }
        if (normalized.startsWith("::ffff:")) {
            return normalized.substring("::ffff:".length());
        }
        return normalized;
    }

    private int resolveCallbackToleranceSeconds(Map<String, String> secrets) {
        Integer value = parseInteger(firstNonBlank(
                secrets.get("callbackTimestampToleranceSeconds"),
                secrets.get("timestampToleranceSeconds"),
                secrets.get("callbackTimeWindowSeconds")
        ));
        return value == null || value <= 0 ? CALLBACK_DEFAULT_TOLERANCE_SECONDS : value;
    }

    private boolean isCallbackTimestampValid(String timestampText, int toleranceSeconds) {
        Long timestampSeconds = parseTimestampSeconds(timestampText);
        if (timestampSeconds == null) {
            return false;
        }
        long nowSeconds = System.currentTimeMillis() / 1000;
        return Math.abs(nowSeconds - timestampSeconds) <= toleranceSeconds;
    }

    private Long parseTimestampSeconds(String timestampText) {
        if (StrUtil.isBlank(timestampText)) {
            return null;
        }
        try {
            long raw = Long.parseLong(timestampText.trim());
            return raw > 9_999_999_999L ? raw / 1000 : raw;
        } catch (NumberFormatException ignored) {
        }
        LocalDateTime dateTime = parseDateTime(timestampText);
        return dateTime == null ? null : dateTime.atZone(java.time.ZoneId.systemDefault()).toEpochSecond();
    }

    private boolean callbackExists(String claimKey) {
        if (StrUtil.isBlank(claimKey)) {
            return false;
        }
        LambdaQueryWrapper<IntegrationCallbackLog> wrapper = new LambdaQueryWrapper<IntegrationCallbackLog>()
                .eq(IntegrationCallbackLog::getClaimKey, claimKey);
        Long count = callbackLogMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    private boolean callbackPathMatches(String requestUri, String callbackUrl) {
        if (StrUtil.isBlank(callbackUrl)) {
            return true;
        }
        try {
            URI uri = URI.create(callbackUrl);
            return Objects.equals(StrUtil.blankToDefault(uri.getPath(), ""), StrUtil.blankToDefault(requestUri, ""));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String resolveCallbackBizNo(JsonNode body) {
        return firstNonBlank(
                findString(body, "bizNo"),
                findString(body, "orderNo"),
                findString(body, "purchaseOrderNo"),
                findString(body, "sampleNo"),
                findString(body, "sampleRecordNo"),
                findString(body, "sampleTaskNo"),
                findString(body, "certificateNo"),
                findString(body, "checkNo"),
                findString(body, "employeeCode"),
                findString(body, "deviceCode")
        );
    }

    private String collectHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        var names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return safeJson(sanitizeLogValue(headers, null));
    }

    private String hmacSha256Hex(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "";
        }
    }

    private String hmacSha256Base64(String content, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private String sha256(String payload) {
        return sha256(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(bytes));
        } catch (Exception ex) {
            return "";
        }
    }

    private URI buildUri(String url, Map<String, Object> query) {
        if (query == null || query.isEmpty()) {
            return URI.create(url);
        }
        StringBuilder builder = new StringBuilder(url);
        builder.append(url.contains("?") ? "&" : "?");
        builder.append(query.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&")));
        return URI.create(builder.toString());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyOrgScope(LambdaQueryWrapper wrapper, Long orgId, DataScopeService.DataScopeResult scope) {
        if (scope.isRestricted()) {
            if (orgId != null) {
                if (!scope.isAllowed(orgId)) {
                    wrapper.apply("1 = 0");
                }
            } else if (scope.getOrgIds().isEmpty()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.in("org_id", scope.getOrgIds());
            }
        }
    }

    private Set<Long> resolveConfigIdsByProvider(String providerCode) {
        if (StrUtil.isBlank(providerCode)) {
            return Collections.emptySet();
        }
        return moduleConfigMapper.selectList(new LambdaQueryWrapper<IntegrationModuleConfig>()
                        .eq(IntegrationModuleConfig::getProviderCode, providerCode)
                        .eq(currentTenantId() != null, IntegrationModuleConfig::getTenantId, currentTenantId()))
                .stream()
                .map(IntegrationModuleConfig::getId)
                .collect(Collectors.toSet());
    }

    private void validateOrgWritable(Long orgId) {
        validateOrgReadable(orgId, dataScopeService.resolveCurrentUserOrgScope());
    }

    private void validateOrgReadable(Long orgId, DataScopeService.DataScopeResult scope) {
        if (orgId == null) {
            return;
        }
        if (!scope.isAllowed(orgId)) {
            throw BizException.forbidden("无权访问该组织数据");
        }
    }

    private void validateTenantReadable(Long tenantId) {
        if (tenantId == null) {
            return;
        }
        if (!Objects.equals(tenantId, currentTenantId())) {
            throw BizException.forbidden("无权访问该租户数据");
        }
    }

    private Long currentTenantId() {
        return Optional.ofNullable(UserContext.getTenantId()).orElse(1L);
    }

    private Long currentOrgId() {
        return UserContext.getOrgId();
    }

    private String generateTaskNo() {
        return "ITG-" + System.currentTimeMillis();
    }

    private long queryCount(String sql, Object... args) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long queryLong(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0L : value.longValue();
    }

    private List<IntegrationOverviewVO.MetricItem> queryMetricList(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        List<IntegrationOverviewVO.MetricItem> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            IntegrationOverviewVO.MetricItem item = new IntegrationOverviewVO.MetricItem();
            item.setCode(asString(row.get("code")));
            item.setLabel(asString(row.get("label")));
            item.setValue(asLong(row.get("value")));
            list.add(item);
        }
        return list;
    }

    private String resolveBizModuleDisplayLabel(String code, String fallback) {
        if (StrUtil.isBlank(code)) {
            return fallback;
        }
        return switch (code) {
            case BIZ_MODULE_PURCHASE_ORDER -> "采购订单";
            case BIZ_MODULE_SAMPLE_RETENTION -> "留样管理";
            case BIZ_MODULE_MORNING_CHECK -> "智能人脸晨检";
            case "supplier" -> "供应商管理";
            case "inbound" -> "入库管理";
            default -> firstNonBlank(fallback, code);
        };
    }

    private String buildOrgFilterSql(String fieldName, Long orgId, DataScopeService.DataScopeResult scope) {
        if (orgId != null) {
            return " AND " + fieldName + " = ?";
        }
        if (scope.isRestricted() && !scope.getOrgIds().isEmpty()) {
            return " AND " + fieldName + " IN (" + scope.getOrgIds().stream().map(id -> "?").collect(Collectors.joining(",")) + ")";
        }
        if (scope.isRestricted()) {
            return " AND 1 = 0";
        }
        return "";
    }

    private Object[] buildOrgFilterArgs(Long orgId, DataScopeService.DataScopeResult scope) {
        if (orgId != null) {
            return new Object[]{orgId};
        }
        if (scope.isRestricted() && !scope.getOrgIds().isEmpty()) {
            return scope.getOrgIds().toArray();
        }
        return new Object[0];
    }

    private BigDecimal percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator * 100.0d / denominator).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String normalizeMode(String mode, IntegrationModuleConfig config) {
        String explicitMode = StrUtil.trimToNull(mode);
        if (explicitMode != null && !MODE_THIRD_PARTY.equals(explicitMode) && !MODE_MANUAL.equals(explicitMode)) {
            throw BizException.validationFailed("维护模式仅支持 manual 或 third_party");
        }
        if (config.getForceThirdParty() != null && config.getForceThirdParty() == 1) {
            return MODE_THIRD_PARTY;
        }
        String result = firstNonBlank(explicitMode, config.getDefaultMode(), MODE_MANUAL);
        if (MODE_THIRD_PARTY.equals(result) || MODE_MANUAL.equals(result)) {
            return result;
        }
        return MODE_MANUAL;
    }

    private String findString(JsonNode node, String key) {
        JsonNode found = findNode(node, key);
        return found == null ? null : asString(nodeToValue(found));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record HealthCheckResult(String status, Boolean authSuccess, Boolean reachable, Boolean callbackReachable,
                                     String message, String authMessage, String reachabilityMessage,
                                     String callbackMessage, RequestLogSnapshot requestSnapshot,
                                     String responsePayload, String errorCode, String errorMessage) {}

    private record RequestSpec(String url, String method, Map<String, String> headers,
                               Map<String, Object> query, Object body, JsonNode testResponse) {}

    private record RequestLogSnapshot(String requestPayload, String requestHeaders, String requestBody) {}

    private record ThirdPartyResponseResult(JsonNode responseNode, RequestLogSnapshot requestSnapshot, String responsePayload) {}

    private record HealthResponseInspection(boolean warning, String errorCode, String message) {}

    private record StatusMatch(String targetStatus, boolean finishFlag, boolean triggerBusinessAction, String actionCode) {}

    private record NormalizedResult(Map<String, Object> data, StatusMatch statusMatch, String processStatus, String processMessage) {}

    private record SyncLogCreatePayload(String requestPayload, String requestHeaders, String requestBody,
                                        String responsePayload, String normalizedPayload, String syncStatus,
                                        String errorCode, String errorMessage, String resultMessage,
                                        String writeBackResult, long durationMs, Long auditLogId) {}

    private record TaskExecutionOptions(boolean writeBackAllowed, boolean persistBindingState) {
        private static TaskExecutionOptions standard() {
            return new TaskExecutionOptions(true, true);
        }

        private static TaskExecutionOptions queryOnly() {
            return new TaskExecutionOptions(false, false);
        }
    }

    private record ValidatedTaskTrigger(String bizModule, String bizScene, Long bizId, String bizNo,
                                        String externalNo, String modeSource, Integer modeLocked, Integer queryOnly) {}

    private record StandardFieldMeta(String valueType, Set<String> allowedValues) {}

    private record FieldMappingFailureDecision(boolean continueProcessing, String processStatus, String processMessage) {}

    private record FileTransferResult(String fileName, String sourceUrl, String minioUrl) {}

    private record FileTransferBatchResult(List<FileTransferResult> availableFiles, int transferredCount, int failedCount,
                                           int skippedCount, int reusedCount, List<String> warnings) {
        private static FileTransferBatchResult empty() {
            return of(Collections.emptyList(), 0, 0, 0, 0, Collections.emptyList());
        }

        private static FileTransferBatchResult of(List<FileTransferResult> availableFiles, int transferredCount, int failedCount,
                                                  int skippedCount, int reusedCount, List<String> warnings) {
            return new FileTransferBatchResult(
                    availableFiles == null ? Collections.emptyList() : List.copyOf(availableFiles),
                    transferredCount,
                    failedCount,
                    skippedCount,
                    reusedCount,
                    warnings == null ? Collections.emptyList() : List.copyOf(warnings)
            );
        }
    }

    private record FileTransferFailure(String errorCode, String errorMessage) {}

    private record DownloadedFilePayload(byte[] bytes, String contentType) {}

    private record FileCandidate(String fileName, String rawUrl, String sanitizedUrl, String signature) {}

    private record AttachmentExtractionResult(List<FileCandidate> files, String diagnosticCode,
                                              String diagnosticMessage, String warningMessage) {
        private static AttachmentExtractionResult of(List<FileCandidate> files, String diagnosticCode,
                                                     String diagnosticMessage, String warningMessage) {
            return new AttachmentExtractionResult(
                    files == null ? Collections.emptyList() : List.copyOf(files),
                    diagnosticCode,
                    diagnosticMessage,
                    warningMessage
            );
        }

        private static AttachmentExtractionResult diagnostic(String diagnosticCode, String diagnosticMessage) {
            return of(Collections.emptyList(), diagnosticCode, diagnosticMessage, null);
        }
    }

    private record CallbackSecurityResult(boolean passed, String message, IntegrationModuleConfig matchedConfig) {}

    private record CallbackBindingMatchResult(IntegrationBinding binding, String reason) {}

    private record CallbackReachabilityResult(boolean applicable, Boolean reachable, String message) {}

    private record ProviderCircuitState(int failureCount, long openUntilEpochMillis) {}

    private static final class RequestAttemptContext {
        private RequestLogSnapshot requestSnapshot;
    }

    private static final class ThirdPartyRequestException extends RuntimeException {

        private final String errorCode;
        private final RequestLogSnapshot requestSnapshot;
        private final String responsePayload;
        private final Integer httpStatus;
        private final boolean refreshable;
        private final boolean rateLimited;

        private ThirdPartyRequestException(String errorCode, String message, RequestLogSnapshot requestSnapshot,
                                           String responsePayload, Integer httpStatus, boolean refreshable, boolean rateLimited) {
            super(message);
            this.errorCode = errorCode;
            this.requestSnapshot = requestSnapshot;
            this.responsePayload = responsePayload;
            this.httpStatus = httpStatus;
            this.refreshable = refreshable;
            this.rateLimited = rateLimited;
        }

        private String errorCode() {
            return errorCode;
        }

        private RequestLogSnapshot requestSnapshot() {
            return requestSnapshot;
        }

        private String responsePayload() {
            return responsePayload;
        }

        private Integer httpStatus() {
            return httpStatus;
        }

        private boolean refreshable() {
            return refreshable;
        }

        private boolean rateLimited() {
            return rateLimited;
        }

        private boolean retryable() {
            return rateLimited || SYNC_LOG_ERROR_CODE_HTTP_5XX.equals(errorCode)
                    || SYNC_LOG_ERROR_CODE_TIMEOUT.equals(errorCode)
                    || SYNC_LOG_ERROR_CODE_NETWORK.equals(errorCode);
        }
    }
}
