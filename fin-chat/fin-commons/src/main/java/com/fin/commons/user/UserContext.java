package com.fin.commons.user;

/**
 * 当前用户上下文 (网关侧写入, 服务侧读取)
 */
public final class UserContext {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_DEVICE = "X-Device-Id";
    public static final String HEADER_ROLES = "X-User-Roles";
    public static final String HEADER_TENANT = "X-Tenant-Id";

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DEVICE = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLES = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();

    private UserContext() {}

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }
    public static void setDevice(String d) { DEVICE.set(d); }
    public static String getDevice() { return DEVICE.get(); }
    public static void setRoles(String r) { ROLES.set(r); }
    public static String getRoles() { return ROLES.get(); }
    public static void setTenant(String t) { TENANT.set(t); }
    public static String getTenant() { return TENANT.get(); }

    public static void clear() {
        USER_ID.remove();
        DEVICE.remove();
        ROLES.remove();
        TENANT.remove();
    }

    public static boolean hasRole(String role) {
        String r = ROLES.get();
        return r != null && (r.contains(role) || r.contains("ADMIN"));
    }
}
