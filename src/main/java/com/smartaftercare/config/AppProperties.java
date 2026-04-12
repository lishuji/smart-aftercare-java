package com.smartaftercare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用自定义配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private MilvusProperties milvus = new MilvusProperties();
    private MinioProperties minio = new MinioProperties();
    private DoubaoProperties doubao = new DoubaoProperties();

    @Data
    public static class MilvusProperties {
        private String host = "localhost";
        private int port = 19530;
        private String collectionName = "appliance_knowledge";

        public String getAddress() {
            return host + ":" + port;
        }
    }

    @Data
    public static class MinioProperties {
        private String endpoint = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "appliance-screenshots";
        private boolean useSsl = false;
    }

    @Data
    public static class DoubaoProperties {
        private String apiKey = "";
        private String chatModel = "";
        private String embeddingModel = "";
        private double temperature = 0.1;
        private int maxToken = 2048;
        private String baseUrl = "https://ark.cn-beijing.volces.com/api/v3";
    }
}
