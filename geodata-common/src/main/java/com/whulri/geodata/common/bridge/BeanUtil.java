package com.whulri.geodata.common.bridge;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BladeX org.springblade.core.tool.utils.BeanUtil 桥接实现（基于 hutool）。
 * <p>签名对齐 blade 原实现（含 Collection 集合重载），迁移代码零改动可用：</p>
 * <ul>
 *   <li>copy(Object, Class) → T / copy(Collection, Class) → List&lt;T&gt;</li>
 *   <li>copyProperties(Object, Class) → T / copyProperties(Collection, Class) → List&lt;T&gt;</li>
 *   <li>copy(Object, Object) 原地拷贝</li>
 * </ul>
 * <p>hutool 类名与本类同名，故以全限定名调用，避免编译单元内类名冲突。</p>
 */
public final class BeanUtil {

    private BeanUtil() {
    }

    /** 对象属性拷贝为新类型实例；source 为 null 时返回 null（与 blade 语义一致） */
    public static <T> T copy(Object source, Class<T> clazz) {
        if (source == null) {
            return null;
        }
        return cn.hutool.core.bean.BeanUtil.toBean(source, clazz);
    }

    /** 集合元素逐一拷贝为目标类型列表；空集合返回空列表（与 blade 语义一致） */
    public static <T> List<T> copy(Collection<?> source, Class<T> clazz) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return source.stream().map(item -> copy(item, clazz)).collect(Collectors.toList());
    }

    /** 对象属性拷贝到已有实例（原地拷贝，对应 blade BeanUtil.copy(source, target)） */
    public static void copy(Object source, Object target) {
        cn.hutool.core.bean.BeanUtil.copyProperties(source, target);
    }

    /** 单对象拷贝（对应 blade BeanUtil.copyProperties(source, clazz)） */
    public static <T> T copyProperties(Object source, Class<T> clazz) {
        return copy(source, clazz);
    }

    /** 集合拷贝（对应 blade BeanUtil.copyProperties(collection, clazz)） */
    public static <T> List<T> copyProperties(Collection<?> source, Class<T> clazz) {
        return copy(source, clazz);
    }
}
