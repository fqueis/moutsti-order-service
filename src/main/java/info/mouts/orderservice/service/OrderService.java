package info.mouts.orderservice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.dto.OrderRequestDTO;

public interface OrderService {
    /**
     * Processes an incoming order request received from Kafka.
     * Performs mapping, calculation, status updates, and persistence.
     *
     * @param request        The {@link OrderRequestDTO} instance containing the
     *                       order data received.
     * @param idempotencyKey The idempotency key from the Kafka message header.
     * @return The processed and saved {@link Order} entity.
     */
    Order processIncomingOrder(OrderRequestDTO request, String idempotencyKey);

    /**
     * Finds an order by its unique ID.
     *
     * @param orderId The UUID of the order to find.
     * @return The found Order entity.
     * @throws OrderNotFoundException if no order exists with the given ID.
     */
    Order findByOrderId(UUID orderId);

    /**
     * Finds all orders.
     *
     * @return A list of all orders.
     */
    List<Order> findAll();

    /**
     * Finds all orders with pagination.
     *
     * @param pageable The pagination information.
     * @return A page of orders.
     */
    Page<Order> findAll(Pageable pageable);
}
