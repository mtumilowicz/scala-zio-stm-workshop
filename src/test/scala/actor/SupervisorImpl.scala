package actor

import actor.Supervisor.{Actor, Supervisor}
import zio.stm.TReentrantLock

import scala.collection.mutable

class ActorImpl extends Actor

class SupervisorImpl(override val lock: TReentrantLock, val actors: mutable.Set[Actor]) extends Supervisor {
  
  def remove(actor: Actor): Boolean = {
    actors.remove(actor)
  }

  def add(actor: Actor): Unit = {
    actors.add(actor)
  }

}