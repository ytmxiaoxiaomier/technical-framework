package com.example.mq.core.config;

import com.example.mq.core.enums.MessageQueueType;

import java.util.Map;

/**
 * 消息队列配置属性接口
 */
public interface MessageQueueProperties {
    /**
     * 获取消息队列类型
     *
     * @return 消息队列类型
     */
    MessageQueueType getType();

    /**
     * 获取集群名称
     *
     * @return 集群名称
     */
    String getCluster();

    /**
     * 获取服务器地址
     *
     * @return 服务器地址
     */
    String getServer();

    /**
     * 获取额外属性
     *
     * @return 额外属性键值对
     */
    Map<String, Object> getProperties();
} 