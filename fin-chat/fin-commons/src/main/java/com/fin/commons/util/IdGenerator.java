package com.fin.commons.util;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.IdUtil;

/**
 * 雪花 + UUID 工具
 *
 * - 单机用 Hutool 自带 Snowflake (workerId=1, datacenterId=1)
 * - 多实例建议接入 Nacos 配置中心下发 workerId
 */
public final class IdGenerator {

    private static final cn.hutool.core.lang.Snowflake SNOWFLAKE = IdUtil.getSnowflake(1, 1);

    private IdGenerator() {}

    /** 雪花 ID (long) */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /** 字符串雪花 (用于主键) */
    public static String nextIdStr() {
        return String.valueOf(SNOWFLAKE.nextId());
    }

    /** UUID (no -) */
    public static String uuid() {
        return UUID.randomUUID().toString(true);
    }

    /** 业务前缀 ID: T-{snowflake} */
    public static String tradeId() {
        return "T-" + nextIdStr();
    }

    public static String msgId() {
        return "M-" + java.time.LocalDate.now() + "-" + nextIdStr();
    }

    public static String conversationId() {
        return "C-" + nextIdStr();
    }
}
