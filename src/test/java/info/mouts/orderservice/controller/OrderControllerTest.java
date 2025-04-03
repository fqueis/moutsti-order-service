package info.mouts.orderservice.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import info.mouts.orderservice.dto.OrderItemResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.hateoas.MediaTypes;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.dto.OrderResponseDTO;
import info.mouts.orderservice.exception.OrderItemNotFoundException;
import info.mouts.orderservice.exception.OrderNotFoundException;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.service.OrderItemService;
import info.mouts.orderservice.service.OrderService;

import org.springframework.data.web.PagedResourcesAssembler;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static info.mouts.orderservice.domain.OrderStatus.*;

@WebMvcTest(OrderController.class)
public class OrderControllerTest {
    private static final String BASE_API_URL = "/api/v1/orders";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private OrderItemService orderItemService;

    @MockitoBean
    private OrderMapper orderMapper;

    @MockitoBean
    private PagedResourcesAssembler<OrderResponseDTO> pagedResourcesAssembler;

    @Nested
    @DisplayName("GET /orders/{id} Endpoint")
    class GetOrderByIdTests {

        @Test
        @DisplayName("Should return 200 OK with OrderResponseDTO and HATEOAS links when order exists")
        void getOrderById_whenExists_shouldReturnDtoWithLinks() throws Exception {
            UUID orderId = UUID.randomUUID();

            LocalDateTime dateTime = LocalDateTime.of(2025, 4, 1, 20, 0, 0);

            Order foundOrder = Order.builder()
                    .id(orderId)
                    .status(PROCESSED)
                    .items(Collections.singletonList(OrderItem.builder().build()))
                    .createdAt(dateTime)
                    .updatedAt(dateTime)
                    .build();

            OrderResponseDTO responseDto = OrderResponseDTO.builder()
                    .id(orderId)
                    .status(PROCESSED)
                    .total(BigDecimal.valueOf(100))
                    .createdAt(dateTime)
                    .processedAt(dateTime)
                    .build();

            given(orderService.findByOrderId(orderId)).willReturn(foundOrder);
            given(orderMapper.toOrderResponseDto(any(Order.class))).willReturn(responseDto);

            String hateoasSelfLink = String.format("%s/%s", BASE_API_URL, orderId.toString());
            String hateoasItemsLink = String.format("%s/%s/items", BASE_API_URL, orderId.toString());

            mockMvc.perform(get(BASE_API_URL + "/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$.id", is(orderId.toString())))
                    .andExpect(jsonPath("$.status", is(PROCESSED.toString())))
                    .andExpect(jsonPath("$.total", is(100)))
                    .andExpect(jsonPath("$.createdAt", is("2025-04-01T20:00:00")))
                    .andExpect(jsonPath("$.processedAt", is("2025-04-01T20:00:00")))
                    .andExpect(jsonPath("$._links.self.href", endsWith(hateoasSelfLink)))
                    .andExpect(jsonPath("$._links.items.href", endsWith(hateoasItemsLink)));
        }

        @Test
        @DisplayName("Should return 404 Not Found when order does not exist")
        void getOrderById_whenNotExists_shouldReturnNotFound() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderService.findByOrderId(orderId)).willThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(get(BASE_API_URL + "/{id}", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(orderId.toString())));
        }
    }

    @Nested
    @DisplayName("GET /orders/{orderId}/items Endpoint")
    class GetOrderItemsTests {

        @Test
        @DisplayName("Should return 200 OK with items list and HATEOAS links when order exists")
        void getOrderItems_whenOrderExists_shouldReturnItems() throws Exception {
            UUID orderId = UUID.randomUUID();

            OrderItem item1 = OrderItem.builder().productId("prod-A")
                    .quantity(1).price(BigDecimal.TEN).build();
            OrderItem item2 = OrderItem.builder().productId("prod-B")
                    .quantity(5).price(BigDecimal.ONE).build();

            List<OrderItem> itemsFromService = Arrays.asList(item1, item2);

            OrderItemResponseDTO itemDto1 = OrderItemResponseDTO.builder().productId("prod-A").build();
            OrderItemResponseDTO itemDto2 = OrderItemResponseDTO.builder().productId("prod-B").build();
            List<OrderItemResponseDTO> itemDtosFromMapper = Arrays.asList(itemDto1, itemDto2);

            given(orderItemService.findOrderItemsByOrderId(orderId)).willReturn(itemsFromService);
            given(orderMapper.toOrderItemResponseDtoList(itemsFromService)).willReturn(itemDtosFromMapper);

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items", orderId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$._links.self.href",
                            endsWith("/orders/" + orderId + "/items")))
                    .andExpect(jsonPath("$._embedded.orderItems", hasSize(2)))
                    .andExpect(jsonPath("$._embedded.orderItems[0].productId", is("prod-A")))
                    .andExpect(jsonPath("$._embedded.orderItems[1].productId", is("prod-B")));
        }

        @Test
        @DisplayName("Should return 200 OK with empty collection when order has no items")
        void getOrderItems_whenOrderHasNoItems_shouldReturnEmptyCollection() throws Exception {
            UUID orderId = UUID.randomUUID();
            given(orderItemService.findOrderItemsByOrderId(orderId)).willReturn(Collections.emptyList());
            given(orderMapper.toOrderItemResponseDtoList(Collections.emptyList()))
                    .willReturn(Collections.emptyList());

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items", orderId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$._links.self.href",
                            endsWith("/orders/" + orderId + "/items")))
                    .andExpect(jsonPath("$._embedded.orderItemResponseDTOList").doesNotExist());
        }

        @Test
        @DisplayName("Should return 404 Not Found when order does not exist")
        void getOrderItems_whenOrderNotExists_shouldReturnNotFound() throws Exception {
            UUID orderId = UUID.randomUUID();

            given(orderItemService.findOrderItemsByOrderId(orderId))
                    .willThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items", orderId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Order Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(orderId.toString())));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when orderId is not a valid UUID")
        void getOrderItems_whenOrderIdIsNotValid_shouldReturnBadRequest() throws Exception {
            String invalidOrderId = "invalid-uuid";

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items", invalidOrderId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title", is("Invalid UUID")))
                    .andExpect(jsonPath("$.detail", containsString(invalidOrderId)));
        }
    }

    @Nested
    @DisplayName("GET /orders/{orderId}/items/{itemId} Endpoint")
    class GetOrderItemByIdTests {

        @Test
        @DisplayName("Should return 200 OK with OrderItemResponseDTO and HATEOAS links when item exists for the order")
        void findOrderItem_whenExists_shouldReturnDtoWithLinks() throws Exception {
            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();

            OrderItem foundItem = OrderItem.builder()
                    .id(itemId)
                    .productId("PROD-XYZ")
                    .quantity(3)
                    .price(new BigDecimal("25.50"))
                    .order(Order.builder().id(orderId).build())
                    .build();

            OrderItemResponseDTO responseDto = OrderItemResponseDTO.builder()
                    .productId("PROD-XYZ")
                    .quantity(3)
                    .price(new BigDecimal("25.50"))
                    .build();

            given(orderItemService.findByOrderIdAndItemId(orderId, itemId)).willReturn(foundItem);
            given(orderMapper.toOrderItemResponseDto(any(OrderItem.class))).willReturn(responseDto);

            String selfLink = String.format("%s/%s/items/%s", BASE_API_URL, orderId, itemId);
            String orderLink = String.format("%s/%s", BASE_API_URL, orderId);
            String itemsLink = String.format("%s/%s/items", BASE_API_URL, orderId);

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items/{itemId}", orderId, itemId))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaTypes.HAL_JSON))
                    .andExpect(jsonPath("$.productId", is("PROD-XYZ")))
                    .andExpect(jsonPath("$.quantity", is(3)))
                    .andExpect(jsonPath("$.price", is(25.50)))
                    .andExpect(jsonPath("$._links.self.href", endsWith(selfLink)))
                    .andExpect(jsonPath("$._links.order.href", endsWith(orderLink)))
                    .andExpect(jsonPath("$._links.items.href", endsWith(itemsLink)));
        }

        @Test
        @DisplayName("Should return 404 Not Found when item ID does not exist")
        void findOrderItem_whenItemIdNotExists_shouldReturnNotFound() throws Exception {
            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();

            given(orderItemService.findByOrderIdAndItemId(orderId, itemId))
                    .willThrow(new OrderItemNotFoundException(itemId));

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items/{itemId}", orderId, itemId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Order Item Not Found")))
                    .andExpect(jsonPath("$.detail", containsString(itemId.toString())));
        }

        @Test
        @DisplayName("Should return 404 Not Found when item ID exists but for different order ID")
        void findOrderItem_whenItemBelongsToDifferentOrder_shouldReturnNotFound() throws Exception {
            UUID orderId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();

            given(orderItemService.findByOrderIdAndItemId(orderId, itemId))
                    .willThrow(new OrderItemNotFoundException(orderId, itemId));

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items/{itemId}", orderId, itemId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Order Item Not Found")))
                    .andExpect(jsonPath("$.detail", containsString("item with ID " + itemId)))
                    .andExpect(jsonPath("$.detail",
                            containsString("not found within order ID " + orderId)));
        }

        @Test
        @DisplayName("Should return 400 Bad Request when orderId is not a valid UUID")
        void findOrderItem_whenOrderIdIsInvalid_shouldReturnBadRequest() throws Exception {
            String invalidOrderId = "invalid-order-uuid";
            UUID validItemId = UUID.randomUUID();

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items/{itemId}", invalidOrderId, validItemId))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 Bad Request when itemId is not a valid UUID")
        void findOrderItem_whenItemIdIsInvalid_shouldReturnBadRequest() throws Exception {
            UUID validOrderId = UUID.randomUUID();
            String invalidItemId = "invalid-item-uuid";

            mockMvc.perform(get(BASE_API_URL + "/{orderId}/items/{itemId}", validOrderId, invalidItemId))
                    .andExpect(status().isBadRequest());
        }
    }
}
