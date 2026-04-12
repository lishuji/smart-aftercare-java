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
    private JwtProperties jwt = new JwtProperties();

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

    @Data
    public static class JwtProperties {
        /** Base64 编码的签名密钥（至少 256 位 / 32 字节） */
        private String secret = "c21hcnQtYWZ0ZXJjYXJlLWp3dC1zZWNyZXQta2V5LTIwMjYtdmVyeS1sb25n";
        /** Access Token 有效期（毫秒），默认 2 小时 */
        private long accessTokenExpireMs = 7200000;
        /** Refresh Token 有效期（毫秒），默认 7 天 */
        private long refreshTokenExpireMs = 604800000;
    }
}
