package com.example.mq.kafka.consumer;

import com.example.mq.core.annotation.ConsumerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.exception.ConsumerException;
import com.example.mq.core.message.Message;
import com.example.mq.kafka.converter.KafkaMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ByteArraySerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.record.TimestampType;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka消费者实现
 */
@Slf4j
public class KafkaConsumerImpl implements KafkaConsumer {
    private final MessageQueueProperties properties;
    private ConsumerAnnotationProperties consumerAnnotationProperties;
    private final KafkaMessageConverter converter;
    private org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> consumer;
    private final Set<String> subscribedTopics = new HashSet<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private String currentGroup;
    private RedisTemplate<String, String> delayTemplate;
    private MessageListener delayListener;
    private ScheduledExecutorService scheduler;

    public KafkaConsumerImpl(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new KafkaMessageConverter(serializer);
        if (properties.getProperties().containsKey(IConsumer.class.getName())
                && properties.getProperties().get(IConsumer.class.getName()) instanceof ConsumerAnnotationProperties annoProperties)
            this.consumerAnnotationProperties = annoProperties;
    }

    private org.apache.kafka.clients.consumer.KafkaConsumer<String, byte[]> createConsumer(String group) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getServer());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        String clientId = getProperty("clientId", "message-queue-kafka-consumer");
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, clientId);

        int autoCommitInterval = getProperty("auto.commit.interval.ms", 5000);
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, autoCommitInterval);

        int sessionTimeout = getProperty("session.timeout.ms", 30000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, sessionTimeout);

        int heartbeatInterval = getProperty("heartbeat.interval.ms", 3000);
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, heartbeatInterval);

        int maxPollInterval = getProperty("max.poll.interval.ms", 300000);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, maxPollInterval);

        int maxPollRecords = getProperty("max.poll.records", 500);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);

        if (StringUtils.hasText(group)) {
            props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
            boolean enableAutoCommit = getProperty("enable.auto.commit", true);
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
        }

        Map<String, Object> customProps = properties.getProperties();
        if (customProps != null) {
            customProps.forEach((key, value) -> {
                if (value != null) {
                    props.put(key, value);
                }
            });
        }

        // 特殊处理
        if (this.consumerAnnotationProperties != null) {
            if (StringUtils.hasText(this.consumerAnnotationProperties.getGroup())) {
                props.put(ConsumerConfig.GROUP_ID_CONFIG, this.consumerAnnotationProperties.getGroup());
                boolean enableAutoCommit = getProperty("enable.auto.commit", true);
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, enableAutoCommit);
            }
            // 顺序消费
            if (this.consumerAnnotationProperties.isSequence()) {
                props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
                props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1);
            }
            // 事务消费
            if (this.consumerAnnotationProperties.isTransaction()) {
                props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
                props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
            }
            // 延迟消费
            if (this.consumerAnnotationProperties.isDelayQueue()) {
                Map<String, String> redisProperties = this.consumerAnnotationProperties.getProperties();
                // 创建redis
                RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
                config.setHostName(redisProperties.get("host"));
                config.setPort(Integer.parseInt(redisProperties.get("port")));
                config.setPassword(redisProperties.get("password"));
                delayTemplate = new RedisTemplate<>();
                delayTemplate.setConnectionFactory(new LettuceConnectionFactory(config));
                // 序列化配置
                delayTemplate.setKeySerializer(new StringRedisSerializer());
                delayTemplate.setValueSerializer(new StringRedisSerializer());
                delayTemplate.afterPropertiesSet();
                // 延迟消费线程
                // 创建定时线程池（核心线程数=CPU核心数）
                scheduler = new ScheduledThreadPoolExecutor(
                        Runtime.getRuntime().availableProcessors(),
                        new ThreadPoolExecutor.AbortPolicy() // 任务满时直接拒绝
                );
                // 示例：每秒执行任务（带异常捕获）
                scheduler.scheduleAtFixedRate(() -> {
                    try {
                        System.out.println("执行定时任务: " + new Date());
                    } catch (Exception e) {
                        System.err.println("任务异常: " + e.getMessage()); // 必须捕获异常防止线程终止
                    }
                }, 1, 10, TimeUnit.SECONDS);
            }
        }

        return new org.apache.kafka.clients.consumer.KafkaConsumer<>(props);
    }

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
    public void subscribe(String topic, String group) {
        if (subscribedTopics.add(topic)) {
            this.consumer = createConsumer(group);
            consumer.subscribe(Collections.singleton(topic));
            this.currentGroup = group;
            log.info("订阅主题: {}, 消费组: {}", topic, group);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        if (subscribedTopics.remove(topic)) {
            consumer.unsubscribe();
            log.info("取消订阅主题: {}", topic);
        }
    }

    @Override
    public void poll(MessageListener listener) {
        try {
            boolean delayQueue = this.consumerAnnotationProperties != null && this.consumerAnnotationProperties.isDelayQueue();
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(3000));
            for (ConsumerRecord<String, byte[]> record : records) {
                if (delayQueue) {
                    Header executeTimeHeader = record.headers().lastHeader("executeTime");
                    if (executeTimeHeader != null) {
                        long executeTime = Long.parseLong(Arrays.toString(executeTimeHeader.value()));
                        if (executeTime > System.currentTimeMillis()) {
                            delayListener = listener;
                            delayConsume(record, executeTime);
                            continue;
                        }
                    }
                }
                processMessage(listener, record);
            }
        } catch (Exception e) {
            log.error("轮询消息失败", e);
        }
    }

    private void processMessage(MessageListener listener, ConsumerRecord<String, byte[]> record) {
        try {
            Message message = converter.toMessage(record);
            final String topic = record.topic();
            final int partition = record.partition();
            final long offset = record.offset();

            MessageListener.ConsumeContext context = new MessageListener.ConsumeContext() {
                @Override
                public String consumerGroup() {
                    return currentGroup;
                }

                @Override
                public String topic() {
                    return topic;
                }

                @Override
                public Object getRawMessage() {
                    return record;
                }

                @Override
                public void acknowledge() {
                    commitOffset(topic, partition, offset + 1);
                }

                @Override
                public void reject(boolean requeue) {
                    if (!requeue) {
                        commitOffset(topic, partition, offset + 1);
                    }
                }
            };

            MessageListener.ConsumeResult result = listener.onMessage(message, context);

            if (result == MessageListener.ConsumeResult.SUCCESS) {
                if (!getProperty("enable.auto.commit", true)) {
                    commitOffset(record.topic(), record.partition(), record.offset() + 1);
                }
            } else {
                log.warn("消息消费失败: {}", message.getMessageId());
            }
        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    private void delayConsume(ConsumerRecord<String, byte[]> record, long executeTime) throws JsonProcessingException {
        // 分桶策略：每10秒一个桶（可根据业务调整）
        long bucket = executeTime / 10_000;
        String bucketKey = "delay:bucket:" + bucket;
        // 存储到 Redis Sorted Set（Score为执行时间戳）
        delayTemplate.opsForZSet().add(bucketKey, objectMapper.writeValueAsString(record), executeTime);
    }

    private void processExpiredMessages() throws JsonProcessingException {
        long currentTime = System.currentTimeMillis();

        // 扫描当前时间前2个分桶（覆盖可能的延迟）
        long currentBucket = currentTime / 10_000;
        for (long bucket = currentBucket - 2; bucket <= currentBucket; bucket++) {
            String bucketKey = "delay:bucket:" + bucket;

            // 拉取 Score <= currentTime 的所有消息
            var messages = delayTemplate.opsForZSet()
                    .rangeByScoreWithScores(bucketKey, 0, currentTime);

            if (messages != null) {
                for (ZSetOperations.TypedTuple<String> message : messages) {
                    // 延迟处理
                    @SuppressWarnings("unchecked")
                    ConsumerRecord<String, byte[]> record = objectMapper.readValue(message.getValue(), ConsumerRecord.class);
                    processMessage(delayListener, record);
                    // 从 Redis 删除已处理消息
                    delayTemplate.opsForZSet().remove(bucketKey, message.getValue());
                }
            }
        }
    }

    // 配置带自定义模块的 ObjectMapper
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new SimpleModule()
                    .addSerializer(byte[].class, new ByteArraySerializer())  // 序列化 byte[] 为 Base64
                    .addDeserializer(ConsumerRecord.class, new ConsumerRecordDeserializer()));

    // 极简反序列化器（核心逻辑）
    private static class ConsumerRecordDeserializer extends JsonDeserializer<ConsumerRecord<String, byte[]>> {
        @Override
        public ConsumerRecord<String, byte[]> deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            return new ConsumerRecord<>(
                    node.get("topic").asText(),
                    node.get("partition").asInt(),
                    node.get("offset").asLong(),
                    node.get("timestamp").asLong(),
                    TimestampType.forName(node.get("timestampType").asText()),
                    node.get("serializedKeySize").asInt(),
                    node.get("serializedValueSize").asInt(),
                    node.get("key").asText(),
                    Base64.getDecoder().decode(node.get("value").asText()),
                    new RecordHeaders(),  // 实际需解析 headers
                    node.get("leaderEpoch").isNull() ? Optional.empty() :
                            Optional.of(node.get("leaderEpoch").asInt())
            );
        }
    }

    @Override
    public void commitOffset(String topic, int partition, long offset) {
        try {
            TopicPartition topicPartition = new TopicPartition(topic, partition);
            consumer.commitSync(Collections.singletonMap(topicPartition, new OffsetAndMetadata(offset)));
        } catch (Exception e) {
            throw new ConsumerException("提交偏移量失败", e);
        }
    }

    @Override
    public void pause() {
        consumer.pause(consumer.assignment());
        log.info("暂停所有主题的消费");
    }

    @Override
    public void resume() {
        consumer.resume(consumer.assignment());
        log.info("恢复所有主题的消费");
    }

    @Override
    public void pause(String topic) {
        Set<TopicPartition> partitions = new HashSet<>();
        for (TopicPartition partition : consumer.assignment()) {
            if (partition.topic().equals(topic)) {
                partitions.add(partition);
            }
        }
        if (!partitions.isEmpty()) {
            consumer.pause(partitions);
            log.info("暂停消费主题: {}", topic);
        }
    }

    @Override
    public void resume(String topic) {
        Set<TopicPartition> partitions = new HashSet<>();
        for (TopicPartition partition : consumer.assignment()) {
            if (partition.topic().equals(topic)) {
                partitions.add(partition);
            }
        }
        if (!partitions.isEmpty()) {
            consumer.resume(partitions);
            log.info("恢复消费主题: {}", topic);
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (consumer != null) {
            consumer.close();
        }
        if (delayTemplate != null)
            if (delayTemplate.getConnectionFactory() instanceof DisposableBean disposableBean) {
                try {
                    disposableBean.destroy();
                } catch (Exception e) {
                    log.error("delayTemplate destroy failed");
                }
            }
        if (delayListener != null)
            delayListener = null;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }
} 