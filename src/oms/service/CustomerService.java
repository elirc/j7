package oms.service;

import oms.domain.Customer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class CustomerService {
    private final Map<String, Customer> customersById = new LinkedHashMap<>();

    public CustomerService(Collection<Customer> customers) {
        for (Customer customer : customers) {
            if (customersById.putIfAbsent(customer.getId(), customer) != null) throw new IllegalArgumentException("Duplicate customer id.");
        }
    }

    public Customer createCustomer(String name, String email, String phone) {
        validateRequired("Customer name", name);
        validateRequired("Customer email", email);
        if (!email.contains("@")) {
            throw new IllegalArgumentException("Email must contain '@'.");
        }

        Customer customer = new Customer(
                UUID.randomUUID().toString(),
                name.trim(),
                email.trim(),
                phone == null ? "" : phone.trim(),
                LocalDateTime.now());
        customersById.put(customer.getId(), customer);
        return customer;
    }

    public List<Customer> listCustomers() {
        return new ArrayList<>(customersById.values());
    }

    public Optional<Customer> findById(String id) {
        return Optional.ofNullable(customersById.get(id));
    }

    public Collection<Customer> all() {
        return List.copyOf(customersById.values());
    }

    private void validateRequired(String fieldName, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
    }
}
