package com.fin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 统一登录请求基类
 */
@Data
public class LoginRequest {

    @NotBlank(message = "设备指纹必填")
    private String deviceId;

    /** 可选, 客户端 IP (服务端 header 兜底) */
    private String clientIp;
}
