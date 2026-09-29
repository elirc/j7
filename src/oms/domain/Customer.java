package oms.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public class Customer {
    private final String id;
    private final String name;
    private final String email;
    private final String phone;
    private final LocalDateTime createdAt;

    public Customer(String id, String name, String email, String phone, LocalDateTime createdAt) {
        this.id = DomainValues.text(id, "Customer id");
        this.name = DomainValues.text(name, "Customer name");
        this.email = DomainValues.text(email, "Email");
        if (!this.email.contains("@")) throw new IllegalArgumentException("Email must contain @.");
        this.phone = phone == null ? "" : phone;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
