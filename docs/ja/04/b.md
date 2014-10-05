---
out: Option-as-Monoid.html
---

### Monoid としての Option

LYAHFGG:

> `Maybe a` をモノイドにする1つ目の方法は、型引数 `a` がモノイドであるときに限り `Maybe a` もモノイドであるとし、`Maybe a` の `mappend` を、`Just` の中身の `mappend` を使って定義することです。

Scalaz がこうなっているか確認しよう。[`std/Option.scala`]($scalazBaseUrl$/core/src/main/scala/scalaz/std/Option.scala#L54-63) 参照:

```scala
  implicit def optionMonoid[A: Semigroup]: Monoid[Option[A]] = new Monoid[Option[A]] {
    def append(f1: Option[A], f2: => Option[A]) = (f1, f2) match {
      case (Some(a1), Some(a2)) => Some(Semigroup[A].append(a1, a2))
      case (Some(a1), None)     => f1
      case (None, Some(a2))     => f2
      case (None, None)         => None
    }

    def zero: Option[A] = None
  }
```

実装はシンプルで良い感じだ。Context bound の `A: Semigroup` は `A` が `|+|` をサポートしなければいけないと言っている。残りはパターンマッチングだ。本の言うとおりの振る舞いだ。

```scala
scala> (none: Option[String]) |+| "andy".some
res23: Option[String] = Some(andy)

scala> (Ordering.LT: Ordering).some |+| none
res25: Option[scalaz.Ordering] = Some(LT)
```

ちゃんと動く。

LYAHFGG:

> 中身がモノイドがどうか分からない状態では、`mappend` は使えません。どうすればいいでしょう？ 1つの選択は、第一引数を返して第二引数は捨てる、と決めておくことです。この用途のために `First a` というものが存在します。

Haskell は `newtype` を使って `First` 型コンストラクタを実装している。Scalaz 7 は強力な Tagged type を使っている:

```scala
scala> Tags.First('a'.some) |+| Tags.First('b'.some)
res26: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(a)

scala> Tags.First(none: Option[Char]) |+| Tags.First('b'.some)
res27: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(b)

scala> Tags.First('a'.some) |+| Tags.First(none: Option[Char])
res28: scalaz.@@[Option[Char],scalaz.Tags.First] = Some(a)
```

LYAHFGG:

> 逆に、2つの `Just` を `mappend` したときに後のほうの引数を優先するような `Maybe a` が欲しい、という人のために、`Data.Monoid` には `Last a` 型も用意されています。

これは `Tags.Last` だ:

```scala
scala> Tags.Last('a'.some) |+| Tags.Last('b'.some)
res29: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(b)

scala> Tags.Last(none: Option[Char]) |+| Tags.Last('b'.some)
res30: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(b)

scala> Tags.Last('a'.some) |+| Tags.Last(none: Option[Char])
res31: scalaz.@@[Option[Char],scalaz.Tags.Last] = Some(a)
```
