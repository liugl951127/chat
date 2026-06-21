# 联网核查引擎 (VerifyEngine)

> **位置**：chat-center 内嵌 + compliance-center 公共服务
> **核查源**：12 类 (工商/征信/舆情/产品库/政策库/客户画像/...)
> **响应时延**：P95 < 800ms | **缓存策略**：动态 TTL (5min ~ 24h)

---

## 1. 触发与架构

### 1.1 触发时机
```
消息进入
   │
   ├─ DLP 命中 (企业名/股票代码/身份证/银行卡/...)
   │
   ├─ 关键词命中 (监管政策/产品名称/风险词)
   │
   └─ 显式指令 (用户输入"查一下"/"核实"/"@核查助手")
        │
        ▼
   ┌──────────────────────────────┐
   │     VerifyRouter (规则路由)  │
   │  按实体类型分派到对应 Provider │
   └──────────────┬───────────────┘
                  │
   ┌──────────────┼──────────────────────────────┐
   ▼              ▼              ▼              ▼
┌────────┐   ┌────────┐   ┌────────┐   ┌────────┐
│ 工商   │   │ 征信   │   │ 舆情   │   │ 产品库 │
│ Provider│  │ Provider│  │ Provider│  │ Provider│
└────────┘   └────────┘   └────────┘   └────────┘
   │              │              │              │
   └──────────────┴──────────────┴──────────────┘
                  │
                  ▼
        ┌─────────────────┐
        │ VerifyAggregator │
        │ 聚合 + 风险评分   │
        └────────┬────────┘
                 ▼
        ┌─────────────────┐
        │ 引用回写消息     │
        │ + 留痕到 audit  │
        └─────────────────┘
```

## 2. Java 核心实现

### 2.1 抽象 Provider
```java
// chat-center/src/main/java/com/fin/chat/verify/VerifyProvider.java
public interface VerifyProvider {

    /** 提供商类型 (用于路由) */
    VerifyType type();

    /** 是否支持该实体类型 */
    boolean supports(VerifyEntity entity);

    /** 执行核查 */
    VerifyResult verify(VerifyEntity entity);

    /** 缓存 TTL (秒) */
    int cacheTtlSeconds();
}
```

### 2.2 路由 + 聚合
```java
// chat-center/src/main/java/com/fin/chat/verify/VerifyRouter.java
@Service
@Slf4j
public class VerifyRouter {

    private final List<VerifyProvider> providers;

    @Autowired
    public VerifyRouter(List<VerifyProvider> providers) {
        this.providers = providers;
    }

    public List<VerifyResult> routeAndVerify(List<VerifyEntity> entities, String userId) {
        // 并行调用各 Provider
        List<CompletableFuture<VerifyResult>> futures = entities.stream()
            .flatMap(e -> providers.stream()
                .filter(p -> p.supports(e))
                .map(p -> CompletableFuture.supplyAsync(() -> safeCall(p, e, userId),
                                                       verifyExecutor)))
            .toList();

        return futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .toList();
    }

    private VerifyResult safeCall(VerifyProvider p, VerifyEntity e, String userId) {
        String cacheKey = "verify:" + p.type() + ":" + e.getType() + ":"
                        + DigestUtils.sha256Hex(e.getValue());
        VerifyResult cached = redis.get(cacheKey);
        if (cached != null) return cached;

        try {
            VerifyResult r = p.verify(e);
            if (r.isSuccess()) {
                redis.setex(cacheKey, p.cacheTtlSeconds(), r);
            }
            auditService.recordVerify(userId, p.type(), e, r);  // 留痕
            return r;
        } catch (Exception ex) {
            log.error("verify fail: type={}, entity={}", p.type(), e, ex);
            return VerifyResult.fail(p.type(), e, ex.getMessage());
        }
    }
}
```

### 2.3 工商 Provider (示例)
```java
// chat-center/src/main/java/com/fin/chat/verify/provider/EntProvider.java
@Component
@Slf4j
public class EntProvider implements VerifyProvider {

    @Value("${fin.verify.ent.url}") private String url;
    @Value("${fin.verify.ent.appKey}") private String appKey;
    @Autowired private RestTemplate restTemplate;

    @Override public VerifyType type() { return VerifyType.ENT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.COMPANY_NAME
            || entity.getType() == EntityType.LEGAL_REP
            || entity.getType() == EntityType.CREDIT_CODE;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        // 1. 去敏后请求第三方 (HTTPS + 双向证书)
        HttpHeaders h = new HttpHeaders();
        h.set("X-App-Key", appKey);
        h.set("X-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        h.set("X-Sign", sign(entity));  // 国密 SM3 签名

        Map<String, Object> body = Map.of("keyword", entity.getValue());

        ResponseEntity<EntApiResp> resp = restTemplate.exchange(
            url + "/v1/ent/search",
            HttpMethod.POST,
            new HttpEntity<>(body, h),
            EntApiResp.class
        );

        // 2. 解析 + 标准化
        EntApiResp r = resp.getBody();
        if (r == null || !r.isOk()) {
            return VerifyResult.fail(type(), entity, r == null ? "empty" : r.getError());
        }

        return VerifyResult.builder()
            .type(type())
            .entity(entity)
            .success(true)
            .summary(formatSummary(r))
            .references(r.getData().stream().map(this::toRef).toList())
            .riskScore(calcRisk(r))   // 0-100
            .build();
    }

    @Override public int cacheTtlSeconds() { return 3600; }  // 1h
}
```

### 2.4 风险词 Provider (内置, 离线)
```java
// chat-center/src/main/java/com/fin/chat/verify/provider/PolicyProvider.java
@Component
public class PolicyProvider implements VerifyProvider {

    @Override public VerifyType type() { return VerifyType.POLICY; }

    @Override public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.POLICY_KEYWORD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        // 查内置监管政策库 (postgres / es)
        List<PolicyDoc> docs = policyRepo.search(entity.getValue(), 5);
        return VerifyResult.builder()
            .type(type())
            .entity(entity)
            .success(!docs.isEmpty())
            .references(docs.stream().map(d -> VerifyRef.builder()
                .id(d.getId())
                .title(d.getTitle())
                .summary(d.getSummary())
                .url(d.getSourceUrl())
                .build()).toList())
            .build();
    }
}
```

### 2.5 异步核查消息体
```java
// chat-center/src/main/java/com/fin/chat/verify/VerifyEngine.java
@Service
@Slf4j
public class VerifyEngine {

    @Autowired private DlpService dlpService;
    @Autowired private VerifyRouter router;
    @Autowired private KafkaTemplate<String, ChatEvent> kafka;

    /** 同步快速核查 (DLP 命中后立即返回) */
    public VerifySummary quickVerify(String text, String userId) {
        List<VerifyEntity> entities = dlpService.extract(text);
        if (entities.isEmpty()) return VerifySummary.empty();

        long start = System.currentTimeMillis();
        List<VerifyResult> results = router.routeAndVerify(entities, userId);
        long cost = System.currentTimeMillis() - start;

        return VerifySummary.builder()
            .entities(entities)
            .results(results)
            .totalCostMs(cost)
            .build();
    }

    /** 异步深度核查 (写消息后异步补充) */
    @Async("verifyExecutor")
    public void asyncDeepVerify(ChatMessage msg) {
        VerifySummary s = quickVerify(msg.getContent(), msg.getSenderId());
        if (s.isEmpty()) return;

        // 把核查结果回写到消息 (审计 + 引用)
        msg.getExt().put("verifyRefs", s.getResults());
        kafka.send("chat-verify", VerifyEvent.of(msg, s));
    }
}
```

## 3. 第三方对接清单

| 核查源 | 提供商 | 协议 | 频率限制 | 备注 |
|--------|--------|------|----------|------|
| 国家企业信用 | 启信宝/天眼查 | HTTPS | 100 QPS | 双证书 |
| 实名核验 | 公安一所/银联 | HTTPS + 加密机 | 50 QPS | 二要素 |
| 银行卡四要素 | 银联 | HTTPS + SM4 | 100 QPS | Luhn 校验 |
| 个人征信 | 央行/百行 | 专线 VPN | 20 QPS | 需准入 |
| 证券信息 | Wind/同花顺 | WebSocket | 50 QPS | 实时 |
| 舆情 | 蚁坊/新浪 | HTTPS | 200 QPS | 关键词 |
| 监管政策 | 自建库 | 离线 | - | 内部维护 |
| 产品库 | 自建 | MySQL | - | 内部维护 |
| 法院失信 | 中国执行信息公开 | HTTPS | 10 QPS | 单 IP 严控 |
| 行政处罚 | 信用中国 | HTTPS | 10 QPS | 增量同步 |
| 工商股东 | 启信宝 | HTTPS | 50 QPS | 工商关联 |
| 风险标签 | 同盾/数美 | HTTPS | 100 QPS | 黑名单 |

## 4. 缓存与降级

### 4.1 多级缓存
```java
// chat-center/src/main/java/com/fin/chat/verify/cache/VerifyCacheManager.java
@Component
public class VerifyCacheManager {

    @Autowired private RedisTemplate<String, VerifyResult> redis;
    @Autowired private CaffeineCache caffeine;

    public VerifyResult get(String key) {
        // L1: 本地 (1 min, 1000 entries)
        VerifyResult r = caffeine.getIfPresent(key);
        if (r != null) return r;

        // L2: Redis (动态 TTL)
        r = redis.opsForValue().get(key);
        if (r != null) {
            caffeine.put(key, r);
        }
        return r;
    }

    public void set(String key, VerifyResult value, int ttlSeconds) {
        caffeine.put(key, value);
        redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }
}
```

### 4.2 熔断降级
```yaml
# application.yml
resilience4j:
  circuitbreaker:
    instances:
      verify-ent:
        slidingWindowSize: 20
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 3
      verify-xinyong:
        slidingWindowSize: 20
        failureRateThreshold: 60
```

```java
@Component
public class VerifyProviderProxy {

    @CircuitBreaker(name = "verify-ent", fallbackMethod = "fallback")
    @TimeLimiter(name = "verify-ent")
    public VerifyResult call(VerifyProvider p, VerifyEntity e) {
        return p.verify(e);
    }

    private VerifyResult fallback(VerifyProvider p, VerifyEntity e, Throwable t) {
        log.warn("verify circuit open: provider={}, err={}", p.type(), t.getMessage());
        // 返回降级结果 (标注"暂不可用", 不阻塞聊天)
        return VerifyResult.degraded(p.type(), e, "服务暂不可用, 已记录");
    }
}
```

## 5. 核查引用展示 (Vue)

```vue
<!-- web/src/components/chat/VerifyRefsPanel.vue -->
<template>
  <div class="verify-refs">
    <div v-for="r in references" :key="r.id" class="ref-item">
      <div class="ref-head">
        <el-tag :type="tagType(r.type)" size="small">{{ r.type }}</el-tag>
        <span class="title">{{ r.title }}</span>
        <span v-if="r.riskScore >= 70" class="risk-high">
          <el-icon><Warning /></el-icon> 高风险
        </span>
      </div>
      <div class="ref-body">{{ r.summary }}</div>
      <div v-if="r.url" class="ref-foot">
        <a :href="r.url" target="_blank">查看原文</a>
        <span class="ts">核查于 {{ formatTime(r.ts) }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Warning } from '@element-plus/icons-vue'

defineProps({ references: Array })

const tagType = (t) => ({
  ENT: 'primary', CREDIT: 'warning', COURT: 'danger',
  POLICY: 'info', PRODUCT: 'success', PUBLIC_SENTIMENT: 'danger'
}[t] || 'info')
</script>

<style scoped>
.verify-refs { background: #fafafa; border-radius: 6px; padding: 8px; margin-top: 4px; }
.ref-item { padding: 6px 0; border-bottom: 1px dashed #eee; }
.ref-head { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.title { font-weight: 500; }
.risk-high { color: #f56c6c; }
</style>
```

## 6. 合规要点

| # | 要求 | 实现 |
|---|------|------|
| 1 | 第三方调用最小化 | 同 entity 5min 内复用 |
| 2 | 数据脱敏传输 | 关键词先 SM3 后传输 |
| 3 | 失败可追溯 | 全量错误进 audit |
| 4 | 监管可查 | `audit_service.recordVerify()` 落库 |
| 5 | 用户告知 | 消息中明确标注 "已联网核查" |
| 6 | 跨境限制 | 仅用国内提供商 (政策合规) |
| 7 | 熔断保护 | Resilience4j + 降级 |

## 7. 性能基线

- **单消息核查**: P95 < 800ms (含 3 个 Provider 并行)
- **缓存命中率**: ≥ 70% (工作时段)
- **降级触发率**: < 0.5% (月)
- **审计日志**: 100% 留痕
