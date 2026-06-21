package com.fin.auth.service;

import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.LoginRequest;
import com.fin.auth.entity.FinUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 登录审计 (异步落库 + 同步进 Redis 计数)
 */
@Slf4j
@Service
public class LoginAuditService {

    @Autowired private StringRedisTemplate redis;
    @Autowired private KmsHttpClient kmsClient;

    @Value("${fin.audit.endpoint:http://localhost:8093}")
    private String auditEndpoint;

    @Async
    public void recordSuccess(FinUser user, LoginChannel channel, LoginRequest req,
                              ChannelIdentity identity, long costMs) {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", user.getId());
        event.put("channel", channel.name());
        event.put("deviceId", req.getDeviceId());
        event.put("clientIp", req.getClientIp());
        event.put("unionid", identity.getUnionid());
        event.put("openid", identity.getOpenid());
        event.put("result", "SUCCESS");
        event.put("costMs", costMs);
        event.put("ts", LocalDateTime.now().toString());

        // 1. 同步进 Redis (近 1 小时)
        String key = "login:success:" + user.getId() + ":" + System.currentTimeMillis();
        redis.opsForValue().set(key, toJson(event), 3600);

        // 2. 异步推 audit-center (沙箱: log)
        log.info("[AUDIT] LOGIN_SUCCESS {}", toJson(event));
        // 生产: post(auditEndpoint + "/api/audit/record", event)
    }

    @Async
    public void recordFailure(LoginChannel channel, LoginRequest req, Exception e) {
        Map<String, Object> event = new HashMap<>();
        event.put("channel", channel.name());
        event.put("deviceId", req.getDeviceId());
        event.put("clientIp", req.getClientIp());
        event.put("result", "FAIL");
        event.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        event.put("ts", LocalDateTime.now().toString());

        String key = "login:fail:" + req.getDeviceId() + ":" + System.currentTimeMillis();
        redis.opsForValue().set(key, toJson(event), 3600);

        log.warn("[AUDIT] LOGIN_FAIL {}", toJson(event));
    }

    private String toJson(Map<String, Object> m) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(m);
        } catch (Exception e) {
            return m.toString();
        }
    }
}
