package com.fin.compliance.service;

import com.fin.compliance.dto.RiskTest;
import com.fin.commons.util.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 风险测评服务
 *
 * <p>10 道题, 每题 1-5 分, 满分 50, 归一化到 0-100
 * <p>等级: C1(0-20) C2(21-40) C3(41-60) C4(61-80) C5(81-100)
 * <p>有效期 12 个月, 到期强制重测
 */
@Slf4j
@Service
public class RiskTestService {

    private final Map<Long, RiskTest> store = new ConcurrentHashMap<>();

    /** 提交测评 */
    public RiskTest submit(Long userId, List<Integer> answers) {
        if (answers == null || answers.size() != 10) {
            throw new IllegalArgumentException("必须 10 道题答案");
        }

        int total = answers.stream().mapToInt(Integer::intValue).sum();  // 10-50
        int score = (total - 10) * 100 / 40;  // 0-100

        String level = levelOf(score);

        RiskTest test = RiskTest.builder()
                .id(IdGenerator.nextId())
                .userId(userId)
                .answers(answers)
                .score(score)
                .level(level)
                .testedAt(LocalDateTime.now())
                .expireAt(LocalDateTime.now().plusMonths(12))
                .build();

        store.put(userId, test);
        log.info("[风险测评] userId={}, score={}, level={}", userId, score, level);
        return test;
    }

    public RiskTest get(Long userId) {
        return store.get(userId);
    }

    public boolean isExpired(Long userId) {
        RiskTest t = store.get(userId);
        return t == null || t.getExpireAt().isBefore(LocalDateTime.now());
    }

    /** 适当性匹配: 用户等级 vs 产品等级 */
    public boolean isAppropriate(String userLevel, String productLevel) {
        int u = levelRank(userLevel);
        int p = levelRank(productLevel);
        // 允许买 ≤ 自己等级的; 或同等级
        return u >= p;
    }

    private int levelRank(String level) {
        return switch (level) {
            case "C1" -> 1;
            case "C2" -> 2;
            case "C3" -> 3;
            case "C4" -> 4;
            case "C5" -> 5;
            default -> 1;
        };
    }

    private String levelOf(int score) {
        if (score <= 20) return "C1";
        if (score <= 40) return "C2";
        if (score <= 60) return "C3";
        if (score <= 80) return "C4";
        return "C5";
    }
}
