package com.youngplace.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "youngplace.debug")
public class NacosDebugProperties {

    private String configSource = "local-websocket";
    private String topicPrefix = "topic";
    private boolean strictToken = false;

    public String getConfigSource() {
        return configSource;
    }

    public void setConfigSource(String configSource) {
        this.configSource = configSource;
    }

    public String getTopicPrefix() {
        return topicPrefix;
    }

    public void setTopicPrefix(String topicPrefix) {
        this.topicPrefix = topicPrefix;
    }

    public boolean isStrictToken() {
        return strictToken;
    }

    public void setStrictToken(boolean strictToken) {
        this.strictToken = strictToken;
    }
}
