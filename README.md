FS2 Throttler
====
[![CI Status](https://github.com/kovstas/fs2-throttler/workflows/Build/badge.svg)](https://github.com/kovstas/fs2-throttler/actions)
[![Maven Central](https://img.shields.io/maven-central/v/dev.kovstas/fs2-throttler_2.13.svg)](http://search.maven.org/#search%7Cga%7C1%7Cfs2-throttler)

Throttling for [FS2](https://fs2.io) based on the [Token bucket](https://en.wikipedia.org/wiki/Token_bucket) algorithm. 

This implementation supports:
- burst in the processing of elements
- calculating a cost for every element of the stream
- two throttle modes (Shaping / Enforcing)

## Install

```scala
libraryDependencies += "dev.kovstas" % "fs2-throttler" % Version
```

## Usage
```scala
import dev.kovstas.fs2throttler.Throttler._

stream.through(throttle(1, 1.second, Shaping))
```
More examples you can find in tests.
