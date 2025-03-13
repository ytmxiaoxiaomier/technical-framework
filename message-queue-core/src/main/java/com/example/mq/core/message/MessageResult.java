package com.example.mq.core.message;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 消息结果包装类
 * 用于Producer注解方法的返回值
 */
@Data
@NoArgsConstructor
public class MessageResult {
    /**
     * 待发送的消息列表
     */
    private List<Message> messages = new ArrayList<>();

    /**
     * 异步发送结果Future
     */
    private CompletableFuture<List<SendResult>> resultFuture;

    /**
     * 创建单消息结果
     *
     * @param message 待发送消息
     * @return 消息结果
     */
    public static MessageResult of(Message message) {
        MessageResult result = new MessageResult();
        result.getMessages().add(message);
        return result;
    }

    /**
     * 创建多消息结果
     *
     * @param messages 待发送消息列表
     * @return 消息结果
     */
    public static MessageResult of(List<Message> messages) {
        MessageResult result = new MessageResult();
        result.getMessages().addAll(messages);
        return result;
    }

    /**
     * 添加消息
     *
     * @param message 消息
     * @return 当前对象
     */
    public MessageResult addMessage(Message message) {
        if (message != null) {
            this.messages.add(message);
        }
        return this;
    }

    /**
     * 添加多条消息
     *
     * @param messages 消息列表
     * @return 当前对象
     */
    public MessageResult addMessages(List<Message> messages) {
        if (messages != null) {
            this.messages.addAll(messages);
        }
        return this;
    }

    /**
     * 同步等待结果
     *
     * @return 发送结果
     * @throws ExecutionException   执行异常
     * @throws InterruptedException 线程中断异常
     */
    public List<SendResult> get() throws ExecutionException, InterruptedException {
        if (resultFuture == null) {
            throw new IllegalStateException("消息尚未发送");
        }
        return resultFuture.get();
    }

    /**
     * 带超时的同步等待结果
     *
     * @param timeout 超时时间
     * @param unit    时间单位
     * @return 发送结果
     * @throws ExecutionException   执行异常
     * @throws InterruptedException 线程中断异常
     * @throws TimeoutException     超时异常
     */
    public List<SendResult> get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
        if (resultFuture == null) {
            throw new IllegalStateException("消息尚未发送");
        }
        return resultFuture.get(timeout, unit);
    }

    /**
     * 消息是否已发送
     *
     * @return 是否已发送
     */
    public boolean isCompleted() {
        return resultFuture != null && resultFuture.isDone();
    }
} 