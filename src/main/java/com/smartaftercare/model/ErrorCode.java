package com.smartaftercare.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 故障代码模型
 */
@Data
@Entity
@Table(name = "error_codes", indexes = {
        @Index(name = "idx_error_codes_code", columnList = "code"),
        @Index(name = "idx_error_codes_brand", columnList = "brand"),
        @Index(name = "idx_error_codes_model", columnList = "model")
})
@SQLDelete(sql = "UPDATE error_codes SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class ErrorCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String brand;

    @Column(nullable = false, length = 100)
    private String model;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String solution;

    @Column(length = 50)
    private String category;

    @Column(length = 20)
    private String severity = "medium";

    @Column(length = 255)
    private String source;
}
