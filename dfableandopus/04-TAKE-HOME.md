# Take-home: catalog updates that cannot rewrite history

Time box: 3 to 4 hours. Work in this repository. No new dependencies, no build tool, no network.

## The brief as a company would write it

Our catalog is write-once. `ProductService` can create a product (`src/oms/service/ProductService.java:23`) and look one up, and that is all; `Product` is final in every field (`src/oms/domain/Product.java:5-10`). Operations need to correct a typo in a product name, change a price, and retire a product so it stops appearing on new orders. They cannot do any of that today without hand-editing `oms.snapshot.txt`.

The risk is obvious to anyone who has seen this go wrong once: a catalog change must never alter what an existing order says it sold. Our order lines already keep their own copy of the name and price, taken at add time in `OrderService.addProductToOrder` (`src/oms/service/OrderService.java:58`), and that property is the one thing this exercise must not break.

## Scope

**1. `ProductService.updateProduct(String id, String name, String sku, BigDecimal price, boolean active)`.**

Replace the stored immutable `Product` with a new one carrying the same id. Reuse the existing validation rather than re-implementing it: `DomainValues.money(value, false)` already rejects a sub-cent or non-positive price (`src/oms/domain/DomainValues.java:10`) and `DomainValues.text` already rejects a blank name or SKU. An unknown id must produce the same kind of domain error the rest of the codebase throws, not a `NullPointerException`; compare `OrderService.requireOrder` (`src/oms/service/OrderService.java:108`).

Decide and document your SKU policy. Today SKU uniqueness is not enforced anywhere, and `astraupskill/VERIFICATION.md` says so. Either enforce it and write the duplicate-SKU test, or state in your notes that you deliberately left it unenforced and why.

**2. A CLI option for it.**

Add it to `printMenu` and the `switch` in `run()` (`src/oms/App.java:95` and `69-85`). Follow the existing shape: `readMoney` returns null and prints guidance on a bad amount (`src/oms/App.java:366`), `readYesNo` does the same for the active flag (line 376), and a caught `IllegalArgumentException` prints "Could not ..." plus the message. Do not introduce a second style of error reporting.

**3. Persistence.**

The change must survive a save and load. `FileDataStore` already writes every product row through `DelimitedFileUtil.join` (`src/oms/persistence/FileDataStore.java:110`), so this should require no format change. If you believe it does, say why in your notes before you change the format.

## Acceptance criteria

1. An order created at 10.00 still shows 10.00 on its line, and a total unchanged, after the catalog price becomes 12.00. A newly created order for the same product captures 12.00.
2. Adding more of the same product to the existing order after the price change keeps the line at 10.00, because `addOrMergeItem` merges into the existing line (`src/oms/domain/Order.java:52-65`). Assert the resulting line total explicitly so the policy is pinned rather than incidental.
3. Retiring a product with `active = false` makes `addProductToOrder` refuse it with "Inactive products cannot be added to orders." (`src/oms/service/OrderService.java:55`) while existing lines are untouched.
4. An update with a price of `0.001`, an empty name, or an unknown id is refused and leaves the stored product exactly as it was.
5. Save, reload through `FileDataStore.load`, and see the updated catalog and the unchanged order totals in the same assertion.
6. All 35 existing cases in `test/oms/RegressionTests.java` still pass, unmodified.

## What to submit

- The diff, as a patch or a branch.
- `NOTES.md`, one page at most: your SKU decision, your merge-after-price-change decision, what you did not do, and the first thing you would fix with another hour.
- The captured test output: the `PASS`/`FAIL` lines and the final `RESULT passed=N failed=0` printed by `test/oms/RegressionTests.java:71`.

Run the suite with:

```
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test
```

or, without PowerShell:

```
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out oms.RegressionTests
```

Add your cases to `test/oms/RegressionTests.java` with the existing helpers: `test(name, check)` at line 13, `equal` at 14, `rejects` at 15, the fixtures `customer()`, `product()`, `order()` and `service(order)` at lines 18-21, and `withDirectory` at line 23, which creates a checked temporary directory and deletes it afterwards. Use `withDirectory` for anything that touches a file; do not write into `data/`.

## Grading rubric

| Criterion | Strong | Weak |
|---|---|---|
| History is immutable | A test proves an existing line keeps its old price and the order total is unchanged after the catalog moves | Only asserts that the product changed |
| Validation reuse | Calls `DomainValues` and the `Product` constructor so the rules cannot drift | Re-implements the scale and blank checks inside the service |
| Error vocabulary | Unknown id throws the same domain exception type the rest of the code uses, with a message in the same voice | `NullPointerException`, or a new bespoke exception type for one method |
| Merge policy | The decision about merging after a price change is stated in `NOTES.md` and pinned by an assertion | Left to whatever the code happens to do |
| Persistence proof | A round trip through `FileDataStore.save` and `load` inside `withDirectory` | Asserts only in memory |
| Regression safety | 35 prior cases pass untouched | An existing case was edited or deleted to go green |
| Honesty | Names what is still unenforced, for example SKU uniqueness or concurrent editors | Claims catalog versioning or audit history that does not exist |

## Reviewer's notes

The first thing I open is your test for criterion 1, and I check that it asserts the *order* rather than the product. A submission that proves the catalog changed has proved the easy half.

Second, I look at where the validation lives. If `updateProduct` builds a `Product` through its constructor then every rule in `src/oms/domain/Product.java:12-18` applies for free and stays applied when someone adds a rule later. If it assigns fields directly, or the class has gained setters, the exercise has been solved by weakening the thing that made the codebase trustworthy.

Third, `NOTES.md`. There is a real decision hiding in criterion 2 and I want to see you take it deliberately. Keeping the original price on a merge is defensible, treating a price change as a new line is defensible, and silently averaging is not. Tell me which you chose.

Last, the snapshot. If you changed the file format, I want the footer still to agree, because `load` recomputes it and compares (`src/oms/persistence/FileDataStore.java:32-33`), and I want the old sample in `data/sample/oms.snapshot.txt` either to still load or to have an explicit migration rule. A format change with no migration story is the failure mode this codebase already learned about once.
