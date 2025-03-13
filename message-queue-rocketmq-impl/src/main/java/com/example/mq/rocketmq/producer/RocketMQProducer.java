package com.example.mq.rocketmq.producer;

import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.core.exception.ProducerException;
import com.example.mq.core.message.BaseSendResult;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;
import com.example.mq.core.producer.AbstractProducer;
import com.example.mq.core.producer.IProducer;
import com.example.mq.rocketmq.converter.RocketMQMessageConverter;
import com.example.mq.support.retry.RetryTemplate;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.*;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * RocketMQ生产者实现
 */
@Slf4j
public class RocketMQProducer extends AbstractProducer {
    private final DefaultMQProducer producer;
    private final RocketMQMessageConverter converter;
    private final MessageQueueProperties properties;
    private ProducerAnnotationProperties annotationProperties;
    private final RetryTemplate retryTemplate;
    private OrderMessageQueueSelector orderMessageQueueSelector;
    CacheManager cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
            .withCache("localTransactionStateCache",
                    CacheConfigurationBuilder.newCacheConfigurationBuilder(
                            String.class,
                            LocalTransactionState.class,
                            ResourcePoolsBuilder.heap(100)
                    ).withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.of(120, ChronoUnit.SECONDS)))) // 创建后 120 秒过期
            .build(true);
    private final Cache<String, LocalTransactionState> localTransactionStateCache = cacheManager.getCache(
            "localTransactionStateCache",
            String.class,
            LocalTransactionState.class);
    private final Set<String> exitsTopics = new ConcurrentSkipListSet<>();

    public RocketMQProducer(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new RocketMQMessageConverter(serializer);
        if (properties.getProperties().containsKey(IProducer.class.getName())
                && properties.getProperties().get(IProducer.class.getName()) instanceof ProducerAnnotationProperties annoProperties) {
            this.annotationProperties = annoProperties;
            this.orderMessageQueueSelector = new OrderMessageQueueSelector();
        }
        this.retryTemplate = new RetryTemplate(3, 1000, true);

        // 创建生产者
        this.producer = createProducer();

        try {
            // 启动生产者
            this.producer.start();
        } catch (MQClientException e) {
            throw new ConnectionException("启动RocketMQ生产者失败", e);
        }
    }

    /**
     * 创建RocketMQ生产者
     */
    private DefaultMQProducer createProducer() {
        DefaultMQProducer producer = new DefaultMQProducer();
        if (annotationProperties != null) {
            // 事务生产
            if (StringUtils.hasText(annotationProperties.getTransactionId())) {
                TransactionMQProducer transactionMQProducer = new TransactionMQProducer();
                // 配置线程池 todo 后期改为项目中的线程池
                transactionMQProducer.setExecutorService(new ThreadPoolExecutor(
                        2,
                        5,
                        60,
                        TimeUnit.SECONDS,
                        new ArrayBlockingQueue<>(1000),
                        runner -> {
                            Thread thread = new Thread(runner);
                            thread.setName(String.format("rocketmq-transaction-status-check-thread-%s-%s", annotationProperties.getCluster(), annotationProperties.getTopic()));
                            return thread;
                        }));
                // 设置监听器
                transactionMQProducer.setTransactionListener(new TransactionListener() {
                    @Override
                    public LocalTransactionState executeLocalTransaction(org.apache.rocketmq.common.message.Message msg, Object arg) {
                        return LocalTransactionState.UNKNOW;
                    }

                    @Override
                    public LocalTransactionState checkLocalTransaction(MessageExt msg) {
                        String txId = msg.getUserProperty("transaction-id");
                        LocalTransactionState state = localTransactionStateCache.get(txId);
                        if (state == null)
                            return LocalTransactionState.UNKNOW;
                        return state;
                    }
                });
            }
        }

        // 设置生产者组名
        String producerGroup = getProperty("producerGroup", "DEFAULT_PRODUCER_GROUP");
        producer.setProducerGroup(producerGroup);

        // 设置NameServer地址
        producer.setNamesrvAddr(properties.getServer());

        // 设置重试次数
        int retryTimes = getProperty("retryTimes", 2);
        producer.setRetryTimesWhenSendFailed(retryTimes);

        // 设置发送超时时间
        int timeout = getProperty("sendMsgTimeout", 3000);
        producer.setSendMsgTimeout(timeout);

        // 设置消息体最大值
        int maxMessageSize = getProperty("maxMessageSize", 4 * 1024 * 1024);
        producer.setMaxMessageSize(maxMessageSize);

        // 设置压缩阈值
        int compressMsgBodyOverHowmuch = getProperty("compressMsgBodyOverHowmuch", 1024 * 4);
        producer.setCompressMsgBodyOverHowmuch(compressMsgBodyOverHowmuch);

        return producer;
    }

    /**
     * 获取RocketMQ配置属性
     */
    @SuppressWarnings("unchecked")
    private <T> T getProperty(String key, T defaultValue) {
        Map<String, Object> props = properties.getProperties();
        if (props == null || !props.containsKey(key)) {
            return defaultValue;
        }

        Object value = props.get(key);
        if (value == null) {
            return defaultValue;
        }

        if (defaultValue instanceof Integer && value instanceof String) {
            return (T) Integer.valueOf((String) value);
        } else if (defaultValue instanceof Long && value instanceof String) {
            return (T) Long.valueOf((String) value);
        } else if (defaultValue instanceof Boolean && value instanceof String) {
            return (T) Boolean.valueOf((String) value);
        } else if (defaultValue.getClass().isInstance(value)) {
            return (T) value;
        }

        return defaultValue;
    }

    private void checkTopic(String topic) {
        if (exitsTopics.contains(topic))
            return;
        DefaultMQAdminExt admin = new DefaultMQAdminExt();
        try {
            admin.setNamesrvAddr(properties.getServer());
            admin.start();
            // 检查 Topic 是否存在
            boolean topicExists = admin.examineTopicRouteInfo(topic) != null;
            if (!topicExists) {
                // 创建 Topic
                admin.createTopic(getProperty("brokerName", "defaultBroker"), topic, 8, new HashMap<>());
                log.info("创建 Topic 成功: {}", topic);
            }
            exitsTopics.add(topic);
        } catch (MQClientException e) {
            if (e.getResponseCode() == ResponseCode.TOPIC_NOT_EXIST) {
                // 创建 Topic
                try {
                    admin.createTopic(getProperty("brokerName", "defaultBroker"), topic, 8, new HashMap<>());
                    log.info("Topic 创建成功: {}", topic);
                    exitsTopics.add(topic);
                } catch (MQClientException ex) {
                    log.error("创建主题失败，topic：{}", topic, ex);
                    exitsTopics.remove(topic);
                }
            } else {
                log.error("创建主题失败，topic：{}", topic, e);
                exitsTopics.remove(topic);
            }
        } catch (RemotingException | InterruptedException e) {
            log.error("创建主题失败，topic：{}", topic, e);
            exitsTopics.remove(topic);
        } finally {
            admin.shutdown();
        }
    }

    @Override
    public SendResult send(Message message, long timeout) {
        try {
            checkTopic(message.getTopic());
            // 转换为RocketMQ消息
            org.apache.rocketmq.common.message.Message rocketMsg = converter.toMQMessage(message);

            // 保存原始超时时间
            int originalTimeout = producer.getSendMsgTimeout();
            try {
                // 设置新的超时时间
                producer.setSendMsgTimeout((int) timeout);

                // 发送消息
                org.apache.rocketmq.client.producer.SendResult rocketResult = retryTemplate.execute(() -> {
                    try {
                        // 是否顺序发送
                        if (this.annotationProperties != null && annotationProperties.isSequence())
                            return producer.send(rocketMsg, orderMessageQueueSelector,
                                    orderMessageQueueSelector.getOrderId(
                                            annotationProperties.getType(), annotationProperties.getCluster(), annotationProperties.getTopic()));
                        else
                            return producer.send(rocketMsg);
                    } catch (MQClientException | RemotingException | MQBrokerException | InterruptedException e) {
                        // 包装为RuntimeException以便RetryTemplate可以捕获
                        throw new RuntimeException("发送消息失败", e);
                    }
                });

                // 转换发送结果
                return convertSendResult(message.getMessageId(), rocketResult);
            } finally {
                // 恢复原始超时时间
                producer.setSendMsgTimeout(originalTimeout);
            }
        } catch (Exception e) {
            log.error("发送消息失败: {}", message.getMessageId(), e);
            return BaseSendResult.failure(message.getMessageId(), e);
        }
    }

    @Override
    public CompletableFuture<SendResult> asyncSend(Message message, long timeout) {
        CompletableFuture<SendResult> future = new CompletableFuture<>();

        try {
            // 转换为RocketMQ消息
            org.apache.rocketmq.common.message.Message rocketMsg = converter.toMQMessage(message);

            // 保存原始超时时间
            int originalTimeout = producer.getSendMsgTimeout();
            try {
                // 设置新的超时时间
                producer.setSendMsgTimeout((int) timeout);

                // 异步发送消息
                producer.send(rocketMsg, new SendCallback() {
                    @Override
                    public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                        future.complete(convertSendResult(message.getMessageId(), sendResult));
                    }

                    @Override
                    public void onException(Throwable e) {
                        future.complete(BaseSendResult.failure(message.getMessageId(), e));
                    }
                });
            } finally {
                // 恢复原始超时时间
                producer.setSendMsgTimeout(originalTimeout);
            }
        } catch (Exception e) {
            log.error("异步发送消息失败: {}", message.getMessageId(), e);
            future.complete(BaseSendResult.failure(message.getMessageId(), e));
        }

        return future;
    }

    @Override
    public void close() {
        // 关闭主生产者
        if (producer != null) {
            try {
                producer.shutdown();
            } catch (Exception e) {
                log.error("关闭RocketMQ主生产者失败", e);
            }
        }
        // 清理其他组件
        localTransactionStateCache.clear();
        cacheManager.close();
        if (orderMessageQueueSelector != null)
            orderMessageQueueSelector = null;
    }

    /**
     * 转换RocketMQ发送结果为统一发送结果
     */
    private SendResult convertSendResult(String messageId, org.apache.rocketmq.client.producer.SendResult rocketResult) {
        if (rocketResult == null) {
            return BaseSendResult.failure(messageId, new ProducerException("发送结果为空"));
        }

        boolean isSuccess = rocketResult.getSendStatus() == SendStatus.SEND_OK;

        BaseSendResult result = new BaseSendResult();
        result.setMessageId(messageId);
        result.setSuccess(isSuccess);
        result.setRawResult(rocketResult);

        // 设置异常信息
        if (!isSuccess) {
            result.setException(new ProducerException("消息发送状态异常: " + rocketResult.getSendStatus()));
        }

        return result;
    }

    @Override
    public List<SendResult> batchSend(List<Message> messages, long timeout) {
        List<SendResult> results = new ArrayList<>(messages.size());

        // 按主题分组消息
        Map<String, List<Message>> messageGroups = messages.stream()
                .collect(Collectors.groupingBy(Message::getTopic));

        // 保存原始超时时间
        int originalTimeout = producer.getSendMsgTimeout();
        try {
            // 设置新的超时时间
            producer.setSendMsgTimeout((int) timeout);

            // 逐个主题发送
            for (Map.Entry<String, List<Message>> entry : messageGroups.entrySet()) {
                String topic = entry.getKey();
                checkTopic(topic);
                List<Message> topicMessages = entry.getValue();

                // 转换为RocketMQ消息
                List<org.apache.rocketmq.common.message.Message> rocketMessages = topicMessages.stream()
                        .map(converter::toMQMessage)
                        .toList();

                try {
                    // 发送批量消息
                    org.apache.rocketmq.client.producer.SendResult rocketResult = retryTemplate.execute(() -> {
                        try {
                            List<MessageQueue> messageQueues = producer.fetchPublishMessageQueues(topic);
                            // 是否顺序发送
                            if (this.annotationProperties != null && annotationProperties.isSequence()) {
                                MessageQueue messageQueue;
                                if (messageQueues.size() == 1)
                                    messageQueue = messageQueues.get(0);
                                else if (messageQueues.size() > 1)
                                    messageQueue = orderMessageQueueSelector.select(messageQueues, null, orderMessageQueueSelector.getOrderId(
                                            annotationProperties.getType(), annotationProperties.getCluster(), annotationProperties.getTopic()));
                                else
                                    return producer.send(rocketMessages);
                                return producer.send(rocketMessages, messageQueue);
                            } else
                                return producer.send(rocketMessages);
                        } catch (MQClientException | RemotingException | MQBrokerException | InterruptedException e) {
                            // 包装为RuntimeException以便RetryTemplate可以捕获
                            throw new RuntimeException("发送批量消息失败", e);
                        }
                    });

                    // 为所有消息设置相同的发送结果
                    for (Message message : topicMessages) {
                        results.add(convertSendResult(message.getMessageId(), rocketResult));
                    }
                } catch (Exception e) {
                    log.error("批量发送消息失败，主题: {}", topic, e);

                    // 设置失败结果
                    for (Message message : topicMessages) {
                        results.add(BaseSendResult.failure(message.getMessageId(), e));
                    }
                }
            }
        } finally {
            // 恢复原始超时时间
            producer.setSendMsgTimeout(originalTimeout);
        }

        return results;
    }


    /**
     * 异步批量发送消息
     *
     * @param messages 消息对象列表
     * @param timeout  超时时间（毫秒）
     * @return 发送结果列表
     */
    public CompletableFuture<List<SendResult>> asyncBatchSend(List<Message> messages, long timeout) {
        CompletableFuture<List<SendResult>> future = new CompletableFuture<>();

        // 按主题分组消息
        Map<String, List<Message>> messageGroups = messages.stream()
                .collect(Collectors.groupingBy(Message::getTopic));

        // 保存原始超时时间
        int originalTimeout = producer.getSendMsgTimeout();
        try {
            // 设置新的超时时间
            producer.setSendMsgTimeout((int) timeout);
            List<SendResult> results = new ArrayList<>(messages.size());
            // 逐个主题发送
            for (Map.Entry<String, List<Message>> entry : messageGroups.entrySet()) {
                String topic = entry.getKey();
                List<Message> topicMessages = entry.getValue();
                // 转换为RocketMQ消息
                List<org.apache.rocketmq.common.message.Message> rocketMessages = topicMessages.stream()
                        .map(converter::toMQMessage)
                        .toList();
                try {
                    // 异步发送消息
                    producer.send(rocketMessages, new SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            results.add(convertSendResult(sendResult.getMsgId(), sendResult));
                        }

                        @Override
                        public void onException(Throwable e) {
                            results.add(BaseSendResult.failure(topicMessages.get(0).getMessageId(), e));
                        }
                    });
                } catch (Exception e) {
                    log.error("批量发送消息失败，主题: {}", topic, e);
                }
            }
        } finally {
            // 恢复原始超时时间
            producer.setSendMsgTimeout(originalTimeout);
        }

        return future;
    }

    @Override
    public List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit, long timeout) {
        // 保存原始延迟时间
        List<Long> oldDelay = new ArrayList<>(messages.size());

        try {
            List<SendResult> results = new ArrayList<>();
            // 设置新的延迟时间
            for (Message message : messages) {
                oldDelay.add(message.getDelayTime());
                message.setDelayTime(unit.toMillis(delay));
                results.add(send(message,timeout));
            }

            // 发送消息
            return results;
        } finally {
            // 恢复原始延迟时间
            for (int i = 0; i < messages.size() && i < oldDelay.size(); i++) {
                Message message = messages.get(i);
                message.setDelayTime(oldDelay.get(i));
            }
        }
    }

    @Override
    public List<SendResult> sendTransaction(List<Message> messages, long timeout) {
        String messageId = messages.get(0).getMessageId();
        try {

            // 保存原始超时时间
            int originalTimeout = producer.getSendMsgTimeout();

            // 构建事务id
            String key = annotationProperties.getType() + "::" + annotationProperties.getCluster() + "::" + annotationProperties.getTopic() + "::" + messageId;
            localTransactionStateCache.put(key, LocalTransactionState.UNKNOW);

            // 转换为RocketMQ消息
            try {
                // 结果
                List<SendResult> results = new ArrayList<>(messages.size());
                // 设置新的超时时间
                producer.setSendMsgTimeout((int) timeout);

                // 发送事务消息
                for (Message message : messages) {
                    org.apache.rocketmq.common.message.Message rocketMessage = converter.toMQMessage(message);
                    rocketMessage.putUserProperty("transaction-id", key);
                    TransactionSendResult rocketResult = producer.sendMessageInTransaction(rocketMessage, key);
                    if (rocketResult.getSendStatus() != SendStatus.SEND_OK)
                        throw new ProducerException("事务消息发送失败");
                    results.add(BaseSendResult.success(message.getMessageId(), rocketResult));
                }

                // 事务状态
                localTransactionStateCache.put(key, LocalTransactionState.COMMIT_MESSAGE);
                // 转换发送结果
                return results;
            } catch (Exception e) {
                // 事务状态
                localTransactionStateCache.put(key, LocalTransactionState.ROLLBACK_MESSAGE);
                throw new ProducerException("事务消息发送异常", e);
            } finally {
                // 恢复原始超时时间
                producer.setSendMsgTimeout(originalTimeout);
            }
        } catch (Exception e) {
            log.error("发送事务消息失败: {}", messageId, e);
            return messages.stream()
                    .map(message -> BaseSendResult.failure(message.getMessageId(), e))
                    .collect(Collectors.toList());
        }
    }

    private static class OrderMessageQueueSelector implements MessageQueueSelector {

        public String getOrderId(MessageQueueType type, String cluster, String topic) {
            return type + "::" + cluster + "::" + topic;
        }

        @Override
        public MessageQueue select(List<MessageQueue> mqs, org.apache.rocketmq.common.message.Message msg, Object arg) {
            String orderId = (String) arg; // arg 即传入的业务 ID
            int index = Math.abs(orderId.hashCode()) % mqs.size();
            return mqs.get(index); // 同一 orderId 始终选同一队列
        }
    }
}