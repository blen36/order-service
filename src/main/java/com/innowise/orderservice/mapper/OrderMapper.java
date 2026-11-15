package com.innowise.orderservice.mapper;

import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.entity.*;
import org.mapstruct.*;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface OrderMapper {
    Item toItem(ItemRequest request);
    void updateItem(ItemRequest request, @MappingTarget Item item);
    ItemView toView(Item item);

    @Mapping(target = "itemId", source = "item.id")
    @Mapping(target = "name", source = "item.name")
    LineView toView(OrderItem line);

    @Mapping(target = "id", source = "order.id")
    @Mapping(target = "user", source = "user")
    OrderView toView(PurchaseOrder order, UserInfo user);
}
