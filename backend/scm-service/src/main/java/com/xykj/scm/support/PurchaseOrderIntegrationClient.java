package com.xykj.scm.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xykj.common.context.UserContext;
import com.xykj.common.exception.BizException;
import com.xykj.scm.dto.PurchaseOrderSceneIntegrationSyncDTO;
import com.xykj.scm.entity.PurchaseOrder;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationLogsVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationMetaVO;
import com.xykj.scm.vo.PurchaseOrderSceneIntegrationTriggerResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseOrderIntegrationClient {

    private static final String SYS_SERVICE_NAME = "smartfood_sys_service";
    private static final String DEFAULT_SYS_SERVICE_BASE_URL = "http://127.0.0.1:8089";
    private static final int SYS_SERVICE_CONNECT_TIMEOUT_MS = 3000;
    private static final int SYS_SERVICE_READ_TIMEOUT_MS = 5000;

    private final DiscoveryClient discoveryClient;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = createRestTemplate();

    public PurchaseOrderSceneIntegrationMetaVO getSceneMeta(PurchaseOrder order, String scene) {
        Map<String, Object> params = buildBaseQuery(order, scene);
        return exchange(
                "/api/v1/integration/internal/bindings/by-biz",
                HttpMethod.GET,
                null,
                params,
                new TypeReference<>() {
                }
        );
    }

    public PurchaseOrderSceneIntegrationTriggerResultVO triggerSceneSync(PurchaseOrder order, String scene,
                                                                         PurchaseOrderSceneIntegrationSyncDTO request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("configId", request.getConfigId());
        body.put("bizModule", "purchase_order");
        body.put("bizScene", scene);
        body.put("bizId", order.getId());
        body.put("bizNo", order.getOrderNo());
        body.put("externalNo", request.getExternalNo());
        body.put("orgId", order.getOrgId());
        body.put("tenantId", order.getTenantId());
        body.put("maintenanceMode", "third_party");
        body.put("modeSource", "business_scene");
        body.put("modeLocked", 0);
        body.put("triggerType", "manual");
        body.put("queryOnly", request.getQueryOnly() == null ? 0 : request.getQueryOnly());
        body.put("operatorId", UserContext.getUserId());
        body.put("operatorName", UserContext.getRealName());
        body.put("operatorUsername", UserContext.getUsername());
        return exchange(
                "/api/v1/integration/internal/bindings/trigger",
                HttpMethod.POST,
                body,
                null,
                new TypeReference<>() {
                }
        );
    }

    public PurchaseOrderSceneIntegrationLogsVO getSceneLogs(PurchaseOrder order, String scene) {
        Map<String, Object> params = buildBaseQuery(order, scene);
        return exchange(
                "/api/v1/integration/internal/logs/by-binding",
                HttpMethod.GET,
                null,
                params,
                new TypeReference<>() {
                }
        );
    }

    public void switchSceneModeBestEffort(PurchaseOrder order, String scene, String maintenanceMode, Long configId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bizModule", "purchase_order");
        body.put("bizScene", scene);
        body.put("bizId", order.getId());
        body.put("bizNo", order.getOrderNo());
        body.put("orgId", order.getOrgId());
        body.put("tenantId", order.getTenantId());
        body.put("configId", configId);
        body.put("maintenanceMode", maintenanceMode);
        body.put("modeSource", "business_scene");
        body.put("operatorId", UserContext.getUserId());
        body.put("operatorName", UserContext.getRealName());
        body.put("operatorUsername", UserContext.getUsername());
        try {
            exchange(
                    "/api/v1/integration/internal/bindings/switch-mode",
                    HttpMethod.POST,
                    body,
                    null,
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception ex) {
            log.warn("切换采购订单第三方绑定模式失败，忽略并继续业务保存: orderId={}, scene={}", order.getId(), scene, ex);
        }
    }

    public void saveSceneBinding(PurchaseOrder order, String scene, Long configId, String externalNo) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bizModule", "purchase_order");
        body.put("bizScene", scene);
        body.put("bizId", order.getId());
        body.put("bizNo", order.getOrderNo());
        body.put("orgId", order.getOrgId());
        body.put("tenantId", order.getTenantId());
        body.put("configId", configId);
        body.put("externalNo", externalNo);
        body.put("maintenanceMode", "third_party");
        body.put("modeSource", "business_scene");
        body.put("operatorId", UserContext.getUserId());
        body.put("operatorName", UserContext.getRealName());
        body.put("operatorUsername", UserContext.getUsername());
        exchange(
                "/api/v1/integration/internal/bindings/switch-mode",
                HttpMethod.POST,
                body,
                null,
                new TypeReference<Map<String, Object>>() {
                }
        );
    }

    private Map<String, Object> buildBaseQuery(PurchaseOrder order, String scene) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("bizModule", "purchase_order");
        params.put("bizScene", scene);
        params.put("bizId", order.getId());
        params.put("orgId", order.getOrgId());
        params.put("tenantId", order.getTenantId());
        params.put("logLimit", 6);
        return params;
    }

    private <T> T exchange(String path, HttpMethod method, Object body, Map<String, Object> queryParams, TypeReference<T> typeReference) {
        List<String> baseUrls = resolveSysServiceBaseUrls();
        RuntimeException lastTechnicalException = null;
        for (String baseUrl : baseUrls) {
            try {
                return exchangeOnce(baseUrl, path, method, body, queryParams, typeReference);
            } catch (BizException ex) {
                throw ex;
            } catch (RestClientException | IllegalStateException ex) {
                lastTechnicalException = ex;
                log.warn("采购订单第三方接入内部调用失败，准备尝试下一个sys-service实例: method={}, path={}, baseUrl={}",
                        method, path, baseUrl, ex);
            }
        }
        String attemptedUrls = String.join(", ", baseUrls);
        log.error("采购订单第三方接入内部调用全部失败: method={}, path={}, attemptedUrls={}",
                method, path, attemptedUrls, lastTechnicalException);
        throw BizException.of("第三方接入服务暂时不可用，请稍后重试");
    }

    private <T> T exchangeOnce(String baseUrl, String path, HttpMethod method, Object body,
                               Map<String, Object> queryParams, TypeReference<T> typeReference) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + path);
        if (queryParams != null) {
            queryParams.forEach((key, value) -> {
                if (value != null) {
                    builder.queryParam(key, value);
                }
            });
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String authorization = currentAuthorization();
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<?> entity = body == null ? new HttpEntity<>(headers) : new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(builder.build(true).toUri(), method, entity, String.class);
            if (!StringUtils.hasText(response.getBody())) {
                throw new IllegalStateException("第三方接入服务返回空响应");
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            String code = root.path("code").asText();
            if (!"SUCCESS".equals(code)) {
                throw BizException.of(root.path("message").asText("第三方接入服务调用失败"));
            }
            JsonNode dataNode = root.path("data");
            if (dataNode.isMissingNode() || dataNode.isNull()) {
                return null;
            }
            return objectMapper.convertValue(dataNode, typeReference);
        } catch (BizException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("第三方接入服务返回解析失败：" + ex.getMessage(), ex);
        }
    }

    private List<String> resolveSysServiceBaseUrls() {
        LinkedHashSet<String> baseUrlSet = new LinkedHashSet<>();
        try {
            List<ServiceInstance> instances = discoveryClient.getInstances(SYS_SERVICE_NAME);
            if (instances != null) {
                for (ServiceInstance instance : instances) {
                    if (instance != null && instance.getUri() != null) {
                        baseUrlSet.add(normalizeBaseUrl(instance.getUri().toString()));
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("获取sys-service实例列表失败，将回退默认地址", ex);
        }
        baseUrlSet.add(DEFAULT_SYS_SERVICE_BASE_URL);
        return new ArrayList<>(baseUrlSet);
    }

    private String normalizeBaseUrl(String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(SYS_SERVICE_CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(SYS_SERVICE_READ_TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    private String currentAuthorization() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        return attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
    }
}
