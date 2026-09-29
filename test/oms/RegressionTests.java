package oms;
import oms.domain.*;
import oms.service.*;
import oms.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
public class RegressionTests {
    static int passed,failed;
    interface Check { void run() throws Exception; }
    static void test(String name,Check check) { try { check.run();passed++;System.out.println("PASS "+name); } catch(Throwable error) { failed++;System.out.println("FAIL "+name+": "+error); } }
    static void equal(Object expected,Object actual) { if(!expected.equals(actual)) throw new AssertionError("Expected "+expected+"; got "+actual); }
    static void rejects(Class<? extends Throwable> type,Check check) throws Exception { try { check.run(); } catch(Throwable error) { if(type.isInstance(error)) return;throw error; } throw new AssertionError("Expected "+type.getSimpleName()); }
    static BigDecimal money(String value) { return new BigDecimal(value); }
    static LocalDateTime time() { return LocalDateTime.of(2026,1,2,3,4); }
    static Customer customer() { return new Customer("C","Learner","learner@example.test","",time()); }
    static Product product() { return new Product("P","Notebook","NOTE",money("10"),true); }
    static Order order() { var order=new Order("O","C",time());order.addOrMergeItem(new OrderItem("P","Notebook",2,money("10")));return order; }
    static OrderService service(Order order) { return new OrderService(List.of(order),new CustomerService(List.of(customer())),new ProductService(List.of(product()))); }
    interface WithDirectory { void run(Path directory) throws Exception; }
    static void withDirectory(WithDirectory check) throws Exception {
        Path directory=Files.createTempDirectory("j7-regression-").toAbsolutePath().normalize();
        Path root=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize();
        if(!directory.startsWith(root) || !directory.getFileName().toString().startsWith("j7-regression-")) throw new AssertionError("Unsafe temporary path");
        try {check.run(directory);} finally {try(var files=Files.walk(directory)){for(Path file:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(file);}}
    }
    public static void main(String[] args) throws Exception {
        test("saved discount survives full order reload",()->withDirectory(dir->{var order=order();order.setDiscountAmount(money("5"));var store=new FileDataStore(dir);store.save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order)));var loaded=store.load().getOrders().get(0);equal(money("5.00"),loaded.getDiscountAmount());equal(money("15.00"),loaded.getTotal());}));
        test("read order cannot mutate service state",()->{var s=service(order());s.findById("O").orElseThrow().setDiscountAmount(money("5"));equal(money("20.00"),s.findById("O").orElseThrow().getTotal());});
        test("item getter cannot invalidate aggregate totals",()->{var order=order();order.getItems().get(0).setQuantity(9);equal(2,order.getItems().get(0).getQuantity());equal(money("20.00"),order.getSubtotal());});
        test("paid order cannot be edited",()->{var s=service(order());s.updateStatus("O",OrderStatus.SUBMITTED);s.updateStatus("O",OrderStatus.PAID);rejects(IllegalArgumentException.class,()->s.addProductToOrder("O","P",1));equal(money("20.00"),s.findById("O").orElseThrow().getTotal());});
        test("sub-cent product price is rejected",()->rejects(IllegalArgumentException.class,()->new Product("P","x","x",money("0.001"),true)));
        test("clearing service all collection cannot delete records",()->{var s=new ProductService(List.of(product()));try{s.all().clear();}catch(UnsupportedOperationException expected){}equal(1,s.listProducts().size());});
        test("carriage returns round trip through field codec",()->equal("a\rb",DelimitedFileUtil.split(DelimitedFileUtil.join("a\rb")).get(0)));
        test("service constructor copies input order",()->{var original=order();var s=service(original);original.setDiscountAmount(money("5"));equal(money("20.00"),s.findById("O").orElseThrow().getTotal());});
        test("created order is detached from service",()->{var s=service(order());var created=s.createOrder("C");created.addOrMergeItem(new OrderItem("P","Notebook",1,money("10")));equal(false,s.findById(created.getId()).orElseThrow().hasItems());});
        test("snapshot owns copies on input and output",()->{var original=order();var snapshot=new StoreSnapshot(List.of(customer()),List.of(product()),List.of(original));original.setDiscountAmount(money("3"));snapshot.getOrders().get(0).setDiscountAmount(money("4"));equal(money("20.00"),snapshot.getOrders().get(0).getTotal());});
        test("invalid discount leaves amount and timestamp unchanged",()->{var order=order();var before=order.getUpdatedAt();rejects(IllegalArgumentException.class,()->order.setDiscountAmount(money("0.001")));rejects(IllegalArgumentException.class,()->order.setDiscountAmount(money("21")));equal(money("0.00"),order.getDiscountAmount());equal(before,order.getUpdatedAt());});
        test("quantity update keeps item price snapshot and derives totals",()->{var s=service(order());s.updateItemQuantity("O","P",3);var found=s.findById("O").orElseThrow();equal(3,found.getItems().get(0).getQuantity());equal(money("10.00"),found.getItems().get(0).getUnitPrice());equal(money("30.00"),found.getTotal());});
        test("quantity reduction below discount rejects without mutation",()->{var order=order();order.setDiscountAmount(money("15"));var s=service(order);var before=s.findById("O").orElseThrow().getUpdatedAt();rejects(IllegalArgumentException.class,()->s.updateItemQuantity("O","P",1));var found=s.findById("O").orElseThrow();equal(2,found.getItems().get(0).getQuantity());equal(money("15.00"),found.getDiscountAmount());equal(before,found.getUpdatedAt());});
        test("item removal requires discount adjustment first",()->{var order=order();order.setDiscountAmount(money("5"));rejects(IllegalArgumentException.class,()->order.removeItemByProductId("P"));equal(1,order.getItems().size());order.setDiscountAmount(money("0"));equal(true,order.removeItemByProductId("P"));equal(money("0.00"),order.getTotal());});
        test("empty order cannot be submitted",()->{var order=new Order("O","C",time());rejects(IllegalArgumentException.class,()->order.setStatus(OrderStatus.SUBMITTED));equal(OrderStatus.DRAFT,order.getStatus());});
        test("domain guards status transitions and terminal edits",()->{var order=order();rejects(IllegalArgumentException.class,()->order.setStatus(OrderStatus.SHIPPED));order.setStatus(OrderStatus.SUBMITTED);order.setStatus(OrderStatus.PAID);rejects(IllegalArgumentException.class,()->order.setDiscountAmount(money("1")));rejects(IllegalArgumentException.class,()->order.removeItemByProductId("P"));order.setStatus(OrderStatus.SHIPPED);rejects(IllegalArgumentException.class,()->order.setStatus(OrderStatus.CANCELLED));});
        test("inactive product cannot be added",()->{var s=new OrderService(List.of(new Order("O","C",time())),new CustomerService(List.of(customer())),new ProductService(List.of(new Product("P","x","X",money("10"),false))));rejects(IllegalArgumentException.class,()->s.addProductToOrder("O","P",1));equal(false,s.findById("O").orElseThrow().hasItems());});
        test("quantity overflow and negative increment preserve original",()->{var item=new OrderItem("P","Notebook",Integer.MAX_VALUE,money("10"));rejects(IllegalArgumentException.class,()->item.increaseQuantity(1));rejects(IllegalArgumentException.class,()->item.increaseQuantity(-1));equal(Integer.MAX_VALUE,item.getQuantity());});
        test("complete restoration preserves paid discount and timestamps",()->{var restored=Order.restore("O","C",OrderStatus.PAID,money("5"),time(),time(),List.of(new OrderItem("P","Notebook",2,money("10"))));equal(money("15.00"),restored.getTotal());equal(time(),restored.getUpdatedAt());equal(OrderStatus.PAID,restored.getStatus());});
        test("restoration rejects impossible discount",()->rejects(IllegalArgumentException.class,()->Order.restore("O","C",OrderStatus.DRAFT,money("21"),time(),time(),List.of(new OrderItem("P","Notebook",2,money("10"))))));
        test("duplicate customer product and order ids rejected",()->{rejects(IllegalArgumentException.class,()->new CustomerService(List.of(customer(),customer())));rejects(IllegalArgumentException.class,()->new ProductService(List.of(product(),product())));rejects(IllegalArgumentException.class,()->new OrderService(List.of(order(),order()),new CustomerService(List.of(customer())),new ProductService(List.of(product()))));});
        test("snapshot rejects missing customer and product references",()->{rejects(IllegalArgumentException.class,()->new StoreSnapshot(List.of(),List.of(product()),List.of(order())));rejects(IllegalArgumentException.class,()->new StoreSnapshot(List.of(customer()),List.of(),List.of(order())));});
        test("restoration rejects duplicate product lines",()->rejects(IllegalArgumentException.class,()->Order.restore("O","C",OrderStatus.DRAFT,money("0"),time(),time(),List.of(new OrderItem("P","Notebook",1,money("10")),new OrderItem("P","Notebook",1,money("10"))))));
        test("invalid saved boolean rejects load",()->withDirectory(dir->{var store=new FileDataStore(dir);store.save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order())));Path file=dir.resolve("oms.snapshot.txt");Files.writeString(file,Files.readString(file).replace("|true","|maybe"));rejects(IllegalStateException.class,store::load);}));
        test("legacy four-file migration preserves discount and original files",()->withDirectory(dir->{
            Files.writeString(dir.resolve("customers.txt"),"C|Learner|learner@example.test||2026-01-02T03:04\n");
            Files.writeString(dir.resolve("products.txt"),"P|Notebook|NOTE|10.00|true\n");
            Files.writeString(dir.resolve("orders.txt"),"O|C|PAID|5.00|2026-01-02T03:04|2026-01-02T03:04\n");
            Files.writeString(dir.resolve("order_items.txt"),"O|P|Notebook|2|10.00\n");
            String before=Files.readString(dir.resolve("orders.txt"));var store=new FileDataStore(dir);var loaded=store.load();equal(money("5.00"),loaded.getOrders().get(0).getDiscountAmount());store.save(loaded);equal(before,Files.readString(dir.resolve("orders.txt")));Files.writeString(dir.resolve("orders.txt"),"broken");equal(money("15.00"),store.load().getOrders().get(0).getTotal());
        }));
        test("incomplete legacy data is rejected",()->withDirectory(dir->{Files.writeString(dir.resolve("customers.txt"),"");rejects(IllegalStateException.class,new FileDataStore(dir)::load);}));
        test("corrupt canonical snapshot never falls back to legacy",()->withDirectory(dir->{for(String file:List.of("customers.txt","products.txt","orders.txt","order_items.txt"))Files.writeString(dir.resolve(file),"");Files.writeString(dir.resolve("oms.snapshot.txt"),"broken\n");rejects(IllegalStateException.class,new FileDataStore(dir)::load);}));
        test("truncation and bad counts reject snapshot",()->withDirectory(dir->{var store=new FileDataStore(dir);store.save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order())));Path file=dir.resolve("oms.snapshot.txt");var lines=Files.readAllLines(file);Files.write(file,lines.subList(0,lines.size()-1));rejects(IllegalStateException.class,store::load);lines.set(lines.size()-1,"END|1|1|1|0");Files.write(file,lines);rejects(IllegalStateException.class,store::load);}));
        test("orphan item is not silently discarded",()->withDirectory(dir->{Files.writeString(dir.resolve("oms.snapshot.txt"),"# oms-snapshot-v1\nI|missing|P|Notebook|1|10.00\nEND|0|0|0|1\n");rejects(IllegalStateException.class,new FileDataStore(dir)::load);}));
        test("codec rejects unknown and incomplete escape",()->{rejects(IllegalArgumentException.class,()->DelimitedFileUtil.split("bad\\q"));rejects(IllegalArgumentException.class,()->DelimitedFileUtil.split("bad\\"));equal(List.of("a|b","path\\x","line\nnext",""),DelimitedFileUtil.split(DelimitedFileUtil.join("a|b","path\\x","line\nnext","")));});
        test("failed replacement keeps existing destination and removes temporary file",()->withDirectory(dir->{Path target=dir.resolve("oms.snapshot.txt");Files.createDirectory(target);Files.writeString(target.resolve("keep.txt"),"keep");rejects(IllegalStateException.class,()->new FileDataStore(dir).save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order()))));equal("keep",Files.readString(target.resolve("keep.txt")));try(var files=Files.list(dir)){equal(1L,files.count());}}));
        test("repeated save persists changed quantity and discount together",()->withDirectory(dir->{var order=order();var store=new FileDataStore(dir);store.save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order)));order.setDiscountAmount(money("5"));order.setItemQuantity("P",3);store.save(new StoreSnapshot(List.of(customer()),List.of(product()),List.of(order)));var loaded=store.load().getOrders().get(0);equal(3,loaded.getItems().get(0).getQuantity());equal(money("25.00"),loaded.getTotal());try(var files=Files.list(dir)){equal(1L,files.count());}}));
        test("empty snapshot file is corruption",()->withDirectory(dir->{Files.writeString(dir.resolve("oms.snapshot.txt"),"");rejects(IllegalStateException.class,new FileDataStore(dir)::load);}));
        test("loading new dataset does not create directory",()->withDirectory(dir->{Path missing=dir.resolve("new");equal(0,new FileDataStore(missing).load().getOrders().size());equal(false,Files.exists(missing));}));
        test("submitted order cannot lose its last item",()->{var order=order();order.setStatus(OrderStatus.SUBMITTED);rejects(IllegalArgumentException.class,()->order.removeItemByProductId("P"));equal(1,order.getItems().size());});
        System.out.println("RESULT passed="+passed+" failed="+failed);
        if(failed!=0) System.exit(1);
    }
}
