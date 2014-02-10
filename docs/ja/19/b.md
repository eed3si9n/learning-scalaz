---
out: Point.html
---

### 点

CM:

> **単集合** (singleton) という非常に便利な集合あって、これは唯一の要素 (element; 元とも) のみを持つ。これを例えば `{me}` という風に固定して、この集合を *1* と呼ぶ。

> **定義**: ある集合の**点** (point) は、*1 => X* という射だ。
>
> (もし *A* が既に親しみのある集合なら、*A* から *X* への射を *X* の 「*A*-要素」という。そのため、「*1*-要素」は点となる。) 点は射であるため、他の射と合成して再び点を得ることができる。

誤解していることを恐れずに言えば、CM は要素という概念を射の特殊なケースとして再定義しているように思える。単集合 (singleton) の別名に unit set というものがあって、Scala では `(): Unit` となる。つまり、値は `Unit => X` の糖衣構文だと言っているのに類似している。

```scala
scala> val johnPoint: Unit => Person = { case () => John } 
johnPoint: Unit => Person = <function1>

scala> favoriteBreakfast compose johnPoint
res1: Unit => Breakfast = <function1>

scala> res1(())
res2: Breakfast = Eggs
```

関数型プログラミングをサポートする言語における第一級関数は、関数を値として扱うことで高階関数を可能とする。圏論は逆方向に統一して値を関数として扱っている。

Session 2 と 3 は Article I の復習を含むため、本を持っている人は是非読んでほしい。

### 集合の射の等価性

Session で面白いなと思った所があって、それは射の等価性に関しての話だ。圏論での問題の多くが射の等価性に関連するけども、*f* と *g* が等価であるかをどうやってテストしたらいいだろうか?

2つの射は 3つの材料が同一である場合に等価であると言える。

- ドメイン *A*
- コドメイン *B*
- *f ∘ a* を割り当てるルール

*1* のために、集合の射 *f: A => B* と *g: A => B* の等価性に関しては以下のように検証できる:

> もしも各点 *a: 1 => A* に対して *f ∘ a = g ∘ a* である場合、*f = g* となる。

これで scalacheck を思い出したので、`f: Person => Breakfast` のチェックを実装してみる。


```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Person {}
case object John extends Person {}
case object Mary extends Person {}
case object Sam extends Person {}

sealed trait Breakfast {}
case object Eggs extends Breakfast {}
case object Oatmeal extends Breakfast {}
case object Toast extends Breakfast {}
case object Coffee extends Breakfast {}

val favoriteBreakfast: Person => Breakfast = {
  case John => Eggs
  case Mary => Coffee
  case Sam  => Coffee
}

val favoritePerson: Person => Person = {
  case John => Mary
  case Mary => John
  case Sam  => Mary
}

val favoritePersonsBreakfast = favoriteBreakfast compose favoritePerson

// Exiting paste mode, now interpreting.

scala> import org.scalacheck.{Prop, Arbitrary, Gen}
import org.scalacheck.{Prop, Arbitrary, Gen}

scala> def arrowEqualsProp(f: Person => Breakfast, g: Person => Breakfast)
         (implicit ev1: Equal[Breakfast], ev2: Arbitrary[Person]): Prop =
         Prop.forAll { a: Person =>
           f(a) === g(a)
         } 
arrowEqualsProp: (f: Person => Breakfast, g: Person => Breakfast)
(implicit ev1: scalaz.Equal[Breakfast], implicit ev2: org.scalacheck.Arbitrary[Person])org.scalacheck.Prop

scala> implicit val arbPerson: Arbitrary[Person] = Arbitrary {
         Gen.oneOf(John, Mary, Sam)
       }
arbPerson: org.scalacheck.Arbitrary[Person] = org.scalacheck.Arbitrary\$\$anon\$2@41ec9951

scala> implicit val breakfastEqual: Equal[Breakfast] = Equal.equalA[Breakfast]
breakfastEqual: scalaz.Equal[Breakfast] = scalaz.Equal\$\$anon\$4@783babde

scala> arrowEqualsProp(favoriteBreakfast, favoritePersonsBreakfast)
res0: org.scalacheck.Prop = Prop

scala> res0.check
! Falsified after 1 passed tests.
> ARG_0: John

scala> arrowEqualsProp(favoriteBreakfast, favoriteBreakfast)
res2: org.scalacheck.Prop = Prop

scala> res2.check
+ OK, passed 100 tests.
```

`arrowEqualsProp` をもう少し一般化してみる:

```scala
scala> def arrowEqualsProp[A, B](f: A => B, g: A => B)
         (implicit ev1: Equal[B], ev2: Arbitrary[A]): Prop =
         Prop.forAll { a: A =>
           f(a) === g(a)
         } 
arrowEqualsProp: [A, B](f: A => B, g: A => B)
(implicit ev1: scalaz.Equal[B], implicit ev2: org.scalacheck.Arbitrary[A])org.scalacheck.Prop

scala> arrowEqualsProp(favoriteBreakfast, favoriteBreakfast)
res4: org.scalacheck.Prop = Prop

scala> res4.check
+ OK, passed 100 tests.
```
