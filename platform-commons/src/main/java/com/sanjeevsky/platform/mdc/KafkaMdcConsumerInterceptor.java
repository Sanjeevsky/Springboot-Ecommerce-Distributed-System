package com.sanjeevsky.platform.mdc;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;
import org.springframework.kafka.listener.RecordInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * Reads correlationId and userId from Kafka message headers and sets them in MDC
 * before the @KafkaListener method executes. Clears them after.
 * Auto-detected by Spring Kafka's ConcurrentKafkaListenerContainerFactory.
 */
public class KafkaMdcConsumerInterceptor<K, V> implements RecordInterceptor<K, V> {

    @Override
    public ConsumerRecord<K, V> intercept(ConsumerRecord<K, V> record) {
        setMdcFromHeader(record, MdcConstants.HEADER_CORRELATION_ID, MdcConstants.CORRELATION_ID);
        setMdcFromHeader(record, MdcConstants.HEADER_USER, MdcConstants.USER_ID);
        return record;
    }

    @Override
    public void success(ConsumerRecord<K, V> record, Consumer<K, V> consumer) {
        clearMdc();
    }

    @Override
    public void failure(ConsumerRecord<K, V> record, Exception exception, Consumer<K, V> consumer) {
        clearMdc();
    }

    private void clearMdc() {
        MDC.remove(MdcConstants.CORRELATION_ID);
        MDC.remove(MdcConstants.USER_ID);
    }

    private void setMdcFromHeader(ConsumerRecord<?, ?> record, String headerName, String mdcKey) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            MDC.put(mdcKey, new String(header.value(), StandardCharsets.UTF_8));
        }
    }
}
