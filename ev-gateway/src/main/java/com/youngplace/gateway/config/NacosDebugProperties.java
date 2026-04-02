package com.youngplace.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "youngplace.debug")
public class NacosDebugProperties {

    private String configSource = "local-gateway";
    private String routePolicy = "default";
    private boolean authHeaderStrict = false;

    public String getConfigSource() {
        return configSource;
    }

    public void setConfigSource(String configSource) {
        this.configSource = configSource;
    }

    public String getRoutePolicy() {
        return routePolicy;
    }

    public void setRoutePolicy(String routePolicy) {
        this.routePolicy = routePolicy;
    }

    public boolean isAuthHeaderStrict() {
        return authHeaderStrict;
    }

    public void setAuthHeaderStrict(boolean authHeaderStrict) {
        this.authHeaderStrict = authHeaderStrict;
    }
}
