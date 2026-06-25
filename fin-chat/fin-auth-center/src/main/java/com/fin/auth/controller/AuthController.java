package com.fin.auth.controller;

import com.fin.auth.dto.*;
import com.fin.auth.service.*;
import com.fin.commons.resp.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 多端登录入口
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Auth", description = "多端登录")
public class AuthController {

    @Autowired private WxMiniLoginHandler wxMiniHandler;
    @Autowired private WxH5LoginHandler wxH5Handler;
    @Autowired private WecomLoginHandler wecomHandler;
    @Autowired private MobileLoginHandler mobileHandler;
    @Autowired private SmsService smsService;
    @Autowired private JwtIssuer jwtIssuer;

    /* ============== 多端登录 ============== */

    @PostMapping("/wx/mini/login")
    @Operation(summary = "微信小程序登录")
    public ApiResponse<LoginResponse> wxMiniLogin(@Valid @RequestBody WxMiniLoginRequest req,
                                                  HttpServletRequest http) {
        req.setClientIp(getClientIp(http));
        return ApiResponse.ok(wxMiniHandler.login(req));
    }

    @PostMapping("/wx/h5/login")
    @Operation(summary = "微信公众号 H5 登录")
    public ApiResponse<LoginResponse> wxH5Login(@Valid @RequestBody WxH5LoginRequest req,
                                                 HttpServletRequest http) {
        req.setClientIp(getClientIp(http));
        return ApiResponse.ok(wxH5Handler.login(req));
    }

    /** 预生成 state, 前端跳微信授权前调用 */
    @GetMapping("/wx/h5/state")
    @Operation(summary = "生成微信 H5 授权 state")
    public ApiResponse<Map<String, String>> wxH5State(@RequestHeader("X-Device-Id") String deviceId) {
        String state = wxH5Handler.generateState(deviceId);
        String url = "https://open.weixin.qq.com/connect/oauth2/authorize"
                   + "?appid=APPID&redirect_uri=REDIRECT_URI&response_type=code"
                   + "&scope=snsapi_userinfo&state=" + state + "#wechat_redirect";
        return ApiResponse.ok(Map.of("state", state, "authUrl", url));
    }

    @PostMapping("/wecom/login")
    @Operation(summary = "企业微信登录")
    public ApiResponse<LoginResponse> wecomLogin(@Valid @RequestBody WecomLoginRequest req,
                                                  HttpServletRequest http) {
        req.setClientIp(getClientIp(http));
        return ApiResponse.ok(wecomHandler.login(req));
    }

    @PostMapping("/mobile/login")
    @Operation(summary = "手机号 + 短信登录")
    public ApiResponse<LoginResponse> mobileLogin(@Valid @RequestBody MobileLoginRequest req,
                                                  HttpServletRequest http) {
        req.setClientIp(getClientIp(http));
        return ApiResponse.ok(mobileHandler.login(req));
    }

    /* ============== 短信 ============== */

    @PostMapping("/sms/send")
    @Operation(summary = "下发短信验证码")
    public ApiResponse<Map<String, Object>> sendSms(@RequestBody Map<String, String> body,
                                                    HttpServletRequest http) {
        String mobile = body.get("mobile");
        String bizStr = body.getOrDefault("biz", "LOGIN");
        SmsBizType biz = SmsBizType.valueOf(bizStr);
        SmsService.SmsSendResult r = smsService.send(mobile, biz, http.getRequestURI());
        return ApiResponse.ok(Map.of(
                "expireSeconds", r.getExpireSeconds(),
                "bizRef", r.getBizRef() == null ? "" : r.getBizRef()
        ));
    }

    /* ============== Token ============== */

    @PostMapping("/refresh")
    @Operation(summary = "刷新 access token")
    public ApiResponse<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) throw new IllegalArgumentException("refreshToken 必填");
        // 简化: 真实场景需 verify + 校验 Redis + 旋转
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", "demo-refreshed-" + System.currentTimeMillis());
        result.put("expiresIn", 900);
        return ApiResponse.ok(result);
    }

    @PostMapping("/logout")
    @Operation(summary = "登出 (拉黑 refresh token)")
    public ApiResponse<Void> logout(@RequestBody Map<String, String> body) {
        String userId = body.get("userId");
        String deviceId = body.get("deviceId");
        String refreshId = body.get("refreshId");
        if (userId != null && deviceId != null && refreshId != null) {
            jwtIssuer.revokeRefresh(userId, deviceId, refreshId);
        }
        return ApiResponse.ok();
    }

    /* ============== 工具 ============== */

    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = req.getRemoteAddr();
        }
        return ip;
    }
}
