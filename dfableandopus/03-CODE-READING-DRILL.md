# Code reading drill: Order Management System

Six drills for a live screen-share round. Work the excerpt before opening the answer. Drills 3 and 6 contain code that has been deliberately altered; everything else is copied from this repository at the lines given.

## Drill 1: explain this method

From `src/oms/domain/Order.java:67-80`.

```java
    public void setItemQuantity(String productId, int quantity) {
        ensureEditable();
        for (int index = 0; index < items.size(); index++) {
            OrderItem current = items.get(index);
            if (current.getProductId().equals(productId)) {
                OrderItem candidate = new OrderItem(productId, current.getProductName(), quantity, current.getUnitPrice());
                validateSubtotal(getSubtotal().subtract(current.getLineTotal()).add(candidate.getLineTotal()));
                items.set(index, candidate);
                touch();
                return;
            }
        }
        throw new IllegalArgumentException("Product was not found in the order.");
    }
```

Name the four things that happen before the list is touched, and say why `candidate` is built rather than `current.setQuantity(quantity)` being called.

<details>
<summary>Answer</summary>

Before any mutation: the order is checked as editable, the line is located, a candidate line is constructed (which runs `OrderItem`'s own rules, so a quantity of zero throws "Quantity must be greater than zero." from `src/oms/domain/OrderItem.java:28`), and the proposed subtotal is validated against the current discount. `items.set` and `touch()` run only after all four succeed.

Calling `current.setQuantity(quantity)` would work for the happy path and be wrong for every other. `setQuantity` recalculates `lineTotal` in place (`src/oms/domain/OrderItem.java:31`), so by the time `validateSubtotal` rejected the change the order would already hold the new number. Building a candidate keeps the order in its old, valid state until the new state is known to be valid too. Note the arithmetic: the proposed subtotal is computed by swapping one line total, not by summing the list twice.

The observable consequence is that a rejection leaves `updatedAt` alone, which "quantity reduction below discount rejects without mutation" (`test/oms/RegressionTests.java:42`) asserts alongside the quantity and the discount.
</details>

## Drill 2: explain this loader branch

From `src/oms/persistence/FileDataStore.java:24-35`.

```java
    public StoreSnapshot load() {
        try {
            var rows = new ArrayList<String>();
            if (!Files.notExists(snapshotFile)) {
                List<String> lines = Files.readAllLines(snapshotFile);
                if (lines.size() < 2 || !HEADER.equals(lines.get(0))) throw new IOException("Missing or unsupported snapshot header.");
                rows.addAll(lines.subList(1, lines.size() - 1));
                StoreSnapshot snapshot = parseRows(rows);
                String expected = footer(snapshot);
                if (!expected.equals(lines.get(lines.size() - 1))) throw new IOException("Missing footer or incorrect record counts.");
                return snapshot;
            }
```

Read the condition carefully. Then say why the footer is checked after parsing rather than before.

<details>
<summary>Answer</summary>

`!Files.notExists(snapshotFile)` is a double negative: this branch runs when the snapshot exists. It is not the same as `Files.exists(...)` in principle, because both methods can return false when the file's status cannot be determined, but here it reads as "the snapshot is present, so it is authoritative".

The row range `subList(1, lines.size() - 1)` drops the header and the footer, so `parseRows` sees only C, P, O and I records. The footer must be checked afterwards because `footer(snapshot)` is computed *from the parsed snapshot* (`src/oms/persistence/FileDataStore.java:99-102`): it counts customers, products, orders and the items belonging to those orders. There is nothing to compare against until the rows have become objects. A truncated file usually fails the comparison because the last surviving line is not an `END|...` row at all, which is the first half of "truncation and bad counts reject snapshot" (`test/oms/RegressionTests.java:63`); the second half rewrites the footer to `END|1|1|1|0` and expects that to be refused too.

The other half of the method is the part to mention unprompted: this branch returns or throws, so a corrupt snapshot never reaches the legacy fallback below it (`test/oms/RegressionTests.java:62`).
</details>

## Drill 3: spot the bug

This `addOrMergeItem` is **not** the code in the repository. One line differs. Find it, and describe a sequence that shows the damage.

```java
    public void addOrMergeItem(OrderItem newItem) {
        ensureEditable();
        Objects.requireNonNull(newItem, "Item is required.");
        for (OrderItem item : items) {
            if (item.getProductId().equals(newItem.getProductId())) {
                item.increaseQuantity(newItem.getQuantity());
                touch();
                return;
            }
        }
        items.add(newItem);
        touch();
    }
```

<details>
<summary>Answer</summary>

The real line is `items.add(newItem.copy());` (`src/oms/domain/Order.java:63`). The mutated version stores the caller's object.

The damage needs two steps. A caller builds an `OrderItem`, adds it to the order, and then keeps its reference and calls `setQuantity(50)` on it. That method is public (`src/oms/domain/OrderItem.java:26`) and recalculates `lineTotal` in place, so the order's subtotal jumps by 480.00 without `ensureEditable`, without `validateSubtotal`, and without moving `updatedAt`. An order can now hold a discount larger than its subtotal, which is the exact state `validateSubtotal` (`src/oms/domain/Order.java:116`) exists to make unreachable.

It is worse than it looks in a service: `OrderService.addProductToOrder` constructs the item locally and drops the reference (`src/oms/service/OrderService.java:58`), so the service path looks fine and only a direct domain caller, or a future caller that keeps the item to log it, triggers the bug. The copy on the way in is what makes the guarantee independent of caller discipline, and `getItems()` copies on the way out for the same reason (`src/oms/domain/Order.java:127`).
</details>

## Drill 4: predict the outcome

An order has one line, 2 units at 10.00, and a 15.00 discount. A user picks menu 14 and asks for quantity 1. What is printed, and what is the state afterwards? Then answer the same question with the discount at 5.00.

<details>
<summary>Answer</summary>

Printed: `Could not update quantity: Reduce the discount before reducing the subtotal.` Afterwards the quantity is still 2, the discount is still 15.00, and `updatedAt` has not moved. That is the case the suite uses (`test/oms/RegressionTests.java:42`).

The path is `App.updateItemQuantity` (`src/oms/App.java:270`) to `OrderService.updateItemQuantity` (`src/oms/service/OrderService.java:62`) to `Order.setItemQuantity`. The candidate line total is 10.00, so the proposed subtotal is 20.00 minus 20.00 plus 10.00, which is 10.00. With a 15.00 discount that trips `validateSubtotal` and the message comes from `src/oms/domain/Order.java:117`. With a 5.00 discount, 10.00 is still above it, so the change is accepted, `items.set` runs and the order prints "Item quantity updated." with a total of 5.00.

The reason to work the arithmetic rather than pattern-match the message is that the rule is about the *proposed* subtotal, not about reduction in general. An interviewer who has read the code will ask for the numbers.
</details>

## Drill 5: predict the control flow

Menu 13 is chosen and the save fails because the data directory has been made read-only. Trace `run()`.

<details>
<summary>Answer</summary>

`case "13" -> running = exitApplication();` (`src/oms/App.java:82`). Inside, `saveData()` returns false, so the else branch prints "Data could not be saved. Session stays open so you can retry." and returns `true` (`src/oms/App.java:295-296`). `running` stays true and the menu loop repeats, so the user keeps the session and can fix the directory and press 13 again. On success the method falls through to `return false` on line 298 and the loop ends.

The naming is worth flagging out loud in an interview: `exitApplication` returns false when the application is going to exit. It is a "keep running" flag assigned to `running`, and reading it as "did we exit" inverts the logic. That is the kind of observation an interviewer is listening for, along with the honest caveat from `astraupskill/VERIFICATION.md` that this retry branch was source-reviewed rather than exercised by automated failure injection, while the storage replacement failure itself was injected (`test/oms/RegressionTests.java:66`).
</details>

## Drill 6: review this diff

A teammate adds order cancellation to the service. Find three defects.

```diff
--- a/src/oms/service/OrderService.java
+++ b/src/oms/service/OrderService.java
@@
+    public void cancelOrder(String orderId) {
+        Order order = ordersById.get(orderId);
+        order.setStatus(OrderStatus.CANCELLED);
+        for (OrderItem item : order.getItems()) {
+            order.removeItemByProductId(item.getProductId());
+        }
+        ordersById.remove(orderId);
+    }
```

<details>
<summary>Answer</summary>

1. `ordersById.get(orderId)` with no null check. Every other method in this class goes through `requireOrder`, which throws "Order not found." (`src/oms/service/OrderService.java:108-114`). As written, an unknown id is a `NullPointerException` rather than a domain error, which in a web adapter is a 500 instead of a 404.
2. The loop cannot work and should not be attempted. `setStatus(CANCELLED)` has already run, and CANCELLED is not editable (`isEditable()` is DRAFT or SUBMITTED only, `src/oms/domain/Order.java:123`), so `removeItemByProductId` throws from `ensureEditable`. Even if the order were reordered, stripping the lines destroys the record of what was cancelled, and the snapshot's line rows are the only history this system keeps.
3. `ordersById.remove(orderId)` deletes the order outright, which contradicts the reason CANCELLED exists as a status at all. Practice exercise 2 in `astraupskill/05-PRACTICE.md` says it directly: submitted, paid, shipped and cancelled records are preserved as history, and deletion is for DRAFT orders under a documented rule.

A fourth remark, not a defect: `updateStatus` already does this job and does it better, refusing a no-op transition with "Order is already in that status." and an illegal one with a message naming both states (`src/oms/service/OrderService.java:84-89`). The right review comment is "call `updateStatus(orderId, CANCELLED)`", not "fix these three lines".
</details>
