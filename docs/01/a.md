  [tt]: http://learnyouahaskell.com/types-and-typeclasses
  [moott]: http://learnyouahaskell.com/making-our-own-types-and-typeclasses
  [z7]: $scalazTree$
  [z7docs]: $docBaseUrl$

### sbt

Here's build.sbt to test Scalaz $scalazVersion$:

```scala
scalaVersion := "$scalaVersion$"

val scalazVersion = "$scalazVersion$"

libraryDependencies ++= Seq(
  "org.scalaz" %% "scalaz-core" % scalazVersion,
  "org.scalaz" %% "scalaz-effect" % scalazVersion,
  "org.scalaz" %% "scalaz-typelevel" % scalazVersion,
  "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion % "test"
)

scalacOptions += "-feature"

initialCommands in console := "import scalaz._, Scalaz._"
```

All you have to do now is open the REPL using sbt 0.13.0:

```scala
\$ sbt console
...
[info] downloading http://repo1.maven.org/maven2/org/scalaz/scalaz-core_2.10/7.0.5/scalaz-core_2.10-7.0.5.jar ...
import scalaz._
import Scalaz._
Welcome to Scala version 2.10.3 (Java HotSpot(TM) 64-Bit Server VM, Java 1.6.0_51).
Type in expressions to have them evaluated.
Type :help for more information.

scala>
```

There's also [API docs][z7docs] generated for Scalaz $scalazVersion$.
