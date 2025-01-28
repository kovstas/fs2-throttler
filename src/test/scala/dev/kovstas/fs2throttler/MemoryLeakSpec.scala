/*
 * Copyright (c) 2021 Stanislav Kovalenko
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dev.kovstas.fs2throttler

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import dev.kovstas.fs2throttler.Throttler.{Enforcing, Shaping}
import fs2.Stream
import munit.{FunSuite, TestOptions}

import java.lang.management.ManagementFactory
import java.nio.file.{Files, Path}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class MemoryLeakSpec extends FunSuite {

  override def munitIgnore: Boolean = Option(System.getenv("CI")).contains("true")

  override def munitFlakyOK = true

  case class LeakTestParams(
      warmupIterations: Int = 3,
      samplePeriod: FiniteDuration = 1.seconds,
      monitorPeriod: FiniteDuration = 10.seconds,
      limitTotalBytesIncreasePerSecond: Long = 700000,
      limitConsecutiveIncreases: Int = 10
  )

  private def heapUsed: IO[Long] =
    IO {
      val runtime = Runtime.getRuntime
      runtime.gc()
      val total = runtime.totalMemory()
      val free = runtime.freeMemory()
      total - free
    }

  protected def leakTest[O](
      name: TestOptions,
      params: LeakTestParams = LeakTestParams()
  )(stream: => Stream[IO, O]): Unit = leakTestF(name, params)(stream.compile.drain)

  protected def leakTestF(
      name: TestOptions,
      params: LeakTestParams = LeakTestParams()
  )(f: => IO[Unit]): Unit =
    test(name) {
      println(s"Running leak test ${name.name}")
      IO.race(
        f,
        IO.race(
          monitorHeap(
            params.warmupIterations,
            params.samplePeriod,
            params.limitTotalBytesIncreasePerSecond,
            params.limitConsecutiveIncreases
          ),
          IO.sleep(params.monitorPeriod)
        )
      ).map {
        case Left(_)            => ()
        case Right(Right(path)) => ()
        case Right(Left(path)) =>
          fail(s"leak detected - heap dump: $path")
      }.unsafeRunSync()
    }

  private def monitorHeap(
      warmupIterations: Int,
      samplePeriod: FiniteDuration,
      limitTotalBytesIncreasePerSecond: Long,
      limitConsecutiveIncreases: Int
  ): IO[Path] = {
    def warmup(iterationsLeft: Int): IO[Path] =
      if (iterationsLeft > 0) IO.sleep(samplePeriod) >> warmup(iterationsLeft - 1)
      else heapUsed.flatMap(x => go(x, x, 0, System.currentTimeMillis()))

    def go(initial: Long, last: Long, positiveCount: Int, started: Long): IO[Path] =
      IO.sleep(samplePeriod) >>
        heapUsed.flatMap { bytes =>
          val deltaSinceStart = bytes - initial
          val deltaSinceLast = bytes - last

          def printBytes(x: Long) = f"$x%,d"

          def printDelta(x: Long) = {
            val pfx = if (x > 0) "+" else ""
            s"$pfx${printBytes(x)}"
          }

          println(
            f"Heap: ${printBytes(bytes)}%12.12s total, ${printDelta(deltaSinceStart)}%12.12s since start, ${printDelta(deltaSinceLast)}%12.12s in last ${samplePeriod}"
          )
          if (
            deltaSinceStart > limitTotalBytesIncreasePerSecond * ((System
              .currentTimeMillis() - started) / 1000.0)
          ) dumpHeap
          else if (deltaSinceLast > 0)
            if (positiveCount > limitConsecutiveIncreases) dumpHeap
            else go(initial, bytes, positiveCount + 1, started)
          else go(initial, bytes, 0, started)
        }

    warmup(warmupIterations)
  }

  private def dumpHeap: IO[Path] =
    IO {
      val path = Files.createTempFile("fs2-leak-test-", ".hprof")
      Files.delete(path)
      val server = ManagementFactory.getPlatformMBeanServer
      val mbean = ManagementFactory.newPlatformMXBeanProxy(
        server,
        "com.sun.management:type=HotSpotDiagnostic",
        classOf[com.sun.management.HotSpotDiagnosticMXBean]
      )
      mbean.dumpHeap(path.toString, true)
      path
    }

  leakTest("Throttler.throttle enforcing") {
    Stream
      .range(0, 1000000)
      .covary[IO]
      .through(Throttler.throttle(100, 10.millis, Enforcing))
  }

  leakTest("Throttler.throttle shaping") {
    Stream
      .range(0, 1000000)
      .covary[IO]
      .through(Throttler.throttle(100, 10.millis, Shaping))
  }

}
