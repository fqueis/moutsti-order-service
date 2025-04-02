package info.mouts.orderservice.exception;

import java.util.UUID;

public class OrderItemNotFoundException extends RuntimeException {
    public OrderItemNotFoundException(UUID itemId) {
        super("Order item not found for ID: " + itemId);
    }
}
