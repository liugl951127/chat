package com.fin.chat.verify.provider;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 个人征信 Provider (央行/百行/朴道)
 *
 * <p>需企业资质准入, 沙箱 mock
 */
@Slf4j
@Component
public class CreditProvider implements VerifyProvider {

    @Value("${fin.verify.credit.url:https://api.pudao.com/v1/credit/query}")
    private String url;

    @Override public VerifyType type() { return VerifyType.CREDIT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.ID_CARD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        try {
            // 沙箱: 直接返回空 (实际需调用征信接口)
            log.info("[CREDIT] verify: {}", entity.getValue());
            return VerifyResult.builder()
                    .type(type()).entity(entity)
                    .success(false)
                    .errorMessage("征信接口需要准入资质, 沙箱未对接")
                    .references(List.of())
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            return VerifyResult.degraded(type(), entity, e.getMessage());
        }
    }

    @Override public int cacheTtlSeconds() { return 0; }   // 不缓存 (实时)
}
