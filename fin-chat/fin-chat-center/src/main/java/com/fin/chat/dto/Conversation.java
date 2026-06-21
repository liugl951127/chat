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
public class Conversation {
    private String id;
    private String type;        // P2P / GROUP / ADVISOR
    private String title;
    private Long userId;
    private Long advisorId;
    private Integer status;     // 1=进行 2=结束 3=归档
    private String lastMsgId;
    private LocalDateTime lastMsgAt;
    private Integer msgCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
