---
out: YesNotypeclass.html
---

### Yes と No の型クラス

Scalaz 流に truthy 値の型クラスを作れるか試してみよう。ただし、命名規則は我流でいく。Scalaz は `Show`、`show`、`show` というように 3つや 4つの異なるものに型クラスの名前を使っているため分かりづらい所があると思う。

`CanBuildFrom` に倣って型クラスは `Can` で始めて、sjson/sbinary に倣って型クラスのメソッドは動詞 + `s` と命名するのが僕の好みだ。`yesno` というのは意味不明なので、`truthy`  と呼ぶ。ゴールは `1.truthy` が `true` を返すことだ。この欠点は型クラスのインスタンスを `CanTruthy[Int].truthys(1)` のように関数として呼ぶと名前に `s` が付くことだ。

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

trait CanTruthy[A] { self =>
  /** @return true, if `a` is truthy. */
  def truthys(a: A): Boolean
}
object CanTruthy {
  def apply[A](implicit ev: CanTruthy[A]): CanTruthy[A] = ev
  def truthys[A](f: A => Boolean): CanTruthy[A] = new CanTruthy[A] {
    def truthys(a: A): Boolean = f(a)
  }
}
trait CanTruthyOps[A] {
  def self: A
  implicit def F: CanTruthy[A]
  final def truthy: Boolean = F.truthys(self)
}
object ToCanIsTruthyOps {
  implicit def toCanIsTruthyOps[A](v: A)(implicit ev: CanTruthy[A]) =
    new CanTruthyOps[A] {
      def self = v
      implicit def F: CanTruthy[A] = ev
    }
}

// Exiting paste mode, now interpreting.

defined trait CanTruthy
defined module CanTruthy
defined trait CanTruthyOps
defined module ToCanIsTruthyOps

scala> import ToCanIsTruthyOps._
import ToCanIsTruthyOps._
```

`Int` への型クラスのインスタンスを定義する:

```scala
scala> implicit val intCanTruthy: CanTruthy[Int] = CanTruthy.truthys({
         case 0 => false
         case _ => true
       })
intCanTruthy: CanTruthy[Int] = CanTruthy$$anon$1@71780051

scala> 10.truthy
res6: Boolean = true
```

次が `List[A]`:

```scala
scala> implicit def listCanTruthy[A]: CanTruthy[List[A]] = CanTruthy.truthys({
         case Nil => false
         case _   => true  
       })
listCanTruthy: [A]=> CanTruthy[List[A]]

scala> List("foo").truthy
res7: Boolean = true

scala> Nil.truthy
<console>:23: error: could not find implicit value for parameter ev: CanTruthy[scala.collection.immutable.Nil.type]
              Nil.truthy
```

またしても不変な型パラメータのせいで `Nil` を特殊扱いしなくてはいけない。

```scala
scala> implicit val nilCanTruthy: CanTruthy[scala.collection.immutable.Nil.type] = CanTruthy.truthys(_ => false)
nilCanTruthy: CanTruthy[collection.immutable.Nil.type] = CanTruthy$$anon$1@1e5f0fd7

scala> Nil.truthy
res8: Boolean = false
```

`Boolean` は `identity` を使える:

```scala
scala> implicit val booleanCanTruthy: CanTruthy[Boolean] = CanTruthy.truthys(identity)
booleanCanTruthy: CanTruthy[Boolean] = CanTruthy$$anon$1@334b4cb

scala> false.truthy
res11: Boolean = false
```

LYAHFGG 同様に `CanTruthy` 型クラスを使って `truthyIf` を定義しよう:

> では、`if` の真似をして `YesNo` 値を取る関数を作ってみましょう。

名前渡しを使って渡された引数の評価を遅延する:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

def truthyIf[A: CanTruthy, B, C](cond: A)(ifyes: => B)(ifno: => C) =
  if (cond.truthy) ifyes
  else ifno

// Exiting paste mode, now interpreting.

truthyIf: [A, B, C](cond: A)(ifyes: => B)(ifno: => C)(implicit evidence$1: CanTruthy[A])Any
```

使用例はこうなる:

```scala
scala> truthyIf (Nil) {"YEAH!"} {"NO!"}
res12: Any = NO!

scala> truthyIf (2 :: 3 :: 4 :: Nil) {"YEAH!"} {"NO!"}
res13: Any = YEAH!

scala> truthyIf (true) {"YEAH!"} {"NO!"}
res14: Any = YEAH!
```

続きはまたあとで。
