  [day17]: http://eed3si9n.com/ja/learning-scalaz-day17

[17日目][day17]は、副作用を抽象化する方法としての IO モナドと、ストリームを取り扱うための Iteratee をみて、シリーズを終えた。

### Func

Applicative 関数の合成を行うより良い方法を引き続き試してみて、`AppFunc` というラッパーを作った:

<scala>
val f = AppFuncU { (x: Int) => x + 1 }
val g = AppFuncU { (x: Int) => List(x, 5) }
(f @&&& g) traverse List(1, 2, 3)
</scala>

これを [pull request](https://github.com/scalaz/scalaz/pull/161) として送った後、Lars Hupel さん ([@larsr_h](https://twitter.com/larsr_h)) から typelevel モジュールを使って一般化した方がいいという提案があったので、`Func` に拡張した:

<scala>
/**
 * Represents a function `A => F[B]` where `[F: TC]`.
 */
trait Func[F[_], TC[F[_]] <: Functor[F], A, B] {
  def runA(a: A): F[B]
  implicit def TC: KTypeClass[TC]
  implicit def F: TC[F]
  ...
}
</scala>

これを使うと、`AppFunc` は `Func` の 2つ目の型パラメータに `Applicative` を入れた特殊形という扱いになる。Lars さんはさらに合成を `HList` に拡張したいみたいだけど、いつかこの機能が Scalaz 7 に入ると楽観的に見ている。

### インタプリタ

今日は、Gabriel Gonzalez の [Why free monads matter](http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html) を読みながら Free モナドをみていきたい:

> 構文木の本質を表す抽象体を考えてみよう。[中略] 僕らの toy 言語には 3つのコマンドしかない:

<haskell>
output b -- prints a "b" to the console
bell     -- rings the computer's bell
done     -- end of execution
</haskell>

`Toy` を Scala に翻訳してみる:

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Toy[A]
case class Output[A, B](a: A, next: B) extends Toy[A]
case class Bell[A, B](next: B) extends Toy[A]
case class Done[A]() extends Toy[A]

// Exiting paste mode, now interpreting.

defined trait Toy
defined class Output
defined class Bell
defined class Done

scala> Output('A', Done())
res0: Output[Char,Done[Nothing]] = Output(A,Done())

scala> Bell(Output('A', Done()))
res1: Bell[Nothing,Output[Char,Done[Nothing]]] = Bell(Output(A,Done()))
</scala>

WFMM:

> しかし残念なことに、コマンドを追加するたびに型が変わってしまうのでこれはうまくいかない。

Scala の場合は全てが `Toy` を継承しているので、これが問題になるかは分からないけど、とりあえず流れで `Fix` を定義してみる:

<scala>
scala> case class Fix[F[_]](f: F[Fix[F]])
defined class Fix

scala> Fix[({type λ[α] = Toy[Char]})#λ](Output('A', Fix[({type λ[α] = Toy[Char]})#λ](Done())))
res4: Fix[[α]Toy[Char]] = Fix(Output(A,Fix(Done())))

scala> Fix[({type λ[α] = Toy[Char]})#λ](Bell(Fix[({type λ[α] = Toy[Char]})#λ](Output('A', Fix[({type λ[α] = Toy[Char]})#λ](Done())))))
res11: Fix[[α]Toy[Char]] = Fix(Bell(Fix(Output(A,Fix(Done())))))
</scala>

これに例外を加えた `FixE` も折角だから実装してみる。

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait FixE[F[_], E]
case class Fix[F[_], E](f: F[FixE[F, E]]) extends FixE[F, E]
case class Throw[F[_], E](e: E) extends FixE[F, E] 

// Exiting paste mode, now interpreting.

defined trait FixE
defined class Fix
defined class Throw
</scala>

`catch` は予約語なので `catchy` という名前にする:

<scala>
scala> import scalaz._
import scalaz._

scala> def catchy[F[_]: Functor, E1, E2](ex: => FixE[F, E1])(f: E1 => FixE[F, E2]): FixE[F, E2] = ex match {
         case Fix(x)   => Fix[F, E2](Functor[F].map(x) {catchy(_)(f)})
         case Throw(e) => f(e)
       }
catchy: [F[_], E1, E2](ex: => FixE[F,E1])(f: E1 => FixE[F,E2])(implicit evidence$1: scalaz.Functor[F])FixE[F,E2]

scala> implicit def ToyFunctor[A1]: Functor[({type λ[α] = Toy[A1]})#λ] = new Functor[({type λ[α] = Toy[A1]})#λ] {
         def map[A, B](fa: Toy[A1])(f: A => B): Toy[A1] = fa match {
           case o: Output[A1, A] => Output(o.a, f(o.next))
           case b: Bell[A1, A]   => Bell(f(b.next))
           case Done()           => Done()
         }
       }
ToyFunctor: [A1]=> scalaz.Functor[[α]Toy[A1,α]]
</scala>

これがサンプルの使用例だ:

<scala>
scala> case class IncompleteException()
defined class IncompleteException

scala> def subroutine = Fix[({type λ[α] = Toy[Char]})#λ, IncompleteException](Output('A', Throw[({type λ[α] = Toy[Char]})#λ, IncompleteException](IncompleteException())))
subroutine: Fix[[α]Toy[Char],IncompleteException]

scala> def program = catchy[({type λ[α] = Toy[Char]})#λ, IncompleteException, Nothing](subroutine) { _ =>
         Fix[({type λ[α] = Toy[Char]})#λ, Nothing](Bell(Fix[({type λ[α] = Toy[Char]})#λ, Nothing](Done())))
       }
program: FixE[[α]Toy[Char],Nothing]
</scala>

### Free monads part 1

WFMM:

> 僕らの `FixE` は既に存在していて、それは Free モナドと呼ばれる:

<haskell>
data Free f r = Free (f (Free f r)) | Pure r
</haskell>

> 名前の通り、これは自動的にモナドだ (ただし、`f` が Functor の場合)

<haskell>
instance (Functor f) => Monad (Free f) where
    return = Pure
    (Free x) >>= f = Free (fmap (>>= f) x)
    (Pure r) >>= f = f r
</haskell>

これに対応する Scalaz でのデータ構造は `Free` と呼ばれる:

<scala>
sealed abstract class Free[S[+_], +A](implicit S: Functor[S]) {
  final def map[B](f: A => B): Free[S, B] =
    flatMap(a => Return(f(a)))

  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
  ...
}

object Free extends FreeInstances {
  /** Return from the computation with the given value. */
  case class Return[S[+_]: Functor, +A](a: A) extends Free[S, A]

  /** Suspend the computation with the given suspension. */
  case class Suspend[S[+_]: Functor, +A](a: S[Free[S, A]]) extends Free[S, A]

  /** Call a subroutine and continue with the given function. */
  case class Gosub[S[+_]: Functor, A, +B](a: Free[S, A],
                                          f: A => Free[S, B]) extends Free[S, B]
}

trait FreeInstances {
  implicit def freeMonad[S[+_]:Functor]: Monad[({type f[x] = Free[S, x]})#f] =
    new Monad[({type f[x] = Free[S, x]})#f] {
      def point[A](a: => A) = Return(a)
      override def map[A, B](fa: Free[S, A])(f: A => B) = fa map f
      def bind[A, B](a: Free[S, A])(f: A => Free[S, B]) = a flatMap f
    }
}
</scala>

Scalaz 版では、`Free` コンストラクタは `Free.Suspend` と呼ばれ、`Pure` は `Free.Return` と呼ばれる。`liftF` を実装して、`Toy` コマンドを移植してみよう:

<scala>
scala> def output[A](a: A): Free[({type λ[+α] = Toy[A]})#λ, Unit] =
         Free.Suspend[({type λ[+α] = Toy[A]})#λ, Unit](Output(a, Free.Return[({type λ[+α] = Toy[A]})#λ, Unit](())))
output: [A](a: A)scalaz.Free[[+α]Toy[A],Unit]

scala> def liftF[F[+_]: Functor, R](command: F[R]): Free[F, R] =
         Free.Suspend[F, R](Functor[F].map(command) {Free.Return[F, R](_)})
liftF: [F[+_], R](command: F[R])(implicit evidence$1: scalaz.Functor[F])scalaz.Free[F,R]

scala> def output[A](a: A) = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Output(a, ()))
output: [A](a: A)scalaz.Free[[+α]Toy[A],Unit]

scala> def bell[A] = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Bell(()))
bell: [A]=> scalaz.Free[[+α]Toy[A],Unit]

scala> def done[A] = liftF[({type λ[+α] = Toy[A]})#λ, Unit](Done())
done: [A]=> scalaz.Free[[+α]Toy[A],Unit]
</scala>

コマンドのシーケンスを作ってみる:

<scala>
scala> val subroutine = output('A')
subroutine: scalaz.Free[[+α]Toy[Char],Unit] = Suspend(Output(A,Return(())))

scala> val program = for {
         _ <- subroutine
         _ <- bell[Char]
         _ <- done[Char]
       } yield ()
program: scalaz.Free[[+α]Toy[Char],Unit] = Gosub(Suspend(Output(A,Return(()))),<function1>)
</scala>

`showProgram` はこの `Free` ではそのままだとうまくいかない。`flatMap` の定義をみてほしい:

<scala>
  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
</scala>

新しい `Return` や `Suspend` を計算する代わりに `Gosub` というデータ構造を作っている。この `Gosub` を評価して `\/` を返す `resume` メソッドがあるので、`showProgram` は以下のように実装できる:

<scala>
scala> def showProgram[A: Show, R: Show](p: Free[({type λ[+α] = Toy[A]})#λ, R]): String =
         p.resume.fold({
           case Output(a: A, next: Free[({type λ[+α] = Toy[A]})#λ, R]) =>
             "output " + Show[A].shows(a) + "\n" + showProgram(next)
           case Bell(next: Free[({type λ[+α] = Toy[A]})#λ, R]) =>
             "bell " + "\n" + showProgram(next)
           case d: Done[A] =>
             "done\n"
         },
         { r: R => "return " + Show[R].shows(r) + "\n" }) 
showProgram: [A, R](p: scalaz.Free[[+α]Toy[A],R])(implicit evidence$1: scalaz.Show[A], implicit evidence$2: scalaz.Show[R])String

scala> showProgram(program)
res101: String = 
"output A
bell 
done
"
</scala>

pretty printer はこうなる:

<scala>
scala> def pretty[A: Show, R: Show](p: Free[({type λ[+α] = Toy[A]})#λ, R]) = print(showProgram(p))
pretty: [A, R](p: scalaz.Free[[+α]Toy[A],R])(implicit evidence$1: scalaz.Show[A], implicit evidence$2: scalaz.Show[R])Unit

scala> pretty(output('A'))
output A
return ()

scala> pretty(output('A') >>= { _ => done[Char]})
output A
done
</scala>

第二部へと飛ばす。

### Free monads part 2

WFMM:

<haskell>
data Free f r = Free (f (Free f r)) | Pure r
data List a   = Cons  a (List a  )  | Nil
</haskell>

> 言い換えると、Free モナドは Functor のリストだと考えることができる。`Free` コンストラクタは `Cons` のように振る舞い Functor をリストの先頭に追加し、`Pure` コンストラクタは `Nil` のように振る舞い空のリストを表す (つまり Functor が無い状態だ)。

第三部。

### Free monads part 3

WFMM:

> Free モナドはインタプリタの良き友だ。Free モナドはインタプリタを限りなく「解放 (free) 」しつつも必要最低限のモナドの条件を満たしている。

逆に、プログラムを書いている側から見ると、Free モナドそのものは逐次化以外の何も提供しない。インタプリタが何らかの `run` 関数を提供して役に立つ機能が得られる。ポイントは、`Functor` を満たすデータ構造さえあれば `Free` が最小のモナドを自動的に提供してくれることだと思う。

もう一つの見方としては、`Free` は与えられたコンテナを使って構文木を作る方法を提供する。

### Stackless Scala with Free Monads

Free モナドに関する一般的な理解が得られた所で、Scala Days 2012 での Rúnar の講演を観よう: [Stackless Scala With Free Monads](http://skillsmatter.com/podcast/scala/stackless-scala-free-monads)。ペーパーを読む前にトークを観ておくことをお勧めするけど、ペーパーの方が引用しやすいので [Stackless Scala With Free Monads](http://days2012.scala-lang.org/sites/days2012/files/bjarnason_trampolines.pdf) もリンクしておく。

Rúnar さんはまず State モナドを使ってリストに添字を zip するコードから始める。これはリストがスタックの限界よりも大きいと、スタックを吹っ飛ばす。続いてプログラム全体を一つのループで回すトランポリンというものを紹介している。

<scala>
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
</scala>

上記のコードでは `Function0` の `k` は次のステップのための thunk となっている。

これを State モナドを使った使用例に拡張するため、`flatMap` を `FlatMap` というデータ構造に具現化している:

<scala>
case class FlatMap [A,+B](
  sub: Trampoline [A],
  k: A => Trampoline[B]) extends Trampoline[B]
</scala>

続いて、`Trampoline` は実は `Function0` の Free モナドであることが明かされる。Scalaz 7 では以下のように定義されている:

<scala>
  type Trampoline[+A] = Free[Function0, A]
</scala>

### Free monads

さらに Rúnar さんは便利な Free モナドを作れるいくつかのデータ構造を紹介する:

<scala>
type Pair[+A] = (A, A)
type BinTree[+A] = Free[Pair, A]

type Tree[+A] = Free[List, A]

type FreeMonoid[+A] = Free[({type λ[+α] = (A,α)})#λ, Unit]

type Trivial[+A] = Unit
type Option[+A] = Free[Trivial, A]
</scala>

Free モナドを使った Iteratee まであるみたいだ。最後に Free モナドを以下のようにまとめている:

> - データが末端に来る全ての再帰データ型に使えるモデル
> - Free モナドは変数が末端にある式木で、flatMap は変数の置換にあたる。

### トランポリン

トランポリンを使えば、どんなプログラムでもスタックを使わないものに変換することができる。トークに出てきた `even` と `odd` を Scalaz 7 の `Trampoline` を使って実装してみよう。`Free` object はトランポリン化に役立つ関数を定義する `FreeFunction` を継承する:

<scala>
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
</scala>

これらを使うには `import Free._` を呼ぶ。

<scala>
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
</scala>

これは意外と簡単にできた。

### Free を用いたリスト

Free を使って「リスト」を定義してみよう。

<scala>
scala> type FreeMonoid[A] = Free[({type λ[+α] = (A,α)})#λ, Unit]
defined type alias FreeMonoid

scala> def cons[A](a: A): FreeMonoid[A] = Free.Suspend[({type λ[+α] = (A,α)})#λ, Unit]((a, Free.Return[({type λ[+α] = (A,α)})#λ, Unit](())))
cons: [A](a: A)FreeMonoid[A]

scala> cons(1)
res0: FreeMonoid[Int] = Suspend((1,Return(())))

scala> cons(1) >>= {_ => cons(2)}
res1: scalaz.Free[[+α](Int, α),Unit] = Gosub(Suspend((1,Return(()))),<function1>)
</scala>

この結果を処理する一例として標準の `List` に変換してみる:

<scala>
scala> def toList[A](list: FreeMonoid[A]): List[A] =
         list.resume.fold(
           { case (x: A, xs: FreeMonoid[A]) => x :: toList(xs) },
           { _ => Nil })

scala> toList(res1)
res4: List[Int] = List(1, 2)
</scala>

今日はここまで。
