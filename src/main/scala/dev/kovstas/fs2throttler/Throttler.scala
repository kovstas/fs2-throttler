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
import cats.effect.Temporal
import cats.effect.kernel.Clock
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

  /** The pipe that uses the token bucket algorithm to throttle elements of the stream according to the given rate.
    *
    * @param elements the allowed number of elements
    * @param duration the period time in which emitted elements must meet
    * @param mode the throttle mode [[ThrottleMode]]
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode
  ): Pipe[F, O, O] =
    throttle(elements, duration, mode, 0, (_: O) => Applicative[F].pure(1L))

  /** The pipe that uses the token bucket algorithm to throttle elements of the stream according to the given rate.
    *
    * @param elements the allowed number of elements
    * @param duration the period time in which emitted elements must meet
    * @param mode the throttle mode [[ThrottleMode]]
    * @param burst increase the capacity threshold
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode,
      burst: Long
  ): Pipe[F, O, O] =
    throttle(elements, duration, mode, burst, (_: O) => Applicative[F].pure(1L))

  /** The pipe that uses the token bucket algorithm to throttle elements of the stream according to the given rate, the burst and elements cost.
    *
    * @param elements the allowed number of elements
    * @param duration the period time in which emitted elements must meet
    * @param mode the throttle mode [[ThrottleMode]]
    * @param burst increase the capacity threshold
    * @param fnCost calculate a cost of the element
    * @return fs2.Pipe
    */
  def throttle[F[_]: Temporal, O](
      elements: Long,
      duration: FiniteDuration,
      mode: ThrottleMode,
      burst: Long,
      fnCost: O => F[Long]
  ): Pipe[F, O, O] = { in =>
    val capacity = if (elements + burst <= 0) Long.MaxValue else elements + burst
    val interval = duration.toNanos / capacity

    def go(
        s: Stream[F, O],
        tokens: => Long,
        time: => Long
    ): Pull[F, O, Unit] = {
      s.pull.uncons1.flatMap {
        case Some((head, tail)) =>
          Pull.eval(fnCost(head) product Clock[F].monotonic.map(_.toNanos)).flatMap { case (cost, now) =>
            val (remainingTokens, nextTime, delay) = {
              val elapsed = now - time

              val tokensArrived =
                if (elapsed >= interval) {
                  elapsed / interval
                } else 0
              val nextTime = time + tokensArrived * interval
              val available = math.min(tokens + tokensArrived, capacity)

              if (cost <= available) {
                (available - cost, nextTime, 0L)
              } else {
                val timePassed = now - nextTime
                val waitingTime = (cost - available) * interval
                val delay = waitingTime - timePassed

                (0L, now + delay, delay)
              }
            }

            if (delay == 0) {
              Pull.output1(head) >> go(tail, remainingTokens, nextTime)
            } else
              mode match {
                case Enforcing =>
                  go(tail, remainingTokens, nextTime)
                case Shaping =>
                  Pull.sleep(delay.nanos) >> Pull.output1(head) >> go(tail, remainingTokens, nextTime)
              }
          }

        case None =>
          Pull.done
      }
    }

    if (interval == 0) {
      in
    } else {
      Stream
        .eval(Clock[F].monotonic)
        .flatMap { time =>
          go(in, elements, time.toNanos).stream
        }
    }

  }

}
