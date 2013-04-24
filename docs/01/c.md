
### Order

LYAHFGG:

> `Ord` is for types that have an ordering. `Ord` covers all the standard comparing functions such as `>`, `<`, `>=` and `<=`.

Scalaz equivalent for the `Ord` typeclass is `Order`:

```scala
scala> 1 > 2.0
res8: Boolean = false

scala> 1 gt 2.0
<console>:14: error: could not find implicit value for parameter F0: scalaz.Order[Any]
              1 gt 2.0
              ^

scala> 1.0 ?|? 2.0
res10: scalaz.Ordering = LT

scala> 1.0 max 2.0
res11: Double = 2.0
```

`Order` enables `?|?` syntax which returns `Ordering`: `LT`, `GT`, and `EQ`. It also enables `lt`, `gt`, `lte`, `gte`, `min`, and `max` operators by declaring `order` method. Similar to `Equal`, comparing `Int` and `Doubl` fails compilation.
