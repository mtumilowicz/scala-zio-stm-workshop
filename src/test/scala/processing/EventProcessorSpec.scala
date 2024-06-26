package processing

import processing.Event.given
import zio.stm.TPriorityQueue
import zio.test.{Gen, Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{Queue, Random, ZIO}

object EventProcessorSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("EventProcessor tests")(
      test("process should correctly process if orderIds are consecutive") {
        check(Gen.int(1, 100)) { n =>
          for {
            orderIds <- Random.shuffle((1 to n).toList)
            customerId <- Random.nextInt
            events = orderIds.map(step => Event(Step(step), CustomerId(customerId)))
            input <- TPriorityQueue.make[Event]().commit
            processed <- Queue.unbounded[CustomerId]
            _ <- EventProcessor.process(input, processed).fork
            _ <- ZIO.foreachDiscard(events)(event => input.offer(event).commit)
            _ <- processed.size.repeatUntil(_ == n)
            processedCustomers <- processed.takeAll
            inputSize <- input.size.commit
          } yield assertTrue(processedCustomers.size == events.size, inputSize == 0)
        }
      },
      test("process should stop processing events at first orderId gap") {
        check(Gen.int(2, 49), Gen.int(51, 100)) { (gapStart, gapEnd) =>
          val orderIds = ((1 to gapStart) ++ (gapEnd to 100)).toSet
          for {
            customerId <- Random.nextInt
            events = orderIds.map(step => Event(Step(step), CustomerId(customerId)))
            input <- TPriorityQueue.make[Event]().commit
            processed <- Queue.unbounded[CustomerId]
            _ <- EventProcessor.process(input, processed).fork
            _ <- ZIO.foreachDiscard(events)(event => input.offer(event).commit)
            _ <- processed.size.repeatUntil(_ == gapStart)
            processedCustomers <- processed.takeAll
            inputSize <- input.size.commit
          } yield assertTrue(processedCustomers.size == gapStart, inputSize == 100 - gapEnd + 1)
        }
      }
    )

}