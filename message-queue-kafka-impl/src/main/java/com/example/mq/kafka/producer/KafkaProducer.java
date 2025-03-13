package com.example.mq.kafka.producer;

import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.core.exception.ProducerException;
import com.example.mq.core.message.BaseSendResult;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;
import com.example.mq.core.producer.AbstractProducer;
import com.example.mq.core.producer.IProducer;
import com.example.mq.kafka.converter.KafkaMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.OutOfOrderSequenceException;
import org.apache.kafka.common.errors.ProducerFencedException;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Kafka生产者实现
 */
@Slf4j
public class KafkaProducer extends AbstractProducer {
    private final org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> producer;
    private final KafkaMessageConverter converter;
    private final MessageQueueProperties properties;
    private ProducerAnnotationProperties annotationProperties;
    private final Set<String> exitsTopics = new ConcurrentSkipListSet<>();

    public KafkaProducer(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new KafkaMessageConverter(serializer);
        if (properties.getProperties().containsKey(IProducer.class.getName())
                && properties.getProperties().get(IProducer.class.getName()) instanceof ProducerAnnotationProperties annoProperties)
            this.annotationProperties = annoProperties;
        // 创建生产者
        this.producer = createProducer();
    }

    /**
     * 创建Kafka生产者
     */
    private org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> createProducer() {
        Properties props = new Properties();

        // 设置Kafka服务器地址
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getServer());

        // 设置键和值的序列化器
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

        // 设置客户端ID
        String clientId = getProperty("clientId", "message-queue-kafka-producer");
        props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);

        // 设置确认机制
        String acks = getProperty("acks", "all");
        props.put(ProducerConfig.ACKS_CONFIG, acks);

        // 设置重试次数
        int retries = getProperty("retries", 3);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);

        // 设置批处理大小
        int batchSize = getProperty("batchSize", 16384);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);

        // 设置linger时间
        int linger = getProperty("linger.ms", 1);
        props.put(ProducerConfig.LINGER_MS_CONFIG, linger);

        // 设置缓冲区大小
        int bufferMemory = getProperty("buffer.memory", 33554432);
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, bufferMemory);

        // 设置压缩类型
        String compressionType = getProperty("compression.type", "none");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compressionType);

        // 添加其他自定义配置
        Map<String, Object> customProps = properties.getProperties();
        if (customProps != null) {
            customProps.forEach((key, value) -> {
                if (value != null) {
                    props.put(key, value);
                }
            });
        }

        boolean openTransaction = false;

        // 特殊处理
        if (annotationProperties != null) {
            // 支持顺序
            if (annotationProperties.isSequence()) {
                props.put(ProducerConfig.ACKS_CONFIG, "all"); // 所有副本确认
                props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1); // 禁用管道
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 幂等性
                props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, OrderIdPartitioner.class.getName()); // 分区限制
            }
            // 支持事务
            if (StringUtils.hasText(annotationProperties.getTransactionId())) {
                props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 幂等性
                props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, annotationProperties.getTransactionId()); // 事务ID(必须唯一)
                props.put(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, annotationProperties.getTimeout());
                openTransaction = true;
            }
        }

        try {
            org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> kafkaProducer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
            if (openTransaction)
                kafkaProducer.initTransactions();
            return kafkaProducer;
        } catch (Exception e) {
            throw new ConnectionException("创建Kafka生产者失败", e);
        }
    }

    private void checkTopic(String topic) {
        if (exitsTopics.contains(topic))
            return;
        Properties props = new Properties();
        // 设置Kafka服务器地址
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getServer());
        // 判断topic是否在线
        try (AdminClient adminClient = AdminClient.create(props)) {
            // 检查 Topic 是否存在
            boolean topicExists = adminClient.listTopics().names().get().contains(topic);

            if (!topicExists) {
                // 创建 Topic
                NewTopic newTopic = new NewTopic(topic, 3, (short) 1);
                adminClient.createTopics(List.of(newTopic)).all().get();
                log.info("Topic 创建成功: {}", topic);
            }
            exitsTopics.add(topic);
        } catch (InterruptedException | ExecutionException e) {
            log.error("创建主题失败，topic：{}", topic, e);
            exitsTopics.remove(topic);
        }
    }

    /**
     * 获取Kafka配置属性
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
            checkTopic(message.getTopic());
            ProducerRecord<String, byte[]> record = converter.toProducerRecord(message);
            RecordMetadata metadata = producer.send(record)
                    .get(timeout, TimeUnit.MILLISECONDS);
            return new BaseSendResult(true, metadata.toString(), metadata);
        } catch (Exception e) {
            throw new ProducerException("发送消息失败", e);
        }
    }

    @Override
    public CompletableFuture<SendResult> asyncSend(Message message, long timeout) {
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        ProducerRecord<String, byte[]> record = converter.toProducerRecord(message);

        producer.send(record, (metadata, exception) -> {
            if (exception != null) {
                future.completeExceptionally(new ProducerException("异步发送消息失败", exception));
            } else {
                future.complete(new BaseSendResult(true, metadata.toString(), metadata));
            }
        });

        return future;
    }

    @Override
    public List<SendResult> batchSend(List<Message> messages, long timeout) {
        List<SendResult> results = new ArrayList<>();
        for (Message message : messages) {
            try {
                results.add(send(message, timeout));
            } catch (Exception e) {
                results.add(new BaseSendResult(false, null, e));
            }
        }
        return results;
    }

    @Override
    public CompletableFuture<List<SendResult>> asyncBatchSend(List<Message> messages, long timeout) {
        CompletableFuture<List<SendResult>> future = new CompletableFuture<>();
        List<SendResult> results = new ArrayList<>();
        for (Message message : messages) {
            ProducerRecord<String, byte[]> record = converter.toProducerRecord(message);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    results.add(BaseSendResult.failure(message.getMessageId(), exception));
                } else {
                    results.add(BaseSendResult.success(message.getMessageId(), metadata));
                }
            });
        }
        future.complete(results);
        return future;
    }

    @Override
    public List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit, long timeout) {
        try {
            List<SendResult> results = new ArrayList<>(messages.size());
            for (Message message : messages) {
                ProducerRecord<String, byte[]> record = converter.toKafkaMessage(message);
                record.headers().add("delayTime", Long.toString(unit.toMillis(delay)).getBytes());
                RecordMetadata metadata = producer.send(record)
                        .get(timeout, TimeUnit.MILLISECONDS);
                results.add(new BaseSendResult(true, metadata.toString(), metadata));
            }
            return results;
        } catch (Exception e) {
            throw new ProducerException("发送消息失败", e);
        }
    }

    @Override
    public List<SendResult> sendTransaction(List<Message> messages, long timeout) {
        if (messages == null || messages.isEmpty())
            return new ArrayList<>();
        try {
            List<SendResult> results = new ArrayList<>(messages.size());
            // 开启事务
            producer.beginTransaction();
            // 生产消息
            for (Message message : messages) {
                SendResult sendResult = send(message, timeout);
                if (!sendResult.isSuccess())
                    throw new ProducerException("发送消息失败: " + sendResult.getRawResult());
                results.add(sendResult);
            }
            // 提交事务
            producer.commitTransaction();
            return results;
        } catch (ProducerFencedException | AuthorizationException | OutOfOrderSequenceException e) {
            throw new ProducerException("发送消息失败", e);
        } catch (Exception e) {
            // 事务回滚
            producer.abortTransaction();
            return messages.stream()
                    .map(message -> BaseSendResult.failure(message.getMessageId(), e))
                    .collect(Collectors.toList());
        }
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
} 