package com.example.mq.core.message;

import com.example.mq.core.enums.MessageQueueType;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * 组合消息结果
 * 用于同时向多个消息队列发送消息的场景
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CompositeMessageResult extends MessageResult {
    /**
     * 各消息队列的发送结果
     */
    private Map<MessageQueueType, Map<String, MessageResult>> results = new EnumMap<>(MessageQueueType.class);

    /**
     * 添加消息队列结果
     *
     * @param type   消息队列类型
     * @param key    往哪个集群的topic发: cluster+"::"+topic
     * @param result 该队列的发送结果
     * @return 当前对象
     */
    public CompositeMessageResult addResult(MessageQueueType type, String key, MessageResult result) {
        compute(results, type, key, result);
        return this;
    }

    /**
     * 获取指定消息队列类型的结果
     *
     * @param type 消息队列类型
     * @return 该队列的发送结果，不存在则返回null
     */
    public MessageResult getResult(MessageQueueType type, String key) {
        return Optional.ofNullable(results.get(type))
                .map(result -> result.get(key))
                .orElse(null);
    }

    /**
     * 检查所有结果是否都已完成
     *
     * @return 是否全部完成
     */
    @Override
    public boolean isCompleted() {
        return results.values()
                .stream()
                .map(Map::values)
                .flatMap(Collection::stream)
                .allMatch(MessageResult::isCompleted);
    }

    /**
     * 等待所有结果完成，返回综合结果
     * - 如果所有MQ都发送成功，则返回成功
     * - 如果任一MQ发送失败，则返回失败
     *
     * @return 综合发送结果
     * @throws ExecutionException   执行异常
     * @throws InterruptedException 中断异常
     */
    @Override
    public List<SendResult> get() throws ExecutionException, InterruptedException {
        CompositeSendResult result = new CompositeSendResult();

        boolean allSuccess = true;
        Throwable firstError = null;

        for (Map.Entry<MessageQueueType, Map<String, MessageResult>> entry : results.entrySet()) {
            MessageQueueType type = entry.getKey();
            Map<String, MessageResult> messageResults = entry.getValue();
            for (Map.Entry<String, MessageResult> messageResultEntry : messageResults.entrySet()) {
                String key = messageResultEntry.getKey();
                MessageResult messageResult = messageResultEntry.getValue();
                try {
                    List<SendResult> sendResults = messageResult.get();
                    result.addResult(type, key, sendResults);
                    if (allSuccess) {
                        SendResult failResult = sendResults.stream()
                                .filter(sendResult -> !sendResult.isSuccess())
                                .findAny()
                                .orElse(null);
                        if (failResult != null) {
                            allSuccess = false;
                            firstError = failResult.getException();
                        }
                    }
                } catch (Exception e) {
                    allSuccess = false;
                    if (firstError == null)
                        firstError = e;
                    result.addResult(type, key, List.of(BaseSendResult.failure(null, e)));
                }
            }
        }

        result.setSuccess(allSuccess);
        result.setException(firstError);

        if (results.isEmpty()) {
            throw new IllegalStateException("没有任何消息发送结果");
        }

        // 使用第一个结果的消息ID作为组合结果的ID
        result.setMessageId(Optional.ofNullable(getMessages())
                .filter(messages -> !messages.isEmpty())
                .map(messages -> messages.get(0).getMessageId())
                .orElse(null));

        return List.of(result);
    }

    /**
     * 组合发送结果
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CompositeSendResult extends BaseSendResult {
        /**
         * 各消息队列的发送结果
         */
        private Map<MessageQueueType, Map<String, List<SendResult>>> results = new EnumMap<>(MessageQueueType.class);

        /**
         * 添加单个结果
         *
         * @param type   消息队列类型
         * @param key    集群+"::"+topic
         * @param result 发送结果
         * @return 当前对象
         */
        public CompositeSendResult addResult(MessageQueueType type, String key, List<SendResult> result) {
            compute(results, type, key, result);
            return this;
        }

        /**
         * 获取指定消息队列类型的结果
         *
         * @param type 消息队列类型
         * @param key  集群+"::"+topic
         * @return 该队列的发送结果，不存在则返回null
         */
        public List<SendResult> getResult(MessageQueueType type, String key) {
            return Optional.ofNullable(results.get(type))
                    .map(result -> result.get(key))
                    .orElse(null);
        }

        @Override
        public Object getRawResult() {
            return results;
        }
    }

    private static <T> void compute(Map<MessageQueueType, Map<String, T>> results,
                                    MessageQueueType type,
                                    String key,
                                    T result) {
        if (type != null && result != null)
            results.compute(type, (messageQueueType, resultMap) -> {
                if (resultMap == null)
                    resultMap = new HashMap<>();
                resultMap.put(key, result);
                return resultMap;
            });
    }
} 