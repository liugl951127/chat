package com.fin.chat.verify.provider;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.fin.chat.verify.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家企业信用信息公示系统 Provider
 *
 * <p>对接第三方数据商: 启信宝 / 天眼查 / 企查查
 * <p>生产 URL 由 fin.verify.ent.url 配置
 */
@Slf4j
@Component
public class EntProvider implements VerifyProvider {

    @Value("${fin.verify.ent.url:https://api.qichacha.com/ECIV4/}")
    private String url;

    @Value("${fin.verify.ent.appKey:demo}")
    private String appKey;

    @Override public VerifyType type() { return VerifyType.ENT; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.COMPANY_NAME
            || entity.getType() == EntityType.LEGAL_REP
            || entity.getType() == EntityType.CREDIT_CODE;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        try {
            // 1. 签名 (HMAC-SHA256 + timestamp)
            long ts = System.currentTimeMillis() / 1000;
            String sign = sign(appKey, entity.getValue(), ts);

            // 2. 调第三方
            String body = HttpUtil.createGet(url + "Search")
                    .form("key", appKey)
                    .form("keyword", entity.getValue())
                    .form("sign", sign)
                    .form("ts", String.valueOf(ts))
                    .timeout(3000)
                    .execute().body();

            JSONObject resp = JSONUtil.parseObj(body);
            JSONArray data = resp.getJSONArray("data");

            List<VerifyRef> refs = new ArrayList<>();
            int risk = 0;
            if (data != null) {
                for (int i = 0; i < Math.min(3, data.size()); i++) {
                    JSONObject e = data.getJSONObject(i);
                    VerifyRef ref = VerifyRef.builder()
                            .id(e.getStr("keyNo"))
                            .title(e.getStr("name"))
                            .summary(StrUtil.format("法人: %s | 成立: %s | 状态: %s | 注册资本: %s",
                                    e.getStr("legalPerson"), e.getStr("estiblishTime"),
                                    e.getStr("status"), e.getStr("registCapi")))
                            .url("https://www.qichacha.com/firm_" + e.getStr("keyNo") + ".html")
                            .ts(System.currentTimeMillis())
                            .build();
                    refs.add(ref);
                    // 风险分: 注销/吊销=高风险
                    if ("注销".equals(e.getStr("status")) || "吊销".equals(e.getStr("status"))) {
                        risk = 80;
                    }
                }
            }

            return VerifyResult.builder()
                    .type(type()).entity(entity)
                    .success(!refs.isEmpty())
                    .summary("工商命中 " + refs.size() + " 条")
                    .references(refs)
                    .riskScore(risk)
                    .costMs(System.currentTimeMillis() - start)
                    .build();
        } catch (Exception e) {
            log.error("ENT verify fail: {}", e.getMessage());
            return VerifyResult.degraded(type(), entity, e.getMessage());
        }
    }

    @Override public int cacheTtlSeconds() { return 3600; }   // 1h

    private String sign(String key, String keyword, long ts) {
        // 沙箱: 简化; 生产: 接入厂商签名算法
        return "demo-sign-" + key.hashCode() + "-" + ts + "-" + keyword.hashCode();
    }
}
