package com.example.mq.core.exception;

/**
 * 生产者异常
 */
public class ProducerException extends MessageQueueException {
    private static final long serialVersionUID = 1L;

    public ProducerException(String message) {
        super(message);
    }

    public ProducerException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProducerException(Throwable cause) {
        super(cause);
    }
} 