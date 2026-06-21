# 团队 Onboarding

> 5 分钟上手 fin-chat 工程

## 1. 看哪些文件

按这个顺序读, 5 分钟建立全局认知:

```
fin-chat-design/
  README.md                       ← 总体架构图 + 合规清单 (先看这个)
  01-multi-channel-login.md       ← 多端登录
  02-chat-replay.md               ← 聊天 + 回溯
  03-online-verification.md       ← 联机核查
  04-trade-encryption.md          ← 交易 + 国密
  05-compliance-checklist.md      ← 合规自查
  06-code-skeleton.md             ← 代码骨架
```

## 2. 工程结构

```
fin-chat/
├── pom.xml                        父 POM
├── fin-commons/                   公共 (响应/异常/追踪)
├── fin-api/                       DTO
├── fin-storage/                   MyBatis-Plus
├── fin-kms-gateway/               ★ 国密加密机代理
├── fin-notify-center/             短信/推送
├── fin-audit-center/              审计存证
├── fin-auth-center/               ★ 多端登录
├── fin-chat-center/               ★ 聊天 + 核查
├── fin-trade-gateway/             ★ 交易
├── fin-compliance-center/         ★ 合规
├── fin-observability/             监控
├── fin-gateway/                   Spring Cloud Gateway
├── fin-bootstrap/                 统一启动器
├── fin-web/                       Vue 3 前端
├── deploy/                        Docker + nginx
├── docs/                          文档
└── scripts/                       检查脚本
```

## 3. 开发流程

### 3.1 新增接口 (后端)

1. Controller 加 `@RestController` + `@RequestMapping`
2. Service 业务逻辑
3. 用 `ApiResponse<T>` 统一返回
4. 用 `@PreAuthorize` 控制权限
5. 跑 `bash scripts/static-check.sh`

### 3.2 新增页面 (前端)

1. `src/views/<模块>/<页面>.vue`
2. `src/api/<模块>.ts` 写 API 调用
3. `src/stores/<模块>.ts` 状态管理 (Pinia)
4. `src/router/index.ts` 加路由
5. 跑 `bash scripts/vue-check.sh`

### 3.3 新增数据库表

1. `deploy/mysql-init/V<N>__<描述>.sql`
2. Entity 加 `@TableName` + 字段映射
3. Mapper 加 `@Mapper` + SQL
4. 跑 `bash scripts/static-check.sh`

## 4. 调试技巧

### 4.1 沙箱数据

| 模块 | 端口 | 默认账号 |
|------|------|----------|
| MySQL | 3306 | root / root123 |
| Redis | 6379 | 无密码 |
| Nacos | 8848 | nacos / nacos |
| MinIO (OSS) | 9001 | minio / minio123 |

### 4.2 业务模拟 (Node)

```bash
node scripts/sim-server.js
# 跑 7 项核心业务逻辑, 输出 OK/FAIL
```

### 4.3 API 测试

```bash
# 1. 下发短信
curl -X POST http://localhost:9000/api/v1/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"13800138000","biz":"LOGIN"}'

# 2. SM3 哈希
curl -X POST http://localhost:9000/api/kms/sm3/hash \
  -H "Content-Type: application/json" \
  -d '{"data":"hello"}'

# 3. 健康检查
curl http://localhost:9000/api/v1/auth/health
```

## 5. 提交规范

```bash
# commit message 格式
<type>: <scope> <subject>

# type: feat/fix/refactor/docs/test/chore
# scope: 模块名

# 示例
feat: auth-center add 企业微信登录
fix: chat-center fix STOMP 心跳
docs: update DEPLOY.md
```

## 6. Code Review Checklist

- [ ] 用 `ApiResponse<T>` 统一响应
- [ ] 错误用 `BizException(ErrorCode.X, msg)`
- [ ] 异步操作加 `@Async`
- [ ] 敏感信息用 SM3 哈希 / SM4 加密
- [ ] 涉金额操作加事务 `@Transactional`
- [ ] 外部调用加超时 + 重试
- [ ] 跑过 `static-check.sh` 和 `sim-server.js`

## 7. 常见踩坑

### 7.1 "运行时找不到类"

A: 检查 `pom.xml` 是否在 dependencyManagement 中引了对应版本

### 7.2 "数据库字段找不到"

A: MyBatis-Plus 用 `@TableField("snake_case")`, 驼峰会自动转下划线

### 7.3 "WebSocket 连不上"

A: nginx 必须配 `Upgrade` 和 `Connection` 头, 否则会被 buffering

### 7.4 "跨域失败"

A: 网关已配 `allowedOriginPatterns: "*"`, 生产请收紧

## 8. 联系谁

| 问题类型 | 联系人 |
|----------|--------|
| 架构 / 设计 | @架构组 |
| 国密 / 合规 | @安全合规 |
| 前端 | @前端组 |
| 后端 | @后端组 |
| 部署 / 运维 | @SRE |
