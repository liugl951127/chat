# FinChat 一键部署脚本

> 4 个脚本, 5 种场景, 0 步踩坑

## 脚本清单

| 脚本 | 作用 | 大小 |
|------|------|------|
| `deploy.sh` | 一键部署 (4 模式) | ~15 KB |
| `destroy.sh` | 一键销毁 | ~4.6 KB |
| `health-check.sh` | 健康检查 | ~3.8 KB |
| `static-check.sh` | 静态语法检查 | 已有 |
| `sim-server.js` | 业务逻辑模拟 (Node) | 已有 |
| `vue-check.sh` | Vue 静态检查 | 已有 |
| `validate-endpoints.sh` | API 端到端验证 | 已有 |

## deploy.sh 用法

```bash
# 查看帮助
./scripts/deploy.sh --help

# 场景 1: 纯沙箱 (默认, 30 秒完成, 无需 Docker)
./scripts/deploy.sh --mode=sandbox

# 场景 2: Docker Compose 全量 (含前端, 3-5 分钟)
./scripts/deploy.sh --mode=docker

# 场景 3: 只启动后端 7 个微服务 (Java 进程, 后台运行)
./scripts/deploy.sh --mode=backend-only

# 场景 4: 只建 MySQL 8 库 18 表
./scripts/deploy.sh --mode=db-only

# 场景 5: 生产模式 (KMS 走硬件加密机)
./scripts/deploy.sh --mode=production

# 通用选项
./scripts/deploy.sh --mode=docker --skip-build    # 跳过 Maven 构建
./scripts/deploy.sh --mode=docker --skip-checks   # 跳过静态检查
./scripts/deploy.sh --mode=docker --no-nginx      # 不启 nginx
./scripts/deploy.sh --mode=docker --clean         # 先清理再部署
./scripts/deploy.sh --mode=docker --no-color      # 关闭彩色
```

## 执行流程 (deploy.sh)

```
1/7  前置环境检查       检查 node/java/mvn/docker/mysql
2/7  静态检查          Java + Vue + Node 业务模拟
3/7  清理 (--clean)     停容器/清数据
4/7  数据库初始化       8 库 18 表 + 种子数据
5/7  构建              Maven / Docker 镜像
6/7  启动服务           Docker / 后台进程
7/7  验证              健康检查 + 端到端
```

## destroy.sh 用法

```bash
./scripts/destroy.sh --mode=docker          # 停 Docker + 删卷
./scripts/destroy.sh --mode=backend-only    # 停 Java 进程
./scripts/destroy.sh --mode=sandbox         # 清沙箱日志
./scripts/destroy.sh --mode=db              # 删 8 库
./scripts/destroy.sh --mode=all             # 全部清理
./scripts/destroy.sh -y                     # 跳过确认
```

## health-check.sh 用法

```bash
./scripts/health-check.sh                   # 全量检查
./scripts/health-check.sh --watch           # 持续监控 (10s)
./scripts/health-check.sh --url http://...  # 自定义 URL
./scripts/health-check.sh --json            # JSON 输出
```

输出示例:
```
✅ KMS              port=8091  42ms   HTTP 200
✅ AUTH             port=8081  38ms   HTTP 200
✅ CHAT             port=8082  56ms   HTTP 200
❌ TRADE            port=8083  3000ms HTTP 000
✅ COMPLIANCE       port=8090  45ms   HTTP 200
...
────────────────────────────────────────
  ✅ 全部健康: 8/8
```

## 4 个场景详细说明

### 场景 1: 纯沙箱 (推荐先试)

```bash
./scripts/deploy.sh --mode=sandbox
```

- 无需 Docker / Java / Maven
- 跑 Node 业务模拟 10 项核心逻辑
- 30 秒内完成
- **适合**: 演示、CI、code review

### 场景 2: Docker 全量 (开发首选)

```bash
./scripts/deploy.sh --mode=docker
```

- 启动 13 个 Docker 容器 (含前端)
- MySQL 8 + Redis + Nacos + 9 微服务 + nginx
- 自动建库 (8 库 18 表) + 种子数据
- 自动做健康检查

**访问入口**:
- http://localhost:3000  Web (经 nginx)
- http://localhost:8088  Web 直连
- http://localhost:9000  API 网关
- http://localhost:8848  Nacos (nacos/nacos)

### 场景 3: 后端 only (本地 Java 调试)

```bash
./scripts/deploy.sh --mode=backend-only
```

- 7 个微服务以 mvn spring-boot:run 启动
- 日志在 `logs/<service>.log`
- PID 在 `logs/<service>.pid`
- 停止用 `destroy.sh --mode=backend-only`

**适合**: IDE 联调、断点调试

### 场景 4: 仅建库 (CI/迁移)

```bash
./scripts/deploy.sh --mode=db-only
```

- 只执行 V1__init_all.sql + V2__seed_test_data.sql
- 需要本地 mysql 客户端, 否则用 Docker 兜底

## 常见问题

### Q: 端口被占用?

```bash
# 看谁占用了
lsof -i :8081

# 释放 (mac)
sudo lsof -ti:8081 | xargs kill -9
```

### Q: Docker 启动慢?

首次启动会拉镜像 + 编译, 约 3-5 分钟。可后台:
```bash
ASSUME_YES=1 ./scripts/deploy.sh --mode=docker
```

### Q: MySQL 启动失败?

检查:
- 端口 3306 没被占
- 密码配置 (默认 root/root123)
- 数据卷没被旧数据污染 (`docker volume rm fin-chat_mysql_data`)

### Q: 想看详细日志?

```bash
# Docker
docker-compose -f deploy/docker-compose.yml logs -f

# 后端 only
tail -f logs/auth-center.log
```

### Q: 想换沙箱端口?

```bash
GATEWAY_URL=http://localhost:8888 ./scripts/health-check.sh
```

## 配置文件

`.env` (在 deploy/ 下, 不存在自动创建):
```bash
# 数据库
DB_USER=root
DB_PASS=root123
DB_HOST=127.0.0.1
DB_PORT=3306

# 微信
WX_MINI_APPID=wx_demo
WX_MINI_SECRET=secret_demo

# 企微
WECOM_CORPID=ww_demo
WECOM_CORPSECRET=secret_demo

# 加密机
KMS_HOST=10.20.1.10
KMS_PORT=8000
KMS_SOFT=false
```
