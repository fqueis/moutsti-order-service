package info.mouts.orderservice.service;

import java.util.List;
import java.util.UUID;
import info.mouts.orderservice.domain.OrderItem;

public interface OrderItemService {
    /**
     * Finds all order items for a given order ID.
     * 
     * @param orderId The ID of the order to find items for.
     * @return A list of all order items for the given order ID.
     */
    List<OrderItem> findOrderItemsByOrderId(UUID orderId);

    /**
     * Finds an order item by its unique ID.
     * 
     * @param itemId The ID of the order item to find.
     * @return The order item with the given ID.
     */
    OrderItem findById(UUID itemId);
}
