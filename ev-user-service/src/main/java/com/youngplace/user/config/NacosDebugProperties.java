package com.youngplace.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "youngplace.debug")
public class NacosDebugProperties {

    private String configSource = "local-user";
    private String userDataMode = "mock";
    private boolean strictAuthHeader = false;

    public String getConfigSource() {
        return configSource;
    }

    public void setConfigSource(String configSource) {
        this.configSource = configSource;
    }

    public String getUserDataMode() {
        return userDataMode;
    }

    public void setUserDataMode(String userDataMode) {
        this.userDataMode = userDataMode;
    }

    public boolean isStrictAuthHeader() {
        return strictAuthHeader;
    }

    public void setStrictAuthHeader(boolean strictAuthHeader) {
        this.strictAuthHeader = strictAuthHeader;
    }
}
