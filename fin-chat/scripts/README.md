# FinChat 一键部署

`oneclick-deploy.sh` 是 FinChat 的**全栈一键部署脚本**, 把以下流程打包成单条命令:

```
环境检查 → Maven 编译 → 数据库初始化 → 启动 9 个微服务 → 配置 Nginx → 注册 systemd
```

## 用法

```bash
# 推荐: 沙箱/CI 测试用 dry-run (只验证不启动)
./scripts/oneclick-deploy.sh --dry-run -y

# 生产: 全量部署 (需 root)
sudo ./scripts/oneclick-deploy.sh -y

# 跳过特定步骤
sudo ./scripts/oneclick-deploy.sh --skip-build   # 用现有 jar
sudo ./scripts/oneclick-deploy.sh --skip-db      # 不初始化数据库
sudo ./scripts/oneclick-deploy.sh --skip-nginx   # 不配置 Nginx

# 离线模式 (无 Redis/MySQL, 用内存版)
sudo ./scripts/oneclick-deploy.sh --profile=offline -y

# 注册 systemd 服务
sudo ./scripts/oneclick-deploy.sh --install-service -y
```

## 部署流程

| Step | 动作 | 失败跳过 |
|------|------|---------|
| 1 | 环境检查 (JDK / Maven / Node / Redis / MySQL / Nginx) | warn 继续 |
| 2 | 创建日志 + 运行目录 (`/var/log/finchat`, `/var/run/finchat`) | - |
| 3 | `mvn clean package -DskipTests` (13 模块) | `--skip-build` |
| 4 | MySQL: `V1__init_all.sql` + `V2__seed_test_data.sql` | `--skip-db` |
| 5 | 按依赖顺序启动 9 个 fat jar | `--dry-run` 只验证 |
| 6 | Nginx 反代 + WS 升级 + H5 静态 | `--skip-nginx` |
| 7 | 写 `/etc/systemd/system/finchat.service` | `--install-service` |

## 9 个微服务 + 端口

| 服务 | 端口 | 说明 |
|------|------|------|
| fin-kms-gateway | 8091 | 国密机网关 (SM2/SM3/SM4) |
| fin-auth-center | 8081 | 认证中心 (微信 H5/小程序/手机号) |
| fin-compliance-center | 8090 | 合规中心 (风险测评/适当性/双录) |
| fin-chat-center | 8082 | 聊天中心 (会话/消息/哈希链/WS) |
| fin-trade-gateway | 8083 | 交易网关 (产品/订单/冷静期) |
| fin-notify-center | 8092 | 通知中心 (短信/邮件/推送) |
| fin-audit-center | 8093 | 审计中心 (留痕/合规报表) |
| fin-observability | 8094 | 可观测性 (Prometheus/告警) |
| fin-gateway | 9000 | Spring Cloud Gateway (统一入口) |

## 部署完成后访问

| 入口 | URL |
|------|-----|
| Web 管理端 | http://localhost/ |
| H5 客户 (聊天) | http://localhost/mobile/chat-demo.html |
| H5 客户 (理财) | http://localhost/mobile/mobile-h5.html |
| WebSocket | ws://localhost/ws/chat |
| API 网关 | http://localhost/api/v1/... |
| Swagger | http://localhost:9000/swagger-ui.html |

## 运维

```bash
# 查看所有服务 PID
ls /var/run/finchat/*.pid

# 查看日志
tail -f /var/log/finchat/fin-chat-center.log

# 停止所有服务
/workspace/fin-chat/scripts/mvn-stop.sh

# 重新部署
sudo /workspace/fin-chat/scripts/oneclick-deploy.sh -y
```

## 端到端测试脚本

```bash
# 聊天 REST
bash /workspace/fin-chat/scripts/chat-e2e-test.sh

# 聊天 WebSocket (双向推送)
bash /workspace/fin-chat/scripts/chat-ws-e2e.sh

# 合规化购买流程 (8 步)
bash /workspace/fin-chat/scripts/trade-e2e-test.sh
```

## 系统要求

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 必需 |
| Maven | 3.8+ | 编译 |
| Node.js | 18+ | 前端构建 (可选) |
| MySQL | 8.0+ | 生产 (offline 模式不需要) |
| Redis | 6.0+ | 生产 (offline 模式不需要) |
| Nginx | 1.18+ | 前端代理 (可选) |
| systemd | - | 服务托管 (可选) |
| 端口 | 8081-8094, 9000, 80/443 | 防火墙需开放 |

## 故障排查

| 现象 | 解决 |
|------|------|
| `Port already in use` | `pkill -9 -f fin-.*-1.0.0.jar` 或换端口 |
| `Maven not found` | `apt install maven` 或 `export PATH=$PATH:/usr/share/maven/bin` |
| `MySQL connect fail` | 跳过 `--skip-db`, 用 `--profile=offline` 跑内存版 |
| 服务启动后 health 502 | 等 60s 启动时间, 或 `tail -100 /var/log/finchat/<svc>.log` |
| WS 连不上 | 检查 Nginx `/ws/` 代理配置 + `Upgrade` header |
| `/mobile/chat-demo.html` 404 | 先 `mvn package` 跑前端 build |

## 沙箱测试

```bash
# 沙箱/CI 只能用 --dry-run (真启动服务会被 sandbox reap)
./scripts/oneclick-deploy.sh --dry-run -y
```

## 部署架构图

```
                       ┌─────────────────────────┐
                       │      Nginx (:80)         │
                       │  /api/* → Gateway        │
                       │  /ws/*  → Gateway (WS)   │
                       │  /mobile/* → static      │
                       └───────────┬───────────────┘
                                   │
                                   ▼
                       ┌─────────────────────────┐
                       │  fin-gateway (:9000)     │
                       │  Spring Cloud Gateway    │
                       └───────────┬───────────────┘
                                   │
        ┌──────────────┬───────────┼───────────────┬──────────────┐
        ▼              ▼           ▼               ▼              ▼
   fin-kms        fin-auth    fin-chat       fin-trade      fin-compliance
   (:8091)        (:8081)     (:8082)        (:8083)        (:8090)

        ┌──────────────┬───────────┐
        ▼              ▼           ▼
   fin-notify     fin-audit   fin-observability
   (:8092)        (:8093)     (:8094)
```