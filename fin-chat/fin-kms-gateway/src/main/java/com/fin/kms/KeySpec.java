package com.fin.kms;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KeySpec {
    /** SM2 / SM4 / AES */
    private String algorithm;
    /** 用途: SIGN / ENC / TLS / JWT */
    private String usage;
    /** 业务标签: trade-user-{id} / chat-conv-{id} */
    private String aliasPrefix;
    /** 业务 ID */
    private String businessId;
    /** 过期天数 (0=永不过期) */
    private int expireDays;
    /** 0=不可导出, 1=可导出备份 */
    private int exportable;

    public String fullAlias() {
        return aliasPrefix + "-" + businessId;
    }
}
