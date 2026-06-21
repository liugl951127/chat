package com.fin.auth.service;

import cn.hutool.core.util.RandomUtil;
import com.fin.auth.service.SmsBizType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务
 *
 * <p>存 Redis 用 SM3 哈希, 校验时常量时间比对, 防爆破
 * <p>生产: 调 notify-center 发短信, 本服务只负责生成/校验
 */
@Slf4j
@Service
public class SmsService {

    @Autowired private StringRedisTemplate redis;
    @Autowired private KmsHttpClient kmsClient;

    @Value("${fin.sms.ttl-seconds:60}")
    private int ttlSeconds;

    @Value("${fin.sms.max-retry:5}")
    private int maxRetry;

    @Value("${fin.sms.cool-down:60}")
    private int coolDown;

    /** 下发验证码 */
    public SmsSendResult send(String mobile, SmsBizType biz, String bizRef) {
        // 1. 冷却: 60s 内同业务只能发一次
        String coolKey = "sms:cool:" + biz + ":" + kmsClient.sm3Hash(mobile);
        if (Boolean.TRUE.equals(redis.hasKey(coolKey))) {
            throw new IllegalArgumentException("请求过于频繁, 请 " + coolDown + " 秒后再试");
        }

        // 2. 生成 6 位数字
        String code = String.format("%06d", new SecureRandom().nextInt(1_000_000));

        // 3. SM3 哈希存 Redis
        String codeHash = kmsClient.sm3Hash(code);
        String key = "sms:code:" + biz + ":" + kmsClient.sm3Hash(mobile);
        redis.opsForValue().set(key, codeHash, ttlSeconds, TimeUnit.SECONDS);

        // 4. 冷却标记
        redis.opsForValue().set(coolKey, "1", coolDown, TimeUnit.SECONDS);

        // 5. 异步发短信 (沙箱: 直接 log; 生产: 调 notify-center HTTP)
        log.info("📱 [SMS] to={}, biz={}, code={}, ttl={}s", maskMobile(mobile), biz, code, ttlSeconds);

        return new SmsSendResult(ttlSeconds, bizRef);
    }

    /** 校验 */
    public boolean verify(String mobile, String code, SmsBizType biz) {
        String key = "sms:code:" + biz + ":" + kmsClient.sm3Hash(mobile);
        String stored = redis.opsForValue().get(key);
        if (stored == null) return false;

        // 1. 防爆破: 错误次数计数
        String errKey = "sms:err:" + biz + ":" + kmsClient.sm3Hash(mobile);
        Long errCount = redis.opsForValue().increment(errKey);
        redis.expire(errKey, 5, TimeUnit.MINUTES);
        if (errCount != null && errCount > maxRetry) {
            // 锁定 5 分钟
            String lockKey = "user:lock:" + kmsClient.sm3Hash(mobile);
            redis.opsForValue().set(lockKey, "sms_brute", 5, TimeUnit.MINUTES);
            throw new IllegalStateException("尝试次数过多, 账号已临时锁定 5 分钟");
        }

        // 2. 常量时间比对
        String inputHash = kmsClient.sm3Hash(code);
        boolean ok = MessageDigest.isEqual(
                stored.getBytes(),
                inputHash.getBytes()
        );

        if (ok) {
            redis.delete(key);
            redis.delete(errKey);
        }

        return ok;
    }

    /** 检查账号是否被锁 */
    public boolean isLocked(String mobile) {
        String lockKey = "user:lock:" + kmsClient.sm3Hash(mobile);
        return Boolean.TRUE.equals(redis.hasKey(lockKey));
    }

    private String maskMobile(String m) {
        if (m == null || m.length() < 7) return m;
        return m.substring(0, 3) + "****" + m.substring(7);
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SmsSendResult {
        private int expireSeconds;
        private String bizRef;
    }
}
