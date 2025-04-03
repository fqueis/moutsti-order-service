package info.mouts.orderservice.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.exception.OrderItemNotFoundException;
import info.mouts.orderservice.repository.OrderItemRepository;
import info.mouts.orderservice.service.OrderItemService;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link OrderItemService} interface.
 * Provides functionality to retrieve order items.
 * Includes caching capabilities.
 */
@Service
@Slf4j
public class OrderItemServiceImpl implements OrderItemService {

    private final OrderItemRepository orderItemRepository;

    /**
     * Constructs an instance of {@code OrderItemServiceImpl}.
     *
     * @param orderItemRepository The repository for order item data access.
     */
    public OrderItemServiceImpl(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    /**
     * Finds all order items associated with a specific order ID.
     * Uses caching based on the order ID.
     *
     * @param orderId The UUID of the order.
     * @return A {@link List} of {@link OrderItem} entities associated with the
     *         given order ID.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order::items", key = "#orderId")
    public List<OrderItem> findOrderItemsByOrderId(UUID orderId) {
        log.info("Cache miss, attempting to find order items for the order with ID: {}", orderId);

        return orderItemRepository.findByOrder_Id(orderId);
    }

    /**
     * Finds a specific order item by its unique identifier (UUID).
     * Uses caching based on the item ID.
     *
     * @param itemId The UUID of the order item to find.
     * @return The {@link OrderItem} entity if found.
     * @throws OrderItemNotFoundException If no order item is found with the given
     *                                    ID.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order::item", key = "#itemId")
    public OrderItem findById(UUID itemId) {
        log.info("Cache miss, attempting to find order item by ID from database: {}", itemId);

        return orderItemRepository.findById(itemId).orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

    /**
     * Finds a specific order item by its unique identifier (UUID) and validates
     * whether it belongs to the parent order with the given ID.
     * Uses caching based on the item ID.
     *
     * @param orderId The UUID of the expected parent order
     * @param itemId  The UUID of the order item to find.
     * @return The {@link OrderItem} entity if found.
     * @throws OrderItemNotFoundException If no order item is found with the given
     *                                    ID.
     */
    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order::item", key = "#orderId + '_' + #itemId")
    public OrderItem findByOrderIdAndItemId(UUID orderId, UUID itemId) {
        log.debug("Cache miss, attempting to find order item by ID: {} for order ID: {}", itemId, orderId);

        OrderItem item = orderItemRepository.findById(itemId)
                .orElseThrow(() -> {
                    log.warn("Order item not found for ID: {}", itemId);
                    return new OrderItemNotFoundException(orderId, itemId);
                });

        if (item.getOrder() == null || !item.getOrder().getId().equals(orderId)) {
            log.warn("Order item {} found, but it does not belong to order {}", itemId, orderId);
            throw new OrderItemNotFoundException(orderId, itemId);
        }

        log.info("Order item {} found for order {}", itemId, orderId);
        return item;
    }

}
