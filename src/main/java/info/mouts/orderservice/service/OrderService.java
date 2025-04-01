package info.mouts.orderservice.service;

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

}
