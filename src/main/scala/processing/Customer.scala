package processing

import zio.{Clock, UIO, ZIO}
import zio.stm.{TQueue, ZSTM}

import java.time.Instant

class Customer
case class Event(instant: Instant, customer: Customer)

def process(queue: TQueue[Event]): UIO[Customer] =
  Clock.instant.flatMap { now =>
    queue.take
      .flatMap { case Event(instant, customer) =>
        if (now.isBefore(instant)) ZSTM.retry else ZSTM.succeed(customer)
      }.commit.forever
  }