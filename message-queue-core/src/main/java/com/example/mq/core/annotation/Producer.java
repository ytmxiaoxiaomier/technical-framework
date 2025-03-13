package com.example.mq.core.annotation;

import com.example.mq.core.enums.MessageQueueType;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 生产者注解
 * 用于标记方法或类为消息生产者
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Producer {
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
     * 标签/ 分区键
     *
     * @return
     */
    String key() default "";

    /**
     * 是否异步发送
     */
    boolean async() default false;

    /**
     * 是否顺序发送
     *
     * @return
     */
    boolean sequence() default false;

    /**
     * 事务id，默认不开启事务
     *
     * @return
     */
    String transactionId() default "";

    /**
     * 延迟时间
     *
     * @return
     */
    long delayTime() default 0;

    /**
     * 延迟时间单位
     *
     * @return
     */
    TimeUnit delayUnit() default TimeUnit.MILLISECONDS;

    /**
     * 超时时间（毫秒）
     */
    long timeout() default 3000L;

    /**
     * 优先级（用于多生产者时的排序）
     */
    int order() default 0;

    /**
     * 额外属性，格式为key=value
     */
    String[] properties() default {};
} 