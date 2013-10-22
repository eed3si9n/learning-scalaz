---
out: sum-function.html
---

### sum 関数

アドホック多相の具体例として、`Int` のリストを合計する簡単な関数 `sum` を徐々に一般化していく。

```scala
scala> def sum(xs: List[Int]): Int = xs.foldLeft(0) { _ + _ }
sum: (xs: List[Int])Int

scala> sum(List(1, 2, 3, 4))
res3: Int = 10
```

### Monoid

> これを少し一般化してみましょう。`Monoid` というものを取り出します。... これは、同じ型の値を生成する `mappend` という関数と「ゼロ」を生成する関数を含む型です。

```scala
scala> object IntMonoid {
         def mappend(a: Int, b: Int): Int = a + b
         def mzero: Int = 0
       }
defined module IntMonoid
```

> これを代入することで、少し一般化されました。

```scala
scala> def sum(xs: List[Int]): Int = xs.foldLeft(IntMonoid.mzero)(IntMonoid.mappend)
sum: (xs: List[Int])Int

scala> sum(List(1, 2, 3, 4))
res5: Int = 10
```

> 次に、全ての型 `A` について `Monoid` が定義できるように、`Monoid` を抽象化します。これで `IntMonoid` が `Int` のモノイドになりました。

```scala
scala> trait Monoid[A] {
         def mappend(a1: A, a2: A): A
         def mzero: A
       }
defined trait Monoid

scala> object IntMonoid extends Monoid[Int] {
         def mappend(a: Int, b: Int): Int = a + b
         def mzero: Int = 0
       }
defined module IntMonoid
```

これで `sum` が `Int` のリストと `Int` のモノイドを受け取って合計を計算できるようになった:

```scala
scala> def sum(xs: List[Int], m: Monoid[Int]): Int = xs.foldLeft(m.mzero)(m.mappend)
sum: (xs: List[Int], m: Monoid[Int])Int

scala> sum(List(1, 2, 3, 4), IntMonoid)
res7: Int = 10
```

> これで `Int` を使わなくなったので、全ての `Int` を一般型に置き換えることができます。

```scala
scala> def sum[A](xs: List[A], m: Monoid[A]): A = xs.foldLeft(m.mzero)(m.mappend)
sum: [A](xs: List[A], m: Monoid[A])A

scala> sum(List(1, 2, 3, 4), IntMonoid)
res8: Int = 10
```

> 最後の変更点は `Monoid` を implicit にすることで毎回渡さなくてもいいようにすることです。

```scala
scala> def sum[A](xs: List[A])(implicit m: Monoid[A]): A = xs.foldLeft(m.mzero)(m.mappend)
sum: [A](xs: List[A])(implicit m: Monoid[A])A

scala> implicit val intMonoid = IntMonoid
intMonoid: IntMonoid.type = IntMonoid\$@3387dfac

scala> sum(List(1, 2, 3, 4))
res9: Int = 10
```

Nick さんはやらなかったけど、この形の暗黙のパラメータは context bound で書かれることが多い:

```scala
scala> def sum[A: Monoid](xs: List[A]): A = {
         val m = implicitly[Monoid[A]]
         xs.foldLeft(m.mzero)(m.mappend)
       }
sum: [A](xs: List[A])(implicit evidence\$1: Monoid[A])A

scala> sum(List(1, 2, 3, 4))
res10: Int = 10
```

> これでどのモノイドのリストでも合計できるようになり、 `sum` 関数はかなり一般化されました。`String` の `Monoid` を書くことでこれをテストすることができます。また、これらは `Monoid` という名前のオブジェクトに包むことにします。その理由は Scala の implicit 解決ルールです。ある型の暗黙のパラメータを探すとき、Scala はスコープ内を探しますが、それには探している型のコンパニオンオブジェクトも含まれるのです。

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

trait Monoid[A] {
  def mappend(a1: A, a2: A): A
  def mzero: A
}
object Monoid {
  implicit val IntMonoid: Monoid[Int] = new Monoid[Int] {
    def mappend(a: Int, b: Int): Int = a + b
    def mzero: Int = 0
  }
  implicit val StringMonoid: Monoid[String] = new Monoid[String] {
    def mappend(a: String, b: String): String = a + b
    def mzero: String = ""
  }
}
def sum[A: Monoid](xs: List[A]): A = {
  val m = implicitly[Monoid[A]]
  xs.foldLeft(m.mzero)(m.mappend)
}

// Exiting paste mode, now interpreting.

defined trait Monoid
defined module Monoid
sum: [A](xs: List[A])(implicit evidence\$1: Monoid[A])A

scala> sum(List("a", "b", "c"))
res12: String = abc
```

> この関数に直接異なるモノイドを渡すこともできます。例えば、`Int` の積算のモノイドのインスタンスを提供してみましょう。

```scala
scala> val multiMonoid: Monoid[Int] = new Monoid[Int] {
         def mappend(a: Int, b: Int): Int = a * b
         def mzero: Int = 1
       }
multiMonoid: Monoid[Int] = \$anon\$1@48655fb6

scala> sum(List(1, 2, 3, 4))(multiMonoid)
res14: Int = 24
```
