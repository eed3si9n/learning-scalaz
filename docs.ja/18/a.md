
### Func

Applicative 関数の合成を行うより良い方法を引き続き試してみて、`AppFunc` というラッパーを作った:

```scala
val f = AppFuncU { (x: Int) => x + 1 }
val g = AppFuncU { (x: Int) => List(x, 5) }
(f @&&& g) traverse List(1, 2, 3)
```

これを [pull request](https://github.com/scalaz/scalaz/pull/161) として送った後、Lars Hupel さん ([@larsr_h](https://twitter.com/larsr_h)) から typelevel モジュールを使って一般化した方がいいという提案があったので、`Func` に拡張した:

```scala
/**
 * Represents a function `A => F[B]` where `[F: TC]`.
 */
trait Func[F[_], TC[F[_]] <: Functor[F], A, B] {
  def runA(a: A): F[B]
  implicit def TC: KTypeClass[TC]
  implicit def F: TC[F]
  ...
}
```

これを使うと、`AppFunc` は `Func` の 2つ目の型パラメータに `Applicative` を入れた特殊形という扱いになる。Lars さんはさらに合成を `HList` に拡張したいみたいだけど、いつかこの機能が Scalaz 7 に入ると楽観的に見ている。
