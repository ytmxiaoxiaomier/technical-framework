package com.example.mq.spring.config;

import com.example.mq.core.config.MessageQueueConfig;
import com.example.mq.core.container.MessageListenerContainerRegistry;
import com.example.mq.spring.processor.MessageQueueListenerProcessor;
import com.example.mq.spring.processor.ProducerAnnotationProcessor;
import com.example.mq.support.serializer.JsonMessageSerializer;
import com.example.mq.support.serializer.MessageSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 消息队列自动配置类
 */
@Configuration
@EnableConfigurationProperties(MessageQueueConfig.class)
@Import({ProducerAnnotationProcessor.class, MessageQueueListenerProcessor.class})
public class MessageQueueAutoConfiguration {
    /**
     * 注册消息序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public MessageSerializer messageSerializer() {
        return new JsonMessageSerializer();
    }

//    @Bean
//    @ConditionalOnMissingBean
//    public MessageQueueConfig messageQueueConfig() {
//        return new MessageQueueConfig();
//    }

    @Bean
    @ConditionalOnMissingBean
    public MessageListenerContainerRegistry messageListenerContainerRegistry() {
        return new MessageListenerContainerRegistry();
    }
} 