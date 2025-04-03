package info.mouts.orderservice.kafka;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(MockitoExtension.class)
public class KafkaConsumerServiceTest {
    @Mock
    private OrderService orderService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    // Use SimpleMeterRegistry instead of mocking
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private KafkaConsumerService kafkaConsumerService;

    private OrderRequestDTO orderRequestTestDTO;
    private String testKey;
    private String testRedisKey;

    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:order:";
    private static final String STATUS_PROCESSING = OrderStatus.PROCESSING.name();
    private static final String STATUS_COMPLETED = OrderStatus.PROCESSED.name();
    private static final Duration PROCESSING_TTL = Duration.ofHours(1);
    private static final Duration COMPLETED_TTL = Duration.ofDays(1);

    @BeforeEach
    void setUp() {
        String key = UUID.randomUUID().toString();

        orderRequestTestDTO = new OrderRequestDTO();
        testKey = key;
        testRedisKey = IDEMPOTENCY_KEY_PREFIX + testKey;

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Simply create the service with the SimpleMeterRegistry
        kafkaConsumerService = new KafkaConsumerService(orderService, redisTemplate, meterRegistry);
    }

    @Test
    @DisplayName("Should process order when idempotency key is new")
    void receiveOrder_newKey_shouldProcess() {
        when(valueOperations.setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL)).thenReturn(true);
        kafkaConsumerService.listen(orderRequestTestDTO, testKey);

        verify(valueOperations).setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL);
        verify(orderService).processIncomingOrder(orderRequestTestDTO, testKey);
        verify(valueOperations).set(testRedisKey, STATUS_COMPLETED, COMPLETED_TTL);
    }

    @Test
    @DisplayName("Should skip processing when idempotency key is already COMPLETED")
    void receiveOrder_completedKey_shouldSkip() {
        when(valueOperations.setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL)).thenReturn(false);
        when(valueOperations.get(testRedisKey)).thenReturn(STATUS_COMPLETED);

        kafkaConsumerService.listen(orderRequestTestDTO, testKey);

        verify(valueOperations).setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL);
        verify(valueOperations).get(testRedisKey);
        verify(orderService, never()).processIncomingOrder(any(), anyString());
        verify(valueOperations, never()).set(anyString(), eq(STATUS_COMPLETED), any(Duration.class));
    }

    @Test
    @DisplayName("Should skip processing when idempotency key is already PROCESSING")
    void receiveOrder_processingKey_shouldSkip() {
        when(valueOperations.setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL)).thenReturn(false);
        when(valueOperations.get(testRedisKey)).thenReturn(STATUS_PROCESSING);

        kafkaConsumerService.listen(orderRequestTestDTO, testKey);

        verify(valueOperations).setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL);
        verify(valueOperations).get(testRedisKey);
        verify(orderService, never()).processIncomingOrder(any(), anyString());
        verify(valueOperations, never()).set(anyString(), eq(STATUS_COMPLETED), any(Duration.class));
    }

    @Test
    @DisplayName("Should re-throw exception if processing fails after acquiring key")
    void receiveOrder_newKey_processingFails() {
        when(valueOperations.setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL)).thenReturn(true);
        RuntimeException exception = new RuntimeException("Processing error");
        doThrow(exception).when(orderService).processIncomingOrder(orderRequestTestDTO, testKey);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            kafkaConsumerService.listen(orderRequestTestDTO, testKey);
        });
        assertEquals(exception, thrown);

        verify(valueOperations).setIfAbsent(testRedisKey, STATUS_PROCESSING, PROCESSING_TTL);
        verify(orderService).processIncomingOrder(orderRequestTestDTO, testKey);
        verify(valueOperations, never()).set(testRedisKey, STATUS_COMPLETED, COMPLETED_TTL);
    }
}
