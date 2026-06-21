package com.fin.trade.risk;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecision {
    private boolean passed;
    private boolean smsRequired;
    private String code;
    private String message;

    public static RiskDecision pass(boolean sms) {
        return RiskDecision.builder().passed(true).smsRequired(sms).build();
    }

    public static RiskDecision reject(String code, String msg) {
        return RiskDecision.builder()
                .passed(false).smsRequired(false)
                .code(code).message(msg).build();
    }
}
