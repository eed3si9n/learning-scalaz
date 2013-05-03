---
out: A-knights-quest.html
---

### 騎士の旅

LYAHFGG:

> ここで、非決定性計算を使って解くのにうってつけの問題をご紹介しましょう。チェス盤の上にナイトの駒が1つだけ乗っています。ナイトを3回動かして特定のマスまで移動させられるか、というのが問題です。

ペアに型エイリアスと付けるかわりにまた case class にしよう:

```scala
scala> case class KnightPos(c: Int, r: Int)
defined class KnightPos
```

以下がナイトの次に取りうる位置を全て計算する関数だ:

```scala
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
               KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
               KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
               KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
               ((1 |-> 8) contains c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
       }
defined class KnightPos

scala> KnightPos(6, 2).move
res50: List[KnightPos] = List(KnightPos(8,1), KnightPos(8,3), KnightPos(4,1), KnightPos(4,3), KnightPos(7,4), KnightPos(5,4))

scala> KnightPos(8, 1).move
res51: List[KnightPos] = List(KnightPos(6,2), KnightPos(7,3))
```

答は合ってるみたいだ。次に、3回のチェインを実装する:

```scala
scala> case class KnightPos(c: Int, r: Int) {
         def move: List[KnightPos] =
           for {
             KnightPos(c2, r2) <- List(KnightPos(c + 2, r - 1), KnightPos(c + 2, r + 1),
             KnightPos(c - 2, r - 1), KnightPos(c - 2, r + 1),
             KnightPos(c + 1, r - 2), KnightPos(c + 1, r + 2),
             KnightPos(c - 1, r - 2), KnightPos(c - 1, r + 2)) if (
             ((1 |-> 8) element c2) && ((1 |-> 8) contains r2))
           } yield KnightPos(c2, r2)
         def in3: List[KnightPos] =
           for {
             first <- move
             second <- first.move
             third <- second.move
           } yield third
         def canReachIn3(end: KnightPos): Boolean = in3 contains end
       }
defined class KnightPos

scala> KnightPos(6, 2) canReachIn3 KnightPos(6, 1)
res56: Boolean = true

scala> KnightPos(6, 2) canReachIn3 KnightPos(7, 3)
res57: Boolean = false
```
