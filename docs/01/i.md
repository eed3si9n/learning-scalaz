  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: https://github.com/scalaz/scalaz/tree/scalaz-seven
  [start]: http://halcat0x15a.github.com/slide/start_scalaz/out/#4
  [z7docs]: http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/

### typeclasses 102

I am now going to skip over to Chapter 8 [Making Our Own Types and Typeclasses][moott] (Chapter 7 if you have the book) since the chapters in between are mostly about Haskell syntax.

### A traffic light data type

```haskell
data TrafficLight = Red | Yellow | Green
```

In Scala this would be:

```scala
scala> :paste
// Entering paste mode (ctrl-D to finish)

sealed trait TrafficLight
case object Red extends TrafficLight
case object Yellow extends TrafficLight
case object Green extends TrafficLight
```

Now let's define an instance for `Equal`.

```scala
scala> implicit val TrafficLightEqual: Equal[TrafficLight] = Equal.equal(_ == _)
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@2457733b
```

Can I use it?

```scala
scala> Red === Yellow
<console>:18: error: could not find implicit value for parameter F0: scalaz.Equal[Product with Serializable with TrafficLight]
              Red === Yellow
```

So apparently `Equal[TrafficLight]` doesn't get picked up because `Equal` has nonvariant subtyping: `Equal[F]`. If I turned `TrafficLight` to a case class then `Red` and `Yellow` would have the same type, but then I lose the tight pattern matching from sealed #fail.

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
TrafficLightEqual: scalaz.Equal[TrafficLight] = scalaz.Equal$$anon$7@42988fee
res3: Boolean = false
```
