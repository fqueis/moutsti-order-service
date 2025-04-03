package info.mouts.orderservice.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import info.mouts.orderservice.domain.Order;
import info.mouts.orderservice.domain.OrderItem;
import info.mouts.orderservice.dto.OrderItemRequestDTO;
import info.mouts.orderservice.dto.OrderItemResponseDTO;
import info.mouts.orderservice.dto.OrderProcessedEventDTO;
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.dto.OrderResponseDTO;

/**
 * Mapper interface for converting between Order DTOs (Data Transfer Objects)
 * and Order domain entities using MapStruct.
 * Provides mappings for {@link Order}, {@link OrderItem}, and related event
 * DTOs.
 */
@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderMapper INSTANCE = Mappers.getMapper(OrderMapper.class);

    /**
     * Maps an {@link OrderItemRequestDTO} to an {@link OrderItem} entity.
     * Ignores the 'id' and 'order' fields during mapping.
     *
     * @param dto The source {@link OrderItemRequestDTO}.
     * @return The mapped {@link OrderItem} entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemRequestDTO dto);

    /**
     * Maps a list of {@link OrderItemRequestDTO}s to a list of {@link OrderItem}
     * entities.
     *
     * @param dtoList The list of source {@link OrderItemRequestDTO}s.
     * @return A list of mapped {@link OrderItem} entities.
     */
    List<OrderItem> toEntityList(List<OrderItemRequestDTO> dtoList);

    /**
     * Maps an {@link OrderRequestDTO} to an {@link Order} entity.
     *
     * @param dto The source {@link OrderRequestDTO}.
     * @return The mapped {@link Order} entity.
     */
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "total", ignore = true),
            @Mapping(target = "idempotencyKey", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(source = "items", target = "items"),
            @Mapping(target = "failureReason", ignore = true)
    })
    Order toEntity(OrderRequestDTO dto);

    /**
     * Maps a list of {@link Order} entities to a list of {@link OrderResponseDTO}s.
     *
     * @param entityList The list of source {@link Order} entities.
     * @return A list of mapped {@link OrderResponseDTO}s.
     */
    List<OrderResponseDTO> toOrderResponseDtoList(List<Order> entityList);

    /**
     * Maps an {@link OrderItem} entity to an {@link OrderItemResponseDTO}.
     *
     * @param entity The source {@link OrderItem} entity.
     * @return The mapped {@link OrderItemResponseDTO}.
     */
    OrderItemResponseDTO toOrderItemResponseDto(OrderItem entity);

    /**
     * Maps a list of {@link OrderItem} entities to a list of
     * {@link OrderItemResponseDTO}s.
     *
     * @param entityList The list of source {@link OrderItem} entities.
     * @return A list of mapped {@link OrderItemResponseDTO}s.
     */
    List<OrderItemResponseDTO> toOrderItemResponseDtoList(List<OrderItem> entityList);

    /**
     * Maps an {@link Order} entity to an {@link OrderResponseDTO}.
     * Maps the entity's 'updatedAt' field to the DTO's 'processedAt' field.
     *
     * @param entity The source {@link Order} entity.
     * @return The mapped {@link OrderResponseDTO}.
     */
    @Mapping(source = "updatedAt", target = "processedAt")
    OrderResponseDTO toOrderResponseDto(Order entity);

    /**
     * Maps an {@link Order} entity to an {@link OrderProcessedEventDTO}.
     * Used for creating event payloads after an order is processed.
     * Maps the entity's 'id' to the DTO's 'orderId' and
     * the entity's 'updatedAt' to the DTO's 'processedAt'.
     *
     * @param entity The source {@link Order} entity.
     * @return The mapped {@link OrderProcessedEventDTO}.
     */
    @Mappings({
            @Mapping(source = "id", target = "orderId"),
            @Mapping(source = "updatedAt", target = "processedAt")
    })
    OrderProcessedEventDTO toProcessedEventDto(Order entity);

}
