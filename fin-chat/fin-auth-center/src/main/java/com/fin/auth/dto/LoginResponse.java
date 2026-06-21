package com.fin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    /** 0=未实名 1=弱实名 2=强实名 */
    private int realNameStatus;
    /** C1-C5 */
    private int riskLevel;
    private String nickname;
    private String avatar;
    /** 是否需要去完善实名 */
    private boolean realNameRequired;
}
