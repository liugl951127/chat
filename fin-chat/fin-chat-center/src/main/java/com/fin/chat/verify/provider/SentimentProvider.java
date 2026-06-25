package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 舆情监测 Provider (蚁坊/新浪舆情通)
 */
@Slf4j
@Component
public class SentimentProvider implements VerifyProvider {

    @Value("${fin.verify.sentiment.url:https://api.newsafer.com/v1/sentiment}")
    private String url;

    @Override public VerifyType type() { return VerifyType.PUBLIC_SENTIMENT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        // 任何文本都可触发舆情
        return true;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        // 沙箱: 简化 (生产调蚁坊/新浪)
        log.info("[SENTIMENT] scan: {}", entity.getValue());

        boolean negative = entity.getValue().matches(".*(跑路|爆雷|维权|投诉|违法|诈骗).*");
        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(true)
                .summary(negative ? "⚠ 检测到敏感舆情关键词" : "舆情无异常")
                .references(List.of())
                .riskScore(negative ? 75 : 5)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 300; }
}
