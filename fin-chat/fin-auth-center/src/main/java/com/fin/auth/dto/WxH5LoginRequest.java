package com.fin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信 H5 (公众号) 登录请求
 *
 * <p>公众号 OAuth 2.0 授权码模式
 * <p>流程: 前端跳微信授权页 → 微信回调带 code → 后端用 code 换 access_token + openid
 */
@Data
public class WxH5LoginRequest extends LoginRequest {
    @NotBlank
    private String code;
    /** 公众号 appid (多公众号支持) */
    private String appid;
    /** state 必传, 防 CSRF, 与回调一致 */
    @NotBlank
    private String state;
}
