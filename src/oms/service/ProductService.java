package oms.service;

import oms.domain.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ProductService {
    private final Map<String, Product> productsById = new LinkedHashMap<>();

    public ProductService(Collection<Product> products) {
        for (Product product : products) {
            if (productsById.putIfAbsent(product.getId(), product) != null) throw new IllegalArgumentException("Duplicate product id.");
        }
    }

    public Product createProduct(String name, String sku, BigDecimal price, boolean active) {
        validateRequired("Product name", name);
        validateRequired("Product SKU", sku);

        Product product = new Product(
                UUID.randomUUID().toString(),
                name.trim(),
                sku.trim(),
                price,
                active);
        productsById.put(product.getId(), product);
        return product;
    }

    public List<Product> listProducts() {
        return new ArrayList<>(productsById.values());
    }

    public Optional<Product> findById(String id) {
        return Optional.ofNullable(productsById.get(id));
    }

    public Collection<Product> all() {
        return List.copyOf(productsById.values());
    }

    private void validateRequired(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}
