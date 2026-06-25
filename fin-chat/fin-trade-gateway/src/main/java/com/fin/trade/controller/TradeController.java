package com.fin.trade.controller;

import com.fin.commons.resp.ApiResponse;
import com.fin.commons.user.UserContext;
import com.fin.trade.dto.BuyRequest;
import com.fin.trade.dto.OrderResponse;
import com.fin.trade.dto.TradeConfirm;
import com.fin.trade.dto.TradeRequest;
import com.fin.trade.entity.Order;
import com.fin.trade.entity.Product;
import com.fin.trade.order.OrderService;
import com.fin.trade.service.ProductService;
import com.fin.trade.service.SuitabilityEngine;
import com.fin.trade.service.TradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 交易网关 Controller
 *
 * <p>REST 端点:
 * <ul>
 *   <li>GET  /products                 - 在售产品列表</li>
 *   <li>GET  /products/{id}            - 产品详情</li>
 *   <li>GET  /products/recommend       - 按用户风险等级推荐</li>
 *   <li>POST /buy                      - 创建购买订单</li>
 *   <li>POST /orders/{id}/risk-test    - 风险测评完成回调</li>
 *   <li>POST /orders/{id}/dual-record  - 双录完成回调</li>
 *   <li>POST /orders/{id}/confirm      - 短信确认 + 进冷静期</li>
 *   <li>POST /orders/{id}/cancel       - 取消订单</li>
 *   <li>GET  /orders/{id}              - 订单详情</li>
 *   <li>GET  /orders                   - 我的订单列表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/trade")
@RequiredArgsConstructor
@Slf4j
public class TradeController {

    private final TradeService tradeService;
    private final OrderService orderService;
    private final ProductService productService;
    private final SuitabilityEngine suitabilityEngine;

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> h = new HashMap<>();
        h.put("status", "UP");
        h.put("module", "fin-trade-gateway");
        return ApiResponse.ok(h);
    }

    // ==================== 原有交易接口 (兼容) ====================
    @PostMapping("/initiate")
    public ApiResponse<TradeService.InitiateResult> initiate(@Valid @RequestBody TradeRequest req) {
        return ApiResponse.ok(tradeService.initiate(req));
    }

    @PostMapping("/confirm")
    public ApiResponse<TradeService.ConfirmResult> confirm(@Valid @RequestBody TradeConfirm confirm) {
        return ApiResponse.ok(tradeService.confirm(confirm));
    }

    // ==================== 产品 ====================
    @GetMapping("/products")
    public ApiResponse<List<Product>> listProducts() {
        return ApiResponse.ok(productService.listOnSale());
    }

    @GetMapping("/products/recommend")
    public ApiResponse<Map<String, Object>> recommend() {
        Long userId = UserContext.getUserId();
        if (userId == null) return ApiResponse.ok(Map.of("products", productService.listOnSale()));

        // 从 orderService cache 取风险等级, 没有默认 C1 (保守)
        String riskLevel = "C1";
        List<Product> matched = productService.listByRiskLevel(riskLevel);

        // 加适当性评估结果
        List<Map<String, Object>> annotated = matched.stream().map(p -> {
            SuitabilityEngine.SuitabilityResult s = suitabilityEngine.evaluate(riskLevel, p);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("product", p);
            m.put("suitability", s.getResult());
            m.put("suitabilityMsg", s.getMessage());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("userRiskLevel", riskLevel);
        resp.put("products", annotated);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/products/{id}")
    public ApiResponse<Product> getProduct(@PathVariable String id) {
        Product p = productService.get(id);
        if (p == null) throw new IllegalArgumentException("产品不存在");
        return ApiResponse.ok(p);
    }

    // ==================== 订单 ====================
    /** 创建订单 (聊天内嵌产品卡片 → 购买) */
    @PostMapping("/buy")
    public ApiResponse<OrderResponse> buy(@Valid @RequestBody BuyRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new IllegalStateException("未登录");
        Order order = orderService.create(userId, req.getConversationId(),
                req.getProductId(), req.getAmount(), req.getRiskTestId());
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 风险测评完成回调 */
    @PostMapping("/orders/{id}/risk-test")
    public ApiResponse<OrderResponse> afterRiskTest(@PathVariable String id,
                                                    @RequestBody Map<String, String> body) {
        String riskTestId = body.get("riskTestId");
        String userRiskLevel = body.getOrDefault("userRiskLevel", "C1");
        Order order = orderService.afterRiskTest(id, riskTestId, userRiskLevel);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 双录完成回调 */
    @PostMapping("/orders/{id}/dual-record")
    public ApiResponse<OrderResponse> completeDualRecord(@PathVariable String id,
                                                          @RequestBody Map<String, String> body) {
        String url = body.getOrDefault("url", "/api/v1/compliance/dual-record/" + id);
        Order order = orderService.completeDualRecord(id, url);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 短信确认 */
    @PostMapping("/orders/{id}/confirm")
    public ApiResponse<OrderResponse> confirm(@PathVariable String id,
                                               @RequestBody Map<String, String> body) {
        String smsCode = body.get("smsCode");
        Order order = orderService.confirm(id, smsCode);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 取消 */
    @PostMapping("/orders/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(@PathVariable String id) {
        Order order = orderService.cancel(id);
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 订单详情 */
    @GetMapping("/orders/{id}")
    public ApiResponse<OrderResponse> getOrder(@PathVariable String id) {
        Order order = orderService.get(id);
        if (order == null) throw new IllegalArgumentException("订单不存在");
        return ApiResponse.ok(OrderResponse.from(order));
    }

    /** 我的订单 */
    @GetMapping("/orders")
    public ApiResponse<List<OrderResponse>> listOrders() {
        Long userId = UserContext.getUserId();
        if (userId == null) return ApiResponse.ok(List.of());
        return ApiResponse.ok(orderService.listByUser(userId).stream()
                .map(OrderResponse::from).collect(Collectors.toList()));
    }
}