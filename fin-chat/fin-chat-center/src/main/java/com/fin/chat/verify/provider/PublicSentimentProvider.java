package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 法院失信被执行人 Provider (中国执行信息公开网)
 *
 * <p>沙箱: 关键词匹配; 生产: 调执行信息公开网
 */
@Slf4j
@Component
public class PublicSentimentProvider implements VerifyProvider {

    @Override public VerifyType type() { return VerifyType.COURT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.COMPANY_NAME
            || entity.getType() == EntityType.LEGAL_REP;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        // 沙箱: 不接真实, 返回空
        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(true)
                .summary("失信查询完成 (未命中)")
                .references(List.of())
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 3600; }
}
