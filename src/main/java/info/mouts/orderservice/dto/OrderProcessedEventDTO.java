package info.mouts.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import info.mouts.orderservice.domain.OrderStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderProcessedEventDTO {
    private UUID orderId;
    private OrderStatus status;
    private BigDecimal total;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private LocalDateTime processedAt;
    private List<OrderItemEventDTO> items;
}