package info.mouts.orderservice.kafka;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.apache.kafka.clients.producer.ProducerRecord;

import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.repository.OrderRepository;
import info.mouts.orderservice.service.OrderService;
import info.mouts.orderservice.util.KafkaUtils;
import lombok.extern.slf4j.Slf4j;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.kafka.consumer.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.producer.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@EmbeddedKafka(brokerProperties = { "listeners=PLAINTEXT://localhost:9093", "port=9093" }, topics = {
        "${app.kafka.orders-received-topic}", "${app.kafka.dlt-orders-topic}" })
@DisplayName("Kafka Consumer Integration Tests (Retry & DLT)")
@Slf4j
public class KafkaConsumerIntegrationTest {

    private static final String SUCCESS_IDEMPOTENCY_KEY = "SUCCESS-KEY";
    private static final String FAIL_IDEMPOTENCY_KEY = "FAIL-KEY";
    private static final String PRODUCT_ID_1 = "PRODUCT-1";
    private static final String PRODUCT_ID_2 = "PRODUCT-2";
    private static final int EXPECTED_RETRY_ATTEMPTS = 3;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private OrderService orderService;

    @Mock
    private ValueOperations<String, String> valueOperationsMock;

    @MockitoSpyBean
    private DltConsumerService dltConsumerService;

    @MockitoSpyBean
    private OrderRepository orderRepository;

    @Value("${app.kafka.orders-received-topic}")
    private String incomingOrdersTopic;

    @Value("${app.kafka.dlt-orders-topic}")
    private String dltTopic;

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    private Consumer<String, byte[]> dltConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> dltConsumerProps = KafkaTestUtils.consumerProps("test-group-dlt", "true",
                embeddedKafkaBroker);
        dltConsumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringDeserializer.class);
        dltConsumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        dltConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        dltConsumer = new DefaultKafkaConsumerFactory<String, byte[]>(dltConsumerProps).createConsumer();

        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, dltTopic);

        when(redisTemplate.opsForValue()).thenReturn(valueOperationsMock);

        reset(orderService, dltConsumerService, orderRepository);
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null)
            dltConsumer.close();
    }

    @Test
    @DisplayName("Should process message successfully on first attempt")
    void processMessage_successFirstTime() throws Exception {
        // Arrange: Create a valid order request DTO
        OrderRequestDTO dto = KafkaUtils.createFakeOrderRequestDTO(PRODUCT_ID_1, 1);

        // Arrange: Mock Redis to indicate the message hasn't been processed yet
        setupRedisMockForProcessing(SUCCESS_IDEMPOTENCY_KEY);

        // Act: Send the order message to the Kafka topic
        sendOrderToKafka(SUCCESS_IDEMPOTENCY_KEY, dto);

        // Assert: Verify the order service processed the message exactly once
        verify(orderService, timeout(5000).times(1))
                .processIncomingOrder(any(OrderRequestDTO.class), eq(SUCCESS_IDEMPOTENCY_KEY));

        // Assert: Verify Redis was updated to mark the message as processed
        String redisKey = KafkaUtils.IDEMPOTENCY_KEY_PREFIX + SUCCESS_IDEMPOTENCY_KEY;
        verify(valueOperationsMock, timeout(1000).times(1)).set(eq(redisKey),
                eq(KafkaUtils.PROCESSED_STATUS),
                any(Duration.class));

        // Assert: Verify the message was NOT sent to the DLT
        verify(dltConsumerService, never()).listen(any());
    }

    @Test
    @DisplayName("Should retry and eventually send to DLT when processing fails")
    void processMessage_failWithRetries_thenDLT() throws Exception {
        // Arrange: Create a valid order request DTO
        OrderRequestDTO dto = KafkaUtils.createFakeOrderRequestDTO(PRODUCT_ID_2, 2);
        // Arrange: Define the exception that simulates a processing failure
        RuntimeException processingException = new RuntimeException("Simulated processing failure!");
        String redisKey = KafkaUtils.IDEMPOTENCY_KEY_PREFIX + FAIL_IDEMPOTENCY_KEY;

        // Arrange: Mock Redis for the initial idempotency check
        setupRedisMockForProcessing(FAIL_IDEMPOTENCY_KEY);

        // Arrange: Mock the order service to consistently throw an exception
        doThrow(processingException).when(orderService).processIncomingOrder(any(OrderRequestDTO.class),
                eq(FAIL_IDEMPOTENCY_KEY));

        // Arrange: Mock the repository for the final check in DLT processing
        when(orderRepository.findByIdempotencyKey(FAIL_IDEMPOTENCY_KEY)).thenReturn(Optional.empty());

        // Act: Send the order message that will fail processing
        sendOrderToKafka(FAIL_IDEMPOTENCY_KEY, dto);

        // Assert: Verify the order service was called multiple times due to retries
        verify(orderService, timeout(10000).times(EXPECTED_RETRY_ATTEMPTS))
                .processIncomingOrder(any(OrderRequestDTO.class), eq(FAIL_IDEMPOTENCY_KEY));

        // Assert: Verify the message landed in the DLT
        ConsumerRecord<String, byte[]> dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, dltTopic,
                Duration.ofSeconds(5));
        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo(FAIL_IDEMPOTENCY_KEY);

        // Assert: Verify the DLT consumer service was invoked
        verify(dltConsumerService, timeout(5000).times(1)).listen(any());

        // Assert: Verify an order record with FAILED status was saved
        verify(orderRepository, timeout(5000).times(1))
                .save(argThat(order -> order.getStatus() == OrderStatus.FAILED &&
                        order.getIdempotencyKey().equals(FAIL_IDEMPOTENCY_KEY)));

        // Assert: Verify Redis was NEVER marked as PROCESSED
        verify(valueOperationsMock, never()).set(eq(redisKey), eq(KafkaUtils.PROCESSED_STATUS),
                any(Duration.class));

        // Assert: Verify the initial Redis check was performed at least once
        verify(valueOperationsMock, atLeastOnce()).setIfAbsent(eq(redisKey),
                eq(KafkaUtils.PROCESSING_STATUS),
                any(Duration.class));
    }

    private void setupRedisMockForProcessing(String idempotencyKey) {
        String redisKey = KafkaUtils.IDEMPOTENCY_KEY_PREFIX + idempotencyKey;
        when(valueOperationsMock.setIfAbsent(eq(redisKey), eq(KafkaUtils.PROCESSING_STATUS),
                any(Duration.class)))
                .thenReturn(true);
    }

    private void sendOrderToKafka(String idempotencyKey, OrderRequestDTO dto) throws Exception {
        log.info("Sending message to Kafka in topic {} with key {}", incomingOrdersTopic, idempotencyKey);
        ProducerRecord<String, Object> record = new ProducerRecord<>(
                incomingOrdersTopic,
                0,
                null,
                idempotencyKey,
                dto,
                List.of(new RecordHeader(KafkaUtils.IDEMPOTENCY_KEY_HEADER,
                        idempotencyKey.getBytes())));
        kafkaTemplate.send(record).get();
    }
}
