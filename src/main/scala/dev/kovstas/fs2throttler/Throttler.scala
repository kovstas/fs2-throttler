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

import cats.Applicative
import cats.effect.kernel.Clock
import cats.effect.{Ref, Temporal}
import cats.implicits._
import fs2.{Pipe, Pull, Stream}

import scala.concurrent.duration._

object Throttler {

  /** Describe how to deal with exceed rate.
    */
  sealed trait ThrottleMode

  /** The mode that make pauses before emitting elements of the stream to meet the required rate.
    */
  case object Shaping extends ThrottleMode

  /** The mode that skips elements to meet the required rate.
    */
  case object Enforcing extends ThrottleMode

  /** Throttles elements of the stream according to the given rate using the token bucket algorithm.
    *
    * @param elements - the allowed number of elements
    * @param duration - the period time in which emitted elements must meet
    * @param mode - the throttle mode [[ThrottleMode]]
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode
  ): Pipe[F, O, O] =
    throttle(elements, duration, mode, 0, (_: O) => Applicative[F].pure(1L))

  /** Throttles elements of the stream according to the given rate using the token bucket algorithm.
    *
    * @param elements - the allowed number of elements
    * @param duration - the period time in which emitted elements must meet
    * @param mode - the throttle mode [[ThrottleMode]]
    * @param burst - increase the capacity threshold
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode,
      burst: Long
  ): Pipe[F, O, O] =
    throttle(elements, duration, mode, burst, (_: O) => Applicative[F].pure(1L))

  /** Throttles elements of the stream according to the given rate using the token bucket algorithm.
    *
    * @param elements - the allowed number of elements
    * @param duration - the period time in which emitted elements must meet
    * @param mode - the throttle mode [[ThrottleMode]]
    * @param burst - increase the capacity threshold
    * @param fnCost - calculate a cost of the element
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode,
      burst: Long,
      fnCost: O => F[Long]
  ): Pipe[F, O, O] = {

    def go(
        s: Stream[F, O],
        bucket: Ref[F, (Long, FiniteDuration)],
        capacity: Long,
        interval: Long
    ): Pull[F, O, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          Pull
            .eval(for {
              cost <- fnCost(head)
              now <- Clock[F].monotonic
              delay <- bucket.modify { case (tokens, lastUpdate) =>
                if (interval == 0) {
                  ((0, now), Duration.Zero)
                } else {
                  val elapsed = (now - lastUpdate).toNanos
                  val tokensArrived =
                    if (elapsed >= interval) {
                      elapsed / interval
                    } else 0

                  val nextTime = lastUpdate + (tokensArrived * interval).nanos
                  val available = math.min(tokens + tokensArrived, capacity)

                  if (cost <= available) {
                    ((available - cost, nextTime), Duration.Zero)
                  } else {
                    val timePassed = now.toNanos - nextTime.toNanos
                    val waitingTime = (cost - available) * interval
                    val delay = (waitingTime - timePassed).nanos

                    ((0, now + delay), delay)
                  }
                }
              }
              continueF = Pull.output1(head) >> go(tail, bucket, capacity, interval)
              result <-
                if (delay == Duration.Zero) {
                  Applicative[F].pure(continueF)
                } else {
                  mode match {
                    case Enforcing =>
                      Applicative[F].pure(go(tail, bucket, capacity, interval))
                    case Shaping =>
                      Clock[F].delayBy(Applicative[F].pure(continueF), delay)
                  }
                }
            } yield result)
            .flatMap(identity)

        case None =>
          Pull.done
      }
    }

    in =>
      val capacity = if (elements + burst <= 0) Long.MaxValue else elements + burst

      for {
        bucket <- Stream.eval(
          Ref.ofEffect(
            Clock[F].monotonic.map((capacity, _))
          )
        )
        stream <- go(in, bucket, capacity, duration.toNanos / capacity).stream
      } yield stream

  }

}
