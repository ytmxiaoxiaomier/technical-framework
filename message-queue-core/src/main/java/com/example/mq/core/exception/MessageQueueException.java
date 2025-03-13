package com.example.mq.core.exception;

/**
 * 消息队列异常基类
 */
public class MessageQueueException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public MessageQueueException(String message) {
        super(message);
    }

    public MessageQueueException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageQueueException(Throwable cause) {
        super(cause);
    }
} 