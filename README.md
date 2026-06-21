# 金融合规多端聊天与交易系统 (fin-chat)

> Java 17 + Spring Boot 3 + Spring Cloud Gateway + Vue 3 + 国密硬件加密机
> 满足等保 2.0 三级 / 个人信息保护法 / 金融销售留痕 / GM/T 国密合规

## 📦 仓库内容

```
.
├── fin-chat-design/        # 7 份设计文档 (120KB+)
│   ├── README.md           # 总体方案 + 架构图
│   ├── 01-multi-channel-login.md
│   ├── 02-chat-replay.md
│   ├── 03-online-verification.md
│   ├── 04-trade-encryption.md
│   ├── 05-compliance-checklist.md
│   └── 06-code-skeleton.md
│
├── fin-chat/               # 工程脚手架 (Maven 多模块)
│   ├── pom.xml
│   ├── fin-commons/                   公共: 响应/异常/追踪
│   ├── fin-api/                       DTO
│   ├── fin-storage/                   MyBatis-Plus
│   ├── fin-kms-gateway/               ★ 国密加密机代理 (SM2/SM3/SM4)
│   ├── fin-notify-center/             短信/推送
│   ├── fin-audit-center/              审计 + 哈希链存证
│   ├── fin-auth-center/               ★ 多端登录 (微信/小程序/企微/H5/手机)
│   ├── fin-chat-center/               ★ 聊天 + 联机核查
│   ├── fin-trade-gateway/             ★ 交易 + 短信 + 国密签名
│   ├── fin-compliance-center/         ★ 风险测评 + 适当性
│   ├── fin-observability/             监控告警
│   ├── fin-gateway/                   Spring Cloud Gateway
│   ├── fin-bootstrap/                 统一启动器
│   ├── fin-web/                       Vue 3 前端 (5 页面)
│   ├── deploy/                        Docker Compose + nginx + MySQL
│   ├── docs/                          DEPLOY.md + TEAM.md
│   ├── scripts/                       static-check + vue-check + sim-server
│   └── .github/workflows/             CI 工作流
│
└── README.md               本文件
```

## 🚀 5 分钟上手

```bash
# 1. 拉代码
git clone https://github.com/liugl951127/chat.git
cd chat/fin-chat

# 2. 静态检查 (无需 JDK)
bash scripts/static-check.sh        # Java 75 文件 0 错误
cd fin-web && bash scripts/vue-check.sh && cd ..   # Vue 19 文件 0 错误
node scripts/sim-server.js          # 业务逻辑模拟 7 项

# 3. 起 Docker 环境
cd deploy
docker-compose up -d mysql redis nacos
docker-compose up -d kms-gateway auth-center

# 4. 验证
curl http://localhost:8091/api/kms/health
```

## 📊 进度 (截至 W3)

| 模块 | 状态 |
|------|------|
| fin-commons / fin-api / fin-storage | ✅ 基础 |
| fin-kms-gateway (SM2/SM3/SM4) | ✅ 完整 |
| fin-auth-center (微信/小程序/企微/H5/手机) | ✅ 完整 |
| fin-chat-center (WebSocket + 哈希链 + 核查) | ✅ 完整 |
| fin-trade-gateway (风控 + 短信 + 国密签名) | ✅ 完整 |
| fin-compliance-center (C1-C5 风险测评) | ✅ 完整 |
| fin-notify-center / fin-audit-center / fin-observability | ✅ 完整 |
| fin-gateway (Spring Cloud Gateway) | ✅ 完整 |
| Vue 3 前端 (登录/聊天/交易/审计/合规/实名) | ✅ 完整 |
| Docker Compose + nginx 统一入口 | ✅ 完整 |
| MySQL Schema (8 库 18 表) | ✅ 完整 |
| CI 工作流 (GitHub Actions) | ✅ 完整 |
| 部署文档 + 团队 Onboarding | ✅ 完整 |

**代码量**: 110+ Java + 20+ Vue/TS + 2 SQL ≈ 12000+ 行

## 🏗 端口分配

```
3000  nginx (统一入口)
8081  fin-auth-center          (多端登录)
8082  fin-chat-center          (聊天 + 核查 + WebSocket)
8083  fin-trade-gateway        (交易)
8088  fin-web (前端调试)
8090  fin-compliance-center    (合规/风险测评)
8091  fin-kms-gateway          (国密加密机)
8092  fin-notify-center        (短信/推送)
8093  fin-audit-center         (审计)
8094  fin-observability        (监控)
9000  fin-gateway              (Spring Cloud Gateway)
```

## 🔐 合规要点

- **等保 2.0 三级**: 双因子认证、mTLS、安全审计 ≥ 6 个月、3-2-1 备份
- **个人信息保护法**: 单独同意、最小必要、可查询/更正/删除
- **金融销售留痕**: 哈希链 + TSA 时间戳、适当性匹配、冷静期、风险测评 12 月
- **GM/T 国密**: SM2/SM3/SM4 全覆盖、密钥在加密机、3 轴分立

详见 `fin-chat-design/05-compliance-checklist.md`

## 📚 文档入口

- **设计**：[fin-chat-design/README.md](./fin-chat-design/README.md)
- **部署**：[fin-chat/docs/DEPLOY.md](./fin-chat/docs/DEPLOY.md)
- **团队**：[fin-chat/docs/TEAM.md](./fin-chat/docs/TEAM.md)

## 🤝 贡献

提交前:
```bash
bash scripts/static-check.sh
cd fin-web && bash scripts/vue-check.sh && cd ..
node scripts/sim-server.js
```

## 📜 许可证

待定 (内部项目)
