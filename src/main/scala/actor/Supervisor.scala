package actor

import zio.stm.TReentrantLock
import zio.{Task, ZIO}

object Supervisor {

  trait Actor

  trait Supervisor { self =>
    val lock: TReentrantLock

    def remove(actor: Actor): Boolean

    def add(actor: Actor): Unit

    def transferTo(
      actor: Actor,
      to: Supervisor
    ): Task[Boolean] =
      val acquire = self.lock.acquireWrite.zip(to.lock.acquireWrite).commit
      val release = self.lock.releaseWrite.zip(to.lock.releaseWrite).commit
      ZIO.acquireReleaseWith(acquire)(_ => release) { _ =>
        ZIO.attempt {
          val removed = self.remove(actor)
          if (removed) to.add(actor)
          removed
        }
      }
  }

}
