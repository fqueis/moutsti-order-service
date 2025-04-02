package info.mouts.orderservice.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class OrderItemEventDTO {
    private String productId;
    private Integer quantity;
    private BigDecimal price;
}