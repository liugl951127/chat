package com.fin.chat.verify.provider;

import com.fin.chat.verify.VerifyEntity;
import com.fin.chat.verify.VerifyResult;
import com.fin.chat.verify.VerifyType;
import com.fin.chat.verify.VerifyRef;
import com.fin.chat.verify.VerifyProvider;
import com.fin.chat.verify.EntityType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 证券行情 Provider (沙箱: 内置样例, 生产: 调 Wind/同花顺)
 */
@Slf4j
@Component
public class StockProvider implements VerifyProvider {

    private static final Pattern STOCK_PATTERN = Pattern.compile("[(（]([0-9]{6})[)）]");

    private static final Map<String, StockInfo> STOCK_MAP = Map.of(
        "600000", new StockInfo("浦发银行", "银行", 8.32, 5_000_000_000L),
        "600519", new StockInfo("贵州茅台", "白酒", 1680.50, 1_000_000_000L),
        "000001", new StockInfo("平安银行", "银行", 12.45, 19_400_000_000L),
        "000002", new StockInfo("万科A", "地产", 8.78, 11_600_000_000L),
        "601318", new StockInfo("中国平安", "保险", 45.20, 10_800_000_000L),
        "601398", new StockInfo("工商银行", "银行", 5.67, 356_000_000_000L)
    );

    @Override public VerifyType type() { return VerifyType.STOCK; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.STOCK_CODE;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        String code = entity.getValue();
        StockInfo info = STOCK_MAP.get(code);

        if (info == null) {
            return VerifyResult.builder()
                .type(type()).entity(entity).success(false)
                .errorMessage("股票代码未找到: " + code)
                .costMs(System.currentTimeMillis() - start)
                .build();
        }

        // 沙箱: 模拟实时价 ±2%
        double nav = info.basePrice * (0.98 + Math.random() * 0.04);

        VerifyRef ref = VerifyRef.builder()
            .id(code)
            .title(info.name + " (" + code + ")")
            .summary(String.format("%s | 最新: %.2f | 总股本: %.1f亿 | 行业: %s",
                info.name, nav, info.totalShares / 1e8, info.industry))
            .url("https://quote.eastmoney.com/sh" + code + ".html")
            .ts(System.currentTimeMillis())
            .build();

        return VerifyResult.builder()
            .type(type()).entity(entity).success(true)
            .summary("实时行情已获取")
            .references(List.of(ref))
            .costMs(System.currentTimeMillis() - start)
            .build();
    }

    @Override public int cacheTtlSeconds() { return 30; }

    /** 从文本中提取股票代码 */
    public static List<String> extract(String text) {
        Matcher m = STOCK_PATTERN.matcher(text);
        java.util.List<String> codes = new java.util.ArrayList<>();
        while (m.find()) codes.add(m.group(1));
        return codes;
    }

    private record StockInfo(String name, String industry, double basePrice, long totalShares) {}
}
