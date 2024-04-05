package processing

import zio.stm.{TQueue, ZSTM}
import zio.{Clock, UIO}

import java.time.Instant

class Customer

case class Event(instant: Instant, customer: Customer)

def process(queue: TQueue[Event]): UIO[Customer] =
  Clock.instant.flatMap { now =>
    (for {
      event <- queue.take
      result <- if (now.isBefore(event.instant)) ZSTM.retry else ZSTM.succeed(event.customer)
    } yield result).commit.forever
  }