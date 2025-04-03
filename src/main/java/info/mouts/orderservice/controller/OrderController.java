package info.mouts.orderservice.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.dto.OrderItemResponseDTO;
import info.mouts.orderservice.dto.OrderResponseDTO;
import info.mouts.orderservice.mapper.OrderMapper;
import info.mouts.orderservice.service.OrderItemService;
import info.mouts.orderservice.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

/**
 * REST controller for managing and retrieving {@link Order} information.
 * Provides endpoints to list orders, get specific orders, and retrieve items
 * associated with an order.
 * Uses HATEOAS to provide navigational links in responses.
 */
@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders API", description = "Endpoints for retrieving orders and their items")
@Slf4j
public class OrderController {
    private final OrderService orderService;
    private final OrderItemService orderItemService;

    private final PagedResourcesAssembler<OrderResponseDTO> pagedResourcesAssembler;

    private final OrderMapper orderMapper;

    /**
     * Constructs an instance of {@code OrderController}.
     *
     * @param orderService            Service for order-related operations.
     * @param orderItemService        Service for order item-related operations.
     * @param orderMapper             Mapper for converting between entities and
     *                                DTOs.
     * @param pagedResourcesAssembler Assembler for creating HATEOAS PagedModel.
     */
    public OrderController(OrderService orderService, OrderItemService orderItemService, OrderMapper orderMapper,
            PagedResourcesAssembler<OrderResponseDTO> pagedResourcesAssembler) {
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.orderMapper = orderMapper;
        this.pagedResourcesAssembler = pagedResourcesAssembler;
    }

    /**
     * <p>
     * Retrieves a paginated list of all orders.
     * </p>
     * <p>
     * Uses HATEOAS to provide navigational links in responses.
     * </p>
     * 
     * @param pageable Pagination and sorting information (defaults to size 10,
     *                 sorted by createdAt descending).
     * @return A {@link ResponseEntity} containing a {@link PagedModel} of
     *         {@link OrderResponseDTO}s with HATEOAS links.
     */
    @GetMapping(produces = { MediaTypes.HAL_JSON_VALUE })
    @Operation(summary = "Get All Orders", description = "Retrieves a paginated list of all orders.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully", content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = PagedModel.class)))
    })
    public ResponseEntity<PagedModel<EntityModel<OrderResponseDTO>>> findAllOrders(
            @Parameter(hidden = true) @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Order> orders = orderService.findAll(pageable);

        Page<OrderResponseDTO> orderResponseDTOs = orders.map(orderMapper::toOrderResponseDto);
        orderResponseDTOs.forEach(dto -> dto.add(
                linkTo(methodOn(OrderController.class).findByOrderId(dto.getId())).withSelfRel()));

        return ResponseEntity.ok(pagedResourcesAssembler.toModel(orderResponseDTOs));
    }

    /**
     * <p>
     * Retrieves an order by its unique ID.
     * </p>
     * <p>
     * Uses HATEOAS to provide navigational links in responses.
     * </p>
     * 
     * @param orderId The UUID of the order to retrieve.
     * @return A {@link ResponseEntity} containing the {@link OrderResponseDTO} with
     *         HATEOAS links (self, items).
     */
    @GetMapping(value = "/{orderId}", produces = { MediaTypes.HAL_JSON_VALUE })
    @Operation(summary = "Get an Order by ID", description = "Retrieves an order by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully", content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = OrderResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for the given ID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderResponseDTO> findByOrderId(@PathVariable UUID orderId) {
        Order order = orderService.findByOrderId(orderId);
        OrderResponseDTO responseDTO = orderMapper.toOrderResponseDto(order);

        responseDTO.add(linkTo(methodOn(OrderController.class).findByOrderId(orderId)).withSelfRel());
        responseDTO.add(linkTo(methodOn(OrderController.class).findOrderItems(orderId)).withRel("items"));

        return ResponseEntity.ok(responseDTO);
    }

    /**
     * <p>
     * Retrieves the list of items associated with a specific order.
     * </p>
     * <p>
     * Uses HATEOAS to provide navigational links in responses.
     * </p>
     * 
     * @param orderId The UUID of the order whose items are to be retrieved.
     * @return A {@link ResponseEntity} containing a {@link CollectionModel} of
     *         {@link OrderItemResponseDTO}s with HATEOAS links (self, order,
     *         individual items).
     */
    @GetMapping(value = "/{orderId}/items", produces = { MediaTypes.HAL_JSON_VALUE })
    @Operation(summary = "Get Items for an Order", description = "Retrieves the list of items associated with a specific order.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Items retrieved successfully", content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = CollectionModel.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Order not found for the given ID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<CollectionModel<OrderItemResponseDTO>> findOrderItems(@PathVariable UUID orderId) {
        List<OrderItem> orderItems = orderItemService.findOrderItemsByOrderId(orderId);

        List<OrderItemResponseDTO> responseDTOs = orderMapper.toOrderItemResponseDtoList(orderItems);
        responseDTOs.forEach(
                dto -> dto.add(
                        linkTo(methodOn(OrderController.class).findOrderItem(orderId,
                                dto.getId())).withRel("item")));

        CollectionModel<OrderItemResponseDTO> collectionModel = CollectionModel.of(responseDTOs);

        collectionModel.add(linkTo(methodOn(OrderController.class).findOrderItems(orderId)).withSelfRel());
        collectionModel.add(linkTo(methodOn(OrderController.class).findByOrderId(orderId)).withRel("order"));

        return ResponseEntity.ok(collectionModel);
    }

    /**
     * <p>
     * Retrieves an item by its unique ID.
     * </p>
     * <p>
     * Uses HATEOAS to provide navigational links in responses.
     * </p>
     * 
     * @param orderId The UUID of the parent order.
     * @param itemId  The UUID of the specific item to retrieve.
     * @return A {@link ResponseEntity} containing the {@link OrderItemResponseDTO}
     */
    @GetMapping(value = "/{orderId}/items/{itemId}", produces = { MediaTypes.HAL_JSON_VALUE })
    @Operation(summary = "Get an Item for an Order", description = "Retrieves an item by its unique ID.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Item retrieved successfully", content = @Content(mediaType = MediaTypes.HAL_JSON_VALUE, schema = @Schema(implementation = OrderItemResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Invalid UUID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Item not found for the given ID", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Internal Server Error", content = @Content(mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = ProblemDetail.class)))
    })
    public ResponseEntity<OrderItemResponseDTO> findOrderItem(@PathVariable UUID orderId,
            @PathVariable UUID itemId) {
        OrderItem orderItem = orderItemService.findByOrderIdAndItemId(orderId, itemId);
        OrderItemResponseDTO responseDTO = orderMapper.toOrderItemResponseDto(orderItem);

        responseDTO.add(linkTo(methodOn(OrderController.class).findOrderItem(orderId, itemId)).withSelfRel());
        responseDTO.add(linkTo(methodOn(OrderController.class).findByOrderId(orderId)).withRel("order"));
        responseDTO.add(linkTo(methodOn(OrderController.class).findOrderItems(orderId)).withRel("items"));

        return ResponseEntity.ok(responseDTO);
    }
}
