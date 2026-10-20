package com.whulri.geodata.common.bridge;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * BladeX org.springblade.core.tool.utils.SpringUtil 桥接实现。
 * <p>迁移代码实际使用：getBean(Class)。由 CommonAutoConfiguration 的包扫描注册为 Bean。</p>
 */
@Component
public class SpringUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        SpringUtil.applicationContext = context;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }

    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }
}
