
  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: https://github.com/scalaz/scalaz/tree/scalaz-seven
  [start]: http://halcat0x15a.github.com/slide/start_scalaz/out/#4
  [z7docs]: http://halcat0x15a.github.com/scalaz/core/target/scala-2.9.2/api/

### sbt

以下が Scalaz 7 を試すための build.sbt だ。これは、ねこはる先生の講義で使われた[スライド][start]を少し更新したものだ:

```scala
scalaVersion := "2.10.1"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % "7.0.0",
  "org.scalaz" %% "scalaz-effect" % "7.0.0",
  "org.scalaz" %% "scalaz-typelevel" % "7.0.0",
  "org.scalaz" %% "scalaz-scalacheck-binding" % "7.0.0" % "test"
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"
```

あとは sbt 0.12.3 から REPL を開くだけだ:

```scala
\$ sbt console
...
[info] downloading http://repo1.maven.org/maven2/org/scalaz/scalaz-core_2.10/7.0.0/scalaz-core_2.10-7.0.0.jar ...
import scalaz._
import Scalaz._
Welcome to Scala version 2.10.1 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_45).
Type in expressions to have them evaluated.
Type :help for more information.

scala> 
```

氏が Scalaz 7.0.0 M1 から生成した [API ドキュメント][z7docs]もある。
