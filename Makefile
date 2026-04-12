.PHONY: build run clean test docker-up docker-down package

# 构建项目
build:
	mvn clean compile -DskipTests

# 打包（生成 jar）
package:
	mvn clean package -DskipTests

# 本地运行（需要先启动依赖服务）
run:
	mvn spring-boot:run

# 运行测试
test:
	mvn test

# 清理构建产物
clean:
	mvn clean

# 启动所有依赖服务（不含应用）
docker-up:
	docker-compose up -d mysql milvus-etcd milvus-minio milvus redis minio
	@echo "等待服务启动..."
	@sleep 15
	@echo "所有依赖服务已启动"

# 启动全部服务（含应用）
docker-all:
	docker-compose up -d --build

# 停止所有服务
docker-down:
	docker-compose down

# 查看服务状态
docker-status:
	docker-compose ps

# 查看日志
docker-logs:
	docker-compose logs -f smart-aftercare

# 重新构建并启动应用
docker-rebuild:
	docker-compose up -d --build smart-aftercare

# 帮助
help:
	@echo "=================================="
	@echo "智能售后服务系统 (Java 版) - 构建命令"
	@echo "=================================="
	@echo ""
	@echo "  make build         - 编译项目"
	@echo "  make package       - 打包 JAR"
	@echo "  make run           - 本地运行"
	@echo "  make test          - 运行测试"
	@echo "  make clean         - 清理构建产物"
	@echo "  make docker-up     - 启动依赖服务"
	@echo "  make docker-all    - 启动全部服务"
	@echo "  make docker-down   - 停止所有服务"
	@echo "  make docker-status - 查看服务状态"
	@echo "  make docker-logs   - 查看应用日志"
