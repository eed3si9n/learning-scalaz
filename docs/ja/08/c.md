---
out: Composing-monadic-functions.html
---

### モナディック関数の合成

LYAHFGG:

> 第13章でモナド則を紹介したとき、`<=<` 関数は関数合成によく似ているど、普通の関数 `a -> b` ではなくて、`a -> m b` みたいなモナディック関数に作用するのだよと言いました。

これも飛ばしてたみたいだ。

### Kleisli

Scalaz には [Kleisli](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Kleisli.scala) と呼ばれる `A => M[B]` という型の関数に対する特殊なラッパーがある:

```scala
sealed trait Kleisli[M[+_], -A, +B] { self =>
  def run(a: A): M[B]
  ...
  /** alias for `andThen` */
  def >=>[C](k: Kleisli[M, B, C])(implicit b: Bind[M]): Kleisli[M, A, C] =  kleisli((a: A) => b.bind(this(a))(k(_)))
  def andThen[C](k: Kleisli[M, B, C])(implicit b: Bind[M]): Kleisli[M, A, C] = this >=> k
  /** alias for `compose` */ 
  def <=<[C](k: Kleisli[M, C, A])(implicit b: Bind[M]): Kleisli[M, C, B] = k >=> this
  def compose[C](k: Kleisli[M, C, A])(implicit b: Bind[M]): Kleisli[M, C, B] = k >=> this
  ...
}

object Kleisli extends KleisliFunctions with KleisliInstances {
  def apply[M[+_], A, B](f: A => M[B]): Kleisli[M, A, B] = kleisli(f)
}
```

構築するには `Kleisli` オブジェクトを使う:

```scala
scala> val f = Kleisli { (x: Int) => (x + 1).some }
f: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@7da2734e

scala> val g = Kleisli { (x: Int) => (x * 100).some }
g: scalaz.Kleisli[Option,Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@49e07991
```

`<=<` を使って関数を合成すると、`f compose g` と同様に右辺項が先に適用される。

```scala
scala> 4.some >>= (f <=< g)
res59: Option[Int] = Some(401)
```

`>=>` を使うと、`f andThen g` 同様に左辺項が先に適用される:

```scala
scala> 4.some >>= (f >=> g)
res60: Option[Int] = Some(500)
```

### Reader 再び

ボーナスとして、Scalaz は `Reader` を `Kleisli` の特殊形として以下のように定義する:

```scala
  type ReaderT[F[+_], E, A] = Kleisli[F, E, A]
  type Reader[E, A] = ReaderT[Id, E, A]
  object Reader {
    def apply[E, A](f: E => A): Reader[E, A] = Kleisli[Id, E, A](f)
  }
```

6日目のリーダーの例題は以下のように書き換えることができる:

```scala
scala> val addStuff: Reader[Int, Int] = for {
         a <- Reader { (_: Int) * 2 }
         b <- Reader { (_: Int) + 10 }
       } yield a + b
addStuff: scalaz.Reader[Int,Int] = scalaz.KleisliFunctions\$\$anon\$18@343bd3ae

scala> addStuff(3)
res76: scalaz.Id.Id[Int] = 19
```

関数をモナドとして使っていることが少しかは明らかになったと思う。
