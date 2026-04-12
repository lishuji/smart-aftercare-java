package com.smartaftercare.service;

import com.smartaftercare.model.QueryLog;
import com.smartaftercare.repository.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 查询日志服务（异步保存）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryLogService {

    private final QueryLogRepository queryLogRepository;

    /**
     * 异步保存查询日志
     */
    @Async("queryLogExecutor")
    public void saveQueryLogAsync(QueryLog queryLog) {
        try {
            queryLogRepository.save(queryLog);
        } catch (Exception e) {
            log.warn("保存查询日志失败: {}", e.getMessage());
        }
    }
}
