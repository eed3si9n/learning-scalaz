---
out: A-knights-quest.html
---

### A knight's quest

LYAHFGG:

> Here's a problem that really lends itself to being solved with non-determinism. Say you have a chess board and only one knight piece on it. We want to find out if the knight can reach a certain position in three moves.

Instead of type aliasing a pair, let's make this into a case class again:

```scala
scala> case class KnightPos(c: Int, r: Int)
defined class KnightPos
```

Heres the function to calculate all of his next next positions:

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

The answers look good. Now we implement chaining this three times:

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
