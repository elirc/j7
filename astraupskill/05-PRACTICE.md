# Practice ladder toward a CRUD order-management web app

These exercises extend the delivered foundation. They are not claims about existing features. Choose one, write its acceptance examples, add a failing test, implement it, and explain its remaining limits before moving to the next. Keep format experiments in a separate learning directory.

## 1. Add catalog updates with stable order snapshots

Add a product rename/price/activity update command that replaces the immutable catalog product. Create an order at 10.00, change the catalog price to 12.00, and verify that the existing line remains 10.00. A newly created order should capture 12.00. Define what adding another quantity to the existing line means; the current merge policy keeps its original price. Test invalid precision and duplicate SKU policy explicitly rather than allowing accidental behavior from map insertion.

## 2. Add draft-order deletion and reference-safe customer changes

Implement confirmed deletion only for DRAFT orders under a documented rule. Decide whether a draft with items may be deleted or must be emptied first. Preserve submitted, paid, shipped, and cancelled records as history. Add customer profile updates without changing customer identity. A physical customer delete must account for referenced orders; a user interface that hides a delete button does not enforce referential integrity.

## 3. Add deterministic time and optimistic versions

Inject a clock so failed-update timestamp tests and tie-ordering tests do not depend on wall-clock timing. Add a version to orders and require an expected version for edits. Two callers read version 1; one edit succeeds and the other conflicts. The comparison and replacement must happen together under the eventual repository's concurrency mechanism. Merely adding a version field without enforcing it does not prevent lost updates.

## 4. Build the CRUD HTTP contract

Expose customers, products, orders, line quantity replacement, line removal, discounts, and status transitions through explicit routes. Decide whether a status transition is a resource replacement or a named command endpoint. Define invalid input, missing resource, and state/version conflict responses. Keep decimal representation explicit in JSON. Test that creating an order returns an ID that can be read, and that a paid quantity edit returns the documented error without changing persisted totals.

## 5. Replace snapshot files with transactional persistence

Model customers, products, orders, and order items with foreign keys and appropriate uniqueness constraints. Persist an order edit and its version together in one transaction. Add a failure after changing an item but before committing order metadata and prove rollback. Loading should reconstruct a complete aggregate without using interactive edit methods. Preserve price snapshots on item rows rather than joining current catalog price to calculate historical totals.

## 6. Add inventory reservation and an outbox

Introduce stock only after defining when it is reserved, released, and consumed. A submitted order could reserve stock, cancellation could release it, and payment/shipping could follow explicit transitions. Two competing orders must not reserve the same final unit. If you also publish a notification, store dispatch intent in the same transaction as the accepted transition, then dispatch asynchronously. Test a broker outage and retry without duplicating the business transition.

## 7. Add authenticated ownership and review evidence

Choose a tenant or owner model and apply it to every lookup and mutation, including nested item routes. Test that changing an order ID cannot reach another owner's order. Demonstrate a stale edit, invalid amount, denied cross-owner lookup, failed transaction, and repeated notification dispatch. A mid-level review should explain the authoritative state and commit point for each case, supported by tests that exercise the real adapter and persistence layer.
