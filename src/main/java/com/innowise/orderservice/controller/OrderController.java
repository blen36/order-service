package com.innowise.orderservice.controller;

import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.service.OrderAccess;
import com.innowise.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @PostMapping
    public ResponseEntity<OrderView> create(@Valid @RequestBody CreateOrder request, @AuthenticationPrincipal Jwt jwt) {
        var order = orders.create(request, access(jwt), jwt.getTokenValue());
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderView> get(@PathVariable @Positive long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(orders.get(id, access(jwt), jwt.getTokenValue()));
    }

    @GetMapping
    public ResponseEntity<Page<OrderView>> list(@RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Set<OrderStatus> statuses,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @AuthenticationPrincipal Jwt jwt) {
        var paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return ResponseEntity.ok(orders.list(userId, from, to, statuses, paging, access(jwt), jwt.getTokenValue()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderView> update(@PathVariable @Positive long id, @Valid @RequestBody UpdateOrder request,
                                             @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(orders.update(id, request, access(jwt), jwt.getTokenValue()));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<OrderView> changeStatus(@PathVariable @Positive long id,
            @Valid @RequestBody ChangeStatus request, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(orders.changeStatus(id, request, access(jwt), jwt.getTokenValue()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id, @AuthenticationPrincipal Jwt jwt) {
        orders.delete(id, access(jwt));
        return ResponseEntity.noContent().build();
    }

    private OrderAccess access(Jwt jwt) {
        return new OrderAccess(Long.parseLong(jwt.getSubject()), "ADMIN".equals(jwt.getClaimAsString("role")));
    }
}
