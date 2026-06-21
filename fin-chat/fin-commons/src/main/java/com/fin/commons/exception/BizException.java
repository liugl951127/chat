package com.fin.commons.exception;

import com.fin.commons.resp.ErrorCode;
import lombok.Getter;

/**
 * 业务异常 (带错误码, 全局拦截统一返回)
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String overrideMessage;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.overrideMessage = null;
    }

    public BizException(ErrorCode errorCode, String overrideMessage) {
        super(overrideMessage);
        this.errorCode = errorCode;
        this.overrideMessage = overrideMessage;
    }

    public int getCode() {
        return errorCode.getCode();
    }

    public String getBizCode() {
        return errorCode.name();
    }
}
