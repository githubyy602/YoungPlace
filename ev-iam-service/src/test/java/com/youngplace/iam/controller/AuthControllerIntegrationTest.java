package com.youngplace.iam.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youngplace.iam.entity.IamUserEntity;
import com.youngplace.iam.entity.IamRefreshSessionEntity;
import com.youngplace.iam.repository.IamAuthAuditLogRepository;
import com.youngplace.iam.repository.IamRefreshSessionRepository;
import com.youngplace.iam.repository.IamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IamUserRepository iamUserRepository;

    @Autowired
    private IamRefreshSessionRepository refreshSessionRepository;

    @Autowired
    private IamAuthAuditLogRepository authAuditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetState() {
        IamUserEntity admin = iamUserRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin user should exist"));
        admin.setEnabled(true);
        admin.setFailedLoginCount(0);
        admin.setLockedUntilEpochMillis(0L);
        admin.setTokenVersion(1);
        iamUserRepository.save(admin);
        refreshSessionRepository.deleteAll();
        authAuditLogRepository.deleteAll();
        ensureGuestUser();
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        CaptchaChallenge captcha = getCaptchaChallenge();

        mockMvc.perform(post("/api/iam/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\",\"captchaId\":\""
                                + captcha.captchaId + "\",\"captchaCode\":\"" + captcha.captchaCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    void shouldLockAccountAfterPasswordFailures() throws Exception {
        for (int i = 1; i <= 4; i++) {
            CaptchaChallenge captcha = getCaptchaChallenge();
            mockMvc.perform(post("/api/iam/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin\",\"password\":\"bad-password\",\"captchaId\":\""
                                    + captcha.captchaId + "\",\"captchaCode\":\"" + captcha.captchaCode + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(40121));
        }

        CaptchaChallenge lastCaptcha = getCaptchaChallenge();
        mockMvc.perform(post("/api/iam/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"bad-password\",\"captchaId\":\""
                                + lastCaptcha.captchaId + "\",\"captchaCode\":\"" + lastCaptcha.captchaCode + "\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value(42311));
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectRefreshAfterLogout() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();

        mockMvc.perform(post("/api/iam/logout")
                        .header("Authorization", "Bearer " + tokenPair.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\",\"logoutAll\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
    }

    @Test
    void shouldRejectRefreshWhenUsingAccessToken() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();
        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.accessToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
    }

    @Test
    void shouldRejectRefreshWhenTokenVersionOutdated() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();
        IamUserEntity admin = iamUserRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalStateException("admin user should exist"));
        admin.setTokenVersion(admin.getTokenVersion() + 1);
        iamUserRepository.save(admin);

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
    }

    @Test
    void shouldQueryAuditLogsByAdmin() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();
        mockMvc.perform(get("/api/iam/audit/logs")
                        .param("eventType", "LOGIN_SUCCESS")
                        .param("page", "0")
                        .param("size", "5")
                        .header("Authorization", "Bearer " + tokenPair.accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.totalElements").isNumber());
    }

    @Test
    void shouldRejectAuditLogQueryForNonAdmin() throws Exception {
        TokenPair tokenPair = loginAndGetTokens("viewer", "viewer123");
        mockMvc.perform(get("/api/iam/audit/logs")
                        .header("Authorization", "Bearer " + tokenPair.accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40321));
    }

    @Test
    void shouldSupportAuditSearchByTraceIdAndMessageKeyword() throws Exception {
        String traceId = "trace-audit-keyword-" + System.currentTimeMillis();
        CaptchaChallenge captcha = getCaptchaChallenge();
        mockMvc.perform(post("/api/iam/login")
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\",\"captchaId\":\""
                                + captcha.captchaId + "\",\"captchaCode\":\"" + captcha.captchaCode + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40121));

        TokenPair adminToken = loginAndGetTokens();
        mockMvc.perform(get("/api/iam/audit/logs")
                        .header("Authorization", "Bearer " + adminToken.accessToken)
                        .param("traceId", traceId)
                        .param("messageKeyword", "password")
                        .param("eventType", "LOGIN_FAIL")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].traceId").value(traceId));
    }

    @Test
    void shouldInvalidateOldRefreshTokenAfterRotation() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();
        MvcResult refreshResult = mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode refreshJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String rotatedRefreshToken = refreshJson.path("data").path("refreshToken").asText();

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRevokeAllSessionsOnLogoutAll() throws Exception {
        TokenPair tokenPair1 = loginAndGetTokens();
        TokenPair tokenPair2 = loginAndGetTokens();

        mockMvc.perform(post("/api/iam/logout")
                        .header("Authorization", "Bearer " + tokenPair1.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair1.refreshToken + "\",\"logoutAll\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logoutAll").value(true));

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair1.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair2.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
    }

    @Test
    void shouldKeepOtherSessionAvailableWhenSingleLogout() throws Exception {
        TokenPair tokenPair1 = loginAndGetTokens();
        TokenPair tokenPair2 = loginAndGetTokens();

        mockMvc.perform(post("/api/iam/logout")
                        .header("Authorization", "Bearer " + tokenPair1.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair1.refreshToken + "\",\"logoutAll\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.logoutAll").value(false));

        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair1.refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40141));
        mockMvc.perform(post("/api/iam/token/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + tokenPair2.refreshToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void shouldRejectAuditLogQueryWhenAuthorizationMissing() throws Exception {
        mockMvc.perform(get("/api/iam/audit/logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40131));
    }

    @Test
    void shouldRejectAuditLogQueryWhenTokenMalformed() throws Exception {
        mockMvc.perform(get("/api/iam/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer malformed-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40132));
    }

    @Test
    void shouldRejectAuditExportForNonAdmin() throws Exception {
        TokenPair tokenPair = loginAndGetTokens("viewer", "viewer123");
        mockMvc.perform(get("/api/iam/audit/logs/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenPair.accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40321));
    }

    @Test
    void shouldExportAuditLogsAsCsv() throws Exception {
        String traceId = "trace-export-" + System.currentTimeMillis();
        CaptchaChallenge captcha = getCaptchaChallenge();
        mockMvc.perform(post("/api/iam/login")
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong-password\",\"captchaId\":\""
                                + captcha.captchaId + "\",\"captchaCode\":\"" + captcha.captchaCode + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40121));

        TokenPair adminToken = loginAndGetTokens();
        mockMvc.perform(get("/api/iam/audit/logs/export")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken.accessToken)
                        .param("traceId", traceId)
                        .param("messageKeyword", "password"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("iam-audit-")))
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string(containsString("id,username,eventType,traceId,message,createdAtEpochMillis")))
                .andExpect(content().string(containsString(traceId)));
    }

    @Test
    void shouldAllowOnlyOneSuccessWhenRefreshingConcurrently() throws Exception {
        TokenPair tokenPair = loginAndGetTokens();
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<RefreshAttempt>> futures = new ArrayList<Future<RefreshAttempt>>();
            futures.add(executor.submit(refreshTask(tokenPair.refreshToken, startLatch)));
            futures.add(executor.submit(refreshTask(tokenPair.refreshToken, startLatch)));
            startLatch.countDown();

            List<RefreshAttempt> attempts = new ArrayList<RefreshAttempt>();
            for (Future<RefreshAttempt> future : futures) {
                attempts.add(future.get(10, TimeUnit.SECONDS));
            }

            int successCount = 0;
            int invalidCount = 0;
            String rotatedRefreshToken = null;
            for (RefreshAttempt attempt : attempts) {
                if (attempt.httpStatus == 200 && attempt.code == 0) {
                    successCount++;
                    rotatedRefreshToken = attempt.refreshToken;
                } else if (attempt.httpStatus == 401 && attempt.code == 40141) {
                    invalidCount++;
                }
            }
            assertEquals(1, successCount, "exactly one concurrent refresh should succeed");
            assertEquals(1, invalidCount, "one concurrent refresh should fail with invalid refresh token");
            assertFalse(rotatedRefreshToken == null || rotatedRefreshToken.isEmpty(),
                    "rotated refresh token should be returned by successful request");

            mockMvc.perform(post("/api/iam/token/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + rotatedRefreshToken + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        } finally {
            executor.shutdownNow();
        }
    }

    private CaptchaChallenge getCaptchaChallenge() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/iam/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
        String captchaId = jsonNode.path("data").path("captchaId").asText();
        String captchaCode = jsonNode.path("data").path("captchaCode").asText();
        return new CaptchaChallenge(captchaId, captchaCode);
    }

    private TokenPair loginAndGetTokens() throws Exception {
        return loginAndGetTokens("admin", "123456");
    }

    private TokenPair loginAndGetTokens(String username, String password) throws Exception {
        CaptchaChallenge captcha = getCaptchaChallenge();
        MvcResult loginResult = mockMvc.perform(post("/api/iam/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\",\"captchaId\":\""
                                + captcha.captchaId + "\",\"captchaCode\":\"" + captcha.captchaCode + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode jsonNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = jsonNode.path("data").path("accessToken").asText();
        String refreshToken = jsonNode.path("data").path("refreshToken").asText();
        return new TokenPair(accessToken, refreshToken);
    }

    private void ensureGuestUser() {
        IamUserEntity viewer = iamUserRepository.findByUsername("viewer").orElse(null);
        if (viewer == null) {
            viewer = new IamUserEntity();
            viewer.setUsername("viewer");
            viewer.setPasswordHash(passwordEncoder.encode("viewer123"));
            viewer.setRoles("ROLE_USER");
        }
        viewer.setEnabled(true);
        viewer.setFailedLoginCount(0);
        viewer.setLockedUntilEpochMillis(0L);
        viewer.setTokenVersion(1);
        iamUserRepository.save(viewer);
        for (IamRefreshSessionEntity session : refreshSessionRepository.findByUsernameAndRevokedFalse("viewer")) {
            session.setRevoked(true);
            refreshSessionRepository.save(session);
        }
    }

    private Callable<RefreshAttempt> refreshTask(String refreshToken, CountDownLatch startLatch) {
        return () -> {
            startLatch.await(5, TimeUnit.SECONDS);
            MvcResult result = mockMvc.perform(post("/api/iam/token/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                    .andReturn();
            int httpStatus = result.getResponse().getStatus();
            JsonNode jsonNode = objectMapper.readTree(result.getResponse().getContentAsString());
            int code = jsonNode.path("code").asInt();
            String nextRefreshToken = jsonNode.path("data").path("refreshToken").asText();
            return new RefreshAttempt(httpStatus, code, nextRefreshToken);
        };
    }

    private static class CaptchaChallenge {
        private final String captchaId;
        private final String captchaCode;

        private CaptchaChallenge(String captchaId, String captchaCode) {
            this.captchaId = captchaId;
            this.captchaCode = captchaCode;
        }
    }

    private static class TokenPair {
        private final String accessToken;
        private final String refreshToken;

        private TokenPair(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }
    }

    private static class RefreshAttempt {
        private final int httpStatus;
        private final int code;
        private final String refreshToken;

        private RefreshAttempt(int httpStatus, int code, String refreshToken) {
            this.httpStatus = httpStatus;
            this.code = code;
            this.refreshToken = refreshToken;
        }
    }
}
