
### Making a safe RPN calculator

LYAHFGG:

> When we were solving the problem of implementing a RPN calculator, we noted that it worked fine as long as the input that it got made sense.

I did not cover that chapter, but the code is here so let's translate it into Scala:

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

Looks like it's working. The next step is to change the folding function to handle errors gracefully. Scalaz adds `parseInt` to `String` which returns `Validation[NumberFormatException, Int]`. We can call `toOption` on a validation to turn it into `Option[Int]` like the book:

```scala
scala> "1".parseInt.toOption
res31: Option[Int] = Some(1)

scala> "foo".parseInt.toOption
res32: Option[Int] = None
```

Here's the updated folding function:

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

Here's the updated `solveRPN`:

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
