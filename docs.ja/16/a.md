<div class="floatingimage">
<img src="http://eed3si9n.com/images/mementobw.jpg">
</div>


### Memo

関数が純粋だからといってその計算量が安いとは限らない。例えば、全ての 8文字の ASCII 文字列の順列に対する SHA-1 ハッシュのリストを求めるとする。タブ文字を抜くと ASCII には 95 の表示可能な文字があるので、繰り上げて 100 とする。`100 ^ 8` は `10 ^ 16` だ。たとえ秒間 1000 ハッシュ処理できたとしても `10 ^ 13` 秒、つまり 316888年かかる。

RAM に少し余裕があれば、計算結果をキャッシュすることで高価な計算とスペースをトレードすることができる。これはメモ化と呼ばれる。以下が [`Memo`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Memo.scala) のコントラクトだ:

```scala
sealed trait Memo[@specialized(Int) K, @specialized(Int, Long, Double) V] {
  def apply(z: K => V): K => V
}
```

潜在的に高価な関数をインプットに渡して、同様に振る舞うけども結果をキャッシュする関数を返してもらう。`Memo` object の下に `Memo.mutableHashMapMemo[K, V]`、`Memo.weakHashMapMemo[K, V]`、や `Memo.arrayMemo[V]` などのいくつかの `Memo` のデフォルト実装がある。

一般的に、これらの最適化のテクニックは気をつけるべきだ。まず、全体の性能をプロファイルして実際に時間を節約できるのか確認するべきだし、スペースとのトレードオフも永遠に増大し続けないか解析したほうがいい。

[Memoization tutorial](http://www.haskell.org/haskellwiki/Memoization) にあるフィボナッチ数の例を実装してみよう:

```scala
scala> val slowFib: Int => Int = {
         case 0 => 0
         case 1 => 1
         case n => slowFib(n - 2) + slowFib(n - 1)
       }
slowFib: Int => Int = <function1>

scala> slowFib(30)
res0: Int = 832040

scala> slowFib(40)
res1: Int = 102334155

scala> slowFib(45)
res2: Int = 1134903170
```

`showFib(45)` は返ってくるのに少し時間がかかった。次がメモ化版:

```scala
scala> val memoizedFib: Int => Int = Memo.mutableHashMapMemo {
         case 0 => 0
         case 1 => 1
         case n => memoizedFib(n - 2) + memoizedFib(n - 1)
       }
memoizedFib: Int => Int = <function1>

scala> memoizedFib(30)
res12: Int = 832040

scala> memoizedFib(40)
res13: Int = 102334155

scala> memoizedFib(45)
res14: Int = 1134903170
```

結果が即座に返ってくるようになった。便利なのはメモ化した関数を作る側も使う側もあまり意識せずにできることだ。Adam Rosien さんも [Scalaz "For the Rest of Us" talk](https://github.com/arosien/scalaz-base-talk-201208) ([動画](http://www.youtube.com/watch?v=kcfIH3GYXMI)) でこの点を言っている。
