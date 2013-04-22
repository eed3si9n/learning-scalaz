  [day10]: http://eed3si9n.com/ja/learning-scalaz-day10

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-6278bw.jpg">
<div class="credit">Darren Hester for openphoto.net</div>
</div>

[昨日][day10]はコンフィギュレーションを抽象化する方法として `Reader` をみた後、モナド変換子を紹介した。

今日はレンズを見てみよう。色んな人がレンズの話をして盛り上がってきてるトピックだし、使われるシナリオもはっきりしてるみたいだ。

### 進め! 亀

今年の Scalathon で [Seth Tisue さん (@SethTisue)](https://twitter.com/SethTisue) が [shapeless の Lens の話](http://scalathon.org/2012/presentations/lenses.pdf)をした。残念ながら僕は聞けなかったけど、使われている例は借りさせてもらう。

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

ここで不変性を壊さずに亀を前進させたい。

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

中に入ったデータ構造を更新するには入れ子で `copy` を呼ばなくてはいけない。Seth 氏の話からまた借りると:

<scala>
// 命令型
a.b.c.d.e += 1

// 関数型
a.copy(
  b = a.b.copy(
    c = a.b.c.copy(
      d = a.b.c.d.copy(
        e = a.b.c.d.e + 1
))))
</scala>

この余計な `copy` の呼び出しを何とかしたい。

### Lens

Scalaz 7 の `Lens` をみてみる:

<scala>
  type Lens[A, B] = LensT[Id, A, B]

  object Lens extends LensTFunctions with LensTInstances {
    def apply[A, B](r: A => Store[B, A]): Lens[A, B] =
      lens(r)
  }
</scala>

他の多くの型クラス同様 `Lens` は `LensT[Id, A, B]` の型エイリアスだ。

### LensT

[`LensT`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Lens.scala) はこうなっている:

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

`Store` って何だろう?

<scala>
  type Store[A, B] = StoreT[Id, A, B]
  // flipped
  type |-->[A, B] = Store[B, A]
  object Store {
    def apply[A, B](f: A => B, a: A): Store[A, B] = StoreT.store(a)(f)
  }
</scala>

とりあえず setter (`A => B => A`) と getter (`A => B`) のラッパーらしい。

### Lens を使う

`turtlePosition` と `pointX` を定義してみよう:

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

次に `Lens` で導入される演算子を利用することができる。`Kleisli` でみたモナディック関数の合成同様に、`LensT` も `compose` (シンボルを使ったエイリアスは `<=<`) と `andThen` (シンボルを使ったエイリアスは `>=>`) を実装する。個人的には `>=>` の見た目が良いと思うので、これを使って `turtleX` を定義する:

<scala>
scala> val turtleX = turtlePosition >=> pointX
turtleX: scalaz.LensT[scalaz.Id.Id,Turtle,Double] = scalaz.LensTFunctions$$anon$5@11b35365
</scala>

`Turtle` から `Double` に向かっているわけだから、型は理にかなっている。`get` メソッドを使って値を取得できる:

<scala>
scala> val t0 = Turtle(Point(2.0, 3.0), 0.0,
                  Color(255.toByte, 255.toByte, 255.toByte))
t0: Turtle = Turtle(Point(2.0,3.0),0.0,Color(-1,-1,-1))

scala> turtleX.get(t0)
res16: scalaz.Id.Id[Double] = 2.0
</scala>

成功だ! `set` メソッドを使って新たな値を設定すると新たな `Turtle` が返ってくる:

<scala>
scala> turtleX.set(t0, 5.0)
res17: scalaz.Id.Id[Turtle] = Turtle(Point(5.0,3.0),0.0,Color(-1,-1,-1))
</scala>

これもうまくいった。値を `get` して、なんらかの関数に適用した後、結果を `set` したい場合はどうすればいいだろう? `mod` がそれを行う:

<scala>
scala> turtleX.mod(_ + 1.0, t0)
res19: scalaz.Id.Id[Turtle] = Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1))
</scala>

`mod` のシンボルを使ったカリー化版として `=>=` がある。これは `Turtle => Turtle` 関数を生成する:

<scala>
scala> val incX = turtleX =>= {_ + 1.0}
incX: Turtle => scalaz.Id.Id[Turtle] = <function1>

scala> incX(t0)
res26: scalaz.Id.Id[Turtle] = Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1))
</scala>

これで内部値の変化を事前に記述して、最後に実際の値を渡すことができた。これは何かに似てない?

### State モナドとしての Lens

これは状態遷移だと思う。実際、`Lens` と `State` は両方とも不変データ構造を使いながら命令形プログラミングを真似ているし相性がいいと思う。`incX` をこう書くこともできる:

<scala>
scala> val incX = for {
         x <- turtleX %= {_ + 1.0}
       } yield x
incX: scalaz.StateT[scalaz.Id.Id,Turtle,Double] = scalaz.StateT$$anon$7@38e61ffa

scala> incX(t0)
res28: (Turtle, Double) = (Turtle(Point(3.0,3.0),0.0,Color(-1,-1,-1)),3.0)
</scala>

`%=` メソッドは `Double => Double` 関数を受け取って、その変化を表す `State` モナドを返す。

`turtleHeading` と `turtleY` も作ろう:

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

これはボイラープレートっぽいので嬉しくない。だけど、これで亀を前進できる! 一般的な `%=` の代わりに、Scalaz は `Numeric` な Lens に対して `+=` などの糖衣構文も提供する。具体例で説明する:

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

これで `copy(position = ...)` を一回も使わずに `forward` 関数を実装できた。これは便利だけど、ここまで来るのに準備も色々必要だったから、それはトレードオフだと言える。`Lens` は他にも多くのメソッドを定義するけど以上で十分使い始められると思う。並べて見てみる:

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

### Lens 則

Seth さん曰く:

> Lens 則は常識的な感覚
>
> (0. 2度 get しても、同じ答が得られる)
> 1. get して、それを set しても何も変わらない。
> 2. set して、それを get すると、set したものが得られる。
> 3. 2度 set して、get すると、2度目に set したものが得られる。

確かに。常識的な感覚だ。Scalaz はコードでこれを表現する:

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

任意の亀を定義すれば `turtleX` が大丈夫かチェックできる。これは省くけど、Lens 則を破るような変な Lens はくれぐれも作らないように。

### リンク

Jordan West さんによる [An Introduction to Lenses in Scalaz](http://blog.stackmob.com/2012/02/an-introduction-to-lenses-in-scalaz/) という記事があって、飛ばし読みした感じだと Scalaz 6 っぽい。

Edward Kmett さんが Boston Area Scala Enthusiasts (BASE) で発表した [Lenses: A Functional Imperative](http://www.youtube.com/watch?v=efv0SQNde5Q) のビデオもある。

最後に、Gerolf Seitz さんによる Lens を生成するコンパイラプラグイン [gseitz/Lensed](https://github.com/gseitz/Lensed) がある。このプロジェクトはまだ実験段階みたいだけど、手で書くかわりにマクロとかコンパイラが Lens を生成してくれる可能性を示している。

また続きは後で。
