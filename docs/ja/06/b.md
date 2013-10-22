
### Reader

LYAHFGG:

> 第11章では、関数を作る型、`(->) r` も、`Functor` のインスタンスであることを見ました。

```scala
scala> val f = (_: Int) * 5
f: Int => Int = <function1>

scala> val g = (_: Int) + 3
g: Int => Int = <function1>

scala> (g map f)(8)
res22: Int = 55
```

> それから、関数はアプリカティブファンクターであることも見ましたね。これにより、関数が将来返すであろう値を、すでに持っているかのように演算できるようになりました。

```scala
scala> val f = ({(_: Int) * 2} |@| {(_: Int) + 10}) {_ + _}
warning: there were 1 deprecation warnings; re-run with -deprecation for details
f: Int => Int = <function1>

scala> f(3)
res35: Int = 19
```

> 関数の型 `(->) r` はファンクターであり、アプリカティブファンクターであるばかりでなく、モナドでもあります。これまでに登場したモナド値と同様、関数もまた文脈を持った値だとみなすことができるのです。関数にとっての文脈とは、値がまだ手元になく、値が欲しければその関数を別の何かに適用しないといけない、というものです。

この例題も実装してみよう:

```scala
scala> val addStuff: Int => Int = for {
         a <- (_: Int) * 2
         b <- (_: Int) + 10
       } yield a + b
addStuff: Int => Int = <function1>

scala> addStuff(3)
res39: Int = 19
```

> `(*2)` と `(+10)` はどちらも `3` に適用されます。実は、`return (a+b)` も同じく `3` に適用されるんですが、引数を無視して常に `a+b` を返しています。そいういうわけで、関数モナドは **Reader モナド**とも呼ばれたりします。すべての関数が共通の情報を「読む」からです。

要は、Reader モナドは値が既にあるかのようなフリをさせてくれる。恐らくこれは1つのパラメータを受け取る関数でしか使えないと予想している。`Option` や `List` モナドと違って、`Writer` も Reader モナドも標準ライブラリには入っていないし、便利そうだ。

続きはまたここから。
