# Practice ladder toward a CRUD order-management web app

These exercises extend the delivered code; none of them describes an existing feature. Each runnable exercise has a **Goal** and a **Check**. The checks drive the real CLI in a fresh JVM against a disposable copy of `data/sample` (two customers, three products, DRAFT order `o-300` with a 5.00 discount, PAID order `o-301`), so they test behaviour rather than method names.

## Set up the checks

Save the file below as `practice/oms-cli.test.mjs` in your working copy (leave it uncommitted) and run it from the project root with JDK 17+ and Node 18+ on PATH:

```bash
node --test practice/oms-cli.test.mjs
```

It compiles `src` and `test` into the OS temp directory, never into the repo. Every case spawns several JVMs, so expect a minute or two per run. Piped input ends with EOF, and the App saves on EOF (`src/oms/App.java:87-89`), so a check that runs the CLI twice against the same copy also proves the change persisted.

```js
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { cpSync, mkdtempSync, readdirSync, readFileSync, statSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';

const ROOT = resolve(process.env.OMS_ROOT ?? '.');
const work = mkdtempSync(join(tmpdir(), 'oms-practice-'));
const classes = join(work, 'classes');
const sources = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name);
  return statSync(path).isDirectory() ? sources(path) : path.endsWith('.java') ? [path] : [];
});
execFileSync('javac', ['-encoding', 'UTF-8', '--release', '17', '-d', classes,
  ...sources(join(ROOT, 'src')), ...sources(join(ROOT, 'test'))]);

let copies = 0;
function copyData(name) {            // a disposable copy of data/sample or data/sample-corrupt
  const target = join(work, `${name}-${++copies}`);
  cpSync(join(ROOT, 'data', name), target, { recursive: true });
  return target;
}
function cli(dataDir, lines) {        // EOF at the end of input triggers the App's save-on-EOF path
  const r = spawnSync('java', ['-cp', classes, 'oms.App', dataDir],
    { input: lines.join('\n') + '\n', encoding: 'utf8' });
  return { out: r.stdout, status: r.status };
}

test('0. warm-up: sample totals are derived from restored lines', () => {
  const { out } = cli(copyData('sample'), ['8', 'o-300', '8', 'o-301']);
  assert.match(out, /Subtotal: \$20\.00\s+Discount: \$5\.00\s+Total: \$15\.00/);
  assert.match(out, /Subtotal: \$45\.00\s+Discount: \$0\.00\s+Total: \$45\.00/);
});

test('1. duplicate SKU is refused after trimming and case-folding', () => {
  const { out } = cli(copyData('sample'), ['3', 'Copy', ' nb-001 ', '1.00', 'y']);
  assert.match(out, /Could not create product/);
});

test('2. corrupt startup exits nonzero and leaves the file untouched', () => {
  const dir = copyData('sample-corrupt');
  const before = readFileSync(join(dir, 'oms.snapshot.txt'));
  const { status } = cli(dir, ['13']);
  assert.notEqual(status, 0);
  assert.deepEqual(readFileSync(join(dir, 'oms.snapshot.txt')), before);
});

test('3. unknown status input gets a domain message, not a Java enum error', () => {
  const { out } = cli(copyData('sample'), ['11', 'o-300', 'teleported']);
  assert.doesNotMatch(out, /No enum constant/);
  assert.match(out, /Unknown status/);
});

test('4. menu 15 repricing keeps existing lines at their snapshot price', () => {
  const dir = copyData('sample');
  const first = cli(dir, ['15', 'p-200', '12.00', '5', 'c-101']);
  const orderId = first.out.match(/Created order with ID: (\S+)/)[1];
  const { out } = cli(dir, ['6', orderId, 'p-200', '1', '8', 'o-300', '8', orderId]);
  assert.match(out, /productId=p-200 \| qty=2 \| unit=\$10\.00/);   // o-300 keeps 10.00
  assert.match(out, /productId=p-200 \| qty=1 \| unit=\$12\.00/);   // new order captures 12.00
});

test('5. menu 16 deletes DRAFT orders only, and the deletion survives restart', () => {
  const dir = copyData('sample');
  const { out } = cli(dir, ['16', 'o-301', 'DELETE', '16', 'o-300', 'DELETE']);
  assert.match(out, /Could not delete order/);                     // o-301 is PAID
  const after = cli(dir, ['9']).out;
  assert.doesNotMatch(after, /Order ID: o-300/);
  assert.match(after, /Order ID: o-301/);
});

test('6. your RegressionTests include a passing stale-version case', () => {
  const r = spawnSync('java', ['-cp', classes, 'oms.RegressionTests'], { encoding: 'utf8' });
  assert.equal(r.status, 0, r.stdout);
  assert.match(r.stdout, /PASS stale version/);
});
```

**State on the delivered code (run 2026-10-06, JDK 17.0.10, Node 22.16):** case 0 passes; cases 1-6 fail, which is the starting point. All seven passed against a scratch copy carrying a reference implementation of exercises 1-6, so every check can be satisfied without editing the check itself. Don't loosen an assertion to make it pass. If you choose a different message or menu number, change the check first and note why.

## 0. Read the sample before changing anything

**Goal:** predict both orders' subtotal, discount and total from `data/sample/oms.snapshot.txt` before you run anything. `o-301` has two lines (4 x 2.50 and 1 x 35.00).
**Check:** case 0 passes. If your prediction was wrong, trace `Order.getSubtotal` (`src/oms/domain/Order.java:129`) before moving on.

## 1. Enforce SKU uniqueness

**Goal:** `ProductService.createProduct` (`src/oms/service/ProductService.java:23-35`) accepts any SKU today. Add a duplicate-SKU refusal and pick a normalization policy: the check expects `" nb-001 "` to collide with `NB-001`. Decide whether `FileDataStore` load should also reject a snapshot holding duplicate SKUs, and add a `RegressionTests` case for whichever you choose.
**Check:** case 1 passes and `RegressionTests` still reports `failed=0`.

## 2. Make startup failure visible to automation

**Goal:** when loading throws, `App.main` prints a message and then just `return`s (`src/oms/App.java:51-54`), so the process exits 0, the same as success. A script or CI job cannot tell the two apart. Exit with a nonzero status and keep the "file not replaced" guarantee.
**Check:** case 2 passes. It asserts both the exit status and the unchanged bytes of the corrupt copy.

## 3. Translate status input at the boundary

**Goal:** `updateOrderStatus` passes raw input to `OrderStatus.valueOf` (`src/oms/App.java:261-262`). An unknown word prints `Could not update order status: No enum constant oms.domain.OrderStatus.TELEPORTED`, which leaks a Java class name to the user. Parse the input explicitly and print `Unknown status: ...` that lists the valid values.
**Check:** case 3 passes.

## 4. Catalog repricing with stable order snapshots

**Goal:** add menu 15 "Update product price" (prompts: product ID, new price). `Product` is immutable, so replace the map entry while keeping the ID. Existing lines keep the price they captured in `OrderService.addProductToOrder` (`src/oms/service/OrderService.java:58`), and a new order captures the new price. Write down what merging more units into an existing line should mean after repricing. The current merge keeps the old price (`src/oms/domain/Order.java:55-61`).
**Check:** case 4 passes. It reprices `p-200` to 12.00, then shows `o-300` still at `unit=$10.00` and a new order at `unit=$12.00`.

## 5. Delete DRAFT orders only

**Goal:** add menu 16 "Delete draft order" (prompts: order ID, then `DELETE` to confirm). Refuse every non-DRAFT status with a message starting `Could not delete order`. Decide whether a draft with items may be deleted. The check deletes `o-300`, which has one line. Submitted, paid, shipped and cancelled orders stay as history.
**Check:** case 5 passes. The second CLI run proves the deletion was saved, not only removed from memory.

## 6. Optimistic versions in the domain

**Goal:** add a `version` to `Order`. It increments on every accepted mutation, is saved in the `O` record (this changes the snapshot format, so decide how version-less files load), and is required by the service's edit commands as an expected version. Two callers read version 1; the first edit succeeds, and the second gets a conflict and changes nothing. While you are in `Order`, inject a `java.time.Clock` in place of `LocalDateTime.now()` in `touch()` (`src/oms/domain/Order.java:122`) so timestamp assertions stop depending on the wall clock.
**Check:** case 6 passes. It only proves that a test named `stale version...` exists and the whole suite is green. In review, show that the test asserts the unchanged quantity, discount and `updatedAt` after the conflict.

## Stretch: the web-app steps (design deliverables, no runnable check here)

These need an HTTP server and a database, which this repo doesn't have. Write each deliverable down and compare it with [06](06-SOLUTIONS-AND-REVIEW.md).

- **HTTP contract.** A route table for customers, products, orders, line quantity (`PUT /orders/{id}/items/{productId}`), discounts and status commands, plus the status code for each refusal this CLI prints today. Done when every `IllegalArgumentException` message in `Order`/`OrderService` maps to exactly one code.
- **Transactional persistence.** DDL with foreign keys, `UNIQUE(order_id, product_id)` on items, and a decision for SKU uniqueness. Done when you can name the single transaction that saves an item change together with the order's version and `updated_at`, and loading still goes through one complete `Order.restore` call.
- **Inventory and outbox.** When stock is reserved, released and consumed, and how an outbox row commits with the transition. Done when a broker outage and a retry are each a written test case that cannot duplicate the business transition.
- **Ownership.** Every lookup in `OrderService` keyed by owner as well as ID. Done when you have a test plan for a cross-owner `GET`, a nested item route, and a status command.
