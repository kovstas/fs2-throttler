FS2 Throttler
====
[![CI Status](https://github.com/kovstas/fs2-throttler/workflows/Build/badge.svg)](https://github.com/kovstas/fs2-throttler/actions)
[![Maven Central](https://img.shields.io/maven-central/v/dev.kovstas/fs2-throttler_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-throttler)

![Cats Friendly Badge](https://typelevel.org/cats/img/cats-badge-tiny.png)

Throttling for [FS2](https://fs2.io) based on the [Token bucket](https://en.wikipedia.org/wiki/Token_bucket) algorithm. 

This implementation supports:
- burst in the processing of elements
- calculates a cost for every element of the stream
- two throttle modes (Shaping / Enforcing)

## Install

Add the following to your `build.sbt` file:
```scala
libraryDependencies += "dev.kovstas" %% "fs2-throttler" % Version
```

## Usage

To use the throttler, import the throttle function and apply it to your stream:
```scala
import cats.effect.IO
import fs2.Stream
import scala.concurrent.duration._
import dev.kovstas.fs2throttler.Throttler._

val stream = Stream(1, 2, 3, 4, 5)

val shapedStream = stream.through(throttle(2, 1.second, Shaping))
val enforcedStream = stream.through(throttle(2, 1.second, Enforcing))

val costFunction: Int => Long = i => i.toLong
val throttledCostStream = stream.through(throttle(2, 1.second, Shaping, costFunction))
```
For more examples, please refer to the tests.