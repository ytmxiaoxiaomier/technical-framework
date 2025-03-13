package com.example.mq.core.annotation;

import java.lang.annotation.*;

/**
 * 多生产者注解
 * 用于标记方法或类为多个消息生产者
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Producers {
    /**
     * 生产者配置列表
     */
    Producer[] value();
} 