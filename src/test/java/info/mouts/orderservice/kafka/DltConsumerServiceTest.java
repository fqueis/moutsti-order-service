package info.mouts.orderservice.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.support.KafkaHeaders;
import org.mockito.quality.Strictness;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;
import info.mouts.orderservice.util.KafkaUtils;

/**
 * Tests for the Kafka Dead Letter Topic (DLT) consumer service.
 * This class tests various scenarios for handling failed messages from Kafka.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class DltConsumerServiceTest {

    // Mock dependencies used by the service
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private DltConsumerService dltConsumerService;

    private String dltTopic = "orders.dlt.v1";

    // Test data shared across test methods
    private String idempotencyKey;
    private OrderRequestDTO dto;
    private byte[] payloadBytes;
    private ConsumerRecord<String, byte[]> consumerRecord;
    private String exceptionMessage = "Error processing original message";

    @BeforeEach
    void setUp() throws Exception {
        idempotencyKey = "DLT-KEY-" + UUID.randomUUID();

        // Create a fake order request DTO for testing
        dto = KafkaUtils.createFakeOrderRequestDTO("p-dlt", 1);

        // Convert the DTO to bytes (simulating a Kafka message payload)
        payloadBytes = new ObjectMapper().writeValueAsBytes(dto);

        // Create Kafka headers with necessary DLT information
        RecordHeaders headers = new RecordHeaders();
        headers.add(KafkaUtils.IDEMPOTENCY_KEY_HEADER, idempotencyKey.getBytes());
        headers.add(KafkaHeaders.DLT_EXCEPTION_MESSAGE, exceptionMessage.getBytes());
        headers.add(KafkaHeaders.DLT_ORIGINAL_TOPIC, dltTopic.getBytes());

        // Create a consumer record representing a message from the DLT topic
        consumerRecord = new ConsumerRecord<>(dltTopic, 0, 0L, "key", payloadBytes);

        Field headersField = ConsumerRecord.class.getDeclaredField("headers");
        headersField.setAccessible(true);
        headersField.set(consumerRecord, headers);

        // Mock the object mapper to return our DTO when deserializing the payload
        when(objectMapper.readValue(eq(payloadBytes), eq(OrderRequestDTO.class))).thenReturn(dto);
    }

    @Test
    @DisplayName("DLT Consumer should mark existing order as FAILED")
    void consumeDltMessage_existingOrder_marksFailed() {
        // Arrange: Create an existing order in a non-terminal state
        Order existingOrder = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PROCESSING).build();

        // Mock the repository to return this existing order when searched by
        // idempotency key
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        // Act: Call the DLT consumer service with our message
        dltConsumerService.listen(consumerRecord);

        // Assert: Verify the repository was queried with the correct idempotency key
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);

        // Capture the order saved to the repository to inspect its properties
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        // Verify the order status was updated to FAILED
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.FAILED);

        // Verify the mapper was not called (should not create a new order)
        verify(orderMapper, never()).toEntity(any(OrderRequestDTO.class));
    }

    @Test
    @DisplayName("DLT Consumer should create new order as FAILED if not existing and DTO parsed")
    void consumeDltMessage_newOrder_createsFailed() {
        // Arrange: Create a mapped order that would result from the DTO
        Order mappedOrder = Order.builder().idempotencyKey(idempotencyKey).items(Collections.emptyList()).build();

        // Mock the mapper to return our prepared order
        when(orderMapper.toEntity(dto)).thenReturn(mappedOrder);

        // Mock the repository to return empty (order doesn't exist)
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        // Act: Call the DLT consumer service with our message
        dltConsumerService.listen(consumerRecord);

        // Assert: Verify the repository was queried with the correct idempotency key
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);

        // Verify the mapper was called to create a new order
        verify(orderMapper).toEntity(dto);

        // Capture the order saved to the repository
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        // Verify the new order was saved with FAILED status
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo(OrderStatus.FAILED);

        // Verify the idempotency key was preserved
        assertThat(orderCaptor.getValue().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }

    @Test
    @DisplayName("DLT Consumer should do nothing if order already COMPLETED")
    void consumeDltMessage_existingCompletedOrder_doesNothing() {
        // Arrange: Create an existing order in a terminal state
        Order existingOrder = Order.builder().id(UUID.randomUUID()).status(OrderStatus.PROCESSED).build();

        // Mock the repository to return this terminal-state order
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        // Act: Call the DLT consumer service with our message
        dltConsumerService.listen(consumerRecord);

        // Assert: Verify the repository was queried with the correct idempotency key
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);

        // Verify the order was NOT saved (nothing should happen for completed orders)
        verify(orderRepository, never()).save(any(Order.class));

        // Verify the mapper was not called (no new order creation)
        verify(orderMapper, never()).toEntity(any(OrderRequestDTO.class));
    }

    @Test
    @DisplayName("DLT Consumer should log error if order not found and DTO cannot be parsed")
    void consumeDltMessage_notFoundAndCannotParse_logsError() throws Exception {
        // Arrange: Mock the repository to return empty (order doesn't exist)
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

        // Mock the object mapper to throw an exception when deserializing
        when(objectMapper.readValue(eq(payloadBytes), eq(OrderRequestDTO.class)))
                .thenThrow(new JsonProcessingException("Parse error") {
                });

        // Act: Call the DLT consumer service with our message
        dltConsumerService.listen(consumerRecord);

        // Assert: Verify the repository was queried with the correct idempotency key
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);

        // Verify no order was saved (parsing failed)
        verify(orderRepository, never()).save(any(Order.class));

        // Verify the mapper was not called (couldn't get to that step)
        verify(orderMapper, never()).toEntity(any(OrderRequestDTO.class));
    }

    @Test
    @DisplayName("DLT Consumer should handle DB error during status update")
    void consumeDltMessage_dbErrorOnUpdate_logsError() {
        // Arrange: Create an existing order in a non-terminal state
        Order existingOrder = new Order();
        existingOrder.setStatus(OrderStatus.PROCESSING);

        // Mock the repository to return our existing order
        when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingOrder));

        // Mock the repository to throw a database exception when saving
        when(orderRepository.save(any(Order.class))).thenThrow(new DataAccessException("DB connection failed") {
        });

        // Act: Call the DLT consumer service with our message
        // We don't expect the method to throw an exception as it should handle errors
        // internally
        assertDoesNotThrow(() -> dltConsumerService.listen(consumerRecord));

        // Assert: Verify the repository was queried with the correct idempotency key
        verify(orderRepository).findByIdempotencyKey(idempotencyKey);

        // Verify the repository tried to save the order (even though it failed)
        verify(orderRepository).save(any(Order.class));
    }
}
