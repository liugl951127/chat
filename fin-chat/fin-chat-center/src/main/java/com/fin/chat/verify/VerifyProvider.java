package com.fin.chat.verify;

/**
 * 核查 Provider 接口
 *
 * <p>每个实现对应一个数据源 (工商/股票/政策/...)
 */
public interface VerifyProvider {

    VerifyType type();

    boolean supports(VerifyEntity entity);

    VerifyResult verify(VerifyEntity entity);

    int cacheTtlSeconds();
}
