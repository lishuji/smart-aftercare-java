package com.smartaftercare.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartaftercare.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Map;

/**
 * Spring Security 配置
 * <p>
 * 无状态 JWT 认证，不使用 Session
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（使用 JWT 无状态认证不需要）
            .csrf(AbstractHttpConfigurer::disable)

            // 无状态会话管理
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开接口：注册、登录、刷新 Token
                .requestMatchers("/api/auth/**").permitAll()
                // 公开接口：健康检查
                .requestMatchers("/api/health", "/api/health/ready").permitAll()
                // 公开接口：前端页面 & 静态资源
                .requestMatchers("/", "/index.html", "/static/**").permitAll()
                // 公开接口：Swagger 文档
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html",
                        "/v3/api-docs/**", "/swagger/index.html").permitAll()
                // 公开接口：OPTIONS 预检请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 管理员接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                // 其余所有接口需要认证
                .anyRequest().authenticated()
            )

            // 异常处理
            .exceptionHandling(ex -> ex
                // 未认证（401）
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        objectMapper.writeValueAsString(
                            Map.of("code", 401, "message", "未登录或登录已过期，请重新登录")
                        )
                    );
                })
                // 无权限（403）
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    response.getWriter().write(
                        objectMapper.writeValueAsString(
                            Map.of("code", 403, "message", "权限不足")
                        )
                    );
                })
            )

            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
