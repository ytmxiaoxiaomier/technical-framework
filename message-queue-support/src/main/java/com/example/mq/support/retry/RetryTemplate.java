package com.example.mq.support.retry;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 重试模板类
 * 用于执行可重试操作
 */
@Slf4j
public class RetryTemplate {
    /**
     * 最大重试次数
     */
    private final int maxRetries;

    /**
     * 重试间隔（毫秒）
     */
    private final long retryInterval;

    /**
     * 是否使用指数退避算法
     */
    private final boolean exponentialBackoff;

    /**
     * 创建重试模板
     *
     * @param maxRetries         最大重试次数
     * @param retryInterval      重试间隔（毫秒）
     * @param exponentialBackoff 是否使用指数退避算法
     */
    public RetryTemplate(int maxRetries, long retryInterval, boolean exponentialBackoff) {
        this.maxRetries = maxRetries;
        this.retryInterval = retryInterval;
        this.exponentialBackoff = exponentialBackoff;
    }

    /**
     * 创建重试模板（默认不使用指数退避）
     *
     * @param maxRetries    最大重试次数
     * @param retryInterval 重试间隔（毫秒）
     */
    public RetryTemplate(int maxRetries, long retryInterval) {
        this(maxRetries, retryInterval, false);
    }

    /**
     * 创建重试模板（默认重试3次，间隔1秒）
     */
    public RetryTemplate() {
        this(3, 1000, false);
    }

    /**
     * 执行可重试操作
     *
     * @param operation 操作
     * @param <T>       返回值类型
     * @return 操作结果
     * @throws Exception 如果所有重试都失败，则抛出最后一次异常
     */
    public <T> T execute(Supplier<T> operation) throws Exception {
        Exception lastException = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                return operation.get();
            } catch (Exception e) {
                lastException = e;

                if (attempt < maxRetries) {
                    long waitTime = calculateWaitTime(attempt);
                    log.warn("操作失败，将在{}毫秒后进行第{}次重试. 异常: {}", waitTime, attempt + 1, e.getMessage());

                    try {
                        TimeUnit.MILLISECONDS.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }

        throw lastException;
    }

    /**
     * 执行无返回值的可重试操作
     *
     * @param operation 操作
     * @throws Exception 如果所有重试都失败，则抛出最后一次异常
     */
    public void execute(Runnable operation) throws Exception {
        execute(() -> {
            operation.run();
            return null;
        });
    }

    /**
     * 计算等待时间
     *
     * @param attempt 当前尝试次数（从0开始）
     * @return 等待时间（毫秒）
     */
    private long calculateWaitTime(int attempt) {
        if (exponentialBackoff) {
            // 指数退避: interval * 2^attempt
            return retryInterval * (1L << attempt);
        } else {
            return retryInterval;
        }
    }
} 