package com.example.mq.core.container;

import com.example.mq.core.annotation.ConsumerAnnotationProperties;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 消息监听容器抽象基类
 */
@Slf4j
@Getter
@Setter
public abstract class MessageListenerContainer implements SmartLifecycle {
    protected final Object lifecycleMonitor = new Object();
    protected final AtomicBoolean running = new AtomicBoolean();

    protected String listenerId;
    protected ConsumerAnnotationProperties consumerAnnotationProperties;
    protected MessageListener messageListener;
    protected IConsumer consumer;
    protected MessageQueueProperties properties;
    protected boolean autoStartup = true;
    protected int phase = Integer.MAX_VALUE;

    @Override
    public boolean isAutoStartup() {
        return this.autoStartup;
    }

    @Override
    public int getPhase() {
        return this.phase;
    }

    @Override
    public boolean isRunning() {
        return this.running.get();
    }

    @Override
    public void start() {
        synchronized (this.lifecycleMonitor) {
            if (!this.running.get()) {
                doStart();
                this.running.set(true);
            }
        }
    }

    @Override
    public void stop() {
        synchronized (this.lifecycleMonitor) {
            if (this.running.get()) {
                doStop();
                this.running.set(false);
            }
        }
    }

    @Override
    public void stop(Runnable callback) {
        synchronized (this.lifecycleMonitor) {
            stop();
            callback.run();
        }
    }

    /**
     * 停止容器
     */
    protected void doStop() {
        try {
            // 先执行子类特定的停止逻辑
            doStopInternal();

            // 最后统一关闭消费者
            if (consumer != null) {
                try {
                    consumer.close();
                    log.info("消费者已关闭: type={}, cluster={}, server={}, topic={}, group={}", properties.getType(), properties.getCluster(), properties.getServer(), getTopic(), getGroup());
                } catch (Exception e) {
                    log.error("关闭消费者失败: type={}, cluster={}, server={}, topic={}, group={}", properties.getType(), properties.getCluster(), properties.getServer(), getTopic(), getGroup(), e);
                }
            }
        } catch (Exception e) {
            log.error("停止容器失败: listenerId={}", listenerId, e);
        }
    }

    /**
     * 子类特定的停止逻辑
     */
    protected void doStopInternal() {
        // 默认空实现，子类可以覆盖
    }

    /**
     * 启动容器
     */
    protected abstract void doStart();

    /**
     * 初始化容器
     */
    public abstract void setupMessageListener(MessageListener messageListener);

    /**
     * 获取主题
     *
     * @return
     */
    public String getTopic() {
        return consumerAnnotationProperties.getTopic();
    }

    public String getGroup() {
        return consumerAnnotationProperties.getGroup();
    }

    public int getConcurrency() {
        return consumerAnnotationProperties.getConcurrency();
    }
} 