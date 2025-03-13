package com.example.mq.core.exception;

/**
 * 消费者异常
 */
public class ConsumerException extends MessageQueueException {
    private static final long serialVersionUID = 1L;

    public ConsumerException(String message) {
        super(message);
    }

    public ConsumerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConsumerException(Throwable cause) {
        super(cause);
    }
} 