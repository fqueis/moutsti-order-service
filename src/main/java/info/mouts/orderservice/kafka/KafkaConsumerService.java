package info.mouts.orderservice.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.service.OrderService;
import info.mouts.orderservice.util.KafkaUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for consuming messages from the main Kafka orders topic.
 * It handles incoming order requests, ensures idempotency using Redis,
 * and delegates the actual order processing to the {@link OrderService}.
 */
@Service
@Slf4j
public class KafkaConsumerService {
    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs an instance of {@code KafkaConsumerService}.
     *
     * @param orderService  The service responsible for processing order logic.
     * @param redisTemplate The Spring Redis template for interacting with Redis
     *                      (used for idempotency).
     */
    public KafkaConsumerService(OrderService orderService, StringRedisTemplate redisTemplate) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Kafka listener method for the main orders topic.
     * Receives {@link OrderRequestDTO} messages.
     * Performs an idempotency check using Redis based on the provided idempotency
     * key header.
     * If the key is new it delegates to
     * {@link OrderService#processIncomingOrder}.
     * If the key indicates the message is already being processed or has been
     * processed, it skips the processing.
     *
     * @param orderRequestDTO The deserialized order request payload.
     * @param idempotencyKey  The idempotency key extracted from the message headers
     *                        (mandatory).
     * @throws Exception If an error occurs during order processing (re-thrown to
     *                   trigger Kafka retries/DLT).
     */
    @KafkaListener(topics = "${app.kafka.orders-received-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload OrderRequestDTO orderRequestDTO,
            @Header(name = KafkaUtils.IDEMPOTENCY_KEY_HEADER, required = true) String idempotencyKey) {
        log.info("Received incoming order request to process");

        String redisKey = KafkaUtils.IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(redisKey,
                KafkaUtils.PROCESSING_STATUS, KafkaUtils.PROCESSING_TTL);

        if (Boolean.FALSE.equals(lockAcquired)) {
            handleExistingKey(idempotencyKey, redisKey);
            return;
        }

        log.info("Idempotency key {} acquired. Starting processing...",
                idempotencyKey);

        try {
            orderService.processIncomingOrder(orderRequestDTO, idempotencyKey);
            redisTemplate.opsForValue().set(redisKey, KafkaUtils.PROCESSED_STATUS,
                    KafkaUtils.PROCESSED_TTL);
        } catch (Exception e) {
            log.error("Error processing message for idempotency key {}: {}", idempotencyKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Handles the scenario when an idempotency key is already found in Redis during
     * the initial check.
     * Logs the current status (PROCESSING or PROCESSED) and determines whether to
     * skip the current message.
     *
     * @param idempotencyKey The idempotency key that already exists.
     * @param redisKey       The corresponding key used in Redis (prefix +
     *                       idempotencyKey).
     */
    private void handleExistingKey(String idempotencyKey, String redisKey) {
        String currentStatus = redisTemplate.opsForValue().get(redisKey);
        log.warn("Idempotency key {} already exists in Redis with status: {}", idempotencyKey, currentStatus);

        if (KafkaUtils.PROCESSED_STATUS.equals(currentStatus)) {
            // The order was already processed, so we can skip processing it again.
            log.info("Order with idempotency key {} already processed, skipping", idempotencyKey);
        } else if (KafkaUtils.PROCESSING_STATUS.equals(currentStatus)) {
            // Another instance might be processing or the previous one failed.
            // Logging and NOT processing again here avoids duplicate processing.
            // The instance that originally set the key (or its retries) is responsible.
            log.warn("Skipping processing for key {} as it is already marked as PROCESSING.", idempotencyKey);
        } else {
            // This is an unexpected status, so we skip processing it.
            log.error("Skipping processing for key {} due to unexpected status in Redis: {}", idempotencyKey,
                    currentStatus);
        }
    }
}
