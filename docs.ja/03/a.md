---
out: Kinds.html
---

  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses

### 型を司るもの、カインド

[型や型クラスを自分で作ろう][moott] の節で昨日のうちにカバーしておくべきだったけどしなかったのはカインドと型の話だ。Scalaz の理解には関係無いだろうと思ってたけど、関係あるので、座って聞いて欲しい。

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) 曰く:

> 型とは、値について何らかの推論をするために付いている小さなラベルです。そして、型にも小さなラベルが付いているんです。その名は**種類** (kind)。
> ...
> 種類とはそもそも何者で、何の役に立つのでしょう？さっそく GHCi の `:k` コマンドを使って、型の種類を調べてみましょう。

Scala REPL に `:k` コマンドが見つからなかったので Scala 2.10.0-M7 向けにひとつ書いてみた。 型コンストラクタに対してはコンパニオンの型を渡す。(アイディアは paulp さんから頂いた)

```scala
def kind[A: scala.reflect.TypeTag]: String = {
  import scala.reflect.runtime.universe._
  def typeKind(sig: Type): String = sig match {
    case PolyType(params, resultType) =>
      (params map { p =>
        typeKind(p.typeSignature) match {
          case "*" => "*"
          case s   => "(" + s + ")"
        }
      }).mkString(" -> ") + " -> *"
    case _ => "*"
  }
  def typeSig(tpe: Type): Type = tpe match {
    case SingleType(pre, sym) => sym.companionSymbol.typeSignature
    case ExistentialType(q, TypeRef(pre, sym, args)) => sym.typeSignature
    case TypeRef(pre, sym, args) => sym.typeSignature
  }
  val sig = typeSig(typeOf[A])
  val s = typeKind(sig)
  sig.typeSymbol.name + "のカインドは " + s + "。" + (s match {
    case "*" =>
      "プロパーな型だ。"
    case x if !(x contains "(") =>
      "型コンストラクタ: 1階カインド型だ。"
    case x =>
      "型コンストラクタを受け取る型コンストラクタ: 高カインド型だ。"
  })
}
```

1日目の `build.sbt` を使って `sbt console` を起動して上の関数をコピペする。使ってみよう:

```
scala> kind[Int]
res0: String = Intのカインドは *。プロパーな型だ。

scala> kind[Option.type]
res1: String = Optionのカインドは * -> *。型コンストラクタ: 1階カインド型だ。

scala> kind[Either.type]
res2: String = Eitherのカインドは * -> * -> *。型コンストラクタ: 1階カインド型だ

scala> kind[Equal.type]
res3: String = Equalのカインドは * -> *。型コンストラクタ: 1階カインド型だ。

scala> kind[Functor.type]
res4: String = Functorのカインドは (* -> *) -> *。型コンストラクタを受け取る型コンストラクタ: 高カインド型だ。
```

上から順番に。`Int` と他の全ての値を作ることのできる型はプロパーな型と呼ばれ `*` というシンボルで表記される (「型」と読む)。これは値レベルだと `1` に相当する。

1階値、つまり `(_: Int) + 3` のような値コンストラクタ、は普通関数と呼ばれる。同様に、1階カインド型はプロパーな型になるために他の型を受け取る型のことだ。これは普通型コンストラクタと呼ばれる。`Option`、`Either`、`Equal` などは全て1階カインドだ。これらが他の型を受け取ることを表記するのにカリー化した表記法を用いて `* -> *` や `* -> * -> *` などと書く。このとき `Option[Int]` は `*` で、`Option` が `* -> *` であることに注意。

`(f: Int => Int, list: List[Int]) => list map {f}` のような高階値、つまり関数を受け取る関数、は普通高階関数と呼ばれる。同様に、高カインド型は型コンストラクタを受け取る型コンストラクタだ。これは多分高カインド型コンストラクタと呼ばれるべきだが、その名前は使われていない。これらは `(* -> *) -> *` と表記される。

Scalaz 7 の場合、`Equal` その他は `* -> *` のカインドを持ち、`Functor` とその派生型は `(* -> *) -> *` カインドを持つ。注入された演算子を使っているぶんにはこれらの心配をする必要はない:

```scala
scala> List(1, 2, 3).shows
res11: String = [1,2,3]
```

だけど `Show[A].shows` を使いたければ、これが `Show[List[Int]]` であって `Show[List]` ではないことを理解している必要がある。同様に、関数を持ち上げ (lift) たければ `Functor[F]` (`F` は `Functor` の `F`) だと知っている必要がある:

```scala
scala> Functor[List[Int]].lift((_: Int) + 2)
<console>:14: error: List[Int] takes no type parameters, expected: one
              Functor[List[Int]].lift((_: Int) + 2)
                      ^

scala> Functor[List].lift((_: Int) + 2)
res13: List[Int] => List[Int] = <function1>
```

[チートシート](http://eed3si9n.com/scalaz-cheat-sheet) を始めたとき、Scalaz 7 のソースコードに合わせて `Equal[F]` と書いた。すると Adam Rosien さんに `Equal[A]` と表記すべきと指摘された。

<blockquote class="twitter-tweet"><p>@<a href="https://twitter.com/eed3si9n">eed3si9n</a> love the scalaz cheat sheet start, but using the type param F usually means Functor, what about A instead?</p>&mdash; Adam Rosien (@arosien) <a href="https://twitter.com/arosien/status/241990437269815296">September 1, 2012</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

これで理由がよく分かった!
