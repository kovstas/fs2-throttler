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

import fs2.Stream
import Throttler._
import cats.effect.IO
import cats.effect.kernel.testkit.TestContext
import cats.effect.unsafe.{IORuntime, IORuntimeConfig, Scheduler}

import concurrent.duration._
import scala.collection.mutable.ListBuffer

class ThrottlerSpec extends munit.FunSuite {

  test("Shaping throttler works") {
    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    Stream
      .range(0, 5)
      .covary[IO]
      .through(throttle(1, 100.millis, Shaping))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.tick()
    assertEquals(elements.toList, List(0))
    ctx.advanceAndTick(100.millis)
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(100.millis)
    assertEquals(elements.toList, List(0, 1, 2))
    ctx.advanceAndTick(100.millis)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(100.millis)
    assertEquals(elements.toList, List(0, 1, 2, 3, 4))
  }

  test("Shaping throttler accept high rates") {
    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    Stream
      .range(0, 5)
      .covary[IO]
      .through(throttle(1, 1.nano, Shaping))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.tick()
    assertEquals(elements.toList, List(0))
    ctx.advanceAndTick(1.nano)
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(1.nano)
    assertEquals(elements.toList, List(0, 1, 2))
    ctx.advanceAndTick(1.nano)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(1.nano)
    assertEquals(elements.toList, List(0, 1, 2, 3, 4))
  }

  test("Shaping throttler accept low rates") {
    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    Stream
      .range(0, 5)
      .covary[IO]
      .through(throttle(1, 100.days, Shaping))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.tick()
    assertEquals(elements.toList, List(0))
    ctx.advanceAndTick(100.days)
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(100.days)
    assertEquals(elements.toList, List(0, 1, 2))
    ctx.advanceAndTick(100.days)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(100.days)
    assertEquals(elements.toList, List(0, 1, 2, 3, 4))
  }

  test("Shaping throttler positive burst") {
    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    Stream
      .range(0, 7)
      .covary[IO]
      .through(throttle(1, 1.second, Shaping, 1))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.tick()
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(2.seconds)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(4.seconds)
    assertEquals(elements.toList, List(0, 1, 2, 3, 4, 5, 6))
  }

  test("Enforcing throttler works") {
    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    Stream
      .range(0, 10)
      .covary[IO]
      .through(throttle(1, 100.millis, Enforcing))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.advanceAndTick(300.millis)
    assertEquals(elements.toList, List(0))
  }

  test("Throttler should use the costFn") {

    val (ctx, runtime) = createDeterministicRuntime
    val elements = ListBuffer[Int]()

    val costFn = (v: Int) =>
      IO.pure {
        if (v == 0) {
          1L
        } else v.toLong
      }

    Stream
      .range(0, 5)
      .covary[IO]
      .through(throttle(1, 1.second, Shaping, 0, costFn))
      .map(elements += _)
      .compile
      .drain
      .unsafeToFuture()(runtime)

    ctx.tick()
    assertEquals(elements.toList, List(0))
    ctx.advanceAndTick(1.seconds)
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(1.seconds)
    assertEquals(elements.toList, List(0, 1))
    ctx.advanceAndTick(1.seconds)
    assertEquals(elements.toList, List(0, 1, 2))
    ctx.advanceAndTick(1.seconds)
    assertEquals(elements.toList, List(0, 1, 2))
    ctx.advanceAndTick(2.seconds)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(1.seconds)
    assertEquals(elements.toList, List(0, 1, 2, 3))
    ctx.advanceAndTick(3.seconds)
    assertEquals(elements.toList, List(0, 1, 2, 3, 4))

  }

  private def createDeterministicRuntime: (TestContext, IORuntime) = {
    val ctx = TestContext()

    val scheduler = new Scheduler {
      def sleep(delay: FiniteDuration, action: Runnable): Runnable = {
        val cancel = ctx.schedule(delay, action)
        () => cancel()
      }

      def nowMillis(): Long = ctx.now().toMillis
      def monotonicNanos(): Long = ctx.now().toNanos
    }

    val runtime = IORuntime(ctx, ctx, scheduler, () => (), IORuntimeConfig())

    (ctx, runtime)
  }

}
