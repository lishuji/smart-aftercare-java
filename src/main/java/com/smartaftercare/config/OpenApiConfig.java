package com.smartaftercare.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 配置
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智能售后服务系统 API")
                        .version("1.0")
                        .description("基于 RAG 的智能家电售后问答与文档管理系统。支持文档上传解析、智能问答、故障代码查询等功能。")
                        .contact(new Contact()
                                .name("Smart Aftercare Team")
                                .email("support@smart-aftercare.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("http://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
