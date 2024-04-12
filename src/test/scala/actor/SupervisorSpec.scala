package actor

import zio.stm.TReentrantLock
import zio.test.{Gen, Sized, Spec, TestEnvironment, ZIOSpecDefault, assertCompletes, assertTrue, check}
import zio.{Random, ZIO}

import scala.collection.mutable

object SupervisorSpec extends ZIOSpecDefault {

  def genActorImpl: Gen[Sized, ActorImpl] = Gen.const(new ActorImpl)

  override def spec: Spec[TestEnvironment, Any] =
    suite("Supervisor")(
      test("transfer all actors from one supervisor to the other") {
        check(Gen.setOf(genActorImpl)) { actors =>
          for {
            reentrantLock1 <- TReentrantLock.make.commit
            reentrantLock2 <- TReentrantLock.make.commit
            supervisor1 = new SupervisorImpl(reentrantLock1, mutable.Set.from(actors))
            supervisor2 = new SupervisorImpl(reentrantLock2, mutable.Set.empty)
            _ <- ZIO.foreachParDiscard(actors) { actor =>
              supervisor1.transferTo(actor, supervisor2)
                .option
            }
          } yield assertTrue(supervisor1.actors.isEmpty, supervisor2.actors == actors)
        }
      },
      test("swap all actors of two supervisors") {
        check(Gen.setOf(genActorImpl), Gen.setOf(genActorImpl)) { (actors1, actors2) =>
          for {
            reentrantLock1 <- TReentrantLock.make.commit
            reentrantLock2 <- TReentrantLock.make.commit
            supervisor1 = new SupervisorImpl(reentrantLock1, mutable.Set.from(actors1))
            supervisor2 = new SupervisorImpl(reentrantLock2, mutable.Set.from(actors2))
            _ <- ZIO.foreachParDiscard(actors1) { actor =>
              supervisor1.transferTo(actor, supervisor2)
                .option
            } <&> ZIO.foreachParDiscard(actors2) { actor =>
              supervisor2.transferTo(actor, supervisor1)
                .option
            }
          } yield assertTrue(supervisor1.actors == actors2, supervisor2.actors == actors1)
        }
      },
      test("after transfers list of actors should be the same") {
        check(Gen.setOf(genActorImpl), Gen.setOf(genActorImpl)) { (actors1, actors2) =>
          for {
            reentrantLock1 <- TReentrantLock.make.commit
            reentrantLock2 <- TReentrantLock.make.commit
            supervisor1 = new SupervisorImpl(reentrantLock1, mutable.Set.from(actors1))
            supervisor2 = new SupervisorImpl(reentrantLock2, mutable.Set.from(actors2))
            actorsAll1 <- Random.shuffle((actors1 ++ actors2).toList)
            actorsAll2 <- Random.shuffle(actorsAll1)
            _ <- ZIO.foreachParDiscard(actorsAll1) { actor =>
              supervisor1.transferTo(actor, supervisor2)
                .option
            } <&> ZIO.foreachParDiscard(actorsAll2) { actor =>
              supervisor2.transferTo(actor, supervisor1)
                .option
            }
          } yield assertTrue(actors1 ++ actors2 == supervisor1.actors ++ supervisor2.actors)
        }
      },
      test("at any given point in time actor can only be under one supervisor") {
        check(Gen.setOf(genActorImpl), Gen.setOf(genActorImpl)) { (actors1, actors2) =>
          for {
            reentrantLock1 <- TReentrantLock.make.commit
            reentrantLock2 <- TReentrantLock.make.commit
            supervisor1 = new SupervisorImpl(reentrantLock1, mutable.Set.from(actors1))
            supervisor2 = new SupervisorImpl(reentrantLock2, mutable.Set.from(actors2))
            actorsAll1 <- Random.shuffle((actors1 ++ actors2).toList)
            actorsAll2 <- Random.shuffle(actorsAll1)
            f1 <- (ZIO.foreachParDiscard(actorsAll1) { actor =>
              supervisor1.transferTo(actor, supervisor2)
                .option
            } <&> ZIO.foreachParDiscard(actorsAll2) { actor =>
              supervisor2.transferTo(actor, supervisor1)
                .option
            }).fork
            f2 <- ZIO.fail("should never fail")
              .unless(supervisor1.actors.intersect(supervisor2.actors).isEmpty)
              .forever.fork
            _ <- f1.join
            _ <- f2.interrupt
          } yield assertCompletes
        }
      }
    )
}
