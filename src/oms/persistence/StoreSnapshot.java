package oms.persistence;
import oms.domain.Customer;
import oms.domain.Order;
import oms.domain.Product;
import oms.service.CustomerService;
import oms.service.ProductService;
import oms.service.OrderService;
import java.util.List;

/** A detached, referentially valid snapshot for the single-threaded CLI. */
public class StoreSnapshot {
    private final List<Customer> customers;
    private final List<Product> products;
    private final List<Order> orders;
    public StoreSnapshot(List<Customer> customers, List<Product> products, List<Order> orders) {
        this.customers = List.copyOf(customers);
        this.products = List.copyOf(products);
        this.orders = orders.stream().map(Order::copy).toList();
        // Reuse the service constructors' duplicate and reference checks.
        new OrderService(this.orders, new CustomerService(this.customers), new ProductService(this.products));
    }
    public List<Customer> getCustomers() { return customers; }
    public List<Product> getProducts() { return products; }
    public List<Order> getOrders() { return orders.stream().map(Order::copy).toList(); }
}
