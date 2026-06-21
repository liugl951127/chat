package com.fin.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummary {
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer realNameStatus;
    private Integer riskLevel;
}
