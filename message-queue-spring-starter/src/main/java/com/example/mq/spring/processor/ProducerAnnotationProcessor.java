package com.example.mq.spring.processor;

import com.example.mq.core.annotation.Producer;
import com.example.mq.core.annotation.ProducerAnnotationProperties;
import com.example.mq.core.annotation.Producers;
import com.example.mq.core.config.MessageQueueConfig;
import com.example.mq.core.config.MessageQueueProperties;
import com.example.mq.core.enums.MessageQueueType;
import com.example.mq.core.exception.ConfigurationException;
import com.example.mq.core.factory.ProducerFactory;
import com.example.mq.core.message.MessageResult;
import com.example.mq.core.producer.IProducer;
import com.example.mq.support.util.PropertyUtils;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
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
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 生产者注解处理器
 */
@Slf4j
@Component
public class ProducerAnnotationProcessor implements SpelExpression, BeanPostProcessor, BeanFactoryAware, DisposableBean {
    private final MessageQueueConfig messageQueueConfig;
    private final Map<MessageQueueType, ProducerFactory> producerFactories = new HashMap<>();
    private final Map<String, IProducer> producers = new ConcurrentHashMap<>();
    private ListableBeanFactory beanFactory;
    private final StandardBeanExpressionResolver expressionResolver = new StandardBeanExpressionResolver();

    @Autowired
    public ProducerAnnotationProcessor(MessageQueueConfig messageQueueConfig) {
        this.messageQueueConfig = messageQueueConfig;
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
        AtomicReference<Object> currentBean = new AtomicReference<>(bean);

        // 处理类级别的@Producer注解
        Producer classProducer = AnnotationUtils.findAnnotation(targetClass, Producer.class);
        if (classProducer != null) {
            currentBean.set(processClassLevelProducer(currentBean.get(), targetClass, classProducer));
        }

        // 处理类级别的@Producers注解
        Producers classProducers = AnnotationUtils.findAnnotation(targetClass, Producers.class);
        if (classProducers != null) {
            for (Producer producer : classProducers.value()) {
                currentBean.set(processClassLevelProducer(currentBean.get(), targetClass, producer));
            }
        }

        // 处理方法级别的@Producer注解
        ReflectionUtils.doWithMethods(targetClass, method -> {
            Producer methodProducer = AnnotationUtils.findAnnotation(method, Producer.class);
            if (methodProducer != null) {
                currentBean.set(processMethodLevelProducer(currentBean.get(), method, methodProducer));
            }

            // 处理方法级别的@Producers注解
            Producers methodProducers = AnnotationUtils.findAnnotation(method, Producers.class);
            if (methodProducers != null) {
                List<Producer> sortedProducers = new ArrayList<>(Arrays.asList(methodProducers.value()));
                sortedProducers.sort(Comparator.comparingInt(Producer::order));

                for (Producer producer : sortedProducers) {
                    currentBean.set(processMethodLevelProducer(currentBean.get(), method, producer));
                }
            }
        });

        return currentBean.get();
    }

    /**
     * 处理类级别的@Producer注解
     *
     * @return 代理对象
     */
    private Object processClassLevelProducer(Object bean, Class<?> targetClass, Producer producer) {
        log.info("处理类级别的@Producer注解: {}", targetClass.getName());
        AtomicReference<Object> currentBean = new AtomicReference<>(bean);

        // 查找所有返回MessageResult的方法
        ReflectionUtils.doWithMethods(targetClass, method -> {
            if (MessageResult.class.isAssignableFrom(method.getReturnType())) {
                currentBean.set(processMethodLevelProducer(currentBean.get(), method, producer));
            }
        });

        return currentBean.get();
    }

    /**
     * 处理方法级别的@Producer注解
     *
     * @return 代理对象
     */
    private Object processMethodLevelProducer(Object bean, Method method, Producer producer) {
        log.info("处理方法级别的@Producer注解: {}.{}", method.getDeclaringClass().getName(), method.getName());

        // 验证方法返回类型
        if (!MessageResult.class.isAssignableFrom(method.getReturnType())) {
            throw new ConfigurationException("@Producer注解的方法必须返回MessageResult类型: " + method);
        }

        // 解析注解属性
        MessageQueueType messageQueueType = producer.type();
        String cluster = resolveExpression(producer.cluster());
        ProducerAnnotationProperties producerAnnotationProperties = ProducerAnnotationProperties.builder()
                .type(messageQueueType)
                .cluster(cluster)
                .topic(resolveExpression(producer.topic()))
                .async(producer.async())
                .sequence(producer.sequence())
                .transactionId(producer.transactionId())
                .delayTime(producer.delayTime())
                .delayUnit(producer.delayUnit())
                .timeout(producer.timeout())
                .properties(PropertyUtils.parseProperties(producer.properties()))
                .build();

        // 获取或创建生产者
        IProducer mqProducer = getOrCreateProducer(messageQueueType, cluster, producerAnnotationProperties);

        // 创建方法代理
        ProxyFactory proxyFactory = new ProxyFactory();
        proxyFactory.setTarget(bean);
        proxyFactory.setProxyTargetClass(true);

        // 创建一个Pointcut，只拦截特定方法
        Pointcut pointcut = new Pointcut() {
            @Override
            public ClassFilter getClassFilter() {
                return ClassFilter.TRUE;
            }

            @Override
            public MethodMatcher getMethodMatcher() {
                return new MethodMatcher() {
                    @Override
                    public boolean matches(Method m, Class<?> targetClass) {
                        return m.equals(method);
                    }

                    @Override
                    public boolean isRuntime() {
                        return false;
                    }

                    @Override
                    public boolean matches(Method m, Class<?> targetClass, Object... args) {
                        return false;
                    }
                };
            }
        };

        // 创建拦截器
        MethodInterceptor interceptor = new ProducerMethodInterceptor(mqProducer, producerAnnotationProperties);

        // 添加Advisor
        proxyFactory.addAdvisor(new DefaultPointcutAdvisor(pointcut, interceptor));

        // 返回代理对象
        return proxyFactory.getProxy();
    }

    /**
     * 获取或创建生产者
     */
    private IProducer getOrCreateProducer(MessageQueueType type, String cluster, ProducerAnnotationProperties annotationProperties) {
        // 如果是顺序生产，那么需要单独使用生产者
        if (annotationProperties.isSequence()
                || StringUtils.hasText(annotationProperties.getTransactionId()))
            return doCreateProducer(type, cluster, (factory, properties) -> factory.createProducer(properties, annotationProperties));
        else
            return producers.computeIfAbsent(type + "::" + (StringUtils.hasText(cluster) ? cluster : messageQueueConfig.getDefaultCluster()),
                    k -> doCreateProducer(type, cluster, ProducerFactory::createProducer));
    }

    private IProducer doCreateProducer(MessageQueueType type, String cluster, ProducerGetter producerGetter) {
        // 获取配置
        MessageQueueProperties properties = messageQueueConfig.getProperties(type, cluster);
        if (properties == null)
            throw new ConfigurationException("未找到消息队列配置: " + type + ", " + cluster);

        // 获取工厂
        ProducerFactory factory = getProducerFactory(type);
        if (factory == null) {
            throw new ConfigurationException("未找到消息队列生产者工厂: " + type);
        }

        // 创建生产者
        return producerGetter.supply(factory, properties);
    }

    /**
     * 获取生产者工厂
     */
    private ProducerFactory getProducerFactory(MessageQueueType type) {
        ProducerFactory factory = producerFactories.get(type);
        if (factory == null) {
            // 查找所有ProducerFactory类型的Bean
            Map<String, ProducerFactory> factories = beanFactory.getBeansOfType(ProducerFactory.class);
            for (ProducerFactory f : factories.values()) {
                if (f.supports(type)) {
                    factory = f;
                    producerFactories.put(type, f);
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
        // 关闭所有生产者
        for (IProducer producer : producers.values()) {
            try {
                producer.close();
            } catch (Exception e) {
                log.error("关闭生产者失败", e);
            }
        }
        producers.clear();
    }

    @FunctionalInterface
    public interface ProducerGetter {
        IProducer supply(ProducerFactory factory, MessageQueueProperties properties);
    }
} 