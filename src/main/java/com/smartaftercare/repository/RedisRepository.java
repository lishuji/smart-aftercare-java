package com.smartaftercare.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 缓存数据访问层
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisRepository {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Data
    public static class QACacheData {
        private String answer;
        private List<String> sources;
        private List<String> images;
    }

    /**
     * 获取问答缓存
     */
    public QACacheData getQACache(String cacheKey) {
        String data = redisTemplate.opsForValue().get(cacheKey);
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(data, QACacheData.class);
        } catch (JsonProcessingException e) {
            log.warn("解析缓存数据失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置问答缓存
     */
    public void setQACache(String cacheKey, String answer, List<String> sources, List<String> images, Duration expiration) {
        QACacheData cache = new QACacheData();
        cache.setAnswer(answer);
        cache.setSources(sources != null ? sources : Collections.emptyList());
        cache.setImages(images != null ? images : Collections.emptyList());

        try {
            String data = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForValue().set(cacheKey, data, expiration);
        } catch (JsonProcessingException e) {
            log.warn("序列化缓存数据失败: {}", e.getMessage());
        }
    }

    /**
     * 删除问答缓存
     */
    public void deleteQACache(String cacheKey) {
        redisTemplate.delete(cacheKey);
    }

    /**
     * 按模式删除缓存
     */
    public void deleteQACacheByPattern(String pattern) {
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Ping 检查 Redis 连接
     */
    public boolean ping() {
        try {
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通用缓存操作
     */
    public void set(String key, String value, Duration expiration) {
        redisTemplate.opsForValue().set(key, value, expiration);
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String... keys) {
        for (String key : keys) {
            redisTemplate.delete(key);
        }
    }

    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public Long incr(String key) {
        return redisTemplate.opsForValue().increment(key);
    }
}
