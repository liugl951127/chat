# FinChat 部署指南

> 适用于开发、测试、生产三种环境。

## 1. 快速启动 (开发/沙箱)

```bash
# 1. 克隆代码
git clone https://github.com/liugl951127/chat.git
cd chat/fin-chat

# 2. 静态检查 (无需 JDK, 验证代码完整性)
bash scripts/static-check.sh        # Java
cd fin-web && bash scripts/vue-check.sh && cd ..   # Vue
node scripts/sim-server.js          # 业务逻辑模拟

# 3. 启动完整环境 (Docker Compose)
cd ../deploy
docker-compose up -d

# 4. 验证
curl http://localhost:3000/health
curl http://localhost:8091/api/kms/health   # KMS
curl http://localhost:8081/api/v1/auth/health  # auth
```

**访问入口**：

| 地址 | 说明 |
|------|------|
| http://localhost:3000 | Web 管理后台 (经 nginx 统一入口) |
| http://localhost:8088 | Web 直连 (调试用) |
| http://localhost:9000 | API 网关 |
| http://localhost:3306 | MySQL |
| http://localhost:6379 | Redis |
| http://localhost:8848 | Nacos 控制台 (nacos/nacos) |

## 2. 模块清单

### 2.1 后端 (Java 17 + Spring Boot 3)

| 模块 | 端口 | 角色 |
|------|------|------|
| fin-commons | - | 公共: 响应/异常/追踪 |
| fin-storage | - | MyBatis-Plus |
| fin-kms-gateway | 8091 | 国密加密机代理 |
| fin-notify-center | 8092 | 短信/推送 |
| fin-audit-center | 8093 | 审计存证 |
| fin-auth-center | 8081 | 多端登录 |
| fin-chat-center | 8082 | 聊天 + 核查 |
| fin-trade-gateway | 8083 | 交易网关 |
| fin-compliance-center | 8090 | 合规引擎 |
| fin-observability | 8094 | 监控告警 |
| fin-gateway | 9000 | Spring Cloud Gateway |

### 2.2 前端 (Vue 3)

- 端口 3000 (经 nginx) / 8088 (直连)
- Element Plus + Pinia + STOMP + ECharts

### 2.3 数据库 (MySQL 8)

8 个分库:
- fin_auth / fin_chat / fin_trade / fin_compliance
- fin_kms / fin_notify / fin_audit / fin_observability

## 3. 环境变量

```bash
# 必需
WECOM_CORPID=ww1234567890abcdef
WECOM_CORPSECRET=xxxxxxxxxxxxxxxx
WX_MINI_APPID=wx1234567890abcdef
WX_MINI_SECRET=xxxxxxxxxxxxxxxx

# 可选 (生产)
KMS_SOFT=false                          # 强制走硬件
KMS_HOST=10.20.1.10
KMS_PORT=8000
KMS_APP_KEY=...

# 数据库
DB_USER=fin
DB_PASS=fin@SecurePass

# 短信
SMS_ALIYUN_KEY=...
SMS_ALIYUN_SECRET=...
SMS_ALIYUN_SIGN=FinChat
```

## 4. 生产部署 Checklist

### 4.1 基础设施

- [ ] 等保三级机房 (国密合规)
- [ ] 国产加密机到位 (卫士通 / 华为 / 三未信安)
- [ ] 国密 IPSec 通道建立
- [ ] 双活 + 异地灾备 (RPO 5min / RTO 30min)
- [ ] mTLS 双向证书签发
- [ ] Nacos 集群 (3 节点)
- [ ] Kafka 集群 (3 broker)

### 4.2 应用层

- [ ] KMS_SOFT=false (硬件加密机)
- [ ] JWT 私钥在加密机 (KMS SM2 签名)
- [ ] HTTPS 全站 (TLS 1.2+)
- [ ] nginx 入口 (3000) → gateway (9000) → 各服务
- [ ] 网关 JWT 验签 + 黑名单拦截
- [ ] 限流配置 (每秒/每分钟)

### 4.3 数据层

- [ ] MySQL 8.0 主从 (读写分离)
- [ ] 分库分表 (chat / audit 按时间分区)
- [ ] Redis 持久化 (AOF + RDB)
- [ ] MongoDB 分片 (会话历史, 5 年留存)
- [ ] 冷归档 (OSS / 蓝光)

### 4.4 监控

- [ ] Prometheus + Grafana
- [ ] 日志聚合 (ELK / Loki)
- [ ] 链路追踪 (Jaeger)
- [ ] 告警通道 (webhook + 邮件 + 短信)

### 4.5 合规

- [ ] 等保三级测评
- [ ] 密码应用安全性评估 (GM/T 0054)
- [ ] 个人信息保护影响评估 (PIA)
- [ ] 渗透测试报告
- [ ] 应急预案 + 演练记录

## 5. 团队 Onboarding

### 5.1 新人 5 分钟跑通

```bash
# 1. 拉代码
git clone <repo> && cd chat/fin-chat

# 2. 看架构
cat fin-chat-design/README.md

# 3. 静态检查 (无需 JDK)
bash scripts/static-check.sh

# 4. 跑业务逻辑模拟 (无需 JDK)
node scripts/sim-server.js

# 5. 起 Docker 环境
cd deploy && docker-compose up -d mysql redis nacos
docker-compose up -d kms-gateway auth-center

# 6. 验证
curl http://localhost:8091/api/kms/health
curl -X POST http://localhost:8081/api/v1/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"13800138000","biz":"LOGIN"}'
```

### 5.2 模块对应人 (示例)

| 模块 | Owner | 核心关注 |
|------|-------|----------|
| fin-kms-gateway | 安全团队 | 国密合规 |
| fin-auth-center | 平台团队 | 多端登录 |
| fin-chat-center | 业务团队 | WebSocket + 哈希链 |
| fin-trade-gateway | 业务团队 | 风控 + 短信 |
| fin-compliance-center | 合规团队 | 适当性 |
| fin-audit-center | 合规团队 | 监管查询 |

## 6. 常见问题

### Q1: 沙箱启动失败?

A: 检查 `KMS_SOFT=true` (环境变量) 或 `application.yml` 的 `fin.kms.soft-fallback.enabled: true`

### Q2: MySQL 连接失败?

A: 检查 `SPRING_DATASOURCE_URL` 中 host 用 `mysql` (Docker 服务名) 而非 `localhost`

### Q3: Redis 连不上?

A: `REDIS_HOST=redis` (Docker 服务名)

### Q4: WebSocket 握手失败?

A: nginx 配置了 `Upgrade` 头, 直接访问 `/ws/chat` 走 nginx

## 7. 监控关键指标

- **业务**: 登录成功/失败率、交易成功率、短信下发延迟、消息吞吐
- **系统**: JVM 堆/线程池/GC、数据库连接池、Redis QPS、Kafka lag
- **业务合规**: 实名完成率、风险测评过期数、风控拦截次数、审计 hash 链断裂

## 8. 升级路径

| 版本 | 升级内容 |
|------|----------|
| V1.0 | 当前版本 (沙箱可跑) |
| V1.1 | STOMP + 企业微信完整闭环 |
| V1.2 | 双录 SDK 集成 (高风险产品) |
| V2.0 | 跨云双活 + 监管报送 T+1 |
