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
    [ConcurrencyCheck] public int Version { get; private set; }
    private readonly List<OrderItem> _items = new();
    public IReadOnlyList<OrderItem> Items => _items;          // the aggregate root owns its lines
    public decimal Subtotal => _items.Sum(i => i.LineTotal);
    public decimal Total => Math.Max(0, Subtotal - DiscountAmount);
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

The bug Astra fixed was an order restored from disk without its items and then re-saved, silently dropping lines. In EF Core the same bug is one missing `Include`:

```csharp
var order = await db.Orders.SingleAsync(o => o.Id == id);        // Items not loaded: the list is empty
order.ApplyDiscount(5m);
await db.SaveChangesAsync();                                      // owned collection: nothing deleted, but Subtotal was computed as 0
```

With an owned collection EF will not delete the rows, but every rule that reads `Items` (subtotal, "cannot submit an empty order") runs against an empty list. The fix is the same as in Java: load the aggregate whole (`db.Orders.Include(o => o.Items)`), or better, a repository method `LoadOrder(id)` that always includes, so no caller can forget.

## Refusals mapped to HTTP

| rule in `OrderService` | C# | status |
|---|---|---|
| discount larger than subtotal | `throw new DomainException(...)` from `ApplyDiscount` | 422 |
| quantity change on a non-DRAFT order | `if (Status != OrderStatus.Draft) throw ...` | 409 |
| terminal status transition (`PAID -> DRAFT`) | `OrderStatus.CanTransitionTo` table, refused | 409 |
| stale save | `DbUpdateConcurrencyException` from `[ConcurrencyCheck]` | 409 |
| unknown product or customer | `FirstOrDefaultAsync` returns null | 404 |
| body fails validation | `ValidationProblem` | 400 |

## Exercise

Write the test that loads an order without `Include`, applies a discount, saves, reloads with `Include`, and asserts the items are still there and the total is right; then break it by making `Items` a plain (non-owned) navigation with `DeleteOrphans` semantics and watch what changes.
