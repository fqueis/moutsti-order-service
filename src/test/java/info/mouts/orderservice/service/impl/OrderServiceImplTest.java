package info.mouts.orderservice.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.domain.OrderStatus;
import info.mouts.orderservice.dto.OrderItemRequestDTO;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.repository.OrderRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OrderServiceImplTest {
    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderServiceImpl orderService;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private OrderRequestDTO orderRequestDTO;
    private Order mappedOrder;
    private Order savedOrder;

    private final String IDEMPOTENCY_KEY = UUID.randomUUID().toString();

    @BeforeEach
    public void setUp() {
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
}
