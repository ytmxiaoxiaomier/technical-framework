package com.example.mq.core.exception;

/**
 * 连接异常
 */
public class ConnectionException extends MessageQueueException {
    private static final long serialVersionUID = 1L;

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConnectionException(Throwable cause) {
        super(cause);
    }
} 