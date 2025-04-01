package info.mouts.orderservice.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

public class OrderStatusTest {
    @Test
    void testEnumValuesExist() {
        assertNotNull(OrderStatus.valueOf("RECEIVED"));
        assertNotNull(OrderStatus.valueOf("PROCESSING"));
        assertNotNull(OrderStatus.valueOf("PROCESSED"));
        assertNotNull(OrderStatus.valueOf("FAILED"));
        assertNotNull(OrderStatus.valueOf("CANCELLED"));
    }

    @Test
    void testOrderStatusToString() {
        assertEquals("RECEIVED", OrderStatus.RECEIVED.toString());
        assertEquals("PROCESSING", OrderStatus.PROCESSING.toString());
        assertEquals("PROCESSED", OrderStatus.PROCESSED.toString());
        assertEquals("FAILED", OrderStatus.FAILED.toString());
        assertEquals("CANCELLED", OrderStatus.CANCELLED.toString());
    }

    @Test
    void testOrderStatusValues() {
        assertEquals(0, OrderStatus.RECEIVED.ordinal());
        assertEquals(1, OrderStatus.PROCESSING.ordinal());
        assertEquals(2, OrderStatus.PROCESSED.ordinal());
        assertEquals(3, OrderStatus.FAILED.ordinal());
        assertEquals(4, OrderStatus.CANCELLED.ordinal());
    }
}