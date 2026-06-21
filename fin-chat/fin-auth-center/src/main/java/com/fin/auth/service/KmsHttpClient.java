package com.fin.auth.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * KMS HTTP 客户端 (沙箱)
 *
 * <p>生产: 用 @DubboReference / FeignClient 调 kms-gateway
 * <p>沙箱: 走 HSM 客户端本地调 (软算法)
 *
 * <p>接口契约见 {@link com.fin.kms.controller.KmsController}
 */
@Slf4j
@Component
public class KmsHttpClient {

    @Value("${fin.kms.url:http://localhost:8091}")
    private String kmsUrl;

    @Value("${fin.kms.soft-fallback:true}")
    private boolean softFallback;

    /* ============== SM3 ============== */

    public String sm3Hash(String data) {
        if (softFallback) {
            return cn.hutool.crypto.SecureUtil.sm3().digestHex(data);
        }
        return postForString("/api/kms/sm3/hash", Map.of("data", data), "hash");
    }

    /* ============== SM4 ============== */

    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        if (softFallback) {
            // 沙箱: 走 jvm 内软算法
            cn.hutool.crypto.symmetric.SM4 sm4 = new cn.hutool.crypto.symmetric.SM4(
                new byte[16], new byte[16],
                cn.hutool.crypto.symmetric.SM4.Mode.CBC,
                cn.hutool.crypto.symmetric.SM4.Padding.PKCS7Padding
            );
            return sm4.encrypt(plaintext);
        }
        // 生产: HTTP 调
        String hex = postForString("/api/kms/sm4/encrypt",
                Map.of("keyAlias", keyAlias, "data", new String(plaintext, StandardCharsets.UTF_8)),
                "hex");
        return cn.hutool.core.util.HexUtil.decodeHex(hex);
    }

    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        if (softFallback) {
            cn.hutool.crypto.symmetric.SM4 sm4 = new cn.hutool.crypto.symmetric.SM4(
                new byte[16], new byte[16],
                cn.hutool.crypto.symmetric.SM4.Mode.CBC,
                cn.hutool.crypto.symmetric.SM4.Padding.PKCS7Padding
            );
            return sm4.decrypt(ciphertext);
        }
        String plain = postForString("/api/kms/sm4/decrypt",
                Map.of("keyAlias", keyAlias, "data", cn.hutool.core.util.HexUtil.encodeHexStr(ciphertext)),
                "plain");
        return plain.getBytes(StandardCharsets.UTF_8);
    }

    /* ============== SM2 ============== */

    public Sm2SignResult sm2Sign(String keyAlias, byte[] data) {
        if (softFallback) {
            // 沙箱: 走 jvm 内 BC 算法
            return softSign(keyAlias, data);
        }
        Map<String, Object> resp = postForMap("/api/kms/sm2/sign",
                Map.of("keyAlias", keyAlias, "data", new String(data, StandardCharsets.UTF_8)));
        Sm2SignResult r = new Sm2SignResult();
        r.setSignature(cn.hutool.core.util.HexUtil.decodeHex((String) resp.get("signature")));
        r.setPublicKey(cn.hutool.core.util.HexUtil.decodeHex((String) resp.get("publicKey")));
        r.setAlgorithm((String) resp.get("algorithm"));
        return r;
    }

    private Sm2SignResult softSign(String keyAlias, byte[] data) {
        // 用 BC 软算法 (沙箱)
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance(
                    "EC", org.bouncycastle.jce.provider.BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new java.security.spec.ECGenParameterSpec("sm2p256v1"));
            java.security.KeyPair kp = kpg.generateKeyPair();
            String digest = cn.hutool.crypto.SecureUtil.sm3().digestHex(data);
            // 简化: 这里用 hutool SM2 API
            cn.hutool.crypto.SM2 sm2 = cn.hutool.crypto.SecureUtil.sm2(
                    (java.security.PrivateKey) kp.getPrivate(),
                    (java.security.PublicKey) kp.getPublic()
            );
            byte[] sig = sm2.sign(digest.getBytes(StandardCharsets.UTF_8));
            return new Sm2SignResult(sig, kp.getPublic().getEncoded(), "SM2withSM3");
        } catch (Exception e) {
            throw new IllegalStateException("软算法 SM2 签名失败", e);
        }
    }

    /* ============== HTTP 工具 ============== */

    private String postForString(String path, Map<String, Object> body, String field) {
        Map<String, Object> resp = postForMap(path, body);
        Object v = resp.get(field);
        return v == null ? null : v.toString();
    }

    private Map<String, Object> postForMap(String path, Map<String, Object> body) {
        String url = kmsUrl + path;
        log.debug("KMS POST {} body={}", url, body);
        String raw = HttpUtil.post(url, JSONUtil.toJsonStr(body), 5000);
        JSONObject obj = JSONUtil.parseObj(raw);
        if (obj.getInt("code", 0) != 0) {
            throw new IllegalStateException("KMS 调用失败: " + obj.getStr("message"));
        }
        return obj.getJSONObject("data").toBean(Map.class);
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class Sm2SignResult {
        private byte[] signature;
        private byte[] publicKey;
        private String algorithm;
    }
}
