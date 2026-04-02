package com.youngplace.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "security.authorization")
public class AuthorizationProperties {

    private boolean enabled = true;
    private List<Rule> rules = new ArrayList<Rule>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public static class Rule {
        private String path;
        private List<String> methods = new ArrayList<String>();
        private List<String> anyRole = new ArrayList<String>();

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public List<String> getMethods() {
            return methods;
        }

        public void setMethods(List<String> methods) {
            this.methods = methods;
        }

        public List<String> getAnyRole() {
            return anyRole;
        }

        public void setAnyRole(List<String> anyRole) {
            this.anyRole = anyRole;
        }
    }
}
