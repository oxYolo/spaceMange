package com.whulri.geodata.common.bridge;

/**
 * BladeX org.springblade.core.tool.utils.Func 桥接实现（基于 hutool）。
 * <p>仅实现迁移代码实际用到的静态方法：isNotEmpty / isNotBlank / isEmpty / isBlank / equals。</p>
 */
public final class Func {

    private Func() {
    }

    /** 对象为空：null / 空串 / 空集合 / 空Map / 空数组 */
    public static boolean isEmpty(Object obj) {
        if (obj == null) {
            return true;
        }
        if (obj instanceof CharSequence) {
            return cn.hutool.core.util.StrUtil.isEmpty((CharSequence) obj);
        }
        if (obj instanceof java.util.Collection) {
            return ((java.util.Collection<?>) obj).isEmpty();
        }
        if (obj instanceof java.util.Map) {
            return ((java.util.Map<?, ?>) obj).isEmpty();
        }
        if (obj.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(obj) == 0;
        }
        return false;
    }

    public static boolean isNotEmpty(Object obj) {
        return !isEmpty(obj);
    }

    public static boolean isBlank(CharSequence cs) {
        return cn.hutool.core.util.StrUtil.isBlank(cs);
    }

    public static boolean isNotBlank(CharSequence cs) {
        return cn.hutool.core.util.StrUtil.isNotBlank(cs);
    }

    public static boolean equals(Object a, Object b) {
        return cn.hutool.core.util.ObjectUtil.equal(a, b);
    }
}
