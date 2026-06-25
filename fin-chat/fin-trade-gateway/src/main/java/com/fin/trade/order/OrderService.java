package com.fin.trade.order;

import com.fin.commons.exception.BizException;
import com.fin.trade.entity.Order;
import com.fin.trade.entity.Product;
import com.fin.trade.service.ProductService;
import com.fin.trade.service.SuitabilityEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单服务
 *
 * <p>合规流程 (含状态机):
 * <pre>
 *   1. 创建订单 (PENDING_RISK_TEST)
 *      ↓ 通过风险测评
 *   2. PENDING_DUAL_RECORD (高风险理财/保险必须)
 *      ↓ 双录完成
 *   3. PENDING_CONFIRM (短信验证码)
 *      ↓ 确认
 *   4. COOLING_OFF (冷静期 24h, 保险 168h)
 *      ↓ 期满
 *   5. CONFIRMED → SUCCESS
 * </pre>
 *
 * <p>如果适当性 BLOCK, 直接 REJECTED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final SuitabilityEngine suitabilityEngine;

    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    /** mock 用户风险等级 (生产从 compliance-center 查) */
    private final Map<Long, String> userRiskCache = new ConcurrentHashMap<>();

    public void cacheUserRisk(Long userId, String riskLevel) {
        userRiskCache.put(userId, riskLevel);
    }

    /**
     * 创建订单
     */
    public Order create(Long userId, String conversationId, String productId,
                        BigDecimal amount, String riskTestId) {
        Product p = productService.get(productId);
        if (p == null) throw new IllegalArgumentException("产品不存在: " + productId);
        if (!"ON_SALE".equals(p.getStatus())) throw new IllegalStateException("产品已下架");

        // 起购金额校验
        if (amount.compareTo(p.getMinAmount()) < 0) {
            throw new IllegalArgumentException("起购金额 " + p.getMinAmount());
        }

        Order order = Order.builder()
                .orderId(genOrderId())
                .userId(userId)
                .conversationId(conversationId)
                .productId(p.getProductId())
                .productName(p.getProductName())
                .productType(p.getProductType())
                .amount(amount)
                .riskTestId(riskTestId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // 风险测评检查
        if (riskTestId == null) {
            order.setStatus("PENDING_RISK_TEST");
        } else {
            proceedToDualRecord(order, p);
        }

        orderStore.put(order.getOrderId(), order);
        log.info("[ORDER CREATE] {} user={} product={} amount={} status={}",
                order.getOrderId(), userId, productId, amount, order.getStatus());
        return order;
    }

    /** 风险测评后, 进入双录或确认 */
    public Order afterRiskTest(String orderId, String riskTestId, String userRiskLevel) {
        Order order = getOrThrow(orderId);
        if (!"PENDING_RISK_TEST".equals(order.getStatus()) &&
            !"PENDING_DUAL_RECORD".equals(order.getStatus())) {
            // 已过风险测评, 重新评估适当性
        }
        order.setRiskTestId(riskTestId);
        order.setRiskLevel(userRiskLevel);
        // 缓存用户风险等级
        cacheUserRisk(order.getUserId(), userRiskLevel);
        Product p = productService.get(order.getProductId());
        proceedToDualRecord(order, p);
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private void proceedToDualRecord(Order order, Product p) {
        // 适当性校验
        String userLevel = order.getRiskLevel() != null ? order.getRiskLevel()
                : userRiskCache.getOrDefault(order.getUserId(), "C1");
        SuitabilityEngine.SuitabilityResult s = suitabilityEngine.evaluate(userLevel, p);
        order.setSuitability(s.getResult());
        log.info("[SUITABILITY] order={} user={} product={} result={}",
                order.getOrderId(), userLevel, p.getMinInvestorLevel(), s.getResult());

        if ("BLOCK".equals(s.getResult())) {
            order.setStatus("REJECTED");
            order.setRejectReason("适当性不匹配: " + s.getMessage());
            return;
        }

        // 是否需要双录
        if (Boolean.TRUE.equals(p.getRequireDualRecord())) {
            order.setStatus("PENDING_DUAL_RECORD");
            order.setDualRecordId("DR-" + order.getOrderId());
            order.setDualRecordUrl("/api/v1/compliance/dual-record/" + order.getOrderId());
        } else {
            order.setStatus("PENDING_CONFIRM");
        }
    }

    /** 完成双录 */
    public Order completeDualRecord(String orderId, String dualRecordUrl) {
        Order order = getOrThrow(orderId);
        if (!"PENDING_DUAL_RECORD".equals(order.getStatus())) {
            throw new IllegalStateException("订单不在双录状态: " + order.getStatus());
        }
        order.setDualRecordUrl(dualRecordUrl);
        order.setStatus("PENDING_CONFIRM");
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /** 短信验证 + 进入冷静期 */
    public Order confirm(String orderId, String smsCode) {
        Order order = getOrThrow(orderId);
        if (!"PENDING_CONFIRM".equals(order.getStatus())) {
            throw new IllegalStateException("订单不在确认状态: " + order.getStatus());
        }
        // 沙箱: 任何 6 位都过
        if (smsCode == null || !smsCode.matches("\\d{6}")) {
            throw new IllegalArgumentException("验证码格式错误");
        }

        // 冷静期
        Product p = productService.get(order.getProductId());
        int coolOffHours = p.getCoolOffHours() != null ? p.getCoolOffHours() : 24;
        if (coolOffHours > 0) {
            order.setCoolOffUntil(LocalDateTime.now().plusHours(coolOffHours));
            order.setStatus("COOLING_OFF");
        } else {
            order.setStatus("CONFIRMED");
        }
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    /** 冷静期后真正生效 */
    public Order settle(String orderId) {
        Order order = getOrThrow(orderId);
        if ("COOLING_OFF".equals(order.getStatus())) {
            if (LocalDateTime.now().isBefore(order.getCoolOffUntil())) {
                throw new IllegalStateException("冷静期未结束");
            }
            order.setStatus("CONFIRMED");
        }
        if ("CONFIRMED".equals(order.getStatus())) {
            // 沙箱: 直接成功
            order.setStatus("SUCCESS");
            order.setUpdatedAt(LocalDateTime.now());
        }
        return order;
    }

    /** 取消 (冷静期内用户可取消) */
    public Order cancel(String orderId) {
        Order order = getOrThrow(orderId);
        if ("COOLING_OFF".equals(order.getStatus()) ||
            "PENDING_CONFIRM".equals(order.getStatus()) ||
            "PENDING_DUAL_RECORD".equals(order.getStatus()) ||
            "PENDING_RISK_TEST".equals(order.getStatus())) {
            order.setStatus("CANCELLED");
            order.setUpdatedAt(LocalDateTime.now());
            return order;
        }
        throw new IllegalStateException("当前状态不可取消: " + order.getStatus());
    }

    public Order get(String orderId) { return orderStore.get(orderId); }

    public List<Order> listByUser(Long userId) {
        List<Order> list = new ArrayList<>();
        for (Order o : orderStore.values()) {
            if (userId.equals(o.getUserId())) list.add(o);
        }
        list.sort(Comparator.comparing(Order::getCreatedAt).reversed());
        return list;
    }

    private Order getOrThrow(String orderId) {
        Order order = orderStore.get(orderId);
        if (order == null) throw new IllegalArgumentException("订单不存在: " + orderId);
        return order;
    }

    private String genOrderId() {
        return "T-" + LocalDateTime.now().toString().substring(0, 10).replace("-", "") +
                "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}