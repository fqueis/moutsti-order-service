package info.mouts.orderservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.exception.OrderItemNotFoundException;
import info.mouts.orderservice.repository.OrderItemRepository;
import lombok.extern.slf4j.Slf4j;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Slf4j
public class OrderItemServiceTest {
    @Mock
    private OrderItemRepository orderItemRepository;

    @InjectMocks
    private OrderItemServiceImpl orderItemService;

    private Order mappedOrder;
    private OrderItem item1;
    private OrderItem item2;
    private UUID orderId = UUID.randomUUID();
    private UUID itemId1 = UUID.randomUUID();
    private UUID itemId2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mappedOrder = Order.builder().id(orderId).build();

        item1 = OrderItem.builder()
                .id(itemId1)
                .order(mappedOrder)
                .productId("prod-1")
                .quantity(2)
                .price(BigDecimal.TEN)
                .build();

        item2 = OrderItem.builder()
                .id(itemId2)
                .order(mappedOrder)
                .productId("prod-2")
                .quantity(1)
                .price(BigDecimal.ONE)
                .build();
    }

    @Test
    @DisplayName("Should return list of order items for a given order ID")
    void findOrderItemsByOrderId_found() {
        List<OrderItem> items = Arrays.asList(item1, item2);
        when(orderItemRepository.findByOrder_Id(orderId)).thenReturn(items);

        List<OrderItem> result = orderItemService.findOrderItemsByOrderId(orderId);

        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(item1, item2);
        verify(orderItemRepository, times(1)).findByOrder_Id(orderId);
    }

    @Test
    @DisplayName("Should return empty list when no order items found for a given order ID")
    void findOrderItemsByOrderId_notFound() {
        UUID nonExistentOrderId = UUID.randomUUID();
        when(orderItemRepository.findByOrder_Id(nonExistentOrderId)).thenReturn(Collections.emptyList());

        List<OrderItem> result = orderItemService.findOrderItemsByOrderId(nonExistentOrderId);

        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(orderItemRepository, times(1)).findByOrder_Id(nonExistentOrderId);
    }

    @Test
    @DisplayName("Should return order item when found by ID")
    void findById_found() {
        when(orderItemRepository.findById(itemId1)).thenReturn(Optional.of(item1));

        OrderItem result = orderItemService.findById(itemId1);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(itemId1);
        assertThat(result).isEqualTo(item1);
        verify(orderItemRepository, times(1)).findById(itemId1);
    }

    @Test
    @DisplayName("Should throw OrderItemNotFoundException when order item ID does not exist")
    void findById_notFound() {
        UUID nonExistentItemId = UUID.randomUUID();
        when(orderItemRepository.findById(nonExistentItemId)).thenReturn(Optional.empty());

        OrderItemNotFoundException thrown = assertThrows(OrderItemNotFoundException.class, () -> {
            orderItemService.findById(nonExistentItemId);
        });

        assertThat(thrown.getMessage()).contains(nonExistentItemId.toString());
        verify(orderItemRepository, times(1)).findById(nonExistentItemId);
    }
}
