package com.smartaftercare.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 查询日志模型（用于记录用户查询和统计）
 */
@Data
@Entity
@Table(name = "query_logs")
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String query;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(name = "query_type", length = 20)
    private String queryType;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(columnDefinition = "TEXT")
    private String sources;

    @Column(name = "cache_hit")
    private boolean cacheHit = false;

    @Column
    private long duration;

    @Column(name = "user_ip", length = 50)
    private String userIp;
}
