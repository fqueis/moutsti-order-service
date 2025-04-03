package info.mouts.orderservice.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderItemRequestDTO;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.exception.OrderNotFoundException;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
public class OrderServiceImplTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private OrderServiceImpl orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private OrderRequestDTO orderRequestDTO;
    private Order mappedOrder;
    private Order savedOrder;

    private final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    @BeforeEach
    public void setUp() {
        orderService = new OrderServiceImpl(orderRepository, orderMapper, eventPublisher, meterRegistry);

        orderRequestDTO = new OrderRequestDTO();
        OrderItemRequestDTO itemDTO1 = new OrderItemRequestDTO();
        itemDTO1.setProductId("prod-1");
        itemDTO1.setQuantity(2);
        itemDTO1.setPrice(BigDecimal.valueOf(10.50));

        OrderItemRequestDTO itemDTO2 = new OrderItemRequestDTO();
        itemDTO2.setProductId("prod-2");
        itemDTO2.setQuantity(1);
        itemDTO2.setPrice(BigDecimal.valueOf(5.25));
        orderRequestDTO.setItems(Arrays.asList(itemDTO1, itemDTO2));

        mappedOrder = new Order();

        OrderItem mappedItem1 = OrderItem.builder()
                .productId("prod-1")
                .quantity(2)
                .price(BigDecimal.valueOf(10.50))
                .build();

        OrderItem mappedItem2 = OrderItem.builder()
                .productId("prod-2")
                .quantity(1)
                .price(BigDecimal.valueOf(5.25))
                .build();

        List<OrderItem> items = new ArrayList<OrderItem>(Arrays.asList(mappedItem1, mappedItem2));

        mappedOrder.setItems(items);
        items.forEach(item -> item.setOrder(mappedOrder));

        when(orderMapper.toEntity(any(OrderRequestDTO.class))).thenReturn(mappedOrder);

        savedOrder = new Order();
        savedOrder.setId(UUID.randomUUID());
        savedOrder.setIdempotencyKey(IDEMPOTENCY_KEY);
        savedOrder.setStatus(OrderStatus.PROCESSED);
        savedOrder.setTotal(BigDecimal.valueOf(26.25));
        savedOrder.setItems(mappedOrder.getItems());

        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);
    }

    @Test
    @DisplayName("Should process incoming order, calculate total, set status, and save")
    void processIncomingOrder_success() {
        Order result = orderService.processIncomingOrder(orderRequestDTO, IDEMPOTENCY_KEY);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(savedOrder.getId());

        verify(orderMapper, times(1)).toEntity(orderRequestDTO);

        verify(orderRepository, times(1)).save(orderCaptor.capture());

        Order orderToSave = orderCaptor.getValue();
        assertThat(orderToSave).isNotNull();
        assertThat(orderToSave.getIdempotencyKey()).isEqualTo(IDEMPOTENCY_KEY);
        assertThat(orderToSave.getStatus()).isEqualTo(OrderStatus.PROCESSED);
        assertThat(orderToSave.getTotal()).isEqualByComparingTo(BigDecimal.valueOf(26.25));
        assertThat(orderToSave.getItems()).hasSize(2);

        assertThat(orderToSave.getItems().getFirst().getOrder()).isEqualTo(orderToSave);
        assertThat(orderToSave.getItems().getLast().getOrder()).isEqualTo(orderToSave);
    }

    @Test
    @DisplayName("Should throw exception if mapping results in null items (edge case)")
    void processIncomingOrder_nullItemsAfterMapping() {
        Order orderWithNullItems = new Order();
        orderWithNullItems.setItems(null);
        when(orderMapper.toEntity(any(OrderRequestDTO.class))).thenReturn(orderWithNullItems);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.processIncomingOrder(orderRequestDTO, IDEMPOTENCY_KEY);
        });
        assertThat(exception.getMessage()).contains("Order must contain items");

        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    @DisplayName("Should propagate DataIntegrityViolationException from repository")
    void processIncomingOrder_duplicateKey() {
        DataIntegrityViolationException dbException = new DataIntegrityViolationException(
                "Duplicate key error simulation");
        when(orderRepository.save(any(Order.class))).thenThrow(dbException);

        DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class, () -> {
            orderService.processIncomingOrder(orderRequestDTO, IDEMPOTENCY_KEY);
        });

        assertThat(thrown).isEqualTo(dbException);

        verify(orderRepository, times(1)).save(orderCaptor.capture());
        Order orderAttemptedToSave = orderCaptor.getValue();
        assertThat(orderAttemptedToSave.getStatus()).isEqualTo(OrderStatus.PROCESSED);
    }

    @Test
    @DisplayName("Should return order when found by ID")
    void findByOrderId_found() {
        UUID existingOrderId = savedOrder.getId();
        when(orderRepository.findById(existingOrderId)).thenReturn(Optional.of(savedOrder));

        Order foundOrder = orderService.findByOrderId(existingOrderId);

        assertThat(foundOrder).isNotNull();
        assertThat(foundOrder.getId()).isEqualTo(existingOrderId);
        assertThat(foundOrder).isEqualTo(savedOrder);

        verify(orderRepository, times(1)).findById(existingOrderId);
    }

    @Test
    @DisplayName("Should throw OrderNotFoundException when order ID does not exist")
    void findByOrderId_notFound() {
        UUID nonExistentOrderId = UUID.randomUUID();
        when(orderRepository.findById(nonExistentOrderId)).thenReturn(Optional.empty());

        OrderNotFoundException thrown = assertThrows(OrderNotFoundException.class, () -> {
            orderService.findByOrderId(nonExistentOrderId);
        });

        assertThat(thrown.getMessage()).contains(nonExistentOrderId.toString());
        verify(orderRepository, times(1)).findById(nonExistentOrderId);
    }

    @Test
    @DisplayName("Should return list of all orders")
    void findAll_returnsList() {
        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        Order order2 = new Order();
        order2.setId(UUID.randomUUID());
        List<Order> orderList = Arrays.asList(order1, order2);

        when(orderRepository.findAll()).thenReturn(orderList);

        List<Order> result = orderService.findAll();

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(order1, order2);

        verify(orderRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should return empty list when no orders exist")
    void findAll_returnsEmptyList() {
        when(orderRepository.findAll()).thenReturn(Collections.emptyList());

        List<Order> result = orderService.findAll();

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();

        verify(orderRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should return page of orders")
    void findAll_paginated_returnsPage() {
        Order order1 = new Order();
        order1.setId(UUID.randomUUID());
        List<Order> orderList = Collections.singletonList(order1);
        Pageable pageable = PageRequest.of(0, 1);
        Page<Order> orderPage = new PageImpl<>(orderList, pageable, 1);

        when(orderRepository.findAll(any(Pageable.class))).thenReturn(orderPage);

        Page<Order> result = orderService.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().getFirst()).isEqualTo(order1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getNumber()).isEqualTo(0);

        verify(orderRepository, times(1)).findAll(pageable);
    }

    @Test
    @DisplayName("Should return empty page when no orders exist for the page")
    void findAll_paginated_returnsEmptyPage() {
        Pageable pageable = PageRequest.of(1, 5);
        Page<Order> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

        when(orderRepository.findAll(any(Pageable.class))).thenReturn(emptyPage);

        Page<Order> result = orderService.findAll(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
        assertThat(result.getTotalPages()).isEqualTo(0);
        assertThat(result.getNumber()).isEqualTo(1);

        verify(orderRepository, times(1)).findAll(pageable);
    }
}
