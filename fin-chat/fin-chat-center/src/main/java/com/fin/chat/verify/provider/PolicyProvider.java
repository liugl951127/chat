package com.fin.chat.verify.provider;

import com.fin.chat.verify.VerifyEntity;
import com.fin.chat.verify.VerifyResult;
import com.fin.chat.verify.VerifyType;
import com.fin.chat.verify.VerifyRef;
import com.fin.chat.verify.VerifyProvider;
import com.fin.chat.verify.EntityType;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 政策库 Provider (内置静态库, 离线)
 */
@Slf4j
@Component
public class PolicyProvider implements VerifyProvider {

    private static final List<PolicyDoc> POLICIES = Arrays.asList(
        new PolicyDoc("P001", "《关于规范金融机构资产管理业务的指导意见》", "资管新规, 打破刚兑",
            "http://www.gov.cn/zhengce/2018-04/27/content_5286560.htm"),
        new PolicyDoc("P002", "《证券期货投资者适当性管理办法》", "投资者分类 + 产品分级 + 适当性匹配",
            "http://www.csrc.gov.cn"),
        new PolicyDoc("P003", "《关于加强金融消费者权益保护工作的指导意见》", "金融消费者八项权益",
            "http://www.pbc.gov.cn"),
        new PolicyDoc("P004", "《商业银行理财业务监督管理办法》", "理财净值化, 信息披露",
            "http://www.cbirc.gov.cn"),
        new PolicyDoc("P005", "《公开募集证券投资基金销售机构监督管理办法》", "基金销售资质",
            "http://www.csrc.gov.cn"),
        new PolicyDoc("P006", "《关于进一步规范私募投资基金登记备案和监管的若干规定》", "私募新规",
            "http://www.amac.org.cn")
    );

    @Override public VerifyType type() { return VerifyType.POLICY; }

    @Override
    public boolean supports(VerifyEntity entity) {
        return entity.getType() == EntityType.POLICY_KEYWORD;
    }

    @Override
    public VerifyResult verify(VerifyEntity entity) {
        long start = System.currentTimeMillis();
        String kw = entity.getValue().toLowerCase(Locale.ROOT);
        List<VerifyRef> refs = new ArrayList<>();
        for (PolicyDoc p : POLICIES) {
            if (p.title.toLowerCase(Locale.ROOT).contains(kw)
                || p.summary.toLowerCase(Locale.ROOT).contains(kw)) {
                refs.add(VerifyRef.builder()
                    .id(p.id).title(p.title).summary(p.summary)
                    .url(p.url).ts(System.currentTimeMillis())
                    .build());
            }
        }
        return VerifyResult.builder()
            .type(type()).entity(entity).success(!refs.isEmpty())
            .summary("命中 " + refs.size() + " 条政策")
            .references(refs)
            .costMs(System.currentTimeMillis() - start)
            .build();
    }

    @Override public int cacheTtlSeconds() { return 3600; }

    private record PolicyDoc(String id, String title, String summary, String url) {}
}
