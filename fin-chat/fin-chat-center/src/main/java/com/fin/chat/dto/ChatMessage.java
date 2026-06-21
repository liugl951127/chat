package com.fin.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String msgId;
    private String conversationId;
    private Long senderId;
    private String senderType;     // CUSTOMER / ADVISOR / SYSTEM
    private String type;           // TEXT / IMAGE / FILE / TRADE_PROPOSAL
    private String content;
    private String contentEnc;     // SM4 加密
    private String contentHash;    // SHA-256 (链式)
    private String prevHash;
    private Long clientTs;
    private Long serverTs;
    private String auditProof;
}
