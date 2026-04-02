package com.youngplace.iam.service;

import com.youngplace.iam.config.AuthSecurityProperties;
import com.youngplace.iam.config.JwtProperties;
import com.youngplace.iam.entity.IamRefreshSessionEntity;
import com.youngplace.iam.repository.IamRefreshSessionRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TokenService {

    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties jwtProperties;
    private final AuthSecurityProperties authSecurityProperties;
    private final IamRefreshSessionRepository refreshSessionRepository;

    private final ConcurrentMap<String, Long> blacklistedAccessJti = new ConcurrentHashMap<String, Long>();

    public TokenService(JwtProperties jwtProperties,
                        AuthSecurityProperties authSecurityProperties,
                        IamRefreshSessionRepository refreshSessionRepository) {
        this.jwtProperties = jwtProperties;
        this.authSecurityProperties = authSecurityProperties;
        this.refreshSessionRepository = refreshSessionRepository;
    }

    public TokenPair issueTokens(String username, List<String> roles, int tokenVersion) {
        cleanupExpired();
        Date now = new Date();

        String sessionId = UUID.randomUUID().toString();
        String accessJti = UUID.randomUUID().toString();
        String refreshJti = UUID.randomUUID().toString();

        Date accessExpiration = new Date(now.getTime() + jwtProperties.getExpireSeconds() * 1000L);
        Date refreshExpiration = new Date(now.getTime() + authSecurityProperties.getRefreshExpireSeconds() * 1000L);

        String accessToken = Jwts.builder()
                .setSubject(username)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(accessExpiration)
                .setId(accessJti)
                .claim("typ", TOKEN_TYPE_ACCESS)
                .claim("roles", roles)
                .claim("sid", sessionId)
                .claim("ver", tokenVersion)
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecret())
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(username)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(refreshExpiration)
                .setId(refreshJti)
                .claim("typ", TOKEN_TYPE_REFRESH)
                .claim("sid", sessionId)
                .claim("ver", tokenVersion)
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecret())
                .compact();

        IamRefreshSessionEntity refreshSession = new IamRefreshSessionEntity();
        refreshSession.setSessionId(sessionId);
        refreshSession.setUsername(username);
        refreshSession.setRefreshJti(refreshJti);
        refreshSession.setTokenVersion(tokenVersion);
        refreshSession.setExpireAtEpochMillis(refreshExpiration.getTime());
        refreshSession.setRevoked(false);
        refreshSessionRepository.save(refreshSession);

        TokenPair pair = new TokenPair();
        pair.setAccessToken(accessToken);
        pair.setRefreshToken(refreshToken);
        pair.setExpiresIn(jwtProperties.getExpireSeconds());
        pair.setRefreshExpiresIn(authSecurityProperties.getRefreshExpireSeconds());
        pair.setTokenType("Bearer");
        pair.setSessionId(sessionId);
        return pair;
    }

    public Claims parseValidAccessToken(String token) {
        cleanupExpired();
        Claims claims = parseClaims(token);
        String type = stringClaim(claims, "typ");
        if (!TOKEN_TYPE_ACCESS.equals(type)) {
            throw new IllegalArgumentException("token type is not access");
        }
        String jti = safeText(claims.getId());
        if (StringUtils.hasText(jti) && blacklistedAccessJti.containsKey(jti)) {
            throw new IllegalArgumentException("token has been revoked");
        }
        return claims;
    }

    public RefreshResult refresh(String refreshToken,
                                 UserAccountService userAccountService,
                                 boolean rotateRefreshToken) {
        cleanupExpired();
        Claims claims = parseClaims(refreshToken);
        String type = stringClaim(claims, "typ");
        if (!TOKEN_TYPE_REFRESH.equals(type)) {
            return RefreshResult.failed("refresh token type is invalid");
        }

        String username = claims.getSubject();
        String sessionId = stringClaim(claims, "sid");
        String refreshJti = safeText(claims.getId());
        int tokenVersion = intClaim(claims, "ver");

        IamRefreshSessionEntity session = refreshSessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isRevoked()) {
            return RefreshResult.failed("refresh session is not active");
        }
        if (!safeText(session.getRefreshJti()).equals(refreshJti)) {
            return RefreshResult.failed("refresh token mismatch");
        }
        if (session.getExpireAtEpochMillis() < Instant.now().toEpochMilli()) {
            refreshSessionRepository.deleteById(sessionId);
            return RefreshResult.failed("refresh token is expired");
        }

        UserAccountService.UserAccount account = userAccountService.findByUsername(username);
        if (account == null || !account.isEnabled()) {
            return RefreshResult.failed("user is not available");
        }
        if (userAccountService.isLocked(account)) {
            return RefreshResult.failed("user is locked");
        }
        if (account.getTokenVersion() != tokenVersion || session.getTokenVersion() != tokenVersion) {
            return RefreshResult.failed("token version is outdated");
        }

        if (rotateRefreshToken) {
            session.setRevoked(true);
            refreshSessionRepository.save(session);
            TokenPair pair = issueTokens(account.getUsername(), account.getRoles(), account.getTokenVersion());
            return RefreshResult.success(pair);
        }

        TokenPair pair = issueAccessTokenOnly(account.getUsername(), account.getRoles(), account.getTokenVersion(), sessionId);
        return RefreshResult.success(pair);
    }

    private TokenPair issueAccessTokenOnly(String username, List<String> roles, int tokenVersion, String sessionId) {
        Date now = new Date();
        String accessJti = UUID.randomUUID().toString();
        Date accessExpiration = new Date(now.getTime() + jwtProperties.getExpireSeconds() * 1000L);

        String accessToken = Jwts.builder()
                .setSubject(username)
                .setIssuer(jwtProperties.getIssuer())
                .setIssuedAt(now)
                .setExpiration(accessExpiration)
                .setId(accessJti)
                .claim("typ", TOKEN_TYPE_ACCESS)
                .claim("roles", roles)
                .claim("sid", sessionId)
                .claim("ver", tokenVersion)
                .signWith(SignatureAlgorithm.HS256, jwtProperties.getSecret())
                .compact();

        TokenPair pair = new TokenPair();
        pair.setAccessToken(accessToken);
        pair.setRefreshToken(null);
        pair.setExpiresIn(jwtProperties.getExpireSeconds());
        pair.setRefreshExpiresIn(authSecurityProperties.getRefreshExpireSeconds());
        pair.setTokenType("Bearer");
        pair.setSessionId(sessionId);
        return pair;
    }

    public LogoutResult logout(String accessToken, String refreshToken) {
        cleanupExpired();
        LogoutResult result = new LogoutResult();

        if (StringUtils.hasText(accessToken)) {
            try {
                Claims accessClaims = parseClaims(accessToken);
                if (TOKEN_TYPE_ACCESS.equals(stringClaim(accessClaims, "typ"))) {
                    String jti = safeText(accessClaims.getId());
                    Date expiration = accessClaims.getExpiration();
                    if (StringUtils.hasText(jti) && expiration != null) {
                        blacklistedAccessJti.put(jti, expiration.getTime());
                        result.setAccessRevoked(true);
                        result.setUsername(accessClaims.getSubject());
                    }
                }
            } catch (Exception ignore) {
                // Ignore malformed token for logout endpoint to support idempotent behavior.
            }
        }

        if (StringUtils.hasText(refreshToken)) {
            try {
                Claims refreshClaims = parseClaims(refreshToken);
                if (TOKEN_TYPE_REFRESH.equals(stringClaim(refreshClaims, "typ"))) {
                    String sessionId = stringClaim(refreshClaims, "sid");
                    revokeSession(sessionId);
                    result.setRefreshRevoked(true);
                    if (!StringUtils.hasText(result.getUsername())) {
                        result.setUsername(refreshClaims.getSubject());
                    }
                }
            } catch (Exception ignore) {
                // Ignore malformed token for logout endpoint to support idempotent behavior.
            }
        }
        return result;
    }

    public int revokeAllSessionsByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return 0;
        }
        List<IamRefreshSessionEntity> sessions = refreshSessionRepository.findByUsernameAndRevokedFalse(username);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        for (IamRefreshSessionEntity session : sessions) {
            session.setRevoked(true);
        }
        refreshSessionRepository.saveAll(sessions);
        return sessions.size();
    }

    public void revokeSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        IamRefreshSessionEntity session = refreshSessionRepository.findById(sessionId).orElse(null);
        if (session != null) {
            session.setRevoked(true);
            refreshSessionRepository.save(session);
        }
    }

    private Claims parseClaims(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token is blank");
        }
        return Jwts.parser()
                .setSigningKey(jwtProperties.getSecret())
                .requireIssuer(jwtProperties.getIssuer())
                .parseClaimsJws(token)
                .getBody();
    }

    private void cleanupExpired() {
        long now = Instant.now().toEpochMilli();
        refreshSessionRepository.deleteByExpireAtEpochMillisLessThan(now);
        blacklistedAccessJti.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    private String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private int intClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value == null) {
            return 0;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        private long expiresIn;
        private long refreshExpiresIn;
        private String sessionId;

        public Map<String, Object> toMap() {
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("accessToken", accessToken);
            data.put("refreshToken", refreshToken);
            data.put("tokenType", tokenType);
            data.put("expiresIn", expiresIn);
            data.put("refreshExpiresIn", refreshExpiresIn);
            data.put("sessionId", sessionId);
            // Backward compatible aliases
            data.put("token", accessToken);
            return data;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public long getRefreshExpiresIn() {
            return refreshExpiresIn;
        }

        public void setRefreshExpiresIn(long refreshExpiresIn) {
            this.refreshExpiresIn = refreshExpiresIn;
        }

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }
    }

    public static class RefreshResult {
        private boolean success;
        private String message;
        private TokenPair tokenPair;

        public static RefreshResult success(TokenPair pair) {
            RefreshResult result = new RefreshResult();
            result.success = true;
            result.tokenPair = pair;
            return result;
        }

        public static RefreshResult failed(String message) {
            RefreshResult result = new RefreshResult();
            result.success = false;
            result.message = message;
            return result;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }

        public TokenPair getTokenPair() {
            return tokenPair;
        }
    }

    public static class LogoutResult {
        private boolean accessRevoked;
        private boolean refreshRevoked;
        private String username;

        public boolean isAccessRevoked() {
            return accessRevoked;
        }

        public void setAccessRevoked(boolean accessRevoked) {
            this.accessRevoked = accessRevoked;
        }

        public boolean isRefreshRevoked() {
            return refreshRevoked;
        }

        public void setRefreshRevoked(boolean refreshRevoked) {
            this.refreshRevoked = refreshRevoked;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }
    }

}
