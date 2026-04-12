package com.smartaftercare.controller;

import com.smartaftercare.model.QueryLog;
import com.smartaftercare.service.ErrorCodeService;
import com.smartaftercare.service.QueryLogService;
import com.smartaftercare.service.RagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 智能问答 / 故障代码查询 Controller
 * <p>
 * 替代 Go 的 internal/handler/qa.go
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "智能问答", description = "基于 RAG 的智能问答和故障代码查询")
public class QAController {

    private final RagService ragService;
    private final ErrorCodeService errorCodeService;
    private final QueryLogService queryLogService;

    @Data
    public static class QARequest {
        @NotBlank(message = "查询内容不能为空")
        private String query;
        private String model;
        private String brand;
    }

    @Data
    public static class ErrorCodeRequest {
        @NotBlank(message = "故障代码不能为空")
        private String code;
        private String model;
    }

    @PostMapping("/qa")
    @Operation(summary = "智能问答", description = "基于 RAG 的智能问答，根据上传的家电说明书内容回答用户问题。支持缓存加速。")
    public ResponseEntity<Map<String, Object>> qa(
            @Valid @RequestBody QARequest req,
            HttpServletRequest httpRequest) {

        long startTime = System.currentTimeMillis();

        try {
            RagService.QAResult result = ragService.qa(req.getQuery(), req.getBrand(), req.getModel());

            long duration = System.currentTimeMillis() - startTime;

            // 异步保存查询日志
            QueryLog queryLog = new QueryLog();
            queryLog.setQuery(req.getQuery());
            queryLog.setBrand(req.getBrand());
            queryLog.setModel(req.getModel());
            queryLog.setQueryType("qa");
            queryLog.setAnswer(result.getAnswer());
            queryLog.setCacheHit(result.isCacheHit());
            queryLog.setDuration(duration);
            queryLog.setUserIp(getClientIp(httpRequest));
            queryLogService.saveQueryLogAsync(queryLog);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("answer", result.getAnswer());
            data.put("sources", result.getSources());
            data.put("images", result.getImages());
            data.put("cache_hit", result.isCacheHit());
            data.put("duration", duration);

            return ResponseEntity.ok(Map.of("code", 200, "data", data));

        } catch (Exception e) {
            log.error("QA查询失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "message", "查询失败，请稍后重试"));
        }
    }

    @PostMapping("/qa/error-code")
    @Operation(summary = "故障代码查询", description = "根据故障代码查询故障原因和解决方案。优先查询本地数据库，未找到时降级走 RAG 检索。")
    public ResponseEntity<Map<String, Object>> queryErrorCode(
            @Valid @RequestBody ErrorCodeRequest req,
            HttpServletRequest httpRequest) {

        long startTime = System.currentTimeMillis();

        try {
            ErrorCodeService.ErrorCodeResult result = errorCodeService.queryErrorCode(req.getCode(), req.getModel());

            long duration = System.currentTimeMillis() - startTime;

            // 异步保存查询日志
            QueryLog queryLog = new QueryLog();
            queryLog.setQuery("故障代码:" + req.getCode());
            queryLog.setModel(req.getModel());
            queryLog.setQueryType("error_code");
            queryLog.setAnswer(result.getAnswer());
            queryLog.setDuration(duration);
            queryLog.setUserIp(getClientIp(httpRequest));
            queryLogService.saveQueryLogAsync(queryLog);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("code", result.getCode());
            data.put("answer", result.getAnswer());
            data.put("sources", result.getSources());
            data.put("images", result.getImages());
            data.put("from_db", result.isFromDb());
            data.put("from_rag", result.isFromRag());
            data.put("duration", duration);

            return ResponseEntity.ok(Map.of("code", (Object) 200, "data", data));

        } catch (Exception e) {
            log.error("故障代码查询失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("code", 500, "message", "查询失败，请稍后重试"));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
