package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 风险名单 Provider (同盾/数美/顶象)
 *
 * <p>黑名单/羊毛党/欺诈设备识别
 */
@Slf4j
@Component
public class RiskListProvider implements VerifyProvider {

    @Value("${fin.verify.risk.url:https://api.tongdun.cn/v1/risk}")
    private String url;

    @Override public VerifyType type() { return VerifyType.RISK_LIST; }

    @Override
    public boolean supports(VerifyEntity entity) {
        // 所有实体都可触发
        return true;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        // 沙箱: 默认无风险
        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(true)
                .summary("风险名单查询完成 (未命中)")
                .references(List.of())
                .riskScore(0)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 1800; }
}
