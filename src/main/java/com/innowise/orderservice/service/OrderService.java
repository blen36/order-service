package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserDirectory;
import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.entity.*;
import com.innowise.orderservice.exception.OrderApiException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final OrderRepository orders;
    private final ItemRepository items;
    private final UserDirectory users;
    private final OrderMapper mapper;

    private final AuditingHandler auditing;

    public OrderService(OrderRepository orders, ItemRepository items, UserDirectory users,
                        OrderMapper mapper, AuditingHandler auditing) {
        this.orders = orders;
        this.items = items;
        this.users = users;
        this.mapper = mapper;
        this.auditing = auditing;
    }

    @Transactional
    public OrderView create(CreateOrder request, OrderAccess access, String token) {
        var user = users.byEmail(request.email(), token);
        access.requireOwner(user.id());
        var order = new PurchaseOrder(user.id());
        replaceItems(order, request.items());
        return mapper.toView(orders.saveAndFlush(order), user);
    }

    public OrderView get(long id, OrderAccess access, String token) {
        var order = orders.findById(id).orElseThrow(() -> OrderApiException.missing("Order"));
        access.requireOwner(order.getUserId());
        return mapper.toView(order, users.byId(order.getUserId(), token));
    }

    public Page<OrderView> list(Long userId, Instant from, Instant to, Set<OrderStatus> statuses,
                                Pageable page, OrderAccess access, String token) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw OrderApiException.invalid("from must be earlier than to");
        }
        var filter = OrderSpecifications.matching(access.listOwner(userId), from, to, statuses);
        var profiles = new HashMap<Long, UserInfo>();
        return orders.findAll(filter, page).map(order -> mapper.toView(order,
                profiles.computeIfAbsent(order.getUserId(), id -> users.byId(id, token))));
    }

    @Transactional
    public OrderView update(long id, UpdateOrder request, OrderAccess access, String token) {
        var order = lockOwned(id, access);
        if (order.getStatus() != OrderStatus.NEW) {
            throw OrderApiException.conflict("Only a NEW order can be edited");
        }
        var user = users.byId(order.getUserId(), token);
        replaceItems(order, request.items());
        // A different set of lines may have the same total; the parent still changed.
        auditing.markModified(order);
        orders.flush();
        return mapper.toView(order, user);
    }

    @Transactional
    public OrderView changeStatus(long id, ChangeStatus request, OrderAccess access, String token) {
        if (!access.admin()) {
            throw OrderApiException.forbidden();
        }
        var order = lockOwned(id, access);
        var user = users.byId(order.getUserId(), token);
        order.setStatus(request.status());
        orders.flush();
        return mapper.toView(order, user);
    }

    @Transactional
    public void delete(long id, OrderAccess access) {
        lockOwned(id, access).markDeleted();
    }

    private PurchaseOrder lockOwned(long id, OrderAccess access) {
        var order = orders.lockById(id).orElseThrow(() -> OrderApiException.missing("Order"));
        access.requireOwner(order.getUserId());
        return order;
    }

    private void replaceItems(PurchaseOrder order, List<LineRequest> lines) {
        Set<Long> ids = new HashSet<>();
        for (var line : lines) {
            if (!ids.add(line.itemId())) {
                throw OrderApiException.invalid("Combine repeated item IDs into one line");
            }
        }
        var catalog = items.lockForPricing(ids).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        if (catalog.size() != ids.size()) {
            throw OrderApiException.missing("Catalog item");
        }
        order.clearItems();
        for (var line : lines) {
            order.addItem(catalog.get(line.itemId()), line.quantity());
        }
        if (order.getTotalPrice().precision() - order.getTotalPrice().scale() > 17) {
            throw OrderApiException.invalid("Order total exceeds the supported amount");
        }
    }
}
