package com.fin.chat.verify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyResult {
    private VerifyType type;
    private VerifyEntity entity;
    private boolean success;
    private String summary;
    private List<VerifyRef> references;
    /** 风险分 0-100, ≥70 高风险 */
    private int riskScore;
    private long costMs;
    private String errorMessage;

    public static VerifyResult degraded(VerifyType type, VerifyEntity entity, String err) {
        return VerifyResult.builder()
                .type(type).entity(entity).success(false)
                .errorMessage(err).build();
    }
}
