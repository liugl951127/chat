package com.fin.auth.service;

import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.MobileLoginRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 手机号 + 短信登录
 */
@Slf4j
@Component
public class MobileLoginHandler extends AbstractLoginHandler<MobileLoginRequest> {

    @Override public LoginChannel channel() { return LoginChannel.MOBILE; }
    @Override public Class<MobileLoginRequest> requestClass() { return MobileLoginRequest.class; }

    @Override
    protected void preValidate(MobileLoginRequest req) {
        // 校验短信验证码 (失败抛 BizException, 走全局拦截)
        boolean ok = smsService.verify(req.getMobile(), req.getSmsCode(), SmsBizType.LOGIN);
        if (!ok) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
    }

    @Override
    protected ChannelIdentity exchange(MobileLoginRequest req) {
        // 手机号本身就是身份, 不需要换 openid
        return ChannelIdentity.builder()
                .channel(LoginChannel.MOBILE)
                .mobile(req.getMobile())
                .build();
    }
}
