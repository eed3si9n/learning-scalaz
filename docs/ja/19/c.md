
### 同型射

CM:

> **定義**: ある射 *f: A => B* に対して *g ∘ f = 1<sub>A</sub>* と *f ∘ g = 1<sub>B</sub>* の両方を満たす射 *g: B => A* が存在するとき、f を同型射 (isomorphism) または可逆な射 (invertible arrow) であるという。
> また、1つでも同型射 *f: A => B* が存在するとき、2つの対象 *A* と *B* は**同型** (isomorphic) であるという。

Scalaz ではこれを `Isomorphism` 内で定義される trait を使って表す:

```scala
sealed abstract class Isomorphisms extends IsomorphismsLow0{
  /**Isomorphism for arrows of kind * -> * -> * */
  trait Iso[Arr[_, _], A, B] {
    self =>
    def to: Arr[A, B]
    def from: Arr[B, A]
  }

  /**Set isomorphism */
  type IsoSet[A, B] = Iso[Function1, A, B]

  /**Alias for IsoSet */
  type <=>[A, B] = IsoSet[A, B]
}

object Isomorphism extends Isomorphisms
```

より高いカインドの同型射も含むが、今のところは `IsoSet` で十分だ。

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait Family {}
case object Mother extends Family {}
case object Father extends Family {}
case object Child extends Family {}

sealed trait Relic {}
case object Feather extends Relic {}
case object Stone extends Relic {}
case object Flower extends Relic {}

import Isomorphism.<=>
val isoFamilyRelic = new (Family <=> Relic) {
  val to: Family => Relic = {
    case Mother => Feather
    case Father => Stone
    case Child  => Flower
  }
  val from: Relic => Family = {
    case Feather => Mother
    case Stone   => Father
    case Flower  => Child
  }
}

isoFamilyRelic: scalaz.Isomorphism.<=>[Family,Relic]{val to: Family => Relic; val from: Relic => Family} = $anon$1@12e3914c
```

Scalaz に同型射を見つけたのは心強い。多分僕たちが正しい方向に向かっているということだと思う。

> **記法**: もし *f: A => B* に (一意に定まる) **逆射** (inverse) があるとき、*f* の逆射は *f<sup>-1</sup>* と表記する。(読みは「f インバース」または「f の逆射」)

上の `isoFamilyRelic` が定義を満たすかを `arrowEqualsProp` で確かめることができる:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

implicit val familyEqual = Equal.equalA[Family]
implicit val relicEqual = Equal.equalA[Relic]
implicit val arbFamily: Arbitrary[Family] = Arbitrary {
  Gen.oneOf(Mother, Father, Child)
}
implicit val arbRelic: Arbitrary[Relic] = Arbitrary {
  Gen.oneOf(Feather, Stone, Flower)
}

// Exiting paste mode, now interpreting.

scala> arrowEqualsProp(isoFamilyRelic.from compose isoFamilyRelic.to, identity[Family] _)
res22: org.scalacheck.Prop = Prop

scala> res22.check
+ OK, passed 100 tests.

scala> arrowEqualsProp(isoFamilyRelic.to compose isoFamilyRelic.from, identity[Relic] _)
res24: org.scalacheck.Prop = Prop

scala> res24.check
+ OK, passed 100 tests.
```
