package com.fin.auth.service;

/**
 * 短信业务类型
 *
 * <p>覆盖场景:
 * <ul>
 *   <li>LOGIN          - 登录验证</li>
 *   <li>REGISTER       - 注册</li>
 *   <li>RESET_PASSWORD - 找回密码</li>
 *   <li>BIND_DEVICE    - 绑定新设备</li>
 *   <li>TRADE_CONFIRM  - 交易确认 (下单/支付)</li>
 *   <li>REAL_NAME      - 实名认证 (合规要求, 单独模板)</li>
 *   <li>RISK_TEST      - 风险测评开启</li>
 *   <li>COOLING_OFF_REMIND - 冷静期到期提醒</li>
 * </ul>
 */
public enum SmsBizType {
    LOGIN,
    REGISTER,
    RESET_PASSWORD,
    BIND_DEVICE,
    TRADE_CONFIRM,
    /** 实名认证 (个人金融销售合规要求) */
    REAL_NAME,
    /** 风险测评开启 */
    RISK_TEST,
    /** 冷静期到期提醒 */
    COOLING_OFF_REMIND
}