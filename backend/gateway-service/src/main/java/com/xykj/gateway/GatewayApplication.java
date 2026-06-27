package com.xykj.gateway;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerUriTools;
import org.springframework.cloud.client.loadbalancer.Response;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.cloud.client.loadbalancer.reactive.ReactiveLoadBalancer;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.gateway.config.GatewayLoadBalancerProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ReactiveLoadBalancerClientFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.cloud.gateway.support.DelegatingServiceInstance;
import org.springframework.cloud.gateway.support.NotFoundException;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API 网关服务启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableConfigurationProperties(GatewayApplication.LocalServiceProperties.class)
public class GatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(GatewayApplication.class);

    private static final String GATEWAY_SERVICE_ID_ATTR = "smartfood.gateway.serviceId";

    private static final Pattern SCHEME_PATTERN = Pattern.compile("[a-zA-Z]([a-zA-Z]|\\d|\\+|\\.|-)*:.*");

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
        System.out.println("========================================");
        System.out.println("  Gateway Service Started Successfully!");
        System.out.println("========================================");
    }

    /**
     * 为第三方接入管理提供显式网关路由，避免仅依赖外部配置时出现接口未放行。
     */
    @Bean
    public RouteLocator integrationManagementRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("integration-management-route",
                route -> route.path("/api/v1/integration/**")
                    .uri("lb://smartfood_sys_service"))
            .build();
    }

    /**
     * 显式兜底网关 HTTP 响应超时，避免远程配置缺失/覆盖时长耗时导入请求被过早 504 截断。
     */
    @Bean
    public HttpClientCustomizer gatewayHttpClientCustomizer() {
        return httpClient -> httpClient.responseTimeout(Duration.ofSeconds(70));
    }

    /**
     * 自定义 RouteToRequestUrlFilter，兼容下划线 serviceId 的 lb:// 路由。
     */
    @Bean
    public GlobalFilter underscoreCompatibleRouteToRequestUrlFilter() {
        return orderedGlobalFilter((exchange, chain) -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (route == null) {
                return chain.filter(exchange);
            }

            URI requestUri = exchange.getRequest().getURI();
            boolean encoded = ServerWebExchangeUtils.containsEncodedParts(requestUri);
            URI routeUri = route.getUri();

            if (hasAnotherScheme(routeUri)) {
                exchange.getAttributes().put(
                    ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR,
                    routeUri.getScheme()
                );
                routeUri = URI.create(routeUri.getSchemeSpecificPart());
            }

            String scheme = routeUri.getScheme();
            if (!"lb".equalsIgnoreCase(scheme)) {
                URI mergedUri = UriComponentsBuilder.fromUri(requestUri)
                    .scheme(routeUri.getScheme())
                    .host(routeUri.getHost())
                    .port(routeUri.getPort())
                    .build(encoded)
                    .toUri();
                exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, mergedUri);
                return chain.filter(exchange);
            }

            String serviceId = routeUri.getHost();
            if (serviceId == null || serviceId.isBlank()) {
                serviceId = extractServiceId(route.getUri());
            }
            if (serviceId == null || serviceId.isBlank()) {
                return Mono.error(new IllegalStateException("Invalid host: " + route.getUri()));
            }

            String placeholderHost = toPlaceholderHost(serviceId);
            URI lbRequestUri = UriComponentsBuilder.fromUri(requestUri)
                .scheme("lb")
                .host(placeholderHost)
                .port(routeUri.getPort())
                .build(encoded)
                .toUri();

            exchange.getAttributes().put(GATEWAY_SERVICE_ID_ATTR, serviceId);
            exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, lbRequestUri);
            return chain.filter(exchange);
        }, RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER);
    }

    /**
     * 自定义 ReactiveLoadBalancerClientFilter，使用原始 serviceId 做实例选择。
     */
    @Bean
    public GlobalFilter underscoreCompatibleReactiveLoadBalancerClientFilter(
        LoadBalancerClientFactory clientFactory,
        GatewayLoadBalancerProperties gatewayLoadBalancerProperties,
        LocalServiceProperties localServiceProperties
    ) {
        return orderedGlobalFilter((exchange, chain) -> {
            URI requestUrl = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR);
            String schemePrefix = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_SCHEME_PREFIX_ATTR);
            if (requestUrl == null) {
                return chain.filter(exchange);
            }

            boolean isLoadBalancedRequest = "lb".equalsIgnoreCase(requestUrl.getScheme())
                || "lb".equalsIgnoreCase(schemePrefix);
            if (!isLoadBalancedRequest) {
                return chain.filter(exchange);
            }

            ServerWebExchangeUtils.addOriginalRequestUrl(exchange, requestUrl);
            String serviceId = exchange.getAttribute(GATEWAY_SERVICE_ID_ATTR);
            if (serviceId == null || serviceId.isBlank()) {
                serviceId = requestUrl.getHost();
            }
            if (serviceId == null || serviceId.isBlank()) {
                return Mono.error(new IllegalStateException("Missing serviceId for load-balanced route: " + requestUrl));
            }
            final String resolvedServiceId = serviceId;
            URI localServiceUri = localServiceProperties.resolve(resolvedServiceId);
            if (shouldPreferLocalService(exchange, localServiceUri)) {
                return routeToLocalOrError(
                    exchange,
                    chain,
                    resolvedServiceId,
                    localServiceUri,
                    gatewayLoadBalancerProperties.isUse404()
                );
            }
            ReactiveLoadBalancer<ServiceInstance> loadBalancer = clientFactory.getInstance(resolvedServiceId);
            if (loadBalancer == null) {
                return routeToLocalOrError(
                    exchange,
                    chain,
                    resolvedServiceId,
                    localServiceUri,
                    gatewayLoadBalancerProperties.isUse404()
                );
            }

            return Mono.from(loadBalancer.choose())
                .flatMap(response -> response != null && response.hasServer()
                    ? handleLoadBalancerResponse(
                        exchange,
                        chain,
                        response,
                        requestUrl,
                        schemePrefix,
                        resolvedServiceId,
                        gatewayLoadBalancerProperties.isUse404()
                    )
                    : routeToLocalOrError(
                        exchange,
                        chain,
                        resolvedServiceId,
                        localServiceUri,
                        gatewayLoadBalancerProperties.isUse404()
                    ))
                .switchIfEmpty(routeToLocalOrError(
                    exchange,
                    chain,
                    resolvedServiceId,
                    localServiceUri,
                    gatewayLoadBalancerProperties.isUse404()
                ));
        }, ReactiveLoadBalancerClientFilter.LOAD_BALANCER_CLIENT_FILTER_ORDER);
    }

    private static Mono<Void> routeToLocalOrError(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        String serviceId,
        URI localServiceUri,
        boolean use404
    ) {
        if (localServiceUri == null) {
            return Mono.error(NotFoundException.create(
                use404,
                "Unable to find instance for " + serviceId
            ));
        }

        log.warn("No discovery instance for service {}, fallback to local uri {}", serviceId, localServiceUri);
        URI routedUri = LoadBalancerUriTools.reconstructURI(
            new StaticServiceInstance(serviceId, localServiceUri),
            exchange.getRequest().getURI()
        );
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, routedUri);
        return chain.filter(exchange);
    }

    private static Mono<Void> handleLoadBalancerResponse(
        ServerWebExchange exchange,
        GatewayFilterChain chain,
        Response<ServiceInstance> response,
        URI requestUrl,
        String schemePrefix,
        String serviceId,
        boolean use404
    ) {
        if (!response.hasServer()) {
            return Mono.error(NotFoundException.create(
                use404,
                "Unable to find instance for " + serviceId
            ));
        }

        ServiceInstance serviceInstance = response.getServer();
        String overrideScheme = serviceInstance.isSecure() ? "https" : "http";
        if (schemePrefix != null) {
            overrideScheme = requestUrl.getScheme();
        }

        DelegatingServiceInstance delegatingServiceInstance =
            new DelegatingServiceInstance(serviceInstance, overrideScheme);
        URI routedUri = LoadBalancerUriTools.reconstructURI(
            delegatingServiceInstance,
            exchange.getRequest().getURI()
        );

        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR, routedUri);
        exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_LOADBALANCER_RESPONSE_ATTR, response);
        return chain.filter(exchange);
    }

    private static boolean hasAnotherScheme(URI uri) {
        return SCHEME_PATTERN.matcher(uri.getSchemeSpecificPart()).matches()
            && uri.getHost() == null
            && uri.getRawPath() == null;
    }

    private static boolean shouldPreferLocalService(ServerWebExchange exchange, URI localServiceUri) {
        if (localServiceUri == null) {
            return false;
        }

        String requestHost = exchange.getRequest().getURI().getHost();
        return "127.0.0.1".equals(requestHost)
            || "localhost".equalsIgnoreCase(requestHost)
            || "::1".equals(requestHost);
    }

    private static String extractServiceId(URI uri) {
        String schemeSpecificPart = uri.getSchemeSpecificPart();
        if (schemeSpecificPart == null || schemeSpecificPart.isBlank()) {
            return null;
        }

        String raw = schemeSpecificPart.startsWith("//")
            ? schemeSpecificPart.substring(2)
            : schemeSpecificPart;
        int slashIndex = raw.indexOf('/');
        return slashIndex >= 0 ? raw.substring(0, slashIndex) : raw;
    }

    private static String toPlaceholderHost(String serviceId) {
        return serviceId.replace('_', '-');
    }

    private static GlobalFilter orderedGlobalFilter(GlobalFilter delegate, int order) {
        return new OrderedGlobalFilter(delegate, order);
    }

    @ConfigurationProperties(prefix = "smartfood.gateway")
    public static class LocalServiceProperties {
        private Map<String, String> services = new HashMap<>();

        public Map<String, String> getServices() {
            return services;
        }

        public void setServices(Map<String, String> services) {
            this.services = services;
        }

        URI resolve(String serviceId) {
            String address = services.get(serviceId);
            if (address == null || address.isBlank()) {
                return null;
            }
            if (address.contains("://")) {
                return URI.create(address);
            }
            return URI.create("http://" + address);
        }
    }

    private static final class StaticServiceInstance implements ServiceInstance {
        private final String serviceId;
        private final URI uri;

        private StaticServiceInstance(String serviceId, URI uri) {
            this.serviceId = serviceId;
            this.uri = uri;
        }

        @Override
        public String getServiceId() {
            return serviceId;
        }

        @Override
        public String getHost() {
            return uri.getHost();
        }

        @Override
        public int getPort() {
            return uri.getPort();
        }

        @Override
        public boolean isSecure() {
            return "https".equalsIgnoreCase(uri.getScheme());
        }

        @Override
        public URI getUri() {
            return uri;
        }

        @Override
        public Map<String, String> getMetadata() {
            return Map.of();
        }
    }

    private record OrderedGlobalFilter(GlobalFilter delegate, int order) implements GlobalFilter, Ordered {
        @Override
        public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
            return delegate.filter(exchange, chain);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
