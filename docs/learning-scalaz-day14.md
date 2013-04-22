  [day13]: http://eed3si9n.com/learning-scalaz-day13

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-11180bw.jpg">
<div class="credit">bman ojel for openphoto.net</div>
</div>

[Yesterday][day13] we looked at what `import scalaz._` and `Scalaz._` bring into the scope, and also talked about a la carte style import. Knowing how instances and syntax are organized prepares us for the next step, which is to hack on Scalaz.

### mailing list

Before we start hacking on a project, it's probably good idea to join [its Google Group](https://groups.google.com/forum/#!forum/scalaz).

### git clone

<code>
$ git clone -b scalaz-seven git://github.com/scalaz/scalaz.git scalaz-seven
</code>

The above should clone `scalaz-seven` branch into `./scalaz-seven` directory. Next I edited the `.git/config` as follows:

<code>
[core]
  repositoryformatversion = 0
  filemode = true
  bare = false
  logallrefupdates = true
  ignorecase = true
[remote "upstream"]
  fetch = +refs/heads/*:refs/remotes/origin/*
  url = git://github.com/scalaz/scalaz.git
[branch "scalaz-seven"]
  remote = upstream
  merge = refs/heads/scalaz-seven
</code>

This way, `scalaz/scalaz` is referenced using the name `upstream` instead of origin. To track the changes, run:

<code>
$ git pull --rebase
Current branch scalaz-seven is up to date.
</code>

### sbt

Next, launch sbt 0.12.0, set scala version to 2.10.0-M7, switch to `core` project, and compile:

<code>
$ sbt
scalaz> ++ 2.10.0-M7
Setting version to 2.10.0-M7
[info] Set current project to scalaz (in build file:/Users/eed3si9n/work/scalaz-seven/)
scalaz> project core
[info] Set current project to scalaz-core (in build file:/Users/eed3si9n/work/scalaz-seven/)
scalaz-core> compile
</code>

This might take a few minutes. Let's make sure this builds a snapshot version:

<code>
scalaz-core> version
[info] 7.0-SNAPSHOT
</code>

To try out the locally compiled Scalaz, just get into the REPL as usual using `console`:

<code>
scalaz-core> console
[info] Starting scala interpreter...
[info] 
Welcome to Scala version 2.10.0-M7 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_33).
Type in expressions to have them evaluated.
Type :help for more information.

scala> [Ctrl + D to exit]
</code>

### including Vector

Let's address some of the things we've noticed in the last few weeks. For example, I think `Vector` instances should be part of `import Scalaz._`. This should be easy while my memory is fresh from yesterday's import review. Let's make a topic branch `topic/vectorinstance`:

<code>
$ git branch topic/vectorinstance
$ git co topic/vectorinstance
Switched to branch 'topic/vectorinstance'
</code>

To confirm that `Vector` instances and methods are not loaded in by `import Scalaz._`, let's check it from sbt console:

<scala>
$ sbt
scalaz> ++ 2.10.0-M7
scalaz> project core
scalaz-core> console
scala> import scalaz._
import scalaz._

scala> import Scalaz._
import Scalaz._

scala> Vector(1, 2) >>= { x => Vector(x + 1)}
<console>:14: error: could not find implicit value for parameter F0: scalaz.Bind[scala.collection.immutable.Vector]
              Vector(1, 2) >>= { x => Vector(x + 1)}
                    ^

scala> Vector(1, 2) filterM { x => Vector(true, false) }
<console>:14: error: value filterM is not a member of scala.collection.immutable.Vector[Int]
              Vector(1, 2) filterM { x => Vector(true, false) }
                           ^
</scala>

Failed as expected.

Update [`std.AllInstances`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllInstances.scala) by mixing in `VectorInstances`:

<scala>
trait AllInstances
  extends AnyValInstances with FunctionInstances with ListInstances with MapInstances
  with OptionInstances with SetInstances with StringInstances with StreamInstances
  with TupleInstances with VectorInstances
  ...
</scala>

Update [`syntax.std.ToAllStdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ToAllStdOps.scala) and add `ToVectorOps`:

<scala>
trait ToAllStdOps
  extends ToBooleanOps with ToOptionOps with ToOptionIdOps with ToListOps with ToStreamOps with ToVectorOps
  ...
</scala>

That's it. Let's try it from REPL. 

<scala>
scala> Vector(1, 2) >>= { x => Vector(x + 1)}
res0: scala.collection.immutable.Vector[Int] = Vector(2, 3)

scala> Vector(1, 2) filterM { x => Vector(true, false) }
res1: scala.collection.immutable.Vector[Vector[Int]] = Vector(Vector(1, 2), Vector(1), Vector(2), Vector())
</scala>

It works. I didn't see tests written for these type of things, so we'll go without one. I committed it as "include VectorInstances and ToVectorOps to import Scalaz._." Next, fork scalaz project on github.

<code>
$ git remote add fork git@github.com:yourname/scalaz.git
$ git push fork topic/vectorinstance
...
 * [new branch]      topic/vectorinstance -> topic/vectorinstance
</code>

Send [a pull request](https://github.com/scalaz/scalaz/pull/151) with some comments, and let's see what happens. To work on a next feature, we want to rewind back to `scalaz-seven` branch. For using locally, let's create a snapshot branch:

### snapshot

<code>
$ git co scalaz-seven
Switched to branch 'scalaz-seven'
$ git branch snapshot
$ git co snapshot
$ git merge topic/vectorinstance
</code>

We can use this branch as a sandbox to play around with Scalaz.

### <*> operator

Next, I'd really like to roll back `<*>` operator for `Apply` back to M2/Haskell behavior. I've [asked this](https://groups.google.com/forum/#!topic/scalaz/g0YPdgBeEAw) on the mailing list and the author seems to be ok with rolling back.

<code>
$ git co scalaz-seven
Switched to branch 'scalaz-seven'
$ git branch topic/applyops
$ git co topic/applyops
Switched to branch 'topic/applyops'
</code>

This one we really should write a test first. Let's add an example in [`ApplyTest`](https://github.com/scalaz/scalaz/blob/9412258332e2dd42ecc82a363e39decb503eb2d5/tests/src/test/scala/scalaz/ApplyTest.scala):

<scala>
  "<*>" in {
    some(9) <*> some({(_: Int) + 3}) must be_===(some(12))
  }
</scala>

The specs used in build.scala works for Scala 2.9.2.

<code>
$ sbt
scalaz> ++ 2.9.2
Setting version to 2.9.2
scalaz> project tests
scalaz-tests> test-only scalaz.ApplyTest
[error] /Users/eed3si9n/work/scalaz-seven/tests/src/test/scala/scalaz/ApplyTest.scala:38: type mismatch;
[error]  found   : org.specs2.matcher.Matcher[Option[Int]]
[error]  required: org.specs2.matcher.Matcher[Option[(Int, Int => Int)]]
[error]     some(9) <*> some({(_: Int) + 3}) must be_===(some(12))
[error]                                                 ^
[error] one error found
[error] (tests/test:compile) Compilation failed
</code>

It didn't even compile because of `===`. Nice.

The `<*>` is in [`ApplyOps`](https://github.com/scalaz/scalaz/blob/9412258332e2dd42ecc82a363e39decb503eb2d5/core/src/main/scala/scalaz/syntax/ApplySyntax.scala), so let's change it back to `F.ap`:

<scala>
  final def <*>[B](f: F[A => B]): F[B] = F.ap(self)(f)
</scala>

Now let's run the test again:

<code>
scalaz-tests> test-only scalaz.ApplyTest
[info] ApplyTest
[info] 
[info] + mapN
[info] + apN
[info] + <*>
[info]  
[info] Total for specification ApplyTest
[info] Finished in 5 seconds, 27 ms
[info] 3 examples, 0 failure, 0 error
[info] 
[info] Passed: : Total 3, Failed 0, Errors 0, Passed 3, Skipped 0
[success] Total time: 9 s, completed Sep 19, 2012 1:57:29 AM
</scala>

I am committing this as "roll back <*> as infix of ap" and pushing it out.

<code>
$ git push fork topic/applyops
...
 * [new branch]      topic/applyops -> topic/applyops
</code>

Send [a pull request](https://github.com/scalaz/scalaz/pull/152) with some comments. Let's apply this to our `snapshot` branch:

<code>
$ git co snapshot
$ git merge topic/applyops
</code>

So now it has both of the changes we created.

### applicative functions

The changed we made were so far simple fixes. From here starts an experiment. It's about applicative functions.

[The Essence of the Iterator Pattern](http://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf) presents an interesting idea of combining applicative functors. What's actually going on is not just the combination of applicative functors (`m ⊠ n`), but the combination of applicative functions:

<haskell>
(⊗)::(Functor m,Functor n) ⇒ (a → m b) → (a → n b) → (a → (m ⊠ n) b)
(f ⊗ g) x = Prod (f x) (g x)
</haskell>

`Int` is a `Monoid`, and any `Monoid` can be treated as an applicative functor, which is called monoidal applicatives. The problem is that when we make that into a function, it's not distinguishable from `Int => Int`, but we need `Int => [α]Int`.

My first idea was to use type tags named `Tags.Monoidal`, so the idea is to make it:

<scala>
scala> { (x: Int) => Tags.Monoidal(x + 1) }
</scala>

This requires all `A @@ Tags.Monoidal` where `[A:Monoid]` to be recognized as an applicative. I got stuck on that step.

Next idea was to make `Monoidal` an alias of `Kleisli` with the following companion:

<scala>
  object Monoidal {
    def apply[A: Monoid](f: A => A): Kleisli[({type λ[+α]=A})#λ, A, A] =
      Kleisli[({type λ[+α]=A})#λ, A, A](f)
  }
</scala>

This let's me write monoidal functions as follows:

<scala>
scala> Monoidal { x: Int => x + 1 }
res4: scalaz.Kleisli[[+α]Int,Int,Int] = scalaz.KleisliFunctions$$anon$18@1a0ceb34
</scala>

But the compiler did not find `Applicative` automatically from `[+α]Int`:

<scala>
scala> List(1, 2, 3) traverseKTrampoline { x => Monoidal { _: Int => x + 1 } } 
<console>:14: error: no type parameters for method traverseKTrampoline: (f: Int => scalaz.Kleisli[G,S,B])(implicit evidence$2: scalaz.Applicative[G])scalaz.Kleisli[G,S,List[B]] exist so that it can be applied to arguments (Int => scalaz.Kleisli[[+α]Int,Int,Int])
 --- because ---
argument expression's type is not compatible with formal parameter type;
 found   : Int => scalaz.Kleisli[[+α]Int,Int,Int]
 required: Int => scalaz.Kleisli[?G,?S,?B]

              List(1, 2, 3) traverseKTrampoline { x => Monoidal { _: Int => x + 1 } } 
                            ^
</scala>

Is this the infamous [SI-2712](https://issues.scala-lang.org/browse/SI-2712)? Then I thought, ok I'll turn this into an actual type:

<scala>
trait MonoidApplicative[F] extends Applicative[({type λ[α]=F})#λ] { self =>
  implicit def M: Monoid[F]
  def point[A](a: => A) = M.zero
  def ap[A, B](fa: => F)(f: => F) = M.append(f, fa)
  override def map[A, B](fa: F)(f: (A) => B) = fa
}
</scala>

This does not work because now we have to convert `x + 1` into `MonoidApplicative`.

Next I thought about giving `Unapply` a shot:

<scala>
scala> List(1, 2, 3) traverseU {_ + 1}
<console>:14: error: Unable to unapply type `Int` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Int])
              List(1, 2, 3) traverseU {_ + 1}
                            ^
</scala>

This could work. All we have to do is unpack `Int` as `({type λ[α]=Int})#λ` in [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala):

<scala>
trait Unapply_3 {
  /** Unpack a value of type `A0` into type `[a]A0`, given a instance of `TC` */
  implicit def unapplyA[TC[_[_]], A0](implicit TC0: TC[({type λ[α] = A0})#λ]): Unapply[TC, A0] {
    type M[X] = A0
    type A = A0
  } = new Unapply[TC, A0] {
    type M[X] = A0
    type A = A0
    def TC = TC0
    def apply(ma: M[A0]) = ma
  }
}
</scala>

Let's try:

<scala>
scala> List(1, 2, 3) traverseU {_ + 1}
res0: Int = 9
</scala>

This actually worked! Can we combine this?

<scala>
scala> val f = { (x: Int) => x + 1 }
f: Int => Int = <function1>

scala> val g = { (x: Int) => List(x, 5) }
g: Int => List[Int] = <function1>

scala> val h = f &&& g
h: Int => (Int, List[Int]) = <function1>

scala> List(1, 2, 3) traverseU f
res0: Int = 9

scala> List(1, 2, 3) traverseU g
res1: List[List[Int]] = List(List(1, 2, 3), List(1, 2, 5), List(1, 5, 3), List(1, 5, 5), List(5, 2, 3), List(5, 2, 5), List(5, 5, 3), List(5, 5, 5))

scala> List(1, 2, 3) traverseU h
res2: (Int, List[List[Int]]) = (9,List(List(1, 5), List(2, 5), List(3, 5)))
</scala>

I am guessing either `res1` or `res2` is wrong. `res1` is what `traverse` is supposed to return at least from what I checked in Haskell. Because `Tuple2` is also an applicative it's doing something unexpected. I was able to confirm this behavior without my changes, so let's add a test:

<scala>
    "traverse int function as monoidal applicative" in {
      val s: Int = List(1, 2, 3) traverseU {_ + 1}
      s must be_===(9)
    }
</scala>

Let's run it:

<code>
scalaz-tests> test-only scalaz.TraverseTest
[info] list should
[info] + apply effects in order
[info] + traverse through option effect
[info] + traverse int function as monoidal applicative
[info] + not blow the stack
[info] + state traverse agrees with regular traverse
[info] + state traverse does not blow stack
...
[success] Total time: 183 s, completed Sep 19, 2012 8:09:03 AM
</code>

Branch out from `scalaz-seven` and make `topic/unapplya` branch:

<code>
$ git co scalaz-seven
M core/src/main/scala/scalaz/Unapply.scala
M tests/src/test/scala/scalaz/TraverseTest.scala
Switched to branch 'scalaz-seven'
$ git branch topic/unapplya
$ git co topic/unapplya
M core/src/main/scala/scalaz/Unapply.scala
M tests/src/test/scala/scalaz/TraverseTest.scala
Switched to branch 'topic/unapplya'
</code>

If all the tests pass, I am committing this as "adds implicit def unapplyA, which unpacks A into [a]A."

<code>
$ git push fork topic/unapplya
...
 * [new branch]      topic/unapplya -> topic/unapplya
</code>

Let's send this as [a pull request](https://github.com/scalaz/scalaz/pull/153) too. This was fun.

We'll pick it up from here later.
