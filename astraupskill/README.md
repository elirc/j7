# Order management: protect the aggregate before adding an API

This Java CLI teaches CRUD through customers, products, orders, line items, discounts, and status transitions. The course focuses on an order as a related group of data whose rules must hold together. A line quantity is not merely an integer field: changing it changes the subtotal, can invalidate a discount, and may be forbidden by the order's current status.

The original application lost saved discounts during reload. It constructed an order with a discount but no items, recalculated against a zero subtotal, and clamped the discount to zero. Adding restored items afterward could not recover the original value. The delivered implementation restores the whole order at once, validates its saved discount against the complete item collection, and derives totals from owned item values.

Other changes support the same learning objective. Reads return detached orders and items. Paid orders reject edits. Quantity replacement is a first-class command and menu option. Sub-cent amounts are rejected rather than rounded silently. Customers, products, orders, and items now persist in one versioned atomic snapshot, and corrupt startup stops before an empty session can overwrite data.

## Run with learning data

Use JDK 17 or newer and set `JAVA_HOME` to your installed JDK if Java is not on PATH. From the project root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\learning-orders
```

The override is a directory. With no override, the runner uses the project's `data` directory. It recompiles source before running, so old checked-in or leftover `out` classes are not the source of truth. The test command runs 35 regression cases with temporary data for filesystem scenarios.

Create a customer and an active product priced at 10.00. Create an order for that customer, add quantity 2, and apply a 5.00 discount. Save and restart: subtotal should be 20.00, discount 5.00, and total 15.00. Use menu 14 to change quantity to 3; the total becomes 25.00. Move through SUBMITTED to PAID, then try another quantity edit and observe the refusal.

## Course sequence

Follow the [code map](01-CODEBASE-MAP.md), [concepts](02-CONCEPTS.md), and [worked change](03-WORKED-CHANGE.md) before adding new features. Use [testing and debugging](04-TESTING-AND-DEBUGGING.md) with the [trace lab](07-TRACE-LAB.md). Attempt the [practice ladder](05-PRACTICE.md), then compare your reasoning with [solution guidance](06-SOLUTIONS-AND-REVIEW.md). The [verification record](VERIFICATION.md) describes executed checks and remaining limits.

This remains a single-threaded local application. It does not contain a browser, HTTP controller, authenticated owner model, database, payment processor, or stock reservation system. Those are possible extensions, and the exercises show where their boundaries would belong. A strong learning result is an explanation of why a rejected mutation leaves the order unchanged, why restoration differs from replaying interactive edits, and why an atomic file save is not a multi-user database transaction.
