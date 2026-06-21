package com.fin.compliance.controller;

import com.fin.compliance.dto.RiskTest;
import com.fin.compliance.service.RiskTestService;
import com.fin.commons.resp.ApiResponse;
import com.fin.commons.user.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规中心 Controller
 */
@RestController
@RequestMapping("/api/v1/compliance")
@RequiredArgsConstructor
@Slf4j
public class ComplianceController {

    private final RiskTestService riskTestService;

    /** 提交风险测评 */
    @PostMapping("/risk-test")
    public ApiResponse<RiskTest> submitRiskTest(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");
        @SuppressWarnings("unchecked")
        List<Integer> answers = (List<Integer>) body.get("answers");
        return ApiResponse.ok(riskTestService.submit(userId, answers));
    }

    /** 获取当前测评 */
    @GetMapping("/risk-test")
    public ApiResponse<RiskTest> getRiskTest() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");
        return ApiResponse.ok(riskTestService.get(userId));
    }

    /** 适当性匹配 */
    @GetMapping("/appropriate/{productCode}")
    public ApiResponse<Map<String, Object>> checkAppropriate(@PathVariable String productCode) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");
        RiskTest t = riskTestService.get(userId);
        if (t == null) {
            return ApiResponse.ok(Map.of(
                    "appropriate", false,
                    "reason", "请先完成风险测评"
            ));
        }
        // 沙箱: 假设所有产品 R3
        boolean ok = riskTestService.isAppropriate(t.getLevel(), "R3");
        Map<String, Object> r = new HashMap<>();
        r.put("appropriate", ok);
        r.put("userLevel", t.getLevel());
        r.put("productLevel", "R3");
        r.put("expired", riskTestService.isExpired(userId));
        return ApiResponse.ok(r);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "module", "fin-compliance-center"));
    }
}
