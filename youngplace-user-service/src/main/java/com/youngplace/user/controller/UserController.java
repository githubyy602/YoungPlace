package com.youngplace.user.controller;

import com.youngplace.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getUser(@PathVariable("id") Long id) {
        if (id == null || id < 1L) {
            return ApiResponse.fail(400, "id must be greater than 0");
        }

        Map<String, Object> user = new HashMap<String, Object>();
        user.put("id", id);
        user.put("username", "user_" + id);
        user.put("nickname", "YoungPlace-" + id);

        return ApiResponse.success(user);
    }
}
