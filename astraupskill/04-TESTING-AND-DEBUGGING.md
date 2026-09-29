# Test invariants across commands, copies, and reloads

The project-local command `powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Test` compiles Java 17 source and runs 35 named regression cases. The harness has no external test dependencies. Filesystem tests create checked temporary directories, observe real files, and remove only their own test data. A nonzero exit indicates failure; printed output alone is not a substitute for checking the process result.

Seven initial cases failed against the original source: saved discounts disappeared, returned orders could mutate service state, returned items could invalidate aggregate totals, paid orders remained editable, sub-cent prices were accepted, live service collections could delete records, and carriage returns were dropped by the field codec. The final suite preserves these fixes and adds tests for the related boundaries discovered during implementation.

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

The delivery also ran seven CLI scenarios in separate Java processes: catalog reload, discount reload, quantity persistence, precision rejection, paid edit refusal, EOF handling, and corrupt-startup byte preservation. The [manual trace](07-TRACE-LAB.md) lets you inspect these behaviors interactively. The normal exit retry branch was source-reviewed; the automated failure test targets the storage replacement operation, not a live terminal retry interaction.

No concurrent service access, browser, HTTP route, database transaction, or payment provider was tested. The application is intentionally single-threaded. Before making it a web server, add tests that match the new concurrency and persistence boundaries instead of assuming this local suite proves them.

## Without PowerShell

```bash
javac --release 17 -Xlint:all -d out $(find src test -name '*.java')
java -cp out oms.RegressionTests            # tests
java -cp out oms.App data/sample            # the app against the sample directory
```

