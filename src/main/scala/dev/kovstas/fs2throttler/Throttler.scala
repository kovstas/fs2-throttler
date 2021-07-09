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
    * @return [[Pipe]]
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
    * @return [[Pipe]]
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
    * @return [[Pipe]]
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
          val delayPull = Pull.eval {
            for {
              cost <- fnCost(head)
              now <- Clock[F].monotonic
              delay <- bucket.modify { case (tokens, lastUpdate) =>
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
            } yield delay
          }

          for {
            delay <- delayPull
            continueF = Pull.output1(head) >> go(tail, bucket, capacity, interval)
            result <- {
              if (delay == Duration.Zero) {
                continueF
              } else {
                mode match {
                  case Enforcing =>
                    go(tail, bucket, capacity, interval)
                  case Shaping =>
                    Pull.sleep(delay) >> continueF
                }
              }
            }
          } yield result

        case None =>
          Pull.done
      }
    }

    in =>
      val capacity = if (elements + burst < 0) Long.MaxValue else elements + burst

      for {
        bucket <- Stream.eval(
          Ref.ofEffect(
            Clock[F].monotonic.map((capacity, _))
          )
        )
        stream <- go(in, bucket, capacity, duration.toNanos / elements).stream
      } yield stream

  }

}
