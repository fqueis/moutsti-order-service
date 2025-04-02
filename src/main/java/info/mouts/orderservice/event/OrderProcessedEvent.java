package info.mouts.orderservice.event;

import org.springframework.context.ApplicationEvent;

import info.mouts.orderservice.domain.Order;
import lombok.Getter;

/**
 * Internal event published when an order is successfully processed.
 */
@Getter
public class OrderProcessedEvent extends ApplicationEvent {
    private final Order processedOrder;

    public OrderProcessedEvent(Object source, Order processedOrder) {
        super(source);
        this.processedOrder = processedOrder;
    }

}
