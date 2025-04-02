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
import info.mouts.orderservice.dto.OrderRequestDTO;
import info.mouts.orderservice.dto.OrderResponseDTO;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderMapper INSTANCE = Mappers.getMapper(OrderMapper.class);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    OrderItem toEntity(OrderItemRequestDTO dto);

    List<OrderItem> toEntityList(List<OrderItemRequestDTO> dtoList);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "total", ignore = true),
            @Mapping(target = "idempotencyKey", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(source = "items", target = "items")
    })
    Order toEntity(OrderRequestDTO dto);

    List<OrderResponseDTO> toOrderResponseDtoList(List<Order> entityList);

    OrderItemResponseDTO toOrderItemResponseDto(OrderItem entity);

    List<OrderItemResponseDTO> toOrderItemResponseDtoList(List<OrderItem> entityList);

    @Mapping(source = "updatedAt", target = "processedAt")
    OrderResponseDTO toOrderResponseDto(Order entity);
}
