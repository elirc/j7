# Solution guidance and review questions

Use this after attempting the [practice ladder](05-PRACTICE.md). The sketches describe future changes, not code already delivered. Your solution should name its invariants and show failure preservation, even if you choose a different implementation approach.

For catalog updates, replace an immutable product in the catalog map while preserving its ID. Existing `OrderItem` values retain their captured name and unit price. A good test reads both the changed catalog product and the unchanged historical line. If SKU uniqueness is required, define case/whitespace normalization and check it on creation, update, and import. Do not rely on uniqueness of product IDs to imply uniqueness of SKUs.

For draft deletion, place the status rule in the command path and enforce it again at the persistence boundary if concurrent state changes are possible. A proposed SQL delete might include both the order ID and expected version/status. A zero affected-row count should produce a defined missing-or-conflict result. Customer deletion requires a reference policy: rejecting referenced deletes, archiving, or another explicit design. Cascading deletion of historical orders would change the domain meaning and should not be an accidental ORM default.

For optimistic concurrency, the critical operation is compare-and-update, not the mere existence of a version property. In a database, an update conditioned on `id`, `owner_id`, and `version` can make the check atomic. In memory, a lock or another coordinated state mechanism is needed. The current services are single-threaded and do not provide this guarantee. Test two operations released together and observe both results.

The HTTP adapter should translate domain outcomes without duplicating all business rules in controllers. A possible contract uses 400 for malformed values, 404 for a visible missing resource, and 409 for a stale version or forbidden state transition. Keep the public response consistent and avoid returning raw stack traces. A route such as `PUT /orders/{id}/items/{productId}` can represent quantity replacement, but define whether the line must already exist. The delivered CLI command requires an existing line.

For database restoration, query the order metadata and all its line snapshots, then call the complete restoration path once. Do not construct an empty paid order and attempt to add items through editing methods. Use foreign keys for customer/product references and a unique order/product constraint if the one-line-per-product policy remains. Validate the discount against the full subtotal before accepting imported state.

For inventory and messaging, keep business acceptance and notification delivery separate. Reserve stock and record an outbox row in one database transaction. A dispatcher can retry publication using a stable event identity. Successful dispatch does not retroactively create the business transition, and failed dispatch must not erase an already accepted order. Define how cancellation releases reservations and how duplicate requests replay or conflict.

During review, ask the author to draw one order edit from HTTP parsing through validation, version check, item change, commit, and response. Then ask what happens if the response is lost after commit. The answer should distinguish request idempotency from optimistic concurrency and from message dispatch deduplication. These are related but separate guarantees, and each needs its own observable test rather than a general claim that the application is reliable.

## Where the historical snapshot is taken

The first sketch above depends on behaviour the delivered service already has: `src/oms/service/OrderService.java:45` copies the product name and price into the `OrderItem` at the moment it is added, so a later catalog edit cannot rewrite an existing line.

```java
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
```

