# Stories and resume lines: Order Management System

## Story 1: the discount that disappeared between save and load

**Situation.** An order held two units at 10.00 with a 5.00 discount, a total of 15.00. Saving and restarting produced the same two units and a total of 20.00. The money on screen was wrong, which is the worst kind of wrong in an order system.

**Task.** Decide whether the defect was in the writer, in the number handling, or in reconstruction, and fix it without loosening any rule.

**Action.** The written row was correct, `O|...|5.00|...`, so the writer was innocent. The old constructor took the saved discount, started with an empty item list and recalculated at once; its recalculation clamped a discount larger than the subtotal down to the subtotal, and the subtotal at that instant was zero. The items were added afterwards, too late to matter. I removed the invalid intermediate state instead of patching the arithmetic: `Order.restore` (`src/oms/domain/Order.java:42`) receives status, discount, timestamps and the whole item collection in one call, copies the items, rejects a duplicate product line, and only then validates the discount against a real subtotal (`src/oms/domain/Order.java:38`). Clamping was deleted; an impossible saved discount is refused rather than repaired.

**Result.** "saved discount survives full order reload" (`test/oms/RegressionTests.java:30`) asserts 5.00 and a total of 15.00 after a genuine file round trip. Seven tests failed against the original implementation; the delivered suite is 35 Java regression tests, and a separate script runs 7 CLI scenarios in fresh Java processes for 42 checks, recorded in `astraupskill/VERIFICATION.md`.

## Story 2: the totals anyone could rewrite

**Situation.** The same investigation showed the aggregate was not actually in charge of its own numbers. Services handed out their live `Order` objects and `Order` handed out its live `OrderItem` objects, so any caller could change a quantity or a discount without passing a single rule.

**Task.** Make the authoritative state unreachable from outside without turning every read into a deep clone of the world.

**Action.** Copy at each boundary that owns something mutable, and leave the immutable types alone. `OrderService` stores `order.copy()` on the way in (`src/oms/service/OrderService.java:31`) and returns `Order::copy` on the way out (line 97). `Order` copies an incoming item (`src/oms/domain/Order.java:63`) and maps `OrderItem::copy` over every read (line 127). `Customer` and `Product` are already final in every field, so their services only need to return unmodifiable collections, which `ProductService.all()` does with `List.copyOf` (`src/oms/service/ProductService.java:46`).

**Result.** Four tests hold the perimeter: a read cannot mutate the service (`test/oms/RegressionTests.java:31`), an item getter cannot invalidate totals (line 32), a newly created order is detached from the service (line 38), and clearing a returned catalog collection deletes nothing (line 35).

## Story 3: four files became one, without losing anyone's data

**Situation.** Saving rewrote four separate files. An interruption partway through could leave order metadata from one generation beside items from another, and nothing in the program could tell.

**Task.** Move to a single atomic snapshot while existing installations still had the four legacy files on disk.

**Action.** `FileDataStore.save` validates a complete `StoreSnapshot`, serializes the header, every C, P, O and I row and a count footer, writes a temp sibling and calls `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` with a `finally` that removes the temp (`src/oms/persistence/FileDataStore.java:117-121`). The loader prefers `oms.snapshot.txt` and, when it is absent, reads the legacy four through the same tagged-row pipeline, demanding all four or none (`src/oms/persistence/FileDataStore.java:40`). A corrupt snapshot never falls back to the old files. The first save leaves the legacy files exactly as they were.

**Result.** "legacy four-file migration preserves discount and original files" (`test/oms/RegressionTests.java:54`) compares `orders.txt` byte for byte across a save and then corrupts it to prove the snapshot is now authoritative, and "failed replacement keeps existing destination and removes temporary file" (line 66) forces the move to fail and asserts the directory still holds exactly one entry.

## Resume bullets

- Diagnosed an order-total corruption that appeared only after a reload and traced it to an aggregate reconstructed in an invalid intermediate state; replaced piecemeal construction with a single complete-restore factory that validates the saved discount against the real subtotal.
- Removed silent data repair from the order domain so an impossible saved discount is refused with a specific message instead of being clamped to a plausible wrong total.
- Closed encapsulation holes across the service and domain layers by copying aggregates and line items at every boundary, so no caller can desynchronize authoritative order totals.
- Replaced a four-file save with one validated snapshot written to a temporary sibling and installed by atomic move, with a record-count footer that detects truncation and a load path that never falls back from a corrupt snapshot to stale legacy files.
- Grew a dependency-free regression suite to 35 cases over real temporary directories covering restoration, rejected mutations, status transitions, codec errors, duplicate identities, missing references and migration; 7 of them failed against the pre-change implementation.

## 60-second spoken summary

"j7 is a Java order-management CLI: customers, a product catalog, and orders that own their line items. The story is the discount that vanished. An order with a 5.00 discount reloaded as if it had none. The saved file was right, so the problem was reconstruction. The old constructor accepted the discount, then recalculated totals while the item list was still empty, and its recalculation clamped any discount above the subtotal down to the subtotal, which at that moment was zero. I did not fix the arithmetic; I removed the state that made the arithmetic wrong. Restoration now takes the order and all of its items in one call, so the discount is validated against a real subtotal, and an impossible value is rejected instead of quietly repaired. The same pass made the aggregate own its data properly, copies in and copies out, and replaced four separate save files with one snapshot written to a temp file and moved atomically. Seven tests failed against the original code; the suite is 35 tests now, plus seven cross-process CLI scenarios."

## What I would do next

From `astraupskill/05-PRACTICE.md` and the limits in `astraupskill/VERIFICATION.md`:

1. An injected clock and an enforced order version, practice exercise 3. Today `touch()` calls `LocalDateTime.now()` directly (`src/oms/domain/Order.java:122`) and nothing prevents a lost update between two sessions.
2. Catalog updates with stable order snapshots, exercise 1, including a decided policy for what a merge does when the price has moved. The current merge deliberately keeps the original line price.
3. The HTTP contract, exercise 4, with an explicit decision on whether a status transition is a resource replacement or a named command.
4. Inventory reservation with an outbox, exercise 6. Until then I would not describe the PAID to CANCELLED transition as a refund workflow, and `astraupskill/VERIFICATION.md` says the same.
