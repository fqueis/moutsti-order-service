package info.mouts.orderservice.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Represents an order in the system.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "items")
@EqualsAndHashCode(exclude = "items")
public class Order {
    private UUID id;

    @NotBlank(message = "Idempotency key cannot be blank")
    private String idempotencyKey;

    @NotNull(message = "Order status cannot be null")
    private OrderStatus status;

    @NotNull
    @Size(min = 1, message = "Order must have at least one item")
    @Builder.Default
    private List<OrderItem> items = new ArrayList<OrderItem>();

    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * Adds an {@link OrderItem} to the order's item list and sets the bidirectional
     * relationship.
     *
     * @param item The {@link OrderItem} instance to add.
     */
    public void addItem(OrderItem item) {
        this.items.add(item);
        item.setOrder(this);

    }
}