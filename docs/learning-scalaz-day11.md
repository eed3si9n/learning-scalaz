  [day10]: http://eed3si9n.com/learning-scalaz-day10

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-6278bw.jpg">
<div class="credit">Darren Hester for openphoto.net</div>
</div>

[Yesterday][day10] we looked at Reader monad as a way of abstracting configuration, and introduced monad transformers.

Today, let's look at lenses. It's a hot topic many people are talking, and looks like it has clear use case.

### Go turtle go

[Seth Tisue (@SethTisue)](https://twitter.com/SethTisue) gave a [talk on shapeless lenses](http://scalathon.org/2012/presentations/lenses.pdf) at Scalathon this year. I missed the talk, but I am going to borrow his example.

<scala>
scala> case class Point(x: Double, y: Double)
defined class Point

scala> case class Color(r: Byte, g: Byte, b: Byte)
defined class Color

scala> case class Turtle(
         position: Point,
         heading: Double,
         color: Color)

scala> Turtle(Point(2.0, 3.0), 0.0,
         Color(255.toByte, 255.toByte, 255.toByte))
res0: Turtle = Turtle(Point(2.0,3.0),0.0,Color(-1,-1,-1))
</scala>

Now without breaking the immutability, we want to move the turtle forward.

<scala>
scala> case class Turtle(position: Point, heading: Double, color: Color) {
         def forward(dist: Double): Turtle =
           copy(position =
             position.copy(
               x = position.x + dist * math.cos(heading),
               y = position.y + dist * math.sin(heading)
           ))
       }
defined class Turtle

scala> Turtle(Point(2.0, 3.0), 0.0,
         Color(255.toByte, 255.toByte, 255.toByte))
res10: Turtle = Turtle(Point(2.0,3.0),0.0,Color(-1,-1,-1))

scala> res10.forward(10)
res11: Turtle = Turtle(Point(12.0,3.0),0.0,Color(-1,-1,-1))
</scala>

To update the child data structure, we need to nest `copy` call. To quote from Seth's example again:

<scala>
// imperative
a.b.c.d.e += 1

// functional
a.copy(
  b = a.b.copy(
    c = a.b.c.copy(
      d = a.b.c.d.copy(
        e = a.b.c.d.e + 1
))))
</scala>

The idea is to get rid of unnecessary `copy` calls.

### Lens

Let's look at `Lens` in Scalaz7:

<scala>
  type Lens[A, B] = LensT[Id, A, B]

  object Lens extends LensTFunctions with LensTInstances {
    def apply[A, B](r: A => Store[B, A]): Lens[A, B] =
      lens(r)
  }
</scala>

`Lens` is a type alias for `LensT[Id, A, B]` like many other typeclasses.

### LensT

[`LensT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Lens.scala) looks like this:

<scala>
import StoreT._
import Id._

sealed trait LensT[F[+_], A, B] {
  def run(a: A): F[Store[B, A]]
  def apply(a: A): F[Store[B, A]] = run(a)
  ...
}

object LensT extends LensTFunctions with LensTInstances {
  def apply[F[+_], A, B](r: A => F[Store[B, A]]): LensT[F, A, B] =
    lensT(r)
}

trait LensTFunctions {
  import StoreT._

  def lensT[F[+_], A, B](r: A => F[Store[B, A]]): LensT[F, A, B] = new LensT[F, A, B] {
    def run(a: A): F[Store[B, A]] = r(a)
  }

  def lensgT[F[+_], A, B](set: A => F[B => A], get: A => F[B])(implicit M: Bind[F]): LensT[F, A, B] =
    lensT(a => M(set(a), get(a))(Store(_, _)))
  def lensg[A, B](set: A => B => A, get: A => B): Lens[A, B] =
    lensgT[Id, A, B](set, get)
  def lensu[A, B](set: (A, B) => A, get: A => B): Lens[A, B] =
    lensg(set.curried, get)
  ...
}
</scala>

### Store

What's a `Store`?

<scala>
  type Store[A, B] = StoreT[Id, A, B]
  // flipped
  type |-->[A, B] = Store[B, A]
  object Store {
    def apply[A, B](f: A => B, a: A): Store[A, B] = StoreT.store(a)(f)
  }
</scala>

It looks like a wrapper for setter `A => B => A` and getter `A => B`.

### Using Lens

Let's define `turtlePosition` and `pointX`:

<scala>
scala> val turtlePosition = Lens.lensu[Turtle, Point] (
         (a, value) => a.copy(position = value),
         _.position
       )
turtlePosition: scalaz.Lens[Turtle,Point] = scalaz.LensTFunctions$$anon$5@421dc8c8

scala> val pointX = Lens.lensu[Point, Double] (
         (a, value) => a.copy(x = value),
         _.x
       )
pointX: scalaz.Lens[Point,Double] = scalaz.LensTFunctions$$anon$5@30d31cf9
</scala>

Next we can take advantage of a bunch of operators introduced in `Lens`. Similar to monadic function composition we saw in `Kleisli`, `LensT` implements `compose` (symbolic alias `<=<`), and `andThen` (symbolic alias `>=>`). I personally think `>=>` looks cool, so let's use that to define `turtleX`:

<scala>
scala> val turtleX = turtlePosition >=> pointX
turtleX: scalaz.LensT[scalaz.Id.Id,Turtle,Double] = scalaz.LensTFunctions$$anon$5@11b35365
</scala>

The type makes sense since it's going form `Turtle` to `Double`. Using `get` method we can get the value:

<scala>
scala> val t0 = Turtle(Point(2.0, 3.0), 0.0,
                  Color(255.toByte, 255.toByte, 255.toByte))
t0: Turtle = Turtle(Point(2.0,3.0),0.0,Color(-1,-1,-1))

scala> turtleX.get(t0)
res16: scalaz.Id.Id[Double] = 2.0
</scala>

Success! Setting a new value using `set` method should return a new `Turtle`:

<scala>
scala> turtleX.set(t0, 5.0)
res17: scalaz.Id.Id[Turtle] = Turtle(Point(5.0,3.0),0.0,Color(-1,-1,-1))
</scala>

This works too. What if I want to `get` the value, apply it to some function, and `set` using the result? `mod` does exactly that:

<scala>
scala> turtleX.mod(_ + 1.0, t0)
res19: scalaz.Id.Id[Turtle] = Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1))
</scala>

There's a symbolic variation to `mod` that's curried called `=>=`. This generates `Turtle => Turtle` function:

<scala>
scala> val incX = turtleX =>= {_ + 1.0}
incX: Turtle => scalaz.Id.Id[Turtle] = <function1>

scala> incX(t0)
res26: scalaz.Id.Id[Turtle] = Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1))
</scala>

We are now describing change of internal values upfront and passing in the actual value at the end. Does this remind you of something?

### Lens as a State monad

That sounds like a state transition to me. In fact `Lens` and `State` I think are good match since they are sort of emulating imperative programming on top of immutable data structure. Here's another way of writing `incX`:

<scala>
scala> val incX = for {
         x <- turtleX %= {_ + 1.0}
       } yield x
incX: scalaz.StateT[scalaz.Id.Id,Turtle,Double] = scalaz.StateT$$anon$7@38e61ffa

scala> incX(t0)
res28: (Turtle, Double) = (Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1)),3.0)
</scala>

`%=` method takes a function `Double => Double` and returns a `State` monad that expresses the change.

Let's make `turtleHeading` and `turtleY` too:

<scala>
scala> val turtleHeading = Lens.lensu[Turtle, Double] (
         (a, value) => a.copy(heading = value),
         _.heading
       )
turtleHeading: scalaz.Lens[Turtle,Double] = scalaz.LensTFunctions$$anon$5@44fdec57

scala> val pointY = Lens.lensu[Point, Double] (
         (a, value) => a.copy(y = value),
         _.y
       )
pointY: scalaz.Lens[Point,Double] = scalaz.LensTFunctions$$anon$5@ddede8c

scala> val turtleY = turtlePosition >=> pointY
</scala>

This is no fun because it feels boilerplatey. But, we can now move turtle forward! Instead of general `%=`, Scalaz even provides sugars like `+=` for `Numeric` lenses. Here's what I mean:

<scala>
scala> def forward(dist: Double) = for {
         heading <- turtleHeading
         x <- turtleX += dist * math.cos(heading)
         y <- turtleY += dist * math.sin(heading)
       } yield (x, y)
forward: (dist: Double)scalaz.StateT[scalaz.Id.Id,Turtle,(Double, Double)]

scala> forward(10.0)(t0)
res31: (Turtle, (Double, Double)) = (Turtle(Point(12.0,3.0),0.0,Color(-1,-1,-1)),(12.0,3.0))

scala> forward(10.0) exec (t0)
res32: scalaz.Id.Id[Turtle] = Turtle(Point(12.0,3.0),0.0,Color(-1,-1,-1))
</scala>

Now we have implemented `forward` function without using a single `copy(position = ...)`. It's nice but we still needed some prep work to get here, so there is some tradeoff. `Lens` defines a lot more methods, but the above should be a good starter. Let's see them all again:

<scala>
sealed trait LensT[F[+_], A, B] {
  def get(a: A)(implicit F: Functor[F]): F[B] =
    F.map(run(a))(_.pos)
  def set(a: A, b: B)(implicit F: Functor[F]): F[A] =
    F.map(run(a))(_.put(b))
  /** Modify the value viewed through the lens */
  def mod(f: B => B, a: A)(implicit F: Functor[F]): F[A] = ...
  def =>=(f: B => B)(implicit F: Functor[F]): A => F[A] =
    mod(f, _)
  /** Modify the portion of the state viewed through the lens and return its new value. */
  def %=(f: B => B)(implicit F: Functor[F]): StateT[F, A, B] =
    mods(f)
  /** Lenses can be composed */
  def compose[C](that: LensT[F, C, A])(implicit F: Bind[F]): LensT[F, C, B] = ...
  /** alias for `compose` */
  def <=<[C](that: LensT[F, C, A])(implicit F: Bind[F]): LensT[F, C, B] = compose(that)
  def andThen[C](that: LensT[F, B, C])(implicit F: Bind[F]): LensT[F, A, C] =
    that compose this
  /** alias for `andThen` */
  def >=>[C](that: LensT[F, B, C])(implicit F: Bind[F]): LensT[F, A, C] = andThen(that)
}
</scala>

### Lens laws

Seth says:

> lens laws are common sense
>
> (0. if I get twice, I get the same answer)
> 1. if I get, then set it back, nothing changes.
> 2. if I set, then get, I get what I set.
> 3. if I set twice then get, I get the second thing I set.

He's right. These are common sense. Here how Scalaz expresses it in code:

<scala>
  trait LensLaw {
    def identity(a: A)(implicit A: Equal[A], ev: F[Store[B, A]] =:= Id[Store[B, A]]): Boolean = {
      val c = run(a)
      A.equal(c.put(c.pos), a)
    }
    def retention(a: A, b: B)(implicit B: Equal[B], ev: F[Store[B, A]] =:= Id[Store[B, A]]): Boolean =
      B.equal(run(run(a) put b).pos, b)
    def doubleSet(a: A, b1: B, b2: B)(implicit A: Equal[A], ev: F[Store[B, A]] =:= Id[Store[B, A]]) = {
      val r = run(a)
      A.equal(run(r put b1) put b2, r put b2)
    }
  }
</scala>

By making arbitrary turtles we can check if our `turtleX` is ok. We'll skip it, but make sure you don't define weird lens that break the law.

### Links

There's an article by Jordan West titled [An Introduction to Lenses in Scalaz](http://blog.stackmob.com/2012/02/an-introduction-to-lenses-in-scalaz/), which I kind of skimmed and looks like Scalaz 6.

There's a video by Edward Kmett's [Lenses: A Functional Imperative](http://www.youtube.com/watch?v=efv0SQNde5Q) presented at the Boston Area Scala Enthusiasts (BASE).

Finally, there's a compiler plugin by Gerolf Seitz that generates lenses: [gseitz/Lensed](https://github.com/gseitz/Lensed). The project seems to be at experimental stage, but it does show the potential of macro or compiler generating lenses instead of hand-coding them.

We'll pick it up from here later.
