package com.example.mq.core.config;

import com.example.mq.core.enums.MessageQueueType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.*;

/**
 * 消息队列配置主类
 */
@ConfigurationProperties(prefix = "message-queue")
@Data
public class MessageQueueConfig {
    /**
     * 启用的消息队列类型列表
     */
    private List<MessageQueueType> enabledTypes = new ArrayList<>();

    /**
     * 默认集群名称
     */
    private String defaultCluster = "default";

    /**
     * 各类型消息队列的配置映射表
     * 第一级Key为消息队列类型
     * 第二级Key为集群名称
     * 值为具体配置
     */
    private Map<MessageQueueType, Map<String, BaseMessageQueueProperties>> configurations = new EnumMap<>(MessageQueueType.class);

    /**
     * 根据类型和集群名称获取消息队列配置
     *
     * @param type    消息队列类型
     * @param cluster 集群名称，如果为null或空字符串，则使用默认集群
     * @return 消息队列配置，如果未找到则返回null
     */
    public MessageQueueProperties getProperties(MessageQueueType type, String cluster) {
        if (type == null) {
            return null;
        }

        // 确定集群名称
        String clusterName = (cluster == null || cluster.isEmpty()) ? defaultCluster : cluster;

        // 获取指定类型的所有集群配置
        Map<String, BaseMessageQueueProperties> typeConfigs = configurations.get(type);
        if (typeConfigs == null || typeConfigs.isEmpty()) {
            return null;
        }

        // 返回指定集群的配置
        return typeConfigs.get(clusterName);
    }

    /**
     * 添加或更新指定类型和集群的配置
     *
     * @param type       消息队列类型
     * @param cluster    集群名称
     * @param properties 配置属性
     */
    public void addProperties(MessageQueueType type, String cluster, BaseMessageQueueProperties properties) {
        if (type == null || cluster == null || properties == null) {
            return;
        }

        // 确保类型配置存在
        configurations.computeIfAbsent(type, k -> new HashMap<>());

        // 更新配置
        configurations.get(type).put(cluster, properties);

        // 确保类型在启用列表中
        if (!enabledTypes.contains(type)) {
            enabledTypes.add(type);
        }
    }

    /**
     * 获取指定类型的所有集群配置
     *
     * @param type 消息队列类型
     * @return 集群配置映射表，如果未找到则返回空映射
     */
    public Map<String, MessageQueueProperties> getPropertiesByType(MessageQueueType type) {
        if (type == null) {
            return Collections.emptyMap();
        }

        Map<String, BaseMessageQueueProperties> typeConfigs = configurations.get(type);
        if (typeConfigs == null) {
            return Collections.emptyMap();
        }

        // 转换为接口类型返回
        Map<String, MessageQueueProperties> result = new HashMap<>();
        typeConfigs.forEach((k, v) -> result.put(k, v));
        return result;
    }

    /**
     * 获取所有启用类型的默认集群配置
     *
     * @return 类型到配置的映射表
     */
    public Map<MessageQueueType, MessageQueueProperties> getDefaultClusterProperties() {
        Map<MessageQueueType, MessageQueueProperties> result = new EnumMap<>(MessageQueueType.class);

        for (MessageQueueType type : enabledTypes) {
            MessageQueueProperties props = getProperties(type, defaultCluster);
            if (props != null) {
                result.put(type, props);
            }
        }

        return result;
    }
} 