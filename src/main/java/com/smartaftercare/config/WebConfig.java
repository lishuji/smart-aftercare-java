package com.smartaftercare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：CORS、静态资源等
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Origin", "Content-Type", "Authorization", "X-Request-ID")
                .exposedHeaders("Content-Length", "X-Request-ID")
                .allowCredentials(true)
                .maxAge(43200); // 12 hours
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 静态文件：前端页面
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 首页重定向到前端页面
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
