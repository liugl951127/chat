package com.fin.trade.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 (理财/基金购买)
 *
 * <p>合规字段:
 * <ul>
 *   <li>riskLevel       - 用户风险等级</li>
 *   <li>suitability     - 适当性匹配结果 PASS / WARN / BLOCK</li>
 *   <li>coolOffUntil    - 冷静期截止时间 (24h)</li>
 *   <li>dualRecordId    - 双录 (录音录像) 会话 ID</li>
 *   <li>riskTestId      - 关联的风险测评</li>
 * </ul>
 *
 * <p>状态机: PENDING_RISK_TEST → PENDING_DUAL_RECORD → PENDING_CONFIRM
 *           → COOLING_OFF → CONFIRMED → SUCCESS / CANCELLED
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    /** 订单号 (T-yyyyMMdd-xxxxxxxx) */
    private String orderId;
    private Long userId;
    private String conversationId;
    /** 产品 ID */
    private String productId;
    /** 产品类型: FUND / WEALTH / INSURANCE */
    private String productType;
    private String productName;
    /** 购买金额 */
    private BigDecimal amount;
    /** 份额 / 份 */
    private BigDecimal shares;

    // ============ 合规字段 ============
    /** 用户风险等级: C1-C5 (保守/稳健/平衡/成长/激进) */
    private String riskLevel;
    /** 适当性匹配结果: PASS / WARN(需要告知) / BLOCK(禁止) */
    private String suitability;
    /** 关联风险测评 ID */
    private String riskTestId;
    /** 双录会话 ID */
    private String dualRecordId;
    /** 冷静期截止 (下单 24h 内) */
    private LocalDateTime coolOffUntil;
    /** 双录视频地址 */
    private String dualRecordUrl;

    // ============ 状态 ============
    /** PENDING_RISK_TEST / PENDING_DUAL_RECORD / PENDING_CONFIRM /
     *  COOLING_OFF / CONFIRMED / SUCCESS / CANCELLED / REJECTED */
    private String status;
    /** 拒绝原因 (合规不通过) */
    private String rejectReason;

    // ============ 时间戳 ============
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime confirmedAt;
}