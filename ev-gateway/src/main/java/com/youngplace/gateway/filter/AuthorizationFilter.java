package com.youngplace.gateway.filter;

import com.youngplace.gateway.config.AuthorizationProperties;
import com.youngplace.gateway.config.JwtAuthProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class AuthorizationFilter implements GlobalFilter, Ordered {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AuthorizationProperties authorizationProperties;
    private final JwtAuthProperties jwtAuthProperties;

    public AuthorizationFilter(AuthorizationProperties authorizationProperties,
                               JwtAuthProperties jwtAuthProperties) {
        this.authorizationProperties = authorizationProperties;
        this.jwtAuthProperties = jwtAuthProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!authorizationProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        AuthorizationProperties.Rule rule = findMatchedRule(exchange);
        if (rule == null) {
            return chain.filter(exchange);
        }
        if (rule.getAnyRole() == null || rule.getAnyRole().isEmpty()) {
            return chain.filter(exchange);
        }

        String roleHeaderValue = exchange.getRequest().getHeaders().getFirst(jwtAuthProperties.getHeaderRoles());
        Set<String> requestRoles = parseRoles(roleHeaderValue);
        if (!hasAnyRequiredRole(requestRoles, rule.getAnyRole())) {
            return writeForbidden(exchange, "access denied for current role");
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -90;
    }

    private AuthorizationProperties.Rule findMatchedRule(ServerWebExchange exchange) {
        String path = exchange.getRequest().getURI().getPath();
        HttpMethod method = exchange.getRequest().getMethod();
        List<AuthorizationProperties.Rule> rules = authorizationProperties.getRules();
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (AuthorizationProperties.Rule rule : rules) {
            if (!StringUtils.hasText(rule.getPath()) || !PATH_MATCHER.match(rule.getPath(), path)) {
                continue;
            }
            if (!matchesMethod(rule, method)) {
                continue;
            }
            return rule;
        }
        return null;
    }

    private boolean matchesMethod(AuthorizationProperties.Rule rule, HttpMethod requestMethod) {
        List<String> methods = rule.getMethods();
        if (methods == null || methods.isEmpty() || requestMethod == null) {
            return true;
        }
        String requestMethodName = requestMethod.name();
        for (String method : methods) {
            if (StringUtils.hasText(method) && requestMethodName.equalsIgnoreCase(method.trim())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> parseRoles(String headerValue) {
        Set<String> roles = new LinkedHashSet<String>();
        if (!StringUtils.hasText(headerValue)) {
            return roles;
        }

        String normalized = headerValue.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (!StringUtils.hasText(normalized)) {
            return roles;
        }

        String[] segments = normalized.split(",");
        for (String segment : segments) {
            String role = segment == null ? null : segment.trim();
            if (StringUtils.hasText(role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private boolean hasAnyRequiredRole(Set<String> requestRoles, List<String> requiredRoles) {
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        for (String requiredRole : requiredRoles) {
            if (!StringUtils.hasText(requiredRole)) {
                continue;
            }
            if (requestRoles.contains(requiredRole.trim())) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        String traceHeader = jwtAuthProperties.getTraceHeader();
        String traceId = exchange.getRequest().getHeaders().getFirst(traceHeader);
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString();
        }

        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(traceHeader, traceId);

        String path = safeText(exchange.getRequest().getURI().getPath());
        long timestamp = System.currentTimeMillis();
        String body = "{\"code\":403,\"message\":\"" + escapeJson(message)
                + "\",\"data\":null,\"traceId\":\"" + escapeJson(traceId)
                + "\",\"timestamp\":" + timestamp
                + ",\"path\":\"" + escapeJson(path) + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
