package com.youngplace.iam.controller;

import com.youngplace.common.api.ApiResponse;
import com.youngplace.iam.config.AuthSecurityProperties;
import com.youngplace.iam.config.NacosDebugProperties;
import com.youngplace.iam.entity.IamAuthAuditLogEntity;
import com.youngplace.iam.error.AuthErrorCode;
import com.youngplace.iam.exception.AuthBusinessException;
import com.youngplace.iam.service.AuthAuditService;
import com.youngplace.iam.service.CaptchaService;
import com.youngplace.iam.service.TokenService;
import com.youngplace.iam.service.UserAccountService;
import io.jsonwebtoken.Claims;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/iam")
public class AuthController {

    private static final String TRACE_HEADER = "X-Trace-Id";

    private final AuthSecurityProperties authSecurityProperties;
    private final UserAccountService userAccountService;
    private final CaptchaService captchaService;
    private final TokenService tokenService;
    private final AuthAuditService authAuditService;
    private final NacosDebugProperties nacosDebugProperties;
    private final Environment environment;

    public AuthController(AuthSecurityProperties authSecurityProperties,
                          UserAccountService userAccountService,
                          CaptchaService captchaService,
                          TokenService tokenService,
                          AuthAuditService authAuditService,
                          NacosDebugProperties nacosDebugProperties,
                          Environment environment) {
        this.authSecurityProperties = authSecurityProperties;
        this.userAccountService = userAccountService;
        this.captchaService = captchaService;
        this.tokenService = tokenService;
        this.authAuditService = authAuditService;
        this.nacosDebugProperties = nacosDebugProperties;
        this.environment = environment;
    }

    @GetMapping("/captcha")
    public ApiResponse<Map<String, Object>> captcha() {
        if (!authSecurityProperties.isCaptchaEnabled()) {
            throw new AuthBusinessException(AuthErrorCode.CAPTCHA_DISABLED);
        }
        CaptchaService.CaptchaChallenge challenge = captchaService.createCaptcha();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("captchaId", challenge.getCaptchaId());
        payload.put("imageBase64", challenge.getImageBase64());
        payload.put("expireSeconds", challenge.getExpireSeconds());
        if (StringUtils.hasText(challenge.getCaptchaCode())) {
            payload.put("captchaCode", challenge.getCaptchaCode());
        }
        return ApiResponse.success(payload);
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {
        String traceId = resolveTraceId(traceIdHeader);
        if (authSecurityProperties.isCaptchaEnabled()) {
            boolean captchaOk = captchaService.verifyAndConsume(request.getCaptchaId(), request.getCaptchaCode());
            if (!captchaOk) {
                authAuditService.record(AuthAuditService.LOGIN_FAIL, request.getUsername(), traceId, "captcha invalid");
                throw new AuthBusinessException(AuthErrorCode.CAPTCHA_INVALID);
            }
        }

        UserAccountService.UserAccount account = userAccountService.findByUsername(request.getUsername());
        if (account == null) {
            authAuditService.record(AuthAuditService.LOGIN_FAIL, request.getUsername(), traceId, "account not found");
            throw new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        if (!account.isEnabled()) {
            authAuditService.record(AuthAuditService.LOGIN_FAIL, account.getUsername(), traceId, "account disabled");
            throw new AuthBusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        }
        if (userAccountService.isLocked(account)) {
            long remain = userAccountService.lockRemainingSeconds(account);
            authAuditService.record(AuthAuditService.LOGIN_FAIL, account.getUsername(), traceId, "account locked");
            throw new AuthBusinessException(AuthErrorCode.ACCOUNT_LOCKED, "account is locked, retry after " + remain + " seconds");
        }
        if (!userAccountService.matchesPassword(account, request.getPassword())) {
            userAccountService.recordLoginFailure(account);
            UserAccountService.UserAccount latestAccount = userAccountService.findByUsername(account.getUsername());
            if (latestAccount != null && userAccountService.isLocked(latestAccount)) {
                long remain = userAccountService.lockRemainingSeconds(latestAccount);
                authAuditService.record(AuthAuditService.LOGIN_FAIL, account.getUsername(), traceId, "password error and account locked");
                throw new AuthBusinessException(AuthErrorCode.ACCOUNT_LOCKED,
                        "account is locked, retry after " + remain + " seconds");
            }
            authAuditService.record(AuthAuditService.LOGIN_FAIL, account.getUsername(), traceId, "password mismatch");
            throw new AuthBusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        userAccountService.recordLoginSuccess(account);
        TokenService.TokenPair pair = tokenService.issueTokens(
                account.getUsername(), account.getRoles(), account.getTokenVersion());

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.putAll(pair.toMap());
        payload.put("username", account.getUsername());
        payload.put("roles", account.getRoles());
        payload.put("tokenVersion", account.getTokenVersion());

        authAuditService.record(AuthAuditService.LOGIN_SUCCESS, account.getUsername(), traceId, "login success");
        return ApiResponse.success(payload);
    }

    @PostMapping("/token/refresh")
    public ApiResponse<Map<String, Object>> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {
        String traceId = resolveTraceId(traceIdHeader);
        TokenService.RefreshResult refreshResult = tokenService.refresh(
                request.getRefreshToken(), userAccountService, authSecurityProperties.isRefreshRotate());
        if (!refreshResult.isSuccess()) {
            AuthErrorCode errorCode = refreshResult.getErrorCode() == null
                    ? AuthErrorCode.REFRESH_TOKEN_INVALID
                    : refreshResult.getErrorCode();
            authAuditService.record(AuthAuditService.REFRESH_FAIL, null, traceId, refreshResult.getMessage());
            throw new AuthBusinessException(errorCode, refreshResult.getMessage());
        }
        TokenService.TokenPair pair = refreshResult.getTokenPair();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.putAll(pair.toMap());
        String username = null;
        try {
            Claims claims = tokenService.parseValidAccessToken(pair.getAccessToken());
            username = claims.getSubject();
        } catch (Exception ignore) {
            // Keep refresh flow successful even if parsing for audit failed.
        }
        authAuditService.record(AuthAuditService.REFRESH_SUCCESS, username, traceId, "refresh token success");
        return ApiResponse.success(payload);
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request,
            @RequestHeader(value = TRACE_HEADER, required = false) String traceIdHeader) {
        String traceId = resolveTraceId(traceIdHeader);
        String accessToken = normalizeBearerToken(authorizationHeader);
        String refreshToken = request == null ? null : request.getRefreshToken();
        boolean logoutAll = request != null && Boolean.TRUE.equals(request.getLogoutAll());

        TokenService.LogoutResult logoutResult = tokenService.logout(accessToken, refreshToken);
        int revokedSessions = 0;
        if (logoutAll && StringUtils.hasText(logoutResult.getUsername())) {
            userAccountService.increaseTokenVersion(logoutResult.getUsername());
            revokedSessions = tokenService.revokeAllSessionsByUsername(logoutResult.getUsername());
        }

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("accessRevoked", logoutResult.isAccessRevoked());
        payload.put("refreshRevoked", logoutResult.isRefreshRevoked());
        payload.put("logoutAll", logoutAll);
        payload.put("revokedSessions", revokedSessions);
        payload.put("username", logoutResult.getUsername());
        authAuditService.record(AuthAuditService.LOGOUT, logoutResult.getUsername(), traceId, "logout success");
        return ApiResponse.success(payload);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        String token = normalizeBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new AuthBusinessException(AuthErrorCode.MISSING_AUTHORIZATION);
        }

        Claims claims;
        try {
            claims = tokenService.parseValidAccessToken(token);
        } catch (Exception ex) {
            throw new AuthBusinessException(AuthErrorCode.TOKEN_INVALID);
        }

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("username", claims.getSubject());
        payload.put("roles", claims.get("roles"));
        payload.put("issuer", claims.getIssuer());
        payload.put("expiresAt", claims.getExpiration());
        payload.put("sessionId", claims.get("sid"));
        payload.put("tokenVersion", claims.get("ver"));
        return ApiResponse.success(payload);
    }

    @GetMapping("/config/debug")
    public ApiResponse<Map<String, Object>> configDebug() {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        payload.put("nacosDiscoveryEnabled",
                environment.getProperty("spring.cloud.nacos.discovery.enabled", "false"));
        payload.put("nacosConfigEnabled",
                environment.getProperty("spring.cloud.nacos.config.enabled", "false"));
        payload.put("configSource", nacosDebugProperties.getConfigSource());
        payload.put("authPolicy", nacosDebugProperties.getAuthPolicy());
        payload.put("captchaStrict", nacosDebugProperties.isCaptchaStrict());
        return ApiResponse.success(payload);
    }

    @GetMapping("/audit/logs")
    public ApiResponse<Map<String, Object>> auditLogs(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "messageKeyword", required = false) String messageKeyword,
            @RequestParam(value = "startAt", required = false) Long startAtEpochMillis,
            @RequestParam(value = "endAt", required = false) Long endAtEpochMillis,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        Claims claims = parseAccessClaims(authorizationHeader);
        if (!hasAdminRole(claims.get("roles"))) {
            throw new AuthBusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        Page<IamAuthAuditLogEntity> result = authAuditService.search(
                username, eventType, traceId, messageKeyword, startAtEpochMillis, endAtEpochMillis, page, size);
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (IamAuthAuditLogEntity entity : result.getContent()) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("id", entity.getId());
            item.put("username", entity.getUsername());
            item.put("eventType", entity.getEventType());
            item.put("traceId", entity.getTraceId());
            item.put("message", entity.getMessage());
            item.put("createdAtEpochMillis", entity.getCreatedAtEpochMillis());
            items.add(item);
        }
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("items", items);
        payload.put("page", result.getNumber());
        payload.put("size", result.getSize());
        payload.put("totalElements", result.getTotalElements());
        payload.put("totalPages", result.getTotalPages());
        return ApiResponse.success(payload);
    }

    @GetMapping("/audit/logs/export")
    public ResponseEntity<String> exportAuditLogs(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam(value = "username", required = false) String username,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "traceId", required = false) String traceId,
            @RequestParam(value = "messageKeyword", required = false) String messageKeyword,
            @RequestParam(value = "startAt", required = false) Long startAtEpochMillis,
            @RequestParam(value = "endAt", required = false) Long endAtEpochMillis,
            @RequestParam(value = "limit", defaultValue = "2000") int limit) {
        Claims claims = parseAccessClaims(authorizationHeader);
        if (!hasAdminRole(claims.get("roles"))) {
            throw new AuthBusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        List<IamAuthAuditLogEntity> entries = authAuditService.searchForExport(
                username, eventType, traceId, messageKeyword, startAtEpochMillis, endAtEpochMillis, limit);
        String csv = buildAuditCsv(entries);
        String filename = "iam-audit-" + System.currentTimeMillis() + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private String normalizeBearerToken(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        String value = token.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return value;
    }

    private String resolveTraceId(String incomingTraceId) {
        if (StringUtils.hasText(incomingTraceId)) {
            return incomingTraceId.trim();
        }
        return UUID.randomUUID().toString();
    }

    private Claims parseAccessClaims(String authorizationHeader) {
        String token = normalizeBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            throw new AuthBusinessException(AuthErrorCode.MISSING_AUTHORIZATION);
        }
        try {
            return tokenService.parseValidAccessToken(token);
        } catch (Exception ex) {
            throw new AuthBusinessException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    private boolean hasAdminRole(Object rolesClaim) {
        if (rolesClaim instanceof Collection) {
            for (Object role : (Collection<?>) rolesClaim) {
                if ("ROLE_ADMIN".equals(String.valueOf(role))) {
                    return true;
                }
            }
            return false;
        }
        if (rolesClaim == null) {
            return false;
        }
        String[] roleSegments = String.valueOf(rolesClaim).replace("[", "").replace("]", "").split(",");
        for (String roleSegment : roleSegments) {
            if ("ROLE_ADMIN".equals(roleSegment.trim())) {
                return true;
            }
        }
        return false;
    }

    private String buildAuditCsv(List<IamAuthAuditLogEntity> entries) {
        StringBuilder csv = new StringBuilder();
        csv.append("id,username,eventType,traceId,message,createdAtEpochMillis\n");
        for (IamAuthAuditLogEntity entry : entries) {
            csv.append(csvEscape(entry.getId())).append(",");
            csv.append(csvEscape(entry.getUsername())).append(",");
            csv.append(csvEscape(entry.getEventType())).append(",");
            csv.append(csvEscape(entry.getTraceId())).append(",");
            csv.append(csvEscape(entry.getMessage())).append(",");
            csv.append(csvEscape(entry.getCreatedAtEpochMillis())).append("\n");
        }
        return csv.toString();
    }

    private String csvEscape(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String text = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + text + "\"";
    }

    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;
        private String captchaId;
        private String captchaCode;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getCaptchaCode() {
            return captchaCode;
        }

        public void setCaptchaCode(String captchaCode) {
            this.captchaCode = captchaCode;
        }
    }

    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
    }

    public static class LogoutRequest {
        private String refreshToken;
        private Boolean logoutAll;

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public Boolean getLogoutAll() {
            return logoutAll;
        }

        public void setLogoutAll(Boolean logoutAll) {
            this.logoutAll = logoutAll;
        }
    }
}
