package com.fin.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskTest {
    private Long id;
    private Long userId;
    private List<Integer> answers;   // 10 道题答案
    private Integer score;           // 0-100
    private String level;            // C1-C5
    private LocalDateTime testedAt;
    private LocalDateTime expireAt;  // +12 个月
}
