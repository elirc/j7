package oms;

import oms.domain.Customer;
import oms.domain.Order;
import oms.domain.OrderItem;
import oms.domain.OrderStatus;
import oms.domain.Product;
import oms.persistence.FileDataStore;
import oms.persistence.StoreSnapshot;
import oms.service.CustomerService;
import oms.service.OrderService;
import oms.service.ProductService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.NoSuchElementException;
import oms.domain.DomainValues;

public class App {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Scanner scanner = new Scanner(System.in);
    private final FileDataStore dataStore;
    private final CustomerService customerService;
    private final ProductService productService;
    private final OrderService orderService;

    public App() {
        this(Path.of("data"));
    }

    public App(Path dataDirectory) {
        dataStore = new FileDataStore(dataDirectory);
        StoreSnapshot snapshot = dataStore.load();
        customerService = new CustomerService(snapshot.getCustomers());
        productService = new ProductService(snapshot.getProducts());
        orderService = new OrderService(snapshot.getOrders(), customerService, productService);
    }

    public static void main(String[] args) {
        App app;
        try {
            app = new App(args.length == 0 ? Path.of("data") : Path.of(args[0]));
        } catch (RuntimeException error) {
            System.out.println("Startup stopped: " + error.getMessage());
            System.out.println("Existing data was not replaced. Repair a separate copy before retrying.");
            return;
        }
        app.run();
    }

    public void run() {
        System.out.println("Order Management System");
        System.out.println("Data loads from the selected directory. Save with 12 or exit with 13.");

        boolean running = true;
        try {
            while (running) {
                printMenu();
                String input = prompt("Choose an option: ");

                switch (input) {
                    case "1" -> createCustomer();
                    case "2" -> listCustomers();
                    case "3" -> createProduct();
                    case "4" -> listProducts();
                    case "5" -> createOrder();
                    case "6" -> addItemToOrder();
                    case "7" -> removeItemFromOrder();
                    case "8" -> viewOrderDetails();
                    case "9" -> listOrders();
                    case "10" -> applyDiscount();
                    case "11" -> updateOrderStatus();
                    case "12" -> saveNow();
                    case "13" -> running = exitApplication();
                    case "14" -> updateItemQuantity();
                    default -> System.out.println("Invalid menu choice. Please try again.");
                }
            }
        } catch (NoSuchElementException error) {
            System.out.println("Input ended. Saving completed changes.");
            saveNow();
        } finally {
            scanner.close();
        }
    }

    private void printMenu() {
        System.out.println();
        System.out.println("1. Create customer");
        System.out.println("2. List customers");
        System.out.println("3. Create product");
        System.out.println("4. List products");
        System.out.println("5. Create order");
        System.out.println("6. Add item to order");
        System.out.println("7. Remove item from order");
        System.out.println("8. View order details");
        System.out.println("9. List all orders");
        System.out.println("10. Apply discount");
        System.out.println("11. Update order status");
        System.out.println("12. Save data");
        System.out.println("13. Exit");
        System.out.println("14. Update item quantity");
    }

    private void createCustomer() {
        try {
            String name = prompt("Customer name: ");
            String email = prompt("Customer email: ");
            String phone = prompt("Customer phone (optional): ");

            Customer customer = customerService.createCustomer(name, email, phone);
            System.out.println("Created customer with ID: " + customer.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("Could not create customer: " + e.getMessage());
        }
    }

    private void listCustomers() {
        List<Customer> customers = customerService.listCustomers();
        if (customers.isEmpty()) {
            System.out.println("No customers found.");
            return;
        }

        for (Customer customer : customers) {
            System.out.println("ID: " + customer.getId());
            System.out.println("  Name: " + customer.getName());
            System.out.println("  Email: " + customer.getEmail());
            System.out.println("  Phone: " + (customer.getPhone().isBlank() ? "-" : customer.getPhone()));
            System.out.println("  Created: " + formatTime(customer.getCreatedAt()));
        }
    }

    private void createProduct() {
        try {
            String name = prompt("Product name: ");
            String sku = prompt("Product SKU: ");
            BigDecimal price = readMoney("Product price: ");
            if (price == null) {
                return;
            }
            Boolean active = readYesNo("Is the product active? (y/n): ");
            if (active == null) {
                return;
            }
            Product product = productService.createProduct(name, sku, price, active);
            System.out.println("Created product with ID: " + product.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("Could not create product: " + e.getMessage());
        }
    }

    private void listProducts() {
        List<Product> products = productService.listProducts();
        if (products.isEmpty()) {
            System.out.println("No products found.");
            return;
        }

        for (Product product : products) {
            System.out.println("ID: " + product.getId());
            System.out.println("  Name: " + product.getName());
            System.out.println("  SKU: " + product.getSku());
            System.out.println("  Price: $" + formatMoney(product.getPrice()));
            System.out.println("  Active: " + product.isActive());
        }
    }

    private void createOrder() {
        try {
            String customerId = prompt("Customer ID: ");
            Order order = orderService.createOrder(customerId);
            System.out.println("Created order with ID: " + order.getId());
        } catch (IllegalArgumentException e) {
            System.out.println("Could not create order: " + e.getMessage());
        }
    }

    private void addItemToOrder() {
        try {
            String orderId = prompt("Order ID: ");
            String productId = prompt("Product ID: ");
            Integer quantity = readPositiveInt("Quantity: ");
            if (quantity == null) {
                return;
            }

            orderService.addProductToOrder(orderId, productId, quantity);
            System.out.println("Product added to order.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not add product: " + e.getMessage());
        }
    }

    private void removeItemFromOrder() {
        try {
            String orderId = prompt("Order ID: ");
            String productId = prompt("Product ID to remove: ");
            orderService.removeProductFromOrder(orderId, productId);
            System.out.println("Product removed from order.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not remove product: " + e.getMessage());
        }
    }

    private void viewOrderDetails() {
        String orderId = prompt("Order ID: ");
        Optional<Order> orderOptional = orderService.findById(orderId);
        if (orderOptional.isEmpty()) {
            System.out.println("Order not found.");
            return;
        }

        Order order = orderOptional.get();
        printOrder(order, true);
    }

    private void listOrders() {
        List<Order> orders = orderService.listOrders();
        if (orders.isEmpty()) {
            System.out.println("No orders found.");
            return;
        }

        for (Order order : orders) {
            printOrder(order, false);
            System.out.println();
        }
    }

    private void applyDiscount() {
        try {
            String orderId = prompt("Order ID: ");
            BigDecimal discountAmount = readMoney("Discount amount: ");
            if (discountAmount == null) {
                return;
            }
            orderService.applyDiscount(orderId, discountAmount);
            System.out.println("Discount applied.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not apply discount: " + e.getMessage());
        }
    }

    private void updateOrderStatus() {
        try {
            String orderId = prompt("Order ID: ");
            System.out.println("Available statuses:");
            for (OrderStatus status : OrderStatus.values()) {
                System.out.println("  " + status.name());
            }

            String statusInput = prompt("New status: ").trim().toUpperCase();
            OrderStatus newStatus = OrderStatus.valueOf(statusInput);
            orderService.updateStatus(orderId, newStatus);
            System.out.println("Order status updated.");
        } catch (IllegalArgumentException e) {
            System.out.println("Could not update order status: " + e.getMessage());
        }
    }

    private void updateItemQuantity() {
        try {
            String orderId = prompt("Order ID: ");
            String productId = prompt("Product ID: ");
            Integer quantity = readPositiveInt("New quantity: ");
            if (quantity == null) return;
            orderService.updateItemQuantity(orderId, productId, quantity);
            System.out.println("Item quantity updated.");
        } catch (IllegalArgumentException error) {
            System.out.println("Could not update quantity: " + error.getMessage());
        }
    }

    private void saveNow() {
        if (saveData()) {
            System.out.println("Data saved.");
        } else {
            System.out.println("Could not save data.");
        }
    }

    private boolean exitApplication() {
        if (saveData()) {
            System.out.println("Data saved. Goodbye.");
        } else {
            System.out.println("Data could not be saved. Session stays open so you can retry.");
            return true;
        }
        return false;
    }

    private boolean saveData() {
        try {
            StoreSnapshot snapshot = new StoreSnapshot(
                    customerService.listCustomers(),
                    productService.listProducts(),
                    orderService.listOrders());
            dataStore.save(snapshot);
            return true;
        } catch (RuntimeException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private void printOrder(Order order, boolean includeItems) {
        String customerLabel = customerService.findById(order.getCustomerId())
                .map(Customer::getName)
                .orElse("Unknown customer");

        System.out.println("Order ID: " + order.getId());
        System.out.println("  Customer: " + customerLabel + " (" + order.getCustomerId() + ")");
        System.out.println("  Status: " + order.getStatus());
        System.out.println("  Created: " + formatTime(order.getCreatedAt()));
        System.out.println("  Updated: " + formatTime(order.getUpdatedAt()));
        System.out.println("  Subtotal: $" + formatMoney(order.getSubtotal()));
        System.out.println("  Discount: $" + formatMoney(order.getDiscountAmount()));
        System.out.println("  Total: $" + formatMoney(order.getTotal()));
        System.out.println("  Item count: " + order.getItems().size());

        if (includeItems) {
            if (order.getItems().isEmpty()) {
                System.out.println("  Items: none");
            } else {
                System.out.println("  Items:");
                for (OrderItem item : order.getItems()) {
                    System.out.println("    - " + item.getProductName()
                            + " | productId=" + item.getProductId()
                            + " | qty=" + item.getQuantity()
                            + " | unit=$" + formatMoney(item.getUnitPrice())
                            + " | line=$" + formatMoney(item.getLineTotal()));
                }
            }
        }
    }

    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine().trim();
    }

    private Integer readPositiveInt(String message) {
        String value = prompt(message);
        try {
            int number = Integer.parseInt(value);
            if (number <= 0) {
                System.out.println("Value must be greater than zero.");
                return null;
            }
            return number;
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid whole number.");
            return null;
        }
    }

    private BigDecimal readMoney(String message) {
        String value = prompt(message);
        try {
            return DomainValues.money(new BigDecimal(value), true);
        } catch (IllegalArgumentException e) {
            System.out.println("Please enter a non-negative amount with at most 2 decimal places, for example 19.99.");
            return null;
        }
    }

    private Boolean readYesNo(String message) {
        String value = prompt(message);
        if (value.equalsIgnoreCase("y") || value.equalsIgnoreCase("yes")) {
            return true;
        }
        if (value.equalsIgnoreCase("n") || value.equalsIgnoreCase("no")) {
            return false;
        }
        System.out.println("Please enter y/yes or n/no.");
        return null;
    }

    private String formatMoney(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatTime(LocalDateTime time) {
        return time.format(TIME_FORMAT);
    }
}
