# Solution guidance and review questions

Use this after attempting the [practice ladder](05-PRACTICE.md). The sketches describe future changes, not code already delivered. Your solution should name its invariants and show failure preservation, even if you choose a different implementation approach.

**Exercise 1 (SKU uniqueness).** Normalize once, for example `sku.trim().toUpperCase(Locale.ROOT)`, and compare against every existing product before `productsById.put`. The reference copy used to validate the checks did exactly that inside `createProduct`. Don't rely on unique product IDs to imply unique SKUs: IDs are random UUIDs (`src/oms/service/ProductService.java:28`), so they never collide. If you also reject duplicates at load time, do it in the `ProductService` constructor, because `StoreSnapshot` already reuses those constructors for validation (`src/oms/persistence/StoreSnapshot.java:20`).

**Exercise 2 (exit status).** In the `catch` at `src/oms/App.java:51-54`, call `System.exit(1)` (or another nonzero code you document) after the two messages. Nothing has been written at that point, so the guarantee that the file was not replaced still holds. Print the diagnostics to `System.err` too, so scripts can separate them from menu output.

**Exercise 3 (status parsing).** Catch the `IllegalArgumentException` from `OrderStatus.valueOf` in its own small `try` and print `Unknown status: X. Choose one of DRAFT, SUBMITTED, ...`. Keep it separate from the outer catch, because the outer one also reports genuine transition refusals from `OrderService.updateStatus`.

**Exercise 4 (repricing).** For catalog updates, replace an immutable product in the catalog map while preserving its ID. Existing `OrderItem` values retain their captured name and unit price. A good test reads both the changed catalog product and the unchanged historical line. If SKU uniqueness is required, define case/whitespace normalization and check it on creation, update, and import. Do not rely on uniqueness of product IDs to imply uniqueness of SKUs.

**Exercise 5 (draft deletion).** For draft deletion, place the status rule in the command path and enforce it again at the persistence boundary if concurrent state changes are possible. A proposed SQL delete might include both the order ID and expected version/status. A zero affected-row count should produce a defined missing-or-conflict result. Customer deletion requires a reference policy: rejecting referenced deletes, archiving, or another explicit design. Cascading deletion of historical orders would change the domain meaning and should not be an accidental ORM default.

**Exercise 6 (versions).** For optimistic concurrency, the critical operation is compare-and-update, not the mere existence of a version property. In a database, an update conditioned on `id`, `owner_id`, and `version` can make the check atomic. In memory, a lock or another coordinated state mechanism is needed. The current services are single-threaded and do not provide this guarantee. Test two operations released together and observe both results.

**Stretch: HTTP contract.** The HTTP adapter should translate domain outcomes without duplicating all business rules in controllers. A possible contract uses 400 for malformed values, 404 for a visible missing resource, and 409 for a stale version or forbidden state transition. Keep the public response consistent and avoid returning raw stack traces. A route such as `PUT /orders/{id}/items/{productId}` can represent quantity replacement, but define whether the line must already exist. The delivered CLI command requires an existing line.

**Stretch: transactional persistence.** For database restoration, query the order metadata and all its line snapshots, then call the complete restoration path once. Do not construct an empty paid order and attempt to add items through editing methods. Use foreign keys for customer/product references and a unique order/product constraint if the one-line-per-product policy remains. Validate the discount against the full subtotal before accepting imported state.

**Stretch: inventory and outbox.** For inventory and messaging, keep business acceptance and notification delivery separate. Reserve stock and record an outbox row in one database transaction. A dispatcher can retry publication using a stable event identity. Successful dispatch does not retroactively create the business transition, and failed dispatch must not erase an already accepted order. Define how cancellation releases reservations and how duplicate requests replay or conflict.

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


## Verified findings in the delivered code

Each finding below was checked against the source and, where marked, reproduced by running the CLI on a copy of `data/sample` (2026-10-06, JDK 17.0.10). Use them as review practice: say the risk, the smallest fix, and the test that would pin it.

1. **Startup failure exits 0 (reproduced).** `src/oms/App.java:51-54` prints `Startup stopped: ...` and `return`s from `main`. Running against `data/sample-corrupt` gives exit status 0, the same as a clean run. The bytes are protected, but automation can't detect the failure. (Exercise 2.)
2. **No SKU or email uniqueness (reproduced).** `ProductService.createProduct` (`src/oms/service/ProductService.java:23-35`) and `CustomerService.createCustomer` (`src/oms/service/CustomerService.java:23-38`) check only that a value is present. A second product with SKU `NB-001` and a second customer with `ada@example.com` were both created and saved. `VERIFICATION.md` admits the SKU gap; the email gap isn't mentioned anywhere. (Exercise 1.)
3. **Enum name leaks to the user (reproduced).** `src/oms/App.java:261-262` feeds raw input to `OrderStatus.valueOf`. Typing `foo` prints `No enum constant oms.domain.OrderStatus.FOO`. In a web adapter the same pattern turns into a 500 or a leaked type name. (Exercise 3.)
4. **Rules duplicated between service and aggregate.** `OrderService.updateStatus` (`src/oms/service/OrderService.java:84-92`) re-checks the transition and the empty-order rule, but only for PAID/SHIPPED. `Order.setStatus` (`src/oms/domain/Order.java:106-111`) checks the full set through `requiresItems`, which includes SUBMITTED (`src/oms/domain/Order.java:113-115`). `OrderService.ensureEditable` (`:116-120`) repeats `Order.ensureEditable` (`src/oms/domain/Order.java:119-121`), and the email `@` rule is in both `CustomerService.java:26` and `Customer.java:17`, with different messages. Today the aggregate always wins, so behaviour is correct. The risk is that someone later edits only one copy. The review question: which layer owns each rule?
5. **EOF means save.** `src/oms/App.java:87-89` catches end of input and saves all completed work. Closing the terminal input (Ctrl+Z / Ctrl+D) therefore commits rather than abandons. Defensible, but it should be documented in the menu text, and it's why every scripted check in 05 persists its changes.
6. **Wall-clock time inside the aggregate.** `touch()` calls `LocalDateTime.now()` (`src/oms/domain/Order.java:122`), and the services do the same for `createdAt` (`src/oms/service/CustomerService.java:35`, `src/oms/service/OrderService.java:40`). The values are zone-less `LocalDateTime` and are saved without an offset (`src/oms/persistence/FileDataStore.java:109`, `:112`). Tests can only assert "unchanged", never "set to T". A server would need `Instant` or an offset plus an injected `Clock`. (Exercise 6.)
7. **Atomic but not durable.** `FileDataStore.save` writes the temp file with `Files.write` and moves it with `ATOMIC_MOVE` (`src/oms/persistence/FileDataStore.java:117-121`) but never forces the bytes to disk. A crash can't leave a half-written snapshot under the real name, but a power loss right after the move may leave an empty or stale file on some filesystems. `VERIFICATION.md` already says power-loss durability isn't established; this line is why.
