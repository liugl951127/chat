package com.fin.chat.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 联机核查引擎
 *
 * <p>入口: 文本 → 提取实体 → 路由 Provider → 聚合结果
 *
 * <p>缓存: Redis (沙箱: 内存 ConcurrentHashMap)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyEngine {

    private final List<VerifyProvider> providers;

    private final Map<String, VerifyResult> cache = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "verify-engine");
        t.setDaemon(true);
        return t;
    });

    /** 从文本提取实体 */
    public List<VerifyEntity> extract(String text) {
        if (text == null || text.isEmpty()) return List.of();

        List<VerifyEntity> entities = new ArrayList<>();

        // 1. 股票代码 (6 位数字 + 括号)
        StockProvider.extract(text).forEach(code ->
            entities.add(VerifyEntity.builder()
                .type(EntityType.STOCK_CODE).value(code).context(text).build()));

        // 2. 身份证号 (18 位)
        Pattern idCard = Pattern.compile("[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0\\d|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]");
        Matcher m1 = idCard.matcher(text);
        while (m1.find()) {
            entities.add(VerifyEntity.builder()
                .type(EntityType.ID_CARD)
                .value(m1.group().substring(0, 6) + "********" + m1.group().substring(14))  // 脱敏
                .context(text).build());
        }

        // 3. 政策关键词
        String[] policyKeywords = {"资管新规", "适当性", "投资者", "理财", "净值", "刚兑", "私募", "公募"};
        for (String kw : policyKeywords) {
            if (text.contains(kw)) {
                entities.add(VerifyEntity.builder()
                    .type(EntityType.POLICY_KEYWORD).value(kw).context(text).build());
            }
        }

        // 4. 产品关键词
        String[] productKeywords = {"稳健理财", "平衡配置", "沪深300", "国债逆回购", "私募"};
        for (String kw : productKeywords) {
            if (text.contains(kw)) {
                entities.add(VerifyEntity.builder()
                    .type(EntityType.PRODUCT_KEYWORD).value(kw).context(text).build());
            }
        }

        return entities;
    }

    /** 同步核查 */
    public List<VerifyResult> verifyAll(List<VerifyEntity> entities) {
        if (entities.isEmpty()) return List.of();

        long start = System.currentTimeMillis();
        List<CompletableFuture<VerifyResult>> futures = entities.stream()
            .flatMap(e -> providers.stream()
                .filter(p -> p.supports(e))
                .map(p -> CompletableFuture.supplyAsync(() -> safeCall(p, e), executor)))
            .collect(Collectors.toList());

        List<VerifyResult> results = futures.stream()
            .map(CompletableFuture::join)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        log.info("[Verify] 实体={}, 结果={}, cost={}ms",
            entities.size(), results.size(), System.currentTimeMillis() - start);
        return results;
    }

    /** 提取并核查 (一站式) */
    public List<VerifyResult> extractAndVerify(String text) {
        return verifyAll(extract(text));
    }

    private VerifyResult safeCall(VerifyProvider p, VerifyEntity e) {
        String cacheKey = p.type().name() + ":" + e.getType().name() + ":" + e.getValue();
        VerifyResult cached = cache.get(cacheKey);
        if (cached != null) return cached;

        try {
            VerifyResult r = p.verify(e);
            cache.put(cacheKey, r);
            return r;
        } catch (Exception ex) {
            log.error("verify fail: {} - {}", p.type(), ex.getMessage());
            return VerifyResult.degraded(p.type(), e, ex.getMessage());
        }
    }
}
