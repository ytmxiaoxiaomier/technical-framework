package com.example.mq.rocketmq.consumer;

import com.example.mq.core.annotation.ConsumerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.rocketmq.converter.RocketMQMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.remoting.protocol.heartbeat.MessageModel;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RocketMQ消费者实现
 */
@Slf4j
public class RocketMQConsumer implements IConsumer {
    private final MessageQueueProperties properties;
    private ConsumerAnnotationProperties consumerAnnotationProperties;
    private final RocketMQMessageConverter converter;
    private final Map<String, DefaultMQPushConsumer> consumers = new ConcurrentHashMap<>();
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    public RocketMQConsumer(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new RocketMQMessageConverter(serializer);
        if (properties.getProperties().containsKey(IConsumer.class.getName())
                && properties.getProperties().get(IConsumer.class.getName()) instanceof ConsumerAnnotationProperties annoProperties)
            this.consumerAnnotationProperties = annoProperties;
    }

    @Override
    public void subscribe(String topic, String consumerGroup) {
        if (!StringUtils.hasText(topic) || !StringUtils.hasText(consumerGroup)) {
            throw new IllegalArgumentException("主题和消费者组不能为空");
        }

        String key = buildConsumerKey(topic, consumerGroup);

        // 如果已经订阅，直接返回
        if (consumers.containsKey(key)) {
            log.warn("RocketMQ消费者已经订阅主题: {}, 消费者组: {}", topic, consumerGroup);
            return;
        }

        // 创建消费者
        DefaultMQPushConsumer consumer = createConsumer(consumerGroup);

        try {
            // 订阅主题
            consumer.subscribe(topic, "*");

            // 注册消息监听器
            consumer.registerMessageListener(new RocketMQMessageListenerAdapter(key));

            // 启动消费者
            consumer.start();

            // 保存消费者
            consumers.put(key, consumer);

            log.info("RocketMQ消费者订阅主题成功: {}, 消费者组: {}", topic, consumerGroup);
        } catch (MQClientException e) {
            throw new ConnectionException("RocketMQ消费者订阅主题失败: " + topic, e);
        }
    }

    @Override
    public void unsubscribe(String topic) {
        // 查找所有该主题的消费者
        consumers.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (key.startsWith(topic + ":")) {
                DefaultMQPushConsumer consumer = entry.getValue();
                consumer.shutdown();
                listeners.remove(key);
                log.info("RocketMQ消费者取消订阅主题: {}", topic);
                return true;
            }
            return false;
        });
    }

    @Override
    public void pause() {
        // RocketMQ不支持暂停消费，这里简化处理，关闭所有消费者
        for (DefaultMQPushConsumer consumer : consumers.values()) {
            consumer.suspend();
        }
        log.info("RocketMQ消费者已暂停");
    }

    @Override
    public void resume() {
        // 恢复所有消费者
        for (DefaultMQPushConsumer consumer : consumers.values()) {
            consumer.resume();
        }
        log.info("RocketMQ消费者已恢复");
    }

    @Override
    public void close() {
        // 关闭所有消费者
        for (DefaultMQPushConsumer consumer : consumers.values()) {
            consumer.shutdown();
        }
        consumers.clear();
        listeners.clear();
        log.info("RocketMQ消费者已关闭");
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
     * 创建RocketMQ消费者
     */
    private DefaultMQPushConsumer createConsumer(String consumerGroup) {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(consumerGroup);

        // 设置NameServer地址
        consumer.setNamesrvAddr(properties.getServer());

        // 设置消费位点，默认从上次消费位点开始
        String consumeFromWhere = getProperty("consumeFromWhere", "CONSUME_FROM_LAST_OFFSET");
        consumer.setConsumeFromWhere(ConsumeFromWhere.valueOf(consumeFromWhere));

        // 设置消费模式，默认为集群模式
        String messageModel = getProperty("messageModel", "CLUSTERING");
        if ("BROADCASTING".equalsIgnoreCase(messageModel)) {
            consumer.setMessageModel(MessageModel.BROADCASTING);
        } else {
            consumer.setMessageModel(MessageModel.CLUSTERING);
        }

        // 设置消费线程数，默认为20
        int consumeThreadMin = getProperty("consumeThreadMin", 20);
        consumer.setConsumeThreadMin(consumeThreadMin);

        int consumeThreadMax = getProperty("consumeThreadMax", 64);
        consumer.setConsumeThreadMax(consumeThreadMax);

        // 设置批量消费大小，默认为1
        int consumeMessageBatchMaxSize = getProperty("consumeMessageBatchMaxSize", 1);
        consumer.setConsumeMessageBatchMaxSize(consumeMessageBatchMaxSize);

        // 设置最大重试次数，默认为16次
        int maxReconsumeTimes = getProperty("maxReconsumeTimes", 16);
        consumer.setMaxReconsumeTimes(maxReconsumeTimes);

        //
        return consumer;
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

    /**
     * 构建消费者键
     */
    private String buildConsumerKey(String topic, String group) {
        return topic + "::" + group;
    }

    /**
     * RocketMQ消息监听器适配器
     */
    private class RocketMQMessageListenerAdapter implements MessageListenerConcurrently {
        private final String key;

        public RocketMQMessageListenerAdapter(String key) {
            this.key = key;
        }

        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgExtList, ConsumeConcurrentlyContext context) {
            // 获取监听器
            MessageListener listener = listeners.get(key);
            if (listener == null) {
                log.error("未找到消息监听器: {}", key);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }

            // 解析主题和消费者组
            String[] parts = key.split(":");
            String topic = parts[0];
            String group = parts[1];

            // 处理消息
            for (MessageExt msgExt : msgExtList) {
                try {
                    // 转换为统一消息对象
                    com.example.mq.core.message.Message message = converter.fromMQMessage(msgExt);

                    // 创建消费上下文
                    RocketMQConsumeContext consumeContext = new RocketMQConsumeContext(topic, group, msgExt);

                    // 调用监听器处理消息
                    MessageListener.ConsumeResult result = listener.onMessage(message, consumeContext);

                    // 处理消费结果
                    if (result != MessageListener.ConsumeResult.SUCCESS) {
                        log.warn("消息消费失败: {}, 主题: {}, 消费者组: {}", msgExt.getMsgId(), topic, group);
                        return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                    }
                } catch (Exception e) {
                    log.error("消息消费异常: {}, 主题: {}, 消费者组: {}", msgExt.getMsgId(), topic, group, e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }

            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }

    /**
     * RocketMQ消费上下文
     */
    private record RocketMQConsumeContext(String topic,
                                          String consumerGroup,
                                          MessageExt rawMessage) implements MessageListener.ConsumeContext {

        @Override
        public Object getRawMessage() {
            return rawMessage;
        }

        @Override
        public void acknowledge() {
            // RocketMQ自动确认，无需手动操作
        }

        @Override
        public void reject(boolean requeue) {
            // RocketMQ不支持手动拒绝消息，这里简化处理
            log.warn("RocketMQ不支持手动拒绝消息");
        }
    }
} 