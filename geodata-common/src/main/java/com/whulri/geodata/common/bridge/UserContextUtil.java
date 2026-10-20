package com.whulri.geodata.common.bridge;

/**
 * 用户上下文解析工具：统一「操作人」取值口径。
 * <p>优先级：显式传入的用户编码 → 登录态（{@link AuthUtil#getNickName()}）→ 默认用户 admin。
 * 用于无登录态/旁路认证场景下替代原实现「用户编码为空即返回空列表」的行为，
 * 避免前端未携带 createBy/userCode 时查询恒为空。</p>
 */
public final class UserContextUtil {

    /** 无登录态时的默认操作人（与前端 userStore 默认值保持一致） */
    public static final String DEFAULT_USER = "admin";

    private UserContextUtil() {
    }

    /**
     * 解析用户编码：非空则原样返回；为空取登录用户；登录态也为空时回退 {@link #DEFAULT_USER}。
     */
    public static String resolveUserCode(String userCode) {
        if (userCode != null && !userCode.trim().isEmpty()) {
            return userCode;
        }
        String login = AuthUtil.getNickName();
        if (login != null && !login.trim().isEmpty()) {
            return login;
        }
        return DEFAULT_USER;
    }
}
