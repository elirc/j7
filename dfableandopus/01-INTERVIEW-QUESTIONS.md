# Interview questions: Order Management System

28 questions with model answers, marked **junior** or **mid**. Every technical answer names a real file, line or symbol in this project.

## Group A: screening (7)

### A1. What is the data model? **junior**

Four persisted types and one enum. `Customer` (`src/oms/domain/Customer.java:6`): id, name, email, phone, createdAt, all final. `Product` (`src/oms/domain/Product.java:5`): id, name, sku, price, active, all final. `Order` (`src/oms/domain/Order.java:12`): id, customerId, a private `List<OrderItem>`, status, discountAmount, createdAt, updatedAt. `OrderItem` (`src/oms/domain/OrderItem.java:5`): productId, productName, quantity, unitPrice, lineTotal. `OrderStatus` (`src/oms/domain/OrderStatus.java:6`) is DRAFT, SUBMITTED, PAID, SHIPPED, CANCELLED.

The shape that matters is that `Order` is an aggregate root: items have no identity of their own, they are reached only through their order, and the order is the only thing that can change them.

### A2. Why does `OrderItem` store `productName` and `unitPrice` when `Product` already has them? **junior**

Because an order line is a historical fact, not a live view of the catalog. The values are copied once, at add time, in `OrderService.addProductToOrder`: `new OrderItem(product.getId(), product.getName(), quantity, product.getPrice())` (`src/oms/service/OrderService.java:58`). Change the catalog price tomorrow and yesterday's order still totals what the customer paid. The merge path keeps that promise too; adding more of the same product calls `item.increaseQuantity(...)` and the comment on `src/oms/domain/Order.java:57` says the line keeps its original price and name snapshot.

### A3. Trace "add a product to an order" end to end. **junior**

Menu 6 in `App.run` calls `addItemToOrder()` (`src/oms/App.java:75`), which prompts for the order id, product id and quantity and calls the service. `OrderService.addProductToOrder` looks the order up through `requireOrder` (`src/oms/service/OrderService.java:108`), refuses a non-editable order, refuses a non-positive quantity, resolves the product or throws "Product not found.", refuses an inactive product (line 54), builds the item and hands it to `order.addOrMergeItem(item)`. The aggregate then either merges into an existing line or adds a copy, and calls `touch()` (`src/oms/domain/Order.java:64`). Nothing is on disk until menu 12 or exit.

### A4. Where does validation live? **mid**

Three rings. `DomainValues` (`src/oms/domain/DomainValues.java:4`) holds the primitive rules: `text` rejects null or blank with "<field> is required.", and `money` normalizes to scale 2 with `RoundingMode.UNNECESSARY` and rejects negatives, and zero when `allowZero` is false. Constructors call it, so no `Product` or `OrderItem` can exist with bad money. The aggregate holds the relational rules: discount not greater than subtotal, a status that requires items cannot be empty, no duplicate product line. The services hold cross-aggregate rules: `OrderService`'s constructor refuses an order whose customer or product is missing (`src/oms/service/OrderService.java:27-29`).

### A5. What does the persisted file look like? **junior**

One file, `oms.snapshot.txt`, written by `FileDataStore.save` (`src/oms/persistence/FileDataStore.java:103`). A header `# oms-snapshot-v1`, then pipe-delimited rows tagged `C`, `P`, `O` and `I`, then a footer. You can read it in `data/sample/oms.snapshot.txt`: the `O|o-300|c-100|DRAFT|5.00|...` row is followed by its `I|o-300|p-200|Notebook|2|10.00` line, and the last line is `END|2|3|2|3`.

### A6. What is that footer for? **mid**

It is a record count, built by `footer(...)` as `END|customers|products|orders|items` (`src/oms/persistence/FileDataStore.java:99-102`). On load, the snapshot is parsed first and then the computed footer is compared to the file's last line; a mismatch throws "Missing footer or incorrect record counts." (`src/oms/persistence/FileDataStore.java:33`). It catches truncation and missing rows. State the limit in the same breath, because `astraupskill/VERIFICATION.md` does: it is not a cryptographic integrity check. `data/sample-corrupt/oms.snapshot.txt` is the same file with `END|2|3|2|2`, and it is refused.

### A7. When does data reach disk? **junior**

Only through `saveData()` (`src/oms/App.java:301`), reached from menu 12 via `saveNow()`, from menu 13 via `exitApplication()`, and from the EOF handler at `src/oms/App.java:89`. Everything else is in memory. A killed process loses the session, which `astraupskill/VERIFICATION.md` names as a limit.

## Group B: deep dive (10)

### B1. Describe the bug the worked change fixed. **mid**

An order with a 5.00 discount and one line of 2 at 10.00 reloaded with a zero discount and a total of 20.00. It was an initialization-order defect, not arithmetic. The old constructor accepted the saved discount, started with an empty item list, recalculated immediately, and clamped a discount larger than subtotal down to subtotal. Subtotal was still zero, so the discount became zero. The loader then added the saved items one at a time, but the discount was already gone.

### B2. What is the fix? **mid**

Never let the aggregate exist without its items. `Order.restore` (`src/oms/domain/Order.java:42`) takes status, discount, timestamps and the full item collection together and delegates to the private constructor, which copies each item, rejects a duplicate product id (line 34), sets the discount through `DomainValues.money`, then calls `validateSubtotal(getSubtotal())` on line 38 with a real subtotal. Clamping is gone: an impossible saved discount is now rejected with "Reduce the discount before reducing the subtotal." rather than silently repaired. The pinning test is "saved discount survives full order reload" (`test/oms/RegressionTests.java:30`), which asserts 5.00 and a total of 15.00 after a round trip.

### B3. Why is the private constructor private and the factory static? **mid**

Because the two ways of creating an order have different rules and the same signature would hide that. The public constructor (`src/oms/domain/Order.java:21`) is "a new draft for this customer": DRAFT, zero discount, no items, both timestamps equal. `restore` is "this order already existed": arbitrary status, a saved discount, saved timestamps, items. The comment on line 44 states the intent, restore the complete aggregate once and then validate the saved discount. A PAID order cannot be edited interactively but must be reconstructible, and "complete restoration preserves paid discount and timestamps" (`test/oms/RegressionTests.java:48`) is the test that says so.

### B4. What breaks if `getItems()` returns `items` instead of copies? **mid**

`getItems()` is `items.stream().map(OrderItem::copy).toList()` (`src/oms/domain/Order.java:127`). Return the live list and a caller can call `setQuantity` on a line, which recalculates that item's `lineTotal` while the order's discount validation never runs. The order's own `getSubtotal()` would then reflect a quantity no rule ever approved, so an order could hold a discount larger than its subtotal, the exact state `validateSubtotal` exists to prevent. "item getter cannot invalidate aggregate totals" (`test/oms/RegressionTests.java:32`) does precisely that and asserts the quantity is still 2 and the subtotal still 20.00.

### B5. `OrderService.findById` returns `Optional.ofNullable(...).map(Order::copy)`. Why the copy? **mid**

`ordersById` holds the authoritative aggregates (`src/oms/service/OrderService.java:19`). Without the copy on line 97 a caller could take the object out of the map and mutate it past every service check. The constructor is symmetric: it stores `order.copy()` on line 31, so an order handed in from outside cannot be changed behind the service's back afterwards. Two tests pin the two directions: "read order cannot mutate service state" (`test/oms/RegressionTests.java:31`) and "service constructor copies input order" (line 37).

### B6. Walk me through the status machine. **junior**

`OrderStatus.allowedTransitions` (`src/oms/domain/OrderStatus.java:17`) is a switch: DRAFT to SUBMITTED or CANCELLED; SUBMITTED to PAID or CANCELLED; PAID to SHIPPED or CANCELLED; SHIPPED and CANCELLED to nothing. Editability is separate: `isEditable()` is true only for DRAFT and SUBMITTED (`src/oms/domain/Order.java:123`). And SUBMITTED, PAID and SHIPPED require items, which is `requiresItems` on line 113. So a submitted order can still be edited but cannot be emptied; `removeItemByProductId` refuses the last line with "A submitted order must keep at least one item." (line 88).

### B7. `OrderService.updateItemQuantity` does not call `ensureEditable`. Is that a bug? **mid**

No, but it is worth noticing. Line 63 is just `requireOrder(orderId).setItemQuantity(productId, quantity);`, while the sibling methods `addProductToOrder`, `removeProductFromOrder` and `applyDiscount` all call `ensureEditable(order)` first. The rule is still enforced, because `Order.setItemQuantity` starts with `ensureEditable()` (`src/oms/domain/Order.java:68`) and the domain is the authority. The service-level check is a duplicate that produces a friendlier message earlier. The honest answer is that the invariant is safe and the inconsistency is a readability cost: a reader has to open the domain class to know.

### B8. A quantity change is rejected. What does the order look like afterwards? **mid**

Exactly as before, including `updatedAt`. `setItemQuantity` builds a candidate `OrderItem` first, computes the proposed subtotal as `getSubtotal().subtract(current.getLineTotal()).add(candidate.getLineTotal())` and validates it before `items.set(index, candidate)` (`src/oms/domain/Order.java:72-74`). `touch()` runs only after the replacement. "quantity reduction below discount rejects without mutation" (`test/oms/RegressionTests.java:42`) asserts the quantity, the discount and the timestamp are all unchanged. That last assertion is the interesting one: a rejected request must not look like a successful edit in an audit trail.

### B9. What is `Files.notExists` doing in the loader, and what is the fallback rule? **mid**

`FileDataStore.load` starts with `if (!Files.notExists(snapshotFile))` (`src/oms/persistence/FileDataStore.java:27`), a double negative that means "if the snapshot exists". In that branch the snapshot is authoritative and there is no fallback: a corrupt snapshot throws, it never retries the legacy files. Only when no snapshot exists does the loader look for the four legacy files, and it requires all four or none: `if (present != 0 && present != files.length) throw new IOException("Incomplete legacy dataset: all four files are required.")` (line 40). Two tests hold both halves, "corrupt canonical snapshot never falls back to legacy" (`test/oms/RegressionTests.java:62`) and "incomplete legacy data is rejected" (line 61).

### B10. Why one snapshot file instead of the previous four? **mid**

Because four writes are four chances to disagree. An interruption between `orders.txt` and `order_items.txt` left items from one generation and orders from another, and nothing detected it. Now `save` validates a complete `StoreSnapshot`, serializes every row, writes a temp sibling and calls `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` (`src/oms/persistence/FileDataStore.java:120`) with a `finally` that deletes the temp. A reader sees the whole old file or the whole new one. What it does not buy: power-loss durability, and safety between two processes. `astraupskill/VERIFICATION.md` says both.

### B11. Why does `StoreSnapshot`'s constructor build services it throws away? **mid**

`new OrderService(this.orders, new CustomerService(this.customers), new ProductService(this.products))` on `src/oms/persistence/StoreSnapshot.java:20` reuses the duplicate-id and missing-reference checks that already exist rather than writing a second copy of them that could drift. It runs on the way in and, because `FileDataStore.save` constructs a fresh `StoreSnapshot` from its argument (`src/oms/persistence/FileDataStore.java:106`), it also runs as a pre-write gate. "snapshot rejects missing customer and product references" (`test/oms/RegressionTests.java:51`) covers it from both sides, a missing customer and a missing product.

### B12. What breaks if you delete the duplicate check on `src/oms/domain/Order.java:34`? **mid**

`Order.restore` would happily build an order with two lines for the same product. Nothing downstream merges them, so `addOrMergeItem` would find the first one and increase it while the second stayed, `setItemQuantity` would silently replace only the first, and the subtotal would double-count. "restoration rejects duplicate product lines" (`test/oms/RegressionTests.java:52`) fails immediately. The wider point: a rule that the write path enforces must also be enforced on the read path, because a file can be hand-edited and a database can be written by another service.

## Group C: behavioral, STAR (5)

### C1. Tell me about a bug whose cause was not where the symptom was. **mid**

*Situation:* saving an order with a discount and reloading it produced the right items and the wrong money. *Task:* find out whether the defect was in the writer, the number format or the reader. *Action:* compared the written row against the reloaded object and found the file was correct, which pointed at reconstruction; read the old constructor and saw it recalculate totals while the item list was still empty, clamping the discount to a zero subtotal. *Result:* replaced piecemeal construction with `Order.restore`, and the failing case is now the first test in the suite (`test/oms/RegressionTests.java:30`). 7 tests failed against the original implementation; 35 pass now, with 7 cross-process CLI scenarios on top, 42 checks.

### C2. Tell me about a time you removed a convenience because it hid errors. **mid**

*Situation:* the old code clamped an out-of-range discount to the subtotal instead of refusing it. *Task:* decide whether to keep repairing bad data quietly. *Action:* made the aggregate refuse: `setDiscountAmount` throws "Discount cannot exceed subtotal." (`src/oms/domain/Order.java:101`) and restoration throws "Reduce the discount before reducing the subtotal." (line 117). *Result:* "restoration rejects impossible discount" (`test/oms/RegressionTests.java:49`) proves a bad saved value now surfaces instead of becoming a plausible wrong number. The cost is that a previously loadable file can now be refused, which is written down rather than discovered.

### C3. Tell me about closing an encapsulation hole. **mid**

*Situation:* services handed out live objects and live collections, so any caller could edit authoritative state. *Task:* make reads safe without copying everything on every call. *Action:* orders are copied on entry and exit (`src/oms/service/OrderService.java:31` and `97`), order items are copied on entry and exit (`src/oms/domain/Order.java:63` and `127`), and the immutable catalog types are returned through `List.copyOf` in `ProductService.all()` (`src/oms/service/ProductService.java:46`). *Result:* "clearing service all collection cannot delete records" (`test/oms/RegressionTests.java:35`) calls `.clear()` on the returned collection, tolerates the `UnsupportedOperationException`, and asserts the product is still there.

### C4. Tell me about a migration you had to make safe. **mid**

*Situation:* existing installations had four legacy files; the new format is one snapshot. *Task:* migrate without a moment where data could be lost. *Action:* the loader reads the legacy files into the same tagged-row pipeline (`src/oms/persistence/FileDataStore.java:41-47`), and the first save writes `oms.snapshot.txt` and leaves the old files untouched. From then on the snapshot is authoritative. *Result:* "legacy four-file migration preserves discount and original files" (`test/oms/RegressionTests.java:54`) asserts the discount survives, compares `orders.txt` byte for byte before and after the save, and then corrupts `orders.txt` to prove the snapshot is read instead. The limit, stated in `astraupskill/VERIFICATION.md`: old binaries do not understand the snapshot and must not be alternated against the same directory.

### C5. Tell me about a failure path you designed deliberately. **junior**

*Situation:* if saving fails at exit, quitting would discard the session. *Task:* make the failure recoverable. *Action:* `exitApplication` returns false when the save succeeded, so `running` becomes false and the loop ends; when the save fails it prints "Data could not be saved. Session stays open so you can retry." and returns true, keeping the menu alive (`src/oms/App.java:291-299`). *Result:* a failed save costs the user a retry rather than their work. `astraupskill/VERIFICATION.md` is explicit that this branch was source-reviewed rather than exercised by automated failure injection, and I would say that unprompted.

## Group D: the follow-ups an interviewer asks next (6)

### D1. "You said the domain validates. So why does `OrderService` check anything?" **mid**

Scope. The domain cannot know whether a customer exists, because an `Order` holds only a `customerId` string (`src/oms/domain/Order.java:14`). Cross-aggregate lookups belong in the service, which is why `addProductToOrder` resolves the product and refuses inactive ones (`src/oms/service/OrderService.java:52-56`) while `Order` never sees a `Product` at all. Keeping that boundary is what lets the aggregate be reconstructed from a file with no services around it.

### D2. "Map those refusals to HTTP." **junior**

`astraupskill/08-THE-SAME-ORDER-IN-CSHARP.md` has the table and I would use it verbatim. A discount larger than the subtotal is 422: the request was well formed and the rule refused it. An edit on a non-editable order and an illegal transition are 409, state conflicts. An unknown order, customer or product is 404. A malformed body is 400. A stale write once versioning exists is 409 from a concurrency check.

### D3. "There is no `Version` field here. How would you add one?" **mid**

Today the last writer wins the entire file, because every save rewrites the whole snapshot from `listOrders()` (`src/oms/App.java:306`). A version has to be enforced at the point of compare-and-write, not just stored. In the C# mapping that is `[ConcurrencyCheck] public int Version` on `Order` and a `DbUpdateConcurrencyException` turned into 409. Practice exercise 3 in `astraupskill/05-PRACTICE.md` makes the same point in Java and adds the warning worth repeating in the interview: adding a version field without enforcing it prevents nothing.

### D4. "Your timestamps come from `LocalDateTime.now()`. Problem?" **mid**

Two. It is untestable time, so a test that asserts "`updatedAt` did not move" relies on the rejection happening rather than on a controlled clock; `touch()` is `updatedAt = LocalDateTime.now()` (`src/oms/domain/Order.java:122`). And `LocalDateTime` has no zone, so two machines in different offsets produce incomparable values. Practice exercise 3 asks for an injected clock for the first reason; for a service I would also move to an instant with an explicit zone.

### D5. "The CLI formats money with HALF_UP. Does that contradict the domain?" **mid**

No, and the distinction is the point. Storage and arithmetic refuse imprecision: `DomainValues.money` uses `RoundingMode.UNNECESSARY` (`src/oms/domain/DomainValues.java:13`), so 0.001 throws "Money must use at most 2 decimal places." and "sub-cent product price is rejected" (`test/oms/RegressionTests.java:34`) pins it. Display rounds: `App.formatMoney` uses `HALF_UP` (`src/oms/App.java:389`). Rounding is a presentation decision; accepting an unrepresentable amount is a data decision.

### D6. "Why a hand-written pipe codec rather than JSON or CSV?" **mid**

For a dependency-free learning project a 60-line codec is reviewable in one sitting, and `DelimitedFileUtil` escapes exactly what can break a row: backslash, pipe, newline and carriage return (`src/oms/persistence/DelimitedFileUtil.java:54-63`). It refuses rather than guesses, throwing on an unknown escape (line 36) and an incomplete one (line 49), with "codec rejects unknown and incomplete escape" (`test/oms/RegressionTests.java:65`) covering both plus a round trip. At production scale the answer inverts: a database and a maintained serializer, because the interesting bugs move from parsing to concurrency.
