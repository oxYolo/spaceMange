package com.whulri.geodata.common.model;

/**
 * 泛型出参容器（模拟 C# out 参数，反编译自 dev-common-1.1.8）
 */
public class OutObject<T> {

    public T value;

    public OutObject() {
    }

    public OutObject(T t) {
        this.value = t;
    }
}
