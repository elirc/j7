package oms.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Owns its item values; every accepted mutation preserves totals and status rules. */
public class Order {
    private final String id;
    private final String customerId;
    private final List<OrderItem> items = new ArrayList<>();
    private OrderStatus status;
    private BigDecimal discountAmount;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Order(String id, String customerId, LocalDateTime createdAt) {
        this(id, customerId, OrderStatus.DRAFT, BigDecimal.ZERO, createdAt, createdAt, List.of());
    }

    private Order(String id, String customerId, OrderStatus status, BigDecimal discount,
                  LocalDateTime createdAt, LocalDateTime updatedAt, Collection<OrderItem> restoredItems) {
        this.id = DomainValues.text(id, "Order id");
        this.customerId = DomainValues.text(customerId, "Customer id");
        this.status = Objects.requireNonNull(status, "Status is required.");
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated time is required.");
        var productIds = new HashSet<String>();
        for (OrderItem item : restoredItems) {
            if (!productIds.add(item.getProductId())) throw new IllegalArgumentException("Duplicate product in order.");
            items.add(item.copy());
        }
        this.discountAmount = DomainValues.money(discount, true);
        validateSubtotal(getSubtotal());
        if (requiresItems(status) && items.isEmpty()) throw new IllegalArgumentException("This order status requires items.");
    }

    public static Order restore(String id, String customerId, OrderStatus status, BigDecimal discount,
                                LocalDateTime createdAt, LocalDateTime updatedAt, Collection<OrderItem> items) {
        // Restore the complete aggregate once, then validate the saved discount.
        return new Order(id, customerId, status, discount, createdAt, updatedAt, items);
    }

    public Order copy() {
        return restore(id, customerId, status, discountAmount, createdAt, updatedAt, items);
    }

    public void addOrMergeItem(OrderItem newItem) {
        ensureEditable();
        Objects.requireNonNull(newItem, "Item is required.");
        for (OrderItem item : items) {
            if (item.getProductId().equals(newItem.getProductId())) {
                // Existing line keeps its original price and name snapshot.
                item.increaseQuantity(newItem.getQuantity());
                touch();
                return;
            }
        }
        items.add(newItem.copy());
        touch();
    }

    public void setItemQuantity(String productId, int quantity) {
        ensureEditable();
        for (int index = 0; index < items.size(); index++) {
            OrderItem current = items.get(index);
            if (current.getProductId().equals(productId)) {
                OrderItem candidate = new OrderItem(productId, current.getProductName(), quantity, current.getUnitPrice());
                validateSubtotal(getSubtotal().subtract(current.getLineTotal()).add(candidate.getLineTotal()));
                items.set(index, candidate);
                touch();
                return;
            }
        }
        throw new IllegalArgumentException("Product was not found in the order.");
    }

    public boolean removeItemByProductId(String productId) {
        ensureEditable();
        for (int index = 0; index < items.size(); index++) {
            OrderItem item = items.get(index);
            if (item.getProductId().equals(productId)) {
                if (status == OrderStatus.SUBMITTED && items.size() == 1)
                    throw new IllegalArgumentException("A submitted order must keep at least one item.");
                validateSubtotal(getSubtotal().subtract(item.getLineTotal()));
                items.remove(index);
                touch();
                return true;
            }
        }
        return false;
    }

    public void setDiscountAmount(BigDecimal discount) {
        ensureEditable();
        BigDecimal candidate = DomainValues.money(discount, true);
        if (candidate.compareTo(getSubtotal()) > 0) throw new IllegalArgumentException("Discount cannot exceed subtotal.");
        discountAmount = candidate;
        touch();
    }

    public void setStatus(OrderStatus target) {
        if (target == null || !status.canTransitionTo(target)) throw new IllegalArgumentException("Invalid order status transition.");
        if (requiresItems(target) && !hasItems()) throw new IllegalArgumentException("An empty order cannot enter that status.");
        status = target;
        touch();
    }

    private boolean requiresItems(OrderStatus value) {
        return value == OrderStatus.SUBMITTED || value == OrderStatus.PAID || value == OrderStatus.SHIPPED;
    }
    private void validateSubtotal(BigDecimal subtotal) {
        if (discountAmount.compareTo(subtotal) > 0) throw new IllegalArgumentException("Reduce the discount before reducing the subtotal.");
    }
    private void ensureEditable() {
        if (!isEditable()) throw new IllegalArgumentException("Orders in " + status + " status cannot be edited.");
    }
    private void touch() { updatedAt = LocalDateTime.now(); }
    public boolean isEditable() { return status == OrderStatus.DRAFT || status == OrderStatus.SUBMITTED; }
    public boolean hasItems() { return !items.isEmpty(); }
    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items.stream().map(OrderItem::copy).toList(); }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getSubtotal() { return items.stream().map(OrderItem::getLineTotal).reduce(new BigDecimal("0.00"), BigDecimal::add); }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public BigDecimal getTotal() { return getSubtotal().subtract(discountAmount); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
