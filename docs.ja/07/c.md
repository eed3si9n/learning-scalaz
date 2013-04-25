---
out: Either.html
---

### \/

LYAHFGG:

> `Either e a` 型も失敗の文脈を与えるモナドです。しかも、失敗に値を付加できるので、何が失敗したかを説明したり、そのほか失敗にまつわる有用な情報を提供できます。

標準ライブラリの `Either[A, B]` は知ってるけど、Scalaz 7 は `Either` に対応する独自のデータ構造 [`\/`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Either.scala) を提供する:

```scala
sealed trait \/[+A, +B] {
  ...
  /** Return `true` if this disjunction is left. */
  def isLeft: Boolean =
    this match {
      case -\/(_) => true
      case \/-(_) => false
    }

  /** Return `true` if this disjunction is right. */
  def isRight: Boolean =
    this match {
      case -\/(_) => false
      case \/-(_) => true
    }
  ...
  /** Flip the left/right values in this disjunction. Alias for `unary_~` */
  def swap: (B \/ A) =
    this match {
      case -\/(a) => \/-(a)
      case \/-(b) => -\/(b)
    }
  /** Flip the left/right values in this disjunction. Alias for `swap` */
  def unary_~ : (B \/ A) = swap
  ...
  /** Return the right value of this disjunction or the given default if left. Alias for `|` */
  def getOrElse[BB >: B](x: => BB): BB =
    toOption getOrElse x
  /** Return the right value of this disjunction or the given default if left. Alias for `getOrElse` */
  def |[BB >: B](x: => BB): BB = getOrElse(x)
  
  /** Return this if it is a right, otherwise, return the given value. Alias for `|||` */
  def orElse[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB =
    this match {
      case -\/(_) => x
      case \/-(_) => this
    }
  /** Return this if it is a right, otherwise, return the given value. Alias for `orElse` */
  def |||[AA >: A, BB >: B](x: => AA \/ BB): AA \/ BB = orElse(x)
  ...
}

private case class -\/[+A](a: A) extends (A \/ Nothing)
private case class \/-[+B](b: B) extends (Nothing \/ B)
```

これらの値は `IdOps` 経由で全てのデータ型に注入された `right` メソッドと `left` メソッドによって作られる:

```scala
scala> 1.right[String]
res12: scalaz.\/[String,Int] = \/-(1)

scala> "error".left[Int]
res13: scalaz.\/[String,Int] = -\/(error)
```

Scala 標準ライブラリの `Either` 型はそれ単体ではモナドではないため、Scalaz を使っても使わなくても `flatMap` メソッドを実装しない:

```scala
scala> Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
<console>:8: error: value flatMap is not a member of scala.util.Left[String,Int]
              Left[String, Int]("boom") flatMap { x => Right[String, Int](x + 1) }
                                        ^
```

`right` メソッドを呼んで `RightProjection` に変える必要がある:

```scala
scala> Left[String, Int]("boom").right flatMap { x => Right[String, Int](x + 1)}
res15: scala.util.Either[String,Int] = Left(boom)
```

`Either` がそもそも存在する理由は左のエラーを報告するためにあるのだから、いちいち `right` を呼ぶのは手間だ。Scalaz の `\/` はだいたいにおいて右投射が欲しいだろうと決めてかかってくれる:

```scala
scala> "boom".left[Int] >>= { x => (x + 1).right }
res18: scalaz.Unapply[scalaz.Bind,scalaz.\/[String,Int]]{type M[X] = scalaz.\/[String,X]; type A = Int}#M[Int] = -\/(boom)
```

これは便利だ。`for` 構文から使ってみよう:

```scala
scala> for {
         e1 <- "event 1 ok".right
         e2 <- "event 2 failed!".left[String]
         e3 <- "event 3 failed!".left[String]
       } yield (e1 |+| e2 |+| e3)
res24: scalaz.\/[String,String] = -\/(event 2 failed!)
```

見ての通り、最初の失敗が最終結果に繰り上がった。`\/` からどうやって値を取り出せばいい? まず、`isRight` と `isLeft` でどっち側にいるか確かめる:

```scala
scala> "event 1 ok".right.isRight
res25: Boolean = true

scala> "event 1 ok".right.isLeft
res26: Boolean = false
```

右値なら `getOrElse` もしくはそのシンボルを使ったエイリアス `|` を使う:

```scala
scala> "event 1 ok".right | "something bad"
res27: String = event 1 ok
```

左値なら `swap` メソッドもしくはそのシンボルを使ったエイリアス `unary_~` を使う:

```scala
scala> ~"event 2 failed!".left[String] | "something good"
res28: String = event 2 failed!
```

`map` を使って右の値を変更できる:

```scala
scala> "event 1 ok".right map {_ + "!"}
res31: scalaz.\/[Nothing,String] = \/-(event 1 ok!)
```

左側で連鎖させるには、`=> AA \/ BB` (ただし `[AA >: A, BB >: B]`) を受け取る `orElse` がある。`orElse` のシンボルを使ったエイリアスは `|||` だ:

```scala
scala> "event 1 failed!".left ||| "retry event 1 ok".right 
res32: scalaz.\/[String,String] = \/-(retry event 1 ok)
```
