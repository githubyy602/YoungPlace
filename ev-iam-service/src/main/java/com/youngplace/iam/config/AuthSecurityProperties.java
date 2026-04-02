package com.youngplace.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.auth")
public class AuthSecurityProperties {

    private boolean captchaEnabled = true;
    private int captchaLength = 4;
    private long captchaExpireSeconds = 120L;
    private boolean debugReturnCaptchaCode = true;

    private int loginMaxFailures = 5;
    private long lockMinutes = 15L;

    private long refreshExpireSeconds = 604800L;
    private boolean refreshRotate = true;

    public boolean isCaptchaEnabled() {
        return captchaEnabled;
    }

    public void setCaptchaEnabled(boolean captchaEnabled) {
        this.captchaEnabled = captchaEnabled;
    }

    public int getCaptchaLength() {
        return captchaLength;
    }

    public void setCaptchaLength(int captchaLength) {
        this.captchaLength = captchaLength;
    }

    public long getCaptchaExpireSeconds() {
        return captchaExpireSeconds;
    }

    public void setCaptchaExpireSeconds(long captchaExpireSeconds) {
        this.captchaExpireSeconds = captchaExpireSeconds;
    }

    public boolean isDebugReturnCaptchaCode() {
        return debugReturnCaptchaCode;
    }

    public void setDebugReturnCaptchaCode(boolean debugReturnCaptchaCode) {
        this.debugReturnCaptchaCode = debugReturnCaptchaCode;
    }

    public int getLoginMaxFailures() {
        return loginMaxFailures;
    }

    public void setLoginMaxFailures(int loginMaxFailures) {
        this.loginMaxFailures = loginMaxFailures;
    }

    public long getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(long lockMinutes) {
        this.lockMinutes = lockMinutes;
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireSeconds;
    }

    public void setRefreshExpireSeconds(long refreshExpireSeconds) {
        this.refreshExpireSeconds = refreshExpireSeconds;
    }

    public boolean isRefreshRotate() {
        return refreshRotate;
    }

    public void setRefreshRotate(boolean refreshRotate) {
        this.refreshRotate = refreshRotate;
    }
}
