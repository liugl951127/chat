package com.fin.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 购买请求 (聊天内嵌产品卡片 → 购买)
 */
@Data
public class BuyRequest {
    @NotBlank(message = "产品 ID 必填")
    private String productId;

    @NotNull(message = "金额必填")
    @Positive(message = "金额必须 > 0")
    private BigDecimal amount;

    /** 来源会话 (聊天场景必传, 用于双录留痕关联) */
    private String conversationId;

    /** 设备 ID (前端生成, 风控用) */
    private String deviceId;

    /** 风险测评 ID (前端先做完测评再下单) */
    private String riskTestId;
}