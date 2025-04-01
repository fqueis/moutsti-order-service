package info.mouts.orderservice.kafka;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import info.mouts.orderservice.dto.OrderRequestDTO;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class KafkaConsumerService {
    private static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";

    @KafkaListener(topics = "${app.kafka.orders-received-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void listen(@Payload OrderRequestDTO orderRequestDTO,
            @Header(name = IDEMPOTENCY_KEY_HEADER, required = true) String idempotencyKey) {
        log.info("Received order request: {} with idempotency key: {}", orderRequestDTO, idempotencyKey);
    }
}
