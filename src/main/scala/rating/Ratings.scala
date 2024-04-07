package rating

import zio.{Console, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}
import zio.stm.{TMap, TRef, USTM, ZSTM}

case class Top(size: Int, values: List[Int] = Nil) {
  def add(element: Int): Top =
    val v = if (values.size >= size) {
      (values :+ element).sorted.tail
    } else {
      values :+ element
    }
    Top(size, v)
}

case class Ratings(top: TRef[Top], all: TMap[String, Int]) {
  def add(id: String, rating: Int): USTM[Unit] =
      for {
        _ <- top.update(_.add(rating))
        _ <- all.putIfAbsent(id, rating)
      } yield ()
}

object RatingSimulation extends ZIOAppDefault {
  val ratings = for {
    top <- TRef.make(Top(3)).commit
    all <- TMap.empty.commit
  } yield Ratings(top, all)

  val ratingsSimulation = for {
    r <- ratings
    _ <- r.add("1", 1).commit
    _ <- r.add("2", 2).commit
    _ <- r.add("3", 3).commit
    top <- r.top.get.commit
    _ <- Console.printLine(s"top: $top")
    _ <- r.add("4", 4).commit
    top <- r.top.get.commit
    _ <- Console.printLine(s"top: $top")
    _ <- r.add("5", 2).commit
    _ <- r.add("6", 100).commit
    top <- r.top.get.commit
    _ <- Console.printLine(s"top: $top")
  } yield ()

  override def run = ratingsSimulation
}