package com.example.mq.core.producer;

import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 消息生产者接口
 */
public interface IProducer {
    /**
     * 发送消息（同步）
     *
     * @param message 消息对象
     * @param timeout 超时时间（毫秒）
     * @return 发送结果
     */
    SendResult send(Message message, long timeout);

    /**
     * 发送消息（异步）
     *
     * @param message 消息对象
     * @param timeout 超时时间（毫秒）
     * @return 异步发送结果Future
     */
    CompletableFuture<SendResult> asyncSend(Message message, long timeout);

    /**
     * 批量发送消息
     *
     * @param messages 消息对象列表
     * @param timeout  超时时间（毫秒）
     * @return 发送结果列表
     */
    List<SendResult> batchSend(List<Message> messages, long timeout);

    /**
     * 异步批量发送消息
     *
     * @param messages 消息对象列表
     * @param timeout  超时时间（毫秒）
     * @return 发送结果列表
     */
    CompletableFuture<List<SendResult>> asyncBatchSend(List<Message> messages, long timeout);

    /**
     * 延迟发送消息
     *
     * @param messages 消息对象
     * @param delay    延迟时间
     * @param unit     时间单位
     * @param timeout  超时时间（毫秒）
     * @return 发送结果
     */
    List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit, long timeout);

    /**
     * 事务发送消息
     *
     * @param messages 消息对象
     * @param timeout  超时时间（毫秒）
     * @return 发送结果
     */
    List<SendResult> sendTransaction(List<Message> messages, long timeout);

    /**
     * 关闭生产者
     */
    void close();
} 