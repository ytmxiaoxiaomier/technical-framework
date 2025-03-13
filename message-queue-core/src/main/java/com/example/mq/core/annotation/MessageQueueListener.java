package com.example.mq.core.annotation;

import com.example.mq.core.enums.MessageQueueType;

import java.lang.annotation.*;

/**
 * 消息队列监听器注解
 * 用于标记方法或类为消息监听器
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessageQueueListener {
    /**
     * 消息队列类型
     */
    MessageQueueType type() default MessageQueueType.KAFKA;

    /**
     * 集群名称，不指定则使用默认集群
     */
    String cluster() default "";

    /**
     * 主题
     */
    String topic();

    /**
     * 消费者组
     */
    String group();

    /**
     * 并发消费者数
     */
    int concurrency() default 1;

    /**
     * 是否顺序消费
     *
     * @return
     */
    boolean sequence() default false;

    /**
     * 是否开启事务，默认不开启
     * @return
     */
    boolean transaction() default false;

    /**
     * 是否开启延迟消费
     *
     * @return
     */
    boolean delayQueue() default false;

    /**
     * 优先级（用于多监听器时的排序）
     */
    int order() default 0;

    /**
     * 额外属性，格式为key=value
     */
    String[] properties() default {};
} 