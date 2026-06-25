package com.fin.auth.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.WxH5LoginRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 微信公众号 H5 登录
 *
 * <p>OAuth 2.0 授权码模式完整流程:
 * <pre>
 *   1. 前端跳: https://open.weixin.qq.com/connect/oauth2/authorize
 *              ?appid=APPID&redirect_uri=REDIRECT&response_type=code
 *              &scope=snsapi_userinfo&state=STATE#wechat_redirect
 *   2. 用户同意后, 微信回调 redirect_uri?code=CODE&state=STATE
 *   3. 后端用 code 调 https://api.weixin.qq.com/sns/oauth2/access_token
 *      拿 access_token + openid + unionid
 *   4. 用 access_token + openid 调 /sns/userinfo 拿昵称头像
 *   5. unionid 入库
 * </pre>
 */
@Slf4j
@Component
public class WxH5LoginHandler extends AbstractLoginHandler<WxH5LoginRequest> {

    @Value("${fin.wx.h5.appid:wx_demo_h5_appid}")
    private String appid;

    @Value("${fin.wx.h5.secret:wx_demo_h5_secret}")
    private String secret;

    @Autowired
    private StringRedisTemplate redis;

    @Override public LoginChannel channel() { return LoginChannel.WX_H5; }
    @Override public Class<WxH5LoginRequest> requestClass() { return WxH5LoginRequest.class; }

    @Override
    protected void preValidate(WxH5LoginRequest req) {
        // 1. state 防 CSRF: 校验 Redis 里的 state
        String key = "wx:h5:state:" + req.getState();
        String stored = redis.opsForValue().get(key);
        if (stored == null) {
            throw new IllegalArgumentException("state 无效或已过期");
        }
        // 一次性, 用完即删
        redis.delete(key);
    }

    @Override
    protected ChannelIdentity exchange(WxH5LoginRequest req) {
        // 1. code → access_token
        String tokenUrl = StrUtil.format(
            "https://api.weixin.qq.com/sns/oauth2/access_token" +
            "?appid={}&secret={}&code={}&grant_type=authorization_code",
            appid, secret, req.getCode()
        );
        String tokenBody = HttpUtil.get(tokenUrl);
        log.info("wx H5 access_token resp: {}", tokenBody);
        WxTokenResp token = JSONUtil.toBean(tokenBody, WxTokenResp.class);

        if (token == null || StrUtil.isBlank(token.getAccess_token())) {
            throw new IllegalStateException("微信 access_token 获取失败: " + tokenBody);
        }

        // 2. access_token → 用户信息
        String userUrl = StrUtil.format(
            "https://api.weixin.qq.com/sns/userinfo" +
            "?access_token={}&openid={}&lang=zh_CN",
            token.getAccess_token(), token.getOpenid()
        );
        String userBody = HttpUtil.get(userUrl);
        JSONObject userObj = JSONUtil.parseObj(userBody);

        return ChannelIdentity.builder()
                .channel(LoginChannel.WX_H5)
                .openid(token.getOpenid())
                .unionid(token.getUnionid() == null ? userObj.getStr("unionid") : token.getUnionid())
                .nickname(userObj.getStr("nickname"))
                .avatar(userObj.getStr("headimgurl"))
                .build();
    }

    /** 预生成 state (前端调此接口拿 state, 再跳微信授权) */
    public String generateState(String deviceId) {
        String state = java.util.UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("wx:h5:state:" + state, deviceId, 5, TimeUnit.MINUTES);
        return state;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WxTokenResp {
        private String access_token;
        private Integer expires_in;
        private String refresh_token;
        private String openid;
        private String scope;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}
