package info.mouts.orderservice.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.event.OrderProcessedEvent;
import info.mouts.orderservice.exception.OrderNotFoundException;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;
import info.mouts.orderservice.service.OrderService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@CacheConfig(cacheNames = "order")
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private Counter ordersReceivedCounter;
    private Counter ordersProcessedCounter;
    private Counter ordersFailedCounter;
    private Timer orderProcessingTimer;

    public OrderServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper,
            ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;

        initializeMetrics(this.meterRegistry);
    }

    @Override
    @Transactional
    @CachePut(key = "#result.id")
    public Order processIncomingOrder(OrderRequestDTO request, String idempotencyKey) {
        this.ordersReceivedCounter.increment();

        return this.orderProcessingTimer.record(() -> {
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

                log.debug("Publishing a processed order event for Order ID: {}", savedOrder.getId());

                this.ordersProcessedCounter.increment();

                OrderProcessedEvent event = new OrderProcessedEvent(this, savedOrder);
                eventPublisher.publishEvent(event);

                log.debug("Processed order event published for Order ID: {}", savedOrder.getId());

                return savedOrder;
            } catch (DataIntegrityViolationException e) {
                log.error("Data integrity violation while saving order for key {}: {}", idempotencyKey, e.getMessage());
                this.ordersFailedCounter.increment();
                throw e;
            } catch (Exception e) {
                log.error("Failed to save processed order for key {}: {}", idempotencyKey, e.getMessage(), e);
                this.ordersFailedCounter.increment();
                throw e;
            }
        });

    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(key = "#orderId")
    public Order findByOrderId(UUID orderId) {
        log.info("Cache miss, attempting to find order by ID from database: {}", orderId);

        return orderRepository.findById(orderId)
                .orElseThrow(() -> {
                    log.warn("Order not found for ID: {}", orderId);
                    return new OrderNotFoundException(orderId);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        log.debug("Attempting to find all orders");

        return orderRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        log.debug("Attempting to find all orders with pagination: {}", pageable);

        return orderRepository.findAll(pageable);
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

    /**
     * Initializes the metrics for the order service.
     * 
     * @param registry The meter registry to register the metrics.
     */
    private void initializeMetrics(MeterRegistry registry) {
        this.ordersReceivedCounter = Counter.builder("orders.received")
                .description("Total number of orders received from Kafka")
                .register(registry);
        this.ordersProcessedCounter = Counter.builder("orders.processed")
                .description("Total number of orders successfully processed")
                .register(registry);
        this.ordersFailedCounter = Counter.builder("orders.failed")
                .description("Total number of orders failed during processing (before DLT)")
                .tag("reason", "processing_exception")
                .register(registry);
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
                .description("Time taken to process an incoming order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
