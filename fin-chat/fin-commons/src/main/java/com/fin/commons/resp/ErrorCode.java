package com.fin.commons.resp;

import lombok.Getter;

/**
 * 业务错误码
 *
 * 区间划分:
 *   0           成功
 *   1000-1999   鉴权/登录
 *   2000-2999   业务
 *   3000-3999   交易
 *   4000-4999   校验
 *   5000-5999   系统
 *   6000-6999   第三方
 *   7000-7999   合规
 */
@Getter
public enum ErrorCode {

    SUCCESS(0, "OK"),

    // 鉴权
    UNAUTHORIZED(1001, "未登录"),
    TOKEN_EXPIRED(1002, "登录已过期"),
    TOKEN_INVALID(1003, "无效的登录凭证"),
    SMS_CODE_INVALID(1101, "验证码错误或已过期"),
    SMS_CODE_FROZEN(1102, "尝试次数过多, 请稍后再试"),
    LOGIN_FAILED(1201, "登录失败"),
    WX_LOGIN_FAILED(1202, "微信登录失败"),
    WECOM_LOGIN_FAILED(1203, "企微登录失败"),
    REAL_NAME_REQUIRED(1301, "请先完成实名认证"),
    DEVICE_LOCKED(1401, "账号已临时锁定, 请稍后再试"),

    // 业务
    CONVERSATION_NOT_FOUND(2001, "会话不存在"),
    MESSAGE_SEND_FAILED(2101, "消息发送失败"),
    RATE_LIMIT(2201, "操作过于频繁, 请稍后再试"),

    // 交易
    TRADE_PRODUCT_INVALID(3001, "产品不存在或已下线"),
    TRADE_RISK_REJECT(3101, "风控拦截"),
    TRADE_INAPPROPRIATE(3102, "产品风险等级超过您的承受能力"),
    TRADE_RISK_TEST_EXPIRED(3103, "风险测评已过期, 请重新测评"),
    TRADE_COOLING(3104, "高风险产品首购有 24 小时冷静期"),
    TRADE_DAILY_LIMIT(3105, "超过单日累计限额"),
    TRADE_BLACKLIST(3106, "账户存在风险, 请联系客服"),
    TRADE_EXPIRED(3201, "交易已过期, 请重新发起"),
    TRADE_SIGN_FAILED(3202, "交易签名失败"),
    TRADE_CORE_ERROR(3301, "交易核心返回错误"),

    // 校验
    PARAM_INVALID(4001, "参数校验失败"),

    // 系统
    INTERNAL_ERROR(5000, "系统内部错误"),
    SERVICE_UNAVAILABLE(5001, "服务暂不可用"),
    KMS_UNAVAILABLE(5101, "加密机不可用"),

    // 第三方
    VERIFY_PROVIDER_ERROR(6001, "核查服务异常"),

    // 合规
    COMPLIANCE_VIOLATION(7001, "合规规则触发");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
