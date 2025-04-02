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

@Service
@Slf4j
public class KafkaConsumerService {
    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;

    public KafkaConsumerService(OrderService orderService, StringRedisTemplate redisTemplate) {
        this.orderService = orderService;
        this.redisTemplate = redisTemplate;
    }

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
     * Handles the case where the idempotency key already exists in Redis.
     * 
     * @param idempotencyKey The idempotency key.
     * @param redisKey       The Redis key.
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
