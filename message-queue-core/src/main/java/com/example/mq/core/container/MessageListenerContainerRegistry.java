package com.example.mq.core.container;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 消息监听容器注册表
 */
public class MessageListenerContainerRegistry implements DisposableBean, SmartLifecycle {

    private final Map<String, MessageListenerContainer> containers = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger();
    private int phase = Integer.MAX_VALUE;
    private volatile boolean running = false;

    /**
     * 注册监听容器
     */
    public void registerContainer(MessageListenerContainer container) {
        String id = generateContainerId(container);
        container.setListenerId(id);
        this.containers.put(id, container);
        if (this.running) {
            container.start();
        }
    }

    /**
     * 获取所有监听容器
     */
    public Collection<MessageListenerContainer> getContainers() {
        return Collections.unmodifiableCollection(this.containers.values());
    }

    /**
     * 获取指定ID的监听容器
     */
    public MessageListenerContainer getContainer(String listenerId) {
        return this.containers.get(listenerId);
    }

    /**
     * 生成容器ID
     */
    private String generateContainerId(MessageListenerContainer container) {
        String id = container.getListenerId();
        if (id == null) {
            id = "message-listener-" + counter.incrementAndGet();
        }
        return id;
    }

    @Override
    public void destroy() {
        stop();
    }

    @Override
    public void start() {
        this.containers.values().forEach(MessageListenerContainer::start);
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
        this.containers.values().forEach(MessageListenerContainer::stop);
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return this.phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }
} 