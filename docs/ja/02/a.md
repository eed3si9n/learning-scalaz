
### Functor

LYAHFGG:

> 今度は、`Functor` （ファンクター）という型クラスを見ていきたいと思います。`Functor` は、**全体を写せる** (map over) ものの型クラスです。

本のとおり、[実装がどうなってるか](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Functor.scala)をみてみよう:

```scala
trait Functor[F[_]]  { self =>
  /** Lift `f` into `F` and apply to `F[A]`. */
  def map[A, B](fa: F[A])(f: A => B): F[B]

  ...
}
```

これが可能とする[演算子](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/FunctorSyntax.scala)はこうなっている:

```scala
trait FunctorOps[F[_],A] extends Ops[F[A]] {
  implicit def F: Functor[F]
  ////
  import Leibniz.===

  final def map[B](f: A => B): F[B] = F.map(self)(f)
  
  ...
}
```

つまり、これは関数 `A => B` を受け取り `F[B]` を返す `map` メソッドを宣言する。コレクションの `map` メソッドなら得意なものだ。

```scala
scala> List(1, 2, 3) map {_ + 1}
res15: List[Int] = List(2, 3, 4)
```

Scalaz は `Tuple` などにも `Functor` のインスタンスを定義している。

```scala
scala> (1, 2, 3) map {_ + 1}
res28: (Int, Int, Int) = (1,2,4)
```

### Functor としての関数

Scalaz は `Function1` に対する `Functor` のインスタンスも定義する。

```scala
scala> ((x: Int) => x + 1) map {_ * 7}
res30: Int => Int = <function1>

scala> res30(3)
res31: Int = 28
```

これは興味深い。つまり、`map` は関数を合成する方法を与えてくれるが、順番が `f compose g` とは逆順だ! 通りで Scalaz は `map` のエイリアスとして ` ∘` を提供するわけだ。`Function1` のもう1つのとらえ方は、定義域 (domain) から値域 (range) への無限の写像だと考えることができる。入出力に関しては飛ばして [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids) へ行こう (本だと、「ファンクターからアプリカティブファンクターへ」)。

> ファンクターとしての関数
> ...
>
> ならば、型 `fmap :: (a -> b) -> (r -> a) -> (r -> b)` が意味するものとは？この型は、`a` から `b` への関数と、`r` から `a` への関数を引数に受け取り、`r` から `b` への関数を返す、と読めます。何か思い出しませんか？そう！関数合成です！

あ、すごい Haskell も僕がさっき言ったように関数合成をしているという結論になったみたいだ。ちょっと待てよ。

```haskell
ghci> fmap (*3) (+100) 1
303
ghci> (*3) . (+100) \$ 1  
303 
```

Haskell では `fmap` は `f compose g` を同じ順序で動作してるみたいだ。Scala でも同じ数字を使って確かめてみる:

```scala
scala> (((_: Int) * 3) map {_ + 100}) (1)
res40: Int = 103
```

何かがおかしい。`fmap` の宣言と Scalaz の `map` 演算子を比べてみよう:

```haskell
fmap :: (a -> b) -> f a -> f b

```

そしてこれが Scalaz:

```scala
final def map[B](f: A => B): F[B] = F.map(self)(f)

```

順番が完全に違っている。ここでの `map` は `F[A]` に注入されたメソッドのため、投射される側のデータ構造が最初に来て、次に関数が来る。`List` で考えると分かりやすい:

```haskell
ghci> fmap (*3) [1, 2, 3]
[3,6,9]
```

で

```scala
scala> List(1, 2, 3) map {3*}
res41: List[Int] = List(3, 6, 9)
```

ここでも順番が逆なことが分かる。

> `fmap` も、関数とファンクター値を取ってファンクター値を返す 2 引数関数と思えますが、そうじゃなくて、関数を取って「元の関数に似てるけどファンクター値を取ってファンクター値を返す関数」を返す関数だと思うこともできます。`fmap` は、関数 `a -> b` を取って、関数 `f a -> f b` を返すのです。こういう操作を、関数の**持ち上げ** (lifting) といいます。

```haskell
ghci> :t fmap (*2)  
fmap (*2) :: (Num a, Functor f) => f a -> f a  
ghci> :t fmap (replicate 3)  
fmap (replicate 3) :: (Functor f) => f a -> f [a]  
```

この関数の持ち上げ (lifting) は是非やってみたい。`Functor` の型クラス内に色々便利な関数が定義されていて、その中の 1つに `lift` がある:

```scala
scala> Functor[List].lift {(_: Int) * 2}
res45: List[Int] => List[Int] = <function1>

scala> res45(List(3))
res47: List[Int] = List(6)
```

`Functor` は他にもデータ構造の中身を書きかえる `>|`、`as`、`fpair`、`strengthL`、`strengthR`、そして `void` などの演算子を可能とする:

```scala
scala> List(1, 2, 3) >| "x"
res47: List[String] = List(x, x, x)

scala> List(1, 2, 3) as "x"
res48: List[String] = List(x, x, x)

scala> List(1, 2, 3).fpair
res49: List[(Int, Int)] = List((1,1), (2,2), (3,3))

scala> List(1, 2, 3).strengthL("x")
res50: List[(String, Int)] = List((x,1), (x,2), (x,3))

scala> List(1, 2, 3).strengthR("x")
res51: List[(Int, String)] = List((1,x), (2,x), (3,x))

scala> List(1, 2, 3).void
res52: List[Unit] = List((), (), ())
```
