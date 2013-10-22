
### Tagged type

「すごいHaskellたのしく学ぼう」の本を持ってるひとは新しい章に進める。モノイドだ。ウェブサイトを読んでるひとは [Functors, Applicative Functors and Monoids](http://learnyouahaskell.com/functors-applicative-functors-and-monoids) の続きだ。

LYAHFGG:

> Haskell の **newtype** キーワードは、まさにこのような「1つの型を取り、それを何かにくるんで別の型に見せかけたい」という場合のために作られたものです。

これは Haskell の言語レベルでの機能なので、Scala に移植するのは無理なんじゃないかと思うと思う。
ところが、約1年前 (2011年9月) [Miles Sabin さん (@milessabin)](https://twitter.com/milessabin) が [gist](https://gist.github.com/89c9b47a91017973a35f) を書き、それを `Tagged` と名付け、[Jason Zaugg さん (@retronym)](https://twitter.com/retronym) が `@@` という型エイリアスを加えた。

```scala
type Tagged[U] = { type Tag = U }
type @@[T, U] = T with Tagged[U]
```

これについて読んでみたいひとは [Eric Torreborre さん (@etorreborre)](http://twitter.com/etorreborre) が [Practical uses for Unboxed Tagged Types](http://etorreborre.blogspot.com/2011/11/practical-uses-for-unboxed-tagged-types.html)、それから [Tim Perrett さん (@timperrett)](http://es.twitter.com/timperrett) が [Unboxed new types within Scalaz7](http://timperrett.com/2012/06/15/unboxed-new-types-within-scalaz7/) を書いている。

例えば、体積をキログラムで表現したいとする。kg は国際的な標準単位だからだ。普通は `Double` を渡して終わる話だけど、それだと他の `Double` の値と区別が付かない。case class は使えるだろうか?

```scala
case class KiloGram(value: Double)
```

型安全性は加わったけど、使うたびに `x.value` というふうに値を取り出さなきゃいけないのが不便だ。Tagged type 登場。

```scala
scala> sealed trait KiloGram
defined trait KiloGram

scala> def KiloGram[A](a: A): A @@ KiloGram = Tag[A, KiloGram](a)
KiloGram: [A](a: A)scalaz.@@[A,KiloGram]

scala> val mass = KiloGram(20.0)
mass: scalaz.@@[Double,KiloGram] = 20.0

scala> 2 * mass
res2: Double = 40.0
```

補足しておくと、`A @@ KiloGram` は `scalaz.@@[A, KiloGram]` の中置記法だ。これで相対論的エネルギーを計算する関数を定義できる。

```scala
scala> sealed trait JoulePerKiloGram
defined trait JoulePerKiloGram

scala> def JoulePerKiloGram[A](a: A): A @@ JoulePerKiloGram = Tag[A, JoulePerKiloGram](a)
JoulePerKiloGram: [A](a: A)scalaz.@@[A,JoulePerKiloGram]

scala> def energyR(m: Double @@ KiloGram): Double @@ JoulePerKiloGram =
     |   JoulePerKiloGram(299792458.0 * 299792458.0 * m)
energyR: (m: scalaz.@@[Double,KiloGram])scalaz.@@[Double,JoulePerKiloGram]

scala> energyR(mass)
res4: scalaz.@@[Double,JoulePerKiloGram] = 1.79751035747363533E18

scala> energyR(10.0)
<console>:18: error: type mismatch;
 found   : Double(10.0)
 required: scalaz.@@[Double,KiloGram]
    (which expands to)  Double with AnyRef{type Tag = KiloGram}
              energyR(10.0)
                      ^
```

見ての通り、素の `Double` を `energyR` に渡すとコンパイル時に失敗する。これは `newtype` そっくりだけど、`Int @@ KiloGram` など定義できるからより強力だと言える。
