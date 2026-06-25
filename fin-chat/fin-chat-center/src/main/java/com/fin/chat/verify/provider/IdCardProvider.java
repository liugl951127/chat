package com.fin.chat.verify.provider;

import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 身份证二要素验证 (公安一所/银联)
 *
 * <p>沙箱: 简单格式校验 + 校验位
 * <p>生产: 调公安一所 (HTTPS + 国密 IPSec)
 */
@Slf4j
@Component
public class IdCardProvider implements VerifyProvider {

    private static final Pattern ID_CARD_PATTERN =
        Pattern.compile("^[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0\\d|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]$");

    @Override public VerifyType type() { return VerifyType.ID_CARD_VERIFY; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.ID_CARD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        String id = entity.getValue();

        boolean formatOk = ID_CARD_PATTERN.matcher(id).matches();
        boolean checksumOk = formatOk && checkChecksum(id);

        return VerifyResult.builder()
                .type(type()).entity(entity)
                .success(formatOk && checksumOk)
                .summary(formatOk && checksumOk
                    ? "身份证号格式 + 校验位通过"
                    : "身份证号格式或校验位错误")
                .references(List.of())
                .riskScore(formatOk ? 0 : 70)
                .costMs(System.currentTimeMillis() - start)
                .build();
    }

    @Override public int cacheTtlSeconds() { return 0; }

    /** GB 11643 校验位算法 */
    public static boolean checkChecksum(String id) {
        if (id.length() != 18) return false;
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checksums = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id.charAt(i) - '0') * weights[i];
        }
        int mod = sum % 11;
        return checksums[mod] == Character.toUpperCase(id.charAt(17));
    }
}
