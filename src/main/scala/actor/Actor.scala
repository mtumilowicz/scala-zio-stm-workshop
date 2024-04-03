package actor

import zio.{Task, ZIO}
import zio.stm.TReentrantLock

object Actor {

  trait Actor

  trait Supervisor {
    val lock: TReentrantLock

    def remove(actor: Actor): Boolean

    def add(actor: Actor): Unit
  }

  def transfer[E, A](
    actor: Actor,
    from: Supervisor,
    to: Supervisor
  ): Task[Boolean] =
    val acquire = from.lock.acquireWrite.zip(to.lock.acquireWrite).commit
    val release = from.lock.releaseWrite.zip(to.lock.releaseWrite).commit
    ZIO.acquireReleaseWith(acquire)(_ => release) { _ =>
      ZIO.attempt {
        val removed = from.remove(actor)
        if (removed) to.add(actor)
        removed
      }
    }

}
