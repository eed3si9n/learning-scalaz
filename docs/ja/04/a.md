---
out: Functor-Laws.html
---

Jason Zaugg にもコメントをもらった:

> This might be a good point to pause and discuss the laws by which a well behaved type class instance must abide.

> この辺りで一度立ち止まって、行儀の良い型クラスが従うべき法則についても議論すべきじゃないですか。

[すごいHaskellたのしく学ぼう](http://www.amazon.co.jp/dp/4274068854)の型クラスの法則に関しては全て飛ばしてきたところを、パトカーに止められた形だ。恥ずかしい限りだ。

### Functor則

LYAHFGG:

> すべてのファンクターの性質や挙動は、ある一定の法則に従うことになっています。
> ...
> ファンクターの第一法則は、「`id` でファンクター値を写した場合、ファンクター値が変化してはいけない」というものです。

言い換えると、

```scala
scala> List(1, 2, 3) map {identity} assert_=== List(1, 2, 3)
```

> 第二法則は、2つの関数 `f` と `g` について、「`f` と `g` の合成関数でファンクター値を写したもの」と、「まず `g`、次に `f` でファンクター値を写したもの」が等しいことを要求します。

言い換えると、

```scala
scala> (List(1, 2, 3) map {{(_: Int) * 3} map {(_: Int) + 1}}) assert_=== (List(1, 2, 3) map {(_: Int) * 3} map {(_: Int) + 1})
```

これらの法則は Functor の実装者が従うべき法則で、コンパイラはチェックしてくれない。Scalaz 7 には[コード](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Functor.scala#L68-77)でこれを記述した `FunctorLaw` trait が入っている:

```scala
trait FunctorLaw {
  /** The identity function, lifted, is a no-op. */
  def identity[A](fa: F[A])(implicit FA: Equal[F[A]]): Boolean = FA.equal(map(fa)(x => x), fa)

  /**
   * A series of maps may be freely rewritten as a single map on a
   * composed function.
   */
  def associative[A, B, C](fa: F[A], f1: A => B, f2: B => C)(implicit FC: Equal[F[C]]): Boolean = FC.equal(map(map(fa)(f1))(f2), map(fa)(f2 compose f1))
}
```

それだけじゃなく、これらを任意の値でテストする ScalaCheck へのバインディングもついてきてる。以下が REPL からこれを実行するための `build.sbt` だ:

```scala
scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.0",
  "org.scalaz" %% "scalaz-effect" % "7.0.0",
  "org.scalaz" %% "scalaz-typelevel" % "7.0.0",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.0" % "test"
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"

initialCommands in console in Test := "import scalaz._, Scalaz._, scalacheck.ScalazProperties._, scalacheck.ScalazArbitrary._,scalacheck.ScalaCheckBinding._"
```

通常の `sbt console` のかわりに、`sbt test:console` を実行する:

```scala
\$ sbt test:console
[info] Starting scala interpreter...
[info] 
import scalaz._
import Scalaz._
import scalacheck.ScalazProperties._
import scalacheck.ScalazArbitrary._
import scalacheck.ScalaCheckBinding._
Welcome to Scala version 2.10.1 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_45).
Type in expressions to have them evaluated.
Type :help for more information.

scala> 
```

`List` が Functor則を満たすかテストしてる:

```scala
scala> functor.laws[List].check
+ functor.identity: OK, passed 100 tests.
+ functor.associative: OK, passed 100 tests.
```

### 法則を破る

本にあわせて、法則を破ってみよう:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait COption[+A] {}
case class CSome[A](counter: Int, a: A) extends COption[A]
case object CNone extends COption[Nothing]

implicit def coptionEqual[A]: Equal[COption[A]] = Equal.equalA
implicit val coptionFunctor = new Functor[COption] {
  def map[A, B](fa: COption[A])(f: A => B): COption[B] = fa match {
    case CNone => CNone
    case CSome(c, a) => CSome(c + 1, f(a))
  }
}

// Exiting paste mode, now interpreting.

defined trait COption
defined class CSome
defined module CNone
coptionEqual: [A]=> scalaz.Equal[COption[A]]
coptionFunctor: scalaz.Functor[COption] = \$anon\$1@42538425

scala> (CSome(0, "ho"): COption[String]) map {(_: String) + "ha"}
res4: COption[String] = CSome(1,hoha)

scala> (CSome(0, "ho"): COption[String]) map {identity}
res5: COption[String] = CSome(1,ho)
```

これは最初の法則を破っている。検知できるかみてみよう。

```scala
scala> functor.laws[COption].check
<console>:26: error: could not find implicit value for parameter af: org.scalacheck.Arbitrary[COption[Int]]
              functor.laws[COption].check
                          ^
```

`COption[A]` の「任意」の値を暗黙に提供しなきゃいけないみたいだ:

```scala
scala> import org.scalacheck.{Gen, Arbitrary}
import org.scalacheck.{Gen, Arbitrary}

scala> implicit def COptionArbiterary[A](implicit a: Arbitrary[A]): Arbitrary[COption[A]] =
         a map { a => (CSome(0, a): COption[A]) }
COptionArbiterary: [A](implicit a: org.scalacheck.Arbitrary[A])org.scalacheck.Arbitrary[COption[A]]
```

これは面白い。ScalaCheck そのものは `map` メソッドを提供しないけど、Scalaz が `Functor[Arbitrary]` として注入している! あまりぱっとしない任意の `COption` だけど、ScalaCheck をよく知らないのでこれでいいとする。

```scala
scala> functor.laws[COption].check
! functor.identity: Falsified after 0 passed tests.
> ARG_0: CSome(0,-170856004)
! functor.associative: Falsified after 0 passed tests.
> ARG_0: CSome(0,1)
> ARG_1: <function1>
> ARG_2: <function1>
```

期待通りテストは失敗した。

### Applicative則

これが [Applicative則](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Applicative.scala#L60-72)だ:

```scala
  trait ApplicativeLaw extends FunctorLaw {
    def identityAp[A](fa: F[A])(implicit FA: Equal[F[A]]): Boolean =
      FA.equal(ap(fa)(point((a: A) => a)), fa)

    def composition[A, B, C](fbc: F[B => C], fab: F[A => B], fa: F[A])(implicit FC: Equal[F[C]]) =
      FC.equal(ap(ap(fa)(fab))(fbc), ap(fa)(ap(fab)(ap(fbc)(point((bc: B => C) => (ab: A => B) => bc compose ab)))))

    def homomorphism[A, B](ab: A => B, a: A)(implicit FB: Equal[F[B]]): Boolean =
      FB.equal(ap(point(a))(point(ab)), point(ab(a)))

    def interchange[A, B](f: F[A => B], a: A)(implicit FB: Equal[F[B]]): Boolean =
      FB.equal(ap(point(a))(f), ap(f)(point((f: A => B) => f(a))))
  }
```

LYAHFGG も詳細は飛ばしているので、僕も見逃してもらう。

### Semigroup則

これが、[Semigroup則](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Semigroup.scala#L38-47)だ:

```scala
  /**
   * A semigroup in type F must satisfy two laws:
    *
    *  - '''closure''': `∀ a, b in F, append(a, b)` is also in `F`. This is enforced by the type system.
    *  - '''associativity''': `∀ a, b, c` in `F`, the equation `append(append(a, b), c) = append(a, append(b , c))` holds.
   */
  trait SemigroupLaw {
    def associative(f1: F, f2: F, f3: F)(implicit F: Equal[F]): Boolean =
      F.equal(append(f1, append(f2, f3)), append(append(f1, f2), f3))
  }
```

`1 * (2 * 3)` と `(1 * 2) * 3` が満たされるべきで、これは結合律 (*associative*) と呼ばれるのは覚えているよね。

```scala
scala> semigroup.laws[Int @@ Tags.Multiplication].check
+ semigroup.associative: OK, passed 100 tests.
```

### Monoid則

これが [Monoid則](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Monoid.scala#L50-59) だ:

```scala
  /**
   * Monoid instances must satisfy [[scalaz.Semigroup.SemigroupLaw]] and 2 additional laws:
   *
   *  - '''left identity''': `forall a. append(zero, a) == a`
   *  - '''right identity''' : `forall a. append(a, zero) == a`
   */
  trait MonoidLaw extends SemigroupLaw {
    def leftIdentity(a: F)(implicit F: Equal[F]) = F.equal(a, append(zero, a))
    def rightIdentity(a: F)(implicit F: Equal[F]) = F.equal(a, append(a, zero))
  }
```

この法則は簡単だ。単位元 (identity value) を左右のどちらに `|+|` (`mappend`) しても同じ値が返ってくるということだ。乗算で確認:

```scala
scala> 1 * 2 assert_=== 2

scala> 2 * 1 assert_=== 2
```

Scalaz で書くと:

```scala
scala> (Monoid[Int @@ Tags.Multiplication].zero |+| Tags.Multiplication(2): Int) assert_=== 2

scala> (Tags.Multiplication(2) |+| Monoid[Int @@ Tags.Multiplication].zero: Int) assert_=== 2

scala> monoid.laws[Int @@ Tags.Multiplication].check
+ monoid.semigroup.associative: OK, passed 100 tests.
+ monoid.left identity: OK, passed 100 tests.
+ monoid.right identity: OK, passed 100 tests.
```
