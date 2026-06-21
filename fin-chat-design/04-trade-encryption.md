# 交易网关 + 短信验证 + 国密加密机 (trade-gateway + kms-gateway)

> **端口**：trade-gateway 8083 / kms-gateway 8091
> **加密机**：国产合规密码机 (华为/卫士通/三未信安, GM/T 0036)
> **算法**：SM2 (签名/密钥交换) / SM3 (摘要) / SM4 (对称)
> **目标**：交易关键环节二次验证 + 硬件密钥 + 不可抵赖

---

## 1. 总体架构

```
                 ┌────────────────────────────────┐
   客户端 ─────► │       trade-gateway (8083)     │
   (携带 JWT)   │                                  │
                 │   ┌──────────────────────────┐  │
                 │   │  TradeRiskEngine          │  │
                 │   │  · 适当性匹配              │  │
                 │   │  · 风险测评有效期          │  │
                 │   │  · 冷静期 (24h)           │  │
                 │   │  · 黑名单 / 限频           │  │
                 │   └────────┬─────────────────┘  │
                 │            ▼                    │
                 │   ┌──────────────────────────┐  │
                 │   │  SmsVerifyService        │  │
                 │   │  · 6 位验证码 (60s)       │  │
                 │   │  · SM3 哈希存 Redis       │  │
                 │   │  · 防爆破 (5 次锁定)     │  │
                 │   └────────┬─────────────────┘  │
                 │            ▼                    │
                 │   ┌──────────────────────────┐  │
                 │   │  SignService             │  │
                 │   │  · 组装交易原文            │  │
                 │   │  · KMS 远程签名 (SM2)    │  │
                 │   │  · 拼装完整请求            │  │
                 │   └────────┬─────────────────┘  │
                 │            ▼                    │
                 │   ┌──────────────────────────┐  │
                 │   │  TradeAuditService       │  │
                 │   │  · 哈希链 + TSA           │  │
                 │   │  · 不可篡改存证            │  │
                 │   └──────────────────────────┘  │
                 └────────────────┬───────────────┘
                                  ▼
                ┌────────────────────────────────────┐
                │     kms-gateway (8091)              │
                │     · TCP 长连接 (加密机 SDK)        │
                │     · 密钥索引 (永远不出硬件)         │
                │     · SM2/SM3/SM4 代理              │
                └────────────────┬───────────────────┘
                                 ▼
                ┌────────────────────────────────────┐
                │   国密硬件加密机 (物理机/HSM)        │
                │   · 密钥存储 (SMK/WLK)              │
                │   · SM2 签名 (私钥不可导出)          │
                │   · 性能: 5000 签名/秒               │
                └────────────────────────────────────┘
```

## 2. 加密机接入 (kms-gateway)

### 2.1 抽象接口
```java
// kms-gateway/src/main/java/com/fin/kms/KmsGatewayClient.java
public interface KmsGatewayClient {

    /** SM4 对称加密 */
    byte[] sm4Encrypt(String keyAlias, byte[] plaintext);
    byte[] sm4Decrypt(String keyAlias, byte[] ciphertext);

    /** SM3 摘要 */
    String sm3Hash(byte[] data);
    String sm3Hash(String data);

    /** SM2 签名 (私钥在加密机, 永远不外传) */
    SignatureResult sm2Sign(String keyAlias, byte[] data);

    /** SM2 验签 */
    boolean sm2Verify(String publicKey, byte[] data, byte[] signature);

    /** SM2 加密 (用于密钥交换) */
    byte[] sm2Encrypt(String publicKey, byte[] plaintext);
    byte[] sm2Decrypt(String keyAlias, byte[] ciphertext);

    /** JWT 签名密钥获取 (用于 auth-center) */
    RSAPrivateKey getJwtSigningKey();   // 内部还是调加密机, 这里只是暴露给 JwtIssuer

    /** 密钥生成 (内部使用, 返回 keyAlias) */
    String generateKey(KeySpec spec);

    /** 健康检查 */
    boolean ping();
}
```

### 2.2 国密硬件实现 (TCP SDK)
```java
// kms-gateway/src/main/java/com/fin/kms/hsm/Sjl03KmsClient.java
@Component
@Slf4j
public class Sjl03KmsClient implements KmsGatewayClient {

    @Value("${fin.kms.host}") private String host;
    @Value("${fin.kms.port}") private int port;
    @Value("${fin.kms.appId}") private String appId;
    @Value("${fin.kms.appKey}") private String appKey;  // 加密机认证用

    private final ConnectionPool pool;

    @PostConstruct
    public void init() {
        // 加密机连接池 (TCP 长连接)
        this.pool = new ConnectionPool(host, port, 8, 30_000);
        // 启动握手 (国密 IPSec 通道)
        pool.warmup(conn -> {
            HandshakeReq req = HandshakeReq.builder()
                .appId(appId)
                .timestamp(Instant.now().getEpochSecond())
                .sign(sm3Hash(appId + appKey + Instant.now().getEpochSecond()))
                .build();
            return conn.send("HSM_HANDSHAKE", req);
        });
    }

    @Override
    public SignatureResult sm2Sign(String keyAlias, byte[] data) {
        // 国密规范: 待签数据先 SM3 摘要, 再用私钥对摘要签名
        byte[] digest = sm3(data);

        return pool.execute(conn -> {
            SignReq req = SignReq.builder()
                .keyAlias(keyAlias)
                .digest(digest)
                .algorithm("SM2")
                .withZ(true)  // 带 Z 值 (国密规范)
                .build();
            SignResp resp = conn.send("SM2_SIGN", req, SignResp.class);
            return new SignatureResult(resp.getSignature(), resp.getPublicKey());
        });
    }

    @Override
    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        return pool.execute(conn -> {
            SymmEncReq req = SymmEncReq.builder()
                .keyAlias(keyAlias)
                .iv(randomIv())           // 随机 IV, 16 字节
                .algorithm("SM4-CBC")
                .padding("PKCS7")
                .plaintext(plaintext)
                .build();
            SymmEncResp resp = conn.send("SM4_ENC", req, SymmEncResp.class);
            return resp.getCiphertext();
        });
    }

    @Override
    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        // IV 拼接在密文前 (12 字节 IV + 4 字节长度 + ciphertext)
        return pool.execute(conn -> {
            SymmDecReq req = SymmDecReq.builder()
                .keyAlias(keyAlias)
                .ciphertext(ciphertext)
                .build();
            SymmDecResp resp = conn.send("SM4_DEC", req, SymmDecResp.class);
            return resp.getPlaintext();
        });
    }

    @Override
    public String sm3Hash(byte[] data) {
        // 加密机内置 SM3, 也可在 JVM 内用 hutool 实现 (性能更好)
        return Hex.encodeHexString(pool.execute(conn ->
            conn.send("SM3_HASH", HashReq.of(data), HashResp.class).getDigest()
        ));
    }

    @Override
    public boolean ping() {
        try {
            pool.execute(conn -> conn.send("PING", null, PingResp.class));
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

### 2.3 连接池
```java
// kms-gateway/src/main/java/com/fin/kms/hsm/ConnectionPool.java
@Component
public class ConnectionPool {

    private final BlockingQueue<HsmConnection> pool;
    private final long timeoutMs;

    public ConnectionPool(String host, int port, int size, long timeoutMs) {
        this.pool = new LinkedBlockingQueue<>(size);
        this.timeoutMs = timeoutMs;
        for (int i = 0; i < size; i++) {
            pool.add(HsmConnection.connect(host, port));
        }
    }

    public <T> T execute(Function<HsmConnection, T> action) {
        HsmConnection conn = null;
        try {
            conn = pool.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (conn == null || !conn.isAlive()) {
                conn = HsmConnection.reconnect(...);
            }
            return action.apply(conn);
        } catch (Exception e) {
            // 健康检查失败: 报警
            throw new KmsException("加密机调用失败", e);
        } finally {
            if (conn != null) pool.offer(conn);
        }
    }
}
```

### 2.4 KMS 降级 (软算法兜底, 仅沙箱用)
```java
// kms-gateway/src/main/java/com/fin/kms/soft/SoftKmsClient.java
@Profile("dev | sandbox")
@Component
@Primary
public class SoftKmsClient implements KmsGatewayClient {
    // 用 hutool-crypto + bouncycastle 实现国密算法
    // 严禁在 prod profile 注册!
}
```

## 3. 短信验证码服务

### 3.1 服务实现
```java
// trade-gateway/src/main/java/com/fin/trade/sms/SmsVerifyService.java
@Service
@Slf4j
public class SmsVerifyService {

    @Autowired private StringRedisTemplate redis;
    @Autowired private KmsGatewayClient kmsClient;
    @Autowired private NotifyCenterClient notifyClient;
    @Autowired private SmsTemplate smsTemplate;
    @Autowired private AuditService auditService;

    private static final int CODE_LEN = 6;
    private static final int TTL_SECONDS = 60;
    private static final int MAX_RETRY = 5;
    private static final int COOL_DOWN = 60;

    /** 下发验证码 */
    public SmsSendResult send(String mobile, SmsBizType biz, Long userId) {
        // 1. 防刷: 60s 内同一手机+业务 已发过, 拒绝
        String coolKey = "sms:cool:" + biz + ":" + kmsClient.sm3Hash(mobile);
        if (redis.hasKey(coolKey)) {
            throw new BizException("请求过于频繁, 请稍后再试");
        }

        // 2. 生成 6 位数字
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));

        // 3. SM3 哈希存 Redis (永远不存明文)
        String codeHash = kmsClient.sm3Hash(code);
        String key = "sms:code:" + biz + ":" + kmsClient.sm3Hash(mobile);
        redis.opsForValue().set(key, codeHash, TTL_SECONDS, TimeUnit.SECONDS);

        // 4. 冷却标记
        redis.opsForValue().set(coolKey, "1", COOL_DOWN, TimeUnit.SECONDS);

        // 5. 调短信通道 (阿里云/腾讯云/华为云, 三选一备份)
        NotifyResult nr = notifyClient.sendSms(mobile,
            smsTemplate.render(biz, code, TTL_SECONDS));

        // 6. 审计 (脱敏)
        auditService.record(new AuditEvent("SMS_SEND", userId, Map.of(
            "mobileHash", kmsClient.sm3Hash(mobile),
            "biz", biz.name(),
            "channelResult", nr.getCode()
        )));

        return SmsSendResult.builder()
            .expireSeconds(TTL_SECONDS)
            .traceId(nr.getTraceId())
            .build();
    }

    /** 校验验证码 */
    public boolean verify(String mobile, String code, SmsBizType biz) {
        String key = "sms:code:" + biz + ":" + kmsClient.sm3Hash(mobile);
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            auditService.record(new AuditEvent("SMS_EXPIRED", null, Map.of(
                "biz", biz.name()
            )));
            return false;
        }

        // 1. 防爆破: 错误次数
        String errKey = "sms:err:" + biz + ":" + kmsClient.sm3Hash(mobile);
        Long errCount = redis.opsForValue().increment(errKey);
        redis.expire(errKey, 300, TimeUnit.SECONDS);
        if (errCount != null && errCount > MAX_RETRY) {
            // 锁定账号 5 分钟
            redis.opsForValue().set("user:lock:" + kmsClient.sm3Hash(mobile),
                "sms_brute", 300, TimeUnit.SECONDS);
            throw new BizException("尝试次数过多, 账号已临时锁定");
        }

        // 2. 校验 (SM3 比对)
        String inputHash = kmsClient.sm3Hash(code);
        boolean ok = constantTimeEquals(stored, inputHash);

        if (ok) {
            redis.delete(key);
            redis.delete(errKey);
        }

        return ok;
    }

    /** 常量时间比较 (防时序攻击) */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }
}
```

### 3.2 业务枚举
```java
public enum SmsBizType {
    LOGIN,                // 登录
    TRADE_CONFIRM,        // 交易确认 (关键)
    TRADE_BUY,            // 买入
    TRADE_SELL,           // 卖出
    TRANSFER,             // 转账
    PASSWORD_RESET,       // 重置密码
    DEVICE_BIND,          // 新设备绑定
}
```

## 4. 交易风控引擎

### 4.1 规则模型
```java
// trade-gateway/src/main/java/com/fin/trade/risk/TradeRiskEngine.java
@Service
@Slf4j
public class TradeRiskEngine {

    @Autowired private ComplianceService complianceService;
    @Autowired private DlpService dlpService;
    @Autowired private SmsVerifyService smsService;

    public RiskDecision preCheck(TradeRequest req, FinUser user) {
        // 1. 适当性匹配 (产品风险等级 vs 用户风险等级)
        if (!complianceService.isAppropriate(user.getId(), req.getProductCode())) {
            return RiskDecision.reject(RiskCode.INAPPROPRIATE,
                "产品风险等级超过您的承受能力, 请重做风险测评");
        }

        // 2. 风险测评有效期 (12 个月)
        if (complianceService.isRiskTestExpired(user.getId())) {
            return RiskDecision.reject(RiskCode.RISK_TEST_EXPIRED,
                "风险测评已过期, 请重新测评");
        }

        // 3. 冷静期 (高风险产品首次购买, 24h)
        if (complianceService.isInCoolingPeriod(user.getId(), req.getProductCode())) {
            return RiskDecision.reject(RiskCode.COOLING_PERIOD,
                "高风险产品首购有 24 小时冷静期, 请稍后再试");
        }

        // 4. 限频 (单日累计 / 单笔限额)
        if (complianceService.exceedsDailyLimit(user.getId(), req.getAmount())) {
            return RiskDecision.reject(RiskCode.DAILY_LIMIT,
                "超过单日累计限额");
        }

        // 5. 限次 (高频拦截)
        if (complianceService.exceedsFrequency(user.getId(), req.getProductCode())) {
            return RiskDecision.reject(RiskCode.FREQ_LIMIT,
                "操作过于频繁, 请稍后再试");
        }

        // 6. 黑名单
        if (complianceService.isBlacklisted(user.getId())) {
            return RiskDecision.reject(RiskCode.BLACKLIST,
                "账户存在风险, 请联系客服");
        }

        // 7. 是否需要短信二次验证 (任何交易都需要, 除非白名单设备)
        boolean needSms = !deviceService.isTrustedDevice(user.getId(), req.getDeviceId())
                       || !req.getDeviceId().equals(user.getLastTrustedDevice());

        return RiskDecision.pass(needSms);
    }
}
```

### 4.2 风控决策
```java
@Data @Builder
public class RiskDecision {
    private boolean passed;
    private boolean smsRequired;
    private RiskCode code;
    private String message;

    public static RiskDecision pass(boolean sms) {
        return RiskDecision.builder().passed(true).smsRequired(sms).build();
    }
    public static RiskDecision reject(RiskCode code, String msg) {
        return RiskDecision.builder().passed(false).smsRequired(false)
            .code(code).message(msg).build();
    }
}
```

## 5. 交易签名 + 提交流程

### 5.1 完整流程
```java
// trade-gateway/src/main/java/com/fin/trade/controller/TradeController.java
@RestController
@RequestMapping("/api/trade")
public class TradeController {

    @Autowired private TradeRiskEngine riskEngine;
    @Autowired private SmsVerifyService smsService;
    @Autowired private SignService signService;
    @Autowired private TradeCoreClient coreClient;
    @Autowired private AuditService auditService;

    /** 1. 发起交易 (风控预检, 发短信) */
    @PostMapping("/initiate")
    public TradeInitResp initiate(@Valid @RequestBody TradeRequest req,
                                   @RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader("X-Device-Id") String deviceId) {
        FinUser user = userService.getById(userId);
        req.setUserId(userId);
        req.setDeviceId(deviceId);

        RiskDecision dec = riskEngine.preCheck(req, user);
        if (!dec.isPassed()) {
            auditService.record(new AuditEvent("TRADE_REJECT", userId, dec));
            throw new BizException(dec.getCode(), dec.getMessage());
        }

        // 发短信
        smsService.send(user.getMobile(), SmsBizType.TRADE_CONFIRM, userId);

        // 生成 tradeId (前端下一步要带回来)
        String tradeId = "T" + snowflake.nextId();
        tradeCache.put(tradeId, req, 5 * 60);  // 5 分钟内有效

        return TradeInitResp.builder()
            .tradeId(tradeId)
            .smsRequired(dec.isSmsRequired())
            .expireSeconds(300)
            .build();
    }

    /** 2. 确认交易 (校验短信 + 签名 + 提交) */
    @PostMapping("/confirm")
    public TradeConfirmResp confirm(@Valid @RequestBody TradeConfirm req,
                                     @RequestHeader("X-User-Id") Long userId,
                                     @RequestHeader("X-Device-Id") String deviceId) {
        // 1. 校验短信
        FinUser user = userService.getById(userId);
        boolean ok = smsService.verify(user.getMobile(), req.getSmsCode(),
                                        SmsBizType.TRADE_CONFIRM);
        if (!ok) {
            auditService.record(new AuditEvent("TRADE_SMS_FAIL", userId, req));
            throw new BizException("SMS_INVALID", "验证码错误或已过期");
        }

        // 2. 取出原始交易
        TradeRequest original = tradeCache.get(req.getTradeId());
        if (original == null) {
            throw new BizException("TRADE_EXPIRED", "交易已过期, 请重新发起");
        }

        // 3. 重新风控 (防时间窗口内状态变更)
        RiskDecision dec = riskEngine.preCheck(original, user);
        if (!dec.isPassed()) {
            throw new BizException(dec.getCode(), dec.getMessage());
        }

        // 4. 国密 SM2 签名 (私钥在加密机)
        TradeSigned signed = signService.sign(original, user);

        // 5. 调交易核心
        TradeCoreResp core = coreClient.submit(signed);

        // 6. 审计 (哈希链 + TSA)
        auditService.recordTrade(original, signed, core);

        // 7. 推送结果到聊天 (异步)
        chatClient.pushTradeResult(original.getConversationId(), core);

        return TradeConfirmResp.builder()
            .tradeId(original.getTradeId())
            .status(core.getStatus())
            .coreSerial(core.getSerial())
            .signature(signed.getSignature())
            .tsaSign(signed.getTsaSign())
            .build();
    }
}
```

### 5.2 签名服务
```java
// trade-gateway/src/main/java/com/fin/trade/sign/SignService.java
@Service
@Slf4j
public class SignService {

    @Autowired private KmsGatewayClient kmsClient;
    @Autowired private TsaClient tsaClient;

    /** 组装交易原文 + 签名 */
    public TradeSigned sign(TradeRequest req, FinUser user) {
        // 1. 标准化交易原文 (国密规范: TLV 编码)
        String canonical = canonicalize(req);

        // 2. SM3 摘要
        String digest = kmsClient.sm3Hash(canonical);

        // 3. SM2 签名 (用用户私钥 alias, 私钥在加密机)
        //    keyAlias 格式: "trade-user-{userId}-{yyyyMM}"
        String keyAlias = "trade-user-" + user.getId()
                        + "-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        SignatureResult sig = kmsClient.sm2Sign(keyAlias, digest.getBytes(StandardCharsets.UTF_8));

        // 4. 第三方时间戳 (不可抵赖)
        String tsa = tsaClient.timestamp(digest);

        // 5. 拼装完整签名包
        return TradeSigned.builder()
            .original(canonical)
            .digest(digest)
            .signature(Hex.encodeHexString(sig.getSignature()))
            .publicKey(Hex.encodeHexString(sig.getPublicKey()))
            .tsaSign(tsa)
            .algorithm("SM2withSM3")
            .signedAt(Instant.now())
            .build();
    }

    /** 标准化 (类似 JWT 的 canonical JSON) */
    private String canonicalize(TradeRequest req) {
        // 按字段名排序, 拼接 key=value&..., 防篡改
        Map<String, Object> sorted = new TreeMap<>();
        sorted.put("userId", req.getUserId());
        sorted.put("productCode", req.getProductCode());
        sorted.put("bizType", req.getBizType().name());
        sorted.put("amount", req.getAmount());
        sorted.put("price", req.getPrice());
        sorted.put("quantity", req.getQuantity());
        sorted.put("accountHash", kmsClient.sm3Hash(req.getAccount()));
        sorted.put("deviceId", req.getDeviceId());
        sorted.put("ts", req.getTimestamp());
        return sorted.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining("&"));
    }
}
```

## 6. Vue 3 交易确认弹窗

```vue
<!-- web/src/views/trade/TradeConfirmDialog.vue -->
<template>
  <el-dialog v-model="visible" title="交易确认" width="500px"
             :close-on-click-modal="false" :close-on-press-escape="false">
    <div v-if="step === 'sms'" class="step-sms">
      <p class="hint">
        系统已向您绑定的手机 <strong>{{ maskMobile(user.mobile) }}</strong>
        发送 6 位短信验证码, 请在 60 秒内输入。
      </p>
      <el-input v-model="smsCode" maxlength="6" placeholder="请输入验证码"
                size="large" class="code-input">
        <template #prefix><el-icon><Lock /></el-icon></template>
      </el-input>
      <div class="countdown">
        <el-countdown v-if="countdown > 0" :value="countdown"
                      @finish="onCountdownFinish" format="mm:ss" />
        <el-button v-else link @click="onResend">重新获取</el-button>
      </div>
    </div>

    <div v-else-if="step === 'review'" class="step-review">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="产品">{{ product.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ bizLabel }}</el-descriptions-item>
        <el-descriptions-item label="金额">{{ formatAmount(amount) }}</el-descriptions-item>
        <el-descriptions-item label="风险等级">
          <el-tag :type="riskTagType(product.riskLevel)">{{ product.riskLevel }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <el-alert type="warning" :closable="false" show-icon
                title="适当性匹配" :description="appropriatenessText" />
      <el-checkbox v-model="confirmed">
        我已阅读并理解 <navigator>《风险揭示书》</navigator> 和 <navigator>《产品合同》</navigator>
      </el-checkbox>
    </div>

    <template #footer>
      <el-button @click="onCancel">取消</el-button>
      <el-button v-if="step === 'sms'" type="primary"
                 :disabled="smsCode.length !== 6" :loading="loading"
                 @click="onConfirmSms">验证并继续</el-button>
      <el-button v-if="step === 'review'" type="primary"
                 :disabled="!confirmed" :loading="loading"
                 @click="onSubmit">提交交易</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { initiateTrade, confirmTrade } from '@/api/trade'

const props = defineProps({
  modelValue: Boolean,
  product: Object,
  bizType: String,
  amount: Number,
})
const emit = defineEmits(['update:modelValue', 'success'])

const visible = defineModel('modelValue')
const step = ref('sms')           // sms → review → done
const smsCode = ref('')
const countdown = ref(Date.now() + 60_000)
const tradeId = ref('')
const loading = ref(false)
const confirmed = ref(false)
const appropriatenessText = computed(() => {
  const map = { OK: '与您的风险等级匹配 ✓', HIGH: '⚠ 高于您的风险承受能力', LOW: '— 低于您的风险等级' }
  return map[props.product.appropriateness] || '—'
})
const riskTagType = (l) => ({ C1: 'success', C2: 'success', C3: 'warning', C4: 'danger', C5: 'danger' }[l])
const bizLabel = computed(() => ({ BUY: '买入', SELL: '卖出', SUBSCRIBE: '申购', REDEEM: '赎回' }[props.bizType]))
const maskMobile = (m) => m?.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
const formatAmount = (a) => '¥' + Number(a).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

watch(visible, async (v) => {
  if (v) {
    step.value = 'sms'
    smsCode.value = ''
    confirmed.value = false
    loading.value = true
    try {
      // 1. 调 initiate, 发短信
      const { data } = await initiateTrade({
        productCode: props.product.code,
        bizType: props.bizType,
        amount: props.amount,
      })
      tradeId.value = data.tradeId
      countdown.value = Date.now() + data.expireSeconds * 1000
    } catch (e) {
      ElMessage.error(e.message)
      visible.value = false
    } finally {
      loading.value = false
    }
  }
})

const onConfirmSms = () => {
  step.value = 'review'
  // 短信验证码先留在内存, 提交时一并带上
}

const onSubmit = async () => {
  loading.value = true
  try {
    const { data } = await confirmTrade({
      tradeId: tradeId.value,
      smsCode: smsCode.value,
    })
    ElMessage.success('交易已提交')
    emit('success', data)
    visible.value = false
  } catch (e) {
    ElMessage.error(e.message)
    if (e.code === 'SMS_INVALID') {
      step.value = 'sms'
      smsCode.value = ''
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.step-sms { text-align: center; }
.code-input { margin: 24px 0; }
.countdown { color: #999; }
.step-review { padding: 0 16px; }
.el-alert { margin: 16px 0; }
</style>
```

## 7. 数据库表

```sql
-- 交易流水 (核心, 永久留存)
CREATE TABLE fin_trade (
    id              VARCHAR(32) PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    product_code    VARCHAR(32),
    biz_type        VARCHAR(16),     -- BUY/SELL/SUBSCRIBE/REDEEM/TRANSFER
    amount          DECIMAL(18,4),
    quantity        DECIMAL(18,4),
    price           DECIMAL(18,6),
    account_hash    CHAR(64),        -- SM3(账号)
    device_id       VARCHAR(128),
    risk_decision   JSON,            -- 风控决策快照
    sms_verified    TINYINT,
    canonical_text  TEXT,            -- 签名原文
    digest          CHAR(64),        -- SM3
    signature       TEXT,            -- SM2 签名
    public_key      TEXT,
    tsa_sign        TEXT,            -- 第三方时间戳
    tsa_provider    VARCHAR(32),
    core_serial     VARCHAR(64),     -- 核心系统流水
    core_status     VARCHAR(16),
    status          VARCHAR(16),     -- INIT / SUBMITTED / SUCCESS / FAILED / REJECTED
    failure_reason  VARCHAR(256),
    trace_id        VARCHAR(64),
    created_at      DATETIME(3),
    updated_at      DATETIME(3),
    INDEX idx_user_time (user_id, created_at),
    INDEX idx_status (status, created_at)
) ENGINE=InnoDB CHARSET=utf8mb4;

-- 短信验证码发送日志
CREATE TABLE fin_sms_log (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    mobile_hash     CHAR(64),
    biz_type        VARCHAR(32),
    channel         VARCHAR(16),     -- ALIYUN/TENCENT/HUAWEI
    channel_trace   VARCHAR(64),
    success         TINYINT,
    error_code      VARCHAR(64),
    ip              VARCHAR(64),
    device_id       VARCHAR(128),
    created_at      DATETIME(3),
    INDEX idx_user_time (user_id, created_at)
) ENGINE=InnoDB CHARSET=utf8mb4;
```

## 8. 加密机选型与对接清单

### 8.1 推荐型号
| 厂商 | 型号 | 接口 | 性能 | 备注 |
|------|------|------|------|------|
| 卫士通 | SJL05/SJL03 | TCP / JCE | 5000 签名/s | 国密经典 |
| 华为 | USG series | TCP / PKCS#11 | 8000 签名/s | 国产化首选 |
| 三未信安 | SJJ1015 | TCP / JCE | 3000 签名/s | 性价高 |
| 江南天安 | TASS | TCP / JCE | 4000 签名/s | 金融老牌 |

### 8.2 选型要点
1. **GM/T 0036 认证** (服务器密码机)
2. **FIPS 140-2 Level 3** 物理防护
3. **支持国密 IPSec** (与网关对接)
4. **双机热备** (避免单点)
5. **密钥双轨** (主备加密机同步)
6. **访问审计** (所有调用留痕)

### 8.3 性能基线
- **签名**: 5000 TPS (单台), 10000 TPS (双机)
- **SM4 加解密**: 10000 TPS
- **SM3 摘要**: 50000 TPS (可走 JVM 内)
- **延迟**: P99 < 50ms

## 9. 合规清单

| # | 要求 | 实现 |
|---|------|------|
| 1 | 关键交易二次验证 | SmsVerifyService + 60s TTL |
| 2 | 私钥不出硬件 | KMS SM2 签名 |
| 3 | 不可抵赖 | TSA 第三方时间戳 |
| 4 | 防爆破 | 5 次锁定 + 冷却 |
| 5 | 适当性匹配 | ComplianceService |
| 6 | 冷静期 | 24h 高风险首购拦截 |
| 7 | 限频/限额 | 风控规则引擎 |
| 8 | 全程留痕 | audit_center 哈希链 |
| 9 | 风险测评有效期 | 12 个月强制重测 |
| 10 | 设备可信 | `isTrustedDevice` 维度 |
