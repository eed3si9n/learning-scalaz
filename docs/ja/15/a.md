<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-9038bw.jpg">
<div class="credit">Rodolfo Cartas for openphoto.net</div>
</div>


### Arrow

射とは、圏論の用語で関数っぽい振る舞いをするものの抽象概念だ。Scalaz だと `Function1[A, B]`、`PartialFunction[A, B]`、`Kleisli[F[_], A, B]`、そして `CoKleisli[F[_], A, B]` がこれにあたる。他の型クラスがコンテナを抽象化するのと同様に `Arrow` はこれらを抽象化する。

以下が [`Arrow`](https://github.com/eed3si9n/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Arrow.scala) の型クラスコントラクトだ:

```scala
trait Arrow[=>:[_, _]] extends Category[=>:] { self =>
  def id[A]: A =>: A
  def arr[A, B](f: A => B): A =>: B
  def first[A, B, C](f: (A =>: B)): ((A, C) =>: (B, C))
}
```

`Arrow[=>:[_, _]]` は `Category[=>:]` を継承するみたいだ。

### Category と Compose

以下が <a href="$scalazBaseUrl$/core/src/main/scala/scalaz/Category.scala"><code>Category[=>:[_, _]]</code></a> だ:

```scala
trait Category[=>:[_, _]] extends ArrId[=>:] with Compose[=>:] { self =>
  // no contract function
} 
```

これは <a href="$scalazBaseUrl$/core/src/main/scala/scalaz/Compose.scala"><code>Compose[=>:]</code></a> を継承する:

```scala
trait Compose[=>:[_, _]]  { self =>
  def compose[A, B, C](f: B =>: C, g: A =>: B): (A =>: C)
}
```

`compose` 関数は 2つの射を合成する。`Compose` は以下の[演算子]($scalazBaseUrl$/core/src/main/scala/scalaz/syntax/ComposeSyntax.scala)を導入する:

```scala
trait ComposeOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def <<<[C](x: F[C, A]): F[C, B] = F.compose(self, x)
  final def >>>[C](x: F[B, C]): F[A, C] = F.compose(x, self)
}
```

`>>>` と `<<<` の意味は射に依存するけど、関数の場合は `andThen` と `compose` と同じだ:

```scala
scala> val f = (_:Int) + 1
f: Int => Int = <function1>

scala> val g = (_:Int) * 100
g: Int => Int = <function1>

scala> (f >>> g)(2)
res0: Int = 300

scala> (f <<< g)(2)
res1: Int = 201
``` 

### Arrow、再び

`Arrow[=>:[_, _]]` の型宣言は少し変わってみえるけど、これは `Arrow[M[_, _]]` と言っているのと変わらない。2つのパラメータを取る型コンストラクタで便利なのは `=>:[A, B]` を `A =>: B` のように中置記法で書けることだ。

`arr` 関数は普通の関数から射を作り、`id` は恒等射を返し、`first` は既存の射の出入力をペアに拡張した新しい射を返す。

上記の関数を使って、`Arrow` は以下の[演算子]($scalazBaseUrl$/core/src/main/scala/scalaz/syntax/ArrowSyntax.scala)を導入する:

```scala
trait ArrowOps[F[_, _],A, B] extends Ops[F[A, B]] {
  final def ***[C, D](k: F[C, D]): F[(A, C), (B, D)] = F.splitA(self, k)
  final def &&&[C](k: F[A, C]): F[A, (B, C)] = F.combine(self, k)
  ...
}
```

Haskell の [Arrow tutorial](http://www.haskell.org/haskellwiki/Arrow_tutorial) を読んでみる:

> `(***)` combines two arrows into a new arrow by running the two arrows on a pair of values (one arrow on the first item of the pair and one arrow on the second item of the pair).
>
> `(***)` は 2つの射を値のペアに対して (1つの射はペアの最初の項で、もう 1つの射はペアの 2つめの項で) 実行することで 1つの新しいに射へと組み合わせる。

具体例で説明すると:

```scala
scala> (f *** g)(1, 2)
res3: (Int, Int) = (2,200)
```

> `(&&&)` は 2つの射を両方とも同じ値に対して実行することで 1つの新しい射へと組み合わせる:

以下が `&&&` の例:

```scala
scala> (f &&& g)(2)
res4: (Int, Int) = (3,200)
```

関数やペアにらんらかのコンテキストを与えたい場合は射が便利かもしれない。
