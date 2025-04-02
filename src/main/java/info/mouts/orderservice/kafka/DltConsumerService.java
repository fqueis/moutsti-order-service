package info.mouts.orderservice.kafka;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.beans.factory.annotation.Autowired;
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
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DltConsumerService {
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderMapper orderMapper;

    @KafkaListener(topics = "${app.kafka.dlt-orders-topic}", groupId = "${spring.kafka.consumer.group-id}-dlt", containerFactory = "dltKafkaListenerContainerFactory")
    public void listen(ConsumerRecord<String, byte[]> consumerRecord) {
        try {
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
        } catch (Exception e) {
            log.error("CRITICAL FAILURE processing DLT message. Message will be skipped. Record: {}, Error: {}",
                    consumerRecord, e.getMessage(), e);
        }
    }

    private String getIdempotencyKeyFromHeaders(ConsumerRecord<?, ?> record) {
        Header header = record.headers().lastHeader(KafkaUtils.IDEMPOTENCY_KEY_HEADER);

        if (header != null && header.value() != null) {
            return new String(header.value());
        }
        return null;
    }

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
            } else {
                // Do not find an existing order and we could not parse the DTO. Only log.
                log.error("Order with key {} not found and DTO could not be parsed. Cannot update status.",
                        idempotencyKey);
            }
        } catch (DataAccessException dbEx) {
            log.error("Database error while trying to mark order key {} as FAILED: {}", idempotencyKey,
                    dbEx.getMessage(), dbEx);
        } catch (Exception ex) {
            log.error("Unexpected error while trying to mark order key {} as FAILED: {}", idempotencyKey,
                    ex.getMessage(), ex);
        }
    }

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

    private String getHeaderValue(Headers headers, String headerKey) {
        Header header = headers.lastHeader(headerKey);

        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }

        return null;
    }
}
