package processing

import processing.Event.given
import zio.stm.{TPriorityQueue, ZSTM}
import zio.{Clock, UIO, ZIO, ZIOAppArgs, ZIOAppDefault, durationInt}

import java.time.Instant

class Customer

case class Event(instant: Instant, customer: Customer)

object Event {
  given Ordering[Event] = Ordering.by(_.instant)
}

def process(queue: TPriorityQueue[Event]): UIO[Customer] =
  Clock.instant.flatMap { now =>
    (for {
      event <- queue.take
      result <- if (now.isBefore(event.instant)) {
        println("retry");
        ZSTM.retry
      } else {
        println("processed");
        ZSTM.succeed(event.customer)
      }
    } yield result).commit.forever
  }

object Process extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Any, Any] = for {
    queue <- TPriorityQueue.make[Event]().commit
    f1 <- process(queue).fork
    _ <- queue.offer(Event(Instant.parse("2030-12-03T10:15:30.00Z"), new Customer)).commit
    _ <- queue.offer(Event(Instant.parse("2010-12-03T10:15:30.00Z"), new Customer)).commit.delay(5.seconds)
    _ <- f1.join
  } yield ()
}