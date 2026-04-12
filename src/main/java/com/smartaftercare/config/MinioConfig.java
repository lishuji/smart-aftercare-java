package com.smartaftercare.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final AppProperties appProperties;

    @Bean
    public MinioClient minioClient() throws Exception {
        AppProperties.MinioProperties minioCfg = appProperties.getMinio();
        log.info("连接 MinIO: {}", minioCfg.getEndpoint());

        MinioClient client = MinioClient.builder()
                .endpoint(minioCfg.getEndpoint())
                .credentials(minioCfg.getAccessKey(), minioCfg.getSecretKey())
                .build();

        // 确保 Bucket 存在
        boolean exists = client.bucketExists(
                BucketExistsArgs.builder().bucket(minioCfg.getBucket()).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(minioCfg.getBucket()).build());
            log.info("创建 Bucket: {}", minioCfg.getBucket());
        }

        log.info("MinIO 连接成功");
        return client;
    }
}
