package com.innowise.orderservice.controller;

import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.service.ItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
    private final ItemService items;

    public ItemController(ItemService items) {
        this.items = items;
    }

    @PostMapping
    public ResponseEntity<ItemView> create(@Valid @RequestBody ItemRequest request) {
        var item = items.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + item.id())).body(item);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemView> get(@PathVariable @Positive long id) {
        return ResponseEntity.ok(items.get(id));
    }

    @GetMapping
    public ResponseEntity<Page<ItemView>> list(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(items.list(PageRequest.of(page, size, Sort.by("id"))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemView> update(@PathVariable @Positive long id, @Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(items.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        items.delete(id);
        return ResponseEntity.noContent().build();
    }
}
