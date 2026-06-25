package com.fin.trade.entity;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * 理财产品
 *
 * <p>合规要点:
 * <ul>
 *   <li>productRiskLevel: 产品自身风险等级 R1-R5</li>
 *   <li>minInvestorLevel: 最低投资者风险承受等级 C1-C5</li>
 *   <li>requireDualRecord: 是否需要双录 (高风险理财/保险)</li>
 *   <li>coolOffHours: 冷静期小时数 (默认 24h)</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    private String productId;
    private String productCode;       // 基金代码 / 理财代码
    /** 名称 */
    private String productName;
    /** 类型: FUND (基金) / WEALTH (银行理财) / INSURANCE (保险) */
    private String productType;
    /** 期限 (天, 0=活期) */
    private Integer termDays;
    /** 预期年化收益率 */
    private BigDecimal expectedYield;
    /** 起购金额 */
    private BigDecimal minAmount;
    /** 产品风险等级 R1-R5 */
    private String productRiskLevel;
    /** 最低投资者风险等级 C1-C5 */
    private String minInvestorLevel;
    /** 是否需要双录 */
    private Boolean requireDualRecord;
    /** 冷静期小时数 */
    private Integer coolOffHours;
    /** 描述 */
    private String description;
    /** 状态: ON_SALE / SOLD_OUT / DELISTED */
    private String status;
}