package com.fin.auth.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fin.auth.dto.ChannelIdentity;
import com.fin.auth.entity.FinUser;
import com.fin.auth.mapper.FinUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 账号合并 (unionid + 手机号)
 *
 * <p>合并策略:
 * <ol>
 *   <li>优先用 unionid 找 (同一微信用户在多端统一)</li>
 *   <li>用手机号 hash 兜底 (跨主体合并)</li>
 *   <li>都没有则创建</li>
 * </ol>
 */
@Slf4j
@Service
public class AccountMergeService {

    @Autowired private FinUserMapper userMapper;
    @Autowired private KmsHttpClient kmsClient;

    @Transactional
    public FinUser mergeOrCreate(ChannelIdentity identity) {
        // 1. 优先 unionid
        FinUser user = null;
        if (StrUtil.isNotBlank(identity.getUnionid())) {
            user = userMapper.selectByUnionid(identity.getUnionid());
            if (user != null) {
                log.debug("按 unionid 命中: userId={}", user.getId());
            }
        }

        // 2. 手机号 hash 兜底
        if (user == null && StrUtil.isNotBlank(identity.getMobile())) {
            String phoneHash = kmsClient.sm3Hash(identity.getMobile());
            user = userMapper.selectByMobileHash(phoneHash);
            if (user != null) {
                log.debug("按 mobile_hash 命中: userId={}", user.getId());
            }
        }

        // 3. 创建
        if (user == null) {
            user = new FinUser();
            user.setUnionid(identity.getUnionid());
            user.setStatus(1);
            user.setRealNameStatus(0);
            user.setRiskLevel(1);
            user.setNickname(identity.getNickname());
            user.setAvatar(identity.getAvatar());

            if (StrUtil.isNotBlank(identity.getMobile())) {
                user.setMobileHash(kmsClient.sm3Hash(identity.getMobile()));
                user.setMobileEnc(kmsClient.sm4Encrypt(
                        "user-mobile",
                        identity.getMobile().getBytes()
                ));
            }
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("创建新用户: userId={}, channel={}", user.getId(), identity.getChannel());
        } else {
            // 4. 已存在: 补全 openid/头像 (按渠道)
            updateOpenIdIfAbsent(user, identity);
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
        }

        // 5. 状态检查
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalStateException("账号已注销");
        }
        if (user.getStatus() != null && user.getStatus() == 2) {
            throw new IllegalStateException("账号已冻结");
        }

        return user;
    }

    private void updateOpenIdIfAbsent(FinUser user, ChannelIdentity identity) {
        // 这里简化为每次都补, 真实场景按渠道类型
        if (StrUtil.isNotBlank(identity.getWecomUserid())) {
            user.setWecomUserid(identity.getWecomUserid());
        }
        if (StrUtil.isBlank(user.getNickname()) && StrUtil.isNotBlank(identity.getNickname())) {
            user.setNickname(identity.getNickname());
        }
        if (StrUtil.isBlank(user.getAvatar()) && StrUtil.isNotBlank(identity.getAvatar())) {
            user.setAvatar(identity.getAvatar());
        }
    }
}
