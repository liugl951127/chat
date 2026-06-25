package com.fin.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 聊天消息 DTO
 *
 * <p>消息类型 (type):
 * <ul>
 *   <li>TEXT         - 纯文本</li>
 *   <li>IMAGE / FILE - 多媒体</li>
 *   <li>PRODUCT_CARD - 产品卡片 (理财/基金/保险), 含 richPayload</li>
 *   <li>ORDER        - 订单状态推送, 含 orderId</li>
 *   <li>RISK_TEST    - 风险测评邀请卡片</li>
 *   <li>DUAL_RECORD  - 双录提示</li>
 *   <li>SYSTEM       - 系统消息</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String msgId;
    private String conversationId;
    private Long senderId;
    private String senderType;     // CUSTOMER / ADVISOR / AGENT / SYSTEM
    private String type;
    private String content;
    private String contentEnc;     // SM4 加密
    private String contentHash;    // SHA-256 (链式)
    private String prevHash;
    private Long clientTs;
    private Long serverTs;
    private String auditProof;

    // ============ 富消息载荷 ============
    /** 产品卡片 (type=PRODUCT_CARD): productId / productName / yield / minAmount / riskLevel */
    private Map<String, Object> productPayload;
    /** 订单 (type=ORDER): orderId / status / amount / nextStep */
    private Map<String, Object> orderPayload;
    /** 风险测评邀请 (type=RISK_TEST): testId / questionsCount / timeLimit */
    private Map<String, Object> riskTestPayload;
    /** 双录提示 (type=DUAL_RECORD): dualRecordId / url / required */
    private Map<String, Object> dualRecordPayload;
}