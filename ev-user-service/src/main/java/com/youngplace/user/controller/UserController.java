package com.youngplace.user.controller;

import com.youngplace.common.api.ApiResponse;
import com.youngplace.user.config.NacosDebugProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final NacosDebugProperties nacosDebugProperties;
    private final Environment environment;

    public UserController(NacosDebugProperties nacosDebugProperties,
                          Environment environment) {
        this.nacosDebugProperties = nacosDebugProperties;
        this.environment = environment;
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getUser(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Auth-User", required = false) String authUser) {
        if (id == null || id < 1L) {
            return ApiResponse.fail(400, "id must be greater than 0");
        }

        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", id);
        user.put("username", "user_" + id);
        user.put("nickname", "YoungPlace-" + id);
        user.put("requestedBy", authUser);

        return ApiResponse.success(user);
    }

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> currentUser(
            @RequestHeader(value = "X-Auth-User", required = false) String authUser,
            @RequestHeader(value = "X-Auth-Roles", required = false) String authRoles) {
        if (!StringUtils.hasText(authUser)) {
            return ApiResponse.fail(401, "user context is missing, please access via gateway with valid token");
        }

        List<String> roles = Collections.emptyList();
        if (StringUtils.hasText(authRoles)) {
            String normalized = authRoles.replace("[", "").replace("]", "").trim();
            if (StringUtils.hasText(normalized)) {
                roles = Arrays.asList(normalized.split("\\s*,\\s*"));
            }
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("username", authUser);
        result.put("roles", roles);
        result.put("service", "user-service");
        return ApiResponse.success(result);
    }

    @GetMapping("/config/debug")
    public ApiResponse<Map<String, Object>> configDebug() {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("service", "user-service");
        result.put("activeProfiles", Arrays.asList(environment.getActiveProfiles()));
        result.put("nacosDiscoveryEnabled",
                environment.getProperty("spring.cloud.nacos.discovery.enabled", "false"));
        result.put("nacosConfigEnabled",
                environment.getProperty("spring.cloud.nacos.config.enabled", "false"));
        result.put("configSource", nacosDebugProperties.getConfigSource());
        result.put("userDataMode", nacosDebugProperties.getUserDataMode());
        result.put("strictAuthHeader", nacosDebugProperties.isStrictAuthHeader());
        return ApiResponse.success(result);
    }
}
