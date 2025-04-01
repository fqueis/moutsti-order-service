package info.mouts.orderservice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OrderTest {

    private static Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void testOrderCreation() {
        String idempotencyKey = UUID.randomUUID().toString();

        Order order = Order.builder().id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey).status(OrderStatus.RECEIVED).build();

        OrderItem item = OrderItem.builder().productId("PROD-1").quantity(1).price(BigDecimal.TEN).build();
        order.addItem(item);

        assertNotNull(order.getId());
        assertEquals(OrderStatus.RECEIVED, order.getStatus());
        assertEquals(idempotencyKey, order.getIdempotencyKey());
        assertEquals(1, order.getItems().size());
        assertSame(order, item.getOrder(), "Order reference should be set in OrderItem");

        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertTrue(violations.isEmpty(), "Order should be valid");
    }

    @Test
    void testAddItemsToOrder() {
        String idempotencyKey = UUID.randomUUID().toString();

        Order order = Order.builder().id(UUID.randomUUID())
                .idempotencyKey(idempotencyKey).status(OrderStatus.RECEIVED).build();

        OrderItem item1 = OrderItem.builder().productId("PROD-1").quantity(1).price(BigDecimal.TEN).build();
        OrderItem item2 = OrderItem.builder().productId("PROD-2").quantity(2).price(BigDecimal.valueOf(5.50)).build();

        order.addItem(item1);
        order.addItem(item2);

        assertEquals(2, order.getItems().size());

        OrderItem firstItem = order.getItems().getFirst();
        OrderItem lastItem = order.getItems().getLast();

        assertSame(order, firstItem.getOrder());
        assertSame(order, lastItem.getOrder());

        assertTrue(firstItem.equals(item1));
        assertTrue(lastItem.equals(item2));

        Set<ConstraintViolation<Order>> violations = validator.validate(order);
        assertTrue(violations.isEmpty(), "Order should be valid");
    }
}
