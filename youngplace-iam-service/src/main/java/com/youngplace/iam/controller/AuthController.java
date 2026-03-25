package com.youngplace.iam.controller;

import com.youngplace.common.api.ApiResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Validated
@RestController
@RequestMapping("/api/iam")
public class AuthController {

    private static final String SECRET = "youngplace-jwt-demo-secret";

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        if (!"admin".equals(request.getUsername()) || !"123456".equals(request.getPassword())) {
            return ApiResponse.fail(401, "username or password is invalid");
        }

        Date now = new Date();
        Date expiration = new Date(now.getTime() + 60L * 60L * 1000L);

        String token = Jwts.builder()
                .setSubject(request.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(SignatureAlgorithm.HS256, SECRET)
                .compact();

        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("token", token);
        payload.put("tokenType", "Bearer");
        payload.put("expiresIn", 3600);

        return ApiResponse.success(payload);
    }

    public static class LoginRequest {
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
