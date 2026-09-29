# Order Management System

A Java 17 CLI learning project for customers, products, orders, items, discounts, and status transitions. The detailed [CRUD upskilling course](astraupskill/README.md) explains the actual source, worked fixes, examples, tests, debugging, and exercises toward a mid-level web application.

## Run and test

Install JDK 17 or newer and set `JAVA_HOME` if needed. From this project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\learning-orders
```

The override is a directory. Without it, the runner uses the project's `data` directory. The runner recompiles source and runs 35 dependency-free Java regression tests with `-Test`. Existing old `out` classes are not the current implementation until recompiled.

## Current behavior

Options 1–13 retain customer/product/order creation and listing, item addition/removal, details, discounts, status changes, save, and exit. Option 14 replaces an existing line's quantity while preserving its product name and unit-price snapshot.

Only DRAFT and SUBMITTED orders permit content edits. SUBMITTED, PAID, and SHIPPED require items; a submitted order cannot lose its final item. Paid and terminal orders reject quantity, item, and discount edits. Status transitions remain DRAFT to SUBMITTED/CANCELLED, SUBMITTED to PAID/CANCELLED, PAID to SHIPPED/CANCELLED, and no transitions from SHIPPED/CANCELLED. These statuses do not perform real payments or refunds.

Money must be non-negative or positive as appropriate and exactly representable with two decimal places. Sub-cent input is rejected without silent rounding. A quantity reduction or item removal that would make subtotal smaller than the discount is rejected; reduce the discount explicitly first.

Orders and items returned from reads are detached copies. Totals derive from owned item values. Restoration receives all items together before validating the saved discount, so discounts and timestamps survive reload without replaying interactive edits.

## Code responsibilities

- `App`: prompts, command translation, explicit save, exit, and EOF handling.
- `CustomerService` / `ProductService`: catalog identity and collection ownership.
- `OrderService`: customer/product lookup and commands on owned orders.
- `Order`: aggregate rules, complete restoration, copies, and derived totals.
- `OrderItem`: captured product values, guarded quantity, and line total.
- `DomainValues` / `OrderStatus`: field normalization and transition graph.
- `StoreSnapshot`: detached collections and cross-record reference validation.
- `FileDataStore` / `DelimitedFileUtil`: strict format parsing, migration, and atomic saving.

See the [code map](astraupskill/01-CODEBASE-MAP.md) for method-level traces.

## Saving and migration

The current authoritative file is `oms.snapshot.txt` inside the selected directory. It contains a version header, tagged customer/product/order/item records, and a count footer. Saving validates and serializes the whole snapshot before writing a temporary sibling and atomically replacing the target. There is no non-atomic fallback.

If no snapshot exists, loading accepts a complete legacy set of `customers.txt`, `products.txt`, `orders.txt`, and `order_items.txt`, or a new directory with none. Partial legacy sets fail explicitly. The first successful save creates the snapshot and preserves old files. Once created, the snapshot is authoritative; corruption does not silently fall back to older files. Do not alternate old binaries and the delivered runner against the same dataset.

Corrupt startup stops before the menu and does not overwrite the rejected file. A failed normal exit keeps the session open for retry. Save-on-exit and explicit save still mean a force-killed process can lose unsaved edits.

## Verification and next steps

Delivery verification passed 35 Java regression tests and seven separate-process CLI scenarios. Read the [verification record](astraupskill/VERIFICATION.md) for limits. The [practice ladder](astraupskill/05-PRACTICE.md) adds catalog updates, draft deletion, optimistic versions, HTTP contracts, database transactions, inventory/outbox behavior, and authenticated ownership.

This is a single-threaded local application. Atomic replacement does not supply cross-process locking, database transactions, power-loss guarantees, or request idempotency. No browser, HTTP API, real payment, stock reservation, or authentication is present.
