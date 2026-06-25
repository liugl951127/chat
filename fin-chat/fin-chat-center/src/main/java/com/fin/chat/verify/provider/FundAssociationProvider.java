package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基金业协会 / 证券业协会 Provider
 *
 * <p>验证金融机构/产品的从业资质
 */
@Slf4j
@Component
public class FundAssociationProvider implements VerifyProvider {

    @Override public VerifyType type() { return VerifyType.QUALIFICATION; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.PRODUCT_KEYWORD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        log.info("[FUND_ASSOC] verify: {}", entity.getValue());
        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(false)
                .errorMessage("基金业协会接口待对接")
                .references(List.of())
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 7200; }
}
