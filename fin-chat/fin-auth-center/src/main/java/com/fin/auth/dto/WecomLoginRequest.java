package com.fin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WecomLoginRequest extends LoginRequest {
    /** 企微 OAuth 授权码 */
    @NotBlank
    private String code;

    /** 企微应用 corpId (多企业支持时由前端传) */
    private String corpId;

    /** 企微 state (防 CSRF, 前端生成回传) */
    private String state;

    /** 企微 user_ticket (通讯录免登场景) */
    private String userTicket;
}
