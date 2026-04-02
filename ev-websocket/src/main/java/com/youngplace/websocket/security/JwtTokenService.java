package com.youngplace.websocket.security;

import com.corundumstudio.socketio.HandshakeData;
import com.youngplace.websocket.config.SocketIoProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtTokenService {

    private final SocketIoProperties properties;

    public JwtTokenService(SocketIoProperties properties) {
        this.properties = properties;
    }

    public String resolveToken(HandshakeData handshakeData) {
        String token = handshakeData.getSingleUrlParam("token");
        if (!StringUtils.hasText(token)) {
            token = handshakeData.getSingleUrlParam("access_token");
        }
        if (!StringUtils.hasText(token)) {
            token = handshakeData.getSingleUrlParam("authorization");
        }
        return normalizeBearerToken(token);
    }

    public String parseSubject(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .setSigningKey(properties.getJwtSecret())
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (Exception ex) {
            return null;
        }
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
}
