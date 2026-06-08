package com.sanjeevsky.platform.mdc;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * Registers MDC propagation infrastructure as Spring beans.
 * Each nested config is guarded by @ConditionalOnClass so the relevant classes
 * are only instantiated in services that actually have those deps on the classpath.
 */
@Configuration(proxyBeanMethods = false)
public class MdcLoggingAutoConfiguration {

    /** Servlet services: inject correlationId + userId into MDC from request headers. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
    static class ServletMdcConfig {
        @Bean
        MdcFilter mdcFilter() {
            return new MdcFilter();
        }
    }

    /** Feign-enabled services: forward correlationId + userId in outgoing HTTP calls. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "feign.RequestInterceptor")
    static class FeignMdcConfig {
        @Bean
        CorrelationIdFeignInterceptor correlationIdFeignInterceptor() {
            return new CorrelationIdFeignInterceptor();
        }
    }

    /**
     * Kafka-enabled services:
     * - Consumer interceptor: auto-detected by Spring Kafka's container factory
     * - Producer interceptor: added to DefaultKafkaProducerFactory via BeanPostProcessor
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.kafka.listener.RecordInterceptor")
    static class KafkaMdcConfig {

        @Bean
        KafkaMdcConsumerInterceptor<Object, Object> kafkaMdcConsumerInterceptor() {
            return new KafkaMdcConsumerInterceptor<>();
        }

        /** Static bean to avoid circular dependency with the producer factory. */
        @Bean
        static BeanPostProcessor kafkaMdcProducerFactoryPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!(bean instanceof DefaultKafkaProducerFactory)) {
                        return bean;
                    }
                    DefaultKafkaProducerFactory factory = (DefaultKafkaProducerFactory) bean;
                    String interceptorClass = KafkaMdcProducerInterceptor.class.getName();
                    Map<String, Object> props = factory.getConfigurationProperties();
                    Object existing = props.get(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG);
                    String updated;
                    if (existing == null || existing.toString().isEmpty()) {
                        updated = interceptorClass;
                    } else if (existing.toString().contains(interceptorClass)) {
                        return bean;
                    } else {
                        updated = existing.toString() + "," + interceptorClass;
                    }
                    factory.updateConfigs(Collections.singletonMap(
                            ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, updated));
                    return bean;
                }
            };
        }
    }
}
