package info.mouts.orderservice.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderItemRequestDTO {

    @NotBlank(message = "Product ID cannot be blank in item request")
    private String productId;

    @NotNull(message = "Quantity cannot be null in item request")
    @Min(value = 1, message = "Quantity must be at least 1 in item request")
    private Integer quantity;

    @NotNull(message = "Price cannot be null in item request")
    @DecimalMin(value = "0.01", message = "Price must be at least 0.01 in item request")
    private BigDecimal price;
}
