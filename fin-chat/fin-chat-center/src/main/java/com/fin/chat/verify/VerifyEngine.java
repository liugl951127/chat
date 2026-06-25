package com.fin.chat.verify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
        com.fin.chat.verify.provider.StockProvider.extract(text).forEach(code ->
            entities.add(VerifyEntity.builder()
                .type(EntityType.STOCK_CODE).value(code).context(text).build()));

        // 2. 身份证号 (18 位)
        Pattern idCard = Pattern.compile("[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0\\d|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]");
        Matcher m1 = idCard.matcher(text);
        while (m1.find()) {
            String id = m1.group();
            // 脱敏: 前 6 + * + 后 4
            String masked = id.substring(0, 6) + "********" + id.substring(14);
            entities.add(VerifyEntity.builder()
                .type(EntityType.ID_CARD).value(masked)
                .context(text).build());
        }

        // 3. 银行卡号 (16-19 位连续数字)
        Pattern bankCard = Pattern.compile("\\b\\d{16,19}\\b");
        Matcher m2 = bankCard.matcher(text);
        while (m2.find()) {
            String card = m2.group();
            // 只匹配 Luhn 校验通过的
            if (com.fin.chat.verify.provider.BankCardProvider.luhnCheck(card)) {
                String masked = card.substring(0, 4) + "**********" + card.substring(card.length() - 4);
                entities.add(VerifyEntity.builder()
                    .type(EntityType.BANK_CARD).value(masked)
                    .context(text).build());
            }
        }

        // 4. 统一社会信用代码 (18 位字母数字混合)
        Pattern credit = Pattern.compile("\\b[0-9A-HJ-NPQRTUWXY]{2}\\d{6}[0-9A-HJ-NPQRTUWXY]{10}\\b");
        Matcher m3 = credit.matcher(text);
        while (m3.find()) {
            entities.add(VerifyEntity.builder()
                .type(EntityType.CREDIT_CODE).value(m3.group())
                .context(text).build());
        }

        // 5. 企业名称 (常见后缀)
        Pattern ent = Pattern.compile("([\\u4e00-\\u9fa5]{2,15}(?:集团|公司|有限|股份|科技|金融|资管))");
        Matcher m4 = ent.matcher(text);
        while (m4.find()) {
            entities.add(VerifyEntity.builder()
                .type(EntityType.COMPANY_NAME).value(m4.group())
                .context(text).build());
        }

        // 6. 政策关键词
        String[] policyKeywords = {"资管新规", "适当性", "投资者", "理财", "净值", "刚兑", "私募", "公募", "双录", "冷静期"};
        for (String kw : policyKeywords) {
            if (text.contains(kw)) {
                entities.add(VerifyEntity.builder()
                    .type(EntityType.POLICY_KEYWORD).value(kw).context(text).build());
            }
        }

        // 7. 产品关键词
        String[] productKeywords = {"稳健理财", "平衡配置", "沪深300", "国债逆回购", "私募", "货币基金", "混合基金", "ETF"};
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
