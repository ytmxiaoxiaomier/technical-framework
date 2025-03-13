package com.example.mq.core.message;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 消息发送结果基础实现类
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor
public class BaseSendResult implements SendResult {
    /**
     * 消息ID
     */
    private String messageId;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 异常信息
     */
    private Throwable exception;

    /**
     * 原始结果对象
     */
    private Object rawResult;

    public BaseSendResult(boolean success, String messageId, Object rawResult) {
        this.success = success;
        this.messageId = messageId;
        this.rawResult = rawResult;
    }

    public BaseSendResult(boolean success, String messageId, Throwable exception) {
        this.success = success;
        this.messageId = messageId;
        this.exception = exception;
    }

    /**
     * 创建成功结果
     *
     * @param messageId 消息ID
     * @param rawResult 原始结果
     * @return 发送结果
     */
    public static BaseSendResult success(String messageId, Object rawResult) {
        return new BaseSendResult(true, messageId, rawResult);
    }

    /**
     * 创建失败结果
     *
     * @param messageId 消息ID
     * @param exception 异常
     * @return 发送结果
     */
    public static BaseSendResult failure(String messageId, Throwable exception) {
        return new BaseSendResult(false, messageId, exception);
    }

    /**
     * 创建失败结果
     *
     * @param messageId 消息ID
     * @param exception 异常
     * @param rawResult 原始结果
     * @return 发送结果
     */
    public static BaseSendResult failure(String messageId, Throwable exception, Object rawResult) {
        return new BaseSendResult()
                .setMessageId(messageId)
                .setSuccess(false)
                .setException(exception)
                .setRawResult(rawResult);
    }
} 