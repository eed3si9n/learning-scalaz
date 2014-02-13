  [279]: https://github.com/scalaz/scalaz/pull/279
  [spire]: https://github.com/non/spire

## Examples of categories

Before we go abtract, we're going to look at some more concrete categories. This is actually a good thing, since we only saw one category yesterday.

### Sets

The category of sets and total functions are denoted by **Sets** written in bold.

### Sets<sub>fin</sub>

The category of all finite sets and total functions between them are called **Sets<sub>fin</sub>**. This is the category we have been looking at so far.

### Pos

Awodey:

> Another kind of example one often sees in mathematics is categories of *structured sets*, that is, sets with some further "structure" and functions that "preserve it," where these notions are determined in some independent way.

> A partially ordered set or *poset* is a set *A* equipped with a binary relation *a ≤<sub>A</sub> b* such that the following conditions hold for all *a, b, c ∈ A*:
> 
> - reflexivity: a ≤<sub>A</sub> a
> - transitivity: if a ≤<sub>A</sub> b and b ≤<sub>A</sub> c, then a ≤<sub>A</sub> c
> - antisymmetry: if a ≤<sub>A</sub> b and b ≤<sub>A</sub> a, then a = b
>
> An arrow from a poset *A* to a poset *B* is a function m: A => B that is *monotone*, in the sense that, for all a, a' ∈ A,
>
> - a ≤<sub>A</sub> a' implies m(a) ≤<sub>A</sub> m(a').

As long as the functions are *monotone*, the objects will continue to be in the category, so the "structure" is preserved. The category of posets and monotone functions is denoted as **Pos**. Awodey likes posets, so it's important we understand it.

### Cat

> **Definition 1.2.** A functor<br>
> F: **C** => **D**<br>
> between categories **C** and **D** is a mapping of objects to objects and arrows to arrows, in such a way that.
> 
> - F(f: A => B) = F(f): F(A) => F(B)
> - F(1<sub>A</sub>) = 1<sub>F(A)</sub>
> - F(g ∘ f) = F(g) ∘ F(f)
>
> That is, *F*, preserves domains and codomains, identity arrows, and composition.

Now we are talking. Functor is an arrow between two categories. Here's the external diagram:

![functor](files/day20-a-functor.png)

The fact that the positions of *F(A)*, *F(B)*, and *F(C)* are distorted is intentional. That's what *F* is doing, slightly distorting the picture, but still preserving the composition.

This category of categories and functors is denoted as **Cat**.

### Monoid

> A *monoid* (sometimes called a semigroup with unit) is a set M equipped with a binary operation *·: M × M => M* and a distinguished "unit" element u ∈ M such that for all x, y, z ∈ M,
>
> - x · (y · z) = (x · y) · z
> - u · x = x = x · u
>
> Equivalently, a monoid is a category with just one object. The arrows of the category are the elements of the monoid. In particular, the identity arrow is the unit element *u*. Composition of arrows is the binary operation m · n for the monoid.

The concept of monoid translates well into Scalaz. You can check out [About those Monoids](Monoid.html) from day 3.

```scala
trait Monoid[A] extends Semigroup[A] { self =>
  ////
  /** The identity element for `append`. */
  def zero: A
  
  ...
}

trait Semigroup[A]  { self =>
  def append(a1: A, a2: => A): A
  ...
}
```

Here is addition of `Int` and `0`:

```scala
scala> 10 |+| Monoid[Int].zero
res26: Int = 10
```

and multiplication of `Int` and `1`:

```scala
scala>  Tags.Multiplication(10) |+| Monoid[Int @@ Tags.Multiplication].zero
res27: scalaz.@@[Int,scalaz.Tags.Multiplication] = 10
```

The idea that these monoids are categories with one object and that elements are arrows used to sound so alien to me, but now it's understandable since we were exposed to singleton.

### Mon

The category of monoids and functions that preserve the monoid structure is denoted by **Mon**. These arrows that preserve structure are called *homomorphism*.

> In detail, a homomorphism from a monoid M to a monoid N is a function h: M => N such that for all m, n ∈ M,
>
> - h(m ·<sub>M</sub> n) = h(m) ·<sub>N</sub> h(n)
> - h(u<sub>M</sub>) = u<sub>N</sub>

Since a monoid is a category, a monoid homomorphism is a special case of functors.

### Groups

> **Definition 1.4** A *group* G is a monoid with an inverse g<sup>-1</sup> for every element g. Thus, G is a category with one object, in which every arrow is an isomorphism.

The category of groups and group homomorphisms is denoted as **Groups**.

Scalaz used to have groups, but it was removed about an year ago in [#279][279], which says it's removing duplication with [Spire][spire].
