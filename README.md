# 金融合规多端聊天与交易系统 (fin-chat)

> Java 17 + Spring Boot 3 + Spring Cloud Gateway + Vue 3 + 国密硬件加密机
> 满足等保 2.0 三级 / 个人信息保护法 / 金融销售留痕 / GM/T 国密合规

## 目录结构

```
.
├── fin-chat-design/        # 设计文档 (7 份, 共 120KB+)
│   ├── README.md           # 总体方案 + 架构图 + 合规清单
│   ├── 01-multi-channel-login.md   # 多端登录 (微信/小程序/企微/H5)
│   ├── 02-chat-replay.md           # 聊天 + 历史回溯 (哈希链 + TSA)
│   ├── 03-online-verification.md   # 联网核查引擎
│   ├── 04-trade-encryption.md      # 交易 + 短信 + 国密加密机
│   ├── 05-compliance-checklist.md  # 合规清单 (等保/个保法/金融/国密)
│   └── 06-code-skeleton.md         # 关键代码骨架
│
├── fin-chat/               # 工程脚手架 (Maven 多模块)
│   ├── pom.xml                       # 父 POM
│   ├── fin-commons/                  # 公共: 响应/异常/上下文/追踪
│   ├── fin-api/                      # DTO/VO/枚举
│   ├── fin-storage/                  # MyBatis-Plus
│   ├── fin-kms-gateway/              # ★ 国密硬件加密机代理 (SM2/SM3/SM4)
│   ├── fin-notify-center/            # 短信/推送
│   ├── fin-audit-center/             # 审计存证
│   ├── fin-auth-center/              # ★ 多端登录 (微信/小程序/企微/H5/手机)
│   ├── fin-chat-center/              # 聊天 (WebSocket)
│   ├── fin-trade-gateway/            # 交易网关
│   ├── fin-compliance-center/        # 合规引擎
│   ├── fin-observability/            # 监控告警
│   ├── fin-gateway/                  # Spring Cloud Gateway
│   ├── fin-bootstrap/                # 统一启动器
│   └── fin-web/                      # Vue 3 管理后台
│
└── deploy/                 # (待补: Docker Compose / K8s / 监控)
```

## 快速开始

```bash
# 1. 启动基础服务
docker-compose -f deploy/docker-compose.yml up -d mysql redis nacos

# 2. 编译
cd fin-chat
mvn clean install -DskipTests

# 3. 启动 (沙箱: KMS 用软算法 fallback)
mvn -pl fin-kms-gateway spring-boot:run &
mvn -pl fin-auth-center spring-boot:run &

# 4. 测试登录
curl -X POST http://localhost:8081/api/v1/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"13800138000","biz":"LOGIN"}'
```

## 当前进度

- [x] **W1**: 工程脚手架 (13 个 Maven 模块, 父 POM + 公共)
- [x] **W1**: **fin-kms-gateway** — 国密加密机代理 (软算法 + HSM 双实现)
- [x] **W1**: **fin-auth-center** — 多端登录核心 (微信 + 手机号)
- [ ] **W2**: fin-chat-center (WebSocket + 历史回溯)
- [ ] **W3**: fin-trade-gateway (交易 + 短信 + 国密签名)
- [ ] **W4**: fin-compliance-center (适当性 + 风险测评)
- [ ] **W5**: fin-audit-center (哈希链 + TSA)
- [ ] **W6**: fin-gateway (Spring Cloud Gateway)
- [ ] **W7**: fin-web (Vue 3 前端)

## 关键设计

1. **私钥永不外传**: JWT 签名、交易 SM2 签名都走加密机, 私钥物理隔离
2. **哈希链存证**: 消息 + 交易 + 审计都进 Merkle 树, 接 TSA 第三方时间戳
3. **5 年留存**: 消息进 MongoDB (5 年 TTL), 交易/审计进 MySQL (永久)
4. **全链路 SM3**: 验证码、敏感数据、用户标识统一 SM3 哈希
5. **unionid 合并**: 微信生态跨端账号统一, 跨主体靠手机号兜底

## 文档入口

详细设计请阅读 [fin-chat-design/README.md](./fin-chat-design/README.md)。

## 许可证

待定 (内部项目)
