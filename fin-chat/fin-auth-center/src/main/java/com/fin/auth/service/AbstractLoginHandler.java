package com.fin.auth.service;

import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.dto.LoginChannel;
import com.fin.auth.dto.LoginRequest;
import com.fin.auth.dto.LoginResponse;
import com.fin.auth.dto.TokenPair;
import com.fin.auth.entity.FinUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 登录策略抽象基类
 *
 * <p>模板方法: 验签 → 限流 → 换 openid → 合并账号 → 颁 Token → 留痕
 */
@Slf4j
public abstract class AbstractLoginHandler<R extends LoginRequest> {

    @Autowired protected AccountMergeService mergeService;
    @Autowired protected JwtIssuer jwtIssuer;
    @Autowired protected LoginAuditService auditService;
    @Autowired protected RiskPreCheckService riskPreCheckService;
    @Autowired protected SmsService smsService;
    @Autowired protected KmsHttpClient kmsHttpClient;

    public abstract LoginChannel channel();
    public abstract Class<R> requestClass();

    /** 渠道换 openid/unionid/mobile */
    protected abstract ChannelIdentity exchange(R request);

    /** 前置校验 (签名/参数) */
    protected void preValidate(R request) {
        // 子类可重写: 验签 (微信 signature / 企微 msg_signature)
    }

    /** 登录主流程 */
    public LoginResponse login(R request) {
        long start = System.currentTimeMillis();

        try {
            // 1. 前置校验
            preValidate(request);

            // 2. 风控前置 (IP/设备/频次)
            riskPreCheckService.preCheck(request);

            // 3. 渠道换 openid
            ChannelIdentity identity = exchange(request);

            // 4. 合并/创建账号
            FinUser user = mergeService.mergeOrCreate(identity);

            // 5. 颁发双 Token
            TokenPair tokens = jwtIssuer.issue(user, request.getDeviceId());

            // 6. 留痕
            long cost = System.currentTimeMillis() - start;
            auditService.recordSuccess(user, channel(), request, identity, cost);

            // 7. 装配响应
            return LoginResponse.builder()
                    .userId(user.getId())
                    .accessToken(tokens.getAccessToken())
                    .refreshToken(tokens.getRefreshToken())
                    .expiresIn(tokens.getExpiresIn())
                    .realNameStatus(user.getRealNameStatus() == null ? 0 : user.getRealNameStatus())
                    .riskLevel(user.getRiskLevel() == null ? 1 : user.getRiskLevel())
                    .nickname(user.getNickname())
                    .avatar(user.getAvatar())
                    .realNameRequired(user.getRealNameStatus() == null || user.getRealNameStatus() < 2)
                    .build();
        } catch (Exception e) {
            auditService.recordFailure(channel(), request, e);
            throw e;
        }
    }
}
