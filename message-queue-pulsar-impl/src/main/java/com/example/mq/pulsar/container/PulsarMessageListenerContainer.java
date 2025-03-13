package com.example.mq.pulsar.container;

import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.pulsar.consumer.PulsarConsumer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Pulsar消息监听容器
 */
@Slf4j
public class PulsarMessageListenerContainer extends MessageListenerContainer {

    private ExecutorService executorService;

    @Override
    public void setupMessageListener(MessageListener messageListener) {
        this.messageListener = messageListener;
    }

    @Override
    protected void doStart() {
        if (!(consumer instanceof PulsarConsumer)) {
            throw new IllegalStateException("Consumer must be PulsarConsumer type");
        }

        PulsarConsumer pulsarConsumer = (PulsarConsumer) consumer;

        // 创建消费线程池
        executorService = Executors.newFixedThreadPool(getConcurrency());

        // 订阅主题
        pulsarConsumer.subscribe(getTopic(), getGroup());

        // 注册消息监听器
        pulsarConsumer.registerListener(getTopic(), getGroup(), messageListener);

        log.info("启动Pulsar消息监听容器: topic={}, group={}, concurrency={}", getTopic(), getGroup(), getConcurrency());
    }

    @Override
    protected void doStopInternal() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                // 等待所有任务完成
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("停止Pulsar消息监听容器: topic={}, group={}", getTopic(), getGroup());
    }
} 