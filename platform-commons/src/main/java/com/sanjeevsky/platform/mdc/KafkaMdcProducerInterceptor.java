package com.sanjeevsky.platform.mdc;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Copies correlationId and userId from MDC into Kafka message headers before the
 * record is sent. Registered on DefaultKafkaProducerFactory by MdcLoggingAutoConfiguration.
 */
public class KafkaMdcProducerInterceptor implements ProducerInterceptor<Object, Object> {

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        addHeader(record, MdcConstants.HEADER_CORRELATION_ID, MdcConstants.CORRELATION_ID);
        addHeader(record, MdcConstants.HEADER_USER, MdcConstants.USER_ID);
        return record;
    }

    private void addHeader(ProducerRecord<?, ?> record, String headerName, String mdcKey) {
        String value = MDC.get(mdcKey);
        if (value != null && !value.isBlank()) {
            record.headers().add(headerName, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
