package com.fin.notify.controller;

import com.fin.commons.resp.ApiResponse;
import com.fin.notify.dto.SendSmsRequest;
import com.fin.notify.service.NotifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notify")
@RequiredArgsConstructor
public class NotifyController {

    private final NotifyService notifyService;

    @PostMapping("/sms/send")
    public ApiResponse<NotifyService.SendResult> sendSms(@Valid @RequestBody SendSmsRequest req) {
        return ApiResponse.ok(notifyService.sendSms(req));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> s = new HashMap<>();
        s.put("smsSent", notifyService.countSent());
        return ApiResponse.ok(s);
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(Map.of("status", "UP", "module", "fin-notify-center"));
    }
}
