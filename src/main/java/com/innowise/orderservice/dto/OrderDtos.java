package com.innowise.orderservice.dto;

import com.innowise.orderservice.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record LineRequest(@NotNull @Positive Long itemId, @NotNull @Positive Integer quantity) {}

    public record CreateOrder(@NotBlank @Email @Size(max = 320) String email,
                              @NotEmpty @Size(max = 100) List<@NotNull @Valid LineRequest> items) {}

    public record UpdateOrder(@NotEmpty @Size(max = 100) List<@NotNull @Valid LineRequest> items) {}

    public record ChangeStatus(@NotNull OrderStatus status) {}

    public record ItemRequest(@NotBlank @Size(max = 255) String name,
                              @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2) BigDecimal price) {}

    public record ItemView(Long id, String name, BigDecimal price) {}

    public record UserInfo(Long id, String name, String surname, String email) {}

    public record LineView(Long itemId, String name, int quantity) {}

    public record OrderView(Long id, OrderStatus status, BigDecimal totalPrice, Instant createdAt,
                            Instant updatedAt, List<LineView> items, UserInfo user) {}
}
