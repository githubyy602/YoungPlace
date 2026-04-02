package com.youngplace.gateway.controller;

import com.youngplace.gateway.config.NacosDebugProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug/gateway")
public class GatewayDebugController {

    private final Environment environment;
    private final NacosDebugProperties nacosDebugProperties;

    public GatewayDebugController(Environment environment,
                                  NacosDebugProperties nacosDebugProperties) {
        this.environment = environment;
        this.nacosDebugProperties = nacosDebugProperties;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("service", "gateway-service");
        payload.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        payload.put("nacosDiscoveryEnabled",
                environment.getProperty("spring.cloud.nacos.discovery.enabled", "false"));
        payload.put("nacosConfigEnabled",
                environment.getProperty("spring.cloud.nacos.config.enabled", "false"));
        payload.put("configSource", nacosDebugProperties.getConfigSource());
        payload.put("routePolicy", nacosDebugProperties.getRoutePolicy());
        payload.put("authHeaderStrict", nacosDebugProperties.isAuthHeaderStrict());
        return payload;
    }
}
