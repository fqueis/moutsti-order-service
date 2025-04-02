package info.mouts.orderservice.event;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.dto.OrderProcessedEventDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderProcessedEventListenerTest {
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private CompletableFuture<SendResult<String, Object>> mockFuture;

    @InjectMocks
    private OrderProcessedEventListener orderProcessedEventListener;

    @Captor
    private ArgumentCaptor<String> topicCaptor;
    @Captor
    private ArgumentCaptor<String> keyCaptor;
    @Captor
    private ArgumentCaptor<OrderProcessedEventDTO> valueCaptor;
    @Captor
    private ArgumentCaptor<BiConsumer<? super SendResult<String, Object>, ? super Throwable>> callbackCaptor;

    @Value("${app.kafka.orders-processed-topic}")
    private String orderProcessedTopic;

    private final UUID ORDER_ID = UUID.randomUUID();
    private Order testOrder;
    private OrderProcessedEventDTO testEventDto;
    private OrderProcessedEvent testEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderProcessedEventListener, "ordersProcessedTopic", orderProcessedTopic);

        testOrder = Order.builder().id(ORDER_ID).build();

        testEventDto = new OrderProcessedEventDTO();
        testEventDto.setOrderId(ORDER_ID);

        testEvent = new OrderProcessedEvent(this, testOrder);
    }

    @Test
    @DisplayName("Should map event and send to Kafka topic on success")
    void handleOrderProcessedEvent_shouldSendToKafka() {
        // Mock the orderMapper to return the testEventDto
        when(orderMapper.toProcessedEventDto(testOrder)).thenReturn(testEventDto);

        // Mock the kafkaTemplate to return the mockFuture
        when(kafkaTemplate.send(eq(orderProcessedTopic), eq(ORDER_ID.toString()), eq(testEventDto)))
                .thenReturn(mockFuture);

        orderProcessedEventListener.onOrderProcessed(testEvent);

        // Verify the orderMapper was called with the testOrder
        verify(orderMapper).toProcessedEventDto(eq(testOrder));

        // Verify the kafkaTemplate was called with the correct arguments
        verify(kafkaTemplate).send(topicCaptor.capture(), keyCaptor.capture(), valueCaptor.capture());

        // Verify the mockFuture was called with the correct arguments
        verify(mockFuture).whenComplete(callbackCaptor.capture());

        // Assert the correct arguments were passed to the kafkaTemplate
        assertThat(topicCaptor.getValue()).isEqualTo(orderProcessedTopic);

        // Assert the correct key and value were passed to the kafkaTemplate
        assertThat(keyCaptor.getValue()).isEqualTo(ORDER_ID.toString());

        // Assert the correct value was passed to the kafkaTemplate
        assertThat(valueCaptor.getValue()).isEqualTo(testEventDto);
    }

    @Test
    @DisplayName("Should log error when Kafka send fails")
    void handleOrderProcessedEvent_shouldLogErrorOnKafkaFailure() {
        RuntimeException kafkaException = new RuntimeException("Kafka send failed!");

        // Mock the orderMapper to return the testEventDto
        when(orderMapper.toProcessedEventDto(testOrder)).thenReturn(testEventDto);

        // Mock the kafkaTemplate to return the mockFuture
        when(kafkaTemplate.send(eq(orderProcessedTopic), eq(ORDER_ID.toString()), eq(testEventDto)))
                .thenReturn(mockFuture);

        orderProcessedEventListener.onOrderProcessed(testEvent);

        // Verify the orderMapper was called with the testOrder
        verify(orderMapper).toProcessedEventDto(eq(testOrder));

        // Verify the kafkaTemplate was called with the correct arguments
        verify(kafkaTemplate).send(eq(orderProcessedTopic), eq(ORDER_ID.toString()), eq(testEventDto));

        // Verify the mockFuture was called with the correct arguments
        verify(mockFuture).whenComplete(callbackCaptor.capture());

        // Simulate the failure callback being invoked
        callbackCaptor.getValue().accept(null, kafkaException);
    }
}
