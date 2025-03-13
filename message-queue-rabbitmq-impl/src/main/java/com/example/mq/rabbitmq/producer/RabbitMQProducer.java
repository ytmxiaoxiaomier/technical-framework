package com.example.mq.rabbitmq.producer;

import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.exception.ConnectionException;
import com.example.mq.core.exception.ProducerException;
import com.example.mq.core.message.BaseSendResult;
import com.example.mq.core.message.Message;
import com.example.mq.core.message.SendResult;
import com.example.mq.core.producer.AbstractProducer;
import com.example.mq.rabbitmq.converter.RabbitMQMessageConverter;
import com.example.mq.support.serializer.MessageSerializer;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ生产者实现
 */
@Slf4j
public class RabbitMQProducer extends AbstractProducer {
    private final Connection connection;
    private final Channel channel;
    private final RabbitMQMessageConverter converter;
    private final MessageQueueProperties properties;
    private final Map<String, Object> exchangeArguments = new HashMap<>();
    private final Map<String, Map<String, Object>> declaredExchanges = new ConcurrentHashMap<>();

    /**
     * 默认交换机类型
     */
    private static final String DEFAULT_EXCHANGE_TYPE = "topic";

    /**
     * 延迟交换机类型
     */
    private static final String DELAYED_EXCHANGE_TYPE = "x-delayed-message";

    public RabbitMQProducer(MessageQueueProperties properties, MessageSerializer serializer) {
        this.properties = properties;
        this.converter = new RabbitMQMessageConverter(serializer);

        try {
            // 创建连接
            ConnectionFactory factory = createConnectionFactory();
            this.connection = factory.newConnection();

            // 创建通道
            this.channel = connection.createChannel();

            // 初始化延迟交换机参数
            exchangeArguments.put("x-delayed-type", DEFAULT_EXCHANGE_TYPE);
        } catch (Exception e) {
            throw new ConnectionException("创建RabbitMQ连接失败", e);
        }
    }

    /**
     * 创建RabbitMQ连接工厂
     */
    private ConnectionFactory createConnectionFactory() {
        ConnectionFactory factory = new ConnectionFactory();

        // 解析服务器地址，格式为：amqp://username:password@host:port/virtualHost
        String server = properties.getServer();
        if (StringUtils.hasText(server)) {
            if (server.startsWith("amqp://")) {
                try {
                    factory.setUri(server);
                } catch (Exception e) {
                    log.error("解析RabbitMQ服务器地址失败", e);
                    // 如果解析失败，使用默认配置
                    factory.setHost("localhost");
                    factory.setPort(5672);
                }
            } else {
                // 简单格式：host:port
                String[] parts = server.split(":");
                factory.setHost(parts[0]);
                if (parts.length > 1) {
                    factory.setPort(Integer.parseInt(parts[1]));
                }
            }
        } else {
            factory.setHost("localhost");
            factory.setPort(5672);
        }

        // 设置用户名和密码
        String username = getProperty("username", "guest");
        factory.setUsername(username);

        String password = getProperty("password", "guest");
        factory.setPassword(password);

        // 设置虚拟主机
        String virtualHost = getProperty("virtualHost", "/");
        factory.setVirtualHost(virtualHost);

        // 设置连接超时时间
        int connectionTimeout = getProperty("connectionTimeout", 60000);
        factory.setConnectionTimeout(connectionTimeout);

        // 设置心跳间隔
        int heartbeat = getProperty("heartbeat", 60);
        factory.setRequestedHeartbeat(heartbeat);

        // 设置自动恢复
        boolean automaticRecovery = getProperty("automaticRecovery", true);
        factory.setAutomaticRecoveryEnabled(automaticRecovery);

        // 设置网络恢复间隔
        long networkRecoveryInterval = getProperty("networkRecoveryInterval", 5000L);
        factory.setNetworkRecoveryInterval(networkRecoveryInterval);

        return factory;
    }

    /**
     * 获取RabbitMQ配置属性
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
            // 确保消息ID存在
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            // 确保主题存在
            if (!StringUtils.hasText(message.getTopic())) {
                throw new IllegalArgumentException("主题不能为空");
            }

            // 声明交换机
            String exchangeName = message.getTopic();
            boolean isDelayed = message.getDelayTime() > 0;
            declareExchangeIfNeeded(exchangeName, isDelayed);

            // 创建消息属性
            AMQP.BasicProperties properties = converter.createMessageProperties(message);

            // 获取消息体
            byte[] body = converter.getMessageBody(message);

            // 设置发送超时
            channel.confirmSelect();
            boolean confirmed = channel.waitForConfirms(timeout);

            // 发送消息
            String routingKey = getRoutingKey(message);
            channel.basicPublish(exchangeName, routingKey, properties, body);

            // 创建发送结果
            BaseSendResult result = new BaseSendResult();
            result.setMessageId(message.getMessageId());
            result.setSuccess(confirmed);

            if (!confirmed) {
                result.setException(new ProducerException("消息发送确认超时"));
            }

            return result;
        } catch (Exception e) {
            log.error("发送消息失败", e);
            return BaseSendResult.failure(message.getMessageId(), e);
        }
    }

    @Override
    public CompletableFuture<SendResult> asyncSend(Message message, long timeout) {
        CompletableFuture<SendResult> future = new CompletableFuture<>();

        // RabbitMQ客户端不直接支持异步发送，这里使用线程池异步执行
        CompletableFuture.runAsync(() -> {
            try {
                SendResult result = send(message, timeout);
                future.complete(result);
            } catch (Exception e) {
                future.complete(BaseSendResult.failure(message.getMessageId(), e));
            }
        });

        return future;
    }

    @Override
    public List<SendResult> batchSend(List<Message> messages, long timeout) {
        List<SendResult> results = new ArrayList<>(messages.size());

        // RabbitMQ客户端不直接支持批量发送，这里循环发送
        for (Message message : messages) {
            results.add(send(message, timeout));
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
        throw new UnsupportedOperationException("RabbitMQ异步批量发送消息未实现");
    }

    @Override
    public List<SendResult> sendDelayed(List<Message> messages, long delay, TimeUnit unit, long timeout) {
        return messages.stream()
                .map(message -> sendDelayed(message, delay, unit, timeout))
                .toList();
    }

    private SendResult sendDelayed(Message message, long delay, TimeUnit unit, long timeout) {
        try {
            // 确保消息ID存在
            if (message.getMessageId() == null) {
                message.setMessageId(UUID.randomUUID().toString());
            }

            // 确保主题存在
            if (!StringUtils.hasText(message.getTopic())) {
                throw new IllegalArgumentException("主题不能为空");
            }

            // 声明延迟交换机
            String exchangeName = message.getTopic();
            declareExchangeIfNeeded(exchangeName, true);

            // 保存原始延迟时间
            long oldDelay = message.getDelayTime();

            try {
                // 设置新的延迟时间
                message.setDelayTime(unit.toMillis(delay));

                // 创建消息属性
                AMQP.BasicProperties properties = converter.createMessageProperties(message);

                // 获取消息体
                byte[] body = converter.getMessageBody(message);

                // 设置发送超时
                channel.confirmSelect();
                boolean confirmed = channel.waitForConfirms(timeout);

                // 发送消息
                String routingKey = getRoutingKey(message);
                channel.basicPublish(exchangeName, routingKey, properties, body);

                // 创建发送结果
                BaseSendResult result = new BaseSendResult();
                result.setMessageId(message.getMessageId());
                result.setSuccess(confirmed);

                if (!confirmed) {
                    result.setException(new ProducerException("消息发送确认超时"));
                }

                return result;
            } finally {
                // 恢复原始延迟时间
                message.setDelayTime(oldDelay);
            }
        } catch (Exception e) {
            log.error("发送延迟消息失败", e);
            return BaseSendResult.failure(message.getMessageId(), e);
        }
    }

    @Override
    public List<SendResult> sendTransaction(List<Message> messages, long timeout) {
        // RabbitMQ不支持事务消息，可以使用发布确认机制代替
        // 这里简化处理，直接发送
        throw new UnsupportedOperationException("RabbitMQ事务消息操作未实现");
    }

    @Override
    public void close() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception e) {
            log.error("关闭RabbitMQ通道失败", e);
        }

        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception e) {
            log.error("关闭RabbitMQ连接失败", e);
        }
    }

    /**
     * 获取路由键
     */
    private String getRoutingKey(Message message) {
        // 如果设置了自定义路由键，优先使用
        String routingKey = message.getHeaders() != null ? message.getHeaders().get("routingKey") : null;
        if (StringUtils.hasText(routingKey)) {
            return routingKey;
        }

        // 如果没有路由键，使用消息键
        if (StringUtils.hasText(message.getKey())) {
            return message.getKey();
        }

        // 默认使用主题作为路由键
        return message.getTopic();
    }

    /**
     * 检查并声明交换机
     *
     * @param exchangeName 交换机名称
     * @param isDelayed    是否是延迟交换机
     * @throws IOException 声明交换机异常
     */
    private void declareExchangeIfNeeded(String exchangeName, boolean isDelayed) throws IOException {
        // 检查交换机是否已声明
        String exchangeKey = exchangeName + ":" + isDelayed;
        if (declaredExchanges.containsKey(exchangeKey)) {
            return;
        }

        // 声明交换机
        String type = isDelayed ? DELAYED_EXCHANGE_TYPE : DEFAULT_EXCHANGE_TYPE;
        Map<String, Object> arguments = isDelayed ? exchangeArguments : null;

        channel.exchangeDeclare(exchangeName, type, true, false, arguments);

        // 添加到已声明交换机集合
        declaredExchanges.put(exchangeKey, arguments != null ? new HashMap<>(arguments) : new HashMap<>());

        log.info("声明RabbitMQ交换机: {}, 类型: {}", exchangeName, type);
    }
}