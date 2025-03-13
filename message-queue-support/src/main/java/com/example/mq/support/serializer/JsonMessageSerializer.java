package com.example.mq.support.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON消息序列化器
 */
@Slf4j
public class JsonMessageSerializer implements MessageSerializer {

    private final ObjectMapper objectMapper;

    public JsonMessageSerializer() {
        this.objectMapper = new ObjectMapper();
        // 注册Java 8时间模块
        this.objectMapper.registerModule(new JavaTimeModule());
        // 配置序列化特性
        this.objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Override
    public byte[] serialize(Object obj) {
        if (obj == null) {
            return new byte[0];
        }

        try {
            return objectMapper.writeValueAsBytes(obj);
        } catch (Exception e) {
            log.error("序列化对象失败: {}", obj.getClass().getName(), e);
            throw new SerializationException("序列化对象失败", e);
        }
    }

    @Override
    public Object deserialize(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(data, Object.class);
        } catch (Exception e) {
            log.error("反序列化对象失败", e);
            throw new SerializationException("反序列化对象失败", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        if (data == null || data.length == 0) {
            return null;
        }

        try {
            return objectMapper.readValue(data, clazz);
        } catch (Exception e) {
            log.error("反序列化对象失败: {}", clazz.getName(), e);
            throw new SerializationException("反序列化对象失败", e);
        }
    }

    /**
     * 序列化异常
     */
    public static class SerializationException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SerializationException(String message) {
            super(message);
        }

        public SerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
} 