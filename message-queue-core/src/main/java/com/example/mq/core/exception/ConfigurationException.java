package com.example.mq.core.exception;

/**
 * 配置异常
 */
public class ConfigurationException extends MessageQueueException {
    private static final long serialVersionUID = 1L;

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(Throwable cause) {
        super(cause);
    }
} 