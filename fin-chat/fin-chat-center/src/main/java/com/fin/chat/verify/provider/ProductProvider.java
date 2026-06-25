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

/**
 * 产品库 Provider (内置, 匹配自有产品)
 */
@Slf4j
@Component
public class ProductProvider implements VerifyProvider {

    private static final Map<String, ProductInfo> PRODUCTS = Map.of(
        "稳健理财", new ProductInfo("P-FUND001", "货币基金", "C1", "低风险"),
        "平衡配置", new ProductInfo("P-FUND002", "混合基金", "C3", "中风险"),
        "沪深300", new ProductInfo("P-STOCK001", "沪深 300 ETF", "C4", "中高风险"),
        "国债逆回购", new ProductInfo("P-BOND001", "国债逆回购", "C2", "低风险"),
        "私募", new ProductInfo("P-PRIVATE001", "私募基金", "C5", "高风险")
    );

    @Override public VerifyType type() { return VerifyType.PRODUCT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.PRODUCT_KEYWORD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        String kw = entity.getValue();

        java.util.List<VerifyRef> refs = new java.util.ArrayList<>();
        for (Map.Entry<String, ProductInfo> e : PRODUCTS.entrySet()) {
            if (kw.contains(e.getKey())) {
                ProductInfo p = e.getValue();
                refs.add(VerifyRef.builder()
                    .id(p.code).title(p.code + " " + p.name)
                    .summary("风险等级: " + p.riskLevel + " (" + p.tag + ")")
                    .url("/trade?product=" + p.code)
                    .ts(System.currentTimeMillis())
                    .build());
            }
        }

        return VerifyResult.builder()
            .type(type()).entity(entity).success(!refs.isEmpty())
            .summary("匹配 " + refs.size() + " 个产品")
            .references(refs)
            .costMs(System.currentTimeMillis() - start)
            .build();
    }

    @Override public int cacheTtlSeconds() { return 300; }

    private record ProductInfo(String code, String name, String riskLevel, String tag) {}
}
