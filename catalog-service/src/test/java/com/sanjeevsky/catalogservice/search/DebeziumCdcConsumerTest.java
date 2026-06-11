package com.sanjeevsky.catalogservice.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sanjeevsky.catalogservice.repository.ProductRepository;
import com.sanjeevsky.catalogservice.search.repository.ProductSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.handler.annotation.Payload;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class DebeziumCdcConsumerTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductSearchRepository searchRepository;
    @Mock private ProductDocumentMapper mapper;

    private DebeziumCdcConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DebeziumCdcConsumer(
                productRepository,
                searchRepository,
                mapper,
                new ObjectMapper());
    }

    @Test
    void listenerAcceptsAndIgnoresTombstoneRecords() throws NoSuchMethodException {
        Method listener = DebeziumCdcConsumer.class.getMethod("onProductChange", String.class);
        Payload payload = listener.getParameters()[0].getAnnotation(Payload.class);

        assertThat(payload).isNotNull();
        assertThat(payload.required()).isFalse();

        consumer.onProductChange(null);

        verifyNoInteractions(productRepository, searchRepository, mapper);
    }

    @Test
    void deleteEventRemovesProductFromSearchIndex() {
        consumer.onProductChange(
                "{\"op\":\"d\",\"before\":{\"id\":\"d1280666-5d09-483b-9638-68bb5b01b057\"}}");

        verify(searchRepository).deleteById("d1280666-5d09-483b-9638-68bb5b01b057");
        verifyNoInteractions(productRepository, mapper);
    }
}
