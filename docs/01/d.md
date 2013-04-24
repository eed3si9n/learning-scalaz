
### Show

LYAHFGG:

> Members of `Show` can be presented as strings.

Scalaz equivalent for the `Show` typeclass is `Show`:

```scala
scala> 3.show
res14: scalaz.Cord = 3

scala> 3.shows
res15: String = 3

scala> "hello".println
"hello"
```

`Cord` apparently is a purely functional data structure for potentially long Strings.
