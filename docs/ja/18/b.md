  [1]: http://www.haskellforall.com/2012/06/you-could-have-invented-free-monads.html
  [2]: https://github.com/eed3si9n/learning-scalaz/blob/master/docs/ja/18/b.md

この記事は Rúnar の助言に基づいて大幅に手を加えた。古い版は github の[ソース][2]を参照してほしい。

### Free Monad

今日は、Gabriel Gonzalez の [Why free monads matter][1] を読みながら Free モナドをみていく:

> 構文木の本質を表す抽象体を考えてみよう。[中略] 僕らの toy 言語には 3つのコマンドしかない:

```
output b -- prints a "b" to the console
bell     -- rings the computer's bell
done     -- end of execution
```

> 次のコマンドが前のコマンドの子ノードであるような構文木としてあらわしてみる:

```haskell
data Toy b next =
    Output b next
  | Bell next
  | Done
```

とりあえずこれを素直に Scala に翻訳するとこうなる:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Toy[+A, +Next]
case class Output[A, Next](a: A, next: Next) extends Toy[A, Next]
case class Bell[Next](next: Next) extends Toy[Nothing, Next]
case class Done() extends Toy[Nothing, Nothing]

// Exiting paste mode, now interpreting.

scala> Output('A', Done())
res0: Output[Char,Done] = Output(A,Done())

scala> Bell(Output('A', Done()))
res1: Bell[Output[Char,Done]] = Bell(Output(A,Done()))
```

### CharToy

WFMM の DSL はアウトプット用のデータ型を型パラメータとして受け取るので、任意のアウトプット型を扱うことができる。上に `Toy` として示したように Scala も同じことができる。だけども、Scala の部分適用型の処理がヘボいため `Free` の説明としては不必要に複雑となってしまう。そのため、本稿では、以下のようにデータ型を `Char` に決め打ちしたものを使う:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  def output[Next](a: Char, next: Next): CharToy[Next] = CharOutput(a, next)
  def bell[Next](next: Next): CharToy[Next] = CharBell(next)
  def done: CharToy[Nothing] = CharDone()
}

// Exiting paste mode, now interpreting.

scala> import CharToy._
import CharToy._

scala> output('A', done)
res0: CharToy[CharToy[Nothing]] = CharOutput(A,CharDone())

scala> bell(output('A', done))
res1: CharToy[CharToy[CharToy[Nothing]]] = CharBell(CharOutput(A,CharDone()))
```

型を `CharToy` に統一するため、小文字の `output`、`bell`、`done` を加えた。

### Fix

WFMM:

> しかし残念なことに、コマンドを追加するたびに型が変わってしまうのでこれはうまくいかない。

`Fix` を定義しよう:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class Fix[F[_]](f: F[Fix[F]])
object Fix {
  def fix(toy: CharToy[Fix[CharToy]]) = Fix[CharToy](toy)
}

// Exiting paste mode, now interpreting.

scala> import Fix._
import Fix._

scala> fix(output('A', fix(done)))
res4: Fix[CharToy] = Fix(CharOutput(A,Fix(CharDone())))

scala> fix(bell(fix(output('A', fix(done)))))
res5: Fix[CharToy] = Fix(CharBell(Fix(CharOutput(A,Fix(CharDone())))))
```

ここでも `fix` を提供して型推論が動作するようにした。

### FixE

これに例外処理を加えた `FixE` も実装してみる。`throw` と `catch` は予約語なので、`throwy`、`catchy` という名前に変える:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait FixE[F[_], E]
object FixE {
  case class Fix[F[_], E](f: F[FixE[F, E]]) extends FixE[F, E]
  case class Throwy[F[_], E](e: E) extends FixE[F, E]   

  def fix[E](toy: CharToy[FixE[CharToy, E]]): FixE[CharToy, E] =
  　Fix[CharToy, E](toy)
  def throwy[F[_], E](e: E): FixE[F, E] = Throwy(e)
  def catchy[F[_]: Functor, E1, E2](ex: => FixE[F, E1])
  　　(f: E1 => FixE[F, E2]): FixE[F, E2] = ex match {
    case Fix(x)    => Fix[F, E2](Functor[F].map(x) {catchy(_)(f)})
    case Throwy(e) => f(e)
  }
}

// Exiting paste mode, now interpreting.
```

> これを実際に使うには Toy b が functor である必要があるので、型検査が通るまで色々試してみる (Functor則を満たす必要もある)。

`CharToy` の `Functor` はこんな感じになった:

```scala
scala> implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
         def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
           case o: CharOutput[A] => CharOutput(o.a, f(o.next))
           case b: CharBell[A]   => CharBell(f(b.next))
           case CharDone()       => CharDone()
         }
       }
charToyFunctor: scalaz.Functor[CharToy] = \$anon\$1@7bc135fe
```

これがサンプルの使用例だ:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

import FixE._
case class IncompleteException()
def subroutine = fix[IncompleteException](
  output('A', 
    throwy[CharToy, IncompleteException](IncompleteException())))
def program = catchy[CharToy, IncompleteException, Nothing](subroutine) { _ =>
  fix[Nothing](bell(fix[Nothing](done)))
}
```

型パラメータでゴテゴテになってるのはちょっと残念な感じだ。

### Free monads part 1

WFMM:

> 僕らの `FixE` は既に存在していて、それは Free モナドと呼ばれる:

```haskell
data Free f r = Free (f (Free f r)) | Pure r
```

> 名前の通り、これは自動的にモナドだ (ただし、`f` が Functor の場合)

```haskell
instance (Functor f) => Monad (Free f) where
    return = Pure
    (Free x) >>= f = Free (fmap (>>= f) x)
    (Pure r) >>= f = f r
```

これに対応する Scalaz でのデータ構造は `Free` と呼ばれる:

```scala
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
```

Scalaz 版では、`Free` コンストラクタは `Free.Suspend` と呼ばれ、`Pure` は `Free.Return` と呼ばれる。 `CharToy` コマンドを `Free` を使って再実装する:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
    def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
        case o: CharOutput[A] => CharOutput(o.a, f(o.next))
        case b: CharBell[A]   => CharBell(f(b.next))
        case CharDone()       => CharDone()
      }
    }

  def output(a: Char): Free[CharToy, Unit] =
    Free.Suspend(CharOutput(a, Free.Return[CharToy, Unit](())))
  def bell: Free[CharToy, Unit] =
    Free.Suspend(CharBell(Free.Return[CharToy, Unit](())))
  def done: Free[CharToy, Unit] = Free.Suspend(CharDone())
}

// Exiting paste mode, now interpreting.

defined trait CharToy
defined module CharToy
```

> これは、さすがに共通パターンを抽出できるはず。

`liftF` をつかったリファクタリングも行う。あと、`return` に相当するものが必要なので、`pointed` も定義する:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait CharToy[+Next]
object CharToy {
  case class CharOutput[Next](a: Char, next: Next) extends CharToy[Next]
  case class CharBell[Next](next: Next) extends CharToy[Next]
  case class CharDone() extends CharToy[Nothing]

  implicit val charToyFunctor: Functor[CharToy] = new Functor[CharToy] {
    def map[A, B](fa: CharToy[A])(f: A => B): CharToy[B] = fa match {
        case o: CharOutput[A] => CharOutput(o.a, f(o.next))
        case b: CharBell[A]   => CharBell(f(b.next))
        case CharDone()       => CharDone()
      }
    }
  private def liftF[F[+_]: Functor, R](command: F[R]): Free[F, R] =
    Free.Suspend[F, R](Functor[F].map(command) { Free.Return[F, R](_) })
  def output(a: Char): Free[CharToy, Unit] =
    liftF[CharToy, Unit](CharOutput(a, ()))
  def bell: Free[CharToy, Unit] = liftF[CharToy, Unit](CharBell(()))
  def done: Free[CharToy, Unit] = liftF[CharToy, Unit](CharDone())
  def pointed[A](a: A) = Free.Return[CharToy, A](a)
}

// Exiting paste mode, now interpreting.
```

コマンドのシーケンスはこんな感じになる:

```scala
scala> import CharToy._
import CharToy._

scala> val subroutine = output('A')
subroutine: scalaz.Free[CharToy,Unit] = Suspend(CharOutput(A,Return(())))

scala> val program = for {
         _ <- subroutine
         _ <- bell
         _ <- done
       } yield ()
program: scalaz.Free[CharToy,Unit] = Gosub(<function0>,<function1>)
```

> 面白くなってきた。「まだ評価されていないもの」に対する `do` 記法を得られることができた。これは純粋なデータだ。

次に、これが本当に純粋なデータであることを証明するために `showProgram` を定義する。WFMM は単純なパターンマッチングを使って `showProgram` を定義するけども、この `Free` はちょっとそのままでうまくいかない。`flatMap` の定義をみてほしい:

```scala
  final def flatMap[B](f: A => Free[S, B]): Free[S, B] = this match {
    case Gosub(a, g) => Gosub(a, (x: Any) => Gosub(g(x), f))
    case a           => Gosub(a, f)
  }
```

新しい `Return` や `Suspend` を計算する代わりに `Gosub` というデータ構造を作っている。この `Gosub` を評価して `\/` を返す `resume` メソッドがあるので、`showProgram` は以下のように実装できる:

```scala
scala> def showProgram[R: Show](p: Free[CharToy, R]): String =
         p.resume.fold({
           case CharOutput(a, next) =>
             "output " + Show[Char].shows(a) + "\n" + showProgram(next)
           case CharBell(next) =>
             "bell " + "\n" + showProgram(next)
           case CharDone() =>
             "done\n"
         },
         { r: R => "return " + Show[R].shows(r) + "\n" }) 
showProgram: [R](p: scalaz.Free[CharToy,R])(implicit evidence\$1: scalaz.Show[R])String

scala> showProgram(program)
res12: String = 
"output A
bell 
done
"
```

pretty printer はこうなる:

```scala
scala> def pretty[R: Show](p: Free[CharToy, R]) = print(showProgram(p))
pretty: [R](p: scalaz.Free[CharToy,R])(implicit evidence\$1: scalaz.Show[R])Unit

scala> pretty(output('A'))
output A
return ()
```

さて、真実の時だ。`Free` を使って生成したモナドはモナド則を満たしているだろうか?

```scala
scala> pretty(output('A'))
output A
return ()

scala> pretty(pointed('A') >>= output)
output A
return ()

scala> pretty(output('A') >>= pointed)
output A
return ()

scala> pretty((output('A') >> done) >> output('C'))
output A
done

scala> pretty(output('A') >> (done >> output('C')))
output A
done
```

うまくいった。`done` が abort的な意味論になっていることにも注目してほしい。

### Free monads part 2

WFMM:

```haskell
data Free f r = Free (f (Free f r)) | Pure r
data List a   = Cons  a (List a  )  | Nil
```

> 言い換えると、Free モナドは Functor のリストだと考えることができる。`Free` コンストラクタは `Cons` のように振る舞い Functor をリストの先頭に追加し、`Pure` コンストラクタは `Nil` のように振る舞い空のリストを表す (つまり Functor が無い状態だ)。

第三部。

### Free monads part 3

WFMM:

> Free モナドはインタプリタの良き友だ。Free モナドはインタプリタを限りなく「解放 (free) 」しつつも必要最低限のモナドの条件を満たしている。

逆に、プログラムを書いている側から見ると、Free モナドそのものは逐次化以外の何も提供しない。インタプリタが何らかの `run` 関数を提供して役に立つ機能が得られる。ポイントは、`Functor` を満たすデータ構造さえあれば `Free` が最小のモナドを自動的に提供してくれることだと思う。

もう一つの見方としては、`Free` は与えられたコンテナを使って構文木を作る方法を提供する。
