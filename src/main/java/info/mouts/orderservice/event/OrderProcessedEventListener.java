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

/**
 * Event listener component that handles {@link OrderProcessedEvent}.
 * This listener is triggered after the transaction that processed the order is
 * successfully committed.
 * It converts the processed order details into a DTO and publishes it to a
 * Kafka topic.
 */
@Component
@Slf4j
public class OrderProcessedEventListener {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.orders-processed-topic}")
    private String ordersProcessedTopic;

    @Autowired
    private OrderMapper orderMapper;

    /**
     * Listens for {@link OrderProcessedEvent} after the originating transaction
     * commits.
     * Maps the {@link Order} from the event to an {@link OrderProcessedEventDTO}.
     * Sends the DTO to the configured Kafka topic {@link #ordersProcessedTopic}
     * asynchronously using {@link KafkaTemplate}.
     * Logs the outcome (success or failure) of the Kafka send operation.
     *
     * @param event The {@link OrderProcessedEvent} containing the processed order
     *              details.
     */
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