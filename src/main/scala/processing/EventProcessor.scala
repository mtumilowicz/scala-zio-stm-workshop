package processing

import zio.stm.{TPriorityQueue, TRef, ZSTM}
import zio.{Queue, UIO}

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