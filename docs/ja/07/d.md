
### Validation

Scalaz のデータ構造で `Either` と比較されるものにもう1つ [`Validation`]($scalazBaseUrl$/core/src/main/scala/scalaz/Validation.scala) がある:

```scala
sealed trait Validation[+E, +A] {
  /** Return `true` if this validation is success. */
  def isSuccess: Boolean = this match {
    case Success(_) => true
    case Failure(_) => false
  }
  /** Return `true` if this validation is failure. */
  def isFailure: Boolean = !isSuccess

  ...
}

final case class Success[E, A](a: A) extends Validation[E, A]
final case class Failure[E, A](e: E) extends Validation[E, A]
```

一見すると `Validation` は `\/` に似ている。お互い `validation` メソッドと `disjunction` メソッドを使って変換することまでできる。

[`ValidationOps`]($scalazBaseUrl$/core/src/main/scala/scalaz/syntax/ValidationOps.scala) によって全てのデータ型に `success[X]`、 `successNel[X]`、`failure[X]`、`failureNel[X]` メソッドが導入されている (今のところ `Nel` に関しては心配しなくていい):

```scala
scala> "event 1 ok".success[String]
res36: scalaz.Validation[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failure[String]
res38: scalaz.Validation[String,String] = Failure(event 1 failed!)
```

`Validation` の違いはこれがモナドではなく、Applicative functor であることだ。最初のイベントの結果を次へと連鎖するのでは無く、`Validation` は全イベントを検証する:

```scala
scala> ("event 1 ok".success[String] |@| "event 2 failed!".failure[String] |@| "event 3 failed!".failure[String]) {_ + _ + _}
res44: scalaz.Unapply[scalaz.Apply,scalaz.Validation[String,String]]{type M[X] = scalaz.Validation[String,X]; type A = String}#M[String] = Failure(event 2 failed!event 3 failed!)
```

ちょっと読みづらいけど、最終結果は `Failure(event 2 failed!event 3 failed!)` だ。計算途中でショートさせた `\/` と違って、`Validation` は計算を続行して全ての失敗を報告する。これはおそらくオンラインのベーコンショップでユーザのインプットを検証するのに役立つと思う。

だけど、問題はエラーメッセージが 1つの文字列にゴチャっと一塊になってしまっていることだ。リストでも使うべきじゃないか?

### NonEmptyList

ここで [`NonEmptyList`]($scalazBaseUrl$/core/src/main/scala/scalaz/NonEmptyList.scala) (略して `Nel`) が登場する:

```scala
/** A singly-linked list that is guaranteed to be non-empty. */
sealed trait NonEmptyList[+A] {
  val head: A
  val tail: List[A]
  def <::[AA >: A](b: AA): NonEmptyList[AA] = nel(b, head :: tail)
  ...
}
```

これは素の `List` のラッパーで、空じゃないことを保証する。必ず 1つのアイテムがあることで `head` は常に成功する。`IdOps` は `Nel` を作るために `wrapNel` を全てのデータ型に導入する。

```scala
scala> 1.wrapNel
res47: scalaz.NonEmptyList[Int] = NonEmptyList(1)
```

これで `successNel[X]` と `failureNel[X]` が分かったかな?

```scala
scala> "event 1 ok".successNel[String]
res48: scalaz.ValidationNEL[String,String] = Success(event 1 ok)

scala> "event 1 failed!".failureNel[String]
res49: scalaz.ValidationNEL[String,String] = Failure(NonEmptyList(event 1 failed!))

scala> ("event 1 ok".successNel[String] |@| "event 2 failed!".failureNel[String] |@| "event 3 failed!".failureNel[String]) {_ + _ + _}
res50: scalaz.Unapply[scalaz.Apply,scalaz.ValidationNEL[String,String]]{type M[X] = scalaz.ValidationNEL[String,X]; type A = String}#M[String] = Failure(NonEmptyList(event 2 failed!, event 3 failed!))
```

`Failure` の中に全ての失敗メッセージを集約することができた。

続きはまたあとで。
