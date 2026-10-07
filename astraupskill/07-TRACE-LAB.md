# Trace lab: discount restoration and state-aware item edits

Use a new directory for this exercise:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-orders
```

Create a customer with menu 1 and record its ID. Use a learning address such as `learner@example.test`; no email is sent. Create an active Notebook product with menu 3, SKU NOTE, and price 10.00. Record that product ID. Create an order through menu 5 using the customer ID, then record the order ID. These IDs connect the records; names are display values rather than reliable lookup keys.

Use menu 6 to add the product with quantity 2. Use menu 10 to apply a discount of 5.00. Menu 8 should show subtotal 20.00, discount 5.00, and total 15.00. Save with 12 and exit with 13. Restart using the same directory and inspect the order again. The discount must remain 5.00. Open `oms.snapshot.txt` and identify the O metadata row and I item row that were restored together.

Use menu 14 to replace quantity with 3. The price remains 10.00 and total becomes 25.00. Change the discount to 25.00, then try to reduce quantity to 2. The proposed subtotal would be 20.00, so the command must fail and preserve quantity 3, discount 25.00, and total 5.00. Reduce the discount explicitly before retrying the quantity edit. This sequence demonstrates that rejection protects the whole aggregate rather than just one field.

Move the order to SUBMITTED with menu 11, then to PAID. Try menu 14, menu 7 item removal, and menu 10 discount changes. Each content edit should be rejected. A PAID-to-SHIPPED transition is allowed, while SHIPPED-to-CANCELLED is not. The program does not charge a payment method or issue a refund; these are local status values only.

For a corruption trace, exit and copy the learning directory to a fresh destination. Append a line after the count footer in the copied snapshot, then compare its hash before and after startup:

```powershell
Copy-Item -LiteralPath .\data\trace-orders -Destination .\data\trace-orders-corrupt -Recurse
Add-Content -LiteralPath .\data\trace-orders-corrupt\oms.snapshot.txt -Value 'broken'
$beforeHash = (Get-FileHash -LiteralPath .\data\trace-orders-corrupt\oms.snapshot.txt).Hash
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -DataPath .\data\trace-orders-corrupt
$afterHash = (Get-FileHash -LiteralPath .\data\trace-orders-corrupt\oms.snapshot.txt).Hash
$beforeHash -eq $afterHash
```

Use an unused copy destination to avoid nested-directory surprises. Startup should stop before the normal menu and leave the corrupt copy unchanged. Keep the valid source directory as the recovery copy; do not force fallback by deleting an authoritative snapshot from real learning work.

Finish by reading the [regression tests](../test/oms/RegressionTests.java). Map each observation to a test and explain which checks use direct domain calls, service calls, or real files. Record why restoration preserves timestamps, why item reads cannot mutate authoritative totals, and why this single-threaded trace does not establish correctness for concurrent HTTP requests.

<details>
<summary>Answers: the expected figures, collected</summary>

- After quantity 2 at 10.00 and a 5.00 discount: subtotal 20.00, discount 5.00, total 15.00, and the same after restart.
- After menu 14 sets quantity 3: unit price stays 10.00, total 25.00.
- With discount 25.00, reducing quantity to 2 is refused (`Could not update quantity: Reduce the discount before reducing the subtotal.`). Quantity stays 3, discount 25.00, total 5.00.
- In PAID, menus 14, 7 and 10 each print `Orders in PAID status cannot be edited.`
- Corrupt copy: `Startup stopped: Failed to load order data: Invalid data record ...` and `$beforeHash -eq $afterHash` is `True`. Check `$LASTEXITCODE` too: it is `0`, because `App.main` returns normally after a failed load (`src/oms/App.java:51-54`).

</details>
