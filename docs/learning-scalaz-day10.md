  [day9]: http://eed3si9n.com/learning-scalaz-day9

On [day 9][day9] we looked at how to update immutable data structure using `TreeLoc` for `Tree`s and `Zipper` for `Stream`s. We also picked up a few typeclasses like `Id`, `Index` and `Length`. Now that we are done with Learn You a Haskell for Great Good, we need to find our own topic.

One concept that I see many times in Scalaz 7 is the monad transformer, so let's find what that's all about. Luckily there's another good Haskell book that I've read that's also available online.

### Monad transformers

[Real World Haskell](http://book.realworldhaskell.org/read/monad-transformers.html) says:

> It would be ideal if we could somehow take the standard `State` monad and add failure handling to it, without resorting to the wholesale construction of custom monads by hand. The standard monads in the `mtl` library don't allow us to combine them. Instead, the library provides a set of *monad transformers* to achieve the same result.
>
> A monad transformer is similar to a regular monad, but it's not a standalone entity: instead, it modifies the behaviour of an underlying monad. 

### Reader, yet again

Let's translate the `Reader` monad example into Scala:

<scala>
scala> def myName(step: String): Reader[String, String] = Reader {step + ", I am " + _}
myName: (step: String)scalaz.Reader[String,String]

scala> def localExample: Reader[String, (String, String, String)] = for {
         a <- myName("First")
         b <- myName("Second") >=> Reader { _ + "dy"}
         c <- myName("Third")  
       } yield (a, b, c)
localExample: scalaz.Reader[String,(String, String, String)]

scala> localExample("Fred")
res0: (String, String, String) = (First, I am Fred,Second, I am Freddy,Third, I am Fred)
</scala>

The point of `Reader` monad is to pass in the configuration information once and everyone uses it without explicitly passing it around. See [Configuration Without the Bugs and Gymnastics](http://vimeo.com/20674558) by [Tony Morris (@dibblego)](https://twitter.com/dibblego).

### ReaderT

Here's an example of stacking `ReaderT`, monad transformer version of `Reader` on `Option` monad.

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

type ReaderTOption[A, B] = ReaderT[Option, A, B]
object ReaderTOption extends KleisliFunctions with KleisliInstances {
  def apply[A, B](f: A => Option[B]): ReaderTOption[A, B] = kleisli(f)
}

// Exiting paste mode, now interpreting.
</scala>

Now using `ReaderTOption` object, we can create a `ReaderTOption`:

<scala>
scala> def configure(key: String) = ReaderTOption[Map[String, String], String] {_.get(key)} 
configure: (key: String)ReaderTOption[Map[String,String],String]
</scala>

On day 2 we mentioned about considering `Function1` as an infinite map. Here we are doing sort of the opposite by using `Map[String, String]` as a reader.

<scala>
scala> def setupConnection = for {
         host <- configure("host")
         user <- configure("user")
         password <- configure("password")
       } yield (host, user, password)
setupConnection: scalaz.Kleisli[Option,Map[String,String],(String, String, String)]

scala> val goodConfig = Map(
         "host" -> "eed3si9n.com",
         "user" -> "sa",
         "password" -> "****"
       )
goodConfig: scala.collection.immutable.Map[String,String] = Map(host -> eed3si9n.com, user -> sa, password -> ****)

scala> setupConnection(goodConfig)
res2: Option[(String, String, String)] = Some((eed3si9n.com,sa,****))

scala> val badConfig = Map(
         "host" -> "example.com",
         "user" -> "sa"
       )
badConfig: scala.collection.immutable.Map[String,String] = Map(host -> example.com, user -> sa)

scala> setupConnection(badConfig)
res3: Option[(String, String, String)] = None
</scala>

As you can see the above `ReaderTOption` monad combines `Reader`'s ability to read from some configuration once, and `Option`'s ability to express failure.

### Stacking multiple monad transformers

RWH:

> When we stack a monad transformer on a normal monad, the result is another monad. This suggests the possibility that we can again stack a monad transformer on top of our combined monad, to give a new monad, and in fact this is a common thing to do.

We can stack `StateT` to represent state transfer on top of `ReaderTOption`.

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

type StateTReaderTOption[C, S, A] = StateT[({type l[+X] = ReaderTOption[C, X]})#l, S, A]

object StateTReaderTOption extends StateTFunctions with StateTInstances {
  def apply[C, S, A](f: S => (S, A)) = new StateT[({type l[+X] = ReaderTOption[C, X]})#l, S, A] {
    def apply(s: S) = f(s).point[({type l[+X] = ReaderTOption[C, X]})#l]
  }
  def get[C, S]: StateTReaderTOption[C, S, S] =
    StateTReaderTOption { s => (s, s) }
  def put[C, S](s: S): StateTReaderTOption[C, S, Unit] =
    StateTReaderTOption { _ => (s, ()) }
}

// Exiting paste mode, now interpreting.
</scala>

This is a bit confusing. Ultimately the point of `State` monad is to wrap `S => (S, A)`, so I kept those parameter names. Next, we need to modify the kind of `ReaderTOption` to `* -> *` (a type constructor that takes exactly one type as its parameter).

Suppose we want to implement `Stack` using state like we did in day 7.

<scala>
scala> type Stack = List[Int]
defined type alias Stack

scala> type Config = Map[String, String]
defined type alias Config

scala> val pop = StateTReaderTOption[Config, Stack, Int] {
         case x :: xs => (xs, x)
       }
pop: scalaz.StateT[[+X]scalaz.Kleisli[Option,Config,X],Stack,Int] = StateTReaderTOption$$anon$1@122313eb
</scala>

Since I wrote `get` and `put` we should be able to write it using `for` syntax as well:

<scala>
scala> val pop: StateTReaderTOption[Config, Stack, Int] = {
         import StateTReaderTOption.{get, put}
         for {
           s <- get[Config, Stack]
           val (x :: xs) = s
           _ <- put(xs)
         } yield x
       }
pop: StateTReaderTOption[Config,Stack,Int] = scalaz.StateT$$anon$7@7eb316d2
</scala>

Here's `push`:

<scala>
scala> def push(x: Int): StateTReaderTOption[Config, Stack, Unit] = {
         import StateTReaderTOption.{get, put}
         for {
           xs <- get[Config, Stack]
           r <- put(x :: xs)
         } yield r
       }
push: (x: Int)StateTReaderTOption[Config,Stack,Unit]
</scala>

We can also port `stackManip`:

<scala>
scala> def stackManip: StateTReaderTOption[Config, Stack, Int] = for {
         _ <- push(3)
         a <- pop
         b <- pop
       } yield(b)
stackManip: StateTReaderTOption[Config,Stack,Int]
</scala>

Here's how we run this.

<scala>
scala> stackManip(List(5, 8, 2, 1))(Map())
res12: Option[(Stack, Int)] = Some((List(8, 2, 1),5))
</scala>

So far we have the same feature as the `State` version. Let's modify `configure`:

<scala>
scala> def configure[S](key: String) = new StateTReaderTOption[Config, S, String] {
         def apply(s: S) = ReaderTOption[Config, (S, String)] { config: Config => config.get(key) map {(s, _)} }
       }
configure: [S](key: String)StateTReaderTOption[Config,S,String]
</scala>

Using this we can now manipulate the stack using read-only configuration:

<scala>
scala> def stackManip: StateTReaderTOption[Config, Stack, Unit] = for {
         x <- configure("x")
         a <- push(x.toInt)
       } yield(a)

scala> stackManip(List(5, 8, 2, 1))(Map("x" -> "7"))
res21: Option[(Stack, Unit)] = Some((List(7, 5, 8, 2, 1),()))

scala> stackManip(List(5, 8, 2, 1))(Map("y" -> "7"))
res22: Option[(Stack, Unit)] = None
</scala>

Now we have `StateT`, `ReaderT` and `Option` working all at the same time. Maybe I am not doing it right, but setting this up defining `StateTReaderTOption` and `configure` was painful. The usage code (`stackManip`) looks clean so we might do these things for special occasions like Thanksgiving.

It was rough without LYAHFGG, but we will pick it up from here later.
