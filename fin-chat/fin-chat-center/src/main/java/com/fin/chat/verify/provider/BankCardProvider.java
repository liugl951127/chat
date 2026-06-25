package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 银行卡四要素验证 Provider (银联 / 连连)
 */
@Slf4j
@Component
public class BankCardProvider implements VerifyProvider {

    @Override public VerifyType type() { return VerifyType.BANK_CARD; }

    @Override
    public boolean supports(VerifyEntity entity) {
        // 需要 entity 上下文带身份证/姓名/手机, 这里简化
        return entity.getType() == EntityType.BANK_CARD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        // 沙箱: Luhn 校验
        String card = entity.getValue().replaceAll("\\s+", "");
        boolean valid = luhnCheck(card);

        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(valid)
                .summary(valid ? "银行卡号格式校验通过" : "银行卡号格式错误")
                .references(List.of())
                .riskScore(valid ? 0 : 60)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 0; }

    /** Luhn 算法校验 */
    public static boolean luhnCheck(String card) {
        if (card == null || card.length() < 13 || card.length() > 19) return false;
        int sum = 0;
        boolean alternate = false;
        for (int i = card.length() - 1; i >= 0; i--) {
            int n = card.charAt(i) - '0';
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return sum % 10 == 0;
    }
}
