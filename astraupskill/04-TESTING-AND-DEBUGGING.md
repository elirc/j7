# Test invariants across commands, copies, and reloads

The project-local command `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test` compiles Java 17 source and runs 35 named regression cases. The harness has no external test dependencies. Filesystem tests create checked temporary directories, observe real files, and remove only their own test data. A nonzero exit indicates failure; printed output alone is not a substitute for checking the process result.

According to the delivery record, seven initial cases failed against the original source (the pre-fix code is not in this repo, so you can't rerun that step): saved discounts disappeared, returned orders could mutate service state, returned items could invalidate aggregate totals, paid orders remained editable, sub-cent prices were accepted, live service collections could delete records, and carriage returns were dropped by the field codec. The final suite preserves these fixes and adds tests for the related boundaries discovered during implementation.

For command tests, assert both rejection and preservation. A quantity reduction below the active discount must leave quantity, discount, total, and update time unchanged. An invalid status transition must leave the old status intact. An attempted mutation through a returned copy must leave the authoritative service unchanged. These are more meaningful than checking that a private helper was called.

```java
var before = service.findById("O").orElseThrow();
rejects(IllegalArgumentException.class,
    () -> service.updateItemQuantity("O", "P", 1));
var after = service.findById("O").orElseThrow();
equal(before.getUpdatedAt(), after.getUpdatedAt());
equal(before.getTotal(), after.getTotal());
```

This sketch assumes the starting discount makes quantity 1 invalid. Always arrange the state explicitly; an exception caused by a missing order would not prove discount protection.

For restoration debugging, put a breakpoint where `FileDataStore.parseRows` calls `Order.restore`. Inspect the entire item collection before stepping into the constructor. The discount should be compared with the final subtotal, and no interactive method should update the saved timestamp. Then test a paid order, because restoring only drafts can hide an accidental dependency on editable status.

For persistence, use a real save followed by a new load. The suite verifies four-file legacy migration, authority of the new snapshot, missing footer/count errors, orphan item rejection, strict booleans, and a failed atomic move. A footer mismatch detects ordinary truncation or missing records, but it is not a checksum and does not prove resistance to intentional edits that also change counts.

The delivery also ran seven CLI scenarios in separate Java processes. That script was not committed; [practice](05-PRACTICE.md) has a runnable `node --test` harness in the same style. The scenarios were: catalog reload, discount reload, quantity persistence, precision rejection, paid edit refusal, EOF handling, and corrupt-startup byte preservation. The [manual trace](07-TRACE-LAB.md) lets you inspect these behaviors interactively. The normal exit retry branch was source-reviewed; the automated failure test targets the storage replacement operation, not a live terminal retry interaction.

No concurrent service access, browser, HTTP route, database transaction, or payment provider was tested. The application is intentionally single-threaded. Before making it a web server, add tests that match the new concurrency and persistence boundaries instead of assuming this local suite proves them.

## Without PowerShell

```bash
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out oms.RegressionTests            # tests: last line is RESULT passed=35 failed=0
cp -r data/sample /tmp/oms-sample           # data/sample/oms.snapshot.txt is tracked; save writes over it
java -cp out oms.App /tmp/oms-sample        # the app against a copy of the sample directory
```

Two behaviours to keep in mind when you script the CLI. First, piped input ends in EOF, and EOF saves (`src/oms/App.java:87-89`), so a scripted run persists every command that completed. Second, a startup that refuses a corrupt file still exits with status 0 (`src/oms/App.java:51-54`). Read stdout for `Startup stopped:` rather than trusting the exit code. [Practice](05-PRACTICE.md) exercise 2 fixes this.

## Break it on purpose: which test catches it?

Make each change in a scratch copy of `src` and `test`, run `oms.RegressionTests`, and compare the failures with these predictions. Both were run on 2026-10-06 against the delivered code.

| Change | Tests that fail | Lesson |
|---|---|---|
| `Order.getItems` returns the live list: `return items;` (`src/oms/domain/Order.java:127`) | 1 of 35: *item getter cannot invalidate aggregate totals* (`Expected 2; got 9`) | Only one test guards item ownership. *read order cannot mutate service state* still passes because `OrderService.findById` copies the whole order first (`src/oms/service/OrderService.java:97`). |
| Reintroduce the original bug: assign and validate the discount *before* the item loop in the `Order` constructor (`src/oms/domain/Order.java:32-38`) | 5 of 35: *saved discount survives full order reload*, *quantity reduction below discount rejects without mutation*, *complete restoration preserves paid discount and timestamps*, *legacy four-file migration preserves discount and original files*, *repeated save persists changed quantity and discount together* | The rejection now replaces the old clamp, so the bug fails loudly with `Reduce the discount before reducing the subtotal.` instead of silently zeroing the discount. The quantity test fails too because its setup goes through `copy()`, which uses the same constructor. |

