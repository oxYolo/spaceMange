package com.whulri.geodata.common.bridge;

import com.whulri.common.security.utils.SecurityUtils;

/**
 * BladeX org.springblade.core.secure.utils.AuthUtil 桥接实现。
 * <p>迁移代码实际使用：getNickName()（写操作日志时取当前操作人）。
 * 原系统认证体系旁路（自建 sdms_user 表），此处对接 whulri 用户上下文，
 * 取不到时返回空串，保持与原系统无登录态时的行为一致。</p>
 */
public final class AuthUtil {

    private AuthUtil() {
    }

    public static String getNickName() {
        try {
            String username = SecurityUtils.getUsername();
            return username == null ? "" : username;
        } catch (Exception e) {
            return "";
        }
    }
}
