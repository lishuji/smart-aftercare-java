package com.smartaftercare.service;

import com.smartaftercare.model.User;
import com.smartaftercare.repository.RedisRepository;
import com.smartaftercare.repository.UserRepository;
import com.smartaftercare.security.JwtTokenProvider;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisRepository redisRepository;

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final long REFRESH_TOKEN_TTL_DAYS = 7;

    @Data
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;

        public TokenPair(String accessToken, String refreshToken, long expiresIn) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresIn = expiresIn;
        }
    }

    /**
     * 用户注册
     */
    @Transactional
    public User register(String username, String password, String nickname) {
        // 检查用户名是否已存在
        if (userRepository.existsByUsernameAndDeletedAtIsNull(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }

        // 密码强度校验
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码长度不能少于6位");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname != null ? nickname : username);
        user.setRole("user");
        user.setStatus("active");

        user = userRepository.save(user);
        log.info("新用户注册成功: {} (ID: {})", username, user.getId());
        return user;
    }

    /**
     * 用户登录
     */
    @Transactional
    public TokenPair login(String username, String password) {
        // 查找用户
        User user = userRepository.findByUsernameAndDeletedAtIsNull(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));

        // 校验状态
        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        // 校验密码
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // 生成 Token 对
        return generateTokenPair(user);
    }

    /**
     * 刷新 Token
     */
    public TokenPair refreshToken(String refreshToken) {
        // 校验 Refresh Token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token 无效或已过期");
        }

        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 检查 Redis 中的 Refresh Token 是否匹配（防止重放）
        String storedToken = redisRepository.get(REFRESH_TOKEN_PREFIX + userId);
        if (storedToken == null || !storedToken.equals(refreshToken)) {
            throw new IllegalArgumentException("Refresh Token 已失效，请重新登录");
        }

        // 查找用户
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!"active".equals(user.getStatus())) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        // 生成新的 Token 对
        return generateTokenPair(user);
    }

    /**
     * 退出登录
     */
    public void logout(Long userId) {
        // 删除 Redis 中的 Refresh Token
        redisRepository.delete(REFRESH_TOKEN_PREFIX + userId);
        log.info("用户退出登录: userId={}", userId);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("原密码错误");
        }

        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("新密码长度不能少于6位");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 使旧 Token 失效
        redisRepository.delete(REFRESH_TOKEN_PREFIX + userId);
        log.info("用户修改密码成功: userId={}", userId);
    }

    // ==================== 私有方法 ====================

    private TokenPair generateTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        // 将 Refresh Token 存入 Redis（用于校验和使旧 Token 失效）
        redisRepository.set(
                REFRESH_TOKEN_PREFIX + user.getId(),
                refreshToken,
                REFRESH_TOKEN_TTL_DAYS,
                TimeUnit.DAYS
        );

        long expiresIn = 7200; // Access Token 有效期（秒）
        return new TokenPair(accessToken, refreshToken, expiresIn);
    }
}
