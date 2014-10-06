---
out: Kinds.html
---

  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [cheatsheet]: scalaz-cheatsheet.html

### 型を司るもの、カインド

[型や型クラスを自分で作ろう][moott] の節で昨日のうちにカバーしておくべきだったけどしなかったのはカインドと型の話だ。Scalaz の理解には関係無いだろうと思ってたけど、関係あるので、座って聞いて欲しい。

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854) 曰く:

> 型とは、値について何らかの推論をするために付いている小さなラベルです。そして、型にも小さなラベルが付いているんです。その名は**種類** (kind)。
> ...
> 種類とはそもそも何者で、何の役に立つのでしょう？さっそく GHCi の `:k` コマンドを使って、型の種類を調べてみましょう。

Scala 2.10 時点では Scala REPL に `:k` コマンドが無かったので、ひとつ書いてみた: [kind.scala](https://gist.github.com/eed3si9n/3610635)。
[scala/scala#2340](https://github.com/scala/scala/pull/2340) を送ってくれた George Leontiev さん ([@folone](https://twitter.com/folone)) その他の協力もあって、Scala 2.11 より `:kind` コマンドは標準機能として取り込まれた。使ってみよう:

```
scala> :k Int
scala.Int's kind is A

scala> :k -v Int
scala.Int's kind is A
*
プロパーな型だ。

scala> :k -v Option
scala.Option's kind is F[+A]
* -(+)-> *
型コンストラクタ: 1階カインド型だ。

scala> :k -v Either
scala.util.Either's kind is F[+A1,+A2]
* -(+)-> * -(+)-> *
型コンストラクタ: 1階カインド型だ。

scala> :k -v Equal
scalaz.Equal's kind is F[A]
* -> *
型コンストラクタ: 1階カインド型だ。

scala> :k -v Functor
scalaz.Functor's kind is X[F[A]]
(* -> *) -> *
型コンストラクタを受け取る型コンストラクタ: 高カインド型だ。
```

上から順番に。`Int` と他の全ての値を作ることのできる型はプロパーな型と呼ばれ `*` というシンボルで表記される (「型」と読む)。これは値レベルだと `1` に相当する。Scala の型変数構文を用いるとこれは `A` と書ける。

1階値、つまり `(_: Int) + 3` のような値コンストラクタ、は普通関数と呼ばれる。同様に、1階カインド型はプロパーな型になるために他の型を受け取る型のことだ。これは普通型コンストラクタと呼ばれる。`Option`、`Either`、`Equal` などは全て1階カインドだ。これらが他の型を受け取ることを表記するのにカリー化した表記法を用いて `* -> *` や `* -> * -> *` などと書く。このとき `Option[Int]` は `*` で、`Option` が `* -> *` であることに注意。Scala の型変数構文を用いるとこれらは `F[+A]`、 `F[+A1,+A2]` となる。

`(f: Int => Int, list: List[Int]) => list map {f}` のような高階値、つまり関数を受け取る関数、は普通高階関数と呼ばれる。同様に、高カインド型は型コンストラクタを受け取る型コンストラクタだ。これは多分高カインド型コンストラクタと呼ばれるべきだが、その名前は使われていない。これらは `(* -> *) -> *` と表記される。Scala の型変数構文を用いるとこれは `X[F[A]]` と書ける。

Scalaz 7.1 の場合、`Equal` その他は `F[A]` のカインドを持ち、`Functor` とその派生型は `X[F[A]]`カインドを持つ。
Scala は型クラスという概念を型コンストラクタを用いてエンコード (悪く言うとコンプレクト) するため、この辺の用語が混乱しやすいことがある。例えば、データ構造である `List` は函手 (functor) となるという言い方をして、これは `List` に対して `Functor[List]` のインスタンスを導き出せるという意味だ。`List` に対するインスタンスなんて一つしか無いのが分かってるので、「`List` は函手である (`List` is a functor)」と言うことができる。 is-a に関する議論は以下も参照:

<blockquote class="twitter-tweet" lang="en"><p>In FP, &quot;is-a&quot; means &quot;an instance can be derived from.&quot; <a href="https://twitter.com/jimduey">@jimduey</a> <a href="https://twitter.com/hashtag/CPL14?src=hash">#CPL14</a> It&#39;s a provable relationship, not reliant on LSP.</p>&mdash; Jessica Kerr (@jessitron) <a href="https://twitter.com/jessitron/status/438432946383360000">February 25, 2014</a></blockquote>

`List` そのものは `F[+A]` なので、`F` が函手に関連すると覚えるのは簡単だ。しかし、型クラス定義の `Functor` は `F[A]` を囲む必要があるので、カインドは `X[F[A]]` となっている。さらにこれを混乱させるのが、Scala から型コンストラクタを第一級変数として扱えることが目新しかったため、コンパイラは 1階カインド型でも「高カインド型」と呼んでいることだ:

```scala
scala> trait Test {
         type F[_]
       }
<console>:14: warning: higher-kinded type should be enabled
by making the implicit value scala.language.higherKinds visible.
This can be achieved by adding the import clause 'import scala.language.higherKinds'
or by setting the compiler option -language:higherKinds.
See the Scala docs for value scala.language.higherKinds for a discussion
why the feature should be explicitly enabled.
         type F[_]
              ^
```

注入された演算子を使っているぶんにはこれらの心配をする必要はないはずだ:

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

[チートシート][cheatsheet] を始めたとき、Scalaz 7 のソースコードに合わせて `Equal[F]` と書いた。すると Adam Rosien さんに `Equal[A]` と表記すべきと指摘された。

<blockquote class="twitter-tweet"><p>@<a href="https://twitter.com/eed3si9n">eed3si9n</a> love the scalaz cheat sheet start, but using the type param F usually means Functor, what about A instead?</p>&mdash; Adam Rosien (@arosien) <a href="https://twitter.com/arosien/status/241990437269815296">September 1, 2012</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>

これで理由がよく分かった!
