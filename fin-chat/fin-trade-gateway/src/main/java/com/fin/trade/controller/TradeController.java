package com.fin.trade.controller;

import com.fin.commons.resp.ApiResponse;
import com.fin.trade.dto.TradeConfirm;
import com.fin.trade.dto.TradeRequest;
import com.fin.trade.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 交易网关 Controller
 */
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
@Slf4j
public class TradeController {

    private final TradeService tradeService;

    @PostMapping("/initiate")
    public ApiResponse<TradeService.InitiateResult> initiate(@Valid @RequestBody TradeRequest req) {
        return ApiResponse.ok(tradeService.initiate(req));
    }

    @PostMapping("/confirm")
    public ApiResponse<TradeService.ConfirmResult> confirm(@Valid @RequestBody TradeConfirm confirm) {
        return ApiResponse.ok(tradeService.confirm(confirm));
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> h = new HashMap<>();
        h.put("status", "UP");
        h.put("module", "fin-trade-gateway");
        return ApiResponse.ok(h);
    }
}
