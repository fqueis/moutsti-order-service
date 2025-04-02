package info.mouts.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

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
@Relation(collectionRelation = "orderItems", itemRelation = "orderItem")
@Schema(description = "Details of an item within an order response")
public class OrderItemResponseDTO extends RepresentationModel<OrderItemResponseDTO> {
    @Schema(description = "Internal unique identifier of the order item (UUID)", example = "731b2542-336a-47c7-80f1-4954d07de128")
    private UUID id;

    @Schema(description = "Identifier for the product from the external system A", example = "EXT-PROD-12345")
    private String productId;

    @Schema(description = "Number of units ordered for this product", example = "10")
    private Integer quantity;

    @Schema(description = "Price per unit of the product", example = "100.00")
    private BigDecimal price;
}
