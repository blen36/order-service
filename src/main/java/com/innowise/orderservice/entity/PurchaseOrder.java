package com.innowise.orderservice.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "orders")
@SQLRestriction("deleted = false")
public class PurchaseOrder extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.NEW;

    @Column(name = "total_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean deleted;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    @BatchSize(size = 32)
    private List<OrderItem> items = new ArrayList<>();

    protected PurchaseOrder() {}

    public PurchaseOrder(Long userId) {
        this.userId = userId;
    }

    public void clearItems() {
        items.clear();
        totalPrice = BigDecimal.ZERO;
    }

    public void addItem(Item item, int quantity) {
        items.add(new OrderItem(this, item, quantity));
        totalPrice = totalPrice.add(item.getPrice().multiply(BigDecimal.valueOf(quantity)));
    }

    public void markDeleted() { deleted = true; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
}
