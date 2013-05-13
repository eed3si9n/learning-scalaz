
### Unapply

Scala コンパイラで苦労させられているのは、例えば `F[M[_, _]]` と `F[M[_]]` や `M[_]` と `F[M[_]]` など異なるカインド付けされた型の間での型推論が無いことだ。

具体的には、`Applicative[M[_]]` のインスタンスは `(* -> *) -> *` (ただ 1つの型を受け取る型コンストラクタを受け取る型コンストラクタ) だ。`Int => Int` を `Int => A` として扱うことで applicative として扱えることが知られている:

```scala
scala> Applicative[Function1[Int, Int]]
<console>:14: error: Int => Int takes no type parameters, expected: one
              Applicative[Function1[Int, Int]]
                          ^

scala> Applicative[({type l[A]=Function1[Int, A]})#l]
res14: scalaz.Applicative[[A]Int => A] = scalaz.std.FunctionInstances\$\$anon\$2@56ae78ac
```

これは `Validation` のような `M[_,_]` で面倒になる。Scalaz が手伝ってくれる 1つの方法として [`Unapply`](https://github.com/scalaz/scalaz/blob/scalaz-seven/core/src/main/scala/scalaz/Unapply.scala) というメタインスタンスがある。

```scala
trait Unapply[TC[_[_]], MA] {
  /** The type constructor */
  type M[_]
  /** The type that `M` was applied to */
  type A
  /** The instance of the type class */
  def TC: TC[M]
  /** Evidence that MA =:= M[A] */
  def apply(ma: MA): M[A]
}
```

`traverse` などの Scalaz のメソッドが `Applicative[M[_]]` を要請するとき、代わりに `Unapply[Applicative, X]` を要請できる。コンパイル時に Scalac は `Function1[Int, Int]` を `M[A]` に強制できないかをパラメータを固定したり、追加したり、もちろん既存の型クラスインスタンスを利用したりして暗黙の変換子を全て試す。

```scala
scala> implicitly[Unapply[Applicative, Function1[Int, Int]]]
res15: scalaz.Unapply[scalaz.Applicative,Int => Int] = scalaz.Unapply_0\$\$anon\$9@2e86566f
```

僕が昨日追加したのは型 `A` に偽の型コンストラクタをつけて `M[A]` に昇進させる方法だ。これによって `Int` を `Applicative` として扱いやすくなる。だけど、`TC0: TC[({type λ[α] = A0})#λ]` を暗黙に要請するから、どの型でも `Applicative` に昇進できるというわけではない。

```scala
scala> implicitly[Unapply[Applicative, Int]]
res0: scalaz.Unapply[scalaz.Applicative,Int] = scalaz.Unapply_3\$\$anon\$1@5179dc20

scala> implicitly[Unapply[Applicative, Any]]
<console>:14: error: Unable to unapply type `Any` into a type constructor of kind `M[_]` that is classified by the type class `scalaz.Applicative`
1) Check that the type class is defined by compiling `implicitly[scalaz.Applicative[<type constructor>]]`.
2) Review the implicits in object Unapply, which only cover common type 'shapes'
(implicit not found: scalaz.Unapply[scalaz.Applicative, Any])
              implicitly[Unapply[Applicative, Any]]
                        ^
```

動いた。これらの結果として以下のようなコードが少しきれいに書けるようになる:

```scala
scala> val failedTree: Tree[Validation[String, Int]] = 1.success[String].node(
         2.success[String].leaf, "boom".failure[Int].leaf)
failedTree: scalaz.Tree[scalaz.Validation[String,Int]] = <tree>

scala> failedTree.sequence[({type l[X]=Validation[String, X]})#l, Int]
res2: scalaz.Validation[java.lang.String,scalaz.Tree[Int]] = Failure(boom)
```

以下が `sequenceU` を用いたもの:

```scala
scala> failedTree.sequenceU
res3: scalaz.Validation[String,scalaz.Tree[Int]] = Failure(boom)
```

ブーム。
