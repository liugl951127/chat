package com.fin.auth.service;

import com.fin.auth.dto.LoginRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 登录风控前置
 *
 * <p>- 频次限制 (单 IP + 单设备)
 * <p>- 黑名单检查
 * <p>- 异常时段提醒
 */
@Slf4j
@Service
public class RiskPreCheckService {

    @Autowired private StringRedisTemplate redis;
    @Autowired private KmsHttpClient kmsClient;

    @Value("${fin.login.max-per-minute:30}")
    private int maxPerMinute;

    public void preCheck(LoginRequest req) {
        String deviceKey = "login:dev:" + req.getDeviceId();
        String ipKey = "login:ip:" + (req.getClientIp() == null ? "unknown" : req.getClientIp());

        // 1. 设备频次
        Long devCount = redis.opsForValue().increment(deviceKey);
        redis.expire(deviceKey, 60, TimeUnit.SECONDS);
        if (devCount != null && devCount > maxPerMinute) {
            log.warn("[风控] 设备频次超限: device={}, count={}", req.getDeviceId(), devCount);
            throw new IllegalStateException("操作过于频繁, 请稍后再试");
        }

        // 2. IP 频次
        Long ipCount = redis.opsForValue().increment(ipKey);
        redis.expire(ipKey, 60, TimeUnit.SECONDS);
        if (ipCount != null && ipCount > maxPerMinute * 3) {
            log.warn("[风控] IP 频次超限: ip={}, count={}", req.getClientIp(), ipCount);
            throw new IllegalStateException("操作过于频繁, 请稍后再试");
        }

        // 3. 黑名单 (这里用 Redis set, 生产用规则引擎)
        if (Boolean.TRUE.equals(redis.hasKey("blacklist:device:" + req.getDeviceId()))) {
            throw new IllegalStateException("设备已被加入黑名单");
        }
    }
}
