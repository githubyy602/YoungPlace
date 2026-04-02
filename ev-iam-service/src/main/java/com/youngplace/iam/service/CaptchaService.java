package com.youngplace.iam.service;

import com.youngplace.iam.config.AuthSecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaService {

    private static final String DIGITS = "23456789";

    private final AuthSecurityProperties authSecurityProperties;
    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<String, CaptchaEntry>();
    private final Random random = new Random();

    public CaptchaService(AuthSecurityProperties authSecurityProperties) {
        this.authSecurityProperties = authSecurityProperties;
    }

    public CaptchaChallenge createCaptcha() {
        cleanupExpired();
        String captchaId = UUID.randomUUID().toString();
        String code = generateCode(Math.max(4, authSecurityProperties.getCaptchaLength()));
        long expireAt = Instant.now().toEpochMilli() + authSecurityProperties.getCaptchaExpireSeconds() * 1000L;
        captchas.put(captchaId, new CaptchaEntry(code, expireAt));

        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='42'>"
                + "<rect width='120' height='42' fill='#f5f7fa'/>"
                + "<text x='16' y='29' font-size='24' fill='#334155' letter-spacing='4'>" + code + "</text>"
                + "</svg>";
        String imageBase64 = "data:image/svg+xml;base64,"
                + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

        CaptchaChallenge challenge = new CaptchaChallenge();
        challenge.setCaptchaId(captchaId);
        challenge.setImageBase64(imageBase64);
        challenge.setExpireSeconds(authSecurityProperties.getCaptchaExpireSeconds());
        if (authSecurityProperties.isDebugReturnCaptchaCode()) {
            challenge.setCaptchaCode(code);
        }
        return challenge;
    }

    public boolean verifyAndConsume(String captchaId, String captchaCode) {
        cleanupExpired();
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            return false;
        }
        CaptchaEntry entry = captchas.remove(captchaId);
        if (entry == null || entry.getExpireAtEpochMillis() < Instant.now().toEpochMilli()) {
            return false;
        }
        return entry.getCode().equalsIgnoreCase(captchaCode.trim());
    }

    private void cleanupExpired() {
        long now = Instant.now().toEpochMilli();
        captchas.entrySet().removeIf(e -> e.getValue().getExpireAtEpochMillis() < now);
    }

    private String generateCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = random.nextInt(DIGITS.length());
            builder.append(DIGITS.charAt(index));
        }
        return builder.toString();
    }

    private static class CaptchaEntry {
        private final String code;
        private final long expireAtEpochMillis;

        private CaptchaEntry(String code, long expireAtEpochMillis) {
            this.code = code;
            this.expireAtEpochMillis = expireAtEpochMillis;
        }

        public String getCode() {
            return code;
        }

        public long getExpireAtEpochMillis() {
            return expireAtEpochMillis;
        }
    }

    public static class CaptchaChallenge {
        private String captchaId;
        private String imageBase64;
        private long expireSeconds;
        private String captchaCode;

        public String getCaptchaId() {
            return captchaId;
        }

        public void setCaptchaId(String captchaId) {
            this.captchaId = captchaId;
        }

        public String getImageBase64() {
            return imageBase64;
        }

        public void setImageBase64(String imageBase64) {
            this.imageBase64 = imageBase64;
        }

        public long getExpireSeconds() {
            return expireSeconds;
        }

        public void setExpireSeconds(long expireSeconds) {
            this.expireSeconds = expireSeconds;
        }

        public String getCaptchaCode() {
            return captchaCode;
        }

        public void setCaptchaCode(String captchaCode) {
            this.captchaCode = captchaCode;
        }
    }
}
