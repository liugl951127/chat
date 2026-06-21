package com.fin.trade.sign;

import com.fin.trade.dto.TradeRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

/**
 * 交易签名 (沙箱版: 客户端计算原文 + 沙箱 SM3 摘要)
 *
 * <p>生产: 调 kms-gateway 做 SM2 签名, 私钥在加密机
 */
@Slf4j
@Service
public class TradeSigner {

    /** 标准化交易原文 (类似 JWT canonical JSON, 按字段名排序) */
    public String canonicalize(TradeRequest req) {
        Map<String, Object> sorted = new TreeMap<>();
        sorted.put("userId", req.getUserId() == null ? "" : req.getUserId());
        sorted.put("productCode", req.getProductCode());
        sorted.put("bizType", req.getBizType());
        sorted.put("amount", req.getAmount() == null ? "0" : req.getAmount().toPlainString());
        sorted.put("price", req.getPrice() == null ? "0" : req.getPrice().toPlainString());
        sorted.put("quantity", req.getQuantity() == null ? "0" : req.getQuantity().toPlainString());
        sorted.put("deviceId", req.getDeviceId() == null ? "" : req.getDeviceId());
        sorted.put("ts", req.getTimestamp() == null ? Instant.now().getEpochSecond() : req.getTimestamp());
        return sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    /** SM3 摘要 (沙箱用 SHA-256 替代, 生产调 kms-gateway) */
    public String digest(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 完整签名结果 (沙箱: 简化为摘要) */
    public SignedTrade sign(TradeRequest req) {
        String canonical = canonicalize(req);
        String digest = digest(canonical);
        log.info("[签名] tradeId={}, digest={}", req.getTradeId(), digest);
        return new SignedTrade(canonical, digest, "SHA256-SANDBOX");
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class SignedTrade {
        private String canonical;
        private String digest;
        private String algorithm;
    }
}
