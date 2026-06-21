package com.fin.trade.risk;

import com.fin.trade.dto.TradeRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 交易风控引擎 (简化版)
 *
 * <p>生产应接 compliance-center, 适当性/测评/限额/冷静期
 */
@Slf4j
@Service
public class TradeRiskEngine {

    private static final Set<String> HIGH_RISK_PRODUCTS = Set.of("FUTURE", "OPTION", "STRUCT_NOTE");
    private static final BigDecimal SINGLE_LIMIT = new BigDecimal("1000000");   // 单笔 100 万
    private static final BigDecimal DAILY_LIMIT = new BigDecimal("5000000");    // 单日 500 万

    public RiskDecision preCheck(TradeRequest req, Long userId) {
        // 1. 金额校验
        if (req.getAmount() == null || req.getAmount().signum() <= 0) {
            return RiskDecision.reject("AMOUNT_INVALID", "金额必须大于 0");
        }
        if (req.getAmount().compareTo(SINGLE_LIMIT) > 0) {
            log.warn("[风控] 单笔超限: userId={}, amount={}", userId, req.getAmount());
            return RiskDecision.reject("SINGLE_LIMIT", "超过单笔限额");
        }
        if (req.getAmount().compareTo(DAILY_LIMIT) > 0) {
            return RiskDecision.reject("DAILY_LIMIT", "超过单日累计限额");
        }

        // 2. 高风险产品 (沙箱简化: 都需要短信)
        boolean highRisk = HIGH_RISK_PRODUCTS.contains(req.getProductCode());
        boolean needSms = true;  // 沙箱: 任何交易都需要

        // 3. 业务类型
        if (!Set.of("BUY", "SELL", "SUBSCRIBE", "REDEEM").contains(req.getBizType())) {
            return RiskDecision.reject("BIZ_TYPE_INVALID", "不支持的业务类型");
        }

        log.info("[风控] 通过: userId={}, product={}, amount={}, highRisk={}",
                userId, req.getProductCode(), req.getAmount(), highRisk);
        return RiskDecision.pass(needSms);
    }
}
