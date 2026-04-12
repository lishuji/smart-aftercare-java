package com.smartaftercare.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 向量数据库配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MilvusConfig {

    private final AppProperties appProperties;
    private MilvusServiceClient milvusClient;

    @Bean
    public MilvusServiceClient milvusServiceClient() {
        AppProperties.MilvusProperties milvusCfg = appProperties.getMilvus();
        log.info("连接 Milvus: {}:{}", milvusCfg.getHost(), milvusCfg.getPort());

        ConnectParam connectParam = ConnectParam.newBuilder()
                .withHost(milvusCfg.getHost())
                .withPort(milvusCfg.getPort())
                .build();
        milvusClient = new MilvusServiceClient(connectParam);
        log.info("Milvus 连接成功");
        return milvusClient;
    }

    @PreDestroy
    public void close() {
        if (milvusClient != null) {
            milvusClient.close();
            log.info("Milvus 连接已关闭");
        }
    }
}
