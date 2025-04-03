package info.mouts.orderservice.kafka;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.dao.DataAccessException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;
import info.mouts.orderservice.util.KafkaUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

/**
 * Service responsible for consuming messages from the Dead Letter Topic (DLT).
 * When a message processing fails repeatedly in the main consumer, it ends up
 * here.
 * This service attempts to mark the corresponding order as FAILED in the
 * database,
 * either by updating an existing record or creating a new one if necessary.
 * It also collects metrics related to DLT processing.
 */
@Service
@Slf4j
public class DltConsumerService {
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;
    private final MeterRegistry meterRegistry;

    private Counter dltMessagesReceivedCounter;
    private Counter dltOrdersMarkedFailedCounter;
    private Counter dltDbErrorsCounter;
    private Counter dltProcessingErrorsCounter;
    private Timer dltProcessingTimer;

    /**
     * Constructs an instance of {@code DltConsumerService}.
     *
     * @param orderRepository The repository for order data access.
     * @param objectMapper    The Jackson object mapper for deserialization.
     * @param orderMapper     The mapper for converting DTOs to entities.
     * @param meterRegistry   The registry for collecting metrics.
     */
    public DltConsumerService(OrderRepository orderRepository, ObjectMapper objectMapper, OrderMapper orderMapper,
            MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.objectMapper = objectMapper;
        this.orderMapper = orderMapper;
        this.meterRegistry = meterRegistry;

        initializeMetrics(meterRegistry);
    }

    /**
     * Kafka listener method for the DLT topic.
     * Receives records that failed processing in the main topic listener.
     * Extracts idempotency key and failure reason from headers, attempts to
     * deserialize
     * the payload, and calls the logic to mark the corresponding order as failed.
     * Records metrics for received messages, processing time, and errors.
     *
     * @param consumerRecord The Kafka consumer record containing the failed message
     *                       payload and headers.
     */
    @KafkaListener(topics = "${app.kafka.dlt-orders-topic}", groupId = "${spring.kafka.consumer.group-id}-dlt", containerFactory = "dltKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, byte[]> consumerRecord) {
        dltMessagesReceivedCounter.increment();

        try {
            dltProcessingTimer.record(() -> {
                log.error("Received a message from DLT to process");

                String idempotencyKey = getIdempotencyKeyFromHeaders(consumerRecord);

                if (idempotencyKey == null) {
                    log.error(
                            "Cannot attempt to mark order as failed because idempotency key was not found in DLT message headers.");
                    return;
                }

                OrderRequestDTO dto = tryToDeserializePayload(consumerRecord.value());
                String failureReason = getFailureReasonFromHeaders(consumerRecord.headers());
                tryToMarkOrderAsFailed(idempotencyKey, dto, failureReason);
            });
        } catch (Exception e) {
            log.error(
                    "CRITICAL FAILURE processing DLT message (outside timed block or rethrown). Record: {}, Error: {}",
                    consumerRecord, e.getMessage(), e);
            dltProcessingErrorsCounter.increment();
        } finally {
            log.debug("Finished processing DLT message for key {}", consumerRecord.key());
        }
    }

    /**
     * Extracts the idempotency key from the Kafka message headers.
     *
     * @param record The Kafka consumer record.
     * @return The idempotency key as a String, or null if the header is not found
     *         or has no value.
     */
    private String getIdempotencyKeyFromHeaders(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaUtils.IDEMPOTENCY_KEY_HEADER);

        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }

    /**
     * Attempts to deserialize the message payload byte array into an
     * {@link OrderRequestDTO}.
     *
     * @param payload The message payload as a byte array.
     * @return The deserialized {@link OrderRequestDTO}, or null if the payload is
     *         null or deserialization fails.
     */
    private OrderRequestDTO tryToDeserializePayload(byte[] payload) {
        if (payload == null)
            return null;

        try {
            return objectMapper.readValue(payload, OrderRequestDTO.class);
        } catch (Exception e) {
            log.error("Could not deserialize DLT message payload: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Attempts to mark an order as FAILED in the database based on the idempotency
     * key. This method is transactional and handles various scenarios for order
     * failure processing.
     * 
     * <p>
     * The method follows these steps:
     * </p>
     * 
     * <ol>
     * <li>Attempts to find an existing order using the idempotency key</li>
     * <li>If the order is found:
     * <ul>
     * <li>If status is RECEIVED or PROCESSING, updates to FAILED with failure
     * reason</li>
     * <li>If status is already terminal (FAILED) or completed, logs a warning</li>
     * </ul>
     * </li>
     * <li>If the order is not found:
     * <ul>
     * <li>If DTO was successfully parsed, creates new Order with FAILED status</li>
     * <li>If DTO parsing failed, logs an error</li>
     * </ul>
     * </li>
     * </ol>
     * 
     * <p>
     * The method records metrics for:
     * <ul>
     * <li>Successful updates/creations</li>
     * <li>Database errors</li>
     * <li>Processing errors</li>
     * </ul>
     * </p>
     *
     * @param idempotencyKey The idempotency key of the order to mark as failed
     * @param dto            The deserialized OrderRequestDTO (may be null if
     *                       deserialization failed)
     * @param failureReason  The reason for the failure, extracted from DLT headers
     */
    @Transactional
    void tryToMarkOrderAsFailed(String idempotencyKey, OrderRequestDTO dto, String failureReason) {
        try {
            // Try to find the order by idempotency key first
            Optional<Order> existingOrderOpt = orderRepository.findByIdempotencyKey(idempotencyKey);

            if (existingOrderOpt.isPresent()) {
                Order order = existingOrderOpt.get();

                if (order.getStatus() == OrderStatus.RECEIVED || order.getStatus() == OrderStatus.PROCESSING) {
                    order.setStatus(OrderStatus.FAILED);
                    order.setFailureReason(failureReason);

                    orderRepository.save(order);
                    log.info("Marked existing order with key {} as FAILED.", idempotencyKey);
                    dltOrdersMarkedFailedCounter.increment();
                } else {
                    log.warn(
                            "Order with key {} already in terminal status {} or completed status {}. Not marking as FAILED.",
                            idempotencyKey, OrderStatus.FAILED, order.getStatus());
                }
            } else if (dto != null) {
                log.warn("Order with key {} not found in database. Creating new record with FAILED status.",
                        idempotencyKey);

                Order failedOrder = orderMapper.toEntity(dto);

                failedOrder.setIdempotencyKey(idempotencyKey);
                failedOrder.setStatus(OrderStatus.FAILED);
                failedOrder.setTotal(BigDecimal.ZERO);
                failedOrder.setFailureReason(failureReason);

                // Associate items, if there are any
                if (failedOrder.getItems() != null) {
                    failedOrder.getItems().forEach(item -> item.setOrder(failedOrder));
                }

                orderRepository.save(failedOrder);
                log.info("Created new order record with key {} in FAILED status.", idempotencyKey);
                dltOrdersMarkedFailedCounter.increment();
            } else {
                // Do not find an existing order and we could not parse the DTO. Only log.
                log.error("Order with key {} not found and DTO could not be parsed. Cannot update status.",
                        idempotencyKey);
            }
        } catch (DataAccessException dbEx) {
            log.error("Database error while trying to mark order key {} as FAILED: {}", idempotencyKey,
                    dbEx.getMessage(), dbEx);
            dltDbErrorsCounter.increment();
        } catch (Exception ex) {
            log.error("Unexpected error while trying to mark order key {} as FAILED: {}", idempotencyKey,
                    ex.getMessage(), ex);
            dltProcessingErrorsCounter.increment();
        }
    }

    /**
     * Extracts a failure reason string from Kafka DLT headers.
     * Prefers the exception message, optionally prefixed by the exception class
     * name (FQCN).
     * Falls back to FQCN if message is missing, or a default message if both are
     * missing.
     *
     * @param headers The Kafka message headers.
     * @return A descriptive failure reason string.
     */
    private String getFailureReasonFromHeaders(Headers headers) {
        String exceptionMessage = getHeaderValue(headers, KafkaUtils.DLT_EXCEPTION_MESSAGE_HEADER);
        String exceptionFqcn = getHeaderValue(headers, KafkaUtils.DLT_EXCEPTION_FQCN_HEADER);

        if (exceptionMessage != null && !exceptionMessage.isBlank()) {
            String reason = exceptionMessage;

            if (exceptionFqcn != null) {
                reason = exceptionFqcn + ": " + reason;
            }
            return reason;

        } else if (exceptionFqcn != null) {
            return exceptionFqcn;
        } else {
            return "Unknown DLT Failure (Missing Exception Headers)";
        }
    }

    /**
     * Helper method to retrieve the value of a specific header as a UTF-8 String.
     *
     * @param headers   The Kafka message headers.
     * @param headerKey The key of the header to retrieve.
     * @return The header value as a String, or null if the header is not found or
     *         has no value.
     */
    private String getHeaderValue(Headers headers, String headerKey) {
        Header header = headers.lastHeader(headerKey);

        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }

        return null;
    }

    /**
     * Initializes the Micrometer metrics for the DLT consumer service.
     * Registers counters for received messages, orders marked as failed, DB errors,
     * and general processing errors, as well as a timer for DLT message processing
     * duration.
     *
     * @param registry The meter registry to register the metrics with.
     */
    private void initializeMetrics(MeterRegistry registry) {
        this.dltMessagesReceivedCounter = Counter.builder("orders.dlt.messages.received")
                .description("Total number of messages received on the DLT")
                .register(meterRegistry);
        this.dltOrdersMarkedFailedCounter = Counter.builder("orders.dlt.orders.marked.failed")
                .description("Total number of orders successfully marked as FAILED from DLT")
                .register(meterRegistry);
        this.dltDbErrorsCounter = Counter.builder("orders.dlt.db.errors")
                .description("Total number of database errors during DLT processing")
                .register(meterRegistry);
        this.dltProcessingErrorsCounter = Counter.builder("orders.dlt.processing.errors")
                .description("Total number of unexpected errors during DLT processing")
                .register(meterRegistry);
        this.dltProcessingTimer = Timer.builder("orders.dlt.processing.time")
                .description("Time taken to process a DLT message")
                .publishPercentiles(0.5, 0.95)
                .register(meterRegistry);
    }
}
