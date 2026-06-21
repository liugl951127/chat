package com.fin.chat.controller;

import com.fin.chat.verify.VerifyEngine;
import com.fin.chat.verify.VerifyResult;
import com.fin.commons.resp.ApiResponse;
import com.fin.commons.user.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 联机核查 API
 */
@RestController
@RequestMapping("/api/v1/chat/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerifyEngine verifyEngine;

    /** 一站式: 提取 + 核查 */
    @PostMapping
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null) return ApiResponse.ok(Map.of("results", List.of()));

        long start = System.currentTimeMillis();
        List<VerifyResult> results = verifyEngine.extractAndVerify(text);
        long cost = System.currentTimeMillis() - start;

        Map<String, Object> resp = new HashMap<>();
        resp.put("results", results);
        resp.put("totalMs", cost);
        resp.put("userId", UserContext.getUserId());
        return ApiResponse.ok(resp);
    }

    /** 单纯提取实体 */
    @PostMapping("/extract")
    public ApiResponse<Map<String, Object>> extract(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        Map<String, Object> resp = new HashMap<>();
        resp.put("entities", verifyEngine.extract(text));
        return ApiResponse.ok(resp);
    }
}
