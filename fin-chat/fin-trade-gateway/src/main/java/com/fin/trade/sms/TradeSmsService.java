package com.fin.trade.sms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 交易短信服务
 *
 * <p>沙箱版: Redis 存 SM3 哈希验证码, 校验时常量时间比对
 * <p>生产: 调 notify-center 发短信, 调 kms-gateway 做 SM3
 */
@Slf4j
@Service
public class TradeSmsService {

    private static final int CODE_TTL = 60;
    private static final int MAX_RETRY = 5;

    private final StringRedisTemplate redis;

    public TradeSmsService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public SmsSendResult send(String mobile, String bizType) {
        // 1. 冷却
        String coolKey = "trade:sms:cool:" + bizType + ":" + mobile;
        if (Boolean.TRUE.equals(redis.hasKey(coolKey))) {
            throw new IllegalStateException("请求过于频繁");
        }

        // 2. 6 位数字
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        String hash = sha256(code);

        // 3. 存 (沙箱: 直接存, 生产用 SM3)
        String key = "trade:sms:code:" + bizType + ":" + mobile;
        redis.opsForValue().set(key, hash, CODE_TTL, TimeUnit.SECONDS);
        redis.opsForValue().set(coolKey, "1", 60, TimeUnit.SECONDS);

        log.info("📱 [TRADE-SMS] to={}, biz={}, code={}", maskMobile(mobile), bizType, code);
        return new SmsSendResult(CODE_TTL, code, LocalDateTime.now());
    }

    public boolean verify(String mobile, String code, String bizType) {
        String key = "trade:sms:code:" + bizType + ":" + mobile;
        String stored = redis.opsForValue().get(key);
        if (stored == null) return false;

        // 防爆破
        String errKey = "trade:sms:err:" + bizType + ":" + mobile;
        Long errCount = redis.opsForValue().increment(errKey);
        redis.expire(errKey, 5, TimeUnit.MINUTES);
        if (errCount != null && errCount > MAX_RETRY) {
            throw new IllegalStateException("尝试次数过多, 已临时锁定 5 分钟");
        }

        String inputHash = sha256(code);
        boolean ok = MessageDigest.isEqual(stored.getBytes(), inputHash.getBytes());
        if (ok) {
            redis.delete(key);
            redis.delete(errKey);
        }
        return ok;
    }

    private String sha256(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String maskMobile(String m) {
        if (m == null || m.length() < 7) return m;
        return m.substring(0, 3) + "****" + m.substring(7);
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class SmsSendResult {
        private int expireSeconds;
        private String code;       // 沙箱: 明文返回; 生产不返回
        private LocalDateTime sentAt;
    }
}
