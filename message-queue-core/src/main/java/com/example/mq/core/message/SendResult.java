package com.example.mq.core.message;

/**
 * 消息发送结果接口
 */
public interface SendResult {
    /**
     * 获取消息ID
     *
     * @return 消息ID
     */
    String getMessageId();

    /**
     * 判断是否发送成功
     *
     * @return 是否成功
     */
    boolean isSuccess();

    /**
     * 获取异常信息（如果发送失败）
     *
     * @return 异常信息，成功则为null
     */
    Throwable getException();

    /**
     * 获取原始结果对象
     *
     * @return 各MQ实现的原始结果对象
     */
    Object getRawResult();
} 