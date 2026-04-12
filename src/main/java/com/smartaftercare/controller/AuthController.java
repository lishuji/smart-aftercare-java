package com.smartaftercare.controller;

import com.smartaftercare.model.User;
import com.smartaftercare.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 认证 Controller
 * <p>
 * 处理注册、登录、Token 刷新、退出登录、修改密码等
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户注册、登录、Token 刷新、退出登录")
public class AuthController {

    private final AuthService authService;

    // ==================== 请求体 ====================

    @Data
    public static class RegisterRequest {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 2, max = 50, message = "用户名长度需在2-50之间")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 6, max = 100, message = "密码长度不能少于6位")
        private String password;

        private String nickname;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class RefreshRequest {
        @NotBlank(message = "Refresh Token 不能为空")
        private String refreshToken;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank(message = "原密码不能为空")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 6, max = 100, message = "新密码长度不能少于6位")
        private String newPassword;
    }

    // ==================== 公开接口（无需认证） ====================

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户账号")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        try {
            User user = authService.register(req.getUsername(), req.getPassword(), req.getNickname());

            Map<String, Object> userData = new LinkedHashMap<>();
            userData.put("id", user.getId());
            userData.put("username", user.getUsername());
            userData.put("nickname", user.getNickname());
            userData.put("role", user.getRole());

            return ResponseEntity.ok(Map.of("code", 200, "message", "注册成功", "data", userData));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回 Access Token 和 Refresh Token")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req) {
        try {
            AuthService.TokenPair tokenPair = authService.login(req.getUsername(), req.getPassword());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("access_token", tokenPair.getAccessToken());
            data.put("refresh_token", tokenPair.getRefreshToken());
            data.put("token_type", "Bearer");
            data.put("expires_in", tokenPair.getExpiresIn());

            return ResponseEntity.ok(Map.of("code", 200, "message", "登录成功", "data", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 Refresh Token 获取新的 Access Token")
    public ResponseEntity<Map<String, Object>> refresh(@Valid @RequestBody RefreshRequest req) {
        try {
            AuthService.TokenPair tokenPair = authService.refreshToken(req.getRefreshToken());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("access_token", tokenPair.getAccessToken());
            data.put("refresh_token", tokenPair.getRefreshToken());
            data.put("token_type", "Bearer");
            data.put("expires_in", tokenPair.getExpiresIn());

            return ResponseEntity.ok(Map.of("code", 200, "message", "刷新成功", "data", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", e.getMessage()));
        }
    }

    // ==================== 需要认证的接口 ====================

    @PostMapping("/logout")
    @Operation(summary = "退出登录", description = "使当前用户的 Refresh Token 失效")
    public ResponseEntity<Map<String, Object>> logout(Authentication authentication) {
        if (authentication != null) {
            Long userId = (Long) authentication.getCredentials();
            authService.logout(userId);
        }
        return ResponseEntity.ok(Map.of("code", 200, "message", "退出成功"));
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码", description = "修改当前用户的密码，修改后需重新登录")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            Authentication authentication) {

        try {
            Long userId = (Long) authentication.getCredentials();
            authService.changePassword(userId, req.getOldPassword(), req.getNewPassword());
            return ResponseEntity.ok(Map.of("code", 200, "message", "密码修改成功，请重新登录"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", 400, "message", e.getMessage()));
        }
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录用户的基本信息")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("code", 401, "message", "未登录"));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", authentication.getName());
        data.put("user_id", authentication.getCredentials());
        data.put("role", authentication.getAuthorities().iterator().next().getAuthority());

        return ResponseEntity.ok(Map.of("code", 200, "data", data));
    }
}
