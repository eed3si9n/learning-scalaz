---
out: Lawless-typeclasses.html
---

Lawless typeclasses
-------------------

  [pc]: https://groups.google.com/d/msg/scalaz/7OE_Nsreqq0/vUs7-tyf1nsJ
  [why]: http://www.haskell.org/haskellwiki/Why_not_Pointed%3F

Scalaz 7.0 contains several typeclasses that are now deemed lawless by Scalaz project: `Length`, `Index`, and `Each`. Some discussions can be found in [#278 What to do about lawless classes?](https://github.com/scalaz/scalaz/issues/278) and [(presumably) Bug in IndexedSeq Index typeclass](https://groups.google.com/d/msg/scalaz/aJx69eWMK6M/gAtne2v6RJYJ). __The three will be deprecated in 7.1, and removed in 7.2__.

### Length

There's a typeclass that expresses length. Here's [the typeclass contract of `Length`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Length.scala):

```scala
trait Length[F[_]]  { self =>
  def length[A](fa: F[A]): Int
}
```

This introduces `length` method. In Scala standard library it's introduced by `SeqLike`, so it could become useful if there were data structure that does not extend `SeqLike` that has length.

### Index

For random access into a container, there's [`Index`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Index.scala):

```scala
trait Index[F[_]]  { self =>
  def index[A](fa: F[A], i: Int): Option[A]
}
```

This introduces `index` and `indexOr` methods:

```scala
trait IndexOps[F[_],A] extends Ops[F[A]] {
  final def index(n: Int): Option[A] = F.index(self, n)
  final def indexOr(default: => A, n: Int): A = F.indexOr(self, default, n)
}
```

This is similar to `List(n)` except it returns `None` for an out-of-range index:

```scala
scala> List(1, 2, 3)(3)
java.lang.IndexOutOfBoundsException: 3
        ...

scala> List(1, 2, 3) index 3
res62: Option[Int] = None
```

### Each

For running side effects along a data structure, there's [`Each`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Each.scala):

```scala
trait Each[F[_]]  { self =>
  def each[A](fa: F[A])(f: A => Unit)
}
```

This introduces `foreach` method:

```scala
sealed abstract class EachOps[F[_],A] extends Ops[F[A]] {
  final def foreach(f: A => Unit): Unit = F.each(self)(f)
}
```

### Foldable or rolling your own?

Some of the functionality above can be emulated using `Foldable`, but as [@nuttycom](https://github.com/scalaz/scalaz/issues/278#issuecomment-16748242) suggested, that would force *O(n)* time even when the underlying data structure implements constant time for `length` and `index`. At that point, we'd be better off rolling our own `Length` if it's actually useful to abstract over `length`.

If inconsistent implementations of these typeclasses were somehow compromising the typesafety I'd understand removing them from the library, but `Length` and `Index` sound like a legitimate abstraction of randomly accessible containers like `Vector`.

### Pointed and Copointed

There actually was another set of typeclasses that was axed earlier: `Pointed` and `Copointed`. There were more interesting arguments on them that can be found in [Pointed/Copointed][pc] and [Why not Pointed?][why]:

> `Pointed` has no useful laws and almost all applications people point to for it are actually abuses of ad hoc relationships it happens to have for the instances it does offer.

This actually is an interesting line of argument that I can understand. In other words, if any container can qualify as `Pointed`, the code using it either is not very useful or it's likely making specific assumption about the instance.

### Tweets to the editor

<blockquote class="twitter-tweet" data-conversation="none" lang="en"><p><a href="https://twitter.com/eed3si9n">@eed3si9n</a> &quot;axiomatic&quot; would be better.</p>&mdash; Miles Sabin (@milessabin) <a href="https://twitter.com/milessabin/statuses/417228497040732160">December 29, 2013</a></blockquote>

<blockquote class="twitter-tweet" data-conversation="none" lang="en"><p><a href="https://twitter.com/eed3si9n">@eed3si9n</a> Foldable too (unless it also has a Functor but then nothing past parametricity): <a href="https://t.co/Lp0YkUTRD9">https://t.co/Lp0YkUTRD9</a> - but Reducer has laws!</p>&mdash; Brian McKenna (@puffnfresh) <a href="https://twitter.com/puffnfresh/statuses/417332352260374528">December 29, 2013</a></blockquote>
<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>
