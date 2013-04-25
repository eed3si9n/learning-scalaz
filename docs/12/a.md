<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-19035bw.jpg">
<div class="credit">reynaldo f. tamayo for openphoto.net</div>
</div>

Today, let's skim some papers. First is [Origami programming](http://www.cs.ox.ac.uk/jeremy.gibbons/publications/origami.pdf) by Jeremy Gibbons. 

### Origami programming

Gibbons says:

> In this chapter we will look at folds and unfolds as abstractions. In a precise technical sense, folds and unfolds are the natural patterns of computation over recursive datatypes; unfolds generate data structures and folds consume them.

We've covered `foldLeft` in [day 4](http://eed3si9n.com/learning-scalaz-day4) using `Foldable`, but what's unfold?

> The dual of folding is unfolding. The Haskell standard List library deﬁnes the function `unfoldr` for generating lists.

Hoogle lists the following sample:

```haskell
Prelude Data.List> unfoldr (\b -> if b == 0 then Nothing else Just (b, b-1)) 10
[10,9,8,7,6,5,4,3,2,1]
```

### DList

There's a data structure called `DList` that supports `DList.unfoldr`. `DList`, or difference list, is a data structure that supports constant-time appending.

```scala
scala> DList.unfoldr(10, { (x: Int) => if (x == 0) none else (x, x - 1).some })
res50: scalaz.DList[Int] = scalaz.DListFunctions$$anon$3@70627153

scala> res50.toList
res51: List[Int] = List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
```

### Folds for Streams

In Scalaz `unfold` defined in `StreamFunctions` is introduced by `import Scalaz._`:

```scala
scala> unfold(10) { (x) => if (x == 0) none else (x, x - 1).some }
res36: Stream[Int] = Stream(10, ?)

scala> res36.toList
res37: List[Int] = List(10, 9, 8, 7, 6, 5, 4, 3, 2, 1)
```

Let's try implementing the selection sort example from the paper:

```scala
scala> def minimumS[A: Order](stream: Stream[A]) = stream match {
         case x #:: xs => xs.foldLeft(x) {_ min _}
       }
minimumS: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])A

scala> def deleteS[A: Equal](y: A, stream: Stream[A]): Stream[A] = (y, stream) match {
         case (_, Stream()) => Stream()
         case (y, x #:: xs) =>
           if (y === x) xs
           else x #:: deleteS(y, xs) 
       }
deleteS: [A](y: A, stream: Stream[A])(implicit evidence$1: scalaz.Equal[A])Stream[A]

scala> def delmin[A: Order](stream: Stream[A]): Option[(A, Stream[A])] = stream match {
         case Stream() => none
         case xs =>
           val y = minimumS(xs)
           (y, deleteS(y, xs)).some
       }
delmin: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])Option[(A, Stream[A])]

scala> def ssort[A: Order](stream: Stream[A]): Stream[A] = unfold(stream){delmin[A]}
ssort: [A](stream: Stream[A])(implicit evidence$1: scalaz.Order[A])Stream[A]

scala> ssort(Stream(1, 3, 4, 2)).toList
res55: List[Int] = List(1, 2, 3, 4)
```

I guess this is considered origami programming because are using `foldLeft` and `unfold`? This paper was written in 2003 as a chapter in [The Fun of Programming](http://www.cs.ox.ac.uk/publications/books/fop/), but I am not sure if origami programming caught on.
