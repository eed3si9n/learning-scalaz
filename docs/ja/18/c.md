
## Stackless Scala with Free Monads

Free モナドに関する一般的な理解が得られた所で、Scala Days 2012 での Rúnar の講演を観よう: [Stackless Scala With Free Monads](http://skillsmatter.com/podcast/scala/stackless-scala-free-monads)。ペーパーを読む前にトークを観ておくことをお勧めするけど、ペーパーの方が引用しやすいので [Stackless Scala With Free Monads](http://days2012.scala-lang.org/sites/days2012/files/bjarnason_trampolines.pdf) もリンクしておく。

Rúnar はまず State モナドを使ってリストに添字を zip するコードから始める。これはリストがスタックの限界よりも大きいと、スタックを吹っ飛ばす。続いてプログラム全体を一つのループで回すトランポリンというものを紹介している。

```scala
sealed trait Trampoline [+ A] {
  final def runT : A =
    this match {
      case More (k) => k().runT
      case Done (v) => v
    }
}
case class More[+A](k: () => Trampoline[A])
  extends Trampoline[A]
case class Done [+A](result: A)
  extends Trampoline [A]
```

上記のコードでは `Function0` の `k` は次のステップのための thunk となっている。

これを State モナドを使った使用例に拡張するため、`flatMap` を `FlatMap` というデータ構造に具現化している:

```scala
case class FlatMap [A,+B](
  sub: Trampoline [A],
  k: A => Trampoline[B]) extends Trampoline[B]
```

続いて、`Trampoline` は実は `Function0` の Free モナドであることが明かされる。Scalaz 7 では以下のように定義されている:

```scala
  type Trampoline[+A] = Free[Function0, A]
```

### Free monads

さらに Rúnar は便利な Free モナドを作れるいくつかのデータ構造を紹介する:

```scala
type Pair[+A] = (A, A)
type BinTree[+A] = Free[Pair, A]

type Tree[+A] = Free[List, A]

type FreeMonoid[+A] = Free[({type λ[+α] = (A,α)})#λ, Unit]

type Trivial[+A] = Unit
type Option[+A] = Free[Trivial, A]
```

Free モナドを使った Iteratee まであるみたいだ。最後に Free モナドを以下のようにまとめている:

> - データが末端に来る全ての再帰データ型に使えるモデル
> - Free モナドは変数が末端にある式木で、flatMap は変数の置換にあたる。

### トランポリン

トランポリンを使えば、どんなプログラムでもスタックを使わないものに変換することができる。トークに出てきた `even` と `odd` を Scalaz 7 の `Trampoline` を使って実装してみよう。`Free` object はトランポリン化に役立つ関数を定義する `FreeFunction` を継承する:

```scala
trait FreeFunctions {
  /** Collapse a trampoline to a single step. */
  def reset[A](r: Trampoline[A]): Trampoline[A] = { val a = r.run; return_(a) }

  /** Suspend the given computation in a single step. */
  def return_[S[+_], A](value: => A)(implicit S: Pointed[S]): Free[S, A] =
    Suspend[S, A](S.point(Return[S, A](value)))

  def suspend[S[+_], A](value: => Free[S, A])(implicit S: Pointed[S]): Free[S, A] =
    Suspend[S, A](S.point(value))

  /** A trampoline step that doesn't do anything. */
  def pause: Trampoline[Unit] =
    return_(())

  ...
}
```

これらを使うには `import Free._` を呼ぶ。

```scala
scala> import Free._
import Free._

scala> :paste
// Entering paste mode (ctrl-D to finish)

def even[A](ns: List[A]): Trampoline[Boolean] =
  ns match {
    case Nil => return_(true)
    case x :: xs => suspend(odd(xs))
  }
def odd[A](ns: List[A]): Trampoline[Boolean] =
  ns match {
    case Nil => return_(false)
    case x :: xs => suspend(even(xs))
  }

// Exiting paste mode, now interpreting.

even: [A](ns: List[A])scalaz.Free.Trampoline[Boolean]
odd: [A](ns: List[A])scalaz.Free.Trampoline[Boolean]

scala> even(List(1, 2, 3)).run
res118: Boolean = false

scala> even(0 |-> 3000).run
res119: Boolean = false
```

これは意外と簡単にできた。

### Free を用いたリスト

Free を使って「リスト」を定義してみよう。

```scala
scala> type FreeMonoid[A] = Free[({type λ[+α] = (A,α)})#λ, Unit]
defined type alias FreeMonoid

scala> def cons[A](a: A): FreeMonoid[A] = Free.Suspend[({type λ[+α] = (A,α)})#λ, Unit]((a, Free.Return[({type λ[+α] = (A,α)})#λ, Unit](())))
cons: [A](a: A)FreeMonoid[A]

scala> cons(1)
res0: FreeMonoid[Int] = Suspend((1,Return(())))

scala> cons(1) >>= {_ => cons(2)}
res1: scalaz.Free[[+α](Int, α),Unit] = Gosub(Suspend((1,Return(()))),<function1>)
```

この結果を処理する一例として標準の `List` に変換してみる:

```scala
scala> def toList[A](list: FreeMonoid[A]): List[A] =
         list.resume.fold(
           { case (x: A, xs: FreeMonoid[A]) => x :: toList(xs) },
           { _ => Nil })

scala> toList(res1)
res4: List[Int] = List(1, 2)
```

今日はここまで。
