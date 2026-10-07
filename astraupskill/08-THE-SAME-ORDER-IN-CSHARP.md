# 08. The same order in C# and EF Core

[Course index](README.md)

## Entities

```csharp
public enum OrderStatus { Draft, Submitted, Paid, Shipped, Cancelled }

public sealed class Order
{
    public string Id { get; private set; } = default!;
    public string CustomerId { get; private set; } = default!;
    public OrderStatus Status { get; private set; }
    public decimal DiscountAmount { get; private set; }
    public DateTime CreatedAt { get; private set; }
    public DateTime UpdatedAt { get; private set; }
    [ConcurrencyCheck] public int Version { get; private set; }   // new: the Java order has no version yet (05, exercise 6)
    private readonly List<OrderItem> _items = new();
    public IReadOnlyList<OrderItem> Items => _items;          // the aggregate root owns its lines
    public decimal Subtotal => _items.Sum(i => i.LineTotal);
    public decimal Total => Subtotal - DiscountAmount;          // no clamp: ApplyDiscount refuses discount > Subtotal, as Order.java:101 does
}

[Owned] public sealed class OrderItem                            // owned: no identity outside its order
{
    public string ProductId { get; private set; } = default!;
    public string ProductName { get; private set; } = default!;   // price snapshot: copied at add time
    public int Quantity { get; private set; }
    public decimal UnitPrice { get; private set; }               // never re-read from Product
    public decimal LineTotal => UnitPrice * Quantity;
}
```

`modelBuilder.Entity<Order>().OwnsMany(o => o.Items, b => { b.ToTable("OrderItems"); b.WithOwner().HasForeignKey("OrderId"); b.Property(i => i.UnitPrice).HasPrecision(18, 2); });` and `Property(o => o.DiscountAmount).HasPrecision(18, 2)`.

## The restoration bug, reproduced

The Java bug was an ordering problem. The old constructor validated the saved discount while the item list was still empty, clamped it to the zero subtotal, and added the items afterwards ([03](03-WORKED-CHANGE.md)). EF Core gives you the same trap, but only when the lines are a *regular* navigation:

```csharp
// modelBuilder.Entity<Order>().HasMany(o => o.Items).WithOne();   // regular navigation, not owned
var order = await db.Orders.SingleAsync(o => o.Id == id);        // no Include: Items is empty
order.ApplyDiscount(5m);                                          // Subtotal is 0, so a rejecting domain throws
```

`ApplyDiscount` runs against an empty list, so it throws (or, with an old-style clamp, silently stores 0). "Cannot submit an empty order" fires on an order that really has lines. With the `OwnsMany` mapping above this can't happen: EF Core loads owned types together with their owner on every query, without `Include`. That makes the owned mapping the C# equivalent of `Order.restore` (`src/oms/domain/Order.java:42-46`), a single path that always yields the whole aggregate. If you model lines as their own entities, put the `Include` inside one repository method such as `LoadOrder(id)` so no caller can forget it.

## Refusals mapped to HTTP

| rule (Java location) | C# | status |
|---|---|---|
| discount larger than subtotal (`Order.setDiscountAmount`) | `throw new DomainException(...)` from `ApplyDiscount` | 422 |
| content edit on a PAID/SHIPPED/CANCELLED order (`Order.ensureEditable`; DRAFT and SUBMITTED are editable) | `if (Status is not (OrderStatus.Draft or OrderStatus.Submitted)) throw ...` | 409 |
| transition outside the graph, e.g. `PAID -> DRAFT` or anything out of SHIPPED/CANCELLED (`OrderStatus.canTransitionTo`) | a `CanTransitionTo` table, refused | 409 |
| stale save (no Java equivalent yet) | `DbUpdateConcurrencyException` from `[ConcurrencyCheck]` | 409 |
| unknown product or customer | `FirstOrDefaultAsync` returns null | 404 |
| body fails validation | `ValidationProblem` | 400 |

## Exercise

With EF Core's SQLite provider, write two tests. (1) Map `Items` as a regular `HasMany` navigation, load an order that has two saved lines without `Include`, and assert that `ApplyDiscount(5m)` throws even though the saved subtotal is 20.00. (2) Switch to the `OwnsMany` mapping above, run the same query without `Include`, and assert two lines, discount 5.00 and total 15.00 after a save and a fresh-context reload. Together the two tests show the bug and show the mapping that removes it.
