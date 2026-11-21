package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.exception.OrderApiException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository items;
    private final OrderMapper mapper;

    public ItemService(ItemRepository items, OrderMapper mapper) {
        this.items = items;
        this.mapper = mapper;
    }

    @Transactional
    public ItemView create(ItemRequest request) {
        return mapper.toView(items.save(mapper.toItem(request)));
    }

    public ItemView get(long id) {
        return mapper.toView(items.findById(id).orElseThrow(() -> OrderApiException.missing("Item")));
    }

    public Page<ItemView> list(Pageable page) {
        return items.findAll(page).map(mapper::toView);
    }

    @Transactional
    public ItemView update(long id, ItemRequest request) {
        Item item = locked(id);
        mapper.updateItem(request, item);
        return mapper.toView(item);
    }

    @Transactional
    public void delete(long id) {
        items.delete(locked(id));
        items.flush();
    }

    private Item locked(long id) {
        return items.lockById(id).orElseThrow(() -> OrderApiException.missing("Item"));
    }
}
