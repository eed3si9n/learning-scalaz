  [day13]: http://eed3si9n.com/learning-scalaz-day13

<div class="floatingimage">
<img src="http://eed3si9n.com/images/openphoto-11180bw.jpg">
<div class="credit">bman ojel for openphoto.net</div>
</div>

[昨日][day13]は `import scalaz._` と `Scalaz._` が何をスコープに取り込むかをみて、アラカルト形式の import の話もした。instance や syntax がどのように構成されているのかを知ることは、実は次のステップへの準備段階で、本当にやりたいのは Scalaz をハックすることだ。

### メーリングリスト

プロジェクトのハックを始める前に礼儀としてそのプロジェクトの [Google Group](https://groups.google.com/forum/#!forum/scalaz) に加入する。

### git clone

<code>
$ git clone -b scalaz-seven git://github.com/scalaz/scalaz.git scalaz-seven
</code>

上を実行すると `scalaz-seven` ブランチが `./scalaz-seven` ディレクトリにクローンされるはずだ。次に `.git/config` を以下のように編集した:

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

これで `origin` のかわりに `scalaz/scalaz` を `upstream` として参照できる。変更を追従するには以下を実行する:

<code>
$ git pull --rebase
Current branch scalaz-seven is up to date.
</code>

### sbt

次に sbt 0.12.0 を起動して、Scala バージョンを 2.10.0-M7 に設定して、`core` プロジェクトに切り替えてコンパイルを始める:

<code>
$ sbt
scalaz> ++ 2.10.0-M7
Setting version to 2.10.0-M7
[info] Set current project to scalaz (in build file:/Users/eed3si9n/work/scalaz-seven/)
scalaz> project core
[info] Set current project to scalaz-core (in build file:/Users/eed3si9n/work/scalaz-seven/)
scalaz-core> compile
</code>

これは数分かかると思う。このビルドがスナップショットのバージョンかを確認する:

<code>
scalaz-core> version
[info] 7.0-SNAPSHOT
</code>

ローカルでコンパイルされた Scalaz を試すには、いつも通り `console` を使って RELP に入る:

<code>
scalaz-core> console
[info] Starting scala interpreter...
[info] 
Welcome to Scala version 2.10.0-M7 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_33).
Type in expressions to have them evaluated.
Type :help for more information.

scala> [Ctrl + D to exit]
</code>

### Vector を入れる

ここ 2週間使ってみて気付いた点を直してみよう。例えば、`Vector` のインスタンスは `import Scalaz._` に入るべきだと思う。昨日 import に関して書いて記憶に新しいので楽勝だ。トピックブランチとして `topic/vectorinstance` を立てる:

<code>
$ git branch topic/vectorinstance
$ git co topic/vectorinstance
Switched to branch 'topic/vectorinstance'
</code>

`Vector` インスタンスが実際に `import Scalaz._` で読み込まれていないことを sbt console から確認しよう:

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

期待通り失敗した。

[`std.AllInstances`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/std/AllInstances.scala) を変更して `VectorInstances` をミックスインする:

<scala>
trait AllInstances
  extends AnyValInstances with FunctionInstances with ListInstances with MapInstances
  with OptionInstances with SetInstances with StringInstances with StreamInstances
  with TupleInstances with VectorInstances
  ...
</scala>

[`syntax.std.ToAllStdOps`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/syntax/std/ToAllStdOps.scala) も変更して `ToVectorOps` を追加する:

<scala>
trait ToAllStdOps
  extends ToBooleanOps with ToOptionOps with ToOptionIdOps with ToListOps with ToStreamOps with ToVectorOps
  ...
</scala>

これだけだ。REPL で使ってみる。

<scala>
scala> Vector(1, 2) >>= { x => Vector(x + 1)}
res0: scala.collection.immutable.Vector[Int] = Vector(2, 3)

scala> Vector(1, 2) filterM { x => Vector(true, false) }
res1: scala.collection.immutable.Vector[Vector[Int]] = Vector(Vector(1, 2), Vector(1), Vector(2), Vector())
</scala>

動いた。こういうことに関するテストは書かれていないみたいなので、テスト無しでいく。これは "include VectorInstances and ToVectorOps to import Scalaz._" としてコミットした。次に、github で scalaz プロジェクトをフォークする。

<code>
$ git remote add fork git@github.com:yourname/scalaz.git
$ git push fork topic/vectorinstance
...
 * [new branch]      topic/vectorinstance -> topic/vectorinstance
</code>

コメントと共に [pull request](https://github.com/scalaz/scalaz/pull/151) を投げたので、あとは向こう次第だ。次の機能の作業をするために `scalaz-seven` ブランチに巻き戻す必要がある。ローカルで新機能を試したいのでスナップショット用のブランチも作る。

### snapshot

<code>
$ git co scalaz-seven
Switched to branch 'scalaz-seven'
$ git branch snapshot
$ git co snapshot
$ git merge topic/vectorinstance
</code>

このブランチが Scalaz で遊ぶためのサンドボックスとなる。

### <*> operator

次は、`Apply` の `<*>` 演算子だけど、これは本当に M2 と Haskell の振る舞いに戻って欲しい。これは既にメーリングリストで[聞いていて](https://groups.google.com/forum/#!topic/scalaz/g0YPdgBeEAw)作者は元に戻す予定みたいなことを言っている。

<code>
$ git co scalaz-seven
Switched to branch 'scalaz-seven'
$ git branch topic/applyops
$ git co topic/applyops
Switched to branch 'topic/applyops'
</code>

これはテストファーストでやるべきだ。[`ApplyTest`](https://github.com/scalaz/scalaz/blob/9412258332e2dd42ecc82a363e39decb503eb2d5/tests/src/test/scala/scalaz/ApplyTest.scala) に例を加える:

<scala>
  "<*>" in {
    some(9) <*> some({(_: Int) + 3}) must be_===(some(12))
  }
</scala>

この build.scala で使われている specs は Scala 2.9.2 向けみたいだ。

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

`===` が使われていてコンパイルさえしない。良し。

`<*>` は [`ApplyOps`](https://github.com/scalaz/scalaz/blob/9412258332e2dd42ecc82a363e39decb503eb2d5/core/src/main/scala/scalaz/syntax/ApplySyntax.scala) にあるので、`F.ap` に戻す:

<scala>
  final def <*>[B](f: F[A => B]): F[B] = F.ap(self)(f)
</scala>

テストを再実行してみよう:

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

これは "roll back <*> as infix of ap" とコミットして、push する。

<code>
$ git push fork topic/applyops
...
 * [new branch]      topic/applyops -> topic/applyops
</code>

これも一言コメントを書いて [pull request](https://github.com/scalaz/scalaz/pull/152) を送る。`snapshot` ブランチにも取り込もう:

<code>
$ git co snapshot
$ git merge topic/applyops
</code>

これで変更した点を両方とも試すことができる。

### applicative 関数

これまでの変更は簡単な修正だった。ここから始まるのは applicative 関数の実験だ。

[The Essence of the Iterator Pattern](http://www.cs.ox.ac.uk/jeremy.gibbons/publications/iterator.pdf) は、applicative functor を組み合わせるという興味深いアイディアを提唱している。実際に行われているのは applicative functor の組み合わせ (`m ⊠ n`) だけじゃなくて、applicative 関数の組み合わせだ:

<haskell>
(⊗)::(Functor m,Functor n) ⇒ (a → m b) → (a → n b) → (a → (m ⊠ n) b)
(f ⊗ g) x = Prod (f x) (g x)
</haskell>

`Int` は `Monoid` で、全ての `Monoid` は applicative functor として扱え、それは monoidal applicative と呼ばれる。問題はこれを関数にすると `Int => Int` と区別がつかないけど、`Int => [α]Int` が必要なことだ。

僕の最初のアイディアは `Tags.Monoidal` という名前の型タグを使って以下のように書くことだった:

<scala>
scala> { (x: Int) => Tags.Monoidal(x + 1) }
</scala>

これは `[A:Monoid]` である全ての `A @@ Tags.Monoidal` を applicative として認識する必要がある。ここで僕はつまずいた。

次のアイディアは `Kleisli` のエイリアスとして `Monoidal` を宣言して、以下のコンパニオンを定義することだった:

<scala>
  object Monoidal {
    def apply[A: Monoid](f: A => A): Kleisli[({type λ[+α]=A})#λ, A, A] =
      Kleisli[({type λ[+α]=A})#λ, A, A](f)
  }
</scala>

これで monoidal 関数を以下のように書ける:

<scala>
scala> Monoidal { x: Int => x + 1 }
res4: scalaz.Kleisli[[+α]Int,Int,Int] = scalaz.KleisliFunctions$$anon$18@1a0ceb34
</scala>

だけど、コンパイラは `[+α]Int` から自動的に `Applicative` を検知してくれなかった:

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

これが悪名高い [SI-2712](https://issues.scala-lang.org/browse/SI-2712) なのだろうか? これで思ったのは、実際の型に変えてしまえばいいということだ:

<scala>
trait MonoidApplicative[F] extends Applicative[({type λ[α]=F})#λ] { self =>
  implicit def M: Monoid[F]
  def point[A](a: => A) = M.zero
  def ap[A, B](fa: => F)(f: => F) = M.append(f, fa)
  override def map[A, B](fa: F)(f: (A) => B) = fa
}
</scala>

これは `x + 1` を `MonoidApplicative` 変換しなければいけないのでうまくいかない。

次に試したのは `Unapply` だ:

<scala>
scala> List(1, 2, 3) traverseU {_ + 1}
<console>:14: error: Unable to unapply type `Int` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Int])
              List(1, 2, 3) traverseU {_ + 1}
                            ^
</scala>

これはうまくいくかもしれない。`Int` を [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala) の中で `({type λ[α]=Int})#λ` に展開するだけでいい:

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

試してみる:

<scala>
scala> List(1, 2, 3) traverseU {_ + 1}
res0: Int = 9
</scala>

実際にうまくいった! 組み合わせはどうだろう?

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

これは `res1` か `res2` が間違っているんじゃないかと思う。`res1` は僕が Haskell で確認した結果と同じものを返している。`Tuple2` も applicative だから、そこで予想外のことをやっているのかもしれない。僕の変更無しでも同じ振る舞いを確認できたので、テストを書く:

<scala>
    "traverse int function as monoidal applicative" in {
      val s: Int = List(1, 2, 3) traverseU {_ + 1}
      s must be_===(9)
    }
</scala>

走らせてみる:

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

`scalaz-seven` から `topic/unapplya` ブランチを立てる:

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

全テストが通過すれば、"adds implicit def unapplyA, which unpacks A into [a]A" としてコミットする。

<code>
$ git push fork topic/unapplya
...
 * [new branch]      topic/unapplya -> topic/unapplya
</code>

これも [pull request](https://github.com/scalaz/scalaz/pull/153) にして送る。これは、なかなか楽しかった。

続きはまた後で。
