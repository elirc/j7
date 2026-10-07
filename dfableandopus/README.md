# Interview pack: Order Management System (j7)

Hiring-loop material for the Java order-management CLI in `src/oms/`. The sibling course `../astraupskill/` teaches how the program works. This folder is about defending it in an interview for a CRUD web-application role.

## The pitch

An order with two units at 10.00 and a 5.00 discount reloaded from disk as a 20.00 order with no discount. The cause was an aggregate that was allowed to exist in an invalid intermediate state: the old constructor took the saved discount, started with an empty item list, recalculated, and clamped the discount down to a subtotal that was still zero. The items arrived afterwards, too late. I replaced piecemeal reconstruction with one entry point, `Order.restore` (`src/oms/domain/Order.java:42`), which takes the items with the order, rejects duplicate product lines, and validates the saved discount against the real subtotal. The same pass closed the ownership holes that made the totals editable from outside (`getItems()` now hands back copies, `src/oms/domain/Order.java:127`) and replaced a four-file save with one validated `oms.snapshot.txt` written to a temp sibling and moved atomically. 7 tests failed against the original code; the suite is now 35 Java cases plus 7 cross-process CLI scenarios, 42 checks.

## Target stacks and off-stack framing

Java 17, no frameworks, no database. Answer in Java and translate immediately, using `../astraupskill/08-THE-SAME-ORDER-IN-CSHARP.md`, which maps this project onto C# and EF Core: `OrderItem` as an `[Owned]` type, the restoration bug as a missing `Include` on a non-owned navigation, and each domain refusal as a status code (400, 404, 409, 422). For a Node interview the same mapping holds with Prisma nested writes in place of owned types; say which part of the mapping you are confident about and which you are not.

## Two-week use

- Days 1-2: `01-INTERVIEW-QUESTIONS.md` groups A and B with `src/oms/domain/Order.java` open.
- Days 3-4: the rest of group B; be able to draw the status machine from memory.
- Days 5-6: `03-CODE-READING-DRILL.md` cold, timed.
- Days 7-8: `02-STORIES-AND-RESUME.md` out loud until the restoration story fits in 90 seconds.
- Days 9-11: `04-TAKE-HOME.md` against `test/oms/RegressionTests.java`.
- Days 12-14: `05-SYSTEM-DESIGN-FOLLOWUPS.md`, with `06-FLASHCARDS.md` every day.

## Files

- [01-INTERVIEW-QUESTIONS.md](01-INTERVIEW-QUESTIONS.md)
- [02-STORIES-AND-RESUME.md](02-STORIES-AND-RESUME.md)
- [03-CODE-READING-DRILL.md](03-CODE-READING-DRILL.md)
- [04-TAKE-HOME.md](04-TAKE-HOME.md)
- [05-SYSTEM-DESIGN-FOLLOWUPS.md](05-SYSTEM-DESIGN-FOLLOWUPS.md)
- [06-FLASHCARDS.md](06-FLASHCARDS.md)
