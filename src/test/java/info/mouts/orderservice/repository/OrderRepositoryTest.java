package info.mouts.orderservice.repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.domain.OrderStatus;
import lombok.extern.slf4j.Slf4j;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Slf4j
public class OrderRepositoryTest {
    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private OrderRepository orderRepository;

    private Order createTestOrder(String idempotencyKey) {
        Order order = Order.builder()
                .idempotencyKey(idempotencyKey)
                .status(OrderStatus.RECEIVED)
                .build();

        OrderItem item = OrderItem.builder()
                .productId("prod-123")
                .quantity(1)
                .price(BigDecimal.TEN)
                .build();

        order.addItem(item);
        return order;
    }

    @Test
    @DisplayName("Should save and retrieve an order with items")
    public void testSaveAndFindById() {
        String key = UUID.randomUUID().toString();

        Order order = createTestOrder(key);

        Order savedOrder = orderRepository.save(order);
        entityManager.flush();
        entityManager.clear();

        Optional<Order> foundOrderOpt = orderRepository.findById(savedOrder.getId());

        assertThat(foundOrderOpt).isPresent();

        Order foundOrder = foundOrderOpt.get();

        assertThat(foundOrder.getIdempotencyKey()).isEqualTo(key);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.RECEIVED);
        assertThat(foundOrder.getItems()).hasSize(1);
        assertThat(foundOrder.getItems().getFirst().getProductId()).isEqualTo("prod-123");
        assertThat(foundOrder.getItems().getFirst().getQuantity()).isEqualTo(1);
        assertThat(foundOrder.getItems().getFirst().getPrice()).isEqualTo(BigDecimal.TEN.setScale(2));
        assertThat(foundOrder.getCreatedAt()).isNotNull();
        assertThat(foundOrder.getUpdatedAt()).isNotNull();
        assertThat(foundOrder.getVersion()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should find an order by idempotency key when it exists")
    void findByIdempotencyKey_whenExists() {
        String key = UUID.randomUUID().toString();
        Order order = createTestOrder(key);
        entityManager.persistAndFlush(order);
        entityManager.clear();

        Optional<Order> foundOrderOpt = orderRepository.findByIdempotencyKey(key);

        assertThat(foundOrderOpt).isPresent();
        assertThat(foundOrderOpt.get().getIdempotencyKey()).isEqualTo(key);
    }

    @Test
    @DisplayName("Should return empty optional when finding by non-existent idempotency key")
    void findByIdempotencyKey_whenNotExists() {
        Optional<Order> foundOrderOpt = orderRepository.findByIdempotencyKey("non-existent-key");

        assertThat(foundOrderOpt).isNotPresent();
    }

    @Test
    @DisplayName("Should enforce uniqueness constraint on idempotency key")
    void shouldFailOnDuplicateIdempotencyKey() {
        String duplicateKey = UUID.randomUUID().toString();
        Order order1 = createTestOrder(duplicateKey);

        entityManager.persistAndFlush(order1);
        entityManager.clear();

        Order order2 = createTestOrder(duplicateKey);

        assertThrows(DataIntegrityViolationException.class, () -> {
            orderRepository.saveAndFlush(order2);
        });
    }

    @Test
    @DisplayName("Should increment version on update")
    void testOptimisticLockingVersionIncrement() {
        String key = UUID.randomUUID().toString();

        Order order = createTestOrder(key);
        entityManager.persistAndFlush(order);
        long initialVersion = order.getVersion();
        entityManager.clear();

        Order orderToUpdate = orderRepository.findById(order.getId()).orElseThrow();
        orderToUpdate.setStatus(OrderStatus.PROCESSING);
        orderRepository.saveAndFlush(orderToUpdate);
        entityManager.clear();

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getVersion()).isGreaterThan(initialVersion);
        assertThat(updatedOrder.getVersion()).isEqualTo(initialVersion + 1);
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    }
}
