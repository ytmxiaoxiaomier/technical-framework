# 统一消息总线框架

## 项目介绍

统一消息总线框架是一个基于Spring Boot的消息总线抽象层框架，旨在提供一套统一的API来操作不同的消息队列实现（如Kafka、RocketMQ、RabbitMQ、Pulsar等）。该框架的主要特点包括：

- 统一的API接口，降低学习成本
- 支持多种消息队列的无缝切换
- 支持同步/异步发送消息
- 支持顺序消息
- 支持事务消息
- 支持延迟消息
- 支持批量发送消息
- 基于注解的简单配置
- 支持SpEL表达式动态解析
- 支持消息的序列化和反序列化
- 支持消息转换器自定义
- 支持多集群配置
- 支持消费者的暂停/恢复
- 完善的异常处理机制

## 架构设计

### 类图

详细的类图设计请参考 [项目类图](项目类图.md)，包含：

- 核心接口设计
- 核心类实现
- 工厂和配置类
- 注解和容器类
- Spring Starter实现
- 异常体系
- 各消息队列实现

### 流程图

详细的流程图设计请参考 [项目流程图](项目流程图.md)，包含：

- Spring Boot自动配置流程
- 消息发送流程
- 消息消费流程
- 事务消息处理流程
- 延迟消息处理流程

## 快速开始

### Maven依赖

```xml

<dependencies>
    <!-- 核心依赖 -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>message-queue-spring-starter</artifactId>
        <version>${latest.version}</version>
    </dependency>

    <!-- 选择需要的实现 -->
    <dependency>
        <groupId>com.example</groupId>
        <artifactId>message-queue-kafka-impl</artifactId>
        <version>${latest.version}</version>
    </dependency>
</dependencies>
```

### 基础配置

```yaml
message-queue:
  enabled-types:
    - KAFKA
    - ROCKETMQ
    - RABBITMQ
    - PULSAR
  default-cluster: primary
  configurations:
    KAFKA:
      primary:
        cluster: primary
        type: KAFKA
        server: 10.50.2.173:19091
        properties:
          group.id: test-group
          auto.offset.reset: earliest
    ROCKETMQ:
      primary:
        cluster: primary
        type: ROCKETMQ
        server: 10.50.2.155:9876
        properties:
          producerGroup: test-group
          sendMsgTimeout: 3000
          brokerName: single-rocket-broker
```

### 生产者使用示例

#### 1. 简单消息发送

```java

@Service
public class OrderService {
    @Producer(type = MessageQueueType.KAFKA,
            topic = "order-topic",
            cluster = "default")
    public MessageResult sendOrder(Order order) {
        return MessageResult.of(Message.builder()
                .payload(order)
                .key(order.getId())
                .build());
    }
}
```

#### 2. 异步消息发送

```java

@Service
public class OrderService {
    @Producer(type = MessageQueueType.KAFKA,
            topic = "order-topic",
            async = true)
    public MessageResult sendOrderAsync(Order order) {
        return MessageResult.of(Message.builder()
                .payload(order)
                .build());
    }
}
```

#### 3. 事务消息发送

```java

@Service
public class OrderService {
    @Producer(type = MessageQueueType.ROCKET_MQ,
            topic = "order-topic",
            transactionId = "TX_ORDER")
    @Transactional
    public MessageResult createOrderWithTransaction(Order order) {
        // 本地事务处理
        orderRepository.save(order);
        // 发送事务消息
        return MessageResult.of(Message.builder()
                .payload(order)
                .build());
    }
}
```

#### 4. 延迟消息发送

```java

@Service
public class OrderService {
    @Producer(type = MessageQueueType.ROCKET_MQ,
            topic = "order-delay-topic",
            delayTime = 30,
            delayUnit = TimeUnit.MINUTES)
    public MessageResult sendDelayedOrder(Order order) {
        return MessageResult.of(Message.builder()
                .payload(order)
                .build());
    }
}
```

### 消费者使用示例

#### 1. 简单消息消费

```java

@Service
@MessageQueueListener(
        type = MessageQueueType.KAFKA,
        topic = "order-topic",
        group = "order-group"
)
public class OrderConsumer implements MessageListener {
    @Override
    public ConsumeResult onMessage(Message message, ConsumeContext context) {
        Order order = message.getPayload(Order.class);
        try {
            // 处理订单
            orderService.processOrder(order);
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            return ConsumeResult.RETRY;
        }
    }
}
```

#### 2. 方法级消息消费

```java

@Service
public class OrderService {
    @MessageQueueListener(
            type = MessageQueueType.KAFKA,
            topic = "order-topic",
            group = "order-group"
    )
    public void processOrder(Message message) {
        Order order = message.getPayload(Order.class);
        // 处理订单
    }
}
```

#### 3. 顺序消息消费

```java

@Service
@MessageQueueListener(
        type = MessageQueueType.ROCKET_MQ,
        topic = "order-topic",
        group = "order-group",
        sequence = true
)
public class OrderSequenceConsumer implements MessageListener {
    @Override
    public ConsumeResult onMessage(Message message, ConsumeContext context) {
        // 处理顺序消息
        return ConsumeResult.SUCCESS;
    }
}
```

#### 4. 事务消息消费

```java

@Service
@MessageQueueListener(
        type = MessageQueueType.ROCKET_MQ,
        topic = "order-topic",
        group = "order-group",
        transaction = true
)
public class OrderTransactionConsumer implements MessageListener {
    @Override
    public ConsumeResult onMessage(Message message, ConsumeContext context) {
        // 处理事务消息
        return ConsumeResult.SUCCESS;
    }
}
```

## 扩展指南

### 实现新的消息队列类型

#### 1. 创建消息转换器

```java
public class NewMQMessageConverter implements MessageConverter<NewMQMessage> {
    @Override
    public NewMQMessage toMQMessage(Message message) {
        // 将统一消息转换为特定MQ的消息
    }

    @Override
    public Message fromMQMessage(NewMQMessage mqMessage) {
        // 将特定MQ的消息转换为统一消息
    }
}
```

#### 2. 实现生产者

```java
public class NewMQProducer extends AbstractProducer {
    private final NewMQClient client;
    private final NewMQMessageConverter converter;

    @Override
    public SendResult send(Message message) {
        try {
            NewMQMessage mqMessage = converter.toMQMessage(message);
            NewMQSendResult result = client.send(mqMessage);
            return BaseSendResult.success(result.getMsgId(), result);
        } catch (Exception e) {
            return BaseSendResult.failure(null, e);
        }
    }

    // 实现其他方法...
}
```

#### 3. 实现消费者

```java
public class NewMQConsumer implements IConsumer {
    private final NewMQClient client;
    private final NewMQMessageConverter converter;

    @Override
    public void subscribe(String topic, String consumerGroup) {
        client.subscribe(topic, consumerGroup, message -> {
            Message unifiedMessage = converter.fromMQMessage(message);
            // 处理消息
        });
    }

    // 实现其他方法...
}
```

#### 4. 实现工厂类

```java

@Component
public class NewMQProducerFactory implements ProducerFactory {
    @Override
    public boolean supports(MessageQueueType type) {
        return type == MessageQueueType.NEW_MQ;
    }

    @Override
    public IProducer createProducer(MessageQueueProperties properties) {
        NewMQClient client = createClient(properties);
        return new NewMQProducer(client, new NewMQMessageConverter());
    }
}

@Component
public class NewMQConsumerFactory implements ConsumerFactory {
    @Override
    public boolean supports(MessageQueueType type) {
        return type == MessageQueueType.NEW_MQ;
    }

    @Override
    public IConsumer createConsumer(MessageQueueProperties properties) {
        NewMQClient client = createClient(properties);
        return new NewMQConsumer(client, new NewMQMessageConverter());
    }
}
```

#### 5. 添加消息监听容器支持

```java

@Slf4j
public class NewMQMessageListenerContainer extends MessageListenerContainer {
  @Override
  protected void doStart() {
      // 启动消费者
      // 启动逻辑...
  }
  
  // 实现其他方法...
  
}

```

#### 6. 添加配置支持

```java

@Configuration
@ConditionalOnProperty(prefix = "message.queue", name = "enabled-types", havingValue = "NEW_MQ")
public class NewMQConfiguration {
    @Bean
    public NewMQProducerFactory newMQProducerFactory() {
        return new NewMQProducerFactory();
    }

    @Bean
    public NewMQConsumerFactory newMQConsumerFactory() {
        return new NewMQConsumerFactory();
    }
}
```

#### 7. Spring自动装配 

创建resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports文件，添加以下内容：
```text
com.example.mq.xxx.config.NewMQConfiguration
```

## 注意事项

#### 1. 消息发送

- 确保消息payload可序列化
- 事务消息需要配合@Transactional使用
- 延迟消息的延迟时间受具体MQ实现限制

#### 2. 消息消费

- 消费者组名称需要全局唯一
- 顺序消息消费要注意性能影响
- 建议在消费者中做好幂等处理

#### 3. 配置建议

- 建议为不同环境配置不同的集群
- 关键业务建议配置死信队列
- 根据业务场景合理设置重试次数

## 常见问题

#### 1. 消息发送失败

- 检查网络连接
- 检查集群配置
- 检查权限设置
- 查看具体异常信息

#### 2. 消息重复消费

- 实现业务幂等性
- 检查消费者组配置
- 检查消费者实例数量

#### 3. 顺序消息乱序

- 确认是否正确配置sequence=true
- 检查分区/队列数量配置
- 确认消息key设置正确

#### 4. 性能问题

- 调整批量处理参数
- 优化序列化方式
- 调整消费者并发数
- 考虑使用异步发送

## 版本历史

- 1.0.0
    - 初始版本发布
    - 支持Kafka、RocketMQ、RabbitMQ、Pulsar
    - 基础功能实现

- 1.1.0
    - 添加延迟消息支持
    - 优化事务消息处理
    - 添加消息轨迹功能

## 开发计划

- [ ] 支持更多消息队列实现
- [ ] 添加消息过滤功能
- [ ] 优化性能监控
- [ ] 添加管理控制台
- [ ] 支持消息回溯 
- [ ] 支持指标监控采集 