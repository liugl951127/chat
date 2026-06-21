package com.fin.notify.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendSmsRequest {
    @NotBlank
    private String mobile;
    @NotBlank
    private String biz;          // LOGIN / TRADE_CONFIRM
    @NotBlank
    private String templateCode;  // SMS_001 / SMS_002
    @NotBlank
    private String content;      // 实际短信内容 (含验证码等)
}
