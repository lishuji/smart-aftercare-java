package com.smartaftercare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 智能售后服务系统 - Spring Boot 启动类
 * <p>
 * 基于 RAG 的智能家电售后问答与文档管理系统。
 * 支持文档上传解析、智能问答、故障代码查询等功能。
 */
@SpringBootApplication
@EnableAsync
public class SmartAftercareApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAftercareApplication.class, args);
    }
}
