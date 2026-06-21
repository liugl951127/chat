package com.fin.auth.service;

import com.fin.auth.dto.TokenPair;
import com.fin.auth.entity.FinUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 颁发 (RS256 / SM2 签名)
 *
 * <p>关键点:
 * <ul>
 *   <li>Access Token 15 min, Refresh Token 14 d (一次性, 旋转)</li>
 *   <li>签名私钥从 KMS 取 (生产: 加密机, 永不外传)</li>
 *   <li>Refresh 进 Redis, 登出/重置密码时拉黑</li>
 * </ul>
 */
@Slf4j
@Service
public class JwtIssuer {

    @Autowired private KmsHttpClient kmsClient;
    @Autowired private StringRedisTemplate redis;

    @Value("${fin.jwt.issuer:fin-auth}")
    private String issuer;

    @Value("${fin.jwt.access-ttl-seconds:900}")
    private long accessTtl;

    @Value("${fin.jwt.refresh-ttl-seconds:1209600}")  // 14 天
    private long refreshTtl;

    @Value("${fin.jwt.signing-key-alias:jwt-signing}")
    private String signingKeyAlias;

    public TokenPair issue(FinUser user, String deviceId) {
        Instant now = Instant.now();
        String userId = String.valueOf(user.getId());

        // 1. Access Token
        Map<String, Object> accessClaims = new HashMap<>();
        accessClaims.put("device", deviceId);
        accessClaims.put("unionid", user.getUnionid());
        accessClaims.put("rl", user.getRiskLevel() == null ? 1 : user.getRiskLevel());
        accessClaims.put("rns", user.getRealNameStatus() == null ? 0 : user.getRealNameStatus());
        accessClaims.put("scope", "chat,trade,read");

        String access = signWithKms(Jwts.builder()
                .header().add("typ", "JWT").add("alg", "SM2").and()
                .issuer(issuer)
                .subject(userId)
                .claims(accessClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtl)))
                .id(UUID.randomUUID().toString()));

        // 2. Refresh Token
        String refreshId = UUID.randomUUID().toString();
        String refresh = signWithKms(Jwts.builder()
                .issuer(issuer)
                .subject(userId)
                .claim("device", deviceId)
                .claim("type", "refresh")
                .id(refreshId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshTtl))));

        // 3. Refresh 进 Redis (用于登出/重置密码时拉黑)
        String rtKey = "rt:" + userId + ":" + deviceId + ":" + refreshId;
        redis.opsForValue().set(rtKey, "1", Duration.ofSeconds(refreshTtl));

        return TokenPair.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(accessTtl)
                .build();
    }

    /** 验签 (网关侧用) */
    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(publicKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 注销 Refresh Token (拉黑) */
    public void revokeRefresh(String userId, String deviceId, String refreshId) {
        String key = "rt:" + userId + ":" + deviceId + ":" + refreshId;
        redis.delete(key);
    }

    /** 校验 Refresh Token 还在 Redis (未被拉黑) */
    public boolean isRefreshActive(String userId, String deviceId, String refreshId) {
        return Boolean.TRUE.equals(redis.hasKey("rt:" + userId + ":" + deviceId + ":" + refreshId));
    }

    /** 签名 (沙箱: HS256 demo key, 生产: KMS SM2) */
    private String signWithKms(io.jsonwebtoken.JwtBuilder builder) {
        // TODO 生产: 调 kmsHttpClient.sm2Sign(signingKeyAlias, builder.build().getEncoded())
        //          然后拼装 header.payload.signature 三段
        // 沙箱: 用 jjwt 默认 HS256, demo 密钥 (生产必须禁)
        String demoKey = "fin-chat-demo-signing-key-please-replace-in-production-min-32bytes";
        return builder
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(demoKey.getBytes()),
                          io.jsonwebtoken.Jwts.SIG.HS256)
                .compact();
    }

    private java.security.PublicKey publicKey() {
        // 沙箱: 返回 null, jjwt 用对称 fallback
        return null;
    }
}
