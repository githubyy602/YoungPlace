package com.youngplace.iam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

@Component
@RefreshScope
@ConfigurationProperties(prefix = "youngplace.debug")
public class NacosDebugProperties {

    private String configSource = "local";
    private String authPolicy = "default";
    private boolean captchaStrict = false;

    public String getConfigSource() {
        return configSource;
    }

    public void setConfigSource(String configSource) {
        this.configSource = configSource;
    }

    public String getAuthPolicy() {
        return authPolicy;
    }

    public void setAuthPolicy(String authPolicy) {
        this.authPolicy = authPolicy;
    }

    public boolean isCaptchaStrict() {
        return captchaStrict;
    }

    public void setCaptchaStrict(boolean captchaStrict) {
        this.captchaStrict = captchaStrict;
    }
}
