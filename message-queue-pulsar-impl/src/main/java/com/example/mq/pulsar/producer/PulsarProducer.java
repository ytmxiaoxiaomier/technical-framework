package com.example.mq.pulsar.producer;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.core.exception.ProducerException;
import com.example.mq.core.message.BaseSendResult;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;
import com.example.mq.core.producer.AbstractProducer;
import com.example.mq.pulsar.converter.PulsarMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.*;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Pulsar生产者实现
 */
@Slf4j
public class PulsarProducer extends AbstractProducer {
    private final PulsarClient client;
    private final PulsarMessageConverter converter;
    private final MessageQueueProperties properties;
    private final Map<String, org.apache.pulsar.client.api.Producer<byte[]>> producers = new ConcurrentHashMap<>();

    public PulsarProducer(MessageQueueProperties properties, MessageSerializer serializer) {
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
            AuthenticationFactory.TLS(tlsCertificateFilePath, tlsKeyFilePath);
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

        // 添加其他自定义配置
        Map<String, Object> customProps = properties.getProperties();
        if (customProps != null) {
            // 处理其他可能的自定义配置
        }

        return clientBuilder.build();
    }

    /**
     * 获取或创建主题生产者
     */
    private org.apache.pulsar.client.api.Producer<byte[]> getOrCreateProducer(String topic) {
        return producers.computeIfAbsent(topic, t -> {
            try {
                ProducerBuilder<byte[]> producerBuilder = client.newProducer(Schema.BYTES)
                        .topic(t)
                        .producerName("producer-" + t)
                        .sendTimeout(getProperty("sendTimeout", 30), TimeUnit.SECONDS)
                        .blockIfQueueFull(getProperty("blockIfQueueFull", true))
                        .maxPendingMessages(getProperty("maxPendingMessages", 1000))
                        .enableBatching(getProperty("enableBatching", true))
                        .batchingMaxPublishDelay(
                                getProperty("batchingMaxPublishDelay", 1),
                                TimeUnit.MILLISECONDS
                        )
                        .batchingMaxMessages(getProperty("batchingMaxMessages", 1000));

                // 设置压缩类型
                String compressionType = getProperty("compressionType", "");
                if (StringUtils.hasText(compressionType)) {
                    org.apache.pulsar.client.api.CompressionType type =
                            org.apache.pulsar.client.api.CompressionType.valueOf(compressionType.toUpperCase());
                    producerBuilder.compressionType(type);
                }

                return producerBuilder.create();
            } catch (Exception e) {
                throw new ProducerException("创建Pulsar生产者失败: " + t, e);
            }
        });
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

    @Override
    public SendResult send(Message message, long timeout) {
        try {
            // 获取生产者
            org.apache.pulsar.client.api.Producer<byte[]> producer = getOrCreateProducer(message.getTopic());

            // 创建消息构建器
            TypedMessageBuilder<byte[]> builder = producer.newMessage();

            // 设置消息属性
            converter.setMessageBuilderProperties(builder, message);

            // 同步发送消息，使用 get 方法设置超时
            MessageId messageId = builder.sendAsync().get(timeout, TimeUnit.MILLISECONDS);

            // 转换发送结果
            BaseSendResult result = new BaseSendResult();
            result.setMessageId(message.getMessageId());
            result.setSuccess(true);
            result.setRawResult(messageId);

            return result;
        } catch (Exception e) {
            log.error("发送消息失败: {}", message.getMessageId(), e);
            return BaseSendResult.failure(message.getMessageId(), e);
        }
    }

    @Override
    public CompletableFuture<SendResult> asyncSend(Message message, long timeout) {
        CompletableFuture<SendResult> future = new CompletableFuture<>();

        try {
            // 获取生产者
            org.apache.pulsar.client.api.Producer<byte[]> producer = getOrCreateProducer(message.getTopic());

            // 创建消息构建器
            TypedMessageBuilder<byte[]> builder = producer.newMessage();

            // 设置消息属性
            converter.setMessageBuilderProperties(builder, message);

            // 异步发送消息
            builder.sendAsync().whenComplete((messageId, throwable) -> {
                        if (throwable != null) {
                            future.complete(BaseSendResult.failure(message.getMessageId(), throwable));
                        } else {
                            BaseSendResult result = new BaseSendResult();
                            result.setMessageId(message.getMessageId());
                            result.setSuccess(true);
                            result.setRawResult(messageId);
                            future.complete(result);
                        }
                    }).orTimeout(timeout, TimeUnit.MILLISECONDS)
                    .exceptionally(throwable -> {
                        future.complete(BaseSendResult.failure(message.getMessageId(), throwable));
                        return null;
                    });
        } catch (Exception e) {
            log.error("异步发送消息失败: {}", message.getMessageId(), e);
            future.complete(BaseSendResult.failure(message.getMessageId(), e));
        }

        return future;
    }

    @Override
    public List<SendResult> batchSend(List<Message> messages, long timeout) {
        List<SendResult> results = new ArrayList<>(messages.size());

        // 按主题分组消息
        Map<String, List<Message>> messagesByTopic = new HashMap<>();
        for (Message message : messages) {
            messagesByTopic.computeIfAbsent(message.getTopic(), k -> new ArrayList<>()).add(message);
        }

        // 对每个主题的消息批量发送
        for (Map.Entry<String, List<Message>> entry : messagesByTopic.entrySet()) {
            List<Message> topicMessages = entry.getValue();

            for (Message message : topicMessages) {
                results.add(send(message, timeout));
            }
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
        throw new UnsupportedOperationException("Pulsar事务消息操作未实现");
    }

    @Override
    public List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit, long timeout) {
        return messages.stream()
                .map(message -> sendDelayed(message, delay, unit, timeout))
                .toList();
    }

    private SendResult sendDelayed(Message message, long delay, TimeUnit unit, long timeout) {
        try {
            // 获取生产者
            org.apache.pulsar.client.api.Producer<byte[]> producer = getOrCreateProducer(message.getTopic());

            // 保存原始延迟时间
            long oldDelay = message.getDelayTime();

            try {
                // 设置新的延迟时间
                message.setDelayTime(unit.toMillis(delay));

                // 创建消息构建器
                TypedMessageBuilder<byte[]> builder = producer.newMessage();

                // 设置消息属性
                converter.setMessageBuilderProperties(builder, message);

                // 设置延迟时间
                builder.deliverAfter(delay, unit);

                // 同步发送消息，使用 get 方法设置超时
                MessageId messageId = builder.sendAsync().get(timeout, TimeUnit.MILLISECONDS);

                // 转换发送结果
                BaseSendResult result = new BaseSendResult();
                result.setMessageId(message.getMessageId());
                result.setSuccess(true);
                result.setRawResult(messageId);

                return result;
            } finally {
                // 恢复原始延迟时间
                message.setDelayTime(oldDelay);
            }
        } catch (Exception e) {
            log.error("发送延迟消息失败: {}", message.getMessageId(), e);
            return BaseSendResult.failure(message.getMessageId(), e);
        }
    }

    @Override
    public List<SendResult> sendTransaction(List<Message> messages, long timeout) {
        // Pulsar支持事务，但需要配置和启用事务协调器
        // 这里简化处理，直接发送普通消息
        throw new UnsupportedOperationException("Pulsar事务消息操作未实现");
    }

    @Override
    public void close() {
        // 关闭所有生产者
        for (org.apache.pulsar.client.api.Producer<byte[]> producer : producers.values()) {
            try {
                producer.close();
            } catch (Exception e) {
                log.error("关闭Pulsar生产者失败", e);
            }
        }
        producers.clear();

        // 关闭客户端
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                log.error("关闭Pulsar客户端失败", e);
            }
        }
    }
} 