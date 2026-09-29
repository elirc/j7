package oms.persistence;

import oms.domain.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One atomic snapshot replaces the legacy four-file save operation. */
public class FileDataStore {
    private static final String HEADER = "# oms-snapshot-v1";
    private final Path directory;
    private final Path snapshotFile;
    public FileDataStore(Path dataDirectory) {
        directory = dataDirectory.toAbsolutePath().normalize();
        snapshotFile = directory.resolve("oms.snapshot.txt");
    }
    public StoreSnapshot load() {
        try {
            var rows = new ArrayList<String>();
            if (!Files.notExists(snapshotFile)) {
                List<String> lines = Files.readAllLines(snapshotFile);
                if (lines.size() < 2 || !HEADER.equals(lines.get(0))) throw new IOException("Missing or unsupported snapshot header.");
                rows.addAll(lines.subList(1, lines.size() - 1));
                StoreSnapshot snapshot = parseRows(rows);
                String expected = footer(snapshot);
                if (!expected.equals(lines.get(lines.size() - 1))) throw new IOException("Missing footer or incorrect record counts.");
                return snapshot;
            }
            String[] files = {"customers.txt", "products.txt", "orders.txt", "order_items.txt"};
            String[] tags = {"C|", "P|", "O|", "I|"};
            int present = 0;
            for (String file : files) if (!Files.notExists(directory.resolve(file))) present++;
            if (present != 0 && present != files.length) throw new IOException("Incomplete legacy dataset: all four files are required.");
            if (present != 0) {
                for (int index = 0; index < files.length; index++) {
                    for (String line : Files.readAllLines(directory.resolve(files[index]))) {
                        if (!line.isBlank() && !line.startsWith("#")) rows.add(tags[index] + line);
                    }
                }
            }
            return parseRows(rows);
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Failed to load order data: " + error.getMessage(), error);
        }
    }

    private StoreSnapshot parseRows(List<String> rows) {
        var customers = new ArrayList<Customer>();
        var products = new ArrayList<Product>();
        var orderRows = new LinkedHashMap<String, List<String>>();
        var itemRows = new LinkedHashMap<String, List<OrderItem>>();
        for (int index = 0; index < rows.size(); index++) {
            try {
                List<String> fields = DelimitedFileUtil.split(rows.get(index));
                switch (fields.get(0)) {
                    case "C" -> {
                        count(fields, 6);
                        customers.add(new Customer(fields.get(1), fields.get(2), fields.get(3), fields.get(4), LocalDateTime.parse(fields.get(5))));
                    }
                    case "P" -> {
                        count(fields, 6);
                        String active = fields.get(5);
                        if (!active.equals("true") && !active.equals("false")) throw new IllegalArgumentException("Active must be true or false.");
                        products.add(new Product(fields.get(1), fields.get(2), fields.get(3), new BigDecimal(fields.get(4)), Boolean.parseBoolean(active)));
                    }
                    case "O" -> {
                        count(fields, 7);
                        if (orderRows.putIfAbsent(fields.get(1), fields) != null) throw new IllegalArgumentException("Duplicate order id.");
                    }
                    case "I" -> {
                        count(fields, 6);
                        itemRows.computeIfAbsent(fields.get(1), key -> new ArrayList<>()).add(new OrderItem(fields.get(2), fields.get(3), Integer.parseInt(fields.get(4)), new BigDecimal(fields.get(5))));
                    }
                    default -> throw new IllegalArgumentException("Unknown record type.");
                }
            } catch (RuntimeException error) {
                throw new IllegalArgumentException("Invalid data record " + (index + 1) + ": " + error.getMessage(), error);
            }
        }
        for (String orderId : itemRows.keySet()) if (!orderRows.containsKey(orderId)) throw new IllegalArgumentException("Item references a missing order.");
        var orders = new ArrayList<Order>();
        for (Map.Entry<String, List<String>> entry : orderRows.entrySet()) {
            List<String> fields = entry.getValue();
            orders.add(Order.restore(fields.get(1), fields.get(2), OrderStatus.valueOf(fields.get(3)), new BigDecimal(fields.get(4)),
                    LocalDateTime.parse(fields.get(5)), LocalDateTime.parse(fields.get(6)), itemRows.getOrDefault(entry.getKey(), List.of())));
        }
        return new StoreSnapshot(customers, products, orders);
    }
    private void count(List<String> fields, int expected) {
        if (fields.size() != expected) throw new IllegalArgumentException("Unexpected field count.");
    }
    private String footer(StoreSnapshot snapshot) {
        long items = snapshot.getOrders().stream().mapToLong(order -> order.getItems().size()).sum();
        return "END|" + snapshot.getCustomers().size() + "|" + snapshot.getProducts().size() + "|" + snapshot.getOrders().size() + "|" + items;
    }
    public void save(StoreSnapshot snapshot) {
        try {
            // Constructor rechecks references and copies mutable order aggregates.
            StoreSnapshot validated = new StoreSnapshot(snapshot.getCustomers(), snapshot.getProducts(), snapshot.getOrders());
            var lines = new ArrayList<String>();
            lines.add(HEADER);
            for (Customer customer : validated.getCustomers()) lines.add(DelimitedFileUtil.join("C", customer.getId(), customer.getName(), customer.getEmail(), customer.getPhone(), customer.getCreatedAt().toString()));
            for (Product product : validated.getProducts()) lines.add(DelimitedFileUtil.join("P", product.getId(), product.getName(), product.getSku(), product.getPrice().toPlainString(), String.valueOf(product.isActive())));
            for (Order order : validated.getOrders()) {
                lines.add(DelimitedFileUtil.join("O", order.getId(), order.getCustomerId(), order.getStatus().name(), order.getDiscountAmount().toPlainString(), order.getCreatedAt().toString(), order.getUpdatedAt().toString()));
                for (OrderItem item : order.getItems()) lines.add(DelimitedFileUtil.join("I", order.getId(), item.getProductId(), item.getProductName(), String.valueOf(item.getQuantity()), item.getUnitPrice().toPlainString()));
            }
            lines.add(footer(validated));
            Files.createDirectories(directory);
            Path temporary = Files.createTempFile(directory, ".oms-", ".tmp");
            try {
                Files.write(temporary, lines);
                Files.move(temporary, snapshotFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("Failed to save order data: " + error.getMessage(), error);
        }
    }
}
