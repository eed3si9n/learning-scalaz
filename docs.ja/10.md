  [day9]: http://eed3si9n.com/ja/learning-scalaz-day9

[9日目][day9]は `TreeLoc` や `Zipper` を使った不変データ構造の更新する方法をみた。また、`Id`、`Index`、`Length` などの型クラスもみた。Learn You a Haskell for Great Good を終えてしまったので、今後は自分でトピックを考えなければいけない。

Scalaz 7 で何度も見て気になっている概念にモナド変換子というのがあるので、何なのかみてみる。幸いなことに、Haskell の良書でオンライン版も公開されている本がもう 1冊ある。

### モナド変換子

[Real World Haskell―実戦で学ぶ関数型言語プログラミング](http://www.amazon.co.jp/dp/4873114233) の原書の [Real World Haskell](http://book.realworldhaskell.org/read/monad-transformers.html) 曰く:

> It would be ideal if we could somehow take the standard `State` monad and add failure handling to it, without resorting to the wholesale construction of custom monads by hand. The standard monads in the `mtl` library don't allow us to combine them. Instead, the library provides a set of *monad transformers* to achieve the same result.
>
> A monad transformer is similar to a regular monad, but it's not a standalone entity: instead, it modifies the behaviour of an underlying monad. 

### Reader、再三

`Reader` モナドの例を Scala にまず翻訳してみる:

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

`Reader` のポイントはコンフィギュレーション情報を一度渡せばあとは明示的に渡して回さなくても皆が使うことができることにある。[Tony Morris さん(@dibblego)](https://twitter.com/dibblego) の [Configuration Without the Bugs and Gymnastics](http://vimeo.com/20674558) 参照。

### ReaderT

`Reader` のモナド変換子版である `ReaderT` を `Option` モナドの上に積んでみる。

<scala>
scala> :paste
// Entering paste mode (ctrl-D to finish)

type ReaderTOption[A, B] = ReaderT[Option, A, B]
object ReaderTOption extends KleisliFunctions with KleisliInstances {
  def apply[A, B](f: A => Option[B]): ReaderTOption[A, B] = kleisli(f)
}

// Exiting paste mode, now interpreting.
</scala>

`ReaderTOption` object を使って `ReaderTOption` 作れる:

<scala>
scala> def configure(key: String) = ReaderTOption[Map[String, String], String] {_.get(key)} 
configure: (key: String)ReaderTOption[Map[String,String],String]
</scala>

2日目に `Function1` を無限の投射として考えるみたいな事を言ったけど、これは `Map[String, String]` をリーダーとして使うから逆をやっていることになる。

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

見ての通り、`ReaderTOption` モナドは `Reader` の能力であるコンフィギュレーションを一回読むことと、`Option` の能力である失敗の表現を併せ持っている。

### 複数のモナド変換子を積み上げる

RWH:

> When we stack a monad transformer on a normal monad, the result is another monad. This suggests the possibility that we can again stack a monad transformer on top of our combined monad, to give a new monad, and in fact this is a common thing to do.

状態遷移を表す `StateT` を `ReaderTOption` の上に積んでみる。

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

これは分かりづらい。結局の所 `State` モナドは `S => (S, A)` をラッピングするものだから、パラメータ名はそれに合わせた。次に、`ReaderTOption` のカインドを `* -> *` (ただ 1つのパラメータを受け取る型コンストラクタ) に変える。

7日目でみた `State` を使った `Stack` を実装しよう。

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

`get` と `put` も書いたので、`for` 構文で書きなおすことができる:

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

これが `push`:

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

ついでに `stackManip` も移植する:

<scala>
scala> def stackManip: StateTReaderTOption[Config, Stack, Int] = for {
         _ <- push(3)
         a <- pop
         b <- pop
       } yield(b)
stackManip: StateTReaderTOption[Config,Stack,Int]
</scala>

実行してみよう。

<scala>
scala> stackManip(List(5, 8, 2, 1))(Map())
res12: Option[(Stack, Int)] = Some((List(8, 2, 1),5))
</scala>

とりあえず `State` 版と同じ機能までたどりつけた。`configure` を変更する:

<scala>
scala> def configure[S](key: String) = new StateTReaderTOption[Config, S, String] {
         def apply(s: S) = ReaderTOption[Config, (S, String)] { config: Config => config.get(key) map {(s, _)} }
       }
configure: [S](key: String)StateTReaderTOption[Config,S,String]
</scala>

これを使ってリードオンリーのコンフィギュレーションを使ったスタックの操作ができるようになった:

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

これで `StateT`、`ReaderT`、それと `Option` を同時に動かすことができた。僕が使い方を良く分かってないせいだと思うけど、`StateTReaderTOption` と `configure` を定義して前準備をするのがかなり面倒だった。使う側のコード (`stackManip`) はクリーンだから、お正月などの特別な機会があったら使ってみたい。

LYAHFGG 無しで前途多難な感じのスタートとなったけど、続きはまた後で。
