package com.example.mq.spring.processor;

import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.message.CompositeMessageResult;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.MessageResult;
import com.example.mq.core.message.SendResult;
import com.example.mq.core.producer.IProducer;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 生产者方法拦截器
 */
@RequiredArgsConstructor
public class ProducerMethodInterceptor implements MethodInterceptor {
    private final IProducer producer;
    private final ProducerAnnotationProperties producerAnnotationProperties;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        // 获取原始方法返回的MessageResult
        MessageResult result = (MessageResult) invocation.proceed();

        // 发送消息
        if (result instanceof CompositeMessageResult compositeResult) {
            // 已经是组合结果，添加当前生产者的结果
            MessageResult mqResult = sendMessages(result.getMessages());
            return compositeResult.addResult(producerAnnotationProperties.getType(),
                    producerAnnotationProperties.getCluster() + "::" + producerAnnotationProperties.getTopic(),
                    mqResult);
        } else if (result != null) {
            // 发送消息并设置结果
            return sendMessages(result.getMessages());
        }
        return null;
    }

    /**
     * 发送消息
     */
    private MessageResult sendMessages(List<Message> messages) {
        MessageResult result = MessageResult.of(messages);

        // 设置主题和额外属性
        String topic = producerAnnotationProperties.getTopic();
        String key = producerAnnotationProperties.getKey();
        Map<String, String> properties = producerAnnotationProperties.getProperties();
        for (Message message : messages) {
            if (StringUtils.hasText(topic))
                message.setTopic(topic);
            if (StringUtils.hasText(key))
                message.setKey(key);
            // 添加自定义属性到消息头
            if (properties != null && !properties.isEmpty())
                message.setHeaders(properties.entrySet()
                        .stream()
                        .filter(entry -> entry.getKey().startsWith("header::"))
                        .collect(Collectors.toMap(entry -> entry.getKey().split("::", 2)[1], Map.Entry::getValue)));
        }

        // 消息超时时间
        var timeout = producerAnnotationProperties.getTimeout();
        // 发送消息
        CompletableFuture<List<SendResult>> future;
        if (StringUtils.hasText(producerAnnotationProperties.getTransactionId())) {
            // 事务
            future = MessageSender.messageSend(() -> producer.sendTransaction(messages, timeout));
        } else if (producerAnnotationProperties.getDelayTime() > 0) {
            // 延迟
            future = MessageSender.messageSend(() -> producer.sendDelayed(messages, producerAnnotationProperties.getDelayTime(), producerAnnotationProperties.getDelayUnit(), timeout));
        } else if (producerAnnotationProperties.isAsync()) {
            // 异步发送
            future = producer.asyncBatchSend(messages, timeout);
        } else {
            // 同步发送
            future = MessageSender.messageSend(() -> producer.batchSend(messages, timeout));
        }
        result.setResultFuture(future);
        return result;
    }

    @FunctionalInterface
    interface MessageSender {

        List<SendResult> send();

        static CompletableFuture<List<SendResult>> messageSend(MessageSender messageSender) {
            CompletableFuture<List<SendResult>> future = new CompletableFuture<>();
            try {
                List<SendResult> sendResults = messageSender.send();
                if (!sendResults.isEmpty())
                    future.complete(sendResults);
                else
                    future.completeExceptionally(new IllegalStateException("批量发送返回空结果"));
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
            return future;
        }

    }

} 