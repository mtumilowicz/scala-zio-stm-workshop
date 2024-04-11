package rating

import zio.UIO
import zio.stm.{TMap, TRef}

case class Top(size: Int, values: Set[Int] = Set.empty) {
  def add(element: Int): Top = {
    if (values.size < size) {
      Top(size, values + element)
    } else {
      val newValues = values + element
      Top(size, newValues - newValues.min)
    }
  }
}

case class Ratings(top: TRef[Top], all: TMap[String, Int]) {
  def add(id: String, rating: Int): UIO[Unit] =
    (for {
      _ <- top.update(_.add(rating))
      _ <- all.putIfAbsent(id, rating)
    } yield ()).commit
}