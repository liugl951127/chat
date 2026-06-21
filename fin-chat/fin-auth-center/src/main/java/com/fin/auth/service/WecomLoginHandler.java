package com.fin.auth.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.WecomLoginRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 企业微信登录
 *
 * <p>流程: OAuth 授权码 → getuserinfo → wecom_userid → 关联账号
 *
 * <p>2 种场景:
 * <ol>
 *   <li>应用内免登: 企微侧边栏/工作台入口</li>
 *   <li>扫码登录: 浏览器扫码后回调</li>
 * </ol>
 */
@Slf4j
@Component
public class WecomLoginHandler extends AbstractLoginHandler<WecomLoginRequest> {

    @Value("${fin.wecom.corpid:ww_demo_corpid}")
    private String corpid;

    @Value("${fin.wecom.corpsecret:ww_demo_corpsecret}")
    private String corpsecret;

    @Value("${fin.wecom.agentid:1000001}")
    private String agentid;

    @Override public LoginChannel channel() { return LoginChannel.WECOM; }
    @Override public Class<WecomLoginRequest> requestClass() { return WecomLoginRequest.class; }

    @Override
    protected void preValidate(WecomLoginRequest req) {
        // 校验 state (防 CSRF)
        // 实际: 从 Redis 取出预存的 state 比对
        if (StrUtil.isBlank(req.getState())) {
            throw new IllegalArgumentException("state 必填");
        }
    }

    @Override
    protected ChannelIdentity exchange(WecomLoginRequest req) {
        // 1. 拿 access_token (生产应缓存, 沙箱实时拉)
        String tokenUrl = StrUtil.format(
            "https://qyapi.weixin.qq.com/cgi-bin/gettoken?corpid={}&corpsecret={}",
            corpid, corpsecret
        );
        String tokenBody = HttpUtil.get(tokenUrl);
        JSONObject tokenResp = JSONUtil.parseObj(tokenBody);
        String accessToken = tokenResp.getStr("access_token");
        if (StrUtil.isBlank(accessToken)) {
            throw new IllegalStateException("企微 access_token 获取失败: " + tokenBody);
        }

        // 2. code → userid
        String userInfoUrl = StrUtil.format(
            "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserinfo?access_token={}&code={}",
            accessToken, req.getCode()
        );
        String userBody = HttpUtil.get(userInfoUrl);
        JSONObject userResp = JSONUtil.parseObj(userBody);
        String wecomUserid = userResp.getStr("UserId");
        if (StrUtil.isBlank(wecomUserid)) {
            // 非企业成员: 走通讯录免登 user_ticket 流程
            wecomUserid = getUserIdByTicket(accessToken, req.getUserTicket());
        }
        if (StrUtil.isBlank(wecomUserid)) {
            throw new IllegalStateException("企微登录失败: " + userBody);
        }

        // 3. 拿用户详情 (头像/邮箱/手机)
        String detailUrl = StrUtil.format(
            "https://qyapi.weixin.qq.com/cgi-bin/user/get?access_token={}&userid={}",
            accessToken, wecomUserid
        );
        String detailBody = HttpUtil.get(detailUrl);
        JSONObject detailResp = JSONUtil.parseObj(detailBody);

        return ChannelIdentity.builder()
                .channel(LoginChannel.WECOM)
                .wecomUserid(wecomUserid)
                .mobile(detailResp.getStr("mobile"))
                .nickname(detailResp.getStr("name"))
                .avatar(detailResp.getStr("avatar"))
                .build();
    }

    private String getUserIdByTicket(String accessToken, String userTicket) {
        if (StrUtil.isBlank(userTicket)) return null;
        String url = "https://qyapi.weixin.qq.com/cgi-bin/auth/getuserdetail?access_token=" + accessToken;
        String body = HttpUtil.post(url, JSONUtil.toJsonStr(java.util.Map.of("user_ticket", userTicket)));
        return JSONUtil.parseObj(body).getStr("userid");
    }
}
