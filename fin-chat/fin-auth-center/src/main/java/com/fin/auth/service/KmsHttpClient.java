package com.fin.auth.service;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Security;
import java.util.Map;

/**
 * KMS HTTP 客户端 (沙箱)
 *
 * <p>沙箱: 走本地 BC 算法
 * <p>生产: HTTP 调 kms-gateway
 */
@Slf4j
@Component
public class KmsHttpClient {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${fin.kms.url:http://localhost:8091}")
    private String kmsUrl;

    @Value("${fin.kms.soft-fallback:true}")
    private boolean softFallback;

    /* ============== SM3 ============== */

    public String sm3Hash(String data) {
        if (softFallback) {
            try {
                byte[] hash = MessageDigest.getInstance("SM3", BouncyCastleProvider.PROVIDER_NAME)
                    .digest(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
                return Hex.toHexString(hash);
            } catch (Exception e) {
                throw new IllegalStateException("SM3 失败: " + e.getMessage(), e);
            }
        }
        return postForString("/api/kms/sm3/hash", Map.of("data", data), "hash");
    }

    /* ============== SM4 ============== */

    public byte[] sm4Encrypt(String keyAlias, byte[] plaintext) {
        // 沙箱简化: 用 XOR + SM3 hash 作 key
        if (softFallback) {
            try {
                byte[] key = sm3Hash(keyAlias).substring(0, 32).getBytes();
                byte[] out = new byte[plaintext.length];
                for (int i = 0; i < plaintext.length; i++) {
                    out[i] = (byte) (plaintext[i] ^ key[i % key.length]);
                }
                return out;
            } catch (Exception e) {
                throw new IllegalStateException("SM4 沙箱模拟失败", e);
            }
        }
        String hex = postForString("/api/kms/sm4/encrypt",
                Map.of("keyAlias", keyAlias, "data", new String(plaintext, StandardCharsets.UTF_8)),
                "hex");
        return Hex.decode(hex);
    }

    public byte[] sm4Decrypt(String keyAlias, byte[] ciphertext) {
        if (softFallback) {
            return sm4Encrypt(keyAlias, ciphertext);  // XOR 自反
        }
        String plain = postForString("/api/kms/sm4/decrypt",
                Map.of("keyAlias", keyAlias, "data", Hex.toHexString(ciphertext)),
                "plain");
        return plain.getBytes(StandardCharsets.UTF_8);
    }

    /* ============== SM2 ============== */

    public Sm2SignResult sm2Sign(String keyAlias, byte[] data) {
        if (softFallback) {
            // 沙箱: 返回 SM3 摘要作伪签名
            String digest = sm3Hash(new String(data, StandardCharsets.UTF_8));
            return new Sm2SignResult(digest.getBytes(), digest.getBytes(), "SANDBOX-SM3");
        }
        Map<String, Object> resp = postForMap("/api/kms/sm2/sign",
                Map.of("keyAlias", keyAlias, "data", new String(data, StandardCharsets.UTF_8)));
        Sm2SignResult r = new Sm2SignResult();
        r.setSignature(Hex.decode((String) resp.get("signature")));
        r.setPublicKey(Hex.decode((String) resp.get("publicKey")));
        r.setAlgorithm((String) resp.get("algorithm"));
        return r;
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
