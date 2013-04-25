
### Length

There's a typeclass that expresses length. Here's [the typeclass contract of `Length`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala):

```scala
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
```

This introduces `length` method. In Scala standard library it's introduced by `SeqLike`, so it could become useful if there were data structure that does not extend `SeqLike` that has length.
