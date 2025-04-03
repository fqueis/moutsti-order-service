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
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link OrderService} interface.
 * Handles the business logic related to order processing, retrieval, and
 * management.
 * Includes caching and metric collection functionalities.
 */
@Service
@Slf4j
@CacheConfig(cacheNames = "order")
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;

    private Timer orderProcessingTimer;

    /**
     * Constructs an instance of {@code OrderServiceImpl}.
     *
     * @param orderRepository The repository for order data access.
     * @param orderMapper     The mapper for converting between DTOs and entities.
     * @param eventPublisher  The application event publisher for order events.
     * @param meterRegistry   The registry for collecting metrics.
     */
    public OrderServiceImpl(OrderRepository orderRepository, OrderMapper orderMapper,
            ApplicationEventPublisher eventPublisher, MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderMapper = orderMapper;
        this.eventPublisher = eventPublisher;
        this.meterRegistry = meterRegistry;

        initializeMetrics(this.meterRegistry);
    }

    /**
     * Processes an incoming order request, saves it, and publishes an event.
     * This method is transactional and caches the resulting order by its ID.
     * Metrics are recorded for received, processed, and failed orders, as well as
     * processing time.
     *
     * @param request        The {@link OrderRequestDTO} containing the order
     *                       details.
     * @param idempotencyKey A unique key to ensure idempotency of the order
     *                       processing.
     * @return The saved {@link Order} entity after processing.
     * @throws IllegalArgumentException        If the order request does not contain
     *                                         items.
     * @throws DataIntegrityViolationException If there's a data integrity issue
     *                                         during persistence (e.g., duplicate
     *                                         idempotency key).
     */
    @Override
    @Transactional
    @CachePut(key = "#result.id")
    public Order processIncomingOrder(OrderRequestDTO request, String idempotencyKey) {
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

                OrderProcessedEvent event = new OrderProcessedEvent(this, savedOrder);
                eventPublisher.publishEvent(event);

                log.debug("Processed order event published for Order ID: {}", savedOrder.getId());

                return savedOrder;
            } catch (DataIntegrityViolationException e) {
                log.error("Data integrity violation while saving order for key {}: {}", idempotencyKey, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Failed to save processed order for key {}: {}", idempotencyKey, e.getMessage(), e);
                throw e;
            }
        });
    }

    /**
     * Finds an order by its unique identifier (UUID).
     * Uses caching to improve performance. If the order is not found in the cache,
     * it retrieves it from the database.
     *
     * @param orderId The UUID of the order to find.
     * @return The {@link Order} entity if found.
     * @throws OrderNotFoundException If no order is found with the given ID.
     */
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

    /**
     * Retrieves all orders from the database.
     * Note: This might be inefficient for large datasets. Consider using
     * pagination.
     *
     * @return A {@link List} of all {@link Order} entities.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Order> findAll() {
        log.debug("Attempting to find all orders");

        return orderRepository.findAll();
    }

    /**
     * Retrieves a paginated list of all orders from the database.
     *
     * @param pageable The pagination information (page number, size, sort).
     * @return A {@link Page} containing the {@link Order} entities for the
     *         requested page.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<Order> findAll(Pageable pageable) {
        log.debug("Attempting to find all orders with pagination: {}", pageable);

        return orderRepository.findAll(pageable);
    }

    /**
     * Calculates the total amount for the given order based on its items.
     * Sums the product of price and quantity for each item.
     *
     * @param order The order entity (must have items loaded).
     * @return The calculated total amount as a {@link BigDecimal}. Returns
     *         {@code BigDecimal.ZERO} if the order or its items are null/empty.
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
     * Initializes the Micrometer metrics for the order service.
     * Registers a timer for processing duration.
     *
     * @param registry The meter registry to register the metrics with.
     */
    private void initializeMetrics(MeterRegistry registry) {
        this.orderProcessingTimer = Timer.builder("orders.processing.time")
                .description("Time taken to process an incoming order")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }
}
