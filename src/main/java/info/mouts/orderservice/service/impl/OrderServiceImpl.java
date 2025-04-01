package info.mouts.orderservice.service.impl;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;
import info.mouts.orderservice.service.OrderService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
    }

    @Override
    @Transactional
    public Order processIncomingOrder(OrderRequestDTO request, String idempotencyKey) {
        log.info("Processing incoming order for idempotency key: {}", idempotencyKey);

        Order order = orderMapper.toEntity(request);
        order.setStatus(OrderStatus.RECEIVED);
        order.setIdempotencyKey(idempotencyKey);

        if (order.getItems() != null && !order.getItems().isEmpty()) {
            order.getItems().forEach(item -> item.setOrder(order));
        } else {
            log.error("Order with key {} has no items after mapping!", idempotencyKey);
            throw new IllegalArgumentException("Order must contain items.");
        }

        order.setStatus(OrderStatus.PROCESSING);
        log.info("Order status changed to PROCESSING for key {}", idempotencyKey);

        BigDecimal totalAmount = calculateTotalAmount(order);
        order.setTotal(totalAmount);
        log.info("Calculated total amount {} for order key {}", totalAmount, idempotencyKey);

        order.setStatus(OrderStatus.PROCESSED);
        log.info("Order status changed to PROCESSED for key {}", idempotencyKey);

        try {
            Order savedOrder = orderRepository.save(order);
            log.info("Order successfully processed and saved with ID {} for key {}", savedOrder.getId(),
                    idempotencyKey);

            return savedOrder;
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while saving order for key {}: {}", idempotencyKey, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Failed to save processed order for key {}: {}", idempotencyKey, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Calculates the total amount for the given order based on its items.
     * 
     * @param order The order entity (must have items loaded).
     * @return The calculated total amount.
     */
    private BigDecimal calculateTotalAmount(Order order) {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        return order.getItems().stream()
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
