# Aggregates, state transitions, and historical snapshots

An aggregate is a group of related values with rules that must stay consistent through an operation. Here, an order's item quantities, price snapshots, discount, status, and total belong together. Allowing callers to mutate a line returned by `getItems` would bypass those rules. Even an unmodifiable list is insufficient if the contained items are still mutable shared objects.

```java
Order view = service.findById("O").orElseThrow();
view.getItems().get(0).setQuantity(99);
// The returned item is detached. Neither the view's aggregate nor
// the service's authoritative order changes through this item reference.
```

The intended update path is `service.updateItemQuantity(orderId, productId, quantity)`. The aggregate builds a candidate line using the existing name and unit price, computes the proposed subtotal, and checks the discount before replacing the line. If a quantity reduction would make subtotal smaller than the discount, it rejects the command. The user must reduce the discount explicitly first; the application no longer silently changes a discount as a side effect of recalculation.

Derived totals reduce the number of facts that must be kept synchronized. The subtotal is the sum of line totals, and total is subtotal minus discount. The application still stores a line total inside each `OrderItem`, but that item's own quantity method maintains it and owned items never escape. A future immutable line-item design could derive that value too; adding more caches without an ownership rule would move in the wrong direction.

Restoration is not the same as applying user commands. A paid order loaded from disk must regain its existing items and discount without reopening it for editing or changing its saved update time. `Order.restore` therefore accepts a complete collection and validates the resulting state directly. It does not call `addOrMergeItem` on a paid order, and it does not temporarily compare the discount to an empty subtotal.

Status transitions express workflow. DRAFT can become SUBMITTED or CANCELLED; SUBMITTED can become PAID or CANCELLED; PAID can become SHIPPED or CANCELLED; SHIPPED and CANCELLED are terminal. Only DRAFT and SUBMITTED permit content edits. SUBMITTED, PAID, and SHIPPED require items, and a submitted order cannot lose its final item. Cancellation from PAID is only a local state transition here: no payment or refund integration exists.

An order line captures product name and unit price when added. Merging another quantity of the same product keeps that existing snapshot. This preserves a stable order price even if a later catalog exercise adds price changes. Decide a different repricing policy explicitly rather than letting a getter fetch current catalog price during every total calculation.

Finally, validation and persistence protect different boundaries. The aggregate rejects invalid commands; the snapshot validates references across customers, products, and orders; atomic file replacement keeps related saved records together. None of these supplies authenticated ownership, concurrent request handling, cross-process stale-write detection, or payment idempotency. Those are the next lessons when this CLI becomes a CRUD web application.

## Glossary

*Aggregate*: the order and its lines as one unit of change. *Aggregate root*: the object (Order) through which every change to the lines must go. *Detached read*: a copy handed out for reading that cannot change the stored state. *Invariant*: a rule that must hold after every operation (discount never exceeds subtotal, so total is never negative; lines change only while the order is DRAFT or SUBMITTED). *Price snapshot*: the unit price and name copied into the line when it was added, so later catalog edits do not rewrite history. *Terminal status*: a status with no allowed transitions out of it (CANCELLED, SHIPPED). *Owned entity*: the C# way of saying a line has no identity outside its order (see 08).

