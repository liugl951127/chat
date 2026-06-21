package com.fin.trade.service;

import com.fin.commons.exception.BizException;
import com.fin.commons.resp.ErrorCode;
import com.fin.commons.user.UserContext;
import com.fin.commons.util.IdGenerator;
import com.fin.trade.dto.TradeConfirm;
import com.fin.trade.dto.TradeRequest;
import com.fin.trade.risk.RiskDecision;
import com.fin.trade.risk.TradeRiskEngine;
import com.fin.trade.sign.TradeSigner;
import com.fin.trade.sms.TradeSmsService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 交易服务
 *
 * <p>initiate → 缓存 + 发短信
 * <p>confirm → 校验短信 + 风控 + 签名 + 提交
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

    private final TradeRiskEngine riskEngine;
    private final TradeSmsService smsService;
    private final TradeSigner signer;
    private final StringRedisTemplate redis;

    private static final int TRADE_TTL = 300;  // 5 分钟

    public InitiateResult initiate(TradeRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BizException(ErrorCode.UNAUTHORIZED);

        req.setUserId(userId);
        req.setDeviceId(UserContext.getDevice());
        req.setTimestamp(Instant.now().getEpochSecond());

        // 1. 风控
        RiskDecision dec = riskEngine.preCheck(req, userId);
        if (!dec.isPassed()) {
            throw new BizException(ErrorCode.TRADE_RISK_REJECT, dec.getMessage());
        }
        if (!dec.isSmsRequired()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "本交易无需二次验证");
        }

        // 2. 缓存 tradeId (沙箱: 用拼接格式, 生产用 JSON)
        String tradeId = IdGenerator.tradeId();
        req.setTradeId(tradeId);
        String cacheKey = "trade:init:" + tradeId;
        String cached = req.getProductCode() + "|" + req.getBizType() + "|"
                + req.getAmount().toPlainString() + "|" + (req.getAccount() == null ? "" : req.getAccount());
        redis.opsForValue().set(cacheKey, cached, TRADE_TTL, TimeUnit.SECONDS);

        // 3. 发短信 (沙箱: mobile 用占位)
        String mobile = "13800138000";  // 真实: 查 user 表
        TradeSmsService.SmsSendResult sms = smsService.send(mobile, "TRADE_CONFIRM");

        log.info("[交易发起] tradeId={}, userId={}, amount={}", tradeId, userId, req.getAmount());
        return new InitiateResult(tradeId, TRADE_TTL, sms.getCode() == null ? null : "***"   // 不返回明文
        );
    }

    public ConfirmResult confirm(TradeConfirm confirm) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BizException(ErrorCode.UNAUTHORIZED);

        // 1. 取出原始 trade
        String cacheKey = "trade:init:" + confirm.getTradeId();
        String cached = redis.opsForValue().get(cacheKey);
        if (cached == null) throw new BizException(ErrorCode.TRADE_EXPIRED);

        // 沙箱: 简化解析 (生产用 JSON)
        // 这里我们重做一次 req, 实际生产应反序列化
        TradeRequest req = parseCachedTrade(cached, confirm.getTradeId());
        req.setUserId(userId);

        // 2. 短信校验
        String mobile = "13800138000";
        boolean smsOk = smsService.verify(mobile, confirm.getSmsCode(), "TRADE_CONFIRM");
        if (!smsOk) throw new BizException(ErrorCode.SMS_CODE_INVALID);

        // 3. 重新风控
        RiskDecision dec = riskEngine.preCheck(req, userId);
        if (!dec.isPassed()) throw new BizException(ErrorCode.TRADE_RISK_REJECT, dec.getMessage());

        // 4. 签名
        TradeSigner.SignedTrade signed = signer.sign(req);

        // 5. 模拟提交核心
        String coreSerial = "CORE-" + IdGenerator.nextIdStr();
        log.info("[交易完成] tradeId={}, userId={}, coreSerial={}", confirm.getTradeId(), userId, coreSerial);

        // 6. 清缓存
        redis.delete(cacheKey);

        return new ConfirmResult(confirm.getTradeId(), "SUCCESS", coreSerial,
                signed.getDigest(), signed.getAlgorithm());
    }

    private TradeRequest parseCachedTrade(String cached, String tradeId) {
        // 沙箱: 简化, 直接构造 (生产用 JSON 反序列化)
        // 格式: productCode|bizType|amount|account
        TradeRequest req = new TradeRequest();
        req.setTradeId(tradeId);
        try {
            String[] parts = cached.split("\\|");
            if (parts.length >= 4) {
                req.setProductCode(parts[0]);
                req.setBizType(parts[1]);
                req.setAmount(new BigDecimal(parts[2]));
                req.setAccount(parts[3]);
            }
        } catch (Exception e) {
            log.warn("解析缓存交易失败, 用空对象: {}", e.getMessage());
        }
        return req;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class InitiateResult {
        private String tradeId;
        private int expireSeconds;
        private String codeHint;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ConfirmResult {
        private String tradeId;
        private String status;
        private String coreSerial;
        private String signature;
        private String algorithm;
    }
}
