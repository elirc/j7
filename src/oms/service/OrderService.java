package oms.service;

import oms.domain.Order;
import oms.domain.OrderItem;
import oms.domain.OrderStatus;
import oms.domain.Product;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class OrderService {
    private final Map<String, Order> ordersById = new LinkedHashMap<>();
    private final CustomerService customerService;
    private final ProductService productService;

    public OrderService(Collection<Order> orders, CustomerService customerService, ProductService productService) {
        this.customerService = customerService;
        this.productService = productService;
        for (Order order : orders) {
            if (customerService.findById(order.getCustomerId()).isEmpty()) throw new IllegalArgumentException("Order references a missing customer.");
            for (OrderItem item : order.getItems()) {
                if (productService.findById(item.getProductId()).isEmpty()) throw new IllegalArgumentException("Order item references a missing product.");
            }
            if (ordersById.putIfAbsent(order.getId(), order.copy()) != null) throw new IllegalArgumentException("Duplicate order id.");
        }
    }

    public Order createOrder(String customerId) {
        if (customerService.findById(customerId).isEmpty()) {
            throw new IllegalArgumentException("Customer not found.");
        }

        Order order = new Order(UUID.randomUUID().toString(), customerId, LocalDateTime.now());
        ordersById.put(order.getId(), order);
        return order.copy();
    }

    public void addProductToOrder(String orderId, String productId, int quantity) {
        Order order = requireOrder(orderId);
        ensureEditable(order);
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }

        Product product = productService.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found."));
        if (!product.isActive()) {
            throw new IllegalArgumentException("Inactive products cannot be added to orders.");
        }

        OrderItem item = new OrderItem(product.getId(), product.getName(), quantity, product.getPrice());
        order.addOrMergeItem(item);
    }

    public void updateItemQuantity(String orderId, String productId, int quantity) {
        requireOrder(orderId).setItemQuantity(productId, quantity);
    }

    public void removeProductFromOrder(String orderId, String productId) {
        Order order = requireOrder(orderId);
        ensureEditable(order);
        boolean removed = order.removeItemByProductId(productId);
        if (!removed) {
            throw new IllegalArgumentException("Product was not found in the order.");
        }
    }

    public void applyDiscount(String orderId, BigDecimal discountAmount) {
        Order order = requireOrder(orderId);
        ensureEditable(order);
        order.setDiscountAmount(discountAmount);
    }

    public void updateStatus(String orderId, OrderStatus newStatus) {
        Order order = requireOrder(orderId);
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == newStatus) {
            throw new IllegalArgumentException("Order is already in that status.");
        }
        if (!currentStatus.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException("Invalid status transition from " + currentStatus + " to " + newStatus + ".");
        }
        if ((newStatus == OrderStatus.PAID || newStatus == OrderStatus.SHIPPED) && !order.hasItems()) {
            throw new IllegalArgumentException("An empty order cannot be moved to " + newStatus + ".");
        }
        order.setStatus(newStatus);
    }

    public Optional<Order> findById(String id) {
        return Optional.ofNullable(ordersById.get(id)).map(Order::copy);
    }

    public List<Order> listOrders() {
        return ordersById.values().stream().map(Order::copy).toList();
    }

    public Collection<Order> all() {
        return listOrders();
    }

    private Order requireOrder(String orderId) {
        Order order = ordersById.get(orderId);
        if (order == null) {
            throw new IllegalArgumentException("Order not found.");
        }
        return order;
    }

    private void ensureEditable(Order order) {
        if (!order.isEditable()) {
            throw new IllegalArgumentException("Orders in " + order.getStatus() + " status cannot be edited.");
        }
    }
}
