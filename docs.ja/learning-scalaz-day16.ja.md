  [day15]: http://eed3si9n.com/ja/learning-scalaz-day15

<div class="floatingimage">
<img src="http://eed3si9n.com/images/mementobw.jpg">
</div>

[昨日][day15]は関数のようなものを抽象化する方法としての `Arrow`、それから型クラスのメタインスタンスを提供する方法としての `Unapply` をみた。また、applicative の実験として並行合成をサポートする `XProduct` も実装した。

### Memo

関数が純粋だからといってその計算量が安いとは限らない。例えば、全ての 8文字の ASCII 文字列の順列に対する SHA-1 ハッシュのリストを求めるとする。タブ文字を抜くと ASCII には 95 の表示可能な文字があるので、繰り上げて 100 とする。`100 ^ 8` は `10 ^ 16` だ。たとえ秒間 1000 ハッシュ処理できたとしても `10 ^ 13` 秒、つまり 316888年かかる。

RAM に少し余裕があれば、計算結果をキャッシュすることで高価な計算とスペースをトレードすることができる。これはメモ化と呼ばれる。以下が [`Memo`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Memo.scala) のコントラクトだ:

<scala>
sealed trait Memo[@specialized(Int) K, @specialized(Int, Long, Double) V] {
  def apply(z: K => V): K => V
}
</scala>

潜在的に高価な関数をインプットに渡して、同様に振る舞うけども結果をキャッシュする関数を返してもらう。`Memo` object の下に `Memo.mutableHashMapMemo[K, V]`、`Memo.weakHashMapMemo[K, V]`、や `Memo.arrayMemo[V]` などのいくつかの `Memo` のデフォルト実装がある。

一般的に、これらの最適化のテクニックは気をつけるべきだ。まず、全体の性能をプロファイルして実際に時間を節約できるのか確認するべきだし、スペースとのトレードオフも永遠に増大し続けないか解析したほうがいい。

[Memoization tutorial](http://www.haskell.org/haskellwiki/Memoization) にあるフィボナッチ数の例を実装してみよう:

<scala>
scala> val slowFib: Int => Int = {
         case 0 => 0
         case 1 => 1
         case n => slowFib(n - 2) + slowFib(n - 1)
       }
slowFib: Int => Int = <function1>

scala> slowFib(30)
res0: Int = 832040

scala> slowFib(40)
res1: Int = 102334155

scala> slowFib(45)
res2: Int = 1134903170
</scala>

`showFib(45)` は返ってくるのに少し時間がかかった。次がメモ化版:

<scala>
scala> val memoizedFib: Int => Int = Memo.mutableHashMapMemo {
         case 0 => 0
         case 1 => 1
         case n => memoizedFib(n - 2) + memoizedFib(n - 1)
       }
memoizedFib: Int => Int = <function1>

scala> memoizedFib(30)
res12: Int = 832040

scala> memoizedFib(40)
res13: Int = 102334155

scala> memoizedFib(45)
res14: Int = 1134903170
</scala>

結果が即座に返ってくるようになった。便利なのはメモ化した関数を作る側も使う側もあまり意識せずにできることだ。Adam Rosien さんも [Scalaz "For the Rest of Us" talk](https://github.com/arosien/scalaz-base-talk-201208) ([動画](http://www.youtube.com/watch?v=kcfIH3GYXMI)) でこの点を言っている。

### 関数型プログラミング

関数型プログラミングとは何だろう? [Rúnar Óli さん](http://twitter.com/runarorama) はこう[定義している](http://apocalisp.wordpress.com/2011/01/10/functional-programming-for-beginners/):

> 関数を使ったプログラミング。

関数とは何だろう?

> `f: A => B`
> 型が `A` の全ての値を、**唯一の**型が `B` の値に関連付け
> 他には何もしない。

この「他には何もしない」という部分を説明するために、以下のように参照透過性という概念を導入する:

> 式 `e` は、プログラムの観測可能な結果に影響を与えることなく使われている全ての `e` をその値に置き換えることができるとき参照透過だ。

この概念を使うと、関数型プログラミングとは参照的に透過な式の木を組み上げていくことだと考えることができる。メモ化はこの参照透過性を利用した方法の 1つだ。

### エフェクトシステム

[Lazy Functional State Threads](http://citeseerx.ist.psu.edu/viewdoc/download?doi=10.1.1.144.2237&rep=rep1&type=pdf) において John Launchbury さんと Simon Peyton-Jones さん曰く:

> Based on earlier work on monads, we present a way of securely encapsulating stateful computations that manipulate multiple, named, mutable objects, in the context of a non-strict purely-functional language.

Scala には `var` があるので一見すると不必要にも思われるけど、stateful な計算をカプセル化するという考え方は役に立つことがある。並列に実行される計算など特殊な状況下では、状態が共有されないかもしくは慎重に共有されているかどうかが正誤を分ける。

### ST

この論文で説明される `ST` に対応するものとして Scalaz には `ST` モナドがある。Rúnar さんの [Towards an Effect System in Scala, Part 1: ST Monad](http://apocalisp.wordpress.com/2011/03/20/towards-an-effect-system-in-scala-part-1/) も参照。以下が [`ST`](https://github.com/scalaz/scalaz/blob/scalaz-seven/effect/src/main/scala/scalaz/effect/ST.scala) のコントラクトだ:

<scala>
sealed trait ST[S, A] {
  private[effect] def apply(s: World[S]): (World[S], A)
}
</scala>

`State` モナドに似ているけども、違いは状態が可変で上書きされていることと、その引き換えとして状態が外から観測できないことにある。

### STRef

LFST:

> What, then is a "state"? Part of every state is a finite mapping from *reference* to values. ... A reference can be thought of as the name of (or address of) a *variable*, an updatable location in the state capable of holding a value.

`STRef` は `ST` モナドのコンテキストの内部でしか使えない可変変数だ。`ST.newVar[A]` によって作られ以下の演算をサポートする:

<scala>
sealed trait STRef[S, A] {
  protected var value: A

  /**Reads the value pointed at by this reference. */
  def read: ST[S, A] = returnST(value)
  /**Modifies the value at this reference with the given function. */
  def mod[B](f: A => A): ST[S, STRef[S, A]] = ...
  /**Associates this reference with the given value. */
  def write(a: => A): ST[S, STRef[S, A]] = ...
  /**Synonym for write*/
  def |=(a: => A): ST[S, STRef[S, A]] = ...
  /**Swap the value at this reference with the value at another. */
  def swap(that: STRef[S, A]): ST[S, Unit] = ...
}
</scala>

自家版の Scalaz 7 を使う:

<scala>
$ sbt
scalaz> project effect
scalaz-effect> console
[info] Compiling 2 Scala sources to /Users/eed3si9n/work/scalaz-seven/effect/target/scala-2.9.2/classes...
[info] Starting scala interpreter...
[info]

scala> import scalaz._, Scalaz._, effect._, ST._
import scalaz._
import Scalaz._
import effect._
import ST._

scala> def e1[S] = for {
         x <- newVar[S](0)
         r <- x mod {_ + 1}
       } yield x
e1: [S]=> scalaz.effect.ST[S,scalaz.effect.STRef[S,Int]]

scala> def e2[S]: ST[S, Int] = for {
         x <- e1[S]
         r <- x.read
       } yield r 
e2: [S]=> scalaz.effect.ST[S,Int]

scala> type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]
defined type alias ForallST

scala> runST(new ForallST[Int] { def apply[S] = e2[S] })
res5: Int = 1
</scala>

Rúnar さんのブログに [Paul Chiusano さん (@pchiusano)](http://twitter.com/pchiusano) が皆が思っていることを言っている:

> 僕はこれの Scala での効用について決めかねているんだけど - わざと反対の立場をとってるんだけど - もし (例えば quicksort) なんらかのアルゴリズムを実装するためにローカルで可変状態が必要なら、関数に渡されたものさえ上書き変更しなければいいだけだ。これを正しくやったとコンパイラをわざわざ説得する意義はある? 別にここでコンパイラの助けを借りなくてもいいと思う。

30分後に帰ってきて、自分の問に答えている:

> もし僕が命令形の quicksort を書いているなら、インプットの列を配列にコピーしてソートの最中に上書き変更して、結果をソートされた配列にたいする不変な列のビューとして返すだろう。STRef を使うと、可変配列に対する STRef を受け取ってコピーを一切避けることができる。さらに、僕の命令形のアクションは第一級となるのでいつものコンビネータを使って合成することができるようになる。

これは面白い点だ。可変状態が漏洩しないことが保証されているため、データのコピーをせずに可変状態の変化を連鎖して合成することができる。可変状態が必要な場合の多くは配列が必要な場合が多い。そのため `STArray` という配列へのラッパーがある:

<scala>
sealed trait STArray[S, A] {
  val size: Int
  val z: A
  private val value: Array[A] = Array.fill(size)(z)
  /**Reads the value at the given index. */
  def read(i: Int): ST[S, A] = returnST(value(i))
  /**Writes the given value to the array, at the given offset. */
  def write(i: Int, a: A): ST[S, STArray[S, A]] = ...
  /**Turns a mutable array into an immutable one which is safe to return. */
  def freeze: ST[S, ImmutableArray[A]] = ...
  /**Fill this array from the given association list. */
  def fill[B](f: (A, B) => A, xs: Traversable[(Int, B)]): ST[S, Unit] = ...
  /**Combine the given value with the value at the given index, using the given function. */
  def update[B](f: (A, B) => A, i: Int, v: B) = ...
}
</scala>

これは `ST.newArr(size: Int, z: A` を用いて作られる。エラトステネスのふるいを使って 1000 以下の全ての素数を計算してみよう...

### 速報

`STArray` にバグを見つけたので、さっさと直すことにする:

<code>
$ git pull --rebase
Current branch scalaz-seven is up to date.
$ git branch topic/starrayfix
$ git co topic/starrayfix
Switched to branch 'topic/starrayfix'
</code>

`ST` にスペックが無いので、バグを再現するためにもスペックを書き起こすことにする。これで誰かが僕の修正を戻してもバグが捕獲できる。

<scala>
package scalaz
package effect

import std.AllInstances._
import ST._

class STTest extends Spec {
  type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]

  "STRef" in {
    def e1[S] = for {
      x <- newVar[S](0)
      r <- x mod {_ + 1}
    } yield x
    def e2[S]: ST[S, Int] = for {
      x <- e1[S]
      r <- x.read
    } yield r
    runST(new ForallST[Int] { def apply[S] = e2[S] }) must be_===(1)
  }

  "STArray" in {
    def e1[S] = for {
      arr <- newArr[S, Boolean](3, true)
      _ <- arr.write(0, false)
      r <- arr.freeze
    } yield r
    runST(new ForallST[ImmutableArray[Boolean]] { def apply[S] = e1[S] }).toList must be_===(
      List(false, true, true))
  }
}
</scala>

これが結果だ:

<code>
[info] STTest
[info] 
[info] + STRef
[error] ! STArray
[error]   NullPointerException: null (ArrayBuilder.scala:37)
[error] scala.collection.mutable.ArrayBuilder$.make(ArrayBuilder.scala:37)
[error] scala.Array$.newBuilder(Array.scala:52)
[error] scala.Array$.fill(Array.scala:235)
[error] scalaz.effect.STArray$class.$init$(ST.scala:71)
...
</code>

Scala で NullPointerException?! これは以下の `STArray` のコードからきている:

<scala>
sealed trait STArray[S, A] {
  val size: Int
  val z: A
  implicit val manifest: Manifest[A]

  private val value: Array[A] = Array.fill(size)(z)
  ...
}
...
trait STArrayFunctions {
  def stArray[S, A](s: Int, a: A)(implicit m: Manifest[A]): STArray[S, A] = new STArray[S, A] {
    val size = s
    val z = a
    implicit val manifest = m
  }
}
</scala>

見えたかな? Paulp さんがこれの [FAQ](https://github.com/paulp/scala-faq/wiki/Initialization-Order) を書いている。`value` が未初期化の `size` と `z` を使って初期化されている。以下が僕の加えた修正:

<scala>
sealed trait STArray[S, A] {
  def size: Int
  def z: A
  implicit def manifest: Manifest[A]

  private lazy val value: Array[A] = Array.fill(size)(z)
  ...
}
</scala>

これでテストは通過したので、push して [pull request](https://github.com/scalaz/scalaz/pull/155) を送る。

### Back to the usual programming

[エラトステネスのふるい](http://en.wikipedia.org/wiki/Sieve_of_Eratosthenes#Implementation) は素数を計算する簡単なアルゴリズムだ。

<scala>
scala> import scalaz._, Scalaz._, effect._, ST._
import scalaz._
import Scalaz._
import effect._
import ST._

scala> def mapM[A, S, B](xs: List[A])(f: A => ST[S, B]): ST[S, List[B]] =
         Monad[({type λ[α] = ST[S, α]})#λ].sequence(xs map f)
mapM: [A, S, B](xs: List[A])(f: A => scalaz.effect.ST[S,B])scalaz.effect.ST[S,List[B]]

scala> def sieve[S](n: Int) = for {
         arr <- newArr[S, Boolean](n + 1, true)
         _ <- arr.write(0, false)
         _ <- arr.write(1, false)
         val nsq = (math.sqrt(n.toDouble).toInt + 1)
         _ <- mapM (1 |-> nsq) { i =>
           for {
             x <- arr.read(i)
             _ <-
               if (x) mapM (i * i |--> (i, n)) { j => arr.write(j, false) }
               else returnST[S, List[Boolean]] {Nil}
           } yield ()
         }
         r <- arr.freeze
       } yield r
sieve: [S](n: Int)scalaz.effect.ST[S,scalaz.ImmutableArray[Boolean]]

scala> type ForallST[A] = Forall[({type λ[S] = ST[S, A]})#λ]
defined type alias ForallST

scala> def prime(n: Int) =
         runST(new ForallST[ImmutableArray[Boolean]] { def apply[S] = sieve[S](n) }).toArray.
         zipWithIndex collect { case (true, x) => x }
prime: (n: Int)Array[Int]

scala> prime(1000)
res21: Array[Int] = Array(2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101, 103, 107, 109, 113, 127, 131, 137, 139, 149, 151, 157, 163, 167, 173, 179, 181, 191, 193, 197, 199, 211, 223, 227, 229, 233, 239, 241, 251, 257, 263, 269, 271, 277, 281, 283, 293, 307, 311, 313, 317, 331, 337, 347, 349, 353, 359, 367, 373, 379, 383, 389, 397, 401, 409, 419, 421, 431, 433, 439, 443, 449, 457, 461, 463, 467, 479, 487, 491, 499, 503, 509, 521, 523, 541, 547, 557, 563, 569, 571, 577, 587, 593, 599, 601, 607, 613, 617, 619, 631, 641, 643, 647, 653, 659, 661, 673, 677, 683, 691, 701, 709, 719, 727, 733, 739, 743, 751, 757, 761, 769, 773, 787, 797, 809, 811, 821, 823, 827, 829, 839, 853, 857, 859, 863, 877, 881, 883, 887, 907, 911, 919, 929, 937, 941, ...
</scala>

[最初の 1000個の素数のリスト](http://primes.utm.edu/lists/small/1000.txt)によると、結果は大丈夫みたいだ。`STArray` の反復を理解するのが一番難しかった。ここでは `ST[S, _]` のコンテキスト内にいるから、ループの結果も ST モナドである必要がある。リストを投射して配列に書き込むとそれは `List[ST[S, Unit]]` を返してしまう。

`ST[S, B]` へのモナディック関数を受け取ってモナドを裏返して `ST[S, List[B]]` を返す `mapM` を実装した。`sequence` と基本的には同じだけど、こっちの方が感覚的に理解しやすいと思う。`var` を使うのと比べると苦労無しとは言い難いけど、可変のコンテキストを渡せるのは役に立つかもしれない。

続きはまた後で。
