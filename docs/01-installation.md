# Installation

Add the following to build.sbt

```scala
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "io.github.finagle" %% "finagle-postgres" % "0.4.0-SNAPSHOT"
)
```

This brings in the latest snapshot version, which is `0.4.0-SNAPSHOT`.

Next, read about [Basic Usage](02-basic-usage.md)
