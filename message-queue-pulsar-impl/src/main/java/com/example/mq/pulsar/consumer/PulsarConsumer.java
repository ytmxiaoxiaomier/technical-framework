package com.example.mq.pulsar.consumer;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.core.exception.ConsumerException;
import com.example.mq.pulsar.converter.PulsarMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pulsar消费者实现
 */
@Slf4j
public class PulsarConsumer implements IConsumer {
    private final PulsarClient client;
    private final PulsarMessageConverter converter;
    private final MessageQueueProperties properties;
    private final Map<String, org.apache.pulsar.client.api.Consumer<byte[]>> consumers = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    public PulsarConsumer(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new PulsarMessageConverter(serializer);

        try {
            // 创建Pulsar客户端
            this.client = createPulsarClient();
        } catch (Exception e) {
            throw new ConnectionException("创建Pulsar客户端失败", e);
        }
    }

    /**
     * 创建Pulsar客户端
     */
    private PulsarClient createPulsarClient() throws PulsarClientException {
        ClientBuilder clientBuilder = PulsarClient.builder();

        // 设置服务地址
        clientBuilder.serviceUrl(properties.getServer());

        // 设置操作超时时间
        int operationTimeout = getProperty("operationTimeout", 30000);
        clientBuilder.operationTimeout(operationTimeout, TimeUnit.MILLISECONDS);

        // 设置连接超时时间
        int connectionTimeout = getProperty("connectionTimeout", 10000);
        clientBuilder.connectionTimeout(connectionTimeout, TimeUnit.MILLISECONDS);

        // 设置是否使用TLS
        boolean useTls = getProperty("useTls", false);
        String tlsCertificateFilePath = getProperty("tlsCertificateFilePath", "");
        String tlsKeyFilePath = getProperty("tlsKeyFilePath", "");
        if (useTls && StringUtils.hasText(tlsCertificateFilePath)) {
            clientBuilder.authentication(AuthenticationFactory.TLS(tlsCertificateFilePath, tlsKeyFilePath));
        }

        // 设置是否允许TLS不受信任的服务器
        boolean allowTlsInsecureConnection = getProperty("allowTlsInsecureConnection", false);
        clientBuilder.allowTlsInsecureConnection(allowTlsInsecureConnection);

        // 设置是否验证主机名
        boolean enableTlsHostnameVerification = getProperty("enableTlsHostnameVerification", false);
        clientBuilder.enableTlsHostnameVerification(enableTlsHostnameVerification);

        // 设置认证参数
        String authPluginClassName = getProperty("authPluginClassName", "");
        String authParams = getProperty("authParams", "");
        if (StringUtils.hasText(authPluginClassName) && StringUtils.hasText(authParams)) {
            clientBuilder.authentication(authPluginClassName, authParams);
        }

        return clientBuilder.build();
    }

    @Override
    public void subscribe(String topic, String consumerGroup) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(consumerGroup)) {
            throw new IllegalArgumentException("主题和消费者组不能为空");
        }

        String key = buildConsumerKey(topic, consumerGroup);

        // 如果已经订阅，直接返回
        if (consumers.containsKey(key)) {
            log.warn("Pulsar消费者已经订阅主题: {}, 消费者组: {}", topic, consumerGroup);
            return;
        }

        try {
            // 创建消费者
            org.apache.pulsar.client.api.Consumer<byte[]> consumer = createConsumer(topic, consumerGroup);

            // 添加到消费者Map
            consumers.put(key, consumer);

            // 启动消息监听
            startListening(key, consumer);

            log.info("Pulsar消费者订阅主题成功: {}, 消费者组: {}", topic, consumerGroup);
        } catch (Exception e) {
            throw new ConsumerException("订阅Pulsar主题失败: " + topic, e);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        // 查找所有该主题的消费者
        consumers.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key.startsWith(topic + ":")) {
                org.apache.pulsar.client.api.Consumer<byte[]> consumer = entry.getValue();
                try {
                    consumer.unsubscribe();
                    consumer.close();
                    listeners.remove(key);
                    log.info("Pulsar消费者取消订阅主题: {}", topic);
                } catch (Exception e) {
                    log.error("取消订阅Pulsar主题失败: {}", topic, e);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public void pause() {
        // Pulsar不直接支持暂停消费，可以通过关闭或停止接收消息来实现
        // 这里简化处理，记录暂停状态，不再接收新消息
        running.set(false);
        log.info("Pulsar消费者已暂停");
    }

    @Override
    public void resume() {
        // 恢复接收消息
        running.set(true);

        // 重新开始接收消息
        for (Map.Entry<String, org.apache.pulsar.client.api.Consumer<byte[]>> entry : consumers.entrySet()) {
            String key = entry.getKey();
            org.apache.pulsar.client.api.Consumer<byte[]> consumer = entry.getValue();
            startListening(key, consumer);
        }

        log.info("Pulsar消费者已恢复");
    }

    @Override
    public void close() {
        // 设置运行状态为false
        running.set(false);

        // 关闭所有消费者
        for (org.apache.pulsar.client.api.Consumer<byte[]> consumer : consumers.values()) {
            try {
                consumer.close();
            } catch (Exception e) {
                log.error("关闭Pulsar消费者失败", e);
            }
        }
        consumers.clear();
        listeners.clear();

        // 关闭客户端
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.error("关闭Pulsar客户端失败", e);
            }
        }

        log.info("Pulsar消费者已关闭");
    }

    /**
     * 注册消息监听器
     *
     * @param topic    主题
     * @param group    消费者组
     * @param listener 监听器
     */
    public void registerListener(String topic, String group, MessageListener listener) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(group) || listener == null) {
            throw new IllegalArgumentException("主题、消费者组和监听器不能为空");
        }

        String key = buildConsumerKey(topic, group);
        listeners.put(key, listener);

        // 如果消费者已存在，确保已订阅
        if (!consumers.containsKey(key)) {
            subscribe(topic, group);
        }
    }

    /**
     * 创建Pulsar消费者
     */
    private org.apache.pulsar.client.api.Consumer<byte[]> createConsumer(String topic, String subscriptionName) throws PulsarClientException {
        ConsumerBuilder<byte[]> builder = client.newConsumer(Schema.BYTES)
                .topic(topic)
                .subscriptionName(subscriptionName)
                .consumerName("consumer-" + UUID.randomUUID().toString());

        // 设置订阅类型
        String subType = getProperty("subscriptionType", "Shared");
        builder.subscriptionType(SubscriptionType.valueOf(subType));

        // 设置订阅初始位置
        String initialPosition = getProperty("subscriptionInitialPosition", "Latest");
        builder.subscriptionInitialPosition(SubscriptionInitialPosition.valueOf(initialPosition));

        // 设置消费者队列大小
        int receiverQueueSize = getProperty("receiverQueueSize", 1000);
        builder.receiverQueueSize(receiverQueueSize);

        // 设置确认超时时间
        int ackTimeout = getProperty("ackTimeout", 0);
        if (ackTimeout > 0) {
            builder.ackTimeout(ackTimeout, TimeUnit.MILLISECONDS);
        }

        // 设置是否启用批量确认
        boolean batchAckEnabled = getProperty("batchAckEnabled", true);
        builder.enableBatchIndexAcknowledgment(batchAckEnabled);

        // 设置负载均衡策略
        builder.subscriptionTopicsMode(org.apache.pulsar.client.api.RegexSubscriptionMode.AllTopics);

        return builder.subscribe();
    }

    /**
     * 启动消息监听
     */
    private void startListening(String key, org.apache.pulsar.client.api.Consumer<byte[]> consumer) {
        // 启动一个线程来接收消息
        Thread thread = new Thread(() -> {
            while (running.get()) {
                try {
                    // 接收消息，设置超时时间
                    org.apache.pulsar.client.api.Message<byte[]> message = consumer.receive(100, TimeUnit.MILLISECONDS);
                    if (message != null) {
                        processMessage(key, consumer, message);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        log.error("接收Pulsar消息失败", e);
                    }
                }
            }
        });
        thread.setName("pulsar-consumer-" + key);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 处理接收到的消息
     */
    private void processMessage(String key, org.apache.pulsar.client.api.Consumer<byte[]> consumer, org.apache.pulsar.client.api.Message<byte[]> message) {
        // 获取监听器
        MessageListener listener = listeners.get(key);
        if (listener == null) {
            log.error("未找到消息监听器: {}", key);
            try {
                // 没有监听器，直接确认消息
                consumer.acknowledge(message);
            } catch (Exception e) {
                log.error("确认Pulsar消息失败", e);
            }
            return;
        }

        try {
            // 转换为统一消息对象
            com.example.mq.core.message.Message unifiedMessage = converter.fromMQMessage(message);

            // 获取主题和消费者组
            String[] parts = key.split(":");
            String topic = parts[0];
            String group = parts[1];

            // 创建消费上下文
            PulsarConsumeContext consumeContext = new PulsarConsumeContext(topic, group, message, consumer);

            // 调用监听器处理消息
            MessageListener.ConsumeResult result = listener.onMessage(unifiedMessage, consumeContext);

            // 处理消费结果
            if (result == MessageListener.ConsumeResult.SUCCESS) {
                // 消费成功，确认消息
                consumer.acknowledge(message);
            } else {
                // 消费失败，否定消息
                consumer.negativeAcknowledge(message);
            }
        } catch (Exception e) {
            log.error("处理Pulsar消息异常", e);
            // 异常情况下，否定消息，让消息重新投递
            consumer.negativeAcknowledge(message);
        }
    }

    /**
     * 构建消费者键
     */
    private String buildConsumerKey(String topic, String group) {
        return topic + ":" + group;
    }

    /**
     * 获取配置属性
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

    /**
     * Pulsar消费上下文
     */
    private static class PulsarConsumeContext implements MessageListener.ConsumeContext {
        private final String topic;
        private final String consumerGroup;
        private final org.apache.pulsar.client.api.Message<byte[]> rawMessage;
        private final org.apache.pulsar.client.api.Consumer<byte[]> consumer;

        public PulsarConsumeContext(String topic, String consumerGroup, org.apache.pulsar.client.api.Message<byte[]> rawMessage,
                                    org.apache.pulsar.client.api.Consumer<byte[]> consumer) {
            this.topic = topic;
            this.consumerGroup = consumerGroup;
            this.rawMessage = rawMessage;
            this.consumer = consumer;
        }

        @Override
        public String consumerGroup() {
            return consumerGroup;
        }

        @Override
        public String topic() {
            return topic;
        }

        @Override
        public Object getRawMessage() {
            return rawMessage;
        }

        @Override
        public void acknowledge() {
            try {
                consumer.acknowledge(rawMessage);
            } catch (Exception e) {
                throw new ConsumerException("确认Pulsar消息失败", e);
            }
        }

        @Override
        public void reject(boolean requeue) {
            if (requeue) {
                // 否定确认，消息会重新投递
                consumer.negativeAcknowledge(rawMessage);
            } else {
                // 确认消息但不重新投递
                try {
                    consumer.acknowledge(rawMessage);
                } catch (Exception e) {
                    throw new ConsumerException("确认Pulsar消息失败", e);
                }
            }
        }
    }
} 