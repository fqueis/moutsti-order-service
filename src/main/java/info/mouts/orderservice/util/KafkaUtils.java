package info.mouts.orderservice.util;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderItemRequestDTO;
import info.mouts.orderservice.dto.OrderRequestDTO;

public class KafkaUtils {
    public static final String IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    public static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:order:";

    public static final String PROCESSING_STATUS = OrderStatus.PROCESSING.name();
    public static final String PROCESSED_STATUS = OrderStatus.PROCESSED.name();

    public static final Duration PROCESSING_TTL = Duration.ofHours(1);
    public static final Duration PROCESSED_TTL = Duration.ofDays(1);

    public static OrderRequestDTO createFakeOrderRequestDTO(String productId, int quantity) {
        OrderRequestDTO dto = new OrderRequestDTO();

        OrderItemRequestDTO item = new OrderItemRequestDTO();

        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(BigDecimal.TEN);

        dto.setItems(List.of(item));

        return dto;
    }
}
