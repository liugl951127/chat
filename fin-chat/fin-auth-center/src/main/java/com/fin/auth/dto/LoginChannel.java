package com.fin.auth.dto;

/**
 * 登录渠道
 */
public enum LoginChannel {
    WX_H5,        // 微信 H5 (公众号)
    WX_MINI,      // 微信小程序
    WECOM,        // 企业微信
    MOBILE,       // 手机号 + 验证码
    WEB,          // Web 后台
    APP_IOS,      // iOS 原生
    APP_ANDROID   // Android 原生
}
