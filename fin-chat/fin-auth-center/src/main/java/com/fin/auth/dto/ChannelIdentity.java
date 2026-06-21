package com.fin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 渠道身份 (合并前中间态)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelIdentity {

    private LoginChannel channel;

    /** 微信 unionid (跨公众号/小程序/App 统一) */
    private String unionid;

    /** 渠道 openid (单渠道唯一) */
    private String openid;

    /** 企微 userid */
    private String wecomUserid;

    /** 手机号 (从微信解密 或 用户输入) */
    private String mobile;

    /** 渠道用户昵称/头像 (展示用) */
    private String nickname;
    private String avatar;
}
