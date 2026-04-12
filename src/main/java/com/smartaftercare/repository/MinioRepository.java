package com.smartaftercare.repository;

import com.smartaftercare.config.AppProperties;
import io.minio.*;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MinIO 对象存储数据访问层
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MinioRepository {

    private final MinioClient minioClient;
    private final AppProperties appProperties;

    /**
     * 上传文件到 MinIO，返回对象访问路径
     */
    public String uploadFile(String filePath, String objectKey) throws Exception {
        String bucket = appProperties.getMinio().getBucket();
        Path path = Path.of(filePath);
        String contentType = detectContentType(filePath);

        minioClient.uploadObject(
                UploadObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .filename(filePath)
                        .contentType(contentType)
                        .build());

        return "/" + bucket + "/" + objectKey;
    }

    /**
     * 上传 InputStream 到 MinIO
     */
    public String uploadStream(InputStream inputStream, String objectKey, long size, String contentType) throws Exception {
        String bucket = appProperties.getMinio().getBucket();

        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build());

        return "/" + bucket + "/" + objectKey;
    }

    /**
     * 从 MinIO 下载文件
     */
    public void downloadFile(String objectKey, String destPath) throws Exception {
        String bucket = appProperties.getMinio().getBucket();
        minioClient.downloadObject(
                DownloadObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .filename(destPath)
                        .build());
    }

    /**
     * 获取对象 InputStream
     */
    public InputStream getObject(String objectKey) throws Exception {
        String bucket = appProperties.getMinio().getBucket();
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
    }

    /**
     * 删除对象
     */
    public void deleteObject(String objectKey) throws Exception {
        String bucket = appProperties.getMinio().getBucket();
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build());
    }

    /**
     * 检查对象是否存在
     */
    public boolean objectExists(String objectKey) {
        try {
            String bucket = appProperties.getMinio().getBucket();
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 列出指定前缀下的所有对象
     */
    public List<Item> listObjects(String prefix) throws Exception {
        String bucket = appProperties.getMinio().getBucket();
        List<Item> objects = new ArrayList<>();

        Iterable<Result<Item>> results = minioClient.listObjects(
                ListObjectsArgs.builder()
                        .bucket(bucket)
                        .prefix(prefix)
                        .recursive(true)
                        .build());

        for (Result<Item> result : results) {
            objects.add(result.get());
        }
        return objects;
    }

    /**
     * 根据文件扩展名检测 Content-Type
     */
    private String detectContentType(String filePath) {
        String ext = "";
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot >= 0) {
            ext = filePath.substring(lastDot).toLowerCase();
        }

        return switch (ext) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".gif" -> "image/gif";
            case ".pdf" -> "application/pdf";
            case ".doc", ".docx" -> "application/msword";
            case ".txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }
}
