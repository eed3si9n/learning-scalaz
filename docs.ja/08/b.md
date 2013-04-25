---
out: Making-a-safe-RPN-calculator.html
---

### 安全な RPN 電卓を作ろう

LYAHFGG:

> 第10章で逆ポーランド記法 (RPN) の電卓を実装せよという問題を解いたときには、この電卓は文法的に正しい入力が与えられる限り正しく動くよ、という注意書きがありました。

最初に RPN 電卓を作った章は飛ばしたけど、コードはここにあるから Scala に訳してみる:

```scala
scala> def foldingFunction(list: List[Double], next: String): List[Double] = (list, next) match {
         case (x :: y :: ys, "*") => (y * x) :: ys
         case (x :: y :: ys, "+") => (y + x) :: ys
         case (x :: y :: ys, "-") => (y - x) :: ys
         case (xs, numString) => numString.toInt :: xs
       }
foldingFunction: (list: List[Double], next: String)List[Double]

scala> def solveRPN(s: String): Double =
         (s.split(' ').toList.foldLeft(Nil: List[Double]) {foldingFunction}).head
solveRPN: (s: String)Double

scala> solveRPN("10 4 3 + 2 * -")
res27: Double = -4.0
```

動作しているみたいだ。次に畳み込み関数がエラーを処理できるようにする。Scalaz は `String` に `Validation[NumberFormatException, Int]` を返す `parseInt` を導入する。これに対して `toOption` を呼べば本の通り `Option[Int]` が得られる:

```scala
scala> "1".parseInt.toOption
res31: Option[Int] = Some(1)

scala> "foo".parseInt.toOption
res32: Option[Int] = None
```

以下が更新された畳込み関数:

```scala
scala> def foldingFunction(list: List[Double], next: String): Option[List[Double]] = (list, next) match {
         case (x :: y :: ys, "*") => ((y * x) :: ys).point[Option]
         case (x :: y :: ys, "+") => ((y + x) :: ys).point[Option]
         case (x :: y :: ys, "-") => ((y - x) :: ys).point[Option]
         case (xs, numString) => numString.parseInt.toOption map {_ :: xs}
       }
foldingFunction: (list: List[Double], next: String)Option[List[Double]]

scala> foldingFunction(List(3, 2), "*")
res33: Option[List[Double]] = Some(List(6.0))

scala> foldingFunction(Nil, "*")
res34: Option[List[Double]] = None

scala> foldingFunction(Nil, "wawa")
res35: Option[List[Double]] = None
```

以下が更新された `solveRPN`:

```scala
scala> def solveRPN(s: String): Option[Double] = for {
         List(x) <- s.split(' ').toList.foldLeftM(Nil: List[Double]) {foldingFunction}
       } yield x
solveRPN: (s: String)Option[Double]

scala> solveRPN("1 2 * 4 +")
res36: Option[Double] = Some(6.0)

scala> solveRPN("1 2 * 4")
res37: Option[Double] = None

scala> solveRPN("1 8 garbage")
res38: Option[Double] = None
```
