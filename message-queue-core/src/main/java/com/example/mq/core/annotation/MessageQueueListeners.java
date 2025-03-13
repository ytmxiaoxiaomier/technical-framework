package com.example.mq.core.annotation;

import java.lang.annotation.*;

/**
 * 多消息队列监听器注解
 * 用于标记方法或类为多个消息监听器
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageQueueListeners {
    /**
     * 监听器配置列表
     */
    MessageQueueListener[] value();
} 