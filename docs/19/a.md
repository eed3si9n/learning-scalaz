  [CM]: http://www.cambridge.org/us/academic/subjects/mathematics/logic-categories-and-sets/conceptual-mathematics-first-introduction-categories-2nd-edition

### Category theory

The most accessible category theory book I've come across is Lawvere and Schanuel's [Conceptual Mathematics: A First Introduction to Categories][CM] 2nd ed. The book mixes Articles, which is written like a normal textbook; and Sessions, which is kind of written like a recitation class.

Even in the Article section, CM uses many pages to go over the basic concept compared to other books, which is good for self learners.

### Sets, arrows, composition

CM:

> Before giving a precise definition of 'category', we should become familiar with one example, the **category of finite sets and maps**.
> An *object* in this category is a finite set or collection.
> ...
> You are probably familiar with some notations for finite sets:

```
{ John, Mary, Sam }
```

There are two ways that I can think of to express this in Scala. One is by using a value `a: Set[Person]`:

```scala
scala> :paste

sealed trait Person {}
case object John extends Person {}
case object Mary extends Person {}
case object Sam extends Person {}

val a: Set[Person] = Set[Person](John, Mary, Sam)

// Exiting paste mode, now interpreting.
```

Another way of looking at it, is that `Person` as the type is a finite set already without `Set`. **Note**: In CM, Lawvere and Schanuel use the term "map", but I'm going to change to *arrow* like Mac Lane and other books.

> A *arrow* *f* in this cateogry consists of three things:
>
> 1. a set A, called the *domain* of the arrow,
> 2. a set B, called the *codomain* of the arrow,
> 3. a rule assigning to each element *a* in the domain, an element *b* in the codomain. This *b* is denoted by *f ∘ a* (or sometimes '*f(a)*'), read '*f* of *a*'.
>
> (Other words for arrow are 'function', 'transformation', 'operator', 'map', and 'morphism'.)

Let's try implementing the favorite breakfast arrow.

```scala
scala> :paste

sealed trait Breakfast {}
case object Eggs extends Breakfast {}
case object Oatmeal extends Breakfast {}
case object Toast extends Breakfast {}
case object Coffee extends Breakfast {}

val favoriteBreakfast: Person => Breakfast = {
  case John => Eggs
  case Mary => Coffee
  case Sam  => Coffee
}

// Exiting paste mode, now interpreting.

favoriteBreakfast: Person => Breakfast = <function1>
```

Note here that an "object" in this category is `Set[Person]` or `Person`, but the "arrow" `favoriteBreakfast` accepts a value whose type is `Person`. Here's the *internal diagram* of the arrow. <br>
![favorite breakfast](files/day19-a-favorite-breakfast.png)

> The important thing is: For each dot in the domain, we have exactly one arrow leaving, and the arrow arrives at some dot in the codomain.

I get that a map can be more general than `Function1[A, B]` but it's ok for this category. Here's the implementation of `favoritePerson`:

```scala
scala> val favoritePerson: Person => Person = {
         case John => Mary
         case Mary => John
         case Sam  => Mary
       }
favoritePerson: Person => Person = <function1>
```

> An arrow in which the domain and codomain are the same object is called an *endomorphism*.

![favorite person](files/day19-c-favorite-person.png)

> An arrow, in which the domain and codomain are the same set *A*, and for each of *a* in *A*, *f(a)* = *a*, is called an *identity arrow*.

The "identity arrow on A" is denoted as 1<sub>A</sub>. <br> ![identity arrow](files/day19-b-identity.png)

Again, identity is an arrow, so it works on an element in the set, not the set itself. So in this case we can just use `scala.Predef.identity`.

```scala
scala> identity(John)
res0: John.type = John
```

Here are the *external diagrams* corresponding to the three internal diagrams from the above. <br> ![external diagrams](files/day19-d-external-diagrams.png)

This reiterates the point that _in the category of finite sets_, the "objects" translate to types like `Person` and `Breakfast`, and arrows translate to functions like `Person => Person`. The external diagram looks a lot like the type-level signatures like `Person => Person`.

> The final basic ingredient, which is what lends all the dynamics to the notion  of category is *composition of arrows*, by which two arrows are combined to obtain a third arrow.

We can do this in scala using `scala.Function1`'s `andThen` or `compose`.

```scala
scala> val favoritePersonsBreakfast = favoriteBreakfast compose favoritePerson
favoritePersonsBreakfast: Person => Breakfast = <function1>
```

Here's the internal diagram: <br> ![composition of maps](files/day19-e-composition-of-maps.png)

and the external diagram: <br> ![external diagram: composition of maps](files/day19-f-composition-external-diagram.png)

After composition the external diagram becomes as follows: <br> ![external diagram: f of g](files/day19-g-external-diagram-f-of-g.png)

> '*f ∘ g*' is read '*f following g*', or sometimes '*f of g*'.

*Data* for a category consists of the four ingredients:

- objects: *A*, *B*, *C*, ...
- arrows: *f: A => B*
- identity arrows: *1<sub>A</sub>: A => A*
- composition of arrows

These data must satisfy the following rules:

The identity laws:

- If *1<sub>A</sub>: A => A, g: A => B*, then *g ∘ 1<sub>A</sub> = g*
- If *f: A => B, 1<sub>B</sub>: B => B*, then *1<sub>A</sub> ∘ f = f*

The associative law:

- If *f: A => B, g: B => C, h: C => D*, then *h ∘ (g ∘ f) = (h ∘ g) ∘ f*
