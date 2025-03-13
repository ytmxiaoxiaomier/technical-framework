package com.example.mq.core.config;

import com.example.mq.core.enums.MessageQueueType;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息队列配置属性基础实现类
 */
@Data
public class BaseMessageQueueProperties implements MessageQueueProperties {
    /**
     * 消息队列类型
     */
    private MessageQueueType type;

    /**
     * 集群名称
     */
    private String cluster;

    /**
     * 服务器地址
     */
    private String server;

    /**
     * 额外属性
     */
    private Map<String, Object> properties = new HashMap<>();

    /**
     * 克隆当前配置
     *
     * @return 克隆后的配置对象
     */
    public BaseMessageQueueProperties copy() {
        BaseMessageQueueProperties copy = new BaseMessageQueueProperties();
        copy.setType(this.type);
        copy.setCluster(this.cluster);
        copy.setServer(this.server);
        copy.setProperties(new HashMap<>(this.properties));
        return copy;
    }

    /**
     * 合并配置
     *
     * @param other 其他配置
     * @return 合并后的配置对象
     */
    public BaseMessageQueueProperties merge(MessageQueueProperties other) {
        if (other == null) {
            return this;
        }

        BaseMessageQueueProperties merged = this.copy();

        if (other.getType() != null) {
            merged.setType(other.getType());
        }

        if (other.getCluster() != null) {
            merged.setCluster(other.getCluster());
        }

        if (other.getServer() != null) {
            merged.setServer(other.getServer());
        }

        if (other.getProperties() != null) {
            merged.getProperties().putAll(other.getProperties());
        }

        return merged;
    }
} 