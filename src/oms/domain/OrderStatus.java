package oms.domain;

import java.util.EnumSet;
import java.util.Set;

public enum OrderStatus {
    DRAFT,
    SUBMITTED,
    PAID,
    SHIPPED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    private Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }
}
