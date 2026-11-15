package com.innowise.orderservice.service;

import com.innowise.orderservice.client.UserDirectory;
import com.innowise.orderservice.dto.OrderDtos.*;
import com.innowise.orderservice.entity.*;
import com.innowise.orderservice.exception.OrderApiException;
import com.innowise.orderservice.mapper.OrderMapper;
import com.innowise.orderservice.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private static final UserInfo BUYER = new UserInfo(7L, "Ada", "Lee", "buyer@example.test");
    private static final String TOKEN = "test-bearer";
    private final OrderAccess owner = new OrderAccess(7L, false);
    private final OrderAccess admin = new OrderAccess(88L, true);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final UserDirectory users = mock(UserDirectory.class);
    private final AuditingHandler auditing = mock(AuditingHandler.class);
    private final OrderService service = new OrderService(orders, items, users, Mappers.getMapper(OrderMapper.class), auditing);

    private Item keyboard() {
        var item = new Item();
        item.setName("Keyboard");
        item.setPrice(new BigDecimal("49.95"));
        ReflectionTestUtils.setField(item, "id", 3L);
        return item;
    }

    private List<LineRequest> lines(int quantity) {
        return List.of(new LineRequest(3L, quantity));
    }

    @Test
    void createUsesCatalogPriceAndEmailProfile() {
        when(users.byEmail(BUYER.email(), TOKEN)).thenReturn(BUYER);
        when(items.lockForPricing(Set.of(3L))).thenReturn(List.of(keyboard()));
        when(orders.saveAndFlush(any(PurchaseOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.create(new CreateOrder(BUYER.email(), lines(2)), owner, TOKEN);
        assertThat(result.status()).isEqualTo(OrderStatus.NEW);
        assertThat(result.totalPrice()).isEqualByComparingTo("99.90");
        assertThat(result.items()).containsExactly(new LineView(3L, "Keyboard", 2));
        assertThat(result.user()).isEqualTo(BUYER);
    }

    @Test
    void createRejectsDuplicateLinesBeforeSaving() {
        when(users.byEmail(BUYER.email(), TOKEN)).thenReturn(BUYER);
        var repeated = List.of(new LineRequest(3L, 1), new LineRequest(3L, 2));
        assertThatThrownBy(() -> service.create(new CreateOrder(BUYER.email(), repeated), owner, TOKEN))
                .isInstanceOf(OrderApiException.class).hasMessageContaining("repeated");
        verifyNoInteractions(items, orders);
    }

    @Test
    void getLoadsOwnerProfileByStableId() {
        when(orders.findById(15L)).thenReturn(Optional.of(new PurchaseOrder(7L)));
        when(users.byId(7L, TOKEN)).thenReturn(BUYER);
        assertThat(service.get(15L, owner, TOKEN).user()).isEqualTo(BUYER);
    }

    @Test
    void getDeniesForeignOrderBeforeCallingUserService() {
        when(orders.findById(15L)).thenReturn(Optional.of(new PurchaseOrder(8L)));
        assertThatThrownBy(() -> service.get(15L, owner, TOKEN)).hasMessage("Access denied");
        verifyNoInteractions(users);
    }

    @Test
    void listFetchesEachBuyerOnlyOncePerPage() {
        var page = PageRequest.of(0, 5);
        var rows = List.of(new PurchaseOrder(7L), new PurchaseOrder(7L));
        when(orders.findAll(org.mockito.ArgumentMatchers.<Specification<PurchaseOrder>>any(), eq(page)))
                .thenReturn(new PageImpl<>(rows, page, 2));
        when(users.byId(7L, TOKEN)).thenReturn(BUYER);
        var result = service.list(null, null, null, Set.of(OrderStatus.NEW), page, admin, TOKEN);
        assertThat(result.getContent()).extracting(OrderView::user).containsExactly(BUYER, BUYER);
        verify(users, times(1)).byId(7L, TOKEN);
    }

    @Test
    void listRejectsInvalidDateRangeAndForeignOwner() {
        var now = Instant.now();
        var page = PageRequest.of(0, 20);
        assertThatThrownBy(() -> service.list(null, now, now, Set.of(), page, owner, TOKEN))
                .isInstanceOf(OrderApiException.class);
        assertThatThrownBy(() -> service.list(8L, null, null, Set.of(), page, owner, TOKEN))
                .hasMessage("Access denied");
        verifyNoInteractions(orders);
    }

    @Test
    void updateReplacesOldLinesAndRecalculatesTotal() {
        var order = new PurchaseOrder(7L);
        order.addItem(keyboard(), 8);
        when(orders.lockById(15L)).thenReturn(Optional.of(order));
        when(users.byId(7L, TOKEN)).thenReturn(BUYER);
        when(items.lockForPricing(Set.of(3L))).thenReturn(List.of(keyboard()));
        var result = service.update(15L, new UpdateOrder(lines(3)), owner, TOKEN);
        assertThat(result.totalPrice()).isEqualByComparingTo("149.85");
        assertThat(result.items()).containsExactly(new LineView(3L, "Keyboard", 3));
        verify(auditing).markModified(order);
    }

    @Test
    void updateRejectsPaidOrder() {
        var order = new PurchaseOrder(7L);
        order.setStatus(OrderStatus.PAID);
        when(orders.lockById(15L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.update(15L, new UpdateOrder(lines(1)), owner, TOKEN))
                .hasMessageContaining("NEW");
        verifyNoInteractions(users, items);
    }

    @Test
    void adminCanChangeStatus() {
        var order = new PurchaseOrder(7L);
        when(orders.lockById(15L)).thenReturn(Optional.of(order));
        when(users.byId(7L, TOKEN)).thenReturn(BUYER);
        assertThat(service.changeStatus(15L, new ChangeStatus(OrderStatus.PAID), admin, TOKEN).status())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void buyerCannotChangeStatus() {
        assertThatThrownBy(() -> service.changeStatus(15L, new ChangeStatus(OrderStatus.PAID), owner, TOKEN))
                .hasMessage("Access denied");
        verifyNoInteractions(orders);
    }

    @Test
    void deleteMarksOrderAndRetainsLines() {
        var order = new PurchaseOrder(7L);
        order.addItem(keyboard(), 2);
        when(orders.lockById(15L)).thenReturn(Optional.of(order));
        service.delete(15L, owner);
        assertThat(ReflectionTestUtils.getField(order, "deleted")).isEqualTo(true);
        assertThat(order.getItems()).hasSize(1);
        verify(orders, never()).delete(any(PurchaseOrder.class));
        verifyNoInteractions(items, users);
    }

    @Test
    void listOwnerLimitsBuyerAndAllowsAdminToReadAll() {
        assertThat(owner.listOwner(null)).isEqualTo(7L);
        assertThat(admin.listOwner(null)).isNull();
        assertThat(admin.listOwner(10L)).isEqualTo(10L);
    }
}
