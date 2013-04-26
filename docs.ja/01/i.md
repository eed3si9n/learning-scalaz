---
out: typeclass102.html
---

  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: https://github.com/scalaz/scalaz/tree/scalaz-seven
  [start]: http://halcat0x15a.github.com/slide/start_scalaz/out/#4
  [z7docs]: http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/

### 型クラス中級講座

Haskell の文法に関しては飛ばして第8章の[型や型クラスを自分で作ろう][moott] まで行こう (本を持っている人は第7章)。

### 信号の型クラス

```haskell
data TrafficLight = Red | Yellow | Green
```

これを Scala で書くと:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait TrafficLight
case object Red extends TrafficLight
case object Yellow extends TrafficLight
case object Green extends TrafficLight
```

これに `Equal` のインスタンスを定義する。

```scala
scala> implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal\$\$anon\$7@2457733b
```

使えるかな?

```scala
scala> Red === Yellow
<console>:18: error: could not find implicit value for parameter F0: scalaz.Equal[Product with Serializable with TrafficLight]
              Red === Yellow
```

`Equal` が不変 (invariant) なサブタイプ `Equal[F]` を持つせいで、`Equal[TrafficLight]` が検知されないみたいだ。`TrafficLight` を case class にして `Red` と `Yellow` が同じ型を持つようになるけど、厳密なパターンマッチングができなくなる。#ダメじゃん

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

case class TrafficLight(name: String)
val red = TrafficLight("red")
val yellow = TrafficLight("yellow")
val green = TrafficLight("green")
implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
red === yellow

// Exiting paste mode, now interpreting.

defined class TrafficLight
red: TrafficLight = TrafficLight(red)
yellow: TrafficLight = TrafficLight(yellow)
green: TrafficLight = TrafficLight(green)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal\$\$anon\$7@42988fee
res3: Boolean = false
```
