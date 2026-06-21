package com.fin.chat.verify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyEntity {
    /** 实体类型 */
    private EntityType type;
    /** 实体值 (原始, 已脱敏的也行) */
    private String value;
    /** 上下文 */
    private String context;
}
