package com.example.mq.kafka.container;

import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.kafka.consumer.KafkaConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Kafka消息监听容器
 */
@Slf4j
public class KafkaMessageListenerContainer extends MessageListenerContainer {

    private ExecutorService executorService;

    @Override
    protected void doStart() {
        if (!(consumer instanceof KafkaConsumer)) {
            throw new IllegalStateException("Consumer must be KafkaConsumer type");
        }

        KafkaConsumer kafkaConsumer = (KafkaConsumer) consumer;

        // 创建消费线程池
        executorService = Executors.newFixedThreadPool(getConcurrency());

        // 订阅主题
        kafkaConsumer.subscribe(getTopic(), getGroup());

        // 启动消费线程
        for (int i = 0; i < getConcurrency(); i++) {
            executorService.submit(() -> {
                try {
                    while (isRunning()) {
                        try {
                            // 消费消息
                            kafkaConsumer.poll(messageListener);
                        } catch (Exception e) {
                            log.error("消费消息失败", e);
                        }
                    }
                } finally {
                    kafkaConsumer.close();
                }
            });
        }

        log.info("启动Kafka消息监听容器: topic={}, group={}, concurrency={}", getTopic(), getGroup(), getConcurrency());
    }

    @Override
    protected void doStopInternal() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("停止Kafka消息监听容器: topic={}, group={}", getTopic(), getGroup());
    }

    @Override
    public void setupMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }
} 