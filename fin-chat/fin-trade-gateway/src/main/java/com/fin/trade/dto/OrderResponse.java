package com.fin.trade.dto;

import com.fin.trade.entity.Order;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 订单响应 (前端展示用)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private String orderId;
    private String productId;
    private String productName;
    private String productType;
    private BigDecimal amount;
    private String status;
    private String suitability;
    private String riskLevel;
    private String dualRecordUrl;
    private String coolOffUntil;
    private String rejectReason;
    private String nextStep;       // 下一步提示: "请完成风险测评" 等
    private Boolean needDualRecord;

    public static OrderResponse from(Order o) {
        OrderResponse r = new OrderResponse();
        r.orderId = o.getOrderId();
        r.productId = o.getProductId();
        r.productName = o.getProductName();
        r.productType = o.getProductType();
        r.amount = o.getAmount();
        r.status = o.getStatus();
        r.suitability = o.getSuitability();
        r.riskLevel = o.getRiskLevel();
        r.dualRecordUrl = o.getDualRecordUrl();
        r.coolOffUntil = o.getCoolOffUntil() == null ? null : o.getCoolOffUntil().toString();
        r.rejectReason = o.getRejectReason();
        r.needDualRecord = "WEALTH".equals(o.getProductType()) || "INSURANCE".equals(o.getProductType());
        r.nextStep = nextStepFor(o);
        return r;
    }

    private static String nextStepFor(Order o) {
        switch (o.getStatus()) {
            case "PENDING_RISK_TEST":   return "请先完成风险测评";
            case "PENDING_DUAL_RECORD": return "请进行双录 (录音录像)";
            case "PENDING_CONFIRM":     return "请输入短信验证码确认";
            case "COOLING_OFF":         return "冷静期: " + (o.getCoolOffUntil() != null ? o.getCoolOffUntil().toString() : "");
            case "CONFIRMED":           return "冷静期已过, 订单确认中";
            case "SUCCESS":             return "购买成功";
            case "CANCELLED":           return "已取消";
            case "REJECTED":            return "已拒绝: " + o.getRejectReason();
            default:                    return "";
        }
    }
}