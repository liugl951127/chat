# 多端登录模块 (auth-center) — 微信 / 小程序 / 企微 / H5 / 原生 App

> **端口**：8081 | **协议**：HTTPS | **认证**：JWT (RS256) + Refresh Token
> **核心目标**：5 端账号统一、实名一次性绑定、设备可追溯、强注销合规

---

## 1. 5 端登录架构

```
                    ┌───────────────────────────────┐
                    │      auth-center (8081)        │
                    │                                │
   微信 H5 ───────► │  ┌────────────────────────┐    │
                    │  │ WxH5LoginHandler       │    │
   微信小程序 ─────► │  │ WxMiniLoginHandler     │    │
                    │  │ (共用 unionid)          │    │
   企业微信 ───────► │  │ WecomLoginHandler       │    │
                    │  │ (OAuth2 授权码)         │    │
   iOS/Android ───► │  │ MobileLoginHandler      │    │
                    │  │ (手机号+验证码)          │    │
   Web 管理端 ─────► │  │ WebLoginHandler         │    │
                    │  └────────┬───────────────┘    │
                    │           ▼                     │
                    │  ┌────────────────────────┐    │
                    │  │   AccountMergeService   │    │
                    │  │   (unionid + 手机号合并) │    │
                    │  └────────┬───────────────┘    │
                    │           ▼                     │
                    │  ┌────────────────────────┐    │
                    │  │   RealNameService      │    │
                    │  │   (二要素 + 活体)        │    │
                    │  └────────┬───────────────┘    │
                    │           ▼                     │
                    │  ┌────────────────────────┐    │
                    │  │  JwtIssuer (RS256)      │    │
                    │  │  access(15m)+refresh(14d)│    │
                    │  └────────────────────────┘    │
                    └───────────────────────────────┘
                                  │
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
        ┌──────────┐        ┌──────────┐        ┌──────────┐
        │  MySQL   │        │  Redis   │        │ 加密机   │
        │ 用户主档  │        │ RT 黑名单 │        │ 密钥托管 │
        └──────────┘        └──────────┘        └──────────┘
```

## 2. 统一账号模型

```sql
-- 用户主档 (跨端唯一)
CREATE TABLE fin_user (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    unionid         VARCHAR(64) UNIQUE COMMENT '微信开放平台 unionid (跨公众号/小程序/App)',
    wecom_userid    VARCHAR(64) UNIQUE COMMENT '企微 userid',
    mobile_hash     CHAR(64)    COMMENT 'SM3(手机号)',
    mobile_enc      VARBINARY(256) COMMENT 'SM4(手机号)',
    id_card_hash    CHAR(64)    COMMENT 'SM3(身份证号)',
    real_name_enc   VARBINARY(256) COMMENT 'SM4(姓名)',
    real_name_status TINYINT   COMMENT '0=未实名 1=弱实名 2=强实名',
    risk_level      TINYINT     COMMENT '风险等级 C1-C5',
    status          TINYINT     DEFAULT 1 COMMENT '0=注销 1=正常 2=冻结',
    created_at      DATETIME(3),
    updated_at      DATETIME(3),
    INDEX idx_unionid (unionid),
    INDEX idx_mobile_hash (mobile_hash)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- 设备指纹 (可追溯)
CREATE TABLE fin_user_device (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    device_id   VARCHAR(128) COMMENT '客户端生成的设备指纹 (IMEI/IDFA/UUID)',
    device_type VARCHAR(32)  COMMENT 'WX_H5/WX_MINI/WECOM/IOS/ANDROID/WEB',
    first_seen  DATETIME(3),
    last_seen   DATETIME(3),
    login_count INT DEFAULT 0,
    UNIQUE KEY uk_user_device (user_id, device_id, device_type)
);

-- 登录日志 (审计)
CREATE TABLE fin_login_log (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT,
    channel     VARCHAR(32),     -- WX_H5 / WX_MINI / WECOM / MOBILE / WEB
    openid      VARCHAR(64),
    device_id   VARCHAR(128),
    ip          VARCHAR(64),
    ua          VARCHAR(512),
    success     TINYINT,
    fail_reason VARCHAR(64),
    trace_id    VARCHAR(64),
    created_at  DATETIME(3),
    INDEX idx_user_time (user_id, created_at)
);
```

## 3. 关键 Java 类

### 3.1 抽象登录策略
```java
// auth-center/src/main/java/com/fin/auth/login/AbstractLoginHandler.java
public abstract class AbstractLoginHandler<C extends LoginContext> {

    @Autowired protected AccountMergeService mergeService;
    @Autowired protected RealNameService realNameService;
    @Autowired protected JwtIssuer jwtIssuer;
    @Autowired protected AuditService auditService;
    @Autowired protected KmsGatewayClient kmsClient;  // 加密机客户端

    public abstract LoginChannel channel();         // WX_H5 / WX_MINI / ...

    public LoginResult login(C ctx) {
        // 1. 验签 (防伪造)
        verifySignature(ctx);

        // 2. 风控前置 (IP/设备/频次)
        riskPreCheck(ctx);

        // 3. 渠道身份换 openid
        ChannelIdentity identity = exchangeToOpenId(ctx);

        // 4. 合并/创建账号 (unionid 维度)
        FinUser user = mergeService.mergeOrCreate(identity);

        // 5. 强制实名 (如未实名, 强金融场景需先实名)
        enforceRealNameIfRequired(user, ctx);

        // 6. 颁发双 Token
        TokenPair tokens = jwtIssuer.issue(user, ctx.getDeviceId());

        // 7. 落设备 + 日志 (异步)
        asyncRecordDeviceAndLog(user, ctx);

        return LoginResult.builder()
            .userId(user.getId())
            .accessToken(tokens.getAccess())
            .refreshToken(tokens.getRefresh())
            .expiresIn(900)
            .realNameStatus(user.getRealNameStatus())
            .riskLevel(user.getRiskLevel())
            .build();
    }
}
```

### 3.2 微信小程序登录 (重点)
```java
// auth-center/src/main/java/com/fin/auth/login/WxMiniLoginHandler.java
@Service
public class WxMiniLoginHandler extends AbstractLoginHandler<WxMiniLoginContext> {

    @Value("${fin.wx.mini.appid}")     private String appid;
    @Value("${fin.wx.mini.secret}")    private String secret;

    @Override public LoginChannel channel() { return LoginChannel.WX_MINI; }

    @Override
    protected ChannelIdentity exchangeToOpenId(WxMiniLoginContext ctx) {
        // 1. 前端 wx.login() 拿到 code, 后端换 session
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                   + "?appid=" + appid
                   + "&secret=" + secret
                   + "&js_code=" + ctx.getCode()
                   + "&grant_type=authorization_code";
        WxSessionResp resp = httpClient.get(url, WxSessionResp.class);

        // 2. 拿到 encryptedData + iv, 解密手机号 (可选)
        String phone = null;
        if (StringUtils.isNotBlank(ctx.getEncryptedData())) {
            phone = WxCrypto.decrypt(
                resp.getSessionKey(),
                ctx.getEncryptedData(),
                ctx.getIv()
            );
        }

        return ChannelIdentity.builder()
            .openid(resp.getOpenid())
            .unionid(resp.getUnionid())
            .channel(LoginChannel.WX_MINI)
            .phone(phone)
            .build();
    }
}
```

### 3.3 UnionID 账号合并 (核心合规点)
```java
// auth-center/src/main/java/com/fin/auth/login/AccountMergeService.java
@Service
@Slf4j
public class AccountMergeService {

    @Transactional
    public FinUser mergeOrCreate(ChannelIdentity identity) {
        // 1. 用 unionid 找主账号 (同一微信用户在多端统一)
        FinUser user = null;
        if (StringUtils.isNotBlank(identity.getUnionid())) {
            user = userMapper.selectByUnionid(identity.getUnionid());
        }

        // 2. 用手机号 hash 兜底 (跨主体合并, 需二次确认)
        if (user == null && identity.getPhone() != null) {
            String phoneHash = kmsClient.sm3Hash(identity.getPhone());
            user = userMapper.selectByMobileHash(phoneHash);
        }

        // 3. 创建新用户 (合规要求: 手机号必填)
        if (user == null) {
            user = new FinUser();
            user.setUnionid(identity.getUnionid());
            user.setOpenid(identity.getOpenid());
            user.setMobileEnc(kmsClient.sm4Encrypt(identity.getPhone()));
            user.setMobileHash(kmsClient.sm3Hash(identity.getPhone()));
            user.setRealNameStatus(0);
            user.setStatus(1);
            userMapper.insert(user);
        } else {
            // 4. 已有账号, 补全 openid (按渠道类型)
            bindOpenIdIfAbsent(user, identity);
        }

        return user;
    }
}
```

### 3.4 JWT 颁发 (RS256 + 设备绑定)
```java
// auth-center/src/main/java/com/fin/auth/jwt/JwtIssuer.java
@Component
public class JwtIssuer {

    @Autowired private KmsGatewayClient kmsClient;  // 私钥在加密机, 这里只调接口

    public TokenPair issue(FinUser user, String deviceId) {
        Instant now = Instant.now();

        // Access Token (15 min)
        String access = Jwts.builder()
            .header().add("typ", "JWT").add("alg", "RS256").and()
            .issuer("fin-auth")
            .subject(String.valueOf(user.getId()))
            .claim("unionid", user.getUnionid())
            .claim("device", deviceId)
            .claim("rl", user.getRiskLevel())
            .claim("rns", user.getRealNameStatus())
            .claim("scope", "chat,trade,read")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(900)))
            .signWith(kmsClient.getJwtSigningKey(), Jwts.SIG.RS256)  // 私钥在加密机
            .compact();

        // Refresh Token (14 天, 一次性, 用完即换)
        String refresh = Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("device", deviceId)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(14 * 86400)))
            .signWith(kmsClient.getJwtSigningKey(), Jwts.SIG.RS256)
            .compact();

        // Refresh 进 Redis 黑名单 (登出/重置密码时拉黑)
        redisTemplate.opsForValue().set(
            "rt:" + user.getId() + ":" + deviceId,
            refresh,
            14, TimeUnit.DAYS
        );

        return new TokenPair(access, refresh);
    }
}
```

## 4. Vue 3 端登录组件

### 4.1 微信小程序登录页
```vue
<!-- miniprogram/pages/login/index.vue -->
<template>
  <view class="login-page">
    <view class="logo" />
    <button class="btn-primary"
            open-type="getPhoneNumber"
            @getphonenumber="onPhone">
      手机号一键登录
    </button>
    <button class="btn-secondary" @click="onWechatLogin">
      微信账号登录
    </button>
    <view class="agreement">
      <checkbox :checked="agreed" @click="agreed = !agreed" />
      登录即同意
      <navigator url="/pages/agreement/privacy">《隐私政策》</navigator>
      和
      <navigator url="/pages/agreement/service">《服务协议》</navigator>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { loginByCode } from '@/api/auth'

const agreed = ref(false)

const onPhone = async (e) => {
  if (!agreed.value) return uni.showToast({ title: '请先勾选协议', icon: 'none' })
  if (e.detail.errMsg !== 'getPhoneNumber:ok') return

  // 第一步: wx.login 拿 code
  const { code } = await uni.login({ provider: 'weixin' })

  // 第二步: 把 code + 加密手机号 一起发给后端
  const { data } = await loginByCode({
    code,
    encryptedData: e.detail.encryptedData,
    iv: e.detail.iv,
    deviceId: getDeviceId(),
  })

  uni.setStorageSync('access_token', data.accessToken)
  uni.setStorageSync('refresh_token', data.refreshToken)
  uni.switchTab({ url: '/pages/chat/index' })
}
</script>
```

### 4.2 Vue 3 Web 端 useAuth Hook
```typescript
// web/src/composables/useAuth.ts
import { ref, computed } from 'vue'
import { loginByWxCode, loginByMobile, refreshToken, logout } from '@/api/auth'

const accessToken = ref(localStorage.getItem('access_token') || '')
const refreshTk = ref(localStorage.getItem('refresh_token') || '')

export function useAuth() {
  const isLoggedIn = computed(() => !!accessToken.value)

  const loginWx = async (code: string, deviceId: string) => {
    const { data } = await loginByWxCode({ code, deviceId })
    setTokens(data.accessToken, data.refreshToken)
    return data
  }

  const loginMobile = async (mobile: string, smsCode: string) => {
    const { data } = await loginByMobile({ mobile, smsCode, deviceId })
    setTokens(data.accessToken, data.refreshToken)
    return data
  }

  const setTokens = (a: string, r: string) => {
    accessToken.value = a
    refreshTk.value = r
    localStorage.setItem('access_token', a)
    localStorage.setItem('refresh_token', r)
  }

  // 自动刷新 (axios 拦截器)
  const silentRefresh = async () => {
    if (!refreshTk.value) throw new Error('no refresh token')
    const { data } = await refreshToken(refreshTk.value)
    accessToken.value = data.accessToken
    localStorage.setItem('access_token', data.accessToken)
    return data.accessToken
  }

  return { isLoggedIn, loginWx, loginMobile, silentRefresh, logout: () => logout() }
}
```

## 5. 关键合规要点

| # | 要求 | 实现 |
|---|------|------|
| 1 | 实名认证后才能交易 | `enforceRealNameIfRequired()` 强制拦截 |
| 2 | 设备可追溯 | `fin_user_device` 表 + 风控比对 |
| 3 | 注销立即生效 | `status=0` + Redis 黑名单 + 推所有端 |
| 4 | 同意记录留痕 | 登录时勾选协议 → 写 `fin_consent_log` |
| 5 | 跨端账号合并透明告知 | `AccountMergeService` 触发站内信 + 短信 |
| 6 | 异地登录二次确认 | 新设备登录触发短信验证 |

## 6. 反例与陷阱

❌ **不要把 unionid 当成唯一键**：跨主体 (微信生态 vs 企微) 不互通
❌ **不要明文存手机号**：金融场景必 SM4 + 加密机托管密钥
❌ **不要用对称密钥签 JWT**：必须 RS256 + 私钥在加密机
❌ **不要 refresh token 永久有效**：必须 14 天 + 一次性 (rotation)
❌ **不要省略 unionid 合并告知**：用户会投诉"我的账号怎么串了"
