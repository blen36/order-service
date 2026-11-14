package com.innowise.orderservice.service;

import com.innowise.orderservice.exception.OrderApiException;

public record OrderAccess(long userId, boolean admin) {
    public void requireOwner(long ownerId) {
        if (!admin && ownerId != userId) {
            throw OrderApiException.forbidden();
        }
    }

    public Long listOwner(Long requestedOwner) {
        if (requestedOwner != null) {
            requireOwner(requestedOwner);
        }
        return admin ? requestedOwner : Long.valueOf(userId);
    }
}
