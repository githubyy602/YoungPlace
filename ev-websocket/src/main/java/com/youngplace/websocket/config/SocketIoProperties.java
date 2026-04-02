package com.youngplace.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "socketio")
public class SocketIoProperties {

    private String host = "0.0.0.0";
    private int port = 9100;
    private String origin = "*";
    private int pingIntervalMs = 25000;
    private int pingTimeoutMs = 60000;
    private String jwtSecret = "ev-jwt-demo-secret";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public int getPingIntervalMs() {
        return pingIntervalMs;
    }

    public void setPingIntervalMs(int pingIntervalMs) {
        this.pingIntervalMs = pingIntervalMs;
    }

    public int getPingTimeoutMs() {
        return pingTimeoutMs;
    }

    public void setPingTimeoutMs(int pingTimeoutMs) {
        this.pingTimeoutMs = pingTimeoutMs;
    }

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }
}
