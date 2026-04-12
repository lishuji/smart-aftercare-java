package com.smartaftercare.controller;

import com.smartaftercare.repository.DocumentRepository;
import com.smartaftercare.repository.ErrorCodeRepository;
import com.smartaftercare.repository.MilvusRepository;
import com.smartaftercare.repository.QueryLogRepository;
import com.smartaftercare.repository.RedisRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查 Controller
 * <p>
 * 替代 Go 的 internal/handler/health.go
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "系统监控", description = "健康检查、就绪检查、系统统计")
public class HealthController {

    private final DataSource dataSource;
    private final RedisRepository redisRepository;
    private final MilvusRepository milvusRepository;
    private final DocumentRepository documentRepository;
    private final ErrorCodeRepository errorCodeRepository;
    private final QueryLogRepository queryLogRepository;

    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "返回服务基础健康状态")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "smart-aftercare"
        ));
    }

    @GetMapping("/health/ready")
    @Operation(summary = "就绪检查", description = "检查所有依赖服务（MySQL、Redis、Milvus）的连接状态")
    public ResponseEntity<Map<String, Object>> readinessCheck() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "ready");
        boolean allHealthy = true;

        // 检查 MySQL
        try (Connection conn = dataSource.getConnection()) {
            conn.isValid(3);
            status.put("mysql", "healthy");
        } catch (Exception e) {
            status.put("mysql", "unhealthy: " + e.getMessage());
            allHealthy = false;
        }

        // 检查 Redis
        try {
            if (redisRepository.ping()) {
                status.put("redis", "healthy");
            } else {
                status.put("redis", "unhealthy");
                allHealthy = false;
            }
        } catch (Exception e) {
            status.put("redis", "unhealthy: " + e.getMessage());
            allHealthy = false;
        }

        // 检查 Milvus
        try {
            Map<String, String> milvusStats = milvusRepository.getCollectionStats();
            status.put("milvus", "healthy");
            status.put("milvus_stats", milvusStats);
        } catch (Exception e) {
            status.put("milvus", "unhealthy: " + e.getMessage());
            allHealthy = false;
        }

        if (!allHealthy) {
            status.put("status", "degraded");
            return ResponseEntity.status(503).body(status);
        }

        return ResponseEntity.ok(status);
    }

    @GetMapping("/stats")
    @Operation(summary = "系统统计", description = "获取文档数量、查询次数等系统统计信息")
    public ResponseEntity<Map<String, Object>> stats() {
        try {
            Map<String, Object> statsData = new LinkedHashMap<>();
            statsData.put("total_documents", documentRepository.count());
            statsData.put("processed_documents", documentRepository.countByStatus("processed"));
            statsData.put("total_error_codes", errorCodeRepository.count());
            statsData.put("today_queries", queryLogRepository.countTodayQueries());

            return ResponseEntity.ok(Map.of("code", 200, "data", statsData));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "message", "获取统计信息失败"));
        }
    }
}
