---
out: IndexOps.html
---

### Index

コンテナへのランダムアクセスを表すのが [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala) だ:

```scala
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
```

これは `index` と `indexOr` メソッドを導入する:

```scala
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
```

これは `List(n)` に似ているけど、範囲外の添字で呼び出すと `None` が返る:

```scala
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
```

続きはまた後で。
