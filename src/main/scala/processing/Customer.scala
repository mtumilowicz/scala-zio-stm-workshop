package processing

import processing.Event.given
import zio.stm.{TPriorityQueue, TRef, ZSTM}
import zio.{Queue, UIO, ZIO, ZIOAppArgs, ZIOAppDefault, durationInt}

case class Customer(id: Int)

case class Event(orderId: Long, customer: Customer)

object Event {
  given Ordering[Event] = Ordering.by(_.orderId)
}

object EventProcessor {

  def process(input: TPriorityQueue[Event], processed: Queue[Customer]): UIO[Unit] = for {
    nextProcessedRef <- TRef.make[Long](1).commit
    _ <- (for {
      customer <- takeIfReadyToProcess(input, nextProcessedRef)
      result <- processed.offer(customer)
    } yield ()).forever
  } yield ()

  private def takeIfReadyToProcess(input: TPriorityQueue[Event], nextProcessedRef: TRef[Long]) = (for {
    event <- input.take
    nextProcessed <- nextProcessedRef.getAndUpdate(_ + 1)
    result <- ZSTM.retry.when(nextProcessed != event.orderId)
  } yield event.customer).commit

}

object Process extends ZIOAppDefault {

  override def run: ZIO[ZIOAppArgs, Any, Any] = for {
    input <- TPriorityQueue.make[Event]().commit
    processed <- Queue.unbounded[Customer]
    f1 <- EventProcessor.process(input, processed).fork
    _ <- input.offer(Event(6, Customer(1))).commit.delay(1.seconds)
    _ <- input.offer(Event(5, Customer(2))).commit.delay(1.seconds)
    _ <- input.offer(Event(1, Customer(3))).commit.delay(1.seconds)
    _ <- input.offer(Event(3, Customer(4))).commit.delay(1.seconds)
    _ <- input.offer(Event(2, Customer(5))).commit.delay(1.seconds)
    _ <- processed.size.repeatUntil(_ == 3)
    _ <- f1.interrupt
  } yield ()
}