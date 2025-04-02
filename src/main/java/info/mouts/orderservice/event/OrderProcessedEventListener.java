package info.mouts.orderservice.event;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.dto.OrderProcessedEventDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class OrderProcessedEventListener {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.orders-processed-topic}")
    private String ordersProcessedTopic;

    @Autowired
    private OrderMapper orderMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderProcessed(OrderProcessedEvent event) {
        Order processedOrder = event.getProcessedOrder();

        log.info("Received order processed event for Order ID: {}", processedOrder.getId());
        OrderProcessedEventDTO orderProcessedEventDTO = orderMapper.toProcessedEventDto(processedOrder);

        String orderId = processedOrder.getId().toString();
        try {
            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(ordersProcessedTopic, orderId,
                    orderProcessedEventDTO);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info(
                            "Successfully published a processed order event to topic {} for Order ID {}",
                            ordersProcessedTopic, orderId);
                } else {
                    // TODO: create a simple outbox pattern to save the event to the database
                    log.error("Failed to publish a processed order event to topic {} for Order ID {}: {}",
                            ordersProcessedTopic, orderId, ex.getMessage(), ex);
                }
            });
        } catch (Exception e) {
            log.error("Exception during sending a processed order event to topic {} for Order ID {}: {}",
                    ordersProcessedTopic, orderId, e.getMessage(), e);

        }
    }

}