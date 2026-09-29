package com.example.aitools.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文持有：给非 Spring 管理的代码（默认方法、静态工具）按类型取 Bean 用。
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.context = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz) {
        if (context == null) {
            throw new IllegalStateException("SpringContextHolder 未初始化");
        }
        return context.getBean(clazz);
    }
}