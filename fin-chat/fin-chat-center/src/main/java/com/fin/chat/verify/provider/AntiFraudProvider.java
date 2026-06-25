package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 反欺诈 Provider (同盾/数美/顶象/极验)
 *
 * <p>设备指纹/行为分析/团伙欺诈识别
 */
@Slf4j
@Component
public class AntiFraudProvider implements VerifyProvider {

    @Value("${fin.verify.antifraud.url:https://api.tongdun.cn/v1/antifraud}")
    private String url;

    @Override public VerifyType type() { return VerifyType.RISK_LIST; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return true;  // 兜底, 所有实体都过一遍反欺诈
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        // 沙箱: 默认低风险
        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(true)
                .summary("反欺诈引擎扫描完成 (低风险)")
                .references(List.of())
                .riskScore(5)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 1800; }
}
