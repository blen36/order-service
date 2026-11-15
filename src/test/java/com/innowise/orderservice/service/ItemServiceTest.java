package com.innowise.orderservice.service;

import com.innowise.orderservice.dto.OrderDtos.ItemRequest;
import com.innowise.orderservice.entity.Item;
import com.innowise.orderservice.exception.OrderApiException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.ItemRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemServiceTest {
    private final ItemRepository items = mock(ItemRepository.class);
    private final OrderMapper mapper = Mappers.getMapper(OrderMapper.class);
    private final ItemService service = new ItemService(items, mapper);
    private final ItemRequest request = new ItemRequest("Keyboard", new BigDecimal("50.00"));

    private Item existingItem() {
        var item = mapper.toItem(request);
        ReflectionTestUtils.setField(item, "id", 9L);
        return item;
    }

    @Test
    void createPersistsNameAndPriceAndReturnsGeneratedId() {
        when(items.save(any(Item.class))).thenAnswer(invocation -> {
            Item item = invocation.getArgument(0);
            ReflectionTestUtils.setField(item, "id", 9L);
            return item;
        });
        var result = service.create(request);
        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.name()).isEqualTo(request.name());
        assertThat(result.price()).isEqualByComparingTo(request.price());
    }

    @Test
    void getReturnsStoredItem() {
        when(items.findById(9L)).thenReturn(Optional.of(existingItem()));
        assertThat(service.get(9L).name()).isEqualTo("Keyboard");
    }

    @Test
    void getRejectsUnknownItem() {
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(OrderApiException.class)
                .hasMessage("Item not found");
    }

    @Test
    void listKeepsPageMetadata() {
        var page = PageRequest.of(1, 1);
        when(items.findAll(page)).thenReturn(new PageImpl<>(List.of(existingItem()), page, 3));
        var result = service.list(page);
        assertThat(result.getNumber()).isOne();
        assertThat(result.getTotalElements()).isEqualTo(3);
        assertThat(result.getContent().getFirst().id()).isEqualTo(9L);
    }

    @Test
    void updateChangesTheLockedItem() {
        var item = existingItem();
        when(items.lockById(9L)).thenReturn(Optional.of(item));
        var result = service.update(9L, new ItemRequest("Monitor", new BigDecimal("99.50")));
        assertThat(result.id()).isEqualTo(9L);
        assertThat(item.getName()).isEqualTo("Monitor");
        assertThat(item.getPrice()).isEqualByComparingTo("99.50");
    }

    @Test
    void deleteRemovesTheLockedItemAndFlushesConstraints() {
        var item = existingItem();
        when(items.lockById(9L)).thenReturn(Optional.of(item));
        service.delete(9L);
        verify(items).delete(item);
        verify(items).flush();
    }
}
