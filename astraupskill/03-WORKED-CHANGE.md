# Worked change: restore the complete order before validating totals

Begin with an order containing two units at 10.00 each and a 5.00 discount. The expected total is 15.00 before and after saving. The original reload produced a zero discount and a total of 20.00. This was not a formatting problem or an incorrect multiplication; it was an initialization-order defect.

The old constructor accepted the saved discount, initialized an empty item collection, and immediately recalculated totals. Its recalculation method clamped a discount larger than subtotal down to subtotal. Because subtotal was temporarily zero, the discount became zero. `FileDataStore` later added saved items one by one, but the original discount value had already been discarded.

The delivered design removes that temporary invalid state. The loader first parses order metadata and groups all item records by order ID. It then calls a complete restoration entry point:

```java
Order restored = Order.restore(
    "O", "C", OrderStatus.PAID, new BigDecimal("5.00"),
    savedCreatedAt, savedUpdatedAt,
    List.of(new OrderItem("P", "Notebook", 2, new BigDecimal("10.00")))
);
```

The factory copies all item values, rejects duplicate product lines, calculates the full subtotal, and validates the saved discount. It preserves status and timestamps directly. Paid status prevents interactive edits, but does not prevent reconstruction of a valid paid order from a complete saved snapshot. A saved discount greater than the final subtotal is rejected rather than silently repaired.

The mutation design follows the same principle. Updating quantity constructs a candidate item and computes the proposed subtotal before replacing the existing line. Removing a line checks the remaining subtotal and submitted-order nonempty rule before deleting anything. Changing a discount validates precision and range before assignment. Failed changes do not touch `updatedAt`, so a rejected request does not falsely look like a successful edit.

The ownership correction prevents bypasses. `OrderService` stores copies of incoming orders and returns detached copies. `Order` copies incoming and outgoing item values. Customers and products are immutable, while their services return copied collections. As a result, clearing a collection or changing a returned item cannot secretly remove catalog entries or desynchronize authoritative order totals.

Persistence now writes one `oms.snapshot.txt` file with C, P, O, and I record tags plus a count footer. Previously, customers, products, order metadata, and items were rewritten separately. An interruption could combine different generations of data. The new save validates and serializes a complete snapshot before writing a temporary sibling and atomically replacing the destination. Loading an existing corrupt snapshot stops startup and never falls back silently to older legacy files.

The [regression suite](../test/oms/RegressionTests.java) contains both the initial failing reproduction and broader protections. Read the saved-discount case, complete paid restoration, failed quantity reduction, missing-reference rejection, and legacy migration tests. Explain which assertion detects the original bug and which assertions protect newly defined behavior. That distinction keeps a review focused on actual guarantees rather than a generic statement that the code was refactored.
