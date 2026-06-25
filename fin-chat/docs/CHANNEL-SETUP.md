# 微信 / 小程序 / 企微 登录配置操作手册

> **面向运维 + 开发**: 从账号申请到上线, 完整 14 步
> **预计时间**: 2-3 小时 (含审批等待)

---

## 📋 总览

| 渠道 | 用途 | 申请方 | 难度 | 时间 |
|------|------|--------|------|------|
| 微信小程序 | 客户在微信里用 | 微信公众平台 | ⭐⭐ | 1-2 天 |
| 微信公众号 H5 | 浏览器中转 | 微信公众平台 | ⭐⭐⭐ | 3-5 天 |
| 企业微信 | 内部员工 | 企业微信管理后台 | ⭐⭐ | 1 天 |
| 手机号 + 短信 | 通用 | 短信服务商 | ⭐ | 1 小时 |

---

## 一、微信小程序登录配置

### 1.1 申请小程序账号

1. 打开 https://mp.weixin.qq.com
2. 立即注册 → 选择 **小程序**
3. 填写邮箱 (未注册过微信公众平台的)、密码
4. 邮箱激活 → 填写主体信息 (企业需营业执照 + 对公账户验证 1 分钱)
5. 获得 **APPID** (类似 `wx1234567890abcdef`)

### 1.2 获取 Secret

1. 登录 https://mp.weixin.qq.com
2. 开发 → 开发管理 → 开发设置
3. **AppSecret** → 生成 (扫码确认) → 获得 **SECRET** (32 字符)
4. ⚠️ **SECRET 只显示一次, 必须立即保存!**

### 1.3 配置服务器域名

1. 开发 → 开发管理 → 开发设置 → 服务器域名
2. 配置以下 4 个域名 (必须 HTTPS):
   - **request 合法域名**: `https://api.your-domain.com`
   - **uploadFile 合法域名**: `https://api.your-domain.com`
   - **downloadFile 合法域名**: `https://api.your-domain.com`
   - **socket 合法域名**: `wss://api.your-domain.com`

### 1.4 配置环境变量

```bash
# fin-chat/deploy/.env 或 Docker Compose environment
WX_MINI_APPID=wx1234567890abcdef
WX_MINI_SECRET=abcdef0123456789abcdef0123456789
```

### 1.5 后端配置 (`application.yml`)

```yaml
fin:
  wx:
    mini:
      appid: ${WX_MINI_APPID}
      secret: ${WX_MINI_SECRET}
```

### 1.6 验证

```bash
# 1. 启动服务
./scripts/mvn-deploy.sh --module=fin-auth-center

# 2. 测试 jscode2session
curl -s "https://api.weixin.qq.com/sns/jscode2session?appid=$WX_MINI_APPID&secret=$WX_MINI_SECRET&js_code=test_code&grant_type=authorization_code"

# 期望: {"errcode":40163,"errmsg":"invalid code"}  ← 40163 表示接口可达
```

### 1.7 常见问题

| 错误码 | 含义 | 解决 |
|--------|------|------|
| 40013 | AppID 错误 | 检查环境变量是否正确传递 |
| 40125 | 密钥错误 | 重新生成 AppSecret |
| 40163 | code 无效 | 前端 wx.login 重新拿 code |
| -1 | 系统繁忙 | 重试 |

---

## 二、微信公众号 H5 登录配置

### 2.1 申请公众号

1. 打开 https://mp.weixin.qq.com
2. 立即注册 → 选择 **订阅号** 或 **服务号** (服务号功能更全)
3. 主体认证 (企业 1-3 天)
4. 获得 **APPID**

### 2.2 配置 OAuth 授权域名

1. 设置 → 公众号设置 → 功能设置 → 网页授权域名
2. 添加 `auth.your-domain.com` (下载 MP_verify_xxx.txt 上传到该域名的根目录)
3. ⚠️ **必须 HTTPS, 端口 80/443**

### 2.3 配置环境变量

```bash
WX_H5_APPID=wxa1b2c3d4e5f6g7h8
WX_H5_SECRET=h8g7f6e5d4c3b2a1098765432abcdef01
```

### 2.4 前端跳转流程

```javascript
// 1. 调后端拿 state
const { state, authUrl } = await api.get('/api/v1/auth/wx/h5/state', {
  headers: { 'X-Device-Id': deviceId }
})

// 2. 跳到微信授权页
window.location.href = authUrl.replace('APPID', 'wxa1b2c3d4e5f6g7h8')
                              .replace('REDIRECT_URI', encodeURIComponent('https://auth.your-domain.com/callback'))

// 3. 微信回调到你的页面, URL 带上 ?code=xxx&state=yyy
// 4. 前端把 code + state 发给后端
const { data } = await api.post('/api/v1/auth/wx/h5/login', {
  code: '...', state: '...', deviceId
})
```

### 2.5 验证

```bash
# 1. 检查 state 接口
curl "http://localhost:8081/api/v1/auth/wx/h5/state" \
  -H "X-Device-Id: dev-test"

# 期望: {"code":0,"data":{"state":"abc-123","authUrl":"https://..."}}
```

---

## 三、企业微信登录配置

### 3.1 创建企业微信

1. 打开 https://work.weixin.qq.com
2. 立即注册 → 填写企业信息 (营业执照 + 法人微信扫码)
3. 创建完成, 自动获得 **CORPID** (`ww1234567890abcdef`)

### 3.2 创建自建应用

1. 应用管理 → 创建应用
2. 应用名称: FinChat
3. 可见范围: 全体员工 (或指定部门)
4. 获得 **AGENTID** (1000001, 1000002...)

### 3.3 获取应用 Secret

1. 进入应用详情 → **Secret** → 发送凭证到企业微信
2. 管理员微信扫码 → 获得 **CORPSECRET** (43 字符)

### 3.4 配置 OAuth 授权域名

1. 应用详情 → 网页授权及 JS-SDK → 网页授权 **可信域名**
2. 添加 `auth.your-domain.com`
3. 上传 `WW_verify_xxx.txt` 验证文件

### 3.5 配置 IP 白名单

1. 我的企业 → 安全与管理 → IP 白名单
2. 添加后端服务器出口 IP (生产环境必做)

### 3.6 配置环境变量

```bash
WECOM_CORPID=ww1234567890abcdef
WECOM_CORPSECRET=43字符的secret_aBcDeFgHiJkLmNoPqRsTuVwXyZ012345678
WECOM_AGENTID=1000001

# 可选: 通讯录免登 secret
WECOM_CONTACT_SECRET=另一个secret
```

### 3.7 后端配置

```yaml
fin:
  wecom:
    corpid: ${WECOM_CORPID}
    corpsecret: ${WECOM_CORPSECRET}
    agentid: ${WECOM_AGENTID}
    contact-secret: ${WECOM_CONTACT_SECRET:}
```

### 3.8 前端跳转流程

```javascript
// 1. 跳企微授权页
const redirect = encodeURIComponent('https://auth.your-domain.com/callback')
const state = 'random-state'
window.location.href = 
  `https://open.weixin.qq.com/connect/oauth2/authorize?appid=${CORPID}` +
  `&redirect_uri=${redirect}&response_type=code&scope=snsapi_base&state=${state}` +
  `&agentid=${AGENTID}#wechat_redirect`

// 2. 回调拿 code
// 3. 发给后端
const { data } = await api.post('/api/v1/auth/wecom/login', {
  code, state, deviceId
})
```

### 3.9 验证

```bash
# 1. 测试 access_token
curl -s "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid=$WECOM_CORPID&corpsecret=$WECOM_CORPSECRET"
# 期望: {"errcode":0,"access_token":"...","expires_in":7200}

# 2. 测试后端登录
curl -X POST http://localhost:8081/api/v1/auth/wecom/login \
  -H "Content-Type: application/json" \
  -d '{"code":"test_code","state":"test_state","deviceId":"dev-1"}'
```

### 3.10 常见问题

| 错误码 | 含义 | 解决 |
|--------|------|------|
| 40001 | secret 错 | 检查 WECOM_CORPSECRET |
| 40029 | code 无效 | 重新授权 |
| 60011 | 没有权限 | 应用可见范围调整 |
| 86003 | URL 未登记 | 检查可信域名 |

---

## 四、手机号 + 短信登录配置

### 4.1 选择短信服务商

| 服务商 | 单价 (分) | 稳定性 | 推荐度 |
|--------|----------|--------|--------|
| 阿里云 | 4.5 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 腾讯云 | 4.5 | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 华为云 | 4.0 | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| 容联 | 3.5 | ⭐⭐⭐ | ⭐⭐ |

### 4.2 阿里云配置 (推荐)

1. 开通 https://dysms.console.aliyun.com
2. 申请签名: 签名管理 → 新建 → **FinChat** (需营业执照 + ICP 备案)
3. 申请模板: 模板管理 → 新建
   ```
   模板示例: 【FinChat】您的验证码为 ${code}, 5 分钟内有效, 请勿泄露。
   ```
4. 创建 AccessKey: 右上角头像 → AccessKey 管理
5. 获得 **ACCESS_KEY_ID** + **ACCESS_KEY_SECRET**

### 4.3 配置环境变量

```bash
SMS_ALIYUN_KEY=LTAI5tFake1234567890abcdef
SMS_ALIYUN_SECRET=alphanum_secret_1234567890abcdef
SMS_ALIYUN_SIGN=FinChat
SMS_ALIYUN_TEMPLATE=SMS_123456789
```

### 4.4 验证

```bash
# 触发短信下发
curl -X POST http://localhost:8081/api/v1/auth/sms/send \
  -H "Content-Type: application/json" \
  -d '{"mobile":"13800138000","biz":"LOGIN"}'

# 期望: {"code":0,"data":{"expireSeconds":60,"bizRef":"..."}}
# (沙箱: 验证码在日志里, 查看 logs/auth-center.log)
```

---

## 五、联机核查 (12 Provider) 配置

### 5.1 Provider 清单

| Provider | 类型 | 沙箱 | 生产对接 |
|----------|------|------|----------|
| PolicyProvider | 政策 | 内置 6 条 | 同步本地库 |
| StockProvider | 行情 | 内置 6 支 | 调 Wind/同花顺 |
| ProductProvider | 产品 | 内置 5 个 | 同步自有产品库 |
| EntProvider | 工商 | mock | 启信宝/天眼查 |
| IdCardProvider | 身份证 | GB 11643 | 公安一所 |
| BankCardProvider | 银行卡 | Luhn | 银联/连连 |
| CreditProvider | 征信 | mock | 央行/百行 |
| SentimentProvider | 舆情 | 关键词 | 蚁坊/新浪 |
| PublicSentimentProvider | 失信 | mock | 执行信息公开网 |
| RiskListProvider | 黑名单 | mock | 同盾/数美 |
| AntiFraudProvider | 反欺诈 | mock | 顶象/极验 |
| FundAssociationProvider | 资质 | mock | 基金业协会 |

### 5.2 第三方对接

#### 启信宝 (EntProvider)

1. 申请 https://www.qichacha.com/openapi
2. 获得 **APPKEY** + **SECRET**
3. 配置:
   ```yaml
   fin:
     verify:
       ent:
         url: https://api.qichacha.com/ECIV4/
         appKey: ${ENT_APPKEY}
   ```

#### 同盾 (RiskListProvider)

1. 申请 https://www.tongdun.cn/api
2. 获得 **PARTNER_CODE** + **PARTNER_KEY**
3. 配置:
   ```yaml
   fin:
     verify:
       risk:
         url: https://api.tongdun.cn/v1/risk
   ```

#### 公安一所 (IdCardProvider)

1. 申请 https://id5.nciic.com.cn (网证 + 公安一所)
2. 获得 **APP_ID** + **APP_KEY** (需 ICP 备案 + 资质审查)
3. 专线 VPN + 国密 IPSec
4. 配置:
   ```yaml
   fin:
     verify:
       id5:
         url: https://api.nciic.com.cn/api/v1/id5
   ```

### 5.3 熔断配置

```yaml
resilience4j:
  circuitbreaker:
    instances:
      verify-ent:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
      verify-xinyong:
        slidingWindowSize: 20
        failureRateThreshold: 60
        waitDurationInOpenState: 60s
```

### 5.4 验证

```bash
# 1. 启动 chat-center
./scripts/mvn-deploy.sh --module=fin-chat-center

# 2. 测试核查
curl -X POST http://localhost:8082/api/v1/chat/verify \
  -H "Content-Type: application/json" \
  -d '{"text":"看好 (600519) 贵州茅台, 涉及资管新规"}'

# 期望: 命中 STOCK(贵州茅台) + POLICY(资管新规)
```

---

## 六、联调测试 Checklist

### 6.1 微信小程序

- [ ] 后端 `WX_MINI_APPID` / `WX_MINI_SECRET` 已配置
- [ ] 微信公众平台 server 域名已配置
- [ ] 前端 `wx.login` 能拿到 code
- [ ] 前端 `getPhoneNumber` 能拿到 encryptedData
- [ ] 后端 `jscode2session` 返回 openid + unionid
- [ ] 后端 `mergeOrCreate` 创建用户
- [ ] 后端颁发 JWT
- [ ] 前端 localStorage 保存 token
- [ ] 后续请求带 Authorization header

### 6.2 微信 H5

- [ ] `WX_H5_APPID` / `WX_H5_SECRET` 已配置
- [ ] 网页授权域名已配置 + MP_verify 上传
- [ ] 前端能跳转到微信授权页
- [ ] 微信回调到 redirect_uri 拿到 code
- [ ] 后端 `oauth2/access_token` 换到 openid
- [ ] `sns/userinfo` 拿到昵称头像
- [ ] unionid 与小程序合并

### 6.3 企业微信

- [ ] `WECOM_CORPID` / `WECOM_CORPSECRET` / `WECOM_AGENTID` 已配置
- [ ] 应用已创建 + 可见范围已设置
- [ ] 网页授权域名已配置 + WW_verify 上传
- [ ] IP 白名单已添加
- [ ] 前端跳企微授权
- [ ] 后端 `gettoken` 成功
- [ ] `auth/getuserinfo` 拿到 userid
- [ ] user/get 拿详情 (手机/姓名)

### 6.4 手机号

- [ ] 短信服务商已开通
- [ ] 签名 + 模板已审核
- [ ] AccessKey 已配置
- [ ] `/sms/send` 调用成功
- [ ] `/sms/send` 5 次错误后锁定
- [ ] `/mobile/login` 验证码正确
- [ ] 60 秒后验证码过期

### 6.5 联机核查

- [ ] 12 个 Provider 全部加载
- [ ] 实体提取覆盖: 股票/身份证/银行卡/企业名/政策词
- [ ] 并行调用 P95 < 1s
- [ ] 缓存命中 70%+
- [ ] 熔断正常触发
- [ ] 降级不阻塞聊天

---

## 七、监控告警

### 7.1 关键指标

| 指标 | 阈值 | 告警 |
|------|------|------|
| 登录成功率 | < 95% | 短信/微信接口异常 |
| jscode2session 错误率 | > 5% | AppID/Secret 失效 |
| 短信下发失败率 | > 2% | 通道异常 |
| 短信到达率 | < 99% | 运营商问题 |
| 核查 Provider 错误率 | > 10% | 第三方接口异常 |
| 熔断器打开 | 任意 | 服务降级 |

### 7.2 告警规则

```yaml
# Prometheus AlertManager
- alert: WxLoginFailureHigh
  expr: rate(wx_login_errors_total[5m]) > 0.05
  for: 2m
  labels: { severity: P1 }
  annotations:
    summary: 微信登录错误率 > 5%

- alert: SmsSendFailure
  expr: rate(sms_send_failures_total[5m]) > 0.02
  for: 1m
  labels: { severity: P0 }
  annotations:
    summary: 短信下发失败率 > 2%

- alert: VerifyProviderDown
  expr: verify_provider_up{provider="ent"} == 0
  for: 30s
  labels: { severity: P1 }
```

---

## 八、上线 Checklist

- [ ] 4 渠道均通过联调
- [ ] 联机核查 12 Provider 全部跑通
- [ ] 监控告警已配置
- [ ] 审计日志已留痕 (登录/核查/短信)
- [ ] 个人信息收集同意协议已法务审核
- [ ] 短信模板已法务审核
- [ ] 应急预案已编写 (短信通道切换)
- [ ] 渗透测试通过 (微信 secret 不在 URL 暴露)
- [ ] 等保三级测评 (金融场景)

---

## 九、常见问题 FAQ

### Q1: 微信小程序登录返回 40163 (invalid code)?

A: code 只能使用一次, 失效时间 5 分钟。前端每次登录重新 `wx.login()`。

### Q2: 企微登录返回 40029 (invalid code)?

A: code 由企微颁发, 只能使用一次, 失效时间 5 分钟。

### Q3: 短信下发慢 (P99 > 3s)?

A:
1. 检查短信通道余额
2. 切换到备选通道 (多通道热备)
3. 调高短信线程池

### Q4: 联机核查返回空 (verifyEmpty)?

A: 文本中没识别到任何实体, 属正常, 不会触发核查。

### Q5: 核查 Provider 调用慢?

A:
1. 看监控 P95 延迟
2. 触发熔断: 检查第三方接口
3. 启用降级 (返回低风险)

### Q6: SECRET 泄露怎么办?

A:
1. 立即撤销 (`微信公众平台 → 重置 Secret`)
2. 启用备用 SECRET (建议提前生成 2 个轮换)
3. 审计: 看 SECRET 泄露期间是否有异常登录
4. 重新部署

### Q7: 企微 IP 白名单?

A: 我的企业 → 安全与管理 → IP 白名单 → 添加生产服务器出口 IP。

### Q8: 小程序 unionid 不一致?

A: 必须绑定到 **微信开放平台** (https://open.weixin.qq.com), 不是公众平台。

---

## 十、联系方式

| 问题 | 联系人 |
|------|--------|
| 微信账号申请 | 业务方 |
| 企微账号申请 | IT 部门 |
| 短信通道 | 阿里云/腾讯云工单 |
| 第三方核查 | 数据商 BD |
| 加密机对接 | @安全合规 |
| 服务器配置 | @SRE |
