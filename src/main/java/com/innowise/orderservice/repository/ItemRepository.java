package com.innowise.orderservice.repository;

import com.innowise.orderservice.entity.Item;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select i from Item i where i.id in :ids order by i.id")
    List<Item> lockForPricing(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> lockById(@Param("id") long id);
}
