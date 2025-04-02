package info.mouts.orderservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import info.mouts.orderservice.domain.OrderItem;

/**
 * Repository interface for managing {@link OrderItem} entities.
 */
@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    /**
     * Finds all order items by the given order ID.
     * 
     * @param orderId The ID of the order to find items for
     * @return A list of order items associated with the given order ID
     */
    List<OrderItem> findByOrder_Id(UUID orderId);
}