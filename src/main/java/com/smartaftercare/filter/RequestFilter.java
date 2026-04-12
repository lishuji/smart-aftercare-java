package com.smartaftercare.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 请求日志 + RequestID 过滤器
 * <p>
 * 替代 Go 的 internal/middleware/middleware.go
 */
@Slf4j
@Component
@Order(1)
public class RequestFilter implements Filter {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        long start = System.currentTimeMillis();

        // 生成 Request ID
        String requestId = httpReq.getHeader("X-Request-ID");
        if (requestId == null || requestId.isEmpty()) {
            requestId = LocalDateTime.now().format(FMT) + "-" + randomString(8);
        }
        httpResp.setHeader("X-Request-ID", requestId);
        httpReq.setAttribute("request_id", requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            long latency = System.currentTimeMillis() - start;
            String method = httpReq.getMethod();
            String path = httpReq.getRequestURI();
            String query = httpReq.getQueryString();
            if (query != null) path = path + "?" + query;

            int status = httpResp.getStatus();
            String clientIp = httpReq.getRemoteAddr();

            log.info("[{}] {} {} | {} | {}ms | {}", method, path, clientIp, status, latency, "");
        }
    }

    private String randomString(int n) {
        String letters = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(letters.charAt(ThreadLocalRandom.current().nextInt(letters.length())));
        }
        return sb.toString();
    }
}
