package com.example.mq.support.serializer;

/**
 * 消息序列化器接口
 */
public interface MessageSerializer {

    /**
     * 序列化对象
     *
     * @param obj 要序列化的对象
     * @return 序列化后的字节数组
     */
    byte[] serialize(Object obj);

    /**
     * 反序列化对象
     *
     * @param data 要反序列化的字节数组
     * @return 反序列化后的对象
     */
    Object deserialize(byte[] data);

    /**
     * 反序列化对象为指定类型
     *
     * @param data  要反序列化的字节数组
     * @param clazz 目标类型
     * @param <T>   目标类型
     * @return 反序列化后的对象
     */
    <T> T deserialize(byte[] data, Class<T> clazz);
} 