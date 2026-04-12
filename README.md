# 智能售后服务系统 (Java Spring Boot 版)

基于 **RAG (Retrieval-Augmented Generation)** 的智能家电售后问答系统，使用 Java Spring Boot 3.x 重构。

## 技术栈

| 分类 | 技术 |
|------|------|
| 框架 | Spring Boot 3.3.5 + Java 17 |
| 数据库 | MySQL 8.0 (Spring Data JPA) |
| 向量库 | Milvus 2.3 (milvus-sdk-java) |
| 缓存 | Redis 7 (Spring Data Redis) |
| 对象存储 | MinIO (minio-java) |
| 大模型 | 豆包/火山方舟 (OkHttp) |
| API 文档 | SpringDoc OpenAPI 3 |
| 构建 | Maven |

## 项目结构

```
smart-aftercare-java/
├── src/main/java/com/smartaftercare/
│   ├── SmartAftercareApplication.java    # 启动类
│   ├── config/                           # 配置类
│   │   ├── AppProperties.java            # 自定义配置属性
│   │   ├── AsyncConfig.java              # 异步线程池配置
│   │   ├── MilvusConfig.java             # Milvus 连接配置
│   │   ├── MinioConfig.java              # MinIO 连接配置
│   │   ├── OpenApiConfig.java            # Swagger 文档配置
│   │   ├── RedisConfig.java              # Redis 序列化配置
│   │   └── WebConfig.java                # CORS + 静态资源配置
│   ├── controller/                       # REST 控制器（= Go handler）
│   │   ├── DocumentController.java       # 文档管理 API
│   │   ├── QAController.java             # 智能问答 API
│   │   └── HealthController.java         # 健康检查 API
│   ├── filter/                           # 过滤器（= Go middleware）
│   │   ├── RequestFilter.java            # 请求日志 + RequestID
│   │   └── GlobalExceptionHandler.java   # 全局异常处理
│   ├── model/                            # JPA 实体（= Go model）
│   │   ├── Document.java
│   │   ├── ErrorCode.java
│   │   ├── User.java
│   │   ├── QueryLog.java
│   │   └── VectorSlice.java
│   ├── repository/                       # 数据访问层（= Go repository）
│   │   ├── DocumentRepository.java       # JPA
│   │   ├── ErrorCodeRepository.java      # JPA
│   │   ├── QueryLogRepository.java       # JPA
│   │   ├── RedisRepository.java          # Redis 缓存操作
│   │   ├── MilvusRepository.java         # Milvus 向量操作
│   │   └── MinioRepository.java          # MinIO 文件操作
│   ├── service/                          # 业务服务（= Go service）
│   │   ├── DoubaoClient.java             # 豆包大模型客户端
│   │   ├── RagService.java               # RAG 检索+生成
│   │   ├── DocumentService.java          # 文档处理
│   │   ├── ErrorCodeService.java         # 故障代码查询
│   │   └── QueryLogService.java          # 查询日志
│   └── util/                             # 工具类（= Go util）
│       ├── PromptUtil.java               # Prompt 模板
│       ├── TextUtil.java                 # 文本切片/关键词
│       └── ResultUtil.java               # 检索结果处理
├── src/main/resources/
│   ├── application.yml                   # Spring Boot 配置
│   └── static/index.html                 # 前端交互页面
├── db/init.sql                           # 数据库初始化脚本
├── docker-compose.yml                    # Docker 编排
├── Dockerfile                            # 多阶段构建
├── Makefile                              # 构建命令
├── .env                                  # 环境变量
└── pom.xml                               # Maven 依赖
```

## Go → Java 技术映射

| Go 技术 | Java 对应 |
|---------|----------|
| Gin Framework | Spring MVC |
| Viper + BindEnv | @ConfigurationProperties + ${ENV} |
| GORM | Spring Data JPA |
| go-redis/v9 | Spring Data Redis |
| milvus-sdk-go | milvus-sdk-java |
| minio-go/v7 | minio-java |
| net/http (豆包 API) | OkHttp3 |
| logrus | SLF4J + Logback |
| goroutine (异步) | @Async + ThreadPoolTaskExecutor |
| signal.Notify (优雅关闭) | Spring Boot graceful shutdown |
| gin.Recovery() | @RestControllerAdvice |
| swag/gin-swagger | SpringDoc OpenAPI 3 |
| go:embed | classpath:/static/ |

## 快速开始

### 1. 启动依赖服务

```bash
make docker-up
```

### 2. 配置环境变量

编辑 `.env` 文件，配置豆包 API Key 和 Endpoint ID。

### 3. 运行应用

```bash
# 加载环境变量
export $(cat .env | grep -v '^#' | xargs)

# 运行
make run
```

### 4. 访问

- 前端页面: http://localhost:8000
- Swagger 文档: http://localhost:8000/swagger/index.html
- 健康检查: http://localhost:8000/api/health

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/document/upload | 文档上传 |
| GET | /api/documents | 文档列表 |
| GET | /api/document/{id} | 文档详情 |
| DELETE | /api/document/{id} | 删除文档 |
| POST | /api/qa | 智能问答 |
| POST | /api/qa/error-code | 故障代码查询 |
| GET | /api/health | 健康检查 |
| GET | /api/health/ready | 就绪检查 |
| GET | /api/stats | 系统统计 |

## Docker 部署

```bash
make docker-all
```

## 注意事项

1. **Embedding 模型**：需要在火山方舟控制台单独创建 Embedding 模型的推理接入点。如果未配置，系统将降级为纯大模型直接回答。
2. **PDF 解析**：当前为简化实现，完整实现需集成 Apache PDFBox 等库。
3. **OCR**：当前为降级实现，完整实现需集成 Tesseract4J 等库。
