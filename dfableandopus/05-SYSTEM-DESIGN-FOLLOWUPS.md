# Scaling questions: from one snapshot to an order service

Seven questions an interviewer asks after you have described the order system. Each answer starts from what this code does today, then names what it costs to change.

## Today, in one picture

```text
   single process, whole-store snapshot, save on demand

   App menu 1-11            services (in memory)          FileDataStore          disk
   [1]                      CustomerService                [2]
      |                     ProductService                    |                   |
      |                     OrderService                      |                   |
      |                          |                            |                   |
   startup ------------------->  |  <---- StoreSnapshot ----   | <-- read rows ----|
      |                     built from                      parse C/P/O/I rows,
      |                     snapshot lists                  compare END footer
      |                          |                            |                   |
   menu 6/10/11/14 ---------->  mutate the aggregate          |                   |
      |                     (nothing on disk yet)             |                   |
      |                          |                            |                   |
   menu 12 or 13 ------------>  listCustomers/Products/Orders  |                   |
      |                          | ------------------------->  validate whole store
      |                          |                            serialize every row
      |                          |                            write .oms-*.tmp ---|--> [temp]
      |                          |                            ATOMIC_MOVE --------|--> [oms.snapshot.txt]
```

[1] `src/oms/App.java:69`  [2] `src/oms/persistence/FileDataStore.java:16`

Every question below follows from one fact: the unit of write is the entire store.

## 1. Two dispatchers edit orders at the same time. What happens?

Both loaded a full store at startup and both write a full store at save. The `ATOMIC_MOVE` on `src/oms/persistence/FileDataStore.java:120` guarantees that no reader sees a half-written file, and guarantees nothing about lost work: the second saver silently discards everything the first one did, including orders they never touched. `astraupskill/VERIFICATION.md` states that no concurrent service use or multi-process file coordination was implemented or tested.

The fix is per-aggregate optimistic concurrency, not a lock on the store. Give `Order` a version, require the caller to send the version it read, and make the compare and the write one operation. `astraupskill/08-THE-SAME-ORDER-IN-CSHARP.md` shows the destination: `[ConcurrencyCheck] public int Version` and a `DbUpdateConcurrencyException` surfaced as 409. The trade-off is a new failure the client has to handle, and practice exercise 3 in `astraupskill/05-PRACTICE.md` names the trap: a version field that nothing enforces prevents nothing.

## 2. A million orders. Where does it break?

At startup and at every save, for the same reason. `FileDataStore.load` reads every line and builds every aggregate (`src/oms/persistence/FileDataStore.java:54-95`), then `StoreSnapshot`'s constructor walks them all again through the service constructors to recheck references (`src/oms/persistence/StoreSnapshot.java:20`), then `OrderService` copies every order into its map (`src/oms/service/OrderService.java:31`). Saving one discount change reserializes a million orders and their lines.

That is not a tuning problem. Move orders into a relational store with the item rows owned by the order, keep the domain rules where they are, and let the unit of work become one aggregate. The part worth defending in the interview is that the aggregate boundary already drawn here is exactly the boundary a repository wants: load an order with its items, or do not load it at all.

## 3. Pagination for the order list?

`listOrders()` returns a copy of every order (`src/oms/service/OrderService.java:100-102`) and `App.listOrders` prints them all. Because `ordersById` is a `LinkedHashMap` (`src/oms/service/OrderService.java:19`), the order of that list is insertion order, which is file order, which is stable but is not a business ordering.

For a real list you need a total order first, and the natural one here is `(createdAt, id)`, because `createdAt` alone ties for orders created in the same tick. Then keyset pagination on that pair: a page carries the last `(createdAt, id)` it saw, and the next query asks for rows after it. Offset pagination is easier to build and gets worse as the table grows, both in cost and in correctness, because a new order shifts every later page. Either way the index has to match the ordering or you have replaced a scan with a sort.

## 4. Multi-tenant. Where does the owner go?

Nowhere today. `Order` holds a `customerId` (`src/oms/domain/Order.java:14`), which identifies whose order it is, not who is allowed to see it. The whole directory belongs to whoever runs the process.

Add a tenant or operator identity taken from the authenticated session, never from the request, and apply it in every lookup, including nested routes like "set the quantity of a line". That last part is where this design is most exposed: `updateItemQuantity` takes an order id and a product id (`src/oms/service/OrderService.java:62`), so an unscoped implementation lets a caller reach any order by guessing its id. Practice exercise 7 asks for exactly that test, changing an order id and proving you cannot reach another owner's order. Put the scoping in one repository method, because a per-handler check will eventually be forgotten in the handler nobody reviews.

## 5. What is the first background job, and what is the trap?

There are no asynchronous effects at all right now; every transition happens between two prompts on one thread. The first realistic job is notification on a status change, because `updateStatus` (`src/oms/service/OrderService.java:81`) is where a business event actually occurs.

The trap is the dual write. If the handler changes the status and then publishes a message, a crash between the two leaves a PAID order nobody was told about, and a retry of the whole handler can send two messages for one transition. Practice exercise 6 gives the shape of the answer: store the dispatch intent in the same transaction as the accepted transition and dispatch from that record afterwards, then test a broker outage and a retry without duplicating the business transition. Say the honest version of what exists today as well: the PAID to CANCELLED transition allowed by `src/oms/domain/OrderStatus.java:21` is a local status change, and `astraupskill/VERIFICATION.md` warns against presenting it as a completed refund workflow.

## 6. Observability: what do you instrument first?

The failure signal today is text. `App.main` prints "Startup stopped: " and a message when loading throws (`src/oms/App.java:52`), and `astraupskill/VERIFICATION.md` names the gap: there is no structured exit-code contract for automation. In fact `main` just returns after the message, so a refused startup exits 0, exactly like success. That is the cheapest first fix and it costs nothing in design.

After that, instrument the refusals, because in an order system the refusals are the product. Count rejections by rule: discount above subtotal, edit on a non-editable order, illegal transition, unknown product. A rising "edit on a non-editable order" rate means your UI is showing controls it should not; a rising conflict rate once versioning exists means your concurrency model is mismatched to how people actually work. Keep customer names and emails out of the logs; they are in `Customer` (`src/oms/domain/Customer.java:9`) and they do not belong in a metrics pipeline.

## 7. Port it to the target stack. What is the first thing you get wrong?

Loading an order without its items. That is the same bug this project already fixed once: the old constructor validated totals against an empty item list. In EF Core it comes back as a missing `Include` on a regular (non-owned) navigation, as `astraupskill/08-THE-SAME-ORDER-IN-CSHARP.md` spells out: `Subtotal` computes as zero and every rule that reads `Items` runs against an empty list, so "cannot submit an empty order" fires on an order that has four lines. Owned collections don't have this failure, because EF Core loads owned types with their owner automatically.

The structural defence is the same in both languages: one entry point that always loads the aggregate whole. In Java that is `Order.restore` (`src/oms/domain/Order.java:42`); in C# it is a repository method that always includes the items so no caller can forget. Model `OrderItem` as an owned type so it cannot be fetched or saved independently, and keep `UnitPrice` on the line with an explicit precision of 18,2 rather than joining the current catalog price, which would recompute history every time the catalog moves. Then write the two tests the chapter ends with: a regular navigation loaded without `Include` refuses the discount, while the owned mapping loads the lines and keeps the total right after a reload.
