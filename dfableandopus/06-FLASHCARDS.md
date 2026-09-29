# Flashcards: Order Management System

38 cards. Cover the right column, answer aloud, then check. Every answer names something real in this project.

| Question | Answer |
|---|---|
| What is the aggregate root here? | `Order` (`src/oms/domain/Order.java:12`); items have no identity outside it |
| Which five statuses exist? | DRAFT, SUBMITTED, PAID, SHIPPED, CANCELLED (`src/oms/domain/OrderStatus.java:6-11`) |
| Which transitions leave DRAFT? | SUBMITTED or CANCELLED (`src/oms/domain/OrderStatus.java:19`) |
| Which statuses are terminal? | SHIPPED and CANCELLED, `EnumSet.noneOf` (line 22) |
| Which statuses are editable? | DRAFT and SUBMITTED only, `isEditable()` (`src/oms/domain/Order.java:123`) |
| Which statuses require items? | SUBMITTED, PAID, SHIPPED, `requiresItems` (`src/oms/domain/Order.java:113-115`) |
| What was the restoration bug? | The old constructor validated the saved discount against a still-empty item list and clamped it to zero |
| What replaced it? | `Order.restore`, one complete-aggregate entry point (`src/oms/domain/Order.java:42`) |
| Which test pins the fix? | "saved discount survives full order reload" (`test/oms/RegressionTests.java:30`), expecting 5.00 and 15.00 |
| How many tests failed against the original code? | 7 |
| What is the final suite size? | 35 Java regression tests, plus 7 CLI scenarios, 42 checks |
| What does `validateSubtotal` refuse? | A subtotal below the current discount: "Reduce the discount before reducing the subtotal." (`src/oms/domain/Order.java:117`) |
| What does `setDiscountAmount` refuse? | A discount above the subtotal: "Discount cannot exceed subtotal." (line 101) |
| Why build a candidate `OrderItem` in `setItemQuantity`? | So a rejected change never mutates the live line or `updatedAt` (`src/oms/domain/Order.java:72-75`) |
| What can a submitted order not do? | Lose its last item: "A submitted order must keep at least one item." (line 88) |
| What does `getItems()` return? | Copies, `items.stream().map(OrderItem::copy).toList()` (line 127) |
| What does `OrderService.findById` return? | A detached copy, `.map(Order::copy)` (`src/oms/service/OrderService.java:97`) |
| Where does an incoming order get copied? | `ordersById.putIfAbsent(order.getId(), order.copy())` (line 31) |
| Why does the order line store `productName` and `unitPrice`? | Price snapshot at add time (`src/oms/service/OrderService.java:58`); the catalog can move afterwards |
| What does merging more of the same product do to the price? | Keeps the original line price (`src/oms/domain/Order.java:57-58`) |
| Which rule blocks a retired product? | "Inactive products cannot be added to orders." (`src/oms/service/OrderService.java:55`) |
| Which method holds the money rules? | `DomainValues.money(value, allowZero)` (`src/oms/domain/DomainValues.java:10`) |
| Which rounding mode does storage use? | `RoundingMode.UNNECESSARY`, so 0.001 throws (line 13) |
| Which rounding mode does display use? | `HALF_UP`, in `App.formatMoney` (`src/oms/App.java:389`) |
| What is the snapshot file called? | `oms.snapshot.txt` (`src/oms/persistence/FileDataStore.java:22`) |
| What is the header line? | `# oms-snapshot-v1` (line 17) |
| What are the four row tags? | `C`, `P`, `O`, `I` |
| What does the footer look like? | `END|customers|products|orders|items`, for example `END|2|3|2|3` in `data/sample/oms.snapshot.txt` |
| What does a wrong footer produce? | "Missing footer or incorrect record counts." (`src/oms/persistence/FileDataStore.java:33`) |
| Is the footer an integrity check? | No. It detects missing records and truncation only; `astraupskill/VERIFICATION.md` says so |
| Does a corrupt snapshot fall back to legacy files? | Never (`test/oms/RegressionTests.java:62`) |
| What does a partial legacy dataset produce? | "Incomplete legacy dataset: all four files are required." (`src/oms/persistence/FileDataStore.java:40`) |
| How is the snapshot installed? | Temp sibling, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)` (line 120), temp deleted in `finally` |
| What does `StoreSnapshot`'s constructor do besides copy? | Builds throwaway services to reuse their duplicate and reference checks (`src/oms/persistence/StoreSnapshot.java:20`) |
| Which characters does the codec escape? | Backslash, pipe, newline, carriage return (`src/oms/persistence/DelimitedFileUtil.java:58-62`) |
| What does an unknown escape produce? | "Unknown escape sequence." (line 36); an unterminated one gives "Incomplete escape sequence." (line 49) |
| Which menu options write to disk? | 12 (save) and 13 (exit), plus the EOF handler (`src/oms/App.java:81-82` and `89`) |
| What happens if the save at exit fails? | "Data could not be saved. Session stays open so you can retry." and the menu keeps running (`src/oms/App.java:295-296`) |
| Which status codes does the C# mapping use? | 422 discount above subtotal, 409 non-editable edit, illegal transition and stale save, 404 unknown product or customer, 400 invalid body |
