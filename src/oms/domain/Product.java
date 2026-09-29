package oms.domain;

import java.math.BigDecimal;

public class Product {
    private final String id;
    private final String name;
    private final String sku;
    private final BigDecimal price;
    private final boolean active;

    public Product(String id, String name, String sku, BigDecimal price, boolean active) {
        this.id = DomainValues.text(id, "Product id");
        this.name = DomainValues.text(name, "Product name");
        this.sku = DomainValues.text(sku, "SKU");
        this.price = normalizePrice(price);
        this.active = active;
    }

    private BigDecimal normalizePrice(BigDecimal value) {
        return DomainValues.money(value, false);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSku() {
        return sku;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
