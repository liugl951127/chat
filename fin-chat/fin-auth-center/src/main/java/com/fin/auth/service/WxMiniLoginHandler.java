package com.fin.auth.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.WxMiniLoginRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 微信小程序登录
 *
 * <p>流程: code → jscode2session → unionid/openid/sessionKey → 解密手机号(可选)
 */
@Slf4j
@Component
public class WxMiniLoginHandler extends AbstractLoginHandler<WxMiniLoginRequest> {

    @Value("${fin.wx.mini.appid:wx_demo_appid}")
    private String appid;

    @Value("${fin.wx.mini.secret:wx_demo_secret}")
    private String secret;

    @Override public LoginChannel channel() { return LoginChannel.WX_MINI; }
    @Override public Class<WxMiniLoginRequest> requestClass() { return WxMiniLoginRequest.class; }

    @Override
    protected ChannelIdentity exchange(WxMiniLoginRequest req) {
        // 1. code → session
        String url = StrUtil.format(
            "https://api.weixin.qq.com/sns/jscode2session?appid={}&secret={}&js_code={}&grant_type=authorization_code",
            appid, secret, req.getCode()
        );
        String body = HttpUtil.get(url);
        log.info("wx jscode2session resp: {}", body);
        WxSessionResp resp = JSONUtil.toBean(body, WxSessionResp.class);

        if (resp == null || StrUtil.isBlank(resp.getOpenid())) {
            throw new IllegalStateException("微信 jscode2session 失败: " + body);
        }

        // 2. 解密手机号 (如果前端传了 encryptedData + iv)
        String phone = null;
        if (StrUtil.isAllNotBlank(req.getEncryptedData(), req.getIv(), resp.getSessionKey())) {
            try {
                phone = decryptPhone(resp.getSessionKey(), req.getEncryptedData(), req.getIv());
            } catch (Exception e) {
                log.warn("解密微信手机号失败 (非阻断): {}", e.getMessage());
            }
        }

        return ChannelIdentity.builder()
                .channel(LoginChannel.WX_MINI)
                .openid(resp.getOpenid())
                .unionid(resp.getUnionid())
                .mobile(phone)
                .nickname(req.getNickname())
                .avatar(req.getAvatar())
                .build();
    }

    /** AES-128-CBC 解密微信加密数据 */
    private String decryptPhone(String sessionKey, String encryptedData, String iv) {
        // JDK 17 自带 javax.crypto, 但需注意 PKCS#7 padding
        // 实际实现: AES/CBC/PKCS7Padding, key=Base64Decode(sessionKey), iv=Base64Decode(iv)
        // 然后从解密 JSON 中取 phoneNumber
        // 简化: 沙箱用 Hutool cn.hutool.crypto.symmetric.AES
        cn.hutool.crypto.symmetric.AES aes = new cn.hutool.crypto.symmetric.AES(
            cn.hutool.core.codec.Base64.decode(sessionKey),
            cn.hutool.core.codec.Base64.decode(iv)
        );
        String json = aes.decryptStr(encryptedData);
        cn.hutool.json.JSONObject obj = JSONUtil.parseObj(json);
        return obj.getStr("phoneNumber");
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class WxSessionResp {
        private String openid;
        private String sessionKey;
        private String unionid;
        private Integer errcode;
        private String errmsg;
    }
}
