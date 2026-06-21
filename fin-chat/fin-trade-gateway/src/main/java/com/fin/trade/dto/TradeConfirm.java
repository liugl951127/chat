package com.fin.trade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class TradeConfirm {
    @NotBlank
    private String tradeId;
    @NotBlank
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须 6 位")
    private String smsCode;
}
