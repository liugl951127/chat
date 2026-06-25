package com.fin.trade.service;

import com.fin.trade.entity.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 产品服务 (沙箱内存版, 生产接 MongoDB)
 *
 * <p>包含 6 只产品: 货币基金 / 债券基金 / 混合基金 / 股票基金 / 银行理财 / 保险
 */
@Slf4j
@Service
public class ProductService {

    private final Map<String, Product> productStore = new ConcurrentHashMap<>();

    public ProductService() {
        // 沙箱种子数据
        seedProduct(Product.builder()
                .productId("FUND-CN-001").productCode("000001")
                .productName("华夏现金增利货币基金").productType("FUND")
                .termDays(0).expectedYield(new BigDecimal("2.50"))
                .minAmount(new BigDecimal("0.01"))
                .productRiskLevel("R1").minInvestorLevel("C1")
                .requireDualRecord(false).coolOffHours(0)
                .description("低风险活期, T+0 赎回")
                .status("ON_SALE").build());

        seedProduct(Product.builder()
                .productId("FUND-CN-002").productCode("110011")
                .productName("易方达债券基金 A").productType("FUND")
                .termDays(0).expectedYield(new BigDecimal("4.20"))
                .minAmount(new BigDecimal("100"))
                .productRiskLevel("R2").minInvestorLevel("C2")
                .requireDualRecord(false).coolOffHours(24)
                .description("中低风险债券型, 主要投资国债与信用债")
                .status("ON_SALE").build());

        seedProduct(Product.builder()
                .productId("FUND-CN-003").productCode("519697")
                .productName("银河稳健混合基金").productType("FUND")
                .termDays(0).expectedYield(new BigDecimal("8.50"))
                .minAmount(new BigDecimal("1000"))
                .productRiskLevel("R3").minInvestorLevel("C3")
                .requireDualRecord(false).coolOffHours(24)
                .description("中等风险, 股债混合配置")
                .status("ON_SALE").build());

        seedProduct(Product.builder()
                .productId("FUND-CN-004").productCode("161725")
                .productName("招商中证白酒指数基金").productType("FUND")
                .termDays(0).expectedYield(new BigDecimal("15.00"))
                .minAmount(new BigDecimal("1000"))
                .productRiskLevel("R4").minInvestorLevel("C4")
                .requireDualRecord(false).coolOffHours(24)
                .description("中高风险, 指数化投资白酒板块")
                .status("ON_SALE").build());

        seedProduct(Product.builder()
                .productId("WEALTH-001").productCode("WL20240601")
                .productName("稳盈 90 天理财计划").productType("WEALTH")
                .termDays(90).expectedYield(new BigDecimal("3.80"))
                .minAmount(new BigDecimal("10000"))
                .productRiskLevel("R2").minInvestorLevel("C2")
                .requireDualRecord(false).coolOffHours(24)
                .description("中低风险 90 天期银行理财")
                .status("ON_SALE").build());

        seedProduct(Product.builder()
                .productId("INSURANCE-001").productCode("INS2024A")
                .productName("安享一生终身寿险").productType("INSURANCE")
                .termDays(365).expectedYield(new BigDecimal("3.20"))
                .minAmount(new BigDecimal("50000"))
                .productRiskLevel("R3").minInvestorLevel("C3")
                .requireDualRecord(true).coolOffHours(168)  // 7 天
                .description("终身寿险, 含身故/全残保障")
                .status("ON_SALE").build());
    }

    private void seedProduct(Product p) {
        productStore.put(p.getProductId(), p);
    }

    public Product get(String productId) {
        return productStore.get(productId);
    }

    public List<Product> listOnSale() {
        List<Product> all = new ArrayList<>(productStore.values());
        all.removeIf(p -> !"ON_SALE".equals(p.getStatus()));
        all.sort(Comparator.comparing(p -> "FUND".equals(p.getProductType()) ? 1 : 2));
        return all;
    }

    /** 按风险等级筛产品 (前端推荐用) */
    public List<Product> listByRiskLevel(String investorLevel) {
        int level = parseLevel(investorLevel);
        List<Product> matched = new ArrayList<>();
        for (Product p : productStore.values()) {
            if (!"ON_SALE".equals(p.getStatus())) continue;
            int minLev = parseLevel(p.getMinInvestorLevel());
            if (level >= minLev) matched.add(p);
        }
        return matched;
    }

    private int parseLevel(String s) {
        if (s == null || s.isBlank()) return 1;
        try { return Integer.parseInt(s.substring(1)); } catch (Exception e) { return 1; }
    }
}