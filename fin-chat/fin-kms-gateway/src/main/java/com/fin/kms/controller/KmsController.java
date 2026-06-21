package com.fin.kms.controller;

import cn.hutool.core.util.HexUtil;
import com.fin.commons.exception.BizException;
import com.fin.commons.resp.ApiResponse;
import com.fin.commons.resp.ErrorCode;
import com.fin.kms.KmsGatewayClient;
import com.fin.kms.KeySpec;
import com.fin.kms.SignatureResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * KMS 对外 HTTP API
 *
 * <p>其他模块 (auth-center, trade-gateway) 通过 HTTP 调本服务, 不直接连加密机
 *
 * <p>所有内部调用必须带服务间 mTLS 或内网白名单
 */
@RestController
@RequestMapping("/api/kms")
@Slf4j
public class KmsController {

    @Autowired
    private KmsGatewayClient kmsClient;

    /* ============== 健康 ============== */

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean ok = kmsClient.ping();
        return ApiResponse.ok(Map.of(
                "alive", ok,
                "type", kmsClient.getClass().getSimpleName()
        ));
    }

    /* ============== SM4 ============== */

    @PostMapping("/sm4/encrypt")
    public ApiResponse<HexResp> sm4Encrypt(@RequestBody @jakarta.validation.Valid SymmReq req) {
        try {
            byte[] ct = kmsClient.sm4Encrypt(req.getKeyAlias(), req.getData().getBytes());
            return ApiResponse.ok(new HexResp(HexUtil.encodeHexStr(ct)));
        } catch (Exception e) {
            log.error("SM4 加密失败: alias={}", req.getKeyAlias(), e);
            throw new BizException(ErrorCode.KMS_UNAVAILABLE, "SM4 加密失败: " + e.getMessage());
        }
    }

    @PostMapping("/sm4/decrypt")
    public ApiResponse<PlainResp> sm4Decrypt(@RequestBody @jakarta.validation.Valid SymmReq req) {
        try {
            byte[] pt = kmsClient.sm4Decrypt(req.getKeyAlias(), HexUtil.decodeHex(req.getData()));
            return ApiResponse.ok(new PlainResp(new String(pt)));
        } catch (Exception e) {
            log.error("SM4 解密失败: alias={}", req.getKeyAlias(), e);
            throw new BizException(ErrorCode.KMS_UNAVAILABLE, "SM4 解密失败: " + e.getMessage());
        }
    }

    /* ============== SM3 ============== */

    @PostMapping("/sm3/hash")
    public ApiResponse<HashResp> sm3Hash(@RequestBody @jakarta.validation.Valid HashReq req) {
        try {
            String h = kmsClient.sm3Hash(req.getData().getBytes());
            return ApiResponse.ok(new HashResp(h));
        } catch (Exception e) {
            throw new BizException(ErrorCode.KMS_UNAVAILABLE, "SM3 失败: " + e.getMessage());
        }
    }

    /* ============== SM2 签名 ============== */

    @PostMapping("/sm2/sign")
    public ApiResponse<SignResp> sm2Sign(@RequestBody @jakarta.validation.Valid SignReq req) {
        try {
            SignatureResult r = kmsClient.sm2Sign(req.getKeyAlias(), req.getData().getBytes());
            return ApiResponse.ok(new SignResp(
                    HexUtil.encodeHexStr(r.getSignature()),
                    HexUtil.encodeHexStr(r.getPublicKey()),
                    r.getAlgorithm()));
        } catch (Exception e) {
            log.error("SM2 签名失败: alias={}", req.getKeyAlias(), e);
            throw new BizException(ErrorCode.KMS_UNAVAILABLE, "SM2 签名失败: " + e.getMessage());
        }
    }

    /* ============== 密钥生成 ============== */

    @PostMapping("/key/generate")
    public ApiResponse<GenerateKeyResp> generateKey(@RequestBody @jakarta.validation.Valid GenerateKeyReq req) {
        try {
            KeySpec spec = new KeySpec(
                    req.getAlgorithm(),
                    req.getUsage(),
                    req.getAliasPrefix(),
                    req.getBusinessId(),
                    req.getExpireDays(),
                    req.getExportable());
            String alias = kmsClient.generateKey(spec);
            return ApiResponse.ok(new GenerateKeyResp(alias));
        } catch (Exception e) {
            throw new BizException(ErrorCode.KMS_UNAVAILABLE, "生成密钥失败: " + e.getMessage());
        }
    }

    /* ============== DTO ============== */

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SymmReq {
        @NotBlank private String keyAlias;
        @NotBlank private String data;       // base string, 服务端按 UTF-8
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class HashReq {
        @NotBlank private String data;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SignReq {
        @NotBlank private String keyAlias;
        @NotBlank private String data;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class HexResp {
        private String hex;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class PlainResp {
        private String plain;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class HashResp {
        private String hash;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SignResp {
        private String signature;
        private String publicKey;
        private String algorithm;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GenerateKeyReq {
        @NotBlank private String algorithm;  // SM2 / SM4
        @NotBlank private String usage;      // SIGN / ENC
        @NotBlank private String aliasPrefix;
        @NotBlank private String businessId;
        private int expireDays;
        private int exportable;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class GenerateKeyResp {
        private String keyAlias;
    }
}
