# 消息队列示例

这个模块提供了消息队列组件的使用示例，包括生产者和消费者的实现。

## 环境要求

在运行示例之前，请确保以下服务已经启动：

1. Kafka
   ```bash
   # 启动 Zookeeper
   bin/zookeeper-server-start.sh config/zookeeper.properties
   # 启动 Kafka
   bin/kafka-server-start.sh config/server.properties
   ```

2. RocketMQ
   ```bash
   # 启动 NameServer
   nohup sh bin/mqnamesrv &
   # 启动 Broker
   nohup sh bin/mqbroker -n localhost:9876 &
   ```

3. RabbitMQ
   ```bash
   # 启动 RabbitMQ
   rabbitmq-server
   ```

4. Pulsar
   ```bash
   # 启动 Pulsar
   bin/pulsar standalone
   ```

## 配置说明

配置文件位于 `src/main/resources/application.yml`，包含了各个消息队列的连接信息。根据实际环境修改相应的配置。

## 运行示例

1. 编译项目
   ```bash
   mvn clean package
   ```

2. 运行应用
   ```bash
   java -jar target/message-queue-examples-1.0-SNAPSHOT.jar
   ```

## 测试接口

1. 发送 Kafka 消息
   ```bash
   curl -X POST http://localhost:8080/test/kafka/send \
        -H "Content-Type: text/plain" \
        -d "Hello Kafka"
   ```

2. 发送 RocketMQ 消息
   ```bash
   curl -X POST http://localhost:8080/test/rocketmq/send \
        -H "Content-Type: text/plain" \
        -d "Hello RocketMQ"
   ```

3. 发送 RabbitMQ 消息
   ```bash
   curl -X POST "http://localhost:8080/test/rabbitmq/send?routingKey=test" \
        -H "Content-Type: text/plain" \
        -d "Hello RabbitMQ"
   ```

4. 发送 Pulsar 消息
   ```bash
   curl -X POST "http://localhost:8080/test/pulsar/send?delay=5&unit=SECONDS" \
        -H "Content-Type: text/plain" \
        -d "Hello Pulsar"
   ```

## 查看结果

运行示例后，可以在控制台查看消息的发送和接收日志。每个消费者都会打印接收到的消息内容。 