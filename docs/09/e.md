
### Index

For random access into a container, there's [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala):

```scala
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
```

This introduces `index` and `indexOr` methods:

```scala
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
```

This is similar to `List(n)` except it returns `None` for an out-of-range index:

```scala
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
```

We'll pick it up from here later.
