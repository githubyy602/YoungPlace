package com.youngplace.iam.controller;

import com.youngplace.common.api.ApiResponse;
import com.youngplace.iam.config.AuthSecurityProperties;
import com.youngplace.iam.config.NacosDebugProperties;
import com.youngplace.iam.service.CaptchaService;
import com.youngplace.iam.service.TokenService;
import com.youngplace.iam.service.UserAccountService;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpHeaders;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/iam")
public class AuthController {

    private final AuthSecurityProperties authSecurityProperties;
    private final UserAccountService userAccountService;
    private final CaptchaService captchaService;
    private final TokenService tokenService;
    private final NacosDebugProperties nacosDebugProperties;
    private final Environment environment;

    public AuthController(AuthSecurityProperties authSecurityProperties,
                          UserAccountService userAccountService,
                          CaptchaService captchaService,
                          TokenService tokenService,
                          NacosDebugProperties nacosDebugProperties,
                          Environment environment) {
        this.authSecurityProperties = authSecurityProperties;
        this.userAccountService = userAccountService;
        this.captchaService = captchaService;
        this.tokenService = tokenService;
        this.nacosDebugProperties = nacosDebugProperties;
        this.environment = environment;
    }

    @GetMapping("/captcha")
    public ApiResponse<Map<String, Object>> captcha() {
        if (!authSecurityProperties.isCaptchaEnabled()) {
            return ApiResponse.fail(400, "captcha is disabled");
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
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        if (authSecurityProperties.isCaptchaEnabled()) {
            boolean captchaOk = captchaService.verifyAndConsume(request.getCaptchaId(), request.getCaptchaCode());
            if (!captchaOk) {
                return ApiResponse.fail(401, "captcha is invalid or expired");
            }
        }

        UserAccountService.UserAccount account = userAccountService.findByUsername(request.getUsername());
        if (account == null) {
            return ApiResponse.fail(401, "username or password is invalid");
        }
        if (!account.isEnabled()) {
            return ApiResponse.fail(403, "account is disabled");
        }
        if (userAccountService.isLocked(account)) {
            return ApiResponse.fail(423, "account is locked, retry after "
                    + userAccountService.lockRemainingSeconds(account) + " seconds");
        }
        if (!userAccountService.matchesPassword(account, request.getPassword())) {
            userAccountService.recordLoginFailure(account);
            if (userAccountService.isLocked(account)) {
                return ApiResponse.fail(423, "account is locked, retry after "
                        + userAccountService.lockRemainingSeconds(account) + " seconds");
            }
            return ApiResponse.fail(401, "username or password is invalid");
        }

        userAccountService.recordLoginSuccess(account);
        TokenService.TokenPair pair = tokenService.issueTokens(
                account.getUsername(), account.getRoles(), account.getTokenVersion());

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.putAll(pair.toMap());
        payload.put("username", account.getUsername());
        payload.put("roles", account.getRoles());
        payload.put("tokenVersion", account.getTokenVersion());

        return ApiResponse.success(payload);
    }

    @PostMapping("/token/refresh")
    public ApiResponse<Map<String, Object>> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        TokenService.RefreshResult refreshResult = tokenService.refresh(
                request.getRefreshToken(), userAccountService, authSecurityProperties.isRefreshRotate());
        if (!refreshResult.isSuccess()) {
            return ApiResponse.fail(401, refreshResult.getMessage());
        }
        TokenService.TokenPair pair = refreshResult.getTokenPair();
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.putAll(pair.toMap());
        return ApiResponse.success(payload);
    }

    @PostMapping("/logout")
    public ApiResponse<Map<String, Object>> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody(required = false) LogoutRequest request) {
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
        return ApiResponse.success(payload);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        String token = normalizeBearerToken(authorizationHeader);
        if (!StringUtils.hasText(token)) {
            return ApiResponse.fail(401, "missing Authorization Bearer token");
        }

        Claims claims;
        try {
            claims = tokenService.parseValidAccessToken(token);
        } catch (Exception ex) {
            return ApiResponse.fail(401, "invalid or expired token");
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
