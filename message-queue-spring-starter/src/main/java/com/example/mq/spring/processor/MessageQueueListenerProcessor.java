package com.example.mq.spring.processor;

import com.example.mq.core.annotation.ConsumerAnnotationProperties;
import com.example.mq.core.annotation.MessageQueueListener;
import com.example.mq.core.annotation.MessageQueueListeners;
import com.example.mq.core.config.MessageQueueConfig;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.consumer.IConsumer;
import com.example.mq.core.consumer.MessageListener;
import com.example.mq.core.container.MessageListenerContainer;
import com.example.mq.core.container.MessageListenerContainerRegistry;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.exception.ConfigurationException;
import com.example.mq.core.factory.ConsumerFactory;
import com.example.mq.core.message.Message;
import com.example.mq.support.util.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 消息监听器注解处理器
 */
@Slf4j
@Component
public class MessageQueueListenerProcessor implements SpelExpression, BeanPostProcessor, BeanFactoryAware, DisposableBean {
    private final MessageQueueConfig messageQueueConfig;
    private final Map<MessageQueueType, ConsumerFactory> consumerFactories = new HashMap<>();
    private final MessageListenerContainerRegistry registry;
    private ListableBeanFactory beanFactory;
    private final StandardBeanExpressionResolver expressionResolver = new StandardBeanExpressionResolver();

    @Autowired
    public MessageQueueListenerProcessor(MessageQueueConfig messageQueueConfig, MessageListenerContainerRegistry registry) {
        this.messageQueueConfig = messageQueueConfig;
        this.registry = registry;
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        if (!(beanFactory instanceof ListableBeanFactory)) {
            throw new IllegalArgumentException("BeanFactory must be a ListableBeanFactory");
        }
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);

        // 处理类级别的@MessageQueueListener注解
        MessageQueueListener classListener = AnnotationUtils.findAnnotation(targetClass, MessageQueueListener.class);
        if (classListener != null) {
            processClassLevelListener(bean, targetClass, classListener);
        }

        // 处理类级别的@MessageQueueListeners注解
        MessageQueueListeners classListeners = AnnotationUtils.findAnnotation(targetClass, MessageQueueListeners.class);
        if (classListeners != null) {
            for (MessageQueueListener listener : classListeners.value()) {
                processClassLevelListener(bean, targetClass, listener);
            }
        }

        // 处理方法级别的@MessageQueueListener注解
        ReflectionUtils.doWithMethods(targetClass, method -> {
            MessageQueueListener methodListener = AnnotationUtils.findAnnotation(method, MessageQueueListener.class);
            if (methodListener != null) {
                processMethodLevelListener(bean, method, methodListener);
            }

            // 处理方法级别的@MessageQueueListeners注解
            MessageQueueListeners methodListeners = AnnotationUtils.findAnnotation(method, MessageQueueListeners.class);
            if (methodListeners != null) {
                List<MessageQueueListener> sortedListeners = new ArrayList<>(Arrays.asList(methodListeners.value()));
                sortedListeners.sort(Comparator.comparingInt(MessageQueueListener::order));

                for (MessageQueueListener listener : sortedListeners) {
                    processMethodLevelListener(bean, method, listener);
                }
            }
        });

        return bean;
    }

    /**
     * 处理类级别的@MessageQueueListener注解
     */
    private void processClassLevelListener(Object bean, Class<?> targetClass, MessageQueueListener listener) {
        log.info("处理类级别的@MessageQueueListener注解: {}", targetClass.getName());

        // 如果类实现了MessageListener接口，则直接注册
        if (MessageListener.class.isAssignableFrom(targetClass)) {
            registerListener(listener, (MessageListener) bean);
            return;
        }

        // 查找所有可能的消息处理方法
        ReflectionUtils.doWithMethods(targetClass, method -> {
            // 检查方法参数是否匹配消息处理方法
            if (isMessageHandlerMethod(method)) {
                processMethodLevelListener(bean, method, listener);
            }
        });
    }

    /**
     * 处理方法级别的@MessageQueueListener注解
     */
    private void processMethodLevelListener(Object bean, Method method, MessageQueueListener listener) {
        log.info("处理 方法 + @MessageQueueListener 注解: {}.{}", method.getDeclaringClass().getName(), method.getName());

        // 验证方法参数
        if (!isMessageHandlerMethod(method)) {
            throw new ConfigurationException("@MessageQueueListener注解的方法必须接受Message参数: " + method);
        }

        // 创建方法适配器
        MessageListener adapter = createMethodAdapter(bean, method);

        // 注册监听器
        registerListener(listener, adapter);
    }

    /**
     * 判断方法是否为消息处理方法
     */
    private boolean isMessageHandlerMethod(Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();

        // 检查方法参数
        return paramTypes.length >= 1 && Message.class.isAssignableFrom(paramTypes[0]);
    }

    /**
     * 创建方法适配器
     */
    private MessageListener createMethodAdapter(Object bean, Method method) {
        return (message, context) -> {
            try {
                // 准备参数
                Object[] args;
                Class<?>[] paramTypes = method.getParameterTypes();

                if (paramTypes.length == 1) {
                    // 只有Message参数
                    args = new Object[]{message};
                } else if (paramTypes.length == 2 && MessageListener.ConsumeContext.class.isAssignableFrom(paramTypes[1])) {
                    // Message和ConsumeContext参数
                    args = new Object[]{message, context};
                } else {
                    log.error("不支持的方法参数类型: {}", method);
                    return MessageListener.ConsumeResult.FAILURE;
                }

                // 调用方法
                method.setAccessible(true);
                Object result = method.invoke(bean, args);

                // 处理返回值
                if (result == null) {
                    return MessageListener.ConsumeResult.SUCCESS;
                } else if (result instanceof MessageListener.ConsumeResult consumeResult) {
                    return consumeResult;
                } else if (result instanceof Boolean flag) {
                    return flag ? MessageListener.ConsumeResult.SUCCESS : MessageListener.ConsumeResult.FAILURE;
                } else {
                    return MessageListener.ConsumeResult.SUCCESS;
                }
            } catch (Exception e) {
                log.error("消息处理失败", e);
                return MessageListener.ConsumeResult.FAILURE;
            }
        };
    }

    /**
     * 注册监听器
     */
    private void registerListener(MessageQueueListener listenerAnnotation, MessageListener listener) {
        // 解析注解属性
        MessageQueueType type = listenerAnnotation.type();
        String cluster = resolveExpression(listenerAnnotation.cluster());
        ConsumerAnnotationProperties annoProperties = ConsumerAnnotationProperties.builder()
                .type(type)
                .cluster(cluster)
                .topic(resolveExpression(listenerAnnotation.topic()))
                .group(resolveExpression(listenerAnnotation.group()))
                .sequence(listenerAnnotation.sequence())
                .transaction(listenerAnnotation.transaction())
                .delayQueue(listenerAnnotation.delayQueue())
                .concurrency(listenerAnnotation.concurrency())
                .properties(PropertyUtils.parseProperties(listenerAnnotation.properties()))
                .build();

        // 获取消息队列配置
        MessageQueueProperties mqProperties = messageQueueConfig.getProperties(type, cluster);
        if (mqProperties == null) {
            throw new ConfigurationException("未找到消息队列配置: " + type + ":" + cluster);
        }

        // 获取消费者工厂
        ConsumerFactory factory = getConsumerFactory(type);
        if (factory == null) {
            throw new ConfigurationException("未找到消息队列消费者工厂: " + type);
        }

        // 获取或创建消费者
        IConsumer consumer = listenerAnnotation.sequence() || listenerAnnotation.transaction() || listenerAnnotation.delayQueue()
                ? factory.createConsumer(mqProperties, annoProperties)
                : factory.createConsumer(mqProperties);

        // 创建监听容器
        MessageListenerContainer container = factory.createContainer(mqProperties);
        container.setConsumerAnnotationProperties(annoProperties);
        container.setConsumer(consumer);
        container.setProperties(mqProperties);
        container.setupMessageListener(listener);

        // 注册容器
        registry.registerContainer(container);

        log.info("注册消息监听容器: type={}, topic={}, group={}, concurrency={}", type, annoProperties.getTopic(), annoProperties.getGroup(), annoProperties.getConcurrency());
    }

    /**
     * 获取消费者工厂
     */
    private ConsumerFactory getConsumerFactory(MessageQueueType type) {
        ConsumerFactory factory = consumerFactories.get(type);
        if (factory == null) {
            // 查找所有ConsumerFactory类型的Bean
            Map<String, ConsumerFactory> factories = beanFactory.getBeansOfType(ConsumerFactory.class);
            for (ConsumerFactory f : factories.values()) {
                if (f.supports(type)) {
                    factory = f;
                    consumerFactories.put(type, f);
                    break;
                }
            }
        }
        return factory;
    }

    @Override
    public StandardBeanExpressionResolver expressionResolver() {
        return expressionResolver;
    }

    @Override
    public void destroy() throws Exception {
        // 监听容器会负责关闭消费者，这里不需要额外操作
        log.info("MessageQueueListenerProcessor销毁");
    }
} 