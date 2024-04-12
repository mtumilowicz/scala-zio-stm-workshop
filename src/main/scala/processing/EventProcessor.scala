package processing

import zio.stm.{TPriorityQueue, TRef, ZSTM}
import zio.{Queue, UIO}

case class CustomerId(id: Int)
case class Step(id: Long) {
  def next(): Step = Step(id + 1)
}
case class Event(step: Step, customer: CustomerId)

object Step {
  given Ordering[Step] = Ordering.by(_.id)
}
object Event {
  given Ordering[Event] = Ordering.by(_.step)
}

object EventProcessor {

  def process(input: TPriorityQueue[Event], processed: Queue[CustomerId]): UIO[Unit] = for {
    nextToBeProcessed <- TRef.make(Step(1)).commit
    _ <- (for {
      customer <- takeIfReadyToProcess(input, nextToBeProcessed)
      result <- processed.offer(customer)
    } yield ()).forever
  } yield ()

  private def takeIfReadyToProcess(input: TPriorityQueue[Event], nextToBeProcessedRef: TRef[Step]) = (for {
    event <- input.take
    nextProcessedStep <- nextToBeProcessedRef.getAndUpdate(_.next())
    result <- ZSTM.retry.when(nextProcessedStep != event.step)
  } yield event.customer).commit

}