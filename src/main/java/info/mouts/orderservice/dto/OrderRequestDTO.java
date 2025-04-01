package info.mouts.orderservice.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class OrderRequestDTO {
    @NotNull(message = "Item list cannot be null in order request")
    @NotEmpty(message = "Order must have at least one item in order request")
    @Valid
    private List<OrderItemRequestDTO> items;

}