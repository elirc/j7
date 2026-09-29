# Sample data

`data/sample/` loads a snapshot with two customers, three products, one DRAFT order with a 5.00 discount and one PAID order. `data/sample-corrupt/` is the same file with a wrong item count in the footer, so the loader refuses it ("Missing footer or incorrect record counts"). Run `./run.ps1 -DataPath data/sample` (copy the directory first if you intend to save).
