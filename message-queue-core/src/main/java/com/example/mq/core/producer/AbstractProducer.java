package com.example.mq.core.producer;

import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public abstract class AbstractProducer implements IProducer {

    /**
     * 发送消息（同步），使用默认超时时间
     *
     * @param message 消息对象
     * @return 发送结果
     */
    public SendResult send(Message message) {
        return send(message, 3000L);
    }

    /**
     * 发送消息（异步），使用默认超时时间
     *
     * @param message 消息对象
     * @return 异步发送结果Future
     */
    public CompletableFuture<SendResult> asyncSend(Message message) {
        return asyncSend(message, 3000L);
    }

    /**
     * 批量发送消息，使用默认超时时间
     *
     * @param messages 消息对象列表
     * @return 发送结果列表
     */
    public List<SendResult> batchSend(List<Message> messages) {
        return batchSend(messages, 3000L);
    }

    /**
     * 异步批量发送消息，使用默认超时时间
     *
     * @param messages
     * @return
     */
    public CompletableFuture<List<SendResult>> asyncBatchSend(List<Message> messages) {
        return asyncBatchSend(messages, 3000L);
    }

    /**
     * 延迟发送消息，使用默认超时时间
     *
     * @param messages 消息对象
     * @param delay    延迟时间
     * @param unit     时间单位
     * @return 发送结果
     */
    public List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit) {
        return sendDelayed(messages, delay, unit, 3000L);
    }

    /**
     * 事务发送消息，使用默认超时时间
     *
     * @param messages 消息对象
     * @return 发送结果
     */
    public List<SendResult> sendTransaction(List<Message> messages) {
        return sendTransaction(messages, 3000L);
    }
}
