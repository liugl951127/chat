package com.fin.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeRequest {
    /** 网关/服务侧注入 */
    private String tradeId;
    private Long userId;
    private String deviceId;
    private Long timestamp;

    @NotBlank
    private String productCode;
    @NotBlank
    private String bizType;           // BUY / SELL / SUBSCRIBE / REDEEM
    @NotNull @Positive
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal quantity;
    private String account;
    private String conversationId;    // 关联会话
}
