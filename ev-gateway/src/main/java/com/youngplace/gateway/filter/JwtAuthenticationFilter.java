package com.youngplace.gateway.filter;

import com.youngplace.gateway.config.JwtAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtAuthProperties properties;

    public JwtAuthenticationFilter(JwtAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String traceId = resolveOrCreateTraceId(exchange.getRequest().getHeaders().getFirst(properties.getTraceHeader()));
        ServerWebExchange tracedExchange = withTraceId(exchange, traceId);

        String path = tracedExchange.getRequest().getURI().getPath();
        if (!properties.isEnabled() || !requiresAuthentication(path)) {
            return chain.filter(tracedExchange);
        }

        String token = resolveBearerToken(tracedExchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token)) {
            return writeUnauthorized(tracedExchange, traceId, "missing Authorization Bearer token");
        }

        Claims claims;
        try {
            JwtParser parser = Jwts.parser()
                    .setSigningKey(properties.getSecret())
                    .setAllowedClockSkewSeconds(properties.getClockSkewSeconds());
            if (StringUtils.hasText(properties.getIssuer())) {
                parser = parser.requireIssuer(properties.getIssuer());
            }
            claims = parser.parseClaimsJws(token).getBody();
        } catch (Exception ex) {
            return writeUnauthorized(tracedExchange, traceId, "invalid or expired token");
        }

        if (!StringUtils.hasText(claims.getSubject())) {
            return writeUnauthorized(tracedExchange, traceId, "token subject is missing");
        }
        if (properties.isEnforceTokenType()) {
            String tokenType = safeText(claims.get("typ") == null ? null : String.valueOf(claims.get("typ")));
            if (!StringUtils.hasText(tokenType)
                    || !properties.getRequiredTokenType().equalsIgnoreCase(tokenType)) {
                return writeUnauthorized(tracedExchange, traceId, "token type is not allowed");
            }
        }

        String roles = stringifyRoles(claims.get("roles"));
        ServerHttpRequest request = tracedExchange.getRequest().mutate()
                .header(properties.getHeaderUser(), claims.getSubject())
                .header(properties.getHeaderRoles(), roles)
                .header(properties.getHeaderTokenIssuer(), safeText(claims.getIssuer()))
                .build();
        return chain.filter(tracedExchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private boolean requiresAuthentication(String path) {
        if (!StringUtils.hasText(path) || !path.startsWith("/api/")) {
            return false;
        }
        if (properties.getExcludePaths() == null) {
            return true;
        }
        for (String excludePath : properties.getExcludePaths()) {
            String normalizedExcludePath = normalizeExcludePath(excludePath);
            if (StringUtils.hasText(normalizedExcludePath) && path.startsWith(normalizedExcludePath)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeExcludePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        String value = path.trim();
        if (value.endsWith("/**")) {
            return value.substring(0, value.length() - 3);
        }
        if (value.endsWith("*")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String resolveBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return null;
    }

    private String stringifyRoles(Object rolesObject) {
        if (rolesObject == null) {
            return "";
        }
        return rolesObject.toString();
    }

    private ServerWebExchange withTraceId(ServerWebExchange exchange, String traceId) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .header(properties.getTraceHeader(), traceId)
                .build();
        exchange.getResponse().getHeaders().set(properties.getTraceHeader(), traceId);
        return exchange.mutate().request(request).build();
    }

    private String resolveOrCreateTraceId(String incomingTraceId) {
        if (StringUtils.hasText(incomingTraceId)) {
            return incomingTraceId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private Mono<Void> writeUnauthorized(ServerWebExchange exchange, String traceId, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(properties.getTraceHeader(), traceId);
        String path = safeText(exchange.getRequest().getURI().getPath());
        long timestamp = System.currentTimeMillis();
        String body = "{\"code\":401,\"message\":\"" + escapeJson(message)
                + "\",\"data\":null,\"traceId\":\"" + escapeJson(traceId)
                + "\",\"timestamp\":" + timestamp
                + ",\"path\":\"" + escapeJson(path) + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String safeText(String value) {
        if (value == null) {
            return "";
        }
        return value;
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
