package com.fin.trade.service;

import com.fin.trade.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 适当性匹配引擎
 *
 * <p>规则 (证监会的投资者适当性管理):
 * <ul>
 *   <li>PASS   - 用户风险等级 >= 产品最低投资者等级</li>
 *   <li>WARN   - 用户风险等级 = 产品等级 - 1 (降一档, 需告知风险)</li>
 *   <li>BLOCK  - 用户风险等级 < 产品等级 - 1 (禁止购买)</li>
 * </ul>
 *
 * <p>风险等级数值: C1=1, C2=2, C3=3, C4=4, C5=5
 *
 * @author Mavis
 */
@Slf4j
@Service
public class SuitabilityEngine {

    @Data
    @AllArgsConstructor
    public static class SuitabilityResult {
        private String result;        // PASS / WARN / BLOCK
        private String message;       // 给前端展示
        private boolean needWarnConfirm;  // 需要用户二次确认风险
    }

    /**
     * @param userRiskLevel  用户风险等级 C1-C5
     * @param product        产品
     */
    public SuitabilityResult evaluate(String userRiskLevel, Product product) {
        int userLev = parseLevel(userRiskLevel);
        int productLev = parseLevel(product.getMinInvestorLevel());

        if (userLev >= productLev) {
            return new SuitabilityResult("PASS",
                    "您的风险等级 " + userRiskLevel + " 满足本产品要求 " + product.getMinInvestorLevel(),
                    false);
        }
        if (userLev == productLev - 1) {
            return new SuitabilityResult("WARN",
                    "本产品风险等级(" + product.getMinInvestorLevel() +
                    ") 高于您的风险等级(" + userRiskLevel + "), 需您书面确认",
                    true);
        }
        return new SuitabilityResult("BLOCK",
                "您的风险等级 " + userRiskLevel + " 低于本产品最低要求 " +
                product.getMinInvestorLevel() + ", 不允许购买",
                false);
    }

    private int parseLevel(String s) {
        if (s == null || s.isBlank()) return 1;
        try { return Integer.parseInt(s.substring(1)); } catch (Exception e) { return 1; }
    }
}