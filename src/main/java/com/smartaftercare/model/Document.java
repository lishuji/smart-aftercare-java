package com.smartaftercare.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 文档元数据模型
 */
@Data
@Entity
@Table(name = "documents")
@SQLDelete(sql = "UPDATE documents SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Document {

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

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType = "pdf";

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String model;

    @Column(length = 100)
    private String uploader;

    @Column(name = "upload_time")
    private LocalDateTime uploadTime;

    @Column(nullable = false, length = 20)
    private String status = "pending";

    @Column(name = "page_count")
    private int pageCount = 0;

    @Column(name = "slice_count")
    private int sliceCount = 0;

    @Column(name = "file_size")
    private long fileSize = 0;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String remark;
}
