---
out: method-injection.html
---

### メソッド注入 (enrich my library)

> `Monoid` を使ってある型の 2つの値を足す関数を書いた場合、このようになります。

```scala
scala> def plus[A: Monoid](a: A, b: A): A = implicitly[Monoid[A]].mappend(a, b)
plus: [A](a: A, b: A)(implicit evidence\$1: Monoid[A])A

scala> plus(3, 4)
res25: Int = 7
```

これに演算子を提供したい。だけど、1つの型だけを拡張するんじゃなくて、`Monoid` のインスタンスを持つ全ての型を拡張したい。Scalaz 7 スタイルでこれを行なってみる。

```scala
scala> trait MonoidOp[A] {
         val F: Monoid[A]
         val value: A
         def |+|(a2: A) = F.mappend(value, a2)
       }
defined trait MonoidOp

scala> implicit def toMonoidOp[A: Monoid](a: A): MonoidOp[A] = new MonoidOp[A] {
         val F = implicitly[Monoid[A]]
         val value = a
       }
toMonoidOp: [A](a: A)(implicit evidence\$1: Monoid[A])MonoidOp[A]

scala> 3 |+| 4
res26: Int = 7

scala> "a" |+| "b"
res28: String = ab
```

1つの定義から `Int` と `String` の両方に `|+|` 演算子を注入することができた。

### 標準型構文

同様のテクニックを使って Scalaz は `Option` や `Boolean` のような標準ライブラリ型へのメソッド注入も提供する:

```scala
scala> 1.some | 2
res0: Int = 1

scala> Some(1).getOrElse(2)
res1: Int = 1

scala> (1 > 10)? 1 | 2
res3: Int = 2

scala> if (1 > 10) 1 else 2
res4: Int = 2
```

これで Scalaz がどういうライブラリなのかというのを感じてもらえただろうか。
