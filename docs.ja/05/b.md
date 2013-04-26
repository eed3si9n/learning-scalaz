---
out: Walk-the-line.html
---

### 綱渡り

LYAHFGG:

> さて、棒の左右にとまった鳥の数の差が3以内であれば、ピエールはバランスを取れているものとしましょう。例えば、右に1羽、左に4羽の鳥がとまっているなら大丈夫。だけど左に5羽目の鳥がとまったら、ピエールはバランスを崩して飛び降りる羽目になります。

本の `Pole` の例題を実装してみよう。

```scala
scala> type Birds = Int
defined type alias Birds

scala> case class Pole(left: Birds, right: Birds)
defined class Pole
```

Scala ではこんな風に `Int` に型エイリアスを付けるのは一般的じゃないと思うけど、ものは試しだ。`landLeft` と `landRight` をメソッドをとして実装したいから `Pole` は case class にする。

```scala
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Pole = copy(left = left + n)
         def landRight(n: Birds): Pole = copy(right = right + n) 
       }
defined class Pole
```

OO の方が見栄えが良いと思う:

```scala
scala> Pole(0, 0).landLeft(2)
res10: Pole = Pole(2,0)

scala> Pole(1, 2).landRight(1)
res11: Pole = Pole(1,3)

scala> Pole(1, 2).landRight(-1)
res12: Pole = Pole(1,1)
```

チェインも可能:

```scala
scala> Pole(0, 0).landLeft(1).landRight(1).landLeft(2)
res13: Pole = Pole(3,1)

scala> Pole(0, 0).landLeft(1).landRight(4).landLeft(-1).landRight(-2)
res15: Pole = Pole(0,2)
```

本が言うとおり、中間値で失敗しても計算が続行してしまっている。失敗を `Option[Pole]` で表現しよう:

```scala
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
       }
defined class Pole

scala> Pole(0, 0).landLeft(2)
res16: Option[Pole] = Some(Pole(2,0))

scala> Pole(0, 3).landLeft(10)
res17: Option[Pole] = None
```

`flatMap` を使ってチェインする:

```scala
scala> Pole(0, 0).landRight(1) flatMap {_.landLeft(2)}
res18: Option[Pole] = Some(Pole(2,1))

scala> (none: Option[Pole]) flatMap {_.landLeft(2)}
res19: Option[Pole] = None

scala> Monad[Option].point(Pole(0, 0)) flatMap {_.landRight(2)} flatMap {_.landLeft(2)} flatMap {_.landRight(2)}
res21: Option[Pole] = Some(Pole(2,4))
```

初期値を `Option` コンテキストから始めるために `Monad[Option].point(...)` が使われていることに注意。`>>=` エイリアスも使うと見た目がモナディックになる:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landRight(2)} >>= {_.landLeft(2)} >>= {_.landRight(2)}
res22: Option[Pole] = Some(Pole(2,4))
```

モナディックチェインが綱渡りのシミュレーションを改善したか確かめる:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.landRight(4)} >>= {_.landLeft(-1)} >>= {_.landRight(-2)}
res23: Option[Pole] = None
```

うまくいった。

### ロープの上のバナナ

LYAHFGG:

> さて、今度はバランス棒にとまっている鳥の数によらず、いきなりピエールを滑らせて落っことす関数を作ってみましょう。この関数を `banana` と呼ぶことにします。

以下が常に失敗する `banana` だ:

```scala
scala> case class Pole(left: Birds, right: Birds) {
         def landLeft(n: Birds): Option[Pole] = 
           if (math.abs((left + n) - right) < 4) copy(left = left + n).some
           else none
         def landRight(n: Birds): Option[Pole] =
           if (math.abs(left - (right + n)) < 4) copy(right = right + n).some
           else none
         def banana: Option[Pole] = none
       }
defined class Pole

scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >>= {_.banana} >>= {_.landRight(1)}
res24: Option[Pole] = None
```

LYAHFGG:

> ところで、入力に関係なく既定のモナド値を返す関数だったら、自作せずとも `>>` 関数を使うという手があります。

以下が `>>` の `Option` での振る舞い:

```scala
scala> (none: Option[Int]) >> 3.some
res25: Option[Int] = None

scala> 3.some >> 4.some
res26: Option[Int] = Some(4)

scala> 3.some >> (none: Option[Int])
res27: Option[Int] = None
```

`banana` を `>> (none: Option[Pole])` に置き換えてみよう:

```scala
scala> Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
<console>:26: error: missing parameter type for expanded function ((x\$1) => x\$1.landLeft(1))
              Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)} >> (none: Option[Pole]) >>= {_.landRight(1)}
                                                   ^
```

突然型推論が崩れてしまった。問題の原因はおそらく演算子の優先順位にある。 [Programming in Scala](http://www.artima.com/pins1ed/basic-types-and-operations.html) 曰く:

> The one exception to the precedence rule, alluded to above, concerns assignment operators, which end in an equals character. If an operator ends in an equals character (`=`), and the operator is not one of the comparison operators `<=`, `>=`, `==`, or `!=`, then the precedence of the operator is the same as that of simple assignment (`=`). That is, it is lower than the precedence of any other operator.

注意: 上記の記述は不完全だ。代入演算子ルールのもう1つの例外は演算子が `===` のように (`=`) から始まる場合だ。

`>>=` (bind) が等号で終わるため、優先順位は最下位に落とされ、`({_.landLeft(1)} >> (none: Option[Pole]))` が先に評価される。いくつかの気が進まない回避方法がある。まず、普通のメソッド呼び出しのようにドットと括弧の記法を使うことができる:

```scala
scala> Monad[Option].point(Pole(0, 0)).>>=({_.landLeft(1)}).>>(none: Option[Pole]).>>=({_.landRight(1)})
res9: Option[Pole] = None
```

もしくは優先順位の問題に気付いたなら、適切な場所に括弧を置くことができる:

```scala
scala> (Monad[Option].point(Pole(0, 0)) >>= {_.landLeft(1)}) >> (none: Option[Pole]) >>= {_.landRight(1)}
res10: Option[Pole] = None
```

両方とも正しい答が得られた。ちなみに、`>>=` を `flatMap` に変えても `>>` の方がまだ優先順位が高いため問題は解決しない。

### for 構文

LYAHFGG:

> Haskell にとってモナドはとても便利なので、モナド専用構文まで用意されています。その名は `do` 記法。

まずは入れ子のラムダ式を書いてみよう:

```scala
scala> 3.some >>= { x => "!".some >>= { y => (x.shows + y).some } }
res14: Option[String] = Some(3!)
```

`>>=` が使われたことで計算のどの部分も失敗することができる:

```scala
scala> 3.some >>= { x => (none: Option[String]) >>= { y => (x.shows + y).some } }
res17: Option[String] = None

scala> (none: Option[Int]) >>= { x => "!".some >>= { y => (x.shows + y).some } }
res16: Option[String] = None

scala> 3.some >>= { x => "!".some >>= { y => (none: Option[String]) } }
res18: Option[String] = None
```

Haskell の `do` 記法のかわりに、Scala には `for` 構文があり、これらは同じものだ:

```scala
scala> for {
         x <- 3.some
         y <- "!".some
       } yield (x.shows + y)
res19: Option[String] = Some(3!)
```

LYAHFGG:

> `do` 式は、`let` 行を除いてすべてモナド値で構成されます。

これも Scala の `for` 構文に当てはまると思う。

### 帰ってきたピエール

LYAHFGG:

> ピエールの綱渡りの動作も、もちろん `do` 記法で書けます。

```scala
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res20: Option[Pole] = Some(Pole(3,2))
```

`yield` は `Option[Pole]` じゃなくて `Pole` を受け取るため、`third` も抽出する必要があった。

LYAHFGG:

> ピエールにバナナの皮を踏ませたい場合、`do` 記法ではこう書きます。

```scala
scala> def routine: Option[Pole] =
         for {
           start <- Monad[Option].point(Pole(0, 0))
           first <- start.landLeft(2)
           _ <- (none: Option[Pole])
           second <- first.landRight(2)
           third <- second.landLeft(1)
         } yield third
routine: Option[Pole]

scala> routine
res23: Option[Pole] = None
```

### パターンマッチングと失敗

LYAHFGG:

> `do` 記法でモナド値を変数名に束縛するときには、`let` 式や関数の引数のときと同様、パターンマッチが使えます。

```scala
scala> def justH: Option[Char] =
         for {
           (x :: xs) <- "hello".toList.some
         } yield x
justH: Option[Char]

scala> justH
res25: Option[Char] = Some(h)
```

> `do` 式の中でパターンマッチが失敗した場合、`Monad` 型クラスの一員である `fail` 関数が使われるので、異常終了という形ではなく、そのモナドの文脈に合った形で失敗を処理できます。

```scala
scala> def wopwop: Option[Char] =
         for {
           (x :: xs) <- "".toList.some
         } yield x
wopwop: Option[Char]

scala> wopwop
res28: Option[Char] = None
```

失敗したパターンマッチングは `None` を返している。これは `for` 構文の興味深い一面で、今まで考えたことがなかったが、言われるとなるほどと思う。
