package info.mouts.orderservice.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import info.mouts.orderservice.domain.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
@Relation(collectionRelation = "orders", itemRelation = "order")
@Schema(description = "Detailed information about a processed order")
public class OrderResponseDTO extends RepresentationModel<OrderResponseDTO> {
    @Schema(description = "Internal unique identifier of the order (UUID)", example = "a1b2c3d4-e5f6-7890-1234-567890abcdef")
    private UUID id;

    @Schema(description = "Idempotency key provided by the external system A", example = "f95b3574-10c9-4698-8edd-f651c14a2592")
    private String idempotencyKey;

    @Schema(description = "Current status of the order", example = "PROCESSED")
    private OrderStatus status;

    @Schema(description = "Total amount of the order", example = "100.00")
    private BigDecimal total;

    @Schema(description = "Timestamp when the order was created", example = "2023-01-01T12:00:00Z")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp when the order was processed", example = "2023-01-01T12:00:00Z")
    private LocalDateTime processedAt;
}
