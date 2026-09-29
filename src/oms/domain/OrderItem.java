package oms.domain;

import java.math.BigDecimal;

public class OrderItem {
    private final String productId;
    private final String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public OrderItem(String productId, String productName, int quantity, BigDecimal unitPrice) {
        this.productId = DomainValues.text(productId, "Product id");
        this.productName = DomainValues.text(productName, "Product name");
        setQuantity(quantity);
        setUnitPrice(unitPrice);
        recalculateLineTotal();
    }

    public void increaseQuantity(int additionalQuantity) {
        if (additionalQuantity <= 0) throw new IllegalArgumentException("Additional quantity must be positive.");
        try { setQuantity(Math.addExact(this.quantity, additionalQuantity)); }
        catch (ArithmeticException error) { throw new IllegalArgumentException("Quantity is too large.", error); }
    }

    public void setQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        this.quantity = quantity;
        recalculateLineTotal();
    }

    private void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = DomainValues.money(unitPrice, false);
    }

    private void recalculateLineTotal() {
        if (unitPrice == null) {
            return;
        }
        lineTotal = unitPrice
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2);
    }

    public OrderItem copy() {
        return new OrderItem(productId, productName, quantity, unitPrice);
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }
}
