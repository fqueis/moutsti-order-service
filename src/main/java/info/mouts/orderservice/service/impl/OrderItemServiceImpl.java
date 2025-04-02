package info.mouts.orderservice.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.exception.OrderItemNotFoundException;
import info.mouts.orderservice.repository.OrderItemRepository;
import info.mouts.orderservice.service.OrderItemService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OrderItemServiceImpl implements OrderItemService {

    private final OrderItemRepository orderItemRepository;

    public OrderItemServiceImpl(OrderItemRepository orderItemRepository) {
        this.orderItemRepository = orderItemRepository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order::items", key = "#orderId")
    public List<OrderItem> findOrderItemsByOrderId(UUID orderId) {
        log.debug("Attempting to find order items for the order with ID: {}", orderId);

        return orderItemRepository.findByOrder_Id(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "order::item", key = "#itemId")
    public OrderItem findById(UUID itemId) {
        return orderItemRepository.findById(itemId).orElseThrow(() -> new OrderItemNotFoundException(itemId));
    }

}
