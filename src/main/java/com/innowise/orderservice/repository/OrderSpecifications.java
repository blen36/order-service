package com.innowise.orderservice.repository;

import com.innowise.orderservice.entity.OrderStatus;
import com.innowise.orderservice.entity.PurchaseOrder;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class OrderSpecifications {
    private OrderSpecifications() {}

    public static Specification<PurchaseOrder> matching(Long userId, Instant from,
                                                         Instant to, Set<OrderStatus> statuses) {
        return (root, query, cb) -> {
            var conditions = new ArrayList<Predicate>();
            if (userId != null) {
                conditions.add(cb.equal(root.get("userId"), userId));
            }
            if (from != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                conditions.add(cb.lessThan(root.get("createdAt"), to));
            }
            if (statuses != null && !statuses.isEmpty()) {
                conditions.add(root.get("status").in(statuses));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
