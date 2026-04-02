package com.youngplace.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security.jwt")
public class JwtAuthProperties {

    private boolean enabled = true;
    private String secret = "ev-jwt-demo-secret";
    private String issuer = "iam-service";
    private long clockSkewSeconds = 30L;
    private String headerUser = "X-Auth-User";
    private String headerRoles = "X-Auth-Roles";
    private String headerTokenIssuer = "X-Auth-Token-Issuer";
    private String traceHeader = "X-Trace-Id";
    private boolean enforceTokenType = true;
    private String requiredTokenType = "access";
    private List<String> excludePaths = new ArrayList<String>(Arrays.asList(
            "/api/iam/login",
            "/api/iam/captcha",
            "/api/iam/token/refresh",
            "/api/iam/me",
            "/socket.io",
            "/actuator"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public long getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(long clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }

    public String getHeaderUser() {
        return headerUser;
    }

    public void setHeaderUser(String headerUser) {
        this.headerUser = headerUser;
    }

    public String getHeaderRoles() {
        return headerRoles;
    }

    public void setHeaderRoles(String headerRoles) {
        this.headerRoles = headerRoles;
    }

    public String getHeaderTokenIssuer() {
        return headerTokenIssuer;
    }

    public void setHeaderTokenIssuer(String headerTokenIssuer) {
        this.headerTokenIssuer = headerTokenIssuer;
    }

    public String getTraceHeader() {
        return traceHeader;
    }

    public void setTraceHeader(String traceHeader) {
        this.traceHeader = traceHeader;
    }

    public boolean isEnforceTokenType() {
        return enforceTokenType;
    }

    public void setEnforceTokenType(boolean enforceTokenType) {
        this.enforceTokenType = enforceTokenType;
    }

    public String getRequiredTokenType() {
        return requiredTokenType;
    }

    public void setRequiredTokenType(String requiredTokenType) {
        this.requiredTokenType = requiredTokenType;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
