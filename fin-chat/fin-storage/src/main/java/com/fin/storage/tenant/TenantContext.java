package com.fin.storage.tenant;

/**
 * 多租户上下文
 */
public final class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenant(String tenant) { TENANT.set(tenant); }
    public static String getTenant() { return TENANT.get(); }
    public static void clear() { TENANT.remove(); }
}
